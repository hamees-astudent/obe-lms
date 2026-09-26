package com.lms.modules.enrollment;

import com.lms.infrastructure.cache.CacheEvictor;
import com.lms.infrastructure.messaging.KafkaEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Staff can be assigned on the offering or as course members. Both stay, but
 * one person must not be the teacher of record and a course member at once.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EnrollmentServiceTest {

    @Mock private EnrollmentRepository enrollmentRepository;
    @Mock private KafkaEventPublisher  kafkaEventPublisher;
    @Mock private CacheEvictor         cacheEvictor;

    private EnrollmentService service;

    private final UUID pscId     = UUID.randomUUID();
    private final UUID teacherId = UUID.randomUUID();
    private final UUID otherId   = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new EnrollmentService(enrollmentRepository, kafkaEventPublisher, cacheEvictor);
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(0));
        when(enrollmentRepository.findTeacherIdByPscId(pscId)).thenReturn(Optional.of(teacherId.toString()));
        when(enrollmentRepository.findByPscIdAndStudentId(any(), any())).thenReturn(Optional.empty());
        when(enrollmentRepository.findEventContext(any(), any())).thenReturn(Optional.empty());
        when(enrollmentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    @DisplayName("the teacher of record cannot also be added as a course member")
    void teacherOfRecordCannotBecomeMember() {
        assertThatThrownBy(() -> service.enroll(pscId, teacherId, "TEACHER"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(enrollmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("another teacher can still be added as a co-teacher")
    void coTeacherCanBeAdded() {
        var result = service.enroll(pscId, otherId, "TEACHER");

        assertThat(result.studentId()).isEqualTo(otherId);
        assertThat(result.courseRole()).isEqualTo("TEACHER");
    }

    // ── Capacity is a limit on students ──────────────────────────────────────

    @Test
    @DisplayName("a full class refuses another student")
    void fullClassRefusesStudent() {
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(30));
        when(enrollmentRepository.countByPscIdAndStatusAndCourseRole(pscId, "ACTIVE", "STUDENT"))
                .thenReturn(30L);

        assertThatThrownBy(() -> service.enroll(pscId, otherId, "STUDENT"))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    @DisplayName("staff can still be added when the class is full of students")
    void staffIgnoreCapacity() {
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(30));
        when(enrollmentRepository.countByPscIdAndStatusAndCourseRole(pscId, "ACTIVE", "STUDENT"))
                .thenReturn(30L);

        assertThat(service.enroll(pscId, otherId, "ASSISTANT").courseRole()).isEqualTo("ASSISTANT");
    }

    @Test
    @DisplayName("staff memberships do not use up student places")
    void onlyStudentsCount() {
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(30));
        when(enrollmentRepository.countByPscIdAndStatusAndCourseRole(pscId, "ACTIVE", "STUDENT"))
                .thenReturn(29L);
        // 29 students + staff would have been "full" when every member counted
        when(enrollmentRepository.countByPscIdAndStatus(pscId, "ACTIVE")).thenReturn(32L);

        assertThat(service.enroll(pscId, otherId, "STUDENT").courseRole()).isEqualTo("STUDENT");
    }

    // ── Bulk enroll (cohorts) ────────────────────────────────────────────────

    private Enrollment existing(UUID studentId, String status, String courseRole) {
        Enrollment e = new Enrollment();
        e.setPscId(pscId);
        e.setStudentId(studentId);
        e.setStatus(status);
        e.setCourseRole(courseRole);
        if (Enrollment.STATUS_DROPPED.equals(status)) e.setDroppedAt(java.time.LocalDateTime.now());
        return e;
    }

    @SuppressWarnings("unchecked")
    private List<Enrollment> savedBatch() {
        ArgumentCaptor<List<Enrollment>> captor = ArgumentCaptor.forClass(List.class);
        verify(enrollmentRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("a batch enrolls new students and skips those who cannot be enrolled")
    void bulkEnrollSkipsIneligible() {
        UUID fresh = UUID.randomUUID();
        UUID active = UUID.randomUUID();
        UUID completed = UUID.randomUUID();
        UUID dropped = UUID.randomUUID();
        when(enrollmentRepository.findAllByPscId(pscId)).thenReturn(List.of(
                existing(active, Enrollment.STATUS_ACTIVE, "STUDENT"),
                existing(completed, Enrollment.STATUS_COMPLETED, "STUDENT"),
                existing(dropped, Enrollment.STATUS_DROPPED, "STUDENT")));
        when(enrollmentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var outcomes = service.enrollStudents(pscId, List.of(fresh, active, completed, dropped, teacherId));

        assertThat(outcomes).extracting(EnrollmentService.BulkOutcome::enrolled)
                .containsExactly(true, false, false, true, false);
        List<Enrollment> saved = savedBatch();
        assertThat(saved).extracting(Enrollment::getStudentId).containsExactly(fresh, dropped);
        Enrollment reactivated = saved.get(1);
        assertThat(reactivated.getStatus()).isEqualTo(Enrollment.STATUS_ACTIVE);
        assertThat(reactivated.getDroppedAt()).isNull();
        verify(cacheEvictor, times(2)).evict(any(), any());
    }

    @Test
    @DisplayName("a batch that does not fit refuses outright instead of filling up in list order")
    void bulkEnrollRefusesOverCapacity() {
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(30));
        when(enrollmentRepository.countByPscIdAndStatusAndCourseRole(pscId, "ACTIVE", "STUDENT"))
                .thenReturn(29L);
        when(enrollmentRepository.findAllByPscId(pscId)).thenReturn(List.of());

        assertThatThrownBy(() -> service.enrollStudents(pscId, List.of(UUID.randomUUID(), UUID.randomUUID())))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.CONFLICT);
        verify(enrollmentRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("students already in the class do not count against the batch's places")
    void bulkEnrollOnlyCountsNewPlaces() {
        UUID active = UUID.randomUUID();
        when(enrollmentRepository.findMaxCapacityByPscId(pscId)).thenReturn(Optional.of(30));
        when(enrollmentRepository.countByPscIdAndStatusAndCourseRole(pscId, "ACTIVE", "STUDENT"))
                .thenReturn(29L);
        when(enrollmentRepository.findAllByPscId(pscId))
                .thenReturn(List.of(existing(active, Enrollment.STATUS_ACTIVE, "STUDENT")));
        when(enrollmentRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

        var outcomes = service.enrollStudents(pscId, List.of(active, otherId));

        assertThat(outcomes).extracting(EnrollmentService.BulkOutcome::enrolled).containsExactly(false, true);
    }

    @Test
    @DisplayName("a batch for an unknown offering is a 404")
    void bulkEnrollUnknownOffering() {
        UUID unknown = UUID.randomUUID();
        when(enrollmentRepository.findMaxCapacityByPscId(unknown)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.enrollStudents(unknown, List.of(otherId)))
                .isInstanceOf(ResponseStatusException.class)
                .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
