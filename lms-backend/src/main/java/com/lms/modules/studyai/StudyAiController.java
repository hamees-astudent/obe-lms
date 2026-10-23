package com.lms.modules.studyai;

import com.lms.infrastructure.security.UserPrincipal;
import com.lms.modules.studyai.dto.*;
import com.lms.shared.Role;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.UUID;

/**
 * HTTP API of the study assistant and question generator.
 *
 * <ul>
 *   <li>{@code /api/ai/status} — whether the local model is up (any user)</li>
 *   <li>{@code /api/offerings/{pscId}/ai/...} — indexing, topics, paper
 *       settings and generation for an offering's staff</li>
 *   <li>{@code /api/me/ai/...} — a student's chat and practice papers</li>
 *   <li>{@code /api/papers/...} — question papers and their printed sets</li>
 * </ul>
 */
@RestController
@RequiredArgsConstructor
public class StudyAiController {

    private final MaterialIndexService index;
    private final ChatService chat;
    private final MockService mocks;
    private final QuestionGenerationService generation;
    private final QuestionAssistService assist;
    private final PaperSettingsService paperSettings;
    private final PaperService papers;

    @GetMapping("/api/ai/status")
    @PreAuthorize("isAuthenticated()")
    public AiStatusResponse status() {
        return index.status();
    }

    // ── Indexing and topics ──────────────────────────────────────────────────

    @GetMapping("/api/offerings/{pscId}/ai/ingestion")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<IngestionStatusResponse> ingestion(@PathVariable UUID pscId, @AuthenticationPrincipal UserPrincipal p) {
        return index.ingestionStatus(pscId, p.getId(), isAdmin(p));
    }

    /** Re-reads one material, or every unindexed document of the offering when materialId is omitted. */
    @PostMapping("/api/offerings/{pscId}/ai/reindex")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public void reindex(@PathVariable UUID pscId, @RequestParam(required = false) UUID materialId,
                        @AuthenticationPrincipal UserPrincipal p) {
        index.reindex(pscId, materialId, p.getId(), isAdmin(p));
    }

    @GetMapping("/api/offerings/{pscId}/ai/topics")
    @PreAuthorize("isAuthenticated()")
    public List<TopicResponse> topics(@PathVariable UUID pscId, @AuthenticationPrincipal UserPrincipal p) {
        return index.topics(pscId, p.getId(), isAdmin(p));
    }

    // ── Paper settings (limits and marks table) ──────────────────────────────

    @GetMapping("/api/offerings/{pscId}/paper-settings")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperSettingsResponse getPaperSettings(@PathVariable UUID pscId, @AuthenticationPrincipal UserPrincipal p) {
        return paperSettings.get(pscId, p.getId(), isAdmin(p));
    }

    @PutMapping("/api/offerings/{pscId}/paper-settings")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public PaperSettingsResponse putPaperSettings(@PathVariable UUID pscId, @RequestBody @Valid PaperSettingsRequest req,
                                                  @AuthenticationPrincipal UserPrincipal p) {
        return paperSettings.update(pscId, req, p.getId(), isAdmin(p));
    }

    // ── Teacher: question generation ─────────────────────────────────────────

