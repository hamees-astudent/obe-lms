package com.lms.modules.courses;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.courses.dto.*;
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
public class CourseController {

    private final CourseService courseService;

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: Course catalog CRUD
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/courses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CourseSummaryResponse createCourse(@Valid @RequestBody CreateCourseRequest req) {
        return courseService.createCourse(req);
    }

    @PutMapping("/api/admin/courses/{courseId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseDetailResponse updateCourse(
            @PathVariable UUID courseId,
            @Valid @RequestBody UpdateCourseRequest req) {
        return courseService.updateCourse(courseId, req);
    }

    @PatchMapping("/api/admin/courses/{courseId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public CourseSummaryResponse changeCourseStatus(
            @PathVariable UUID courseId,
            @Valid @RequestBody ChangeCourseStatusRequest req) {
        return courseService.changeCourseStatus(courseId, req);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: CLO management
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/courses/{courseId}/clos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CloResponse createClo(
            @PathVariable UUID courseId,
            @Valid @RequestBody CreateCloRequest req) {
        return courseService.createClo(courseId, req);
    }

    @PutMapping("/api/admin/courses/{courseId}/clos/{cloId}")
    @PreAuthorize("hasRole('ADMIN')")
    public CloResponse updateClo(
            @PathVariable UUID courseId,
            @PathVariable UUID cloId,
            @Valid @RequestBody UpdateCloRequest req) {
        return courseService.updateClo(courseId, cloId, req);
    }

    @DeleteMapping("/api/admin/courses/{courseId}/clos/{cloId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void deleteClo(
            @PathVariable UUID courseId,
            @PathVariable UUID cloId) {
        courseService.deleteClo(courseId, cloId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: CLO → PLO mappings
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/clos/{cloId}/plo-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public CloPloMappingResponse addCloPloMapping(
            @PathVariable UUID cloId,
            @Valid @RequestBody CreateCloPloMappingRequest req) {
        return courseService.addCloPloMapping(cloId, req);
    }

    @DeleteMapping("/api/admin/clos/{cloId}/plo-mappings/{ploId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeCloPloMapping(
            @PathVariable UUID cloId,
            @PathVariable UUID ploId) {
        courseService.removeCloPloMapping(cloId, ploId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: PSC Offerings
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/offerings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public OfferingSummaryResponse createOffering(
            @Valid @RequestBody CreateOfferingRequest req) {
        return courseService.createOffering(req);
    }

    @PutMapping("/api/admin/offerings/{pscId}")
    @PreAuthorize("hasRole('ADMIN')")
    public OfferingSummaryResponse updateOffering(
            @PathVariable UUID pscId,
            @Valid @RequestBody UpdateOfferingRequest req) {
        return courseService.updateOffering(pscId, req);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Admin: Course assistants
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/admin/offerings/{pscId}/assistants")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void addAssistant(
            @PathVariable UUID pscId,
            @RequestParam UUID userId) {
        courseService.addAssistant(pscId, userId);
    }

    @DeleteMapping("/api/admin/offerings/{pscId}/assistants/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    public void removeAssistant(
            @PathVariable UUID pscId,
            @PathVariable UUID userId) {
        courseService.removeAssistant(pscId, userId);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Teacher / Assistant: Course materials
    // ═══════════════════════════════════════════════════════════════════════

    @PostMapping("/api/offerings/{pscId}/materials")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public CourseMaterialResponse createMaterial(
            @PathVariable UUID pscId,
            @Valid @RequestBody CreateCourseMaterialRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return courseService.createMaterial(pscId, principal.getId(), req);
    }

    @PutMapping("/api/materials/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public CourseMaterialResponse updateMaterial(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateCourseMaterialRequest req) {
        return courseService.updateMaterial(id, req);
    }

    @DeleteMapping("/api/materials/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public void deleteMaterial(@PathVariable UUID id) {
        courseService.deleteMaterial(id);
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Read-only: accessible to all authenticated users
    // ═══════════════════════════════════════════════════════════════════════

    @GetMapping("/api/courses")
    public Page<CourseSummaryResponse> listCourses(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "name") Pageable pageable) {
        return courseService.listCourses(status, pageable);
    }

    @GetMapping("/api/courses/{courseId}")
    public CourseDetailResponse getCourse(@PathVariable UUID courseId) {
        return courseService.getCourseDetail(courseId);
    }

    @GetMapping("/api/courses/{courseId}/clos")
    public List<CloResponse> listClos(@PathVariable UUID courseId) {
        return courseService.listClos(courseId);
    }

    @GetMapping("/api/clos/{cloId}/plo-mappings")
    public List<CloPloMappingResponse> listCloPloMappings(@PathVariable UUID cloId) {
        return courseService.listCloPloMappings(cloId);
    }

    @GetMapping("/api/semesters/{semesterId}/offerings")
    public List<OfferingSummaryResponse> listOfferingsBySemester(
            @PathVariable UUID semesterId) {
        return courseService.listOfferingsBySemester(semesterId);
    }

    @GetMapping("/api/courses/{courseId}/offerings")
    public List<OfferingSummaryResponse> listOfferingsByCourse(
            @PathVariable UUID courseId) {
        return courseService.listOfferingsByCourse(courseId);
    }

    @GetMapping("/api/offerings/{pscId}")
    public OfferingSummaryResponse getOffering(@PathVariable UUID pscId) {
        return courseService.getOffering(pscId);
    }

    @GetMapping("/api/offerings/{pscId}/assistants")
    public List<UUID> listAssistants(@PathVariable UUID pscId) {
        return courseService.listAssistants(pscId);
    }

    @GetMapping("/api/offerings/{pscId}/materials")
    public List<CourseMaterialResponse> listMaterials(
            @PathVariable UUID pscId,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "false") boolean visibleOnly) {
        if (type != null) {
            return courseService.listMaterialsByType(pscId, type);
        }
        if (visibleOnly) {
            return courseService.listVisibleMaterials(pscId);
        }
        return courseService.listMaterials(pscId);
    }

    @GetMapping("/api/materials/{id}")
    public CourseMaterialResponse getMaterial(@PathVariable UUID id) {
        return courseService.getMaterial(id);
    }
}
