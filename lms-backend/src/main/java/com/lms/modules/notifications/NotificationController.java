package com.lms.modules.notifications;

import com.lms.modules.notifications.dto.NotificationPage;
import com.lms.modules.notifications.dto.NotificationResponse;
import lombok.RequiredArgsConstructor;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationStreams notificationStreams;

    /**
     * Server-sent events: each new notification for the current user, as it is
     * saved. Events are named {@code notification} and carry a
     * {@link NotificationResponse}; the first event, {@code ready}, confirms the
     * connection. The browser reconnects when the stream ends.
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(Authentication auth, HttpServletResponse response) {
        UUID userId = notificationService.resolveUserId(auth.getName());
        // Proxies such as nginx buffer responses by default, which would hold events back.
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader("Cache-Control", "no-cache");
        return notificationStreams.open(userId);
    }

    /**
     * Get the current user's notification feed.
     *
     * @param unreadOnly if true, returns only unread notifications
     * @param page       0-based page number (default 0)
     * @param size       page size (default 20, max 100)
     */
    @GetMapping
    public NotificationPage listNotifications(
            Authentication auth,
            @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        size = Math.min(size, 100);
        UUID userId = notificationService.resolveUserId(auth.getName());
        return notificationService.listNotifications(userId, unreadOnly, page, size);
    }

    /**
     * Get the count of unread notifications for the current user.
     * Lightweight endpoint for badge display.
     */
    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount(Authentication auth) {
        UUID userId = notificationService.resolveUserId(auth.getName());
        return Map.of("unreadCount", notificationService.getUnreadCount(userId));
    }

    /**
     * Mark a single notification as read.
     * Users can only mark their own notifications.
     */
    @PutMapping("/{id}/read")
    public NotificationResponse markAsRead(
            @PathVariable UUID id,
            Authentication auth) {
        UUID userId = notificationService.resolveUserId(auth.getName());
        return notificationService.markAsRead(id, userId);
    }

    /**
     * Mark all of the current user's unread notifications as read.
     * Returns the number of notifications updated.
     */
    @PutMapping("/read-all")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, Integer> markAllAsRead(Authentication auth) {
        UUID userId = notificationService.resolveUserId(auth.getName());
        int updated = notificationService.markAllAsRead(userId);
        return Map.of("updated", updated);
    }
}
