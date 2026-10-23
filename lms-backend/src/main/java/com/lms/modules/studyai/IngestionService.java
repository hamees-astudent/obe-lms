package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.LlmGateway;
import com.lms.infrastructure.nlp.NlpExecutors;
import com.lms.infrastructure.nlp.NlpProperties;
import com.lms.infrastructure.storage.StorageService;
import com.lms.modules.studyai.Chunker.Chunk;
import com.lms.modules.studyai.DocumentTextExtractor.Format;
import com.lms.modules.studyai.DocumentTextExtractor.NoTextException;
import com.lms.modules.studyai.DocumentTextExtractor.Segment;
import com.lms.modules.studyai.IngestionRepository.MaterialRow;
import com.lms.modules.studyai.IngestionRepository.Topic;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

/**
 * Reads a document material and builds its search index: text by page/slide,
 * passages, key phrases and topics.
 *
 * <p>Runs on the NLP ingestion pool, never on a request thread: a large PDF
 * takes seconds to parse. Each step records its state in
 * {@code material_ingestions}, so the teacher sees "Processing", "Indexed" or
 * why it failed against each file.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IngestionService {

    private final IngestionRepository repository;
    private final StorageService storage;
    private final DocumentTextExtractor extractor;
    private final Chunker chunker;
    private final TopicExtractor topicExtractor;
    private final LlmGateway llm;
    private final NlpProperties properties;
    private final NlpExecutors executors;

    /** Queues a material for (re)indexing. */
    public void schedule(UUID materialId) {
        try {
            executors.ingestion().submit(() -> {
                try {
                    ingest(materialId);
                } catch (Exception e) {
                    log.error("Indexing material {} failed unexpectedly", materialId, e);
                    repository.markFinished(materialId, "FAILED", "Indexing failed: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException e) {
            // Left PENDING; the startup sweep or a manual reindex picks it up.
            log.warn("Indexing queue full; material {} left pending", materialId);
            repository.markPending(materialId);
        }
    }

    void ingest(UUID materialId) {
        Optional<MaterialRow> found = repository.findMaterial(materialId);
        if (found.isEmpty()) return;                      // deleted meanwhile
        MaterialRow m = found.get();
        if (!"DOCUMENT".equals(m.type())) return;
        if (m.objectKey() == null || m.objectKey().isBlank()) {
            repository.markFinished(materialId, "FAILED", "This material has no file attached.");
            return;
        }
        Format format = DocumentTextExtractor.detect(m.mimeType(), m.fileName());
        if (format == null) {
            repository.markFinished(materialId, "UNSUPPORTED",
                    "Only PDF, PowerPoint (.pptx) and Word (.docx) files are indexed for the study assistant.");
            return;
        }

        repository.markProcessing(materialId);
        long started = System.currentTimeMillis();
        try {
            byte[] data = download(m.objectKey());
            String sha = sha256(data);
            if (repository.findSha(materialId).filter(sha::equals).isPresent()) {
                repository.markFinished(materialId, "READY", null);  // same file, index still valid
                return;
            }
            List<Segment> segments = extractor.extract(data, format, properties.getIngestion().getMaxPages());
            List<Chunk> chunks = chunker.chunk(segments);
            List<Topic> topics = topicExtractor.extract(chunks, m.title());
            int pages = segments.stream().mapToInt(Segment::number).max().orElse(0);
            repository.replaceIndex(materialId, m.pscId(), chunks, topics, pages, sha);
            log.info("Indexed material {} ({}): {} {}s, {} passages, {} topics in {} ms", materialId, format,
                    pages, format == Format.PPTX ? "slide" : format == Format.PDF ? "page" : "section",
                    chunks.size(), topics.size(), System.currentTimeMillis() - started);
            refineTopicNames(materialId, topics, chunks);
        } catch (NoTextException | IllegalArgumentException e) {
            repository.markFinished(materialId, "FAILED", e.getMessage());
        }
    }

    private byte[] download(String objectKey) {
        long max = properties.getIngestion().getMaxFileBytes();
        try (InputStream in = storage.download(objectKey)) {
            byte[] data = in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, max + 1));
            if (data.length > max) {
                throw new IllegalArgumentException("This file is larger than " + (max / (1024 * 1024))
                        + " MB, the most the study assistant indexes.");
            }
            return data;
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("The file could not be fetched from storage: " + e.getMessage(), e);
        }
    }

    // ── Optional: tidy topic names with the local model ──────────────────────

    record TopicNames(List<TopicName> topics) {}
    record TopicName(int index, String name, String summary) {}

    private static final Map<String, Object> TOPIC_SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("topics", Map.of(
                    "type", "array",
                    "items", Map.of("type", "object",
                            "properties", Map.of(
                                    "index", Map.of("type", "integer"),
                                    "name", Map.of("type", "string"),
                                    "summary", Map.of("type", "string")),
                            "required", List.of("index", "name", "summary")))),
            "required", List.of("topics"));

    /**
     * Headings such as "Lecture 4 – Part 2" make poor topic names. When the
     * local model is up it rewrites each name and adds a one-line summary;
     * the page ranges stay as found. Best effort: on any failure the
     * classical names stand.
     */
    private void refineTopicNames(UUID materialId, List<Topic> topics, List<Chunk> chunks) {
        if (topics.isEmpty() || !llm.available()) return;
        StringBuilder prompt = new StringBuilder("Topics found in a course material, with the text they start with:\n\n");
        for (int i = 0; i < topics.size(); i++) {
            Topic t = topics.get(i);
            String opening = chunks.stream().filter(c -> c.start() >= t.start()).findFirst()
                    .map(c -> c.content().length() > 220 ? c.content().substring(0, 220) : c.content()).orElse("");
            prompt.append(i).append(". ").append(t.name()).append(" — ").append(opening.replace('\n', ' ')).append("\n");
        }
        prompt.append("\nFor every topic give a short, specific topic name (2-6 words) and a one-sentence summary. "
                + "Keep the same index numbers. Use only information shown above.");
        llm.structured("You name the topics of university course material. Output JSON only.",
                        prompt.toString(), TOPIC_SCHEMA, TopicNames.class)
                .ifPresent(result -> {
                    Map<Integer, String[]> names = new HashMap<>();
                    for (TopicName n : result.topics() == null ? List.<TopicName>of() : result.topics()) {
                        if (n.index() < 0 || n.index() >= topics.size() || n.name() == null) continue;
                        String name = n.name().strip();
                        if (name.length() < 3 || name.length() > 120) continue;
                        names.put(n.index(), new String[]{name, n.summary() == null ? null : n.summary().strip()});
                    }
                    repository.renameTopics(materialId, names);
                });
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (Exception e) {
            return null;
        }
    }
}
