package com.lms.infrastructure.messaging;

/**
 * String constants for every Kafka topic and Spring Cloud Stream binding name used
 * in the application.
 *
 * <ul>
 *   <li><b>TOPIC_*</b> — physical Kafka topic names (used for {@code StreamBridge}
 *       publishing and {@code NewTopic} beans).</li>
 *   <li><b>BINDING_*</b> — Spring Cloud Stream binding names for consumer functions
 *       declared in {@code spring.cloud.function.definition}.</li>
 * </ul>
 */
public final class KafkaTopics {

    // ── Physical topic names ─────────────────────────────────────────────────

    public static final String TOPIC_ENROLLMENT  = "lms.enrollment.events";
    public static final String TOPIC_ATTENDANCE  = "lms.attendance.events";
    public static final String TOPIC_ASSESSMENT  = "lms.assessment.events";
    public static final String TOPIC_SEMESTER    = "lms.semester.events";

    // ── Stream binding names (consumer function name + "-in-0") ──────────────

    /** Binding for notifications module's enrollment consumer. */
    public static final String BINDING_ENROLLMENT_IN  = "enrollmentNotificationHandler-in-0";

    /** Binding for notifications module's attendance-alert consumer. */
    public static final String BINDING_ATTENDANCE_IN  = "attendanceAlertHandler-in-0";

    /** Binding for notifications module's assessment consumer. */
    public static final String BINDING_ASSESSMENT_IN  = "assessmentEventHandler-in-0";

    /** Binding for notifications module's semester consumer. */
    public static final String BINDING_SEMESTER_IN    = "semesterEventHandler-in-0";

    /** Binding for transcript module's semester consumer (separate consumer group). */
    public static final String BINDING_TRANSCRIPT_SEMESTER_IN = "transcriptSemesterHandler-in-0";

    private KafkaTopics() {}
}
