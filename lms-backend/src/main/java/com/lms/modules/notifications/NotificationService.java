package com.lms.modules.notifications;

import com.lms.modules.notifications.NotificationDataRepository.UserRow;
import com.lms.modules.notifications.dto.NotificationPage;
import com.lms.modules.notifications.dto.NotificationResponse;
import com.lms.shared.events.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository     notifRepo;
    private final NotificationDataRepository dataRepo;
    private final NotificationEmailService   emailService;

    // ── Event Handlers ───────────────────────────────────────────────────────

    public void handleEnrollmentEvent(EnrollmentEvent event) {
        boolean enrolled = event.getAction() == EnrollmentEvent.Action.ENROLLED;
        String eventType = enrolled ? "ENROLLMENT_CONFIRMED" : "ENROLLMENT_DROPPED";

        String title = enrolled
                ? "Enrollment Confirmed — " + event.getCourseCode()
                : "Enrollment Dropped — " + event.getCourseCode();

        String body = enrolled
                ? String.format("You have been enrolled in %s for %s (%s).",
                        event.getCourseName(), event.getSemesterName(), event.getProgramName())
                : String.format("Your enrollment in %s for %s has been dropped.",
                        event.getCourseName(), event.getSemesterName());

        persist(event.getStudentId(), title, body, eventType,
                "ENROLLMENT", event.getProgramSemesterCourseId());

        String emailSubject = "[LMS] " + title;
        String emailBody = "Dear " + event.getStudentName() + ",\n\n" + body
                + "\n\nPlease log in to the LMS portal to view your updated course list."
                + "\n\n— LMS Team";
        emailService.sendEmail(event.getStudentEmail(), emailSubject, emailBody);
    }

    public void handleAttendanceAlertEvent(AttendanceAlertEvent event) {
        String title = "Attendance Alert — " + event.getCourseCode();
        String body = String.format(
                "Your attendance in %s (%s) is %.1f%%, which is below the required %.1f%%.",
                event.getCourseName(), event.getSemesterName(),
                event.getAttendancePercentage(), event.getThresholdPercentage());

        persist(event.getStudentId(), title, body, "ATTENDANCE_ALERT", null, null);

        String emailBody = "Dear " + event.getStudentName() + ",\n\n" + body
                + "\n\nPlease attend your classes regularly to avoid academic penalties."
                + "\n\n— LMS Team";
        emailService.sendEmail(event.getStudentEmail(), "[LMS] " + title, emailBody);
    }

    public void handleAssessmentEvent(AssessmentEvent event) {
        switch (event.getAction()) {
            case ASSIGNMENT_SUBMITTED -> handleAssignmentSubmitted(event);
            case ASSIGNMENT_GRADED    -> handleAssignmentGraded(event);
            case QUIZ_SUBMITTED       -> handleQuizSubmitted(event);
        }
    }

    private void handleAssignmentSubmitted(AssessmentEvent event) {
        // Notify student: submission confirmed
        String title = "Assignment Submitted — " + event.getAssessmentTitle();
        String body  = String.format("Your submission for '%s' in %s (%s) has been received.",
                event.getAssessmentTitle(), event.getCourseName(), event.getCourseCode());

        persist(event.getStudentId(), title, body, "ASSIGNMENT_SUBMITTED",
                "ASSIGNMENT", event.getAssessmentId());

        emailService.sendEmail(event.getStudentEmail(),
                "[LMS] " + title,
                "Dear " + event.getStudentName() + ",\n\n" + body
                        + "\n\n— LMS Team");

        // Notify teacher: new submission to review
        dataRepo.findAssignmentCreator(event.getAssessmentId()).ifPresent(teacher -> {
            String teacherTitle = "New Submission — " + event.getAssessmentTitle();
            String teacherBody  = String.format("%s submitted '%s' in %s (%s).",
                    event.getStudentName(), event.getAssessmentTitle(),
                    event.getCourseName(), event.getCourseCode());

            persist(teacher.id(), teacherTitle, teacherBody, "ASSIGNMENT_SUBMITTED",
                    "ASSIGNMENT", event.getAssessmentId());

            emailService.sendEmail(teacher.email(),
                    "[LMS] " + teacherTitle,
                    "Hello,\n\n" + teacherBody + "\n\nPlease log in to grade the submission.\n\n— LMS Team");
        });
    }

    private void handleAssignmentGraded(AssessmentEvent event) {
        String score = event.getMarksObtained() != null && event.getTotalMarks() != null
                ? String.format("%.1f/%.1f", event.getMarksObtained(), event.getTotalMarks())
                : "N/A";

        String title = "Assignment Graded — " + event.getAssessmentTitle();
        String body  = String.format(
                "Your submission for '%s' in %s has been graded. Score: %s.",
                event.getAssessmentTitle(), event.getCourseCode(), score);

        persist(event.getStudentId(), title, body, "ASSIGNMENT_GRADED",
                "ASSIGNMENT", event.getAssessmentId());

        String feedbackSection = (event.getFeedback() != null && !event.getFeedback().isBlank())
                ? "\n\nFeedback: " + event.getFeedback()
                : "";

        emailService.sendEmail(event.getStudentEmail(),
                "[LMS] " + title,
                "Dear " + event.getStudentName() + ",\n\n" + body + feedbackSection
                        + "\n\n— LMS Team");
    }

    private void handleQuizSubmitted(AssessmentEvent event) {
        // Notify student: quiz submission confirmed
        String title = "Quiz Submitted — " + event.getAssessmentTitle();
        String body  = String.format("Your attempt for quiz '%s' in %s has been recorded.",
                event.getAssessmentTitle(), event.getCourseCode());

        persist(event.getStudentId(), title, body, "QUIZ_SUBMITTED",
                "QUIZ", event.getAssessmentId());

        emailService.sendEmail(event.getStudentEmail(),
                "[LMS] " + title,
                "Dear " + event.getStudentName() + ",\n\n" + body + "\n\n— LMS Team");

        // Notify teacher: new quiz attempt
        dataRepo.findQuizCreator(event.getAssessmentId()).ifPresent(teacher -> {
            String teacherTitle = "New Quiz Attempt — " + event.getAssessmentTitle();
            String teacherBody  = String.format("%s completed quiz '%s' in %s (%s).",
                    event.getStudentName(), event.getAssessmentTitle(),
                    event.getCourseName(), event.getCourseCode());

            persist(teacher.id(), teacherTitle, teacherBody, "QUIZ_SUBMITTED",
                    "QUIZ", event.getAssessmentId());

            emailService.sendEmail(teacher.email(),
                    "[LMS] " + teacherTitle,
                    "Hello,\n\n" + teacherBody + "\n\n— LMS Team");
        });
    }

    public void handleSemesterEvent(SemesterEvent event) {
        boolean closed = event.getAction() == SemesterEvent.Action.CLOSED;
        String eventType = closed ? "SEMESTER_CLOSED" : "SEMESTER_REOPENED";
        String verb      = closed ? "closed" : "reopened";

        String title = String.format("Semester %s — %s", verb.substring(0, 1).toUpperCase() + verb.substring(1),
                event.getSemesterName());
        String body  = String.format(
                "Semester '%s' for program '%s' has been %s.",
                event.getSemesterName(), event.getProgramName(), verb);

        // In-app notification for every enrolled student
        List<UserRow> students = dataRepo.findEnrolledStudentsByProgramSemester(event.getSemesterId());
        for (UserRow student : students) {
            persist(student.id(), title, body, eventType, "SEMESTER", event.getSemesterId());
        }

        // Email confirmation to the admin who triggered the change
        if (event.getTriggeredByEmail() != null) {
            emailService.sendEmail(event.getTriggeredByEmail(),
                    "[LMS] Semester " + verb + " — " + event.getSemesterName(),
                    "Hello,\n\nSemester '" + event.getSemesterName() + "' for '"
                            + event.getProgramName() + "' has been " + verb + " successfully."
                            + (closed ? " Transcripts will be generated shortly." : "")
                            + "\n\n— LMS Team");
        }

        log.info("Semester {} event processed: {} student notifications queued, semesterId={}",
                verb, students.size(), event.getSemesterId());
    }

    // ── REST operations ──────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public NotificationPage listNotifications(UUID recipientId, boolean unreadOnly,
                                              int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> resultPage = unreadOnly
                ? notifRepo.findAllByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(recipientId, pageable)
                : notifRepo.findAllByRecipientIdOrderByCreatedAtDesc(recipientId, pageable);

        long unreadCount = notifRepo.countByRecipientIdAndIsReadFalse(recipientId);

        return NotificationPage.builder()
                .content(resultPage.getContent().stream().map(this::toResponse).toList())
                .page(resultPage.getNumber())
                .size(resultPage.getSize())
                .totalElements(resultPage.getTotalElements())
                .totalPages(resultPage.getTotalPages())
                .unreadCount(unreadCount)
                .build();
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(UUID recipientId) {
        return notifRepo.countByRecipientIdAndIsReadFalse(recipientId);
    }

    public NotificationResponse markAsRead(UUID id, UUID requesterId) {
        Notification n = notifRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found"));
        if (!n.getRecipientId().equals(requesterId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cannot mark another user's notification as read");
        }
        if (!n.isRead()) {
            n.setRead(true);
            n.setReadAt(LocalDateTime.now());
            notifRepo.save(n);
        }
        return toResponse(n);
    }

    public int markAllAsRead(UUID recipientId) {
        return notifRepo.markAllReadByRecipientId(recipientId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Notification persist(UUID recipientId, String title, String body,
                                 String eventType, String referenceType, UUID referenceId) {
        Notification n = new Notification();
        n.setRecipientId(recipientId);
        n.setTitle(title);
        n.setBody(body);
        n.setEventType(eventType);
        n.setReferenceType(referenceType);
        n.setReferenceId(referenceId);
        return notifRepo.save(n);
    }

    public UUID resolveUserId(String email) {
        return dataRepo.findUserIdByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + email));
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .recipientId(n.getRecipientId())
                .title(n.getTitle())
                .body(n.getBody())
                .eventType(n.getEventType())
                .referenceType(n.getReferenceType())
                .referenceId(n.getReferenceId())
                .isRead(n.isRead())
                .readAt(n.getReadAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
