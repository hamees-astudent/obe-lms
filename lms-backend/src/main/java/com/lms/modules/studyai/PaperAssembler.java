package com.lms.modules.studyai;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;

/**
 * Chooses which questions make it into a paper without going over its marks
 * or question limit.
 *
 * <p>Greedy in two passes: first every question that brings a requested CLO
 * or a Bloom level the selection does not have yet, then the rest in order,
 * each only if it still fits. Questions keep their original order. The total
 * can come in under the limit, never over it.
 */
final class PaperAssembler {

    private PaperAssembler() {}

    static <T> List<T> fit(List<T> items, Function<T, BigDecimal> marks, Function<T, Collection<UUID>> clos,
                           Function<T, String> bloom, BigDecimal maxMarks, int maxCount,
                           Collection<UUID> targetClos) {
        boolean[] chosen = new boolean[items.size()];
        BigDecimal total = BigDecimal.ZERO;
        int count = 0;
        Set<UUID> coveredClos = new HashSet<>();
        Set<String> coveredLevels = new HashSet<>();
        Set<UUID> targets = targetClos == null ? Set.of() : new HashSet<>(targetClos);

        for (int i = 0; i < items.size() && count < maxCount; i++) {
            T item = items.get(i);
            boolean newClo = clos.apply(item).stream().anyMatch(c -> targets.contains(c) && !coveredClos.contains(c));
            boolean newLevel = bloom.apply(item) != null && !coveredLevels.contains(bloom.apply(item));
            if (!(newClo || newLevel)) continue;
            BigDecimal m = marks.apply(item);
            if (total.add(m).compareTo(maxMarks) > 0) continue;
            chosen[i] = true;
            total = total.add(m);
            count++;
            coveredClos.addAll(clos.apply(item));
            if (bloom.apply(item) != null) coveredLevels.add(bloom.apply(item));
        }
        for (int i = 0; i < items.size() && count < maxCount; i++) {
            if (chosen[i]) continue;
            BigDecimal m = marks.apply(items.get(i));
            if (total.add(m).compareTo(maxMarks) > 0) continue;
            chosen[i] = true;
            total = total.add(m);
            count++;
        }
        List<T> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) if (chosen[i]) out.add(items.get(i));
        return out;
    }

    static <T> BigDecimal total(Collection<T> items, Function<T, BigDecimal> marks) {
        return items.stream().map(marks).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
