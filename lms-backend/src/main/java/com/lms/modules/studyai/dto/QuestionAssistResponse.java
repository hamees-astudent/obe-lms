package com.lms.modules.studyai.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record QuestionAssistResponse(
        String bloomLevel,
        /** Why, e.g. "opening verb \"compare\"". */
        String bloomSource,
        BigDecimal marks,
        /** "Short answer 4 × Apply 1.25 × Medium 1 = 5". */
        String marksBreakdown,
        List<UUID> suggestedCloIds,
        /** Every CLO's score: [{"cloId","code","score"}], best first. */
        List<Map<String, Object>> cloScores,
        String cloRationale,
        /** Whether the local model was consulted (otherwise classical only). */
        boolean modelUsed
) {}
