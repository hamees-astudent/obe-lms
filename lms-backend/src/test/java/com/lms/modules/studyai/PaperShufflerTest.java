package com.lms.modules.studyai;

import com.lms.modules.studyai.PaperShuffler.Item;
import com.lms.modules.studyai.PaperShuffler.Layout;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class PaperShufflerTest {

    private final List<Item> items = new ArrayList<>();

    PaperShufflerTest() {
        for (int i = 0; i < 8; i++) items.add(new Item(UUID.randomUUID(), "CHOICE", i, List.of("a", "b", "c", "d")));
        for (int i = 0; i < 3; i++) items.add(new Item(UUID.randomUUID(), "SHORT", i, List.of()));
        items.add(new Item(UUID.randomUUID(), "LONG", 0, List.of()));
    }

    @Test
    void theSameSeedGivesTheSameLayout() {
        Layout a = PaperShuffler.layout(items, "A", 42, true, true);
        Layout b = PaperShuffler.layout(items, "A", 42, true, true);
        assertThat(a.questionOrder()).isEqualTo(b.questionOrder());
        assertThat(a.optionOrders()).isEqualTo(b.optionOrders());
    }

    @Test
    void setsDifferButSectionsKeepTheirOrder() {
        AtomicLong seed = new AtomicLong(7);
        List<Layout> sets = PaperShuffler.layouts(items, 4, true, true, seed::incrementAndGet);

        assertThat(sets).extracting(Layout::setCode).containsExactly("A", "B", "C", "D");
        assertThat(new HashSet<>(sets.stream().map(Layout::questionOrder).toList())).hasSize(4);
        Map<UUID, String> section = new HashMap<>();
        items.forEach(i -> section.put(i.id(), i.section()));
        for (Layout l : sets) {
            List<String> sections = l.questionOrder().stream().map(section::get).toList();
            assertThat(sections.subList(0, 8)).containsOnly("CHOICE");
            assertThat(sections.subList(8, 11)).containsOnly("SHORT");
            assertThat(sections.get(11)).isEqualTo("LONG");
            assertThat(new HashSet<>(l.questionOrder())).hasSize(items.size());
        }
    }

    @Test
    void withoutShufflingEverySetIsInTheTeachersOrder() {
        Layout l = PaperShuffler.layout(items, "A", 99, false, false);
        assertThat(l.questionOrder()).containsExactlyElementsOf(items.stream().map(Item::id).toList());
        assertThat(l.optionOrders().values()).allSatisfy(o -> assertThat(o).containsExactly("a", "b", "c", "d"));
    }

    @Test
    void answerKeyLettersFollowThePrintedOptionOrder() {
        assertThat(PaperShuffler.correctLetters(List.of("c", "a", "d", "b"), Set.of("a"))).containsExactly("B");
        assertThat(PaperShuffler.correctLetters(List.of("c", "a", "d", "b"), Set.of("b", "c"))).containsExactly("A", "D");
    }
}
