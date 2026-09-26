package com.lms.modules.notifications;

import com.lms.modules.notifications.dto.NotificationResponse;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Open server-sent-event streams, one per signed-in browser tab, so a
 * notification reaches the screen the moment it is saved rather than on the
 * next poll.
 *
 * <p>Streams live in this process's memory. That is enough for the single
 * instance the application runs as; with several instances behind a load
 * balancer a notification would only reach tabs connected to the instance that
 * consumed its Kafka event, and delivery would need a shared channel (e.g.
 * Redis pub/sub). The notification row is saved either way, so nothing is lost
 * — the tab sees it on its next load of the feed.
 *
 * <p>A stream closes itself after {@link #TIMEOUT}; the browser reconnects,
 * presenting its current access token, so a stream never outlives a session by
 * long. A comment line every {@link #HEARTBEAT} keeps proxies from closing an
 * idle connection and finds tabs that went away without saying so.
 */
@Slf4j
@Component
public class NotificationStreams {

    static final Duration TIMEOUT = Duration.ofMinutes(30);
    static final Duration HEARTBEAT = Duration.ofSeconds(25);

    /** Tabs one user may hold open; the oldest stream is closed past this. */
    static final int MAX_STREAMS_PER_USER = 5;

    private final Map<UUID, Set<SseEmitter>> streams = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "lms-sse-heartbeat");
        t.setDaemon(true);
        return t;
    });

    public NotificationStreams() {
        heartbeat.scheduleAtFixedRate(this::sendHeartbeat,
                HEARTBEAT.toSeconds(), HEARTBEAT.toSeconds(), TimeUnit.SECONDS);
    }

    /** Opens a stream for the user; the first event tells the browser it is connected. */
    public SseEmitter open(UUID userId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT.toMillis());
        Set<SseEmitter> mine = streams.computeIfAbsent(userId, k -> new CopyOnWriteArraySet<>());
        mine.add(emitter);
        emitter.onCompletion(() -> remove(userId, emitter));
        emitter.onTimeout(() -> remove(userId, emitter));
        emitter.onError(e -> remove(userId, emitter));

        while (mine.size() > MAX_STREAMS_PER_USER) {
            SseEmitter oldest = mine.iterator().next();
            mine.remove(oldest);
            oldest.complete();
        }

        try {
            emitter.send(SseEmitter.event().name("ready").reconnectTime(5_000).data("connected"));
        } catch (IOException e) {
            remove(userId, emitter);
        }
        return emitter;
    }

    /** Sends one notification to every open stream of its recipient. */
    public void send(NotificationResponse notification) {
        Set<SseEmitter> mine = streams.get(notification.getRecipientId());
        if (mine == null) return;
        for (SseEmitter emitter : mine) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .id(String.valueOf(notification.getId()))
                        .data(notification, MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // The tab went away; the container reports it via onError too.
                remove(notification.getRecipientId(), emitter);
            }
        }
    }

    private void sendHeartbeat() {
        streams.forEach((userId, mine) -> {
            for (SseEmitter emitter : mine) {
                try {
                    emitter.send(SseEmitter.event().comment("keep-alive"));
                } catch (IOException | IllegalStateException e) {
                    remove(userId, emitter);
                }
            }
        });
    }

    private void remove(UUID userId, SseEmitter emitter) {
        streams.computeIfPresent(userId, (k, mine) -> {
            mine.remove(emitter);
            return mine.isEmpty() ? null : mine;
        });
    }

    @PreDestroy
    void shutdown() {
        heartbeat.shutdownNow();
        streams.values().forEach(mine -> mine.forEach(SseEmitter::complete));
        streams.clear();
    }
}
