package com.lms.infrastructure.nlp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TextAnalyzerTest {

    private final TextAnalyzer analyzer = new TextAnalyzer();

    @Test
    void loadsTheBundledModels() {
        assertThat(analyzer.modelsLoaded()).isTrue();
    }

    @Test
    void splitsSentencesAndBullets() {
        List<String> s = analyzer.sentences(
                "A stack is a LIFO structure. A queue is FIFO.\n\n• Push adds an item\n• Pop removes it");
        assertThat(s).containsExactly(
                "A stack is a LIFO structure.", "A queue is FIFO.", "Push adds an item", "Pop removes it");
    }

    @Test
    void findsNounPhrasesAsKeyPhrases() {
        String text = "A binary search tree keeps keys in sorted order. Searching a binary search tree "
                + "takes logarithmic time when the tree is balanced. Balanced trees include AVL trees.";
        List<String> phrases = analyzer.keyPhrases(text, 5).stream()
                .map(TextAnalyzer.KeyPhrase::text).map(String::toLowerCase).toList();
        assertThat(phrases).contains("binary search tree");
        assertThat(phrases).doesNotContain("the tree", "when");
    }

    @Test
    void aVerbTheTaggerReadsAsANounEndsThePhrase() {
        List<String> phrases = analyzer.candidatePhrases(
                "The banker's algorithm avoids deadlock by granting a request only if the system stays in a safe state.");
        assertThat(phrases).noneMatch(p -> p.contains("avoids") || p.contains("stays"));
        assertThat(phrases).contains("safe state");
        assertThat(analyzer.candidatePhrases("System calls are expensive.")).contains("System calls");
        assertThat(analyzer.candidatePhrases("A process moves between the ready and running states."))
                .noneMatch(p -> p.contains("moves"));
        assertThat(analyzer.candidatePhrases("Paging divides physical memory into fixed size frames."))
                .contains("physical memory").noneMatch(p -> p.contains("divides"));
    }

    @Test
    void hyphenatedWordsMatchTheirParts() {
        assertThat(analyzer.termSet("User-level threads")).containsAll(analyzer.termSet("level threads"));
    }

    @Test
    void termsAreStemmedWithoutStopwords() {
        assertThat(analyzer.terms("The routers are routing packets"))
                .containsExactly("router", "rout", "packet");
    }
}
