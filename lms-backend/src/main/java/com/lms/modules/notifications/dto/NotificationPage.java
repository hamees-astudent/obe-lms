package com.lms.modules.notifications.dto;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class NotificationPage {
    List<NotificationResponse> content;
    int page;
    int size;
    long totalElements;
    int  totalPages;
    long unreadCount;
}
