package com.lms.modules.enrollment;

import com.lms.modules.enrollment.dto.AddCohortMembersRequest;
import com.lms.modules.enrollment.dto.AddCohortMembersResponse;
import com.lms.modules.enrollment.dto.CohortDetailResponse;
import com.lms.modules.enrollment.dto.CohortEnrollmentResponse;
import com.lms.modules.enrollment.dto.CohortRequest;
import com.lms.modules.enrollment.dto.CohortSummaryResponse;
import com.lms.modules.enrollment.dto.EnrollCohortRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Admin-only: manage cohorts and enroll a whole cohort in an offering. */
@RestController
@RequestMapping("/api/admin/cohorts")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CohortController {

    private final CohortService cohortService;

    @GetMapping
    public List<CohortSummaryResponse> list() {
        return cohortService.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CohortSummaryResponse create(@Valid @RequestBody CohortRequest req) {
        return cohortService.create(req);
    }

    @GetMapping("/{id}")
    public CohortDetailResponse get(@PathVariable UUID id) {
        return cohortService.get(id);
    }

    @PutMapping("/{id}")
    public CohortSummaryResponse update(@PathVariable UUID id, @Valid @RequestBody CohortRequest req) {
        return cohortService.update(id, req);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        cohortService.delete(id);
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @PostMapping("/{id}/members")
    public AddCohortMembersResponse addMembers(
            @PathVariable UUID id,
            @Valid @RequestBody AddCohortMembersRequest req) {
        return cohortService.addMembers(id, req);
    }

    @DeleteMapping("/{id}/members/{studentId}")
    public CohortDetailResponse removeMember(@PathVariable UUID id, @PathVariable UUID studentId) {
        return cohortService.removeMember(id, studentId);
    }

    // ── Enroll ────────────────────────────────────────────────────────────────

    @PostMapping("/{id}/enrollments")
    public CohortEnrollmentResponse enroll(
            @PathVariable UUID id,
            @Valid @RequestBody EnrollCohortRequest req) {
        return cohortService.enroll(id, req.pscId());
    }
}
