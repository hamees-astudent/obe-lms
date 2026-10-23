package com.lms.modules.studyai;

import com.lms.shared.events.MaterialChangedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Starts indexing when a teacher adds or edits a document, once the material
 * row is committed — indexing earlier could read a row that then rolls back.
 *
 * <p>The listener only queues the work and returns; the indexing itself runs
 * on the NLP pool. Anything left pending or half-done by a restart is picked
 * up again on startup.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class MaterialIngestionListener {

    private final IngestionService ingestion;
    private final IngestionRepository repository;

    @TransactionalEventListener
    void on(MaterialChangedEvent event) {
        if ("DOCUMENT".equals(event.type())) {
            ingestion.schedule(event.materialId());
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    void resumeUnfinished() {
        try {
            var pending = repository.stuck();
            if (!pending.isEmpty()) log.info("Resuming indexing of {} material(s)", pending.size());
            pending.forEach(ingestion::schedule);
        } catch (Exception e) {
            log.warn("Could not resume pending indexing: {}", e.getMessage());
        }
    }
}
