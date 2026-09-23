package com.lms.modules.enrollment;

import com.lms.infrastructure.cache.CacheEvictor;
import com.lms.infrastructure.messaging.KafkaEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
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
}
