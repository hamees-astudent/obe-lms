package com.lms.modules.studyai;

import com.lms.modules.studyai.Chunker.Chunk;
import com.lms.modules.studyai.RetrievalService.RetrievedChunk;
import com.lms.modules.studyai.dto.PaperRequest;
import com.lms.modules.studyai.dto.PaperSettingsRequest;
import com.lms.modules.transcript.TranscriptDataRepository;
import com.lms.shared.OfferingStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * The study assistant's SQL against a real PostgreSQL: the migrations, the
 * generated tsvector column, full-text retrieval, and every repository.
 *
 * <p>Skipped unless {@code LMS_IT_DB_URL} points at a throwaway database (see
 * {@code NativeQueryIT} for a one-line container). Each test is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JacksonAutoConfiguration.class, IngestionRepository.class, RetrievalService.class, StudentScope.class,
        CourseContextReader.class, ChatRepository.class, GenerationRepository.class, MockRepository.class,
        PaperRepository.class, PaperSettingsService.class, OfferingStaff.class, TranscriptDataRepository.class})
@EnabledIfEnvironmentVariable(named = "LMS_IT_DB_URL", matches = ".+")
class StudyAiSqlIT {

    @DynamicPropertySource
    static void db(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("LMS_IT_DB_URL"));
        r.add("spring.datasource.username", () -> env("LMS_IT_DB_USER", "lms_user"));
        r.add("spring.datasource.password", () -> env("LMS_IT_DB_PASSWORD", "lms_password"));
        r.add("spring.flyway.enabled", () -> "true");
    }

    private static String env(String name, String fallback) {
        String v = System.getenv(name);
        return v == null || v.isBlank() ? fallback : v;
    }

    /** Needed by the notification entity's push listener, which the JPA slice does not create. */
    @MockBean com.lms.modules.notifications.NotificationStreams notificationStreams;

    @Autowired JdbcTemplate jdbc;
    @Autowired IngestionRepository ingestion;
    @Autowired RetrievalService retrieval;
    @Autowired StudentScope scope;
    @Autowired CourseContextReader context;
    @Autowired ChatRepository chats;
    @Autowired GenerationRepository generation;
    @Autowired MockRepository mocks;
    @Autowired PaperRepository papers;
    @Autowired PaperSettingsService paperSettings;
    @Autowired TranscriptDataRepository transcript;

    private UUID teacher, student, outsider, course, psc, otherPsc, material, clo;

    @BeforeEach
    void seed() {
        teacher = user("Tariq Teacher", "TEACHER");
        student = user("Sana Student", "STUDENT");
        outsider = user("Omar Outsider", "STUDENT");
        UUID program = insert("INSERT INTO programs (name, code) VALUES ('BSCS', ?) RETURNING id", "P-" + rnd());
        UUID semester = insert("""
                INSERT INTO semesters (program_id, name, start_date, end_date) VALUES (?, 'Fall 2026', ?, ?) RETURNING id""",
                program, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        course = insert("INSERT INTO courses (code, name, credit_hours) VALUES (?, 'Databases', 3) RETURNING id", "CS-" + rnd());
        UUID other = insert("INSERT INTO courses (code, name, credit_hours) VALUES (?, 'Networks', 3) RETURNING id", "CS-" + rnd());
        psc = insert("INSERT INTO program_semester_courses (semester_id, course_id, teacher_id) VALUES (?, ?, ?) RETURNING id",
                semester, course, teacher);
        otherPsc = insert("INSERT INTO program_semester_courses (semester_id, course_id, teacher_id) VALUES (?, ?, ?) RETURNING id",
                semester, other, teacher);
        jdbc.update("INSERT INTO enrollments (psc_id, student_id, course_role) VALUES (?, ?, 'STUDENT')", psc, student);
        clo = insert("""
                INSERT INTO clos (course_id, code, title, order_index, bloom_level)
                VALUES (?, 'CLO-1', 'Apply normalisation to relational schemas', 1, 'APPLY') RETURNING id""", course);

        material = insert("""
                INSERT INTO course_materials (psc_id, uploaded_by, type, title, content, visible)
                VALUES (?, ?, 'DOCUMENT', 'Lecture 5', '{"objectKey":"materials/x/l5.pptx","mimeType":"application/pdf"}', TRUE)
                RETURNING id""", psc, teacher);
        List<Chunk> chunks = new Chunker(NlpFixtures.ANALYZER).chunk(NlpFixtures.LECTURE);
        List<IngestionRepository.Topic> topics = new TopicExtractor(NlpFixtures.ANALYZER).extract(chunks, "Lecture 5");
        ingestion.replaceIndex(material, psc, chunks, topics, 4, "abc");
    }

    // ── Index and retrieval ──────────────────────────────────────────────────

    @Test
    @DisplayName("indexing records status, passages with a generated tsvector, and topics")
    void indexIsStored() {
        var status = ingestion.statusForOffering(psc);
        assertThat(status).singleElement().satisfies(s -> {
            assertThat(s.status()).isEqualTo("READY");
            assertThat(s.chunkCount()).isEqualTo(4);
            assertThat(s.topicCount()).isGreaterThanOrEqualTo(4);
        });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM material_chunks WHERE tsv IS NOT NULL AND material_id = ?",
                Integer.class, material)).isEqualTo(4);
        assertThat(ingestion.topics(psc, true)).extracting(t -> t.name()).contains("Normalisation", "Indexes");
        assertThat(ingestion.findSha(material)).contains("abc");
    }

    @Test
    @DisplayName("search matches inflected forms and ranks the right passage first")
    void stemmedSearch() {
        List<RetrievedChunk> hits = retrieval.search(List.of(psc), "Which keys uniquely identify the rows?", 2500, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).heading()).isEqualTo("Primary keys");
        assertThat(hits.get(0).locationLabel()).isEqualTo("Slide 1");
    }

    @Test
    @DisplayName("a question with off-topic words falls back to matching any of its terms")
    void orFallback() {
        List<RetrievedChunk> hits = retrieval.search(List.of(psc), "deadlock between transactions and the weather in Lahore", 2500, null);
        assertThat(hits).isNotEmpty();
        assertThat(hits.get(0).heading()).isEqualTo("Transactions");
    }

    @Test
    @DisplayName("hidden material and other offerings are never searched")
    void scopeAndVisibility() {
        assertThat(retrieval.search(List.of(otherPsc), "primary key", 2500, null)).isEmpty();
        jdbc.update("UPDATE course_materials SET visible = FALSE WHERE id = ?", material);
        assertThat(retrieval.search(List.of(psc), "primary key", 2500, null)).isEmpty();
        assertThat(ingestion.topics(psc, true)).isEmpty();
        assertThat(ingestion.topics(psc, false)).isNotEmpty();
    }

    @Test
    @DisplayName("hostile or symbol-only input does not break the query")
    void hostileInput() {
        for (String q : List.of("'); DROP TABLE users; --", "& | ! <-> :*", "\"\"\"", "the of and", "???")) {
            assertThatNoException().isThrownBy(() -> retrieval.search(List.of(psc), q, 2500, null));
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isPositive();
    }

    @Test
    @DisplayName("generation passages follow topic ranges; the phrase pool and locations resolve")
    void generationInputs() {
        UUID indexes = ingestion.topics(psc, false).stream().filter(t -> t.name().equals("Indexes")).findFirst().orElseThrow().id();
        List<RetrievedChunk> chunks = retrieval.forGeneration(psc, List.of(indexes), null, false, 80);
        assertThat(chunks).singleElement().satisfies(c -> assertThat(c.heading()).isEqualTo("Indexes"));
        assertThat(retrieval.forGeneration(psc, null, List.of(material), true, 80)).hasSize(4);
        assertThat(retrieval.phrasePool(psc, 50)).isNotEmpty();
        assertThat(retrieval.locate(List.of(chunks.get(0).id())).get(chunks.get(0).id()).locationLabel()).isEqualTo("Slide 3");
    }

    // ── Scope and context ────────────────────────────────────────────────────

    @Test
    @DisplayName("only active STUDENT enrollments are in scope")
    void studentScope() {
        assertThat(scope.isEnrolled(psc, student)).isTrue();
        assertThat(scope.isEnrolled(psc, outsider)).isFalse();
        assertThat(scope.offerings(student)).singleElement().satisfies(o -> {
            assertThat(o.courseName()).isEqualTo("Databases");
            assertThat(o.readyMaterials()).isEqualTo(1);
        });
    }

    @Test
    @DisplayName("offering, CLOs and open quiz questions are read")
    void courseContext() {
        assertThat(context.offering(psc).courseName()).isEqualTo("Databases");
        assertThat(context.clos(course)).singleElement().satisfies(c -> assertThat(c.bloomLevel()).isEqualTo("APPLY"));
        UUID quiz = insert("INSERT INTO quizzes (psc_id, created_by, title) VALUES (?, ?, 'Quiz 2') RETURNING id", psc, teacher);
        jdbc.update("""
                INSERT INTO quiz_questions (quiz_id, question_text, type, options, correct_answer, marks, model_answer)
                VALUES (?, 'Define a primary key', 'SHORT_ANSWER', '[]', '[]', 2, 'It identifies each row.')""", quiz);
        assertThat(context.openQuizQuestions(psc, student)).extracting(q -> q.questionText()).containsExactly("Define a primary key");
    }

    // ── Chat, generation, mocks ──────────────────────────────────────────────

    @Test
    @DisplayName("chat sessions keep messages in order with their citations")
    void chat() {
        UUID session = chats.createSession(student, psc);
        chats.addMessage(session, "USER", "What is a primary key?", List.of(), false, null, false);
        var cite = retrieval.locate(List.of(retrieval.search(List.of(psc), "primary key", 2500, null).get(0).id()))
                .values().iterator().next();
        chats.addMessage(session, "ASSISTANT", "It identifies each row [S1].", List.of(cite), false, "EXTRACTIVE", false);
        chats.retitle(session, "Primary keys");

        var s = chats.session(session);
        assertThat(s.title()).isEqualTo("Primary keys");
        assertThat(s.messages()).extracting(m -> m.role()).containsExactly("USER", "ASSISTANT");
        assertThat(s.messages().get(1).citations()).singleElement().satisfies(c -> assertThat(c.locationLabel()).isEqualTo("Slide 1"));
        assertThat(chats.listSessions(student, psc)).hasSize(1);
        assertThat(chats.listSessions(student, null)).hasSize(1);
    }

    @Test
    @DisplayName("generation jobs store drafts with CLOs, rubric and sources")
    void generationJobs() {
        UUID chunk = retrieval.forGeneration(psc, null, null, false, 80).get(0).id();
        UUID job = generation.createJob(psc, teacher, Map.of("maxTotalMarks", 20), List.of(material), List.of());
        GenQuestion q = GenQuestion.builder().type("SHORT_ANSWER").questionText("Define a primary key.")
                .modelAnswer("A primary key uniquely identifies each row.").marks(BigDecimal.valueOf(4))
                .rubric(new ArrayList<>(List.of(new GenQuestion.RubricItem("Uniqueness", BigDecimal.valueOf(4), List.of("unique")))))
                .bloomLevel("REMEMBER").difficulty("EASY").cloIds(new ArrayList<>(List.of(clo)))
                .cloScores(new ArrayList<>(List.of(new GenQuestion.CloScore(clo, "CLO-1", 0.4))))
                .sourceChunkIds(new ArrayList<>(List.of(chunk))).source("TEMPLATE").build();
        generation.complete(job, List.of(q), "TEMPLATE", List.of("note"));

        var resp = generation.job(job);
        assertThat(resp.status()).isEqualTo("READY");
        assertThat(resp.notes()).containsExactly("note");
        assertThat(resp.totalMarks()).isEqualByComparingTo("4");
        assertThat(resp.drafts()).singleElement().satisfies(d -> {
            assertThat(d.suggestedCloIds()).containsExactly(clo);
            assertThat(d.rubric()).hasSize(1);
            assertThat(d.sources()).singleElement().satisfies(c -> assertThat(c.locationLabel()).isEqualTo("Slide 1"));
        });
        UUID draft = resp.drafts().get(0).id();
        GenQuestion edited = generation.findDraft(draft).orElseThrow().question();
        edited.setQuestionText("Define the term primary key.");
        generation.replaceDraft(draft, edited, "ACCEPTED", true);
        assertThat(generation.draftResponse(draft).edited()).isTrue();
        assertThat(generation.listJobs(psc)).hasSize(1);
    }

    @Test
    @DisplayName("mock attempts move from generating to graded")
    void mockAttempts() {
        UUID id = mocks.create(student, psc, "QUIZ", Map.of("counts", Map.of("MCQ", 1)));
        GenQuestion q = GenQuestion.builder().id("q1").type("MCQ").questionText("?").marks(BigDecimal.ONE)
                .options(new ArrayList<>(List.of(new GenQuestion.Option("a", "Yes"), new GenQuestion.Option("b", "No"))))
                .correctAnswer(new ArrayList<>(List.of("a"))).build();
        mocks.ready(id, List.of(q), BigDecimal.ONE, List.of());
        mocks.graded(id, Map.of("q1", List.of("a")), Map.of("q1", Map.of("score", 1)), BigDecimal.ONE);

        var row = mocks.find(id).orElseThrow();
        assertThat(row.status()).isEqualTo("GRADED");
        assertThat(row.questions()).singleElement().satisfies(x -> assertThat(x.getCorrectAnswer()).containsExactly("a"));
        assertThat(row.answers()).containsKey("q1");
        assertThat(mocks.list(student, psc)).hasSize(1);
    }

    // ── Papers ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("papers keep a cached total, refuse to exceed their limit, and store printed sets")
    void papers() {
        assertThat(paperSettings.settings(psc).maxTotalMarks()).isEqualByComparingTo("100");
        paperSettings.update(psc, new PaperSettingsRequest(BigDecimal.valueOf(60), 30, 120, BigDecimal.valueOf(25), null),
                teacher, true);
        assertThat(paperSettings.settings(psc).maxTotalMarks()).isEqualByComparingTo("60");

        UUID paper = papers.create(psc, teacher, new PaperRequest("Midterm", null, 90, BigDecimal.valueOf(10)));
        GenQuestion q = GenQuestion.builder().type("MCQ").questionText("Pick one").marks(BigDecimal.valueOf(6))
                .options(new ArrayList<>(List.of(new GenQuestion.Option("a", "A"), new GenQuestion.Option("b", "B"))))
                .correctAnswer(new ArrayList<>(List.of("b"))).build();
        papers.addItem(paper, "CHOICE", 0, q.getMarks(), q, null, null);
        papers.afterItemsChanged(paper);
        assertThat(papers.find(paper).orElseThrow().totalMarks()).isEqualByComparingTo("6");

        var items = papers.items(paper);
        var layouts = PaperShuffler.layouts(items.stream().map(i -> new PaperShuffler.Item(i.id(), i.section(),
                i.orderIndex(), List.of("a", "b"))).toList(), 2, true, true, new Random(3)::nextLong);
        papers.replaceSets(paper, layouts, teacher);
        assertThat(papers.sets(paper)).extracting(PaperRepository.SetRow::setCode).containsExactly("A", "B");
        assertThat(papers.response(paper, 30, true).status()).isEqualTo("FINAL");

        // A second 6-mark question would take the 10-mark paper to 12: the database refuses too.
        papers.addItem(paper, "CHOICE", 1, q.getMarks(), q, null, null);
        assertThatThrownBy(() -> papers.afterItemsChanged(paper)).isInstanceOf(DataIntegrityViolationException.class);
    }

    // ── Grading state ────────────────────────────────────────────────────────

    @Test
    @DisplayName("a quiz attempt awaiting review is not counted in the transcript")
    void pendingReviewNotCounted() {
        UUID quiz = insert("INSERT INTO quizzes (psc_id, created_by, title, total_marks) VALUES (?, ?, 'Q', 10) RETURNING id",
                psc, teacher);
        jdbc.update("""
                INSERT INTO quiz_submissions (quiz_id, student_id, submitted_at, started_at, grading_status)
                VALUES (?, ?, now(), now() - interval '5 minutes', 'PENDING_REVIEW')""", quiz, student);
        assertThat(transcript.findStudentQuizMarks(psc, student)).isZero();

        jdbc.update("UPDATE quiz_submissions SET grading_status = 'GRADED', score = 7 WHERE quiz_id = ?", quiz);
        assertThat(transcript.findStudentQuizMarks(psc, student)).isEqualTo(7.0);

        assertThatThrownBy(() -> jdbc.update("UPDATE quiz_submissions SET score = NULL WHERE quiz_id = ?", quiz))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID user(String name, String role) {
        return insert("INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, 'x', ?) RETURNING id",
                name, name.toLowerCase().replace(' ', '.') + "." + rnd() + "@it.lms.local", role);
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }

    private static String rnd() {
        return UUID.randomUUID().toString().substring(0, 6);
    }
}
