package com.lms.modules.exams;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.ObjectMappers;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.lms.modules.exams.dto.ExtractedMarksSheet;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Reads an exam copy's marks table with a Claude vision model.
 *
 * <p>Handwriting is why this is a vision model rather than classical OCR:
 * teachers write marks by hand, cross values out, and lay tables out
 * differently on every paper. The request is constrained to
 * {@link ExtractedMarksSheet} via structured outputs, so the model returns
 * typed JSON and there is no free-text parsing to go wrong.
 *
 * <p>The bean exists only when an API key is configured — see
 * {@link ExamProperties}. Without it the exams module falls back to manual mark
 * entry rather than failing to start.
 */
@Slf4j
@Component
@ConditionalOnProperty(prefix = "app.exams.extraction", name = "enabled", havingValue = "true", matchIfMissing = true)
public class ClaudeMarksSheetExtractor implements MarksSheetExtractor {

    /**
     * Full request/response traffic with Claude, for developers debugging a bad
     * read. logback-spring.xml routes this logger to its own file (not the
     * console); set its level to OFF to stop writing it. The file holds student
     * names and roll numbers read off the page, so it is not for sharing.
     */
    private static final Logger traffic = LoggerFactory.getLogger("com.lms.modules.exams.ClaudeTraffic");

    private final ExamProperties  properties;
    private final AnthropicClient client;

    public ClaudeMarksSheetExtractor(ExamProperties properties) {
        this.properties = properties;
        this.client = properties.getApiKey().isBlank()
                ? null
                : AnthropicOkHttpClient.builder()
                        .apiKey(properties.getApiKey())
                        .timeout(Duration.ofSeconds(properties.getTimeoutSeconds()))
                        .build();
        if (this.client == null) {
            log.warn("Exam marks extraction is disabled: no app.exams.extraction.api-key configured. "
                    + "Teachers can still record exam marks manually.");
        }
    }

    /** Whether extraction can actually run; false when no API key was supplied. */
    public boolean isAvailable() {
        return client != null;
    }

    @Override
    public String identifier() {
        return properties.getModel();
    }

    @Override
    public ExtractedMarksSheet extract(byte[] image, String contentType,
                                       List<QuestionExpectation> expectations) {
        String callId = UUID.randomUUID().toString().substring(0, 8);
        try {
            return call(callId, image, contentType, expectations);
        } catch (MarksExtractionException e) {
            // Also covers rejections before any request is sent (no API key,
            // oversized or unsupported image), so the log never stays silent.
            traffic.info("[{}] --- NOT READ: {}", callId, e.getMessage());
            throw e;
        }
    }

    private ExtractedMarksSheet call(String callId, byte[] image, String contentType,
                                     List<QuestionExpectation> expectations) {
        if (client == null) {
            throw new MarksExtractionException(
                    "Marks extraction is not configured on this server. Enter the marks manually.");
        }
        if (image.length > properties.getMaxImageBytes()) {
            throw new MarksExtractionException(
                    "That image is too large to read (limit " + properties.getMaxImageBytes() / (1024 * 1024) + " MB).");
        }

        StructuredMessageCreateParams<ExtractedMarksSheet> params =
                MessageCreateParams.builder()
                        .model(properties.getModel())
                        .maxTokens(4096L)
                        .system(SYSTEM_PROMPT)
                        .outputConfig(ExtractedMarksSheet.class)
                        .addUserMessageOfBlockParams(List.of(
                                ContentBlockParam.ofImage(ImageBlockParam.builder()
                                        .source(Base64ImageSource.builder()
                                                .mediaType(mediaTypeFor(contentType))
                                                .data(Base64.getEncoder().encodeToString(image))
                                                .build())
                                        .build()),
                                ContentBlockParam.ofText(TextBlockParam.builder()
                                        .text(userPrompt(expectations))
                                        .build())))
                        .build();

        logRequest(callId, image, contentType, expectations);
        long started = System.nanoTime();

        try {
            StructuredMessage<ExtractedMarksSheet> response = client.messages().create(params);
            logResponse(callId, response, started);
            return response.content().stream()
                    .flatMap(block -> block.text().stream())
                    .map(block -> block.text())
                    .findFirst()
                    .orElseThrow(() -> new MarksExtractionException(
                            "The reader returned no marks sheet for this page."));
        } catch (MarksExtractionException e) {
            throw e;
        } catch (Exception e) {
            logFailure(callId, e, started);
            log.error("Marks extraction failed: {}", e.getMessage(), e);
            throw new MarksExtractionException("Could not read this page: " + e.getMessage(), e);
        }
    }

