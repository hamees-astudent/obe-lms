package com.lms.modules.attendance;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.modules.attendance.dto.*;
import com.lms.shared.CacheNames;
import com.lms.shared.OfferingStaff;
import com.lms.shared.events.AttendanceAlertEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lms.infrastructure.cache.CacheEvictor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final AttendanceSessionRepository sessionRepository;
    private final AttendanceRecordRepository  recordRepository;
    private final KafkaEventPublisher         kafkaEventPublisher;
    private final AttendanceProperties        properties;
    private final CacheEvictor                cacheEvictor;
    private final OfferingStaff               offeringStaff;

    // ── Sessions ──────────────────────────────────────────────────────────────

    @Transactional
    public SessionResponse createSession(UUID createdBy, boolean isAdmin, CreateSessionRequest req) {
        requireOfferingStaff(req.pscId(), createdBy, isAdmin);
        var session = new AttendanceSession();
        session.setPscId(req.pscId());
        session.setCreatedBy(createdBy);
        session.setSessionDate(req.sessionDate());
        session.setTopic(req.topic());
        return toSessionResponse(sessionRepository.save(session));
    }

    @Transactional
    public SessionResponse closeSession(UUID sessionId, UUID actorId, boolean isAdmin) {
        var session = requireSession(sessionId);
        requireOfferingStaff(session.getPscId(), actorId, isAdmin);
        if (!session.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Session is already closed");
        }
        session.setClosedAt(LocalDateTime.now());
        return toSessionResponse(sessionRepository.save(session));
    }

    public List<SessionResponse> listSessions(UUID pscId, LocalDate date) {
        List<AttendanceSession> sessions = (date != null)
                ? sessionRepository.findAllByPscIdAndSessionDate(pscId, date)
                : sessionRepository.findAllByPscIdOrderBySessionDateDesc(pscId);
        return sessions.stream().map(this::toSessionResponse).toList();
    }

    public SessionResponse getSession(UUID sessionId) {
        return toSessionResponse(requireSession(sessionId));
    }

    // ── Records (mark attendance) ─────────────────────────────────────────────

    @Transactional
    public AttendanceRecordResponse markRecord(UUID sessionId, UUID studentId,
                                               MarkAttendanceRequest req, UUID markedBy,
                                               boolean isAdmin) {
        var session = requireSession(sessionId);
        requireOfferingStaff(session.getPscId(), markedBy, isAdmin);
        if (!session.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot mark attendance on a closed session");
        }
        requireEnrolledStudents(session.getPscId(), List.of(studentId));

        // Upsert
        var record = recordRepository.findBySessionIdAndStudentId(sessionId, studentId)
                .orElseGet(() -> {
                    var r = new AttendanceRecord();
                    r.setSessionId(sessionId);
                    r.setStudentId(studentId);
                    return r;
                });
        record.setStatus(req.status());
        record.setRemarks(req.remarks());
        record.setMarkedBy(markedBy);
        record = recordRepository.save(record);

        evictSummary(session.getPscId(), studentId);
        checkThresholdAndAlert(session.getPscId(), studentId);
        return toRecordResponse(record);
    }

    @Transactional
    public List<AttendanceRecordResponse> bulkMark(UUID sessionId,
                                                    BulkMarkRequest req, UUID markedBy,
                                                    boolean isAdmin) {
        var session = requireSession(sessionId);
        requireOfferingStaff(session.getPscId(), markedBy, isAdmin);
        if (!session.isOpen()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot mark attendance on a closed session");
        }
        requireEnrolledStudents(session.getPscId(),
                req.records().stream().map(BulkMarkEntry::studentId).toList());

        var results = req.records().stream().map(entry -> {
            var record = recordRepository.findBySessionIdAndStudentId(sessionId, entry.studentId())
                    .orElseGet(() -> {
                        var r = new AttendanceRecord();
                        r.setSessionId(sessionId);
                        r.setStudentId(entry.studentId());
                        return r;
                    });
            record.setStatus(entry.status());
            record.setRemarks(entry.remarks());
            record.setMarkedBy(markedBy);
            return recordRepository.save(record);
        }).toList();

        // Refresh the cached summary and check the threshold for each student affected
        results.forEach(r -> {
            evictSummary(session.getPscId(), r.getStudentId());
            checkThresholdAndAlert(session.getPscId(), r.getStudentId());
        });

        return results.stream().map(this::toRecordResponse).toList();
    }

    public List<AttendanceRecordResponse> getRecordsForSession(UUID sessionId) {
        requireSession(sessionId);
        return recordRepository.findAllBySessionId(sessionId)
                .stream().map(this::toRecordResponse).toList();
    }

    public List<AttendanceRecordResponse> getRecordsForStudent(UUID studentId) {
        return recordRepository.findAllByStudentId(studentId)
                .stream().map(this::toRecordResponse).toList();
    }

    // ── Attendance summary ────────────────────────────────────────────────────

    @Cacheable(value = CacheNames.ATTENDANCE_SUMMARY, key = "#pscId + ':' + #studentId")
    public AttendanceSummaryResponse getAttendanceSummary(UUID pscId, UUID studentId) {
        return buildSummary(pscId, studentId);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    /** Restricts a write to the staff who run the offering. See {@link OfferingStaff}. */
    private void requireOfferingStaff(UUID pscId, UUID userId, boolean isAdmin) {
        offeringStaff.require(pscId, userId, isAdmin);
    }

    /**
     * Attendance is recorded only for students actively enrolled in the
     * offering. Any user id was accepted before, so a record could be written
     * for someone outside the class, or for its staff.
     */
    private void requireEnrolledStudents(UUID pscId, List<UUID> studentIds) {
        Set<String> enrolled = new HashSet<>(sessionRepository.findActiveStudentIds(pscId));
        long outside = studentIds.stream().filter(id -> !enrolled.contains(id.toString())).count();
        if (outside > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, outside == 1 && studentIds.size() == 1
                    ? "That user is not an enrolled student of this course offering."
                    : outside + " of the listed users are not enrolled students of this course offering.");
        }
    }

    private AttendanceSession requireSession(UUID id) {
        return sessionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Attendance session not found: " + id));
    }

    /**
     * Drops the cached summary for one student in one offering.
     *
     * <p>Explicit rather than {@code @CacheEvict}: the marking methods call this
     * from inside the same bean, and a self-invocation never passes through the
     * caching proxy — the annotation would be silently inert and every student
     * would keep reading the summary cached before their attendance was marked.
     */
    private void evictSummary(UUID pscId, UUID studentId) {
        cacheEvictor.evict(CacheNames.ATTENDANCE_SUMMARY, pscId + ":" + studentId);
    }

    /** Recomputes the summary and publishes an alert if below threshold. */
    public void checkThresholdAndAlert(UUID pscId, UUID studentId) {
        var summary = buildSummary(pscId, studentId);
        // Nothing counted yet (no records, or only EXCUSED ones) — a 0% here
        // means "unknown", so alerting on it would be noise.
        if (summary.total() == 0) {
            return;
        }
        if (summary.percentage() < properties.getThresholdPercentage()) {
            sessionRepository.findAlertContext(pscId, studentId).ifPresentOrElse(
                    ctx -> kafkaEventPublisher.publishAttendanceAlertEvent(
                            AttendanceAlertEvent.builder()
                                    .studentId(studentId)
                                    .studentEmail(ctx.getStudentEmail())
                                    .studentName(ctx.getStudentName())
                                    .programSemesterCourseId(pscId)
                                    .courseCode(ctx.getCourseCode())
                                    .courseName(ctx.getCourseName())
                                    .semesterName(ctx.getSemesterName())
                                    .attendancePercentage(summary.percentage())
                                    .thresholdPercentage(properties.getThresholdPercentage())
                                    .build()),
                    () -> log.warn("Alert context not found for pscId={} studentId={}", pscId, studentId));
        }
    }

    private AttendanceSummaryResponse buildSummary(UUID pscId, UUID studentId) {
        var view = recordRepository.computeSummary(pscId, studentId);
        long attended = view.getAttended();
        long total    = view.getTotal();
        // No marked sessions means "nothing recorded yet", not "perfect
        // attendance" — reporting 100% here hides a course nobody is marking
        // and suppresses the below-threshold alert.
        double pct    = (total > 0) ? (attended * 100.0 / total) : 0.0;
        return new AttendanceSummaryResponse(pscId, studentId, attended, total, pct);
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private SessionResponse toSessionResponse(AttendanceSession s) {
        return new SessionResponse(s.getId(), s.getPscId(), s.getCreatedBy(),
                s.getSessionDate(), s.getTopic(), s.getOpenedAt(), s.getClosedAt(),
                s.isOpen(), s.getCreatedAt());
    }

    private AttendanceRecordResponse toRecordResponse(AttendanceRecord r) {
        return new AttendanceRecordResponse(r.getId(), r.getSessionId(), r.getStudentId(),
                r.getStatus(), r.getMarkedBy(), r.getRemarks(),
                r.getCreatedAt(), r.getUpdatedAt());
    }
}
