package com.lms.infrastructure.nlp;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the study assistant and question generator ({@code app.nlp.*}).
 *
 * <p>Everything runs on this server. The classical pipeline (full-text search,
 * OpenNLP, TF-IDF, rule-based question templates) is always on. A local
 * open-weights model served by Ollama improves answers and questions when it
 * is reachable; when it is not, every feature falls back to the classical path
 * rather than failing. No third-party API is called.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.nlp")
public class NlpProperties {

    private Llm llm = new Llm();
    private Limits limits = new Limits();
    private Ingestion ingestion = new Ingestion();

    @Getter
    @Setter
    public static class Llm {
        /** False forces classical-only ("basic") mode even if Ollama is running. */
        private boolean enabled = true;
        /** Ollama's HTTP endpoint. Keep it on localhost. */
        private String baseUrl = "http://localhost:11434";
        private String model = "qwen2.5:3b-instruct";
        /** Per request; CPU inference of a long answer can take a while. */
        private int timeoutSeconds = 180;
        /** Inference is CPU-bound, so requests are queued rather than run in parallel. */
        private int maxConcurrency = 1;
        /** How long a request waits for a free slot before falling back. */
        private int queueWaitSeconds = 20;
        /** Context window requested from the model, in tokens. */
        private int contextTokens = 4096;
        private double temperature = 0.2;
    }

    @Getter
    @Setter
    public static class Limits {
        private int chatMessagesPerHour = 30;
        private int mocksPerDay = 10;
        private int generationJobsPerDay = 20;
        private int assistsPerHour = 100;
    }

    @Getter
    @Setter
    public static class Ingestion {
        private long maxFileBytes = 40L * 1024 * 1024;
        private int maxPages = 1500;
    }
}
