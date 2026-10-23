package com.lms.modules.studyai;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * How many marks a question is worth: a base by question type, scaled by its
 * Bloom level and difficulty, rounded to the nearest half mark.
 *
 * <p>A fixed table rather than the model's opinion, so equal questions get
 * equal marks across a paper and a teacher can see — and change, per
 * offering — exactly why a question carries what it does.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MarksPolicy(Map<String, BigDecimal> base, Map<String, Double> bloom, Map<String, Double> difficulty) {

    public static final List<String> TYPES = List.of("MCQ", "MSQ", "SHORT_ANSWER", "LONG_ANSWER");

    public static MarksPolicy defaults() {
        Map<String, BigDecimal> base = new LinkedHashMap<>();
        base.put("MCQ", BigDecimal.valueOf(1));
        base.put("MSQ", BigDecimal.valueOf(2));
        base.put("SHORT_ANSWER", BigDecimal.valueOf(4));
        base.put("LONG_ANSWER", BigDecimal.valueOf(10));
        Map<String, Double> bloom = new LinkedHashMap<>();
        bloom.put("REMEMBER", 1.0);
        bloom.put("UNDERSTAND", 1.0);
        bloom.put("APPLY", 1.25);
        bloom.put("ANALYZE", 1.5);
        bloom.put("EVALUATE", 1.5);
        bloom.put("CREATE", 1.75);
        Map<String, Double> difficulty = new LinkedHashMap<>();
        difficulty.put("EASY", 1.0);
        difficulty.put("MEDIUM", 1.0);
        difficulty.put("HARD", 1.25);
        return new MarksPolicy(base, bloom, difficulty);
    }

    /** This table with any missing entries taken from the defaults. */
    public MarksPolicy withDefaults() {
        MarksPolicy d = defaults();
        Map<String, BigDecimal> b = new LinkedHashMap<>(d.base());
        if (base != null) base.forEach((k, v) -> { if (v != null && v.signum() > 0) b.put(k, v); });
        Map<String, Double> bl = new LinkedHashMap<>(d.bloom());
        if (bloom != null) bloom.forEach((k, v) -> { if (v != null && v > 0) bl.put(k, v); });
        Map<String, Double> df = new LinkedHashMap<>(d.difficulty());
        if (difficulty != null) difficulty.forEach((k, v) -> { if (v != null && v > 0) df.put(k, v); });
        return new MarksPolicy(b, bl, df);
    }

    public BigDecimal marks(String type, String bloomLevel, String difficultyLevel) {
        BigDecimal b = base.getOrDefault(type, BigDecimal.ONE);
        // Choice questions stay short whatever their level; cap their scaling.
        double bm = bloom.getOrDefault(bloomLevel == null ? "UNDERSTAND" : bloomLevel, 1.0);
        if ("MCQ".equals(type) || "MSQ".equals(type)) bm = Math.min(bm, 1.5);
        double dm = difficulty.getOrDefault(difficultyLevel == null ? "MEDIUM" : difficultyLevel, 1.0);
        return roundHalf(b.multiply(BigDecimal.valueOf(bm * dm)));
    }

    /**
     * Expected marks of one question of {@code type} when it may land on any of
     * {@code bloomLevels} — used to check a request against the marks limit
     * before anything is generated.
     */
    public BigDecimal estimate(String type, Collection<String> bloomLevels, String difficultyLevel) {
        List<String> levels = bloomLevels == null || bloomLevels.isEmpty()
                ? List.of(defaultLevel(type)) : List.copyOf(bloomLevels);
        BigDecimal sum = BigDecimal.ZERO;
        for (String l : levels) sum = sum.add(marks(type, l, difficultyLevel));
        return roundHalf(sum.divide(BigDecimal.valueOf(levels.size()), 4, RoundingMode.HALF_UP));
    }

    static String defaultLevel(String type) {
        return switch (type) {
            case "LONG_ANSWER" -> "ANALYZE";
            case "SHORT_ANSWER" -> "UNDERSTAND";
            default -> "REMEMBER";
        };
    }

    public static BigDecimal roundHalf(BigDecimal v) {
        BigDecimal r = v.multiply(BigDecimal.valueOf(2)).setScale(0, RoundingMode.HALF_UP)
                .divide(BigDecimal.valueOf(2), 1, RoundingMode.UNNECESSARY);
        return r.signum() <= 0 ? new BigDecimal("0.5") : r;
    }
}