    // ── Traffic log ──────────────────────────────────────────────────────────

    // The image itself is logged as size and hash only: a base64 page is
    // hundreds of KB per call, and the hash is enough to tie a log entry back to
    // the uploaded file.
    private void logRequest(String callId, byte[] image, String contentType,
                            List<QuestionExpectation> expectations) {
        if (!traffic.isInfoEnabled()) return;
        traffic.info("""
                [{}] >>> REQUEST
                model: {}
                max_tokens: 4096
                image: {} bytes, {}, sha256={}
                --- system ---
                {}
                --- user ---
                {}""",
                callId, properties.getModel(), image.length, contentType, sha256(image),
                SYSTEM_PROMPT.strip(), userPrompt(expectations).strip());
    }

    private void logResponse(String callId, StructuredMessage<ExtractedMarksSheet> response, long started) {
        if (!traffic.isInfoEnabled()) return;
        String body;
        try {
            body = ObjectMappers.jsonMapper().writerWithDefaultPrettyPrinter()
                    .writeValueAsString(response.rawMessage());
        } catch (Exception e) {
            body = response.rawMessage().toString();
        }
        traffic.info("[{}] <<< RESPONSE in {} ms\n{}", callId, elapsedMs(started), body);
    }

    private void logFailure(String callId, Exception e, long started) {
        if (!traffic.isInfoEnabled()) return;
        String status = e instanceof AnthropicServiceException api
                ? "HTTP " + api.statusCode() + " "
                : "";
        traffic.info("[{}] <<< FAILED in {} ms: {}{}", callId, elapsedMs(started), status, e.toString());
    }

    private static long elapsedMs(long started) {
        return (System.nanoTime() - started) / 1_000_000;
    }

    private static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    /**
     * The instruction that matters most is "transcribe, do not compute": a model
     * asked to be helpful will happily add up a column and report a total that
     * is not written anywhere on the page, or fill a blank cell with a plausible
     * mark. Either would put a number on a student's record that no marker ever
     * wrote.
     */
    private static final String SYSTEM_PROMPT = """
            You transcribe the front page of a marked university exam copy.

            Your job is to report exactly what is written on the page — nothing more.

            Rules:
            - Transcribe, do not compute. Never calculate a total, never infer a mark from \
            surrounding marks, and never fill in a blank cell.
            - A blank, struck-through or illegible mark is null, never zero. A zero is only a \
            zero when a zero is actually written.
            - Report the written total as it appears even if it does not match the sum of the \
            individual marks. Do not correct it.
            - Where a mark has been changed, report the value the marker clearly settled on; if \
            it is genuinely ambiguous, report null and say so in the notes.
            - If the page is not an exam marks sheet at all, set confidence to LOW and explain \
            in the notes.

            A human teacher reviews everything you return before it is recorded, so an honest \
            "I could not read this" is far more useful than a confident guess.
            """;

    private String userPrompt(List<QuestionExpectation> expectations) {
        if (expectations == null || expectations.isEmpty()) {
            return "Read this exam copy's front page: the student's roll number and name, "
                   + "the course code, the exam date, and every row of the marks table.";
        }
        String table = expectations.stream()
                .map(q -> "  - question " + q.questionNo() + " (out of " + q.maxMarks() + ")")
                .collect(Collectors.joining("\n"));
        // The expected labels are given so the model reports marks under the same
        // labels the exam uses, which is what lets a row be matched to a question.
        // They are explicitly not a licence to invent a missing row.
        return """
               Read this exam copy's front page: the student's roll number and name, the course \
               code, the exam date, and every row of the marks table.

               This exam is recorded with the following questions:
               %s

               Use these exact question labels when a row on the page corresponds to one of them, \
               so the marks can be matched up. If the page shows a question that is not in this \
               list, report it anyway under the label printed on the page. If one of these \
               questions does not appear on the page, leave it out — do not add a row with a \
               guessed or zero mark.
               """.formatted(table);
    }

    private static Base64ImageSource.MediaType mediaTypeFor(String contentType) {
        String ct = contentType == null ? "" : contentType.toLowerCase();
        return switch (ct) {
            case "image/png"  -> Base64ImageSource.MediaType.IMAGE_PNG;
            case "image/gif"  -> Base64ImageSource.MediaType.IMAGE_GIF;
            case "image/webp" -> Base64ImageSource.MediaType.IMAGE_WEBP;
            case "image/jpeg", "image/jpg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
            default -> throw new MarksExtractionException(
                    "Unsupported image type '" + contentType + "'. Capture the page as JPEG or PNG.");
        };
    }
}
