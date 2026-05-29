package com.lms.modules.programs;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.programs.dto.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;

    // ═════════════════════════════════════════════════════════════════════════
    // Admin: Program CRUD
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/programs")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramSummaryResponse createProgram(@Valid @RequestBody CreateProgramRequest req) {
        return programService.createProgram(req);
    }

    @PutMapping("/api/admin/programs/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramDetailResponse updateProgram(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateProgramRequest req) {
        return programService.updateProgram(id, req);
    }

    @PatchMapping("/api/admin/programs/{id}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ProgramSummaryResponse changeProgramStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeProgramStatusRequest req) {
        return programService.changeProgramStatus(id, req);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Admin: PLO management
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/programs/{programId}/plos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public PloResponse createPlo(
            @PathVariable UUID programId,
            @Valid @RequestBody CreatePloRequest req) {
        return programService.createPlo(programId, req);
    }

    @PutMapping("/api/admin/programs/{programId}/plos/{ploId}")
    @PreAuthorize("hasRole('ADMIN')")
    public PloResponse updatePlo(
            @PathVariable UUID programId,
            @PathVariable UUID ploId,
            @Valid @RequestBody UpdatePloRequest req) {
        return programService.updatePlo(programId, ploId, req);
    }

    @DeleteMapping("/api/admin/programs/{programId}/plos/{ploId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deletePlo(
            @PathVariable UUID programId,
            @PathVariable UUID ploId) {
        programService.deletePlo(programId, ploId);
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Admin: Semester lifecycle
    // ═════════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/programs/{programId}/semesters")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public SemesterResponse createSemester(
            @PathVariable UUID programId,
            @Valid @RequestBody CreateSemesterRequest req) {
        return programService.createSemester(programId, req);
    }

    @PutMapping("/api/admin/semesters/{semesterId}")
    @PreAuthorize("hasRole('ADMIN')")
    public SemesterResponse updateSemester(
            @PathVariable UUID semesterId,
            @Valid @RequestBody UpdateSemesterRequest req) {
        return programService.updateSemester(semesterId, req);
    }

    @PostMapping("/api/admin/semesters/{semesterId}/close")
    @PreAuthorize("hasRole('ADMIN')")
    public SemesterResponse closeSemester(
            @PathVariable UUID semesterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return programService.closeSemester(semesterId, principal.getId(), principal.getEmail());
    }

    @PostMapping("/api/admin/semesters/{semesterId}/reopen")
    @PreAuthorize("hasRole('ADMIN')")
    public SemesterResponse reopenSemester(
            @PathVariable UUID semesterId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return programService.reopenSemester(semesterId, principal.getEmail());
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Read-only: accessible to all authenticated users
    // ═════════════════════════════════════════════════════════════════════════

    @GetMapping("/api/programs")
    public Page<ProgramSummaryResponse> listPrograms(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return programService.listPrograms(status, pageable);
    }

    @GetMapping("/api/programs/{id}")
    public ProgramDetailResponse getProgram(@PathVariable UUID id) {
        return programService.getProgramDetail(id);
    }

    @GetMapping("/api/programs/{programId}/plos")
    public List<PloResponse> listPlos(@PathVariable UUID programId) {
        return programService.listPlos(programId);
    }

    @GetMapping("/api/programs/{programId}/semesters")
    public List<SemesterResponse> listSemesters(
            @PathVariable UUID programId,
            @RequestParam(required = false) String status) {
        return programService.listSemesters(programId, status);
    }

    @GetMapping("/api/semesters/{semesterId}")
    public SemesterResponse getSemester(@PathVariable UUID semesterId) {
        return programService.getSemester(semesterId);
    }
}
