package com.lms.infrastructure.nlp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * {@link LlmGateway} backed by an Ollama server on this machine.
 *
 * <p>Ollama serves open-weights models over a local HTTP API; nothing leaves
 * the server. Structured output uses Ollama's {@code format} parameter, which
 * constrains decoding to a JSON schema, so generated questions arrive as
 * parseable JSON rather than prose to scrape.
 *
 * <p>Inference runs on the CPU, so at most {@code max-concurrency} requests run
 * at once and the rest wait up to {@code queue-wait-seconds} for a slot. A
 * request that cannot get one returns empty and its caller falls back to the
 * classical path: a queue of students waiting minutes for an answer is worse
 * than an extractive answer now.
 */
@Slf4j
@Component
public class OllamaLlmGateway implements LlmGateway {

    /**
     * Size, timing and outcome of every model call. Prompts and answers are not
     * logged: they contain students' questions.
     */
    private static final Logger traffic = LoggerFactory.getLogger("com.lms.infrastructure.nlp.LlmTraffic");

    static final Duration HEALTH_TTL = Duration.ofSeconds(30);

    private final NlpProperties.Llm config;
    private final ObjectMapper mapper;
    private final MeterRegistry meters;
    private final HttpClient http;
    private final Semaphore slots;

    private volatile long healthCheckedAt;
    private volatile boolean healthy;

    public OllamaLlmGateway(NlpProperties properties, ObjectMapper mapper, MeterRegistry meters) {
        this.config = properties.getLlm();
        this.mapper = mapper;
        this.meters = meters;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        this.slots = new Semaphore(Math.max(1, config.getMaxConcurrency()), true);
    }

    @Override
    public String modelName() {
        return config.getModel();
    }

    @Override
    public boolean available() {
        if (!config.isEnabled()) return false;
        long now = System.currentTimeMillis();
        if (now - healthCheckedAt < HEALTH_TTL.toMillis()) return healthy;
        healthy = probe();
        healthCheckedAt = now;
        return healthy;
    }

