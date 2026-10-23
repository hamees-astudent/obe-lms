package com.lms.modules.studyai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A generated question as it moves through the pipeline — written (by the
 * local model or a template), validated, then annotated with its Bloom level,
 * CLOs and marks. Also the stored form of a mock attempt's questions.
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenQuestion {

    /** Stable within one mock attempt or job; "q1", "q2", … */
    private String id;
    /** MCQ | MSQ | SHORT_ANSWER | LONG_ANSWER */
    private String type;
    private String questionText;
    @Builder.Default
    private List<Option> options = new ArrayList<>();
    @Builder.Default
    private List<String> correctAnswer = new ArrayList<>();
    private String modelAnswer;
    @Builder.Default
    private List<RubricItem> rubric = new ArrayList<>();
    private String explanation;

    private String bloomLevel;
    /** How the level was decided, e.g. "opening verb \"compare\"". */
    private String bloomSource;
    private String difficulty;
    private BigDecimal marks;

    @Builder.Default
    private List<UUID> cloIds = new ArrayList<>();
    @Builder.Default
    private List<CloScore> cloScores = new ArrayList<>();
    private String cloRationale;

    @Builder.Default
    private List<UUID> sourceChunkIds = new ArrayList<>();
    /** LLM | TEMPLATE */
    private String source;

    /** CLO codes the model proposed; a hint for the CLO suggester. */
    @Builder.Default
    private List<String> modelCloCodes = new ArrayList<>();
    /** Bloom level the model proposed; used when the verb lexicon finds none. */
    private String modelBloomLevel;

    /** Draft bookkeeping, not part of the stored question. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean editedFlag;
    @com.fasterxml.jackson.annotation.JsonIgnore
    private int orderIndex;

    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isWritten() {
        return "SHORT_ANSWER".equals(type) || "LONG_ANSWER".equals(type);
    }

    public record Option(String id, String text) {}

    public record RubricItem(String criterion, BigDecimal marks, List<String> keywords) {}

    public record CloScore(UUID cloId, String code, double score) {}
}
