package com.lms.infrastructure.messaging;

import com.lms.shared.events.AssessmentEvent;
import com.lms.shared.events.AttendanceAlertEvent;
import com.lms.shared.events.EnrollmentEvent;
import com.lms.shared.events.SemesterEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

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
public class KafkaEventPublisher {

    private final StreamBridge streamBridge;
    private final Executor     executor;

    public KafkaEventPublisher(StreamBridge streamBridge,
                               @Qualifier(KafkaConfig.EVENT_PUBLISH_EXECUTOR) Executor executor) {
        this.streamBridge = streamBridge;
        this.executor = executor;
    }

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
     * Same as {@link #publishAssessmentEvent}: every publish now waits for the
     * surrounding transaction to commit. Kept so existing callers still read
     * as intended.
     */
    public void publishAssessmentEventAfterCommit(AssessmentEvent event) {
        publishAssessmentEvent(event);
    }

    public void publishSemesterEvent(SemesterEvent event) {
        send(KafkaTopics.TOPIC_SEMESTER, event, event.getEventId());
    }

    // ── Internal ──────────────────────────────────────────────────────────────

    /**
     * Hands the event to the publish executor once the surrounding transaction
     * commits (immediately when there is none).
     *
     * <p>Sending used to happen on the request thread, inside the transaction.
     * With the broker down or slow the Kafka producer blocks for up to
     * {@code max.block.ms} waiting for metadata, so a student's submission hung
     * past the client's 30 s timeout and looked failed although it was saved;
     * and an event published before a rollback announced work that never
     * happened. Now the request returns as soon as its data is committed.
     */
    private void send(String topic, Object payload, Object eventId) {
        Runnable publish = () -> sendNow(topic, payload, eventId);
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            dispatch(publish, topic, eventId);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        dispatch(publish, topic, eventId);
                    }
                });
    }

    private void dispatch(Runnable publish, String topic, Object eventId) {
        try {
            executor.execute(publish);
        } catch (RejectedExecutionException ex) {
            // Queue full: drop rather than block the caller. Events are
            // notifications; the committed write stands either way.
            log.error("Event publish queue full; dropped event to topic '{}' (eventId={})",
                    topic, eventId);
        }
    }

    /**
     * Publishes best-effort, on the publish executor.
     *
     * <p>These events are notifications about work that has already been
     * committed — an unreachable broker must not turn a teacher's saved
     * attendance mark into a 500. A failure is logged loudly; the write stands
     * either way.
     */
    private void sendNow(String topic, Object payload, Object eventId) {
        log.debug("Publishing event to topic '{}': eventId={}", topic, eventId);
        try {
            boolean accepted = streamBridge.send(topic, payload);
            if (!accepted) {
                log.error("StreamBridge rejected event to topic '{}': eventId={}", topic, eventId);
            }
        } catch (RuntimeException ex) {
            log.error("Failed to publish event to topic '{}' (eventId={}): {}",
                    topic, eventId, ex.getMessage());
        }
    }
}
