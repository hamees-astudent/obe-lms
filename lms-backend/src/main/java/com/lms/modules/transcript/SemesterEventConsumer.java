package com.lms.modules.transcript;

import com.lms.shared.events.SemesterEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Spring Cloud Stream consumer for semester lifecycle events.
 * Bound to topic {@code lms.semester.events} with consumer group
 * {@code ${spring.application.name}-transcript} — isolated from the
 * notifications module's consumer group so each module gets its own copy.
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class SemesterEventConsumer {

    private final TranscriptService transcriptService;

    @Bean
    public Consumer<SemesterEvent> transcriptSemesterHandler() {
        return event -> {
            if (event.getAction() != SemesterEvent.Action.CLOSED) {
                return;
            }
            log.info("Received SEMESTER_CLOSED event: semesterId={} programId={}",
                    event.getSemesterId(), event.getProgramId());
            // Delegate to @Async method so the Kafka thread is released immediately
            transcriptService.generateTranscriptsForSemester(
                    event.getSemesterId(),
                    event.getProgramId(),
                    event.getTriggeredByEmail());
        };
    }
}
