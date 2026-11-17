package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.OllamaLlmGateway;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.*;

import static com.lms.modules.studyai.NlpFixtures.ANALYZER;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The checks that keep generated questions and answers usable: front matter
 * kept out, questions that need unseen context or give their answer away
 * rejected, rephrased duplicates caught, equivalent chat questions searched
 * alike, and code and citations kept intact.
 */
class ContentQualityTest {

    private final List<RetrievedChunk> chunks = NlpFixtures.chunks();
    private final QuestionValidator validator = new QuestionValidator(ANALYZER);

    // ── Front matter ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("prefaces, contents pages, copyright pages, indexes and reference lists are not course content")
    void frontMatterIsRecognised() {
        assertThat(InstructionalFilter.isNonInstructional("Preface",
                "This book grew out of lectures given over ten years. I thank my students.")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional("Acknowledgments", "Many people helped.")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional(null,
                "Copyright © 2021 Pearson Education. All rights reserved. ISBN 978-0-13-468599-1")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional("Contents",
                "1 Introduction ........ 1\n2 Agents ........ 15\n3 Search ........ 40\n4 Logic ........ 77")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional(null,
                "agent, 34, 56\nalpha-beta pruning, 167\nbelief state, 140–142\nBFS, 81\ncost, 12, 88\ndepth, 90")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional(null,
                "[1] S. Russell and P. Norvig, Artificial Intelligence, 2020.\n[2] T. Mitchell, Machine Learning, 1997.\n"
                + "[3] R. Sutton, Reinforcement Learning, 2018.\n[4] C. Bishop, Pattern Recognition, 2006.")).isTrue();
        assertThat(InstructionalFilter.isNonInstructional("Office hours", "Mon 2-4 pm, Room 12")).isTrue();
    }

    @Test
    @DisplayName("subject content is kept, including topics named like front matter")
    void contentIsKept() {
        chunks.forEach(c -> assertThat(InstructionalFilter.isNonInstructional(c)).as(c.heading()).isFalse());
        assertThat(InstructionalFilter.isNonInstructional("Index",
                "A B-tree index keeps keys sorted so lookups take logarithmic time.")).isFalse();
        assertThat(InstructionalFilter.isNonInstructional("References",
                "A C++ reference is an alias for an existing variable and must be initialised.")).isFalse();
        assertThat(InstructionalFilter.isNonInstructional("Permissions",
                "Unix file permissions grant read, write and execute rights to owner, group and others.")).isFalse();
    }

    @Test
    @DisplayName("generation draws on the whole selection, not just its first passages")
    void generationSpreadsOverTheSelection() {
        List<RetrievedChunk> many = new ArrayList<>();
        for (int i = 0; i < 400; i++) many.add(chunk(i, "Topic " + i, "Content " + i));
        List<RetrievedChunk> picked = RetrievalService.spread(many, 80, 7);
        assertThat(picked).hasSize(80);
        assertThat(picked.get(79).chunkIndex()).isGreaterThan(380);
        assertThat(RetrievalService.spread(many, 80, 7)).isEqualTo(picked);
        assertThat(RetrievalService.spread(many, 80, 500).get(0)).isNotEqualTo(picked.get(0));
    }

    // ── Questions that cannot stand alone ────────────────────────────────────

    @Test
    @DisplayName("questions that lean on a figure, a passage or a source id are rejected")
    void absentContextIsRejected() {
        for (String text : List.of(
                "The bunny shown in the figure above is of which colour?",
                "According to the passage, what is a primary key?",
                "As described in S2, which index supports range queries?",
                "What does [S1] say a deadlock is?",
                "It keeps keys sorted so that lookups take logarithmic time. What is it?",
                "Who wrote the preface of this book?")) {
            GenQuestion q = mcq(text, List.of("B-tree index", "Hash index", "Bitmap index", "Heap file"), "a");
            assertThat(validator.validate(q, chunks, List.of()).ok()).as(text).isFalse();
        }
        GenQuestion fine = mcq("What does the following code print?\n```python\nprint(2 ** 3)\n```",
                List.of("8", "6", "9", "5"), "a");
        assertThat(InstructionalFilter.needsAbsentContext(fine.getQuestionText())).isFalse();
        assertThat(InstructionalFilter.needsAbsentContext("Which algorithm finds shortest paths in a weighted graph?"))
                .isFalse();
        assertThat(InstructionalFilter.needsAbsentContext("In automata, what does state S1 accept?")).isFalse();
    }

