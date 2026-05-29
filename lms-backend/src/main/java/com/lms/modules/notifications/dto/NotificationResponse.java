package com.lms.modules.notifications.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;
import java.util.UUID;

@Value
@Builder
public class NotificationResponse {
    UUID          id;
    UUID          recipientId;
    String        title;
    String        body;
    String        eventType;
    String        referenceType;
    UUID          referenceId;
    boolean       isRead;
    LocalDateTime readAt;
    LocalDateTime createdAt;
}
