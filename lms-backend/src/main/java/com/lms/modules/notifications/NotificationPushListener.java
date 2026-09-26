package com.lms.modules.notifications;

import com.lms.modules.notifications.dto.NotificationResponse;
import jakarta.persistence.PostPersist;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Sends every newly saved {@link Notification} to its recipient's open browser
 * tabs, whichever code path saved it.
 *
 * <p>Delivery waits for the transaction to commit, so a tab is never shown a
 * notification that is then rolled back and missing from its feed.
 *
 * <p>A JPA entity listener; Hibernate obtains it from Spring, which supplies
 * the {@link NotificationStreams}.
 */
public class NotificationPushListener {

    private final NotificationStreams streams;

    public NotificationPushListener(NotificationStreams streams) {
        this.streams = streams;
    }

    @PostPersist
    void saved(Notification n) {
        NotificationResponse response = NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .title(n.getTitle())
                .body(n.getBody())
                .eventType(n.getEventType())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            streams.send(response);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                streams.send(response);
            }
        });
    }
}
