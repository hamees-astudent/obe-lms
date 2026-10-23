package com.lms.modules.studyai;

import java.util.*;

/**
 * Lays out the printed sets of a paper so neighbours in an exam hall hold
 * differently ordered papers.
 *
 * <p>Each set has its own random seed. Within each section (A: choice, B:
 * short answer, C: long answer) the questions are shuffled with Fisher–Yates
 * — sections themselves keep their order so the paper still reads normally —
 * and each choice question's options are shuffled too, so "Q4 is B" is no use
 * to the next desk. Questions are renumbered per set.
 *
 * <p>The seed and resulting orders are stored, so a set's answer key can be
 * regenerated and checked against its printed copy at any time.
 */
final class PaperShuffler {

    private PaperShuffler() {}

    static final List<String> SECTION_ORDER = List.of("CHOICE", "SHORT", "LONG");

    record Item(UUID id, String section, int orderIndex, List<String> optionIds) {}

    record Layout(String setCode, long seed, List<UUID> questionOrder, Map<UUID, List<String>> optionOrders,
                  boolean shuffledQuestions, boolean shuffledOptions) {}

    static String setCode(int index) {
        return String.valueOf((char) ('A' + index));
    }

    /** The unshuffled order: by section, then the teacher's order. */
    static List<Item> canonical(List<Item> items) {
        return items.stream()
                .sorted(Comparator.comparingInt((Item i) -> SECTION_ORDER.indexOf(i.section()))
                        .thenComparingInt(Item::orderIndex))
                .toList();
    }

    static Layout layout(List<Item> items, String setCode, long seed, boolean shuffleQuestions,
                         boolean shuffleOptions) {
        Random random = new Random(seed);
        List<UUID> order = new ArrayList<>();
        for (String section : SECTION_ORDER) {
            List<UUID> ids = new ArrayList<>(canonical(items).stream()
                    .filter(i -> i.section().equals(section)).map(Item::id).toList());
            if (shuffleQuestions) fisherYates(ids, random);
            order.addAll(ids);
        }
        Map<UUID, List<String>> options = new LinkedHashMap<>();
        for (Item item : items) {
            if (item.optionIds().isEmpty()) continue;
            List<String> o = new ArrayList<>(item.optionIds());
            if (shuffleOptions) fisherYates(o, random);
            options.put(item.id(), o);
        }
        return new Layout(setCode, seed, order, options, shuffleQuestions, shuffleOptions);
    }

    /**
     * {@code count} layouts, re-seeding a set that came out identical to an
     * earlier one (likely only for a tiny paper) so no two sets match when
     * they can differ at all.
     */
    static List<Layout> layouts(List<Item> items, int count, boolean shuffleQuestions, boolean shuffleOptions,
                                java.util.function.LongSupplier seeds) {
        List<Layout> out = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            Layout l = null;
            for (int attempt = 0; attempt < 12; attempt++) {
                l = layout(items, setCode(i), seeds.getAsLong(), shuffleQuestions, shuffleOptions);
                Layout candidate = l;
                if (out.stream().noneMatch(o -> same(o, candidate))) break;
            }
            out.add(l);
        }
        return out;
    }

    private static boolean same(Layout a, Layout b) {
        return a.questionOrder().equals(b.questionOrder()) && a.optionOrders().equals(b.optionOrders());
    }

    private static <T> void fisherYates(List<T> list, Random random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    /** Printed letters (A, B, …) of the correct options under a set's option order. */
    static List<String> correctLetters(List<String> printedOrder, Collection<String> correctIds) {
        List<String> letters = new ArrayList<>();
        for (int i = 0; i < printedOrder.size(); i++) {
            if (correctIds.contains(printedOrder.get(i))) letters.add(String.valueOf((char) ('A' + i)));
        }
        return letters;
    }
}
