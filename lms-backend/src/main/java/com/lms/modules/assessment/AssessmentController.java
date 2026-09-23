package com.lms.modules.assessment;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.assessment.dto.*;
import com.lms.shared.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AssessmentController {

    private final AssessmentService assessmentService;

    // ── Assignments ───────────────────────────────────────────────────────────

    @PostMapping("/api/offerings/{pscId}/assignments")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AssignmentResponse createAssignment(
            @PathVariable UUID pscId,
            @RequestBody @Valid CreateAssignmentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        req.setPscId(pscId);
        return assessmentService.createAssignment(principal.getId(), isAdmin(principal), req);
    }

    @GetMapping("/api/offerings/{pscId}/assignments")
    @PreAuthorize("isAuthenticated()")
    public List<AssignmentResponse> listAssignments(@PathVariable UUID pscId) {
        return assessmentService.listAssignments(pscId);
    }

    @GetMapping("/api/assignments/{id}")
    @PreAuthorize("isAuthenticated()")
    public AssignmentResponse getAssignment(@PathVariable UUID id) {
        return assessmentService.getAssignment(id);
    }

    @PutMapping("/api/assignments/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AssignmentResponse updateAssignment(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateAssignmentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.updateAssignment(id, req, principal.getId(), isAdmin(principal));
    }

    @DeleteMapping("/api/assignments/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void deleteAssignment(@PathVariable UUID id,
                                 @AuthenticationPrincipal UserPrincipal principal) {
        assessmentService.deleteAssignment(id, principal.getId(), isAdmin(principal));
    }

    // ── Assignment Submissions ────────────────────────────────────────────────

    @PostMapping("/api/assignments/{id}/submit")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public AssignmentSubmissionResponse submitAssignment(
            @PathVariable UUID id,
            @RequestBody SubmitAssignmentRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.submitAssignment(id, principal.getId(), req);
    }

    @PostMapping("/api/submissions/assignments/{submissionId}/grade")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AssignmentSubmissionResponse gradeSubmission(
            @PathVariable UUID submissionId,
            @RequestBody @Valid GradeSubmissionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.gradeSubmission(submissionId, principal.getId(), isAdmin(principal), req);
    }

    @GetMapping("/api/assignments/{id}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<AssignmentSubmissionResponse> listSubmissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.listSubmissions(id, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/submissions/assignments/{submissionId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AssignmentSubmissionResponse getSubmission(
            @PathVariable UUID submissionId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.getSubmission(submissionId, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/me/assignments/{id}/submission")
    @PreAuthorize("hasRole('STUDENT')")
    public AssignmentSubmissionResponse getMySubmission(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.getMySubmission(id, principal.getId());
    }

    // ── Quizzes ───────────────────────────────────────────────────────────────

    @PostMapping("/api/offerings/{pscId}/quizzes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuizResponse createQuiz(
            @PathVariable UUID pscId,
            @RequestBody @Valid CreateQuizRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        req.setPscId(pscId);
        return assessmentService.createQuiz(principal.getId(), isAdmin(principal), req);
    }

    @GetMapping("/api/offerings/{pscId}/quizzes")
    @PreAuthorize("isAuthenticated()")
    public List<QuizResponse> listQuizzes(@PathVariable UUID pscId) {
        return assessmentService.listQuizzes(pscId);
    }

    @GetMapping("/api/quizzes/{id}")
    @PreAuthorize("isAuthenticated()")
    public QuizResponse getQuiz(@PathVariable UUID id) {
        return assessmentService.getQuiz(id);
    }

    @PutMapping("/api/quizzes/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuizResponse updateQuiz(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateQuizRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.updateQuiz(id, req, principal.getId(), isAdmin(principal));
    }

    @DeleteMapping("/api/quizzes/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void deleteQuiz(@PathVariable UUID id,
                           @AuthenticationPrincipal UserPrincipal principal) {
        assessmentService.deleteQuiz(id, principal.getId(), isAdmin(principal));
    }

    // ── Quiz Questions ────────────────────────────────────────────────────────

    @PostMapping("/api/quizzes/{id}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuizQuestionResponse addQuestion(
            @PathVariable UUID id,
            @RequestBody @Valid CreateQuizQuestionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.addQuestion(id, req, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/quizzes/{id}/questions")
    @PreAuthorize("isAuthenticated()")
    public List<QuizQuestionResponse> listQuestions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        boolean hasStaffRole = principal.getRole() == Role.TEACHER
                || principal.getRole() == Role.ASSISTANT;
        return assessmentService.listQuestions(id, principal.getId(), isAdmin(principal), hasStaffRole);
    }

    @PutMapping("/api/quiz-questions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuizQuestionResponse updateQuestion(
            @PathVariable UUID id,
            @RequestBody @Valid UpdateQuizQuestionRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.updateQuestion(id, req, principal.getId(), isAdmin(principal));
    }

    @DeleteMapping("/api/quiz-questions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public void deleteQuestion(@PathVariable UUID id,
                               @AuthenticationPrincipal UserPrincipal principal) {
        assessmentService.deleteQuestion(id, principal.getId(), isAdmin(principal));
    }

    // ── Quiz Submissions ──────────────────────────────────────────────────────

    @PostMapping("/api/quizzes/{id}/start")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public QuizSubmissionResponse startQuiz(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.startQuiz(id, principal.getId());
    }

    @PutMapping("/api/quiz-submissions/{id}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public QuizSubmissionResponse saveAnswers(
            @PathVariable UUID id,
            @RequestBody @Valid SubmitQuizAnswersRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.saveAnswers(id, principal.getId(), req);
    }

    @PostMapping("/api/quiz-submissions/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public QuizSubmissionResponse submitQuiz(
            @PathVariable UUID id,
            @RequestBody(required = false) @Valid SubmitQuizAnswersRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.submitQuiz(id, principal.getId(),
                req == null ? null : req.getAnswers());
    }

    @GetMapping("/api/quizzes/{id}/submissions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<QuizSubmissionResponse> listQuizSubmissions(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.listQuizSubmissions(id, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/quiz-submissions/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuizSubmissionResponse getQuizSubmission(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.getQuizSubmission(id, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/me/quizzes/{id}/submission")
    @PreAuthorize("hasRole('STUDENT')")
    public QuizSubmissionResponse getMyQuizSubmission(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.getMyQuizSubmission(id, principal.getId());
    }

    // ── CLO Mappings: Assignments ─────────────────────────────────────────────

    // Not under /api/admin/**: the security filter makes that prefix admin-only,
    // so teachers were refused before @PreAuthorize was even consulted.
    @PostMapping("/api/assignments/{id}/clo-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public CloMappingResponse addAssignmentCloMapping(
            @PathVariable UUID id,
            @RequestBody @Valid CloMappingRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.addAssignmentCloMapping(id, req, principal.getId(), isAdmin(principal));
    }

    @DeleteMapping("/api/assignments/{id}/clo-mappings/{cloId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void removeAssignmentCloMapping(@PathVariable UUID id, @PathVariable UUID cloId,
                                           @AuthenticationPrincipal UserPrincipal principal) {
        assessmentService.removeAssignmentCloMapping(id, cloId, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/assignments/{id}/clo-mappings")
    @PreAuthorize("isAuthenticated()")
    public List<CloMappingResponse> listAssignmentCloMappings(@PathVariable UUID id) {
        return assessmentService.listAssignmentCloMappings(id);
    }

    // ── CLO Mappings: Quizzes ─────────────────────────────────────────────────

    @PostMapping("/api/quizzes/{id}/clo-mappings")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public CloMappingResponse addQuizCloMapping(
            @PathVariable UUID id,
            @RequestBody @Valid CloMappingRequest req,
            @AuthenticationPrincipal UserPrincipal principal) {
        return assessmentService.addQuizCloMapping(id, req, principal.getId(), isAdmin(principal));
    }

    @DeleteMapping("/api/quizzes/{id}/clo-mappings/{cloId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void removeQuizCloMapping(@PathVariable UUID id, @PathVariable UUID cloId,
                                     @AuthenticationPrincipal UserPrincipal principal) {
        assessmentService.removeQuizCloMapping(id, cloId, principal.getId(), isAdmin(principal));
    }

    @GetMapping("/api/quizzes/{id}/clo-mappings")
    @PreAuthorize("isAuthenticated()")
    public List<CloMappingResponse> listQuizCloMappings(@PathVariable UUID id) {
        return assessmentService.listQuizCloMappings(id);
    }

    private static boolean isAdmin(UserPrincipal principal) {
        return principal.getRole() == Role.ADMIN;
    }
}
