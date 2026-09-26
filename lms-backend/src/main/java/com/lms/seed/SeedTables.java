package com.lms.seed;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.seed.SeedWriter.Table;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

/**
 * Every table the seeder writes, declared parents first so that
 * {@link SeedWriter#flush} satisfies each foreign key.
 */
final class SeedTables {

    static final String ENROLLMENT_LISTENER =
            "com.lms.modules.notifications.NotificationKafkaConsumers.enrollmentNotificationHandler";
    static final String ASSESSMENT_LISTENER =
            "com.lms.modules.notifications.NotificationKafkaConsumers.assessmentEventHandler";
    static final String SEMESTER_NOTIFICATION_LISTENER =
            "com.lms.modules.notifications.NotificationKafkaConsumers.semesterEventHandler";
    static final String SEMESTER_TRANSCRIPT_LISTENER =
            "com.lms.modules.transcript.SemesterEventConsumer.transcriptSemesterHandler";

    private final SeedWriter writer;
    private final ObjectMapper objectMapper;

    final Table users;
    final Table studentProfiles;
    final Table teacherProfiles;
    final Table programs;
    final Table plos;
    final Table gradingScales;
    final Table gradingScaleEntries;
    final Table courses;
    final Table clos;
    final Table cloPloMappings;
    final Table cohorts;
    final Table cohortMembers;
    final Table semesters;
    final Table offerings;
    final Table courseAssistants;
    final Table enrollments;
    final Table quizzes;
    final Table quizQuestions;
    final Table quizCloMappings;
    final Table assignments;
    final Table assignmentCloMappings;
    final Table courseMaterials;
    final Table materialCloMappings;
    final Table quizSubmissions;
    final Table assignmentSubmissions;
    final Table attendanceSessions;
    final Table attendanceRecords;
    final Table exams;
    final Table examQuestions;
    final Table examQuestionCloMappings;
    final Table examResults;
    final Table examQuestionMarks;
    final Table eventPublications;

    SeedTables(SeedWriter writer, ObjectMapper objectMapper) {
        this.writer = writer;
        this.objectMapper = objectMapper;
        users = writer.table("users",
                "id", "name", "email", "password_hash", "role", "status", "created_at");
        studentProfiles = writer.table("student_profiles",
                "user_id", "student_number", "phone", "address", "enrollment_date", "created_at");
        teacherProfiles = writer.table("teacher_profiles",
                "user_id", "employee_number", "department", "designation", "phone", "joining_date", "bio",
                "created_at");
        programs = writer.table("programs",
                "id", "name", "code", "description", "duration_years", "status", "created_at");
        plos = writer.table("plos",
                "id", "program_id", "code", "title", "description", "order_index", "created_at");
        gradingScales = writer.table("grading_scales",
                "id", "program_id", "name", "is_default", "created_at");
        gradingScaleEntries = writer.table("grading_scale_entries",
                "id", "scale_id", "grade_letter", "min_percentage", "max_percentage", "grade_points",
                "order_index", "created_at");
        courses = writer.table("courses",
                "id", "code", "name", "description", "credit_hours", "status", "created_at");
        clos = writer.table("clos",
                "id", "course_id", "code", "title", "description", "order_index", "created_at");
        cloPloMappings = writer.table("clo_plo_mappings",
                "clo_id", "plo_id", "weight", "created_at");
        cohorts = writer.table("cohorts",
                "id", "name", "description", "created_at");
        cohortMembers = writer.table("cohort_members",
                "cohort_id", "student_id", "created_at");
        semesters = writer.table("semesters",
                "id", "program_id", "name", "start_date", "end_date", "status", "closed_at", "closed_by",
                "created_at");
        offerings = writer.table("program_semester_courses",
                "id", "semester_id", "course_id", "teacher_id", "max_capacity", "created_at");
        courseAssistants = writer.table("course_assistants",
                "psc_id", "user_id", "created_at");
        enrollments = writer.table("enrollments",
                "id", "psc_id", "student_id", "status", "dropped_at", "enrolled_at", "course_role", "created_at");
        quizzes = writer.table("quizzes",
                "id", "psc_id", "created_by", "title", "description", "duration_minutes", "total_marks",
                "available_from", "available_until", "shuffle_questions", "shuffle_options", "created_at");
        quizQuestions = writer.table("quiz_questions",
                "id", "quiz_id", "question_text", "type", "options::jsonb", "correct_answer::jsonb", "marks",
                "order_index", "explanation", "created_at");
        quizCloMappings = writer.table("quiz_clo_mappings",
                "quiz_id", "clo_id", "weight", "created_at");
        assignments = writer.table("assignments",
                "id", "psc_id", "created_by", "title", "description", "submission_type", "total_marks",
                "due_date", "allow_late_submission", "late_penalty_percent", "created_at");
        assignmentCloMappings = writer.table("assignment_clo_mappings",
                "assignment_id", "clo_id", "weight", "created_at");
        courseMaterials = writer.table("course_materials",
                "id", "psc_id", "uploaded_by", "type", "title", "description", "content::jsonb", "visible",
                "order_index", "created_at");
        materialCloMappings = writer.table("material_clo_mappings",
                "material_id", "clo_id", "weight", "created_at");
        quizSubmissions = writer.table("quiz_submissions",
                "id", "quiz_id", "student_id", "answers::jsonb", "submitted_at", "score", "is_auto_graded",
                "started_at", "created_at");
        assignmentSubmissions = writer.table("assignment_submissions",
                "id", "assignment_id", "student_id", "status", "text_content", "submitted_at", "marks_obtained",
                "feedback", "graded_by", "graded_at", "created_at");
        attendanceSessions = writer.table("attendance_sessions",
                "id", "psc_id", "created_by", "session_date", "topic", "opened_at", "closed_at", "created_at");
        attendanceRecords = writer.table("attendance_records",
                "id", "session_id", "student_id", "status", "marked_by", "remarks", "created_at");
        exams = writer.table("exams",
                "id", "psc_id", "created_by", "title", "exam_type", "exam_date", "total_marks", "status",
                "created_at");
        examQuestions = writer.table("exam_questions",
                "id", "exam_id", "question_no", "max_marks", "order_index", "created_at");
        examQuestionCloMappings = writer.table("exam_question_clo_mappings",
                "question_id", "clo_id", "weight", "created_at");
        examResults = writer.table("exam_results",
                "id", "exam_id", "student_id", "total_obtained", "source", "recorded_by", "recorded_at",
                "remarks", "created_at");
        examQuestionMarks = writer.table("exam_question_marks",
                "id", "result_id", "question_id", "marks_obtained", "created_at");
        eventPublications = writer.table("event_publication",
                "id", "listener_id", "event_type", "serialized_event", "publication_date", "completion_date");
    }

    /**
     * Records an event as already delivered to its listener. The completion
     * date is always set: an incomplete publication would be re-sent by
     * Spring Modulith on the next application start.
     */
    void publishedEvent(String listenerId, Object event, LocalDateTime when) {
        var published = when.atZone(ZoneId.systemDefault()).toOffsetDateTime();
        eventPublications.add(UUID.randomUUID(), listenerId, event.getClass().getName(), json(event),
                published, published.plusSeconds(2));
    }

    String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Cannot serialise " + value, e);
        }
    }

    void flush() {
        writer.flush();
    }

    Map<String, Long> counts() {
        return writer.counts();
    }
}
