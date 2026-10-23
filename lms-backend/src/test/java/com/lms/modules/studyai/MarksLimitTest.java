package com.lms.modules.studyai;

import com.lms.modules.studyai.dto.GenerationEstimateResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

/** The marks limit: estimated up front, and enforced when a paper is assembled. */
class MarksLimitTest {

    private final PaperSettingsService.Settings settings = new PaperSettingsService.Settings(
            BigDecimal.valueOf(100), 40, 180, BigDecimal.valueOf(50), MarksPolicy.defaults());

    @Test
    void marksScaleWithBloomLevelAndDifficulty() {
        MarksPolicy p = MarksPolicy.defaults();
        assertThat(p.marks("SHORT_ANSWER", "UNDERSTAND", "MEDIUM")).isEqualByComparingTo("4");
        assertThat(p.marks("SHORT_ANSWER", "APPLY", "MEDIUM")).isEqualByComparingTo("5");
        assertThat(p.marks("LONG_ANSWER", "ANALYZE", "HARD")).isEqualByComparingTo("19");
        assertThat(p.marks("MCQ", "CREATE", "MEDIUM")).isEqualByComparingTo("1.5");   // capped for choice questions
    }

    @Test
    void aMixOverTheLimitIsRefusedWithTheOverage() {
        GenerationEstimateResponse e = QuestionGenerationService.estimate(settings,
                Map.of("MCQ", 30, "LONG_ANSWER", 3), List.of(), "MEDIUM", BigDecimal.valueOf(50));
        // 30 × 1 + 3 × 15 (Analyze default for long answers) = 75
        assertThat(e.estimatedMarks()).isEqualByComparingTo("75");
        assertThat(e.withinLimit()).isFalse();
        assertThat(e.problem()).contains("25 over the 50-mark limit");
    }

    @Test
    void aLimitAboveTheOfferingMaximumIsRefused() {
        GenerationEstimateResponse e = QuestionGenerationService.estimate(settings,
                Map.of("MCQ", 5), List.of(), "MEDIUM", BigDecimal.valueOf(150));
        assertThat(e.withinLimit()).isFalse();
        assertThat(e.problem()).contains("may not exceed 100");
    }

    @Test
    void aMixWithinTheLimitPasses() {
        GenerationEstimateResponse e = QuestionGenerationService.estimate(settings,
                Map.of("MCQ", 10, "SHORT_ANSWER", 5), List.of("REMEMBER", "UNDERSTAND"), "EASY", BigDecimal.valueOf(50));
        assertThat(e.estimatedMarks()).isEqualByComparingTo("30");
        assertThat(e.withinLimit()).isTrue();
    }

    @Test
    void assemblyNeverExceedsTheLimitAndPrefersCoverage() {
        UUID clo1 = UUID.randomUUID(), clo2 = UUID.randomUUID();
        List<GenQuestion> qs = List.of(
                q("q1", 10, clo1, "ANALYZE"), q("q2", 10, clo1, "ANALYZE"), q("q3", 10, clo1, "ANALYZE"),
                q("q4", 4, clo2, "UNDERSTAND"), q("q5", 1, clo2, "REMEMBER"));

        List<GenQuestion> fitted = PaperAssembler.fit(qs, GenQuestion::getMarks, GenQuestion::getCloIds,
                GenQuestion::getBloomLevel, BigDecimal.valueOf(20), 40, List.of(clo1, clo2));

        assertThat(PaperAssembler.total(fitted, GenQuestion::getMarks)).isLessThanOrEqualTo(BigDecimal.valueOf(20));
        assertThat(fitted).extracting(GenQuestion::getId).contains("q4", "q5");   // the only clo2 questions
        assertThat(fitted).extracting(GenQuestion::getId).containsSubsequence("q1", "q4", "q5"); // original order kept
    }

    @Test
    void assemblyRespectsTheQuestionLimit() {
        List<GenQuestion> qs = new ArrayList<>();
        for (int i = 0; i < 10; i++) qs.add(q("q" + i, 1, null, "REMEMBER"));
        assertThat(PaperAssembler.fit(qs, GenQuestion::getMarks, GenQuestion::getCloIds, GenQuestion::getBloomLevel,
                BigDecimal.valueOf(100), 6, List.of())).hasSize(6);
    }

    private static GenQuestion q(String id, int marks, UUID clo, String bloom) {
        return GenQuestion.builder().id(id).type("SHORT_ANSWER").questionText(id).marks(BigDecimal.valueOf(marks))
                .cloIds(clo == null ? new ArrayList<>() : new ArrayList<>(List.of(clo))).bloomLevel(bloom).build();
    }
}
