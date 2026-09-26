package com.lms.modules.enrollment;

import com.lms.modules.enrollment.dto.AddCohortMembersRequest;
import com.lms.modules.enrollment.dto.AddCohortMembersResponse;
import com.lms.modules.enrollment.dto.CohortDetailResponse;
import com.lms.modules.enrollment.dto.CohortEnrollmentResponse;
import com.lms.modules.enrollment.dto.CohortMemberResponse;
import com.lms.modules.enrollment.dto.CohortRequest;
import com.lms.modules.enrollment.dto.CohortSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Cohorts: named groups of students, kept so a whole batch can be enrolled in
 * an offering at once. Enrolling goes through
 * {@link EnrollmentService#enrollStudents}, so a cohort enrollment is the same
 * as enrolling each student by hand — same rows, same events, same notifications.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CohortService {

    private static final String ROLE_STUDENT   = "STUDENT";
    private static final String STATUS_ACTIVE  = "ACTIVE";

    private final CohortRepository  cohortRepository;
    private final EnrollmentService enrollmentService;

    // ── Cohort CRUD ───────────────────────────────────────────────────────────

    public List<CohortSummaryResponse> list() {
        Map<UUID, Long> sizes = cohortRepository.countMembers().stream()
                .collect(Collectors.toMap(v -> UUID.fromString(v.getCohortId()),
                        CohortSizeView::getMemberCount));
        return cohortRepository.findAllByOrderByNameAsc().stream()
                .map(c -> toSummary(c, sizes.getOrDefault(c.getId(), 0L)))
                .toList();
    }

    public CohortDetailResponse get(UUID id) {
        return toDetail(requireCohort(id));
    }

    @Transactional
    public CohortSummaryResponse create(CohortRequest req) {
        String name = req.name().trim();
        if (cohortRepository.existsByNameIgnoreCase(name)) {
            throw duplicateName(name);
        }
        Cohort cohort = new Cohort();
        cohort.setName(name);
        cohort.setDescription(blankToNull(req.description()));
        return toSummary(cohortRepository.save(cohort), 0);
    }

    @Transactional
    public CohortSummaryResponse update(UUID id, CohortRequest req) {
        Cohort cohort = requireCohort(id);
        String name = req.name().trim();
        if (cohortRepository.existsByNameIgnoreCaseAndIdNot(name, id)) {
            throw duplicateName(name);
        }
        cohort.setName(name);
        cohort.setDescription(blankToNull(req.description()));
        return toSummary(cohortRepository.save(cohort), cohortRepository.countMembers(id));
    }

    /** Deletes the group only; enrollments made through it are untouched. */
    @Transactional
    public void delete(UUID id) {
        cohortRepository.delete(requireCohort(id));
    }

    // ── Membership ────────────────────────────────────────────────────────────

    @Transactional
    public AddCohortMembersResponse addMembers(UUID cohortId, AddCohortMembersRequest req) {
        Cohort cohort = requireCohort(cohortId);

        List<String> notFound = new ArrayList<>();
        // Keyed by user id so a student given both by id and by roll number is added once.
        Map<UUID, CohortMemberView> matched = new LinkedHashMap<>();

        if (req.studentIds() != null && !req.studentIds().isEmpty()) {
            Set<UUID> ids = new LinkedHashSet<>(req.studentIds());
            Map<UUID, CohortMemberView> found = cohortRepository.findUsersByIds(ids).stream()
                    .collect(Collectors.toMap(v -> UUID.fromString(v.getStudentId()), Function.identity()));
            for (UUID id : ids) {
                CohortMemberView user = found.get(id);
                if (user == null) notFound.add(id.toString());
                else matched.putIfAbsent(id, user);
            }
        }

        if (req.studentNumbers() != null) {
            // Case-insensitive: pasted lists do not keep the case the register uses.
            Map<String, String> numbers = new LinkedHashMap<>();
            for (String n : req.studentNumbers()) {
                if (n != null && !n.isBlank()) numbers.putIfAbsent(n.trim().toLowerCase(), n.trim());
            }
            if (!numbers.isEmpty()) {
                Map<String, CohortMemberView> found = cohortRepository
                        .findUsersByStudentNumbers(numbers.keySet()).stream()
                        .collect(Collectors.toMap(v -> v.getStudentNumber().toLowerCase(), Function.identity()));
                numbers.forEach((key, original) -> {
                    CohortMemberView user = found.get(key);
                    if (user == null) notFound.add(original);
                    else matched.putIfAbsent(UUID.fromString(user.getStudentId()), user);
                });
            }
        }

        List<String> notStudents = new ArrayList<>();
        int added = 0;
        int alreadyMembers = 0;
        for (var entry : matched.entrySet()) {
            CohortMemberView user = entry.getValue();
            if (!ROLE_STUDENT.equals(user.getRole())) {
                notStudents.add(user.getName() + " (" + user.getRole() + ")");
            } else if (cohortRepository.addMember(cohortId, entry.getKey()) == 1) {
                added++;
            } else {
                alreadyMembers++;
            }
        }

        return new AddCohortMembersResponse(added, alreadyMembers, notFound, notStudents, toDetail(cohort));
    }

    @Transactional
    public CohortDetailResponse removeMember(UUID cohortId, UUID studentId) {
        Cohort cohort = requireCohort(cohortId);
        if (cohortRepository.removeMember(cohortId, studentId) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "That student is not a member of this cohort");
        }
        return toDetail(cohort);
    }

    // ── Enroll the cohort ─────────────────────────────────────────────────────

    /**
     * Enrolls every active member of the cohort as a STUDENT of the offering.
     * Members whose account is not active are skipped here; everything else a
     * student can be skipped for is decided by {@link EnrollmentService#enrollStudents}.
     */
    @Transactional
    public CohortEnrollmentResponse enroll(UUID cohortId, UUID pscId) {
        requireCohort(cohortId);
        List<CohortMemberView> members = cohortRepository.findMembers(cohortId);
        if (members.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This cohort has no members to enroll");
        }

        Map<UUID, String> skipped = new LinkedHashMap<>();
        List<UUID> eligible = new ArrayList<>();
        for (CohortMemberView m : members) {
            UUID id = UUID.fromString(m.getStudentId());
            if (!STATUS_ACTIVE.equals(m.getStatus())) {
                skipped.put(id, "Account is " + m.getStatus().toLowerCase());
            } else {
                eligible.add(id);
            }
        }

        if (!eligible.isEmpty()) {
            for (EnrollmentService.BulkOutcome o : enrollmentService.enrollStudents(pscId, eligible)) {
                if (!o.enrolled()) skipped.put(o.studentId(), o.skipReason());
            }
        } else {
            // Still report a missing offering as such, not as "everyone skipped".
            enrollmentService.requireOfferingExists(pscId);
        }

        List<CohortEnrollmentResponse.Result> results = members.stream()
                .map(m -> {
                    UUID id = UUID.fromString(m.getStudentId());
                    String reason = skipped.get(id);
                    return new CohortEnrollmentResponse.Result(id, m.getName(), m.getStudentNumber(),
                            reason == null ? CohortEnrollmentResponse.ENROLLED : CohortEnrollmentResponse.SKIPPED,
                            reason);
                })
                .toList();
        return new CohortEnrollmentResponse(cohortId, pscId,
                members.size() - skipped.size(), skipped.size(), results);
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private Cohort requireCohort(UUID id) {
        return cohortRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Cohort not found: " + id));
    }

    private static ResponseStatusException duplicateName(String name) {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "A cohort named \"" + name + "\" already exists");
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    private static CohortSummaryResponse toSummary(Cohort c, long memberCount) {
        return new CohortSummaryResponse(c.getId(), c.getName(), c.getDescription(),
                memberCount, c.getCreatedAt(), c.getUpdatedAt());
    }

    private CohortDetailResponse toDetail(Cohort c) {
        List<CohortMemberResponse> members = cohortRepository.findMembers(c.getId()).stream()
                .map(m -> new CohortMemberResponse(UUID.fromString(m.getStudentId()), m.getName(),
                        m.getEmail(), m.getStudentNumber(), m.getStatus()))
                .toList();
        return new CohortDetailResponse(c.getId(), c.getName(), c.getDescription(),
                members, c.getCreatedAt(), c.getUpdatedAt());
    }
}