    @PostMapping("/api/offerings/{pscId}/ai/question-jobs/estimate")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public GenerationEstimateResponse estimate(@PathVariable UUID pscId, @RequestBody @Valid GenerationRequest req,
                                               @AuthenticationPrincipal UserPrincipal p) {
        return generation.estimate(pscId, req, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/offerings/{pscId}/ai/question-jobs")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public GenerationJobResponse createJob(@PathVariable UUID pscId, @RequestBody @Valid GenerationRequest req,
                                           @AuthenticationPrincipal UserPrincipal p) {
        return generation.createJob(pscId, req, p.getId(), isAdmin(p));
    }

    @GetMapping("/api/offerings/{pscId}/ai/question-jobs")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<GenerationJobResponse> listJobs(@PathVariable UUID pscId, @AuthenticationPrincipal UserPrincipal p) {
        return generation.listJobs(pscId, p.getId(), isAdmin(p));
    }

    @GetMapping("/api/ai/question-jobs/{jobId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public GenerationJobResponse getJob(@PathVariable UUID jobId, @AuthenticationPrincipal UserPrincipal p) {
        return generation.getJob(jobId, p.getId(), isAdmin(p));
    }

    @PutMapping("/api/ai/question-drafts/{draftId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuestionDraftResponse updateDraft(@PathVariable UUID draftId, @RequestBody @Valid UpdateDraftRequest req,
                                             @AuthenticationPrincipal UserPrincipal p) {
        return generation.updateDraft(draftId, req, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/ai/question-drafts/{draftId}/regenerate")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuestionDraftResponse regenerateDraft(@PathVariable UUID draftId, @AuthenticationPrincipal UserPrincipal p) {
        return generation.regenerateDraft(draftId, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/ai/question-jobs/{jobId}/save")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public SaveDraftsResponse save(@PathVariable UUID jobId, @RequestBody @Valid SaveDraftsRequest req,
                                   @AuthenticationPrincipal UserPrincipal p) {
        return generation.save(jobId, req, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/ai/question-assist")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public QuestionAssistResponse assist(@RequestBody @Valid QuestionAssistRequest req,
                                         @AuthenticationPrincipal UserPrincipal p) {
        return assist.assist(req, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/ai/quiz-submissions/{submissionId}/grade-suggestions")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<GradeSuggestionResponse> gradeSuggestions(@PathVariable UUID submissionId,
                                                          @AuthenticationPrincipal UserPrincipal p) {
        return assist.gradeSuggestions(submissionId, p.getId(), isAdmin(p));
    }

    // ── Teacher: question papers ─────────────────────────────────────────────

    @GetMapping("/api/offerings/{pscId}/papers")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public List<PaperResponse> listPapers(@PathVariable UUID pscId, @AuthenticationPrincipal UserPrincipal p) {
        return papers.list(pscId, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/offerings/{pscId}/papers")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse createPaper(@PathVariable UUID pscId, @RequestBody @Valid PaperRequest req,
                                     @AuthenticationPrincipal UserPrincipal p) {
        return papers.create(pscId, req, p.getId(), isAdmin(p));
    }

    @GetMapping("/api/papers/{paperId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse getPaper(@PathVariable UUID paperId, @AuthenticationPrincipal UserPrincipal p) {
        return papers.get(paperId, p.getId(), isAdmin(p));
    }

    @PutMapping("/api/papers/{paperId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse updatePaper(@PathVariable UUID paperId, @RequestBody @Valid PaperRequest req,
                                     @AuthenticationPrincipal UserPrincipal p) {
        return papers.update(paperId, req, p.getId(), isAdmin(p));
    }

    @DeleteMapping("/api/papers/{paperId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER')")
    public void deletePaper(@PathVariable UUID paperId, @AuthenticationPrincipal UserPrincipal p) {
        papers.delete(paperId, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/papers/{paperId}/items")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public AddPaperItemsResponse addItems(@PathVariable UUID paperId, @RequestBody AddPaperItemsRequest req,
                                          @AuthenticationPrincipal UserPrincipal p) {
        return papers.addItems(paperId, req, p.getId(), isAdmin(p));
    }

    @PutMapping("/api/paper-items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse updateItem(@PathVariable UUID itemId, @RequestBody @Valid UpdatePaperItemRequest req,
                                    @AuthenticationPrincipal UserPrincipal p) {
        return papers.updateItem(itemId, req, p.getId(), isAdmin(p));
    }

    @DeleteMapping("/api/paper-items/{itemId}")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse deleteItem(@PathVariable UUID itemId, @AuthenticationPrincipal UserPrincipal p) {
        return papers.deleteItem(itemId, p.getId(), isAdmin(p));
    }

    @PutMapping("/api/papers/{paperId}/order")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse reorder(@PathVariable UUID paperId, @RequestBody @Valid ReorderPaperItemsRequest req,
                                 @AuthenticationPrincipal UserPrincipal p) {
        return papers.reorder(paperId, req, p.getId(), isAdmin(p));
    }

    @PostMapping("/api/papers/{paperId}/print-sets")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public PaperResponse print(@PathVariable UUID paperId, @RequestBody @Valid PrintPaperRequest req,
                               @AuthenticationPrincipal UserPrincipal p) {
        return papers.print(paperId, req, p.getId(), isAdmin(p));
    }

    @GetMapping("/api/papers/{paperId}/print-sets/{setCode}/pdf")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ResponseEntity<byte[]> setPdf(@PathVariable UUID paperId, @PathVariable String setCode,
                                         @RequestParam(defaultValue = "false") boolean answerKey,
                                         @AuthenticationPrincipal UserPrincipal p) {
        byte[] pdf = papers.setPdf(paperId, setCode, answerKey, p.getId(), isAdmin(p));
        String name = papers.fileName(paperId, "-Set-" + setCode.toUpperCase() + (answerKey ? "-answer-key" : "") + ".pdf");
        return file(pdf, MediaType.APPLICATION_PDF, name);
    }

    @GetMapping("/api/papers/{paperId}/print-sets.zip")
    @PreAuthorize("hasAnyRole('ADMIN','TEACHER','ASSISTANT')")
    public ResponseEntity<byte[]> zip(@PathVariable UUID paperId, @AuthenticationPrincipal UserPrincipal p) {
        byte[] zip = papers.zip(paperId, p.getId(), isAdmin(p));
        return file(zip, MediaType.parseMediaType("application/zip"), papers.fileName(paperId, "-all-sets.zip"));
    }

    // ── Student: chat ────────────────────────────────────────────────────────

    @GetMapping("/api/me/ai/courses")
    @PreAuthorize("hasRole('STUDENT')")
    public List<StudyCourseResponse> courses(@AuthenticationPrincipal UserPrincipal p) {
        return chat.courses(p.getId());
    }

    @PostMapping("/api/me/ai/chat/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('STUDENT')")
    public ChatSessionResponse createSession(@RequestBody @Valid CreateChatSessionRequest req,
                                             @AuthenticationPrincipal UserPrincipal p) {
        return chat.createSession(p.getId(), req.pscId());
    }

    @GetMapping("/api/me/ai/chat/sessions")
    @PreAuthorize("hasRole('STUDENT')")
    public List<ChatSessionResponse> sessions(@RequestParam(required = false) UUID pscId,
                                              @AuthenticationPrincipal UserPrincipal p) {
        return chat.listSessions(p.getId(), pscId);
    }

    @GetMapping("/api/me/ai/chat/sessions/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public ChatSessionResponse session(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
        return chat.getSession(id, p.getId());
    }

    @DeleteMapping("/api/me/ai/chat/sessions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('STUDENT')")
    public void deleteSession(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
        chat.deleteSession(id, p.getId());
    }

    /** The answer streams back as server-sent events: delta…, citations, done (or error). */
    @PostMapping(value = "/api/me/ai/chat/sessions/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public SseEmitter ask(@PathVariable UUID id, @RequestBody @Valid ChatMessageRequest req,
                          @AuthenticationPrincipal UserPrincipal p) {
        return chat.ask(id, p.getId(), req.content());
    }

    // ── Student: practice papers ─────────────────────────────────────────────

    @PostMapping("/api/me/ai/mocks")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @PreAuthorize("hasRole('STUDENT')")
    public MockAttemptResponse createMock(@RequestBody @Valid MockRequest req, @AuthenticationPrincipal UserPrincipal p) {
        return mocks.create(p.getId(), req);
    }

    @GetMapping("/api/me/ai/mocks")
    @PreAuthorize("hasRole('STUDENT')")
    public List<MockAttemptResponse> listMocks(@RequestParam(required = false) UUID pscId,
                                               @AuthenticationPrincipal UserPrincipal p) {
        return mocks.list(p.getId(), pscId);
    }

    @GetMapping("/api/me/ai/mocks/{id}")
    @PreAuthorize("hasRole('STUDENT')")
    public MockAttemptResponse getMock(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal p) {
        return mocks.get(id, p.getId());
    }

    @PutMapping("/api/me/ai/mocks/{id}/answers")
    @PreAuthorize("hasRole('STUDENT')")
    public MockAttemptResponse saveMockAnswers(@PathVariable UUID id, @RequestBody @Valid MockAnswersRequest req,
                                               @AuthenticationPrincipal UserPrincipal p) {
        return mocks.saveAnswers(id, p.getId(), req);
    }

    @PostMapping("/api/me/ai/mocks/{id}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public MockAttemptResponse submitMock(@PathVariable UUID id, @RequestBody(required = false) MockAnswersRequest req,
                                          @AuthenticationPrincipal UserPrincipal p) {
        return mocks.submit(id, p.getId(), req);
    }

    private static ResponseEntity<byte[]> file(byte[] body, MediaType type, String name) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(body);
    }

    private static boolean isAdmin(UserPrincipal p) {
        return p.getRole() == Role.ADMIN;
    }
}
