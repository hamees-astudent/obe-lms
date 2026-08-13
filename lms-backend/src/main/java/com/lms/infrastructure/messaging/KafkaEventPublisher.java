package com.lms.infrastructure.messaging;

import com.lms.shared.events.AssessmentEvent;
import com.lms.shared.events.AttendanceAlertEvent;
import com.lms.shared.events.EnrollmentEvent;
import com.lms.shared.events.SemesterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Thin wrapper around {@link StreamBridge} for publishing domain events to Kafka.
 *
 * <p>Service-layer code should inject this bean rather than using
 * {@link StreamBridge} directly, which keeps topic names centralised and makes
 * the publish calls easy to stub in unit tests.
 *
 * <pre>{@code
 *   // In an @Service:
 *   kafkaEventPublisher.publishEnrollmentEvent(
 *       EnrollmentEvent.builder()
 *           .action(EnrollmentEvent.Action.ENROLLED)
 *           .studentId(student.getId())
 *           ...
 *           .build());
 * }</pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KafkaEventPublisher {

    private final StreamBridge streamBridge;

    // ── Public publish methods ────────────────────────────────────────────────

    public void publishEnrollmentEvent(EnrollmentEvent event) {
        send(KafkaTopics.TOPIC_ENROLLMENT, event, event.getEventId());
    }

    public void publishAttendanceAlertEvent(AttendanceAlertEvent event) {
        send(KafkaTopics.TOPIC_ATTENDANCE, event, event.getEventId());
    }

    public void publishAssessmentEvent(AssessmentEvent event) {
        send(KafkaTopics.TOPIC_ASSESSMENT, event, event.getEventId());
    }

    /**
     * Publishes only once the surrounding transaction commits.
     *
     * <p>Use for events announcing that something now exists. Publishing inline
     * can announce a quiz whose transaction then rolls back — the class gets
     * told about work they will never see, and the notification cannot be
     * recalled. Outside a transaction this behaves like an immediate publish.
     */
    public void publishAssessmentEventAfterCommit(AssessmentEvent event) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishAssessmentEvent(event);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        publishAssessmentEvent(event);
                    }
                });
    }

    public void publishSemesterEvent(SemesterEvent event) {
        send(KafkaTopics.TOPIC_SEMESTER, event, event.getEventId());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    private void send(String topic, Object payload, Object eventId) {
        log.debug("Publishing event to topic '{}': eventId={}", topic, eventId);
        boolean accepted = streamBridge.send(topic, payload);
        if (!accepted) {
            log.error("StreamBridge rejected event to topic '{}': eventId={}", topic, eventId);
        }
    }
}
