package com.lms.modules.studyai.dto;

/**
 * Whether the NLP features are running with the local language model or in
 * basic (classical-only) mode.
 */
public record AiStatusResponse(
        boolean llmEnabled,
        boolean llmAvailable,
        String model,
        /** LLM | BASIC */
        String mode,
        /** OpenNLP models loaded (false = rule-based text analysis). */
        boolean nlpModelsLoaded
) {}
