package com.lms.modules.courses;

import com.lms.infrastructure.messaging.KafkaEventPublisher;
import com.lms.shared.OfferingStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.lms.modules.courses.dto.UpdateOfferingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyIterable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CourseServiceTest {

    @Mock private CourseRepository                courseRepository;
    @Mock private ProgramSemesterCourseRepository pscRepository;
    @Mock private CourseAssistantRepository       assistantRepository;
    @Mock private CloRepository                   cloRepository;
    @Mock private CloPloMappingRepository         mappingRepository;
    @Mock private CourseMaterialRepository        materialRepository;
    @Mock private MaterialCloMappingRepository    materialCloMappingRepository;
    @Mock private KafkaEventPublisher             kafkaEventPublisher;
    @Mock private OfferingStaff                   offeringStaff;

    private CourseService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new CourseService(courseRepository, pscRepository, assistantRepository,
                cloRepository, mappingRepository, materialRepository,
                materialCloMappingRepository, kafkaEventPublisher, offeringStaff);
    }

    @Test
    @DisplayName("teaching offerings carry their semester and program, sorted by course code")
    void teachingOfferingsAreEnrichedAndSorted() {
        var os = offering("CS-363", "Operating Systems");
        var ds = offering("CS-201", "Data Structures");
        when(pscRepository.findTeachingOfferings(userId)).thenReturn(List.of(
                view(os.getId(), "Fall 2026", "BSCS"),
                view(ds.getId(), "Fall 2026 (Evening)", "BSSE")));
        when(pscRepository.findAllById(anyIterable())).thenReturn(List.of(os, ds));

        var result = service.listTeachingOfferings(userId);

        assertThat(result).extracting("courseCode").containsExactly("CS-201", "CS-363");
        assertThat(result.get(0).semesterName()).isEqualTo("Fall 2026 (Evening)");
        assertThat(result.get(0).programName()).isEqualTo("BSSE");
        assertThat(result.get(1).semesterName()).isEqualTo("Fall 2026");
    }

    @Test
    @DisplayName("a user who teaches nothing gets an empty list")
    void noTeachingOfferings() {
        when(pscRepository.findTeachingOfferings(userId)).thenReturn(List.of());
        when(pscRepository.findAllById(Set.of())).thenReturn(List.of());

        assertThat(service.listTeachingOfferings(userId)).isEmpty();
    }

    @Test
    @DisplayName("an active course member cannot be made the teacher of record")
    void memberCannotBecomeTeacherOfRecord() {
        var psc = offering("CS-363", "Operating Systems");
        var memberId = UUID.randomUUID();
        when(pscRepository.findById(psc.getId())).thenReturn(Optional.of(psc));
        when(pscRepository.isActiveMember(psc.getId(), memberId)).thenReturn(true);

        assertThatThrownBy(() -> service.updateOffering(psc.getId(),
                new UpdateOfferingRequest(memberId, 40)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(pscRepository, never()).save(any());
    }

    @Test
    @DisplayName("keeping the same teacher while editing capacity is not a clash")
    void sameTeacherIsAllowed() {
        var psc = offering("CS-363", "Operating Systems");
        when(pscRepository.findById(psc.getId())).thenReturn(Optional.of(psc));
        when(pscRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var result = service.updateOffering(psc.getId(), new UpdateOfferingRequest(psc.getTeacherId(), 60));

        assertThat(result.maxCapacity()).isEqualTo(60);
        verify(pscRepository, never()).isActiveMember(any(), any());
    }

    private static ProgramSemesterCourse offering(String code, String name) {
        var course = new Course();
        ReflectionTestUtils.setField(course, "id", UUID.randomUUID());
        course.setCode(code);
        course.setName(name);
        course.setCreditHours(3);

        var psc = new ProgramSemesterCourse();
        ReflectionTestUtils.setField(psc, "id", UUID.randomUUID());
        psc.setCourse(course);
        psc.setSemesterId(UUID.randomUUID());
        psc.setTeacherId(UUID.randomUUID());
        return psc;
    }

    private static TeachingOfferingView view(UUID pscId, String semester, String program) {
        return new TeachingOfferingView() {
            public String getPscId()        { return pscId.toString(); }
            public String getSemesterName() { return semester; }
            public String getProgramName()  { return program; }
        };
    }
}
