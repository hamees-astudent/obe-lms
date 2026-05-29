package com.lms.modules.notifications;

import com.lms.shared.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.function.Consumer;

/**
 * Spring Cloud Stream consumer beans for all four Kafka event topics.
 *
 * <p>Binding names map to {@code spring.cloud.function.definition}:
 * <ul>
 *   <li>{@code enrollmentNotificationHandler-in-0} → {@code lms.enrollment.events}</li>
 *   <li>{@code attendanceAlertHandler-in-0}        → {@code lms.attendance.events}</li>
 *   <li>{@code assessmentEventHandler-in-0}        → {@code lms.assessment.events}</li>
 *   <li>{@code semesterEventHandler-in-0}          → {@code lms.semester.events}</li>
 * </ul>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class NotificationKafkaConsumers {

    private final NotificationService notificationService;

    @Bean
    public Consumer<EnrollmentEvent> enrollmentNotificationHandler() {
        return event -> {
            log.debug("Received EnrollmentEvent: action={} student={}",
                    event.getAction(), event.getStudentEmail());
            notificationService.handleEnrollmentEvent(event);
        };
    }

    @Bean
    public Consumer<AttendanceAlertEvent> attendanceAlertHandler() {
        return event -> {
            log.debug("Received AttendanceAlertEvent: student={} course={}",
                    event.getStudentEmail(), event.getCourseCode());
            notificationService.handleAttendanceAlertEvent(event);
        };
    }

    @Bean
    public Consumer<AssessmentEvent> assessmentEventHandler() {
        return event -> {
            log.debug("Received AssessmentEvent: action={} student={}",
                    event.getAction(), event.getStudentEmail());
            notificationService.handleAssessmentEvent(event);
        };
    }

    @Bean
    public Consumer<SemesterEvent> semesterEventHandler() {
        return event -> {
            log.info("Received SemesterEvent: action={} semester={}",
                    event.getAction(), event.getSemesterName());
            notificationService.handleSemesterEvent(event);
        };
    }
}