    // ── Giveaways ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("an answer recognisable by its form, length or wording is rejected")
    void giveawaysAreRejected() {
        GenQuestion web = mcq("Which website hosts the database course forum?",
                List.of("dbforum.com", "Primary key", "Foreign key", "Candidate key"), "a");
        assertThat(validator.validate(web, chunks, List.of()).rejection()).contains("web");

        GenQuestion longest = mcq("What is a transaction?",
                List.of("A unit of work that is executed completely or not at all", "A lock", "An index", "A row"), "a");
        assertThat(validator.validate(longest, chunks, List.of()).rejection()).contains("longer");

        GenQuestion echo = mcq("Which property of a transaction guarantees durability of committed work?",
                List.of("Durability", "Normalisation", "Indexing", "Partitioning"), "a");
        assertThat(validator.validate(echo, chunks, List.of()).rejection()).contains("repeats");

        GenQuestion above = mcq("Which of these are ACID properties?",
                List.of("Atomicity", "Isolation", "Durability", "All of the above"), "d");
        assertThat(validator.validate(above, chunks, List.of()).rejection()).contains("above");

        GenQuestion article = mcq("A structure that speeds up data retrieval at the cost of extra storage is an ________.",
                List.of("index", "transaction", "deadlock", "table"), "a");
        assertThat(validator.validate(article, chunks, List.of()).rejection()).contains("agrees");

        GenQuestion fair = mcq("Which index type cannot answer range queries?",
                List.of("Hash index", "B-tree index", "Clustered index", "Covering index"), "a");
        assertThat(validator.validate(fair, chunks, List.of()).ok()).isTrue();
    }

    // ── Duplicates ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("a rephrased question with the same answer counts as a duplicate")
    void rephrasedDuplicatesAreCaught() {
        GenQuestion first = validator.validate(mcq("Which index type cannot answer range queries?",
                List.of("Hash index", "B-tree index", "Clustered index", "Covering index"), "a"), chunks, List.of()).question();
        GenQuestion rephrased = mcq("Range queries cannot be answered by which kind of index?",
                List.of("B-tree index", "Hash index", "Clustered index", "Covering index"), "b");
        assertThat(validator.validate(rephrased, chunks, List.of(first)).rejection()).contains("duplicate");

        GenQuestion different = mcq("Which normal form removes transitive dependencies between non-key attributes?",
                List.of("Third normal form", "First normal form", "Second normal form", "Boyce-Codd normal form"), "a");
        assertThat(validator.validate(different, chunks, List.of(first)).ok()).isTrue();

        // Questions from an earlier practice paper arrive as text only.
        GenQuestion earlier = GenQuestion.builder().questionText("Which index type cannot answer range queries?").build();
        assertThat(validator.isDuplicate(first, List.of(earlier))).isTrue();
    }

    @Test
    @DisplayName("template fill-in-the-blanks only use sentences that stand on their own")
    void templateSentencesStandAlone() {
        assertThat(TemplateQuestionGenerator.standsAlone(
                "The bunny shown in the picture above is white in colour and very fast.")).isFalse();
        assertThat(TemplateQuestionGenerator.standsAlone(
                "It keeps the keys sorted so that lookups take logarithmic time.")).isFalse();
        assertThat(TemplateQuestionGenerator.standsAlone(
                "Visit www.dbcourse.com for the lecture notes and the lab manuals.")).isFalse();
        assertThat(TemplateQuestionGenerator.standsAlone("The following steps are required:")).isFalse();
        assertThat(TemplateQuestionGenerator.standsAlone(
                "A hash index supports fast equality lookups but cannot answer range queries.")).isTrue();
    }

