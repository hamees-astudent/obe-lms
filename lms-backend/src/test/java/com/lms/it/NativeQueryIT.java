package com.lms.it;

import com.lms.modules.assessment.AssignmentRepository;
import com.lms.modules.assessment.QuizRepository;
import com.lms.modules.attendance.AttendanceSessionRepository;
import com.lms.modules.courses.ProgramSemesterCourseRepository;
import com.lms.modules.enrollment.EnrollmentRepository;
import com.lms.modules.exams.ExamDataRepository;
import com.lms.shared.OfferingStaff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the hand-written native SQL against a real PostgreSQL, which the unit
 * tests (all mocked) cannot check: column names, casts, and whether each alias
 * actually reaches its projection getter.
 *
 * <p>Skipped unless {@code LMS_IT_DB_URL} points at a database the Flyway
 * migrations can run on, e.g. a throwaway container:
 * <pre>
 * docker run -d --rm --name lms-it-pg -e POSTGRES_USER=lms_user \
 *   -e POSTGRES_PASSWORD=lms_password -e POSTGRES_DB=lms_db -p 55432:5432 postgres:16-alpine
 * LMS_IT_DB_URL=jdbc:postgresql://localhost:55432/lms_db ./mvnw test -Dtest=NativeQueryIT
 * </pre>
 * Each test runs in a transaction that is rolled back.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({ExamDataRepository.class, OfferingStaff.class})
@EnabledIfEnvironmentVariable(named = "LMS_IT_DB_URL", matches = ".+")
class NativeQueryIT {

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

    @Autowired JdbcTemplate jdbc;
    @Autowired AssignmentRepository assignments;
    @Autowired QuizRepository quizzes;
    @Autowired AttendanceSessionRepository sessions;
    @Autowired EnrollmentRepository enrollments;
    @Autowired ProgramSemesterCourseRepository offerings;
    @Autowired ExamDataRepository examData;
    @Autowired OfferingStaff offeringStaff;

    // teacher: teacher of record · assistant: course_assistants row
    // coTeacher: TEACHER course member · student: STUDENT member · outsider: none
    private UUID teacher, assistant, coTeacher, student, outsider;
    private UUID psc, assignment, quiz;

    @BeforeEach
    void seed() {
        teacher   = user("Tariq Teacher", "TEACHER");
        assistant = user("Asma Assistant", "ASSISTANT");
        coTeacher = user("Kamran CoTeacher", "TEACHER");
        student   = user("Sana Student", "STUDENT");
        outsider  = user("Omar Outsider", "TEACHER");

        UUID program = insert("""
                INSERT INTO programs (name, code) VALUES ('BS Computer Science', ?) RETURNING id""",
                "BSCS-" + UUID.randomUUID().toString().substring(0, 6));
        UUID semester = insert("""
                INSERT INTO semesters (program_id, name, start_date, end_date)
                VALUES (?, 'Fall 2026', ?, ?) RETURNING id""",
                program, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 31));
        UUID course = insert("""
                INSERT INTO courses (code, name, credit_hours) VALUES (?, 'Operating Systems', 3) RETURNING id""",
                "CS-" + UUID.randomUUID().toString().substring(0, 6));
        psc = insert("""
                INSERT INTO program_semester_courses (semester_id, course_id, teacher_id)
                VALUES (?, ?, ?) RETURNING id""", semester, course, teacher);

        jdbc.update("INSERT INTO course_assistants (psc_id, user_id) VALUES (?, ?)", psc, assistant);
        jdbc.update("INSERT INTO enrollments (psc_id, student_id, course_role) VALUES (?, ?, 'TEACHER')", psc, coTeacher);
        jdbc.update("INSERT INTO enrollments (psc_id, student_id, course_role) VALUES (?, ?, 'STUDENT')", psc, student);
        jdbc.update("INSERT INTO student_profiles (user_id, student_number) VALUES (?, 'B26-0042')", student);

