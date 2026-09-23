package com.lms.infrastructure.messaging;

import com.lms.shared.events.AssessmentEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.stream.function.StreamBridge;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Publishing must never hold up, or fail, the request that raised the event:
 * it waits for the commit and runs on its own executor.
 */
class KafkaEventPublisherTest {

    private final StreamBridge bridge = mock(StreamBridge.class);
    /** Runs tasks only when told to, so the test controls "later". */
    private final List<Runnable> queued = new ArrayList<>();
    private final Executor deferred = queued::add;

    @BeforeEach
    void accept() {
        when(bridge.send(anyString(), any())).thenReturn(true);
    }

    @AfterEach
    void clearTransaction() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    @DisplayName("nothing is sent on the caller's thread")
    void sendsOffTheCallerThread() {
        var event = event();
        new KafkaEventPublisher(bridge, deferred).publishAssessmentEvent(event);

        verify(bridge, never()).send(anyString(), any());
        queued.forEach(Runnable::run);
        verify(bridge).send(KafkaTopics.TOPIC_ASSESSMENT, event);
    }

    @Test
    @DisplayName("inside a transaction, the event waits for the commit")
    void waitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        new KafkaEventPublisher(bridge, Runnable::run).publishAssessmentEvent(event());

        verify(bridge, never()).send(anyString(), any());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(TransactionSynchronization::afterCommit);
        verify(bridge).send(anyString(), any());
    }

    @Test
    @DisplayName("a rolled-back transaction publishes nothing")
    void nothingOnRollback() {
        TransactionSynchronizationManager.initSynchronization();
        new KafkaEventPublisher(bridge, Runnable::run).publishAssessmentEvent(event());

        TransactionSynchronizationManager.getSynchronizations()
                .forEach(s -> s.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verify(bridge, never()).send(anyString(), any());
    }

    @Test
    @DisplayName("a full queue drops the event instead of failing the caller")
    void fullQueueDoesNotThrow() {
        Executor full = task -> { throw new RejectedExecutionException("full"); };

        assertThatCode(() -> new KafkaEventPublisher(bridge, full).publishAssessmentEvent(event()))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a broker failure is logged, not thrown")
    void brokerFailureDoesNotThrow() {
        when(bridge.send(anyString(), any())).thenThrow(new IllegalStateException("broker down"));

        assertThatCode(() -> new KafkaEventPublisher(bridge, Runnable::run).publishAssessmentEvent(event()))
                .doesNotThrowAnyException();
    }

    private static AssessmentEvent event() {
        return AssessmentEvent.builder().action(AssessmentEvent.Action.ASSIGNMENT_SUBMITTED).build();
    }
}
