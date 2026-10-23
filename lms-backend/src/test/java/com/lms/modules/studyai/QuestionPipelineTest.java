package com.lms.modules.studyai;

import com.lms.infrastructure.nlp.BloomClassifier;
import com.lms.infrastructure.nlp.LlmGateway;
import com.lms.modules.studyai.GenQuestion.Option;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.*;

import static com.lms.modules.studyai.NlpFixtures.ANALYZER;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** Template writing, validation, Bloom/CLO/marks annotation — the classical generation path. */
class QuestionPipelineTest {

    private final List<RetrievedChunk> chunks = NlpFixtures.chunks();
    private final TemplateQuestionGenerator templates = new TemplateQuestionGenerator(ANALYZER);
    private final QuestionValidator validator = new QuestionValidator(ANALYZER);
    private final BloomClassifier bloom = new BloomClassifier(ANALYZER);
    private final CloSuggester clos = new CloSuggester(ANALYZER, bloom);

    private List<String> pool() {
        Set<String> p = new LinkedHashSet<>();
        chunks.forEach(c -> p.addAll(c.keyPhrases()));
        return new ArrayList<>(p);
    }

    @Test
    void templateMcqsAreWellFormedAndGrounded() {
        TemplateQuestionGenerator.Run run = templates.start(chunks, pool(), 1);
        List<GenQuestion> mcqs = templates.generate(run, "MCQ", 4, "MEDIUM", List.of());

        assertThat(mcqs).isNotEmpty();
        List<GenQuestion> accepted = new ArrayList<>();
        for (GenQuestion q : mcqs) {
            QuestionValidator.Result r = validator.validate(q, chunks, accepted);
            assertThat(r.ok()).as(q.getQuestionText() + " → " + r.rejection()).isTrue();
            accepted.add(r.question());
            assertThat(r.question().getOptions()).hasSize(4);
            assertThat(r.question().getCorrectAnswer()).hasSize(1);
        }
        assertThat(mcqs).anyMatch(q -> q.getQuestionText().startsWith("Which of the following best describes")
                || q.getQuestionText().startsWith("Fill in the blank"));
    }

    @Test
    void templateWrittenQuestionsCarryModelAnswersAndRubrics() {
        TemplateQuestionGenerator.Run run = templates.start(chunks, pool(), 2);
        List<GenQuestion> shorts = templates.generate(run, "SHORT_ANSWER", 3, "MEDIUM", List.of("REMEMBER", "UNDERSTAND"));
        List<GenQuestion> longs = templates.generate(run, "LONG_ANSWER", 1, "MEDIUM", List.of("EVALUATE"));

        assertThat(shorts).hasSize(3);
        assertThat(longs).hasSize(1);
        for (GenQuestion q : concat(shorts, longs)) {
            assertThat(q.getModelAnswer()).isNotBlank();
            assertThat(q.getRubric()).isNotEmpty();
            assertThat(validator.validate(q, chunks, List.of()).ok()).isTrue();
        }
        assertThat(longs.get(0).getQuestionText()).startsWith("Critically evaluate");
        assertThat(bloom.classify(longs.get(0).getQuestionText(), "LONG_ANSWER").level().name()).isEqualTo("EVALUATE");
    }

    @Test
    void validatorRejectsMalformedUngroundedAndDuplicateQuestions() {
        GenQuestion twoCorrect = mcq("Which index supports range queries?", List.of("B-tree index", "Hash index", "Bitmap index"),
                List.of("a", "b"));
        assertThat(validator.validate(twoCorrect, chunks, List.of()).rejection()).contains("exactly one");

        GenQuestion invented = mcq("Who invented the relational model in 1970 at IBM?",
                List.of("Edgar Frank Codd", "Grace Hopper", "Alan Turing", "Donald Knuth"), List.of("a"));
        assertThat(validator.validate(invented, chunks, List.of()).rejection()).contains("not supported");

        GenQuestion ok = mcq("What does a B-tree index keep sorted so lookups take logarithmic time?",
                List.of("Keys", "Locks", "Transactions", "Users"), List.of("a"));
        GenQuestion accepted = validator.validate(ok, chunks, List.of()).question();
        GenQuestion again = mcq("What does a B-tree index keep sorted so that lookups take logarithmic time?",
                List.of("Keys", "Rows", "Pages", "Logs"), List.of("a"));
        assertThat(validator.validate(again, chunks, List.of(accepted)).rejection()).contains("duplicate");
    }

    @Test
    void annotatesBloomMarksAndTheMatchingClo() {
        LlmGateway llm = mock(LlmGateway.class);
        when(llm.available()).thenReturn(false);
        QuestionGenerator generator = new QuestionGenerator(null, null, llm, null, templates, validator, bloom, clos, ANALYZER);
        QuestionGenerator.Spec spec = new QuestionGenerator.Spec(UUID.randomUUID(), Map.of(), "MEDIUM", List.of(),
                List.of(), BigDecimal.valueOf(100), 40, List.of(), List.of(), false, null, MarksPolicy.defaults(), 1, List.of());

        GenQuestion q = GenQuestion.builder().type("SHORT_ANSWER")
                .questionText("Apply normalisation to remove redundancy from a table with partial dependencies.")
                .modelAnswer("Second normal form removes partial dependencies on a composite key, reducing redundancy.")
                .rubric(new ArrayList<>(List.of(new GenQuestion.RubricItem("Names 2NF", BigDecimal.ONE, List.of("second normal form")),
                        new GenQuestion.RubricItem("Explains partial dependency", BigDecimal.ONE, List.of("partial dependencies")))))
                .build();
        GenQuestion annotated = generator.annotate(q, spec, NlpFixtures.clos());

        assertThat(annotated.getBloomLevel()).isEqualTo("APPLY");
        assertThat(annotated.getMarks()).isEqualByComparingTo("5");          // short answer 4 × Apply 1.25
        assertThat(annotated.getRubric().stream().map(GenQuestion.RubricItem::marks)
                .reduce(BigDecimal.ZERO, BigDecimal::add)).isEqualByComparingTo("5");
        assertThat(annotated.getCloIds()).containsExactly(NlpFixtures.clos().get(1).id());
        assertThat(annotated.getCloRationale()).contains("CLO-2");
        assertThat(annotated.getCloScores()).hasSize(3);
    }

    @Test
    void theModelsCloIsTakenOnlyWhenItIsAPlausibleMatch() {
        var all = NlpFixtures.clos();
        CloSuggester.Suggestion withHint = clos.suggest("Explain why a deadlock occurs between two transactions.",
                "Two transactions wait for locks held by each other.", "UNDERSTAND", all, List.of("CLO-1"));
        // CLO-1 is about keys; it scores in the top three of three, so the hint stands — but the
        // rationale says it was the model's choice rather than the best match.
        assertThat(withHint.rationale()).contains("CLO-");
        CloSuggester.Suggestion noHint = clos.suggest("Explain why a deadlock occurs between two transactions.",
                "Two transactions wait for locks held by each other.", "UNDERSTAND", all, List.of());
        assertThat(noHint.cloIds()).containsExactly(all.get(2).id());
    }

    private static GenQuestion mcq(String text, List<String> options, List<String> correct) {
        List<Option> opts = new ArrayList<>();
        for (int i = 0; i < options.size(); i++) opts.add(new Option(String.valueOf((char) ('a' + i)), options.get(i)));
        return GenQuestion.builder().type("MCQ").questionText(text).options(opts).correctAnswer(new ArrayList<>(correct)).build();
    }

    private static <T> List<T> concat(List<T> a, List<T> b) {
        List<T> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }
}
