package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A generated question awaiting the teacher's review. */
public record QuestionDraftResponse(
        UUID id,
        int orderIndex,
        String type,
        String questionText,
        List<Map<String, Object>> options,
        List<String> correctAnswer,
        String modelAnswer,
        List<Map<String, Object>> rubric,
        String explanation,
        BigDecimal marks,
        String bloomLevel,
        /** Why that Bloom level, e.g. "opening verb \"compare\"". */
        String bloomSource,
        String difficulty,
        List<UUID> suggestedCloIds,
        /** Every CLO's similarity score: [{"cloId","code","score"}]. */
        List<Map<String, Object>> cloScores,
        String cloRationale,
        /** LLM | TEMPLATE */
        String source,
        List<CitationResponse> sources,
        /** PENDING | ACCEPTED | REJECTED */
        String status,
        boolean edited
) {}