        assignment = insert("""
                INSERT INTO assignments (psc_id, created_by, title, total_marks, due_date)
                VALUES (?, ?, 'Lab 1', 10, now() + interval '7 days') RETURNING id""", psc, teacher);
        quiz = insert("""
                INSERT INTO quizzes (psc_id, created_by, title) VALUES (?, ?, 'Quiz 1') RETURNING id""",
                psc, teacher);
    }

    // ── Projections: does every alias reach its getter? ──────────────────────

    @Test
    @DisplayName("assignment event context fills every field")
    void assignmentEventContext() {
        var ctx = assignments.findEventContext(assignment, student);
        assertThat(ctx.getStudentEmail()).isEqualTo(email("Sana Student"));
        assertThat(ctx.getStudentName()).isEqualTo("Sana Student");
        assertThat(ctx.getCourseCode()).startsWith("CS-");
        assertThat(ctx.getCourseName()).isEqualTo("Operating Systems");
    }

    @Test
    @DisplayName("quiz event context fills every field")
    void quizEventContext() {
        var ctx = quizzes.findEventContext(quiz, student);
        assertThat(ctx.getStudentEmail()).isEqualTo(email("Sana Student"));
        assertThat(ctx.getStudentName()).isEqualTo("Sana Student");
        assertThat(ctx.getCourseCode()).startsWith("CS-");
        assertThat(ctx.getCourseName()).isEqualTo("Operating Systems");
    }

    @Test
    @DisplayName("roster names and roll numbers resolve")
    void rosterNames() {
        var names = enrollments.findStudentNamesByPscId(psc);
        var sana = names.stream().filter(v -> v.getStudentId().equals(student.toString())).findFirst();
        assertThat(sana).isPresent();
        assertThat(sana.get().getStudentName()).isEqualTo("Sana Student");
        assertThat(sana.get().getStudentNumber()).isEqualTo("B26-0042");
    }

    @Test
    @DisplayName("attendance alert context fills every field")
    void attendanceAlertContext() {
        var ctx = sessions.findAlertContext(psc, student).orElseThrow();
        assertThat(ctx.getStudentEmail()).isEqualTo(email("Sana Student"));
        assertThat(ctx.getCourseName()).isEqualTo("Operating Systems");
        assertThat(ctx.getSemesterName()).isEqualTo("Fall 2026");
    }

    // ── Staff: the same three-way rule everywhere ────────────────────────────

    @Test
    @DisplayName("OfferingStaff counts all three kinds of staff, and nobody else")
    void offeringStaff() {
        assertThat(offeringStaff.isStaff(psc, teacher)).isTrue();
        assertThat(offeringStaff.isStaff(psc, assistant)).isTrue();
        assertThat(offeringStaff.isStaff(psc, coTeacher)).isTrue();
        assertThat(offeringStaff.isStaff(psc, student)).isFalse();
        assertThat(offeringStaff.isStaff(psc, outsider)).isFalse();
    }

    @Test
    @DisplayName("the exam scan offering list counts all three kinds of staff")
    void examOfferingsForStaff() {
        for (UUID staff : new UUID[] {teacher, assistant, coTeacher}) {
            assertThat(examData.findOfferingsForTeacher(staff)).hasSize(1);
        }
        assertThat(examData.findOfferingsForTeacher(student)).isEmpty();
        assertThat(examData.findOfferingsForTeacher(outsider)).isEmpty();
    }

    @Test
    @DisplayName("teaching offerings list the course for every kind of staff, with names")
    void teachingOfferings() {
        for (UUID staff : new UUID[] {teacher, assistant, coTeacher}) {
            var rows = offerings.findTeachingOfferings(staff);
            assertThat(rows).extracting(v -> v.getPscId()).containsExactly(psc.toString());
            assertThat(rows.get(0).getSemesterName()).isEqualTo("Fall 2026");
            assertThat(rows.get(0).getProgramName()).isEqualTo("BS Computer Science");
        }
        assertThat(offerings.findTeachingOfferings(student)).isEmpty();
        assertThat(offerings.findTeachingOfferings(outsider)).isEmpty();
    }

    @Test
    @DisplayName("attendance can be recorded only for active STUDENT members")
    void activeStudentIds() {
        assertThat(sessions.findActiveStudentIds(psc)).containsExactly(student.toString());
    }

    // ── Clash guards (1.2) ───────────────────────────────────────────────────

    @Test
    @DisplayName("teacher-of-record and membership lookups")
    void clashLookups() {
        assertThat(enrollments.findTeacherIdByPscId(psc)).contains(teacher.toString());
        assertThat(offerings.isActiveMember(psc, coTeacher)).isTrue();
        assertThat(offerings.isActiveMember(psc, teacher)).isFalse();
    }

    // ── Fixtures ─────────────────────────────────────────────────────────────

    private UUID user(String name, String role) {
        return insert("""
                INSERT INTO users (name, email, password_hash, role) VALUES (?, ?, 'x', ?) RETURNING id""",
                name, email(name), role);
    }

    private static String email(String name) {
        return name.toLowerCase().replace(' ', '.') + "@it.lms.local";
    }

    private UUID insert(String sql, Object... args) {
        return jdbc.queryForObject(sql, UUID.class, args);
    }
}
