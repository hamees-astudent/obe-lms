package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.TextAnalyzer;
import com.lms.modules.studyai.Chunker.Chunk;
import com.lms.modules.studyai.DocumentTextExtractor.Segment;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkerTest {

    private final Chunker chunker = new Chunker(new TextAnalyzer());

    @Test
    void mergesConsecutiveShortSlidesIntoOneRange() {
        List<Chunk> chunks = chunker.chunk(List.of(
                new Segment("SLIDE", 1, "Course overview", "Welcome to Databases."),
                new Segment("SLIDE", 2, "Agenda", "Relational model. SQL. Normalisation."),
                new Segment("SLIDE", 3, "Keys", "A primary key identifies each row uniquely.")));

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).start()).isEqualTo(1);
        assertThat(chunks.get(0).end()).isEqualTo(3);
        assertThat(chunks.get(0).heading()).isEqualTo("Course overview");
        assertThat(chunks.get(0).content()).contains("Keys: A primary key identifies each row uniquely.");
    }

    @Test
    void splitsALongPageBetweenSentencesWithOverlapAndNeverAcrossPages() {
        List<String> sentences = new ArrayList<>();
        for (int i = 1; i <= 80; i++) sentences.add("Sentence number " + i + " explains a separate idea about query planning.");
        List<Chunk> chunks = chunker.chunk(List.of(
                new Segment("PAGE", 4, "Query planning", String.join(" ", sentences)),
                new Segment("PAGE", 5, "Joins", String.join(" ", sentences.subList(0, 60)))));

        assertThat(chunks).allSatisfy(c -> {
            assertThat(Chunker.wordCount(c.content())).isLessThanOrEqualTo(Chunker.MAX_WORDS);
            assertThat(c.start()).isEqualTo(c.end());
            assertThat(c.content()).endsWith(".");
        });
        List<Chunk> page4 = chunks.stream().filter(c -> c.start() == 4).toList();
        assertThat(page4).hasSizeGreaterThan(1);
        // The next passage starts with the tail of the previous one.
        String lastOfFirst = page4.get(0).content().substring(page4.get(0).content().lastIndexOf("Sentence number"));
        assertThat(page4.get(1).content()).contains(lastOfFirst);
        assertThat(chunks).extracting(Chunk::index).containsExactlyElementsOf(
                java.util.stream.IntStream.range(0, chunks.size()).boxed().toList());
    }

    @Test
    void recordsKeyPhrasesForEachPassage() {
        List<Chunk> chunks = chunker.chunk(List.of(new Segment("PAGE", 1, "Deadlock",
                "A deadlock occurs when processes wait on each other forever. Deadlock prevention removes one of "
                + "the four Coffman conditions, such as circular wait or hold and wait. Resource allocation graphs "
                + "help detect a deadlock in an operating system.")));
        assertThat(chunks.get(0).keyPhrases()).isNotEmpty();
        assertThat(String.join("|", chunks.get(0).keyPhrases()).toLowerCase()).contains("deadlock");
    }
}