    // ── Chat ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("two wordings of one question search for the same thing")
    void instructionWordsDoNotChangeTheSearch() {
        String a = RetrievalService.focus("describe applications of agents in real-world scenarios");
        String b = RetrievalService.focus("explain agents in real-world scenario");
        assertThat(ANALYZER.termSet(a)).contains(ANALYZER.stem("agents"), ANALYZER.stem("scenario"))
                .doesNotContain(ANALYZER.stem("describe"));
        assertThat(ANALYZER.termSet(b)).doesNotContain(ANALYZER.stem("explain"));
        assertThat(RetrievalService.focus("Explain in detail")).isEqualTo("Explain in detail");
    }

    @Test
    @DisplayName("explanations get a long, structured answer; definitions a short one")
    void answerDepthFollowsTheQuestion() {
        assertThat(ChatService.depth("Explain how a B-tree keeps lookups logarithmic")).isEqualTo(ChatService.Depth.DETAILED);
        assertThat(ChatService.depth("describe applications of agents in real-world scenarios"))
                .isEqualTo(ChatService.Depth.DETAILED);
        assertThat(ChatService.depth("What is a deadlock?")).isEqualTo(ChatService.Depth.BRIEF);
        assertThat(ChatService.systemPrompt(new CourseContextReader.Offering(UUID.randomUUID(), UUID.randomUUID(),
                "CS301", "Databases"))).contains("LaTeX").contains("fenced").doesNotContain("at most 6 sentences");
    }

    @Test
    @DisplayName("source markers are normalised and stripped where passages are not listed")
    void markersAreNormalised() {
        assertThat(Citations.normaliseMarkers("Keys are sorted (S1). Hashing [Source 2]. Both [S1][S3].")).isEqualTo(
                "Keys are sorted [S1]. Hashing [S2]. Both [S1, S3].");
        assertThat(Citations.stripMarkers("A deadlock is a cycle of waits [S2].")).isEqualTo("A deadlock is a cycle of waits.");
    }

    // ── Model output and ingestion ───────────────────────────────────────────

    @Test
    @DisplayName("JSON cut off by the token cap keeps the questions that were finished")
    void truncatedJsonIsSalvaged() {
        String cut = "{\"questions\":[{\"question\":\"What is a key?\",\"options\":[\"a\",\"b\"]},"
                + "{\"question\":\"What is an ind";
        assertThat(OllamaLlmGateway.class).isNotNull();
        assertThat(salvage(cut)).isEqualTo("{\"questions\":[{\"question\":\"What is a key?\",\"options\":[\"a\",\"b\"]}]}");
        assertThat(salvage("{\"questions\":[{\"question\":\"Wh")).isNull();
    }

    @Test
    @DisplayName("code keeps its lines and indentation through chunking")
    void codeKeepsItsLayout() {
        Chunker chunker = new Chunker(ANALYZER);
        String page = "A loop repeats a block of statements while its condition holds. The example below sums a list "
                + "of numbers and prints the total, which is a common pattern in introductory programming courses "
                + "and appears in most textbooks on the subject.\n"
                + "def total(xs):\n    s = 0\n    for x in xs:\n        s += x\n    return s\n"
                + "The function returns the sum. Loops can also be nested inside one another to walk over tables.";
        String content = chunker.chunk(List.of(new DocumentTextExtractor.Segment("PAGE", 1, null, page))).get(0).content();
        assertThat(content).contains("def total(xs):\n    s = 0\n    for x in xs:\n        s += x");
        assertThat(DocumentTextExtractor.clean("    if x:\n        y = 1  \n")).isEqualTo("    if x:\n        y = 1");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private static String salvage(String json) {
        try {
            var m = OllamaLlmGateway.class.getDeclaredMethod("salvage", String.class);
            m.setAccessible(true);
            return (String) m.invoke(null, json);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static RetrievedChunk chunk(int index, String heading, String content) {
        return new RetrievedChunk(UUID.randomUUID(), NlpFixtures.MATERIAL, "Notes", index, "PAGE", index + 1, index + 1,
                heading, content, List.of(), 50, 1.0);
    }

    private static GenQuestion mcq(String text, List<String> options, String correct) {
        List<Option> opts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) opts.add(new Option(String.valueOf((char) ('a' + i)), options.get(i)));
        return GenQuestion.builder().type("MCQ").questionText(text).options(opts)
                .correctAnswer(new ArrayList<>(List.of(correct))).build();
    }
}
