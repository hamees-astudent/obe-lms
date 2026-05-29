package com.lms.modules.notifications;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findAllByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(
            UUID recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    @Modifying
    @Query("""
            UPDATE Notification n
            SET    n.isRead = TRUE, n.readAt = CURRENT_TIMESTAMP
            WHERE  n.recipientId = :recipientId AND n.isRead = FALSE
            """)
    int markAllReadByRecipientId(@Param("recipientId") UUID recipientId);
}
