package com.lms.modules.notifications;

import com.lms.modules.notifications.NotificationDataRepository.CourseRow;
import com.lms.modules.notifications.NotificationDataRepository.UserRow;
import com.lms.shared.events.AssessmentEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the fan-out that was missing entirely: publishing an assignment, quiz
 * or material told nobody, because the only assessment events the system
 * emitted were about submissions.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

    @Mock private NotificationRepository     notifRepo;
    @Mock private NotificationDataRepository dataRepo;
    @Mock private NotificationEmailService   emailService;

    @InjectMocks private NotificationService service;

    private final UUID pscId        = UUID.randomUUID();
    private final UUID assessmentId = UUID.randomUUID();

    private final UserRow alice = new UserRow(UUID.randomUUID(), "alice@uni.edu", "Alice");
    private final UserRow bilal = new UserRow(UUID.randomUUID(), "bilal@uni.edu", "Bilal");

    @Test
    @DisplayName("a new assignment notifies every actively-enrolled student")
    void assignmentCreatedFansOutToClass() {
        when(dataRepo.findActiveStudentsByPsc(pscId)).thenReturn(List.of(alice, bilal));
        when(dataRepo.findCourseByPsc(pscId))
                .thenReturn(Optional.of(new CourseRow("CS-363", "Operating Systems")));

        service.handleAssessmentEvent(event(AssessmentEvent.Action.ASSIGNMENT_CREATED, "Lab 3"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifRepo).saveAll(saved.capture());

        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue())
                .extracting(Notification::getRecipientId)
                .containsExactlyInAnyOrder(alice.id(), bilal.id());
        assertThat(saved.getValue())
                .allSatisfy(n -> {
                    assertThat(n.getEventType()).isEqualTo("ASSIGNMENT_CREATED");
                    assertThat(n.getReferenceId()).isEqualTo(assessmentId);
                    assertThat(n.getTitle()).contains("CS-363");
                    assertThat(n.getBody()).contains("Lab 3");
                });

        verify(emailService, times(2)).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("a new quiz notifies the class")
    void quizCreatedFansOutToClass() {
        when(dataRepo.findActiveStudentsByPsc(pscId)).thenReturn(List.of(alice));
        when(dataRepo.findCourseByPsc(pscId))
                .thenReturn(Optional.of(new CourseRow("BSCS-501", "Compilers")));

        service.handleAssessmentEvent(event(AssessmentEvent.Action.QUIZ_CREATED, "Midterm"));

        verify(notifRepo).saveAll(any());
        verify(emailService).sendEmail(eq(alice.email()), anyString(), anyString());
    }

    @Test
    @DisplayName("new material notifies the class")
    void materialAddedFansOutToClass() {
        when(dataRepo.findActiveStudentsByPsc(pscId)).thenReturn(List.of(alice, bilal));
        when(dataRepo.findCourseByPsc(pscId))
                .thenReturn(Optional.of(new CourseRow("CS-363", "Operating Systems")));

        service.handleAssessmentEvent(event(AssessmentEvent.Action.MATERIAL_ADDED, "Week 4 slides"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> saved = ArgumentCaptor.forClass(List.class);
        verify(notifRepo).saveAll(saved.capture());
        assertThat(saved.getValue())
                .allSatisfy(n -> assertThat(n.getEventType()).isEqualTo("MATERIAL_ADDED"));
    }

    @Test
    @DisplayName("an offering with no enrolled students notifies nobody")
    void emptyRosterNotifiesNobody() {
        when(dataRepo.findActiveStudentsByPsc(pscId)).thenReturn(List.of());

        service.handleAssessmentEvent(event(AssessmentEvent.Action.QUIZ_CREATED, "Midterm"));

        verify(notifRepo, never()).saveAll(any());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an event without an offering is dropped rather than half-processed")
    void missingPscIsIgnored() {
        AssessmentEvent malformed = AssessmentEvent.builder()
                .action(AssessmentEvent.Action.ASSIGNMENT_CREATED)
                .assessmentTitle("Lab 3")
                .build();

        service.handleAssessmentEvent(malformed);

        verify(notifRepo, never()).saveAll(any());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    private AssessmentEvent event(AssessmentEvent.Action action, String title) {
        return AssessmentEvent.builder()
                .action(action)
                .pscId(pscId)
                .assessmentId(assessmentId)
                .assessmentTitle(title)
                .detail("Due tomorrow")
                .build();
    }
}
