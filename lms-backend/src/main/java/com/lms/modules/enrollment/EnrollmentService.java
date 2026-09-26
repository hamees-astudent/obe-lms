package com.lms.modules.enrollment;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.enrollment.dto.EnrollmentResponse;
import com.lms.shared.CacheNames;
import com.lms.shared.events.EnrollmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lms.infrastructure.cache.CacheEvictor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EnrollmentService {

    private final EnrollmentRepository   enrollmentRepository;
    private final KafkaEventPublisher    kafkaEventPublisher;
    private final CacheEvictor           cacheEvictor;

    // ── Enroll ────────────────────────────────────────────────────────────────

    @Transactional
    @CacheEvict(value = CacheNames.ENROLLMENT, key = "#studentId")
    public EnrollmentResponse enroll(UUID pscId, UUID studentId, String courseRole) {
        // 1. Capacity check
        int maxCapacity = enrollmentRepository.findMaxCapacityByPscId(pscId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course offering not found: " + pscId));

        // Staff can be assigned on the offering (teacher of record) or as course
        // members; both are kept, but one person must not hold both at once.
        boolean isTeacherOfRecord = enrollmentRepository.findTeacherIdByPscId(pscId)
                .map(id -> id.equals(studentId.toString()))
                .orElse(false);
        if (isTeacherOfRecord) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This user is already the teacher of record for this offering. "
                    + "To change their role, change the teacher on the offering instead.");
        }

        // Capacity is a limit on students. Staff memberships neither count toward
        // it nor are blocked by it — counting them let adding an assistant or
        // co-teacher fill the class.
        if (maxCapacity > 0 && "STUDENT".equals(courseRole)) {
            long students = enrollmentRepository.countByPscIdAndStatusAndCourseRole(
                    pscId, "ACTIVE", "STUDENT");
            if (students >= maxCapacity) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Course offering is at full student capacity (" + maxCapacity + ")");
            }
        }

        // 2. Check for existing enrollment row
        var existing = enrollmentRepository.findByPscIdAndStudentId(pscId, studentId);
        Enrollment enrollment;

        if (existing.isPresent()) {
            enrollment = existing.get();
            switch (enrollment.getStatus()) {
                case "ACTIVE" -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Student is already actively enrolled in this offering");
                case "COMPLETED" -> throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Cannot re-enroll in a completed offering");
                case "DROPPED" -> {
                    // Re-activate the existing row
                    enrollment.setStatus("ACTIVE");
                    enrollment.setDroppedAt(null);
                    enrollment.setEnrolledAt(LocalDateTime.now());
                }
                default -> throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "Unknown enrollment status: " + enrollment.getStatus());
            }
        } else {
            enrollment = new Enrollment();
            enrollment.setPscId(pscId);
            enrollment.setStudentId(studentId);
        }

        enrollment.setCourseRole(courseRole);
        enrollment = enrollmentRepository.save(enrollment);
        publishEvent(enrollment, EnrollmentEvent.Action.ENROLLED);
        return toResponse(enrollment);
    }

    // ── Bulk enroll ───────────────────────────────────────────────────────────

    /** One student's result in {@link #enrollStudents}; a null reason means enrolled. */
    public record BulkOutcome(UUID studentId, String skipReason) {
        public boolean enrolled() {
            return skipReason == null;
        }
    }

    /**
     * Enrolls many students as STUDENTs of one offering, in one transaction.
     *
     * <p>A student who cannot be enrolled — already a member, already completed
     * the offering, the teacher of record — is skipped and reported rather than
     * failing the batch: a cohort is typically enrolled again after a few late
     * students join it, and that re-run must not be refused because most of the
     * cohort is already in. Capacity is the exception. It is checked for the
     * batch as a whole and refuses it outright, because enrolling only the
     * first N would hand out the places by list order.
     *
     * <p>Every check is made up front rather than by calling {@link #enroll}
     * per student and catching its refusals: an exception thrown through a
     * joined {@code @Transactional} call marks the whole transaction
     * rollback-only, even when caught, and the batch would fail on commit.
     */
    @Transactional
    public List<BulkOutcome> enrollStudents(UUID pscId, Collection<UUID> studentIds) {
        int maxCapacity = enrollmentRepository.findMaxCapacityByPscId(pscId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course offering not found: " + pscId));
        String teacherId = enrollmentRepository.findTeacherIdByPscId(pscId).orElse(null);
        Map<UUID, Enrollment> existing = enrollmentRepository.findAllByPscId(pscId).stream()
                .collect(Collectors.toMap(Enrollment::getStudentId, e -> e));

        List<BulkOutcome> outcomes = new ArrayList<>();
        List<Enrollment> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();

        for (UUID studentId : new LinkedHashSet<>(studentIds)) {
            Enrollment enrollment = existing.get(studentId);
            String reason = null;

            if (studentId.toString().equals(teacherId)) {
                reason = "Teacher of record for this offering";
            } else if (enrollment == null) {
                enrollment = new Enrollment();
                enrollment.setPscId(pscId);
                enrollment.setStudentId(studentId);
            } else {
                switch (enrollment.getStatus()) {
                    case Enrollment.STATUS_ACTIVE ->
                            reason = "Already a member (" + enrollment.getCourseRole() + ")";
                    case Enrollment.STATUS_COMPLETED ->
                            reason = "Already completed this offering";
                    case Enrollment.STATUS_DROPPED -> {
                        enrollment.setStatus(Enrollment.STATUS_ACTIVE);
                        enrollment.setDroppedAt(null);
                        enrollment.setEnrolledAt(now);
                    }
                    default -> reason = "Unknown enrollment status: " + enrollment.getStatus();
                }
            }

            if (reason == null) {
                enrollment.setCourseRole("STUDENT");
                toSave.add(enrollment);
            }
            outcomes.add(new BulkOutcome(studentId, reason));
        }

        if (maxCapacity > 0 && !toSave.isEmpty()) {
            long students = enrollmentRepository.countByPscIdAndStatusAndCourseRole(
                    pscId, Enrollment.STATUS_ACTIVE, "STUDENT");
            long placesLeft = Math.max(0, maxCapacity - students);
            if (toSave.size() > placesLeft) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Enrolling these students needs " + toSave.size() + " places, but the offering has "
                        + placesLeft + " left (student capacity " + maxCapacity + "). "
                        + "Raise the offering's capacity, or enroll fewer students.");
            }
        }

        for (Enrollment saved : enrollmentRepository.saveAll(toSave)) {
            evictStudentCache(saved.getStudentId());
            publishEvent(saved, EnrollmentEvent.Action.ENROLLED);
        }
        return outcomes;
    }

    // ── Drop ──────────────────────────────────────────────────────────────────

    @Transactional
    public EnrollmentResponse drop(UUID enrollmentId, UUID requestorId, boolean isAdmin) {
        var enrollment = requireEnrollment(enrollmentId);

        if (!isAdmin && !enrollment.getStudentId().equals(requestorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You can only drop your own enrollment");
        }
        if (!"ACTIVE".equals(enrollment.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only ACTIVE enrollments can be dropped (current: " + enrollment.getStatus() + ")");
        }

        enrollment.setStatus("DROPPED");
        enrollment.setDroppedAt(LocalDateTime.now());
        enrollment = enrollmentRepository.save(enrollment);

        evictStudentCache(enrollment.getStudentId());
        publishEvent(enrollment, EnrollmentEvent.Action.DROPPED);
        return toResponse(enrollment);
    }

    // ── Queries ───────────────────────────────────────────────────────────────

    @Cacheable(value = CacheNames.ENROLLMENT, key = "#studentId")
    public List<EnrollmentResponse> listByStudent(UUID studentId) {
        return enrollmentRepository.findAllByStudentId(studentId)
                .stream().map(this::toResponse).toList();
    }

    public List<EnrollmentResponse> listByStudentAndStatus(UUID studentId, String status) {
        requireValidStatus(status);
        return enrollmentRepository.findAllByStudentIdAndStatus(studentId, status)
                .stream().map(this::toResponse).toList();
    }

    /**
     * Rejects an unknown status filter. Without this an unrecognised value —
     * a client sending {@code ENROLLED} instead of {@code ACTIVE}, say — comes
     * back as an empty list that is indistinguishable from "no enrollments",
     * which is how a whole module can look empty for every student.
     */
    private void requireValidStatus(String status) {
        if (status != null && !Enrollment.STATUSES.contains(status)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown enrollment status '" + status + "'; expected one of "
                            + Enrollment.STATUSES);
        }
    }

    public List<EnrollmentResponse> listByPsc(UUID pscId, String status) {
        requireValidStatus(status);
        List<Enrollment> rows = (status != null)
                ? enrollmentRepository.findAllByPscIdAndStatus(pscId, status)
                : enrollmentRepository.findAllByPscId(pscId);
        Map<UUID, StudentNameView> identities = enrollmentRepository.findStudentNamesByPscId(pscId)
                .stream()
                .collect(Collectors.toMap(
                        v -> UUID.fromString(v.getStudentId()),
                        v -> v));
        return rows.stream()
                .map(e -> {
                    StudentNameView identity = identities.get(e.getStudentId());
                    return toResponse(e,
                            identity != null ? identity.getStudentName() : null,
                            identity != null ? identity.getStudentNumber() : null);
                })
                .toList();
    }

    public EnrollmentResponse getEnrollment(UUID id) {
        return toResponse(requireEnrollment(id));
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    public void requireOfferingExists(UUID pscId) {
        if (enrollmentRepository.findMaxCapacityByPscId(pscId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Course offering not found: " + pscId);
        }
    }

    private Enrollment requireEnrollment(UUID id) {
        return enrollmentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Enrollment not found: " + id));
    }

    /**
     * Drops the student's cached enrollment list.
     *
     * <p>Explicit rather than {@code @CacheEvict}: this is called from
     * {@code drop} on the same bean, and a self-invocation bypasses the caching
     * proxy — leaving a dropped course visible in "my courses" until the entry
     * expires on its own.
     */
    public void evictStudentCache(UUID studentId) {
        cacheEvictor.evict(CacheNames.ENROLLMENT, studentId);
    }

    private void publishEvent(Enrollment enrollment, EnrollmentEvent.Action action) {
        enrollmentRepository.findEventContext(enrollment.getPscId(), enrollment.getStudentId())
                .ifPresentOrElse(
                        ctx -> kafkaEventPublisher.publishEnrollmentEvent(
                                EnrollmentEvent.builder()
                                        .action(action)
                                        .studentId(enrollment.getStudentId())
                                        .studentEmail(ctx.getStudentEmail())
                                        .studentName(ctx.getStudentName())
                                        .programSemesterCourseId(enrollment.getPscId())
                                        .courseCode(ctx.getCourseCode())
                                        .courseName(ctx.getCourseName())
                                        .semesterName(ctx.getSemesterName())
                                        .programName(ctx.getProgramName())
                                        .build()),
                        () -> log.warn("Could not load event context for enrollment {}; skipping event",
                                enrollment.getId()));
    }

    private EnrollmentResponse toResponse(Enrollment e) {
        return toResponse(e, null, null);
    }

    private EnrollmentResponse toResponse(Enrollment e, String studentName, String studentNumber) {
        return new EnrollmentResponse(e.getId(), e.getPscId(), e.getStudentId(),
                studentName, studentNumber, e.getCourseRole(), e.getStatus(),
                e.getEnrolledAt(), e.getDroppedAt(), e.getCreatedAt());
    }
}