    /** The server is up and has the configured model pulled. */
    private boolean probe() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) return false;
            for (JsonNode m : mapper.readTree(res.body()).path("models")) {
                String name = m.path("name").asText();
                if (name.equals(config.getModel()) || name.equals(config.getModel() + ":latest")) return true;
            }
            log.warn("Ollama is running but model '{}' is not pulled; run `ollama pull {}`. "
                    + "Using basic (classical) mode.", config.getModel(), config.getModel());
            return false;
        } catch (Exception e) {
            log.debug("Ollama not reachable at {}: {}", config.getBaseUrl(), e.toString());
            return false;
        }
    }

    @Override
    public <T> Optional<T> structured(String system, String user, Map<String, Object> jsonSchema, Class<T> type) {
        return structured(system, user, jsonSchema, type, -1);
    }

    @Override
    public <T> Optional<T> structured(String system, String user, Map<String, Object> jsonSchema, Class<T> type,
                                      int maxTokens) {
        if (!available()) return Optional.empty();
        if (!acquire()) return Optional.empty();
        long started = System.nanoTime();
        try {
            Map<String, Object> body = body(system, List.of(Message.user(user)), false, maxTokens);
            body.put("format", jsonSchema);
            HttpResponse<String> res = http.send(post(body), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                record("structured", "http_" + res.statusCode(), started, 0);
                return Optional.empty();
            }
            JsonNode reply = mapper.readTree(res.body());
            String content = reply.path("message").path("content").asText("");
            boolean cut = "length".equals(reply.path("done_reason").asText(""));
            record("structured", cut ? "truncated" : "ok", started, content.length());
            try {
                return Optional.of(mapper.readValue(content, type));
            } catch (Exception parse) {
                // Output stopped by the token cap mid-item: keep the items that were finished.
                String salvaged = salvage(content);
                return salvaged == null ? Optional.empty() : Optional.of(mapper.readValue(salvaged, type));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            record("structured", "error", started, 0);
            log.warn("Local model call failed ({}); falling back to classical NLP", e.toString());
            return Optional.empty();
        } finally {
            slots.release();
        }
    }

    @Override
    public Optional<String> stream(String system, List<Message> messages, Consumer<String> onToken) {
        if (!available()) return Optional.empty();
        if (!acquire()) return Optional.empty();
        long started = System.nanoTime();
        StringBuilder answer = new StringBuilder();
        try {
            HttpResponse<InputStream> res = http.send(post(body(system, messages, true, -1)),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (res.statusCode() != 200) {
                res.body().close();
                record("stream", "http_" + res.statusCode(), started, 0);
                return Optional.empty();
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(res.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    JsonNode chunk = mapper.readTree(line);
                    String piece = chunk.path("message").path("content").asText("");
                    if (!piece.isEmpty()) {
                        answer.append(piece);
                        onToken.accept(piece);
                    }
                    if (chunk.path("done").asBoolean(false)) break;
                }
            }
            record("stream", "ok", started, answer.length());
            return Optional.of(answer.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return answer.isEmpty() ? Optional.empty() : Optional.of(answer.toString());
        } catch (Exception e) {
            record("stream", "error", started, answer.length());
            log.warn("Local model stream failed ({})", e.toString());
            // Tokens already sent cannot be taken back; return what was written.
            return answer.isEmpty() ? Optional.empty() : Optional.of(answer.toString());
        } finally {
            slots.release();
        }
    }

    /**
     * JSON cut off inside an array of objects — {@code {"items":[{...},{...},{"q":"Wh} —
     * closed after the last complete object; null when there is none.
     */
    static String salvage(String json) {
        int depth = 0, lastComplete = -1;
        boolean inString = false, escaped = false;
        StringBuilder closers = new StringBuilder();
        String closeAtLast = null;
        for (int i = 0; i < json.length(); i++) {
            char ch = json.charAt(i);
            if (inString) {
                if (escaped) escaped = false;
                else if (ch == '\\') escaped = true;
                else if (ch == '"') inString = false;
                continue;
            }
            switch (ch) {
                case '"' -> inString = true;
                case '{', '[' -> {
                    closers.append(ch == '{' ? '}' : ']');
                    depth++;
                }
                case '}', ']' -> {
                    if (depth == 0) return null;
                    closers.setLength(closers.length() - 1);
                    depth--;
                    // An element of a top-level array just closed: a safe place to cut.
                    if (ch == '}' && depth == 2) {
                        lastComplete = i;
                        closeAtLast = new StringBuilder(closers).reverse().toString();
                    }
                }
                default -> { }
            }
        }
        if (depth == 0 || lastComplete < 0) return null;
        return json.substring(0, lastComplete + 1) + closeAtLast;
    }

    private boolean acquire() {
        try {
            if (slots.tryAcquire(config.getQueueWaitSeconds(), TimeUnit.SECONDS)) return true;
            meters.counter("nlp.llm.requests", "outcome", "busy").increment();
            traffic.info("model busy: no slot within {} s, falling back", config.getQueueWaitSeconds());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private Map<String, Object> body(String system, List<Message> messages, boolean stream, int maxTokens) {
        List<Map<String, String>> all = new ArrayList<>();
        all.add(Map.of("role", "system", "content", system));
        for (Message m : messages) all.add(Map.of("role", m.role(), "content", m.content()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", config.getModel());
        body.put("messages", all);
        body.put("stream", stream);
        body.put("keep_alive", "30m");
        // Every call uses the same num_ctx: a different value makes Ollama reload the model.
        Map<String, Object> options = new LinkedHashMap<>();
        options.put("temperature", config.getTemperature());
        options.put("num_ctx", config.getContextTokens());
        if (maxTokens > 0) options.put("num_predict", maxTokens);
        body.put("options", options);
        return body;
    }

    private HttpRequest post(Map<String, Object> body) throws Exception {
        return HttpRequest.newBuilder(URI.create(config.getBaseUrl() + "/api/chat"))
                .timeout(Duration.ofSeconds(config.getTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .build();
    }

    private void record(String kind, String outcome, long started, int chars) {
        long ms = (System.nanoTime() - started) / 1_000_000;
        meters.counter("nlp.llm.requests", "kind", kind, "outcome", outcome).increment();
        meters.timer("nlp.llm.latency", "kind", kind).record(Duration.ofMillis(ms));
        traffic.info("{} {} model={} {} ms, {} chars out", kind, outcome, config.getModel(), ms, chars);
    }
}
