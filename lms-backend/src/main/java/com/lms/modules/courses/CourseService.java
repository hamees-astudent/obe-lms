package com.lms.modules.courses;

import com.lms.modules.courses.dto.*;
import com.lms.shared.CacheNames;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseService {

    private final CourseRepository              courseRepository;
    private final ProgramSemesterCourseRepository pscRepository;
    private final CourseAssistantRepository     assistantRepository;
    private final CloRepository                 cloRepository;
    private final CloPloMappingRepository       mappingRepository;
    private final CourseMaterialRepository      materialRepository;

    // ── Course CRUD ───────────────────────────────────────────────────────────

    @Transactional
    public CourseSummaryResponse createCourse(CreateCourseRequest req) {
        if (courseRepository.existsByCode(req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Course code already exists: " + req.code());
        }
        var course = new Course();
        course.setCode(req.code());
        course.setName(req.name());
        course.setDescription(req.description());
        course.setCreditHours(req.creditHours());
        return toCourseSummary(courseRepository.save(course));
    }

    public Page<CourseSummaryResponse> listCourses(String status, Pageable pageable) {
        if (status != null) {
            return courseRepository.findAllByStatus(status, pageable).map(this::toCourseSummary);
        }
        return courseRepository.findAll(pageable).map(this::toCourseSummary);
    }

    @Cacheable(value = CacheNames.COURSES, key = "#courseId")
    public CourseDetailResponse getCourseDetail(UUID courseId) {
        var course = requireCourse(courseId);
        var clos   = cloRepository.findAllByCourse_IdOrderByOrderIndex(courseId)
                .stream().map(this::toCloResponse).toList();
        return toCourseDetail(course, clos);
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSES, key = "#courseId")
    public CourseDetailResponse updateCourse(UUID courseId, UpdateCourseRequest req) {
        var course = requireCourse(courseId);
        course.setName(req.name());
        course.setDescription(req.description());
        course.setCreditHours(req.creditHours());
        courseRepository.save(course);
        var clos = cloRepository.findAllByCourse_IdOrderByOrderIndex(courseId)
                .stream().map(this::toCloResponse).toList();
        return toCourseDetail(course, clos);
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSES, key = "#courseId")
    public CourseSummaryResponse changeCourseStatus(UUID courseId, ChangeCourseStatusRequest req) {
        var course = requireCourse(courseId);
        course.setStatus(req.status());
        return toCourseSummary(courseRepository.save(course));
    }

    // ── CLO management ────────────────────────────────────────────────────────

    public List<CloResponse> listClos(UUID courseId) {
        requireCourse(courseId);
        return cloRepository.findAllByCourse_IdOrderByOrderIndex(courseId)
                .stream().map(this::toCloResponse).toList();
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSES, key = "#courseId")
    public CloResponse createClo(UUID courseId, CreateCloRequest req) {
        var course = requireCourse(courseId);
        if (cloRepository.existsByCourse_IdAndCode(courseId, req.code())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "CLO code already exists in this course: " + req.code());
        }
        if (cloRepository.existsByCourse_IdAndOrderIndex(courseId, req.orderIndex())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "order_index " + req.orderIndex() + " already used in this course");
        }
        var clo = new Clo();
        clo.setCourse(course);
        clo.setCode(req.code());
        clo.setTitle(req.title());
        clo.setDescription(req.description());
        clo.setOrderIndex(req.orderIndex());
        return toCloResponse(cloRepository.save(clo));
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSES, key = "#courseId")
    public CloResponse updateClo(UUID courseId, UUID cloId, UpdateCloRequest req) {
        var clo = requireClo(cloId, courseId);
        // Uniqueness checks (exclude self)
        cloRepository.findAllByCourse_IdOrderByOrderIndex(courseId).stream()
                .filter(c -> !c.getId().equals(cloId) && c.getCode().equals(req.code()))
                .findFirst().ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "CLO code already used: " + req.code());
                });
        cloRepository.findAllByCourse_IdOrderByOrderIndex(courseId).stream()
                .filter(c -> !c.getId().equals(cloId) && c.getOrderIndex() == req.orderIndex())
                .findFirst().ifPresent(c -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "order_index " + req.orderIndex() + " already used");
                });
        clo.setCode(req.code());
        clo.setTitle(req.title());
        clo.setDescription(req.description());
        clo.setOrderIndex(req.orderIndex());
        return toCloResponse(cloRepository.save(clo));
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSES, key = "#courseId")
    public void deleteClo(UUID courseId, UUID cloId) {
        var clo = requireClo(cloId, courseId);
        cloRepository.delete(clo);
    }

    // ── CLO-PLO mappings ──────────────────────────────────────────────────────

    public List<CloPloMappingResponse> listCloPloMappings(UUID cloId) {
        requireCloById(cloId);
        return mappingRepository.findAllByClo_Id(cloId)
                .stream().map(this::toMappingResponse).toList();
    }

    @Transactional
    public CloPloMappingResponse addCloPloMapping(UUID cloId, CreateCloPloMappingRequest req) {
        var clo = requireCloById(cloId);
        if (mappingRepository.existsByIdCloIdAndIdPloId(cloId, req.ploId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Mapping already exists for CLO " + cloId + " → PLO " + req.ploId());
        }
        var mapping = new CloPloMapping();
        mapping.setId(new CloPloMappingId(cloId, req.ploId()));
        mapping.setClo(clo);
        mapping.setWeight(req.weight());
        return toMappingResponse(mappingRepository.save(mapping));
    }

    @Transactional
    public void removeCloPloMapping(UUID cloId, UUID ploId) {
        var id = new CloPloMappingId(cloId, ploId);
        if (!mappingRepository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Mapping not found for CLO " + cloId + " → PLO " + ploId);
        }
        mappingRepository.deleteById(id);
    }

    // ── PSC Offerings ─────────────────────────────────────────────────────────

    @Transactional
    public OfferingSummaryResponse createOffering(CreateOfferingRequest req) {
        requireCourse(req.courseId());
        if (pscRepository.existsBySemesterIdAndCourse_Id(req.semesterId(), req.courseId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Course is already offered in this semester");
        }
        var psc = new ProgramSemesterCourse();
        psc.setSemesterId(req.semesterId());
        psc.setCourse(requireCourse(req.courseId()));
        psc.setTeacherId(req.teacherId());
        psc.setMaxCapacity(req.maxCapacity());
        return toOfferingSummary(pscRepository.save(psc));
    }

    @Transactional
    public OfferingSummaryResponse updateOffering(UUID pscId, UpdateOfferingRequest req) {
        var psc = requirePsc(pscId);
        psc.setTeacherId(req.teacherId());
        psc.setMaxCapacity(req.maxCapacity());
        return toOfferingSummary(pscRepository.save(psc));
    }

    public OfferingSummaryResponse getOffering(UUID pscId) {
        return toOfferingSummary(requirePsc(pscId));
    }

    public List<OfferingSummaryResponse> listOfferingsBySemester(UUID semesterId) {
        return pscRepository.findAllBySemesterId(semesterId)
                .stream().map(this::toOfferingSummary).toList();
    }

    public List<OfferingSummaryResponse> listOfferingsByCourse(UUID courseId) {
        requireCourse(courseId);
        return pscRepository.findAllByCourse_Id(courseId)
                .stream().map(this::toOfferingSummary).toList();
    }

    public List<OfferingSummaryResponse> listOfferingsByTeacher(UUID teacherId) {
        return pscRepository.findAllByTeacherId(teacherId)
                .stream().map(this::toOfferingSummary).toList();
    }

    // ── Course assistants ─────────────────────────────────────────────────────

    public List<UUID> listAssistants(UUID pscId) {
        requirePsc(pscId);
        return assistantRepository.findAllByPsc_Id(pscId)
                .stream().map(CourseAssistant::getUserId).toList();
    }

    @Transactional
    public void addAssistant(UUID pscId, UUID userId) {
        var psc = requirePsc(pscId);
        if (assistantRepository.existsByIdPscIdAndIdUserId(pscId, userId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "User " + userId + " is already an assistant on this offering");
        }
        assistantRepository.save(CourseAssistant.of(psc, userId));
    }

    @Transactional
    public void removeAssistant(UUID pscId, UUID userId) {
        if (!assistantRepository.existsByIdPscIdAndIdUserId(pscId, userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Assistant not found on this offering");
        }
        assistantRepository.deleteByIdPscIdAndIdUserId(pscId, userId);
    }

    // ── Course materials ──────────────────────────────────────────────────────

    @Cacheable(value = CacheNames.COURSE_MATERIALS, key = "#pscId")
    public List<CourseMaterialResponse> listMaterials(UUID pscId) {
        requirePsc(pscId);
        return materialRepository.findAllByPsc_IdOrderByOrderIndex(pscId)
                .stream().map(this::toMaterialResponse).toList();
    }

    public List<CourseMaterialResponse> listMaterialsByType(UUID pscId, String type) {
        requirePsc(pscId);
        return materialRepository.findAllByPsc_IdAndTypeOrderByOrderIndex(pscId, type)
                .stream().map(this::toMaterialResponse).toList();
    }

    public List<CourseMaterialResponse> listVisibleMaterials(UUID pscId) {
        requirePsc(pscId);
        return materialRepository.findAllByPsc_IdAndVisibleTrueOrderByOrderIndex(pscId)
                .stream().map(this::toMaterialResponse).toList();
    }

    public CourseMaterialResponse getMaterial(UUID id) {
        return toMaterialResponse(requireMaterial(id));
    }

    @Transactional
    @CacheEvict(value = CacheNames.COURSE_MATERIALS, key = "#pscId")
    public CourseMaterialResponse createMaterial(UUID pscId, UUID uploadedBy,
                                                  CreateCourseMaterialRequest req) {
        var psc = requirePsc(pscId);
        var material = new CourseMaterial();
        material.setPsc(psc);
        material.setUploadedBy(uploadedBy);
        material.setType(req.type());
        material.setTitle(req.title());
        material.setDescription(req.description());
        material.setContent(req.content() != null ? req.content() : new java.util.HashMap<>());
        material.setVisible(req.visible());
        material.setOrderIndex(req.orderIndex());
        return toMaterialResponse(materialRepository.save(material));
    }

    @Transactional
    public CourseMaterialResponse updateMaterial(UUID id, UpdateCourseMaterialRequest req) {
        var material = requireMaterial(id);
        material.setTitle(req.title());
        material.setDescription(req.description());
        material.setContent(req.content() != null ? req.content() : new java.util.HashMap<>());
        material.setVisible(req.visible());
        material.setOrderIndex(req.orderIndex());
        // Evict the list cache for this material's psc
        evictMaterialCache(material.getPsc().getId());
        return toMaterialResponse(materialRepository.save(material));
    }

    @Transactional
    public void deleteMaterial(UUID id) {
        var material = requireMaterial(id);
        UUID pscId = material.getPsc().getId();
        materialRepository.delete(material);
        evictMaterialCache(pscId);
    }

    // ── Guards ────────────────────────────────────────────────────────────────

    private Course requireCourse(UUID id) {
        return courseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course not found: " + id));
    }

    private ProgramSemesterCourse requirePsc(UUID id) {
        return pscRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course offering not found: " + id));
    }

    private Clo requireClo(UUID cloId, UUID courseId) {
        var clo = requireCloById(cloId);
        if (!clo.getCourse().getId().equals(courseId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "CLO " + cloId + " does not belong to course " + courseId);
        }
        return clo;
    }

    private Clo requireCloById(UUID cloId) {
        return cloRepository.findById(cloId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "CLO not found: " + cloId));
    }

    private CourseMaterial requireMaterial(UUID id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Course material not found: " + id));
    }

    // ── Cache helper ──────────────────────────────────────────────────────────

    @CacheEvict(value = CacheNames.COURSE_MATERIALS, key = "#pscId")
    public void evictMaterialCache(UUID pscId) {
        // eviction side-effect only
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private CourseSummaryResponse toCourseSummary(Course c) {
        return new CourseSummaryResponse(c.getId(), c.getCode(), c.getName(),
                c.getDescription(), c.getCreditHours(), c.getStatus(), c.getCreatedAt());
    }

    private CourseDetailResponse toCourseDetail(Course c, List<CloResponse> clos) {
        return new CourseDetailResponse(c.getId(), c.getCode(), c.getName(),
                c.getDescription(), c.getCreditHours(), c.getStatus(), c.getCreatedAt(), clos);
    }

    CloResponse toCloResponse(Clo c) {
        return new CloResponse(c.getId(), c.getCourse().getId(), c.getCode(),
                c.getTitle(), c.getDescription(), c.getOrderIndex(), c.getCreatedAt());
    }

    private CloPloMappingResponse toMappingResponse(CloPloMapping m) {
        return new CloPloMappingResponse(m.getId().getCloId(), m.getId().getPloId(),
                m.getWeight(), m.getCreatedAt());
    }

    OfferingSummaryResponse toOfferingSummary(ProgramSemesterCourse p) {
        return new OfferingSummaryResponse(p.getId(), p.getSemesterId(),
                p.getCourse().getId(), p.getCourse().getCode(), p.getCourse().getName(),
                p.getCourse().getCreditHours(), p.getTeacherId(),
                p.getMaxCapacity(), p.getCreatedAt());
    }

    CourseMaterialResponse toMaterialResponse(CourseMaterial m) {
        return new CourseMaterialResponse(m.getId(), m.getPsc().getId(), m.getUploadedBy(),
                m.getType(), m.getTitle(), m.getDescription(), m.getContent(),
                m.isVisible(), m.getOrderIndex(), m.getCreatedAt());
    }
}
