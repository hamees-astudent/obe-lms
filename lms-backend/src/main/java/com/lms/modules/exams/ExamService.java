package com.lms.modules.exams;

import com.lms.modules.exams.ExamDataRepository.CloRow;
import com.lms.modules.exams.ExamDataRepository.OfferingRow;
import com.lms.modules.exams.ExamDataRepository.StudentRow;
import com.lms.modules.exams.dto.*;
import com.lms.shared.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Exam definitions, their question structure, and the confirmed marks recorded
 * against them.
 *
 * <p>Every path that writes marks funnels through
 * {@link #writeResult}, so the rules that protect an academic record — the exam
 * must be open, the student must be on the roster, a mark may not exceed the
 * question's maximum — are enforced in exactly one place regardless of whether
 * the marks were typed in or came from a scan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExamService {

    private final ExamRepository                   examRepo;
    private final ExamQuestionRepository           questionRepo;
    private final ExamQuestionCloMappingRepository cloMappingRepo;
    private final ExamResultRepository             resultRepo;
    private final ExamQuestionMarkRepository       questionMarkRepo;
    private final ExamDataRepository               dataRepo;

    // ── Exam CRUD ────────────────────────────────────────────────────────────

    public ExamResponse createExam(UUID actorId, Role actorRole, CreateExamRequest req) {
        requireOfferingAccess(req.getPscId(), actorId, actorRole);

        if (examRepo.existsByPscIdAndExamTypeAndExamDate(
                req.getPscId(), req.getExamType(), req.getExamDate())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A " + req.getExamType() + " already exists for this course on " + req.getExamDate()
                    + ". Scans are matched by course and date, so two would be ambiguous.");
        }

        Exam exam = new Exam();
        exam.setPscId(req.getPscId());
        exam.setCreatedBy(actorId);
        exam.setTitle(req.getTitle());
        exam.setExamType(req.getExamType());
        exam.setExamDate(req.getExamDate());
        exam.setTotalMarks(req.getTotalMarks());
        exam.setStatus(Exam.STATUS_DRAFT);
        Exam saved = examRepo.save(exam);

        if (req.getQuestions() != null && !req.getQuestions().isEmpty()) {
            replaceQuestions(saved, req.getQuestions());
        }
        return toResponse(saved);
    }

    public ExamResponse updateExam(UUID examId, UUID actorId, Role actorRole, UpdateExamRequest req) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        requireNotLocked(exam);

        exam.setTitle(req.getTitle());
        exam.setExamType(req.getExamType());
        exam.setExamDate(req.getExamDate());
        exam.setTotalMarks(req.getTotalMarks());

        // A null question list means "leave the structure alone"; an empty one
        // means "remove every question", which is only safe before any marks
        // exist, since results hang off the question rows.
        if (req.getQuestions() != null) {
            requireNoResults(exam, "change the question list");
            replaceQuestions(exam, req.getQuestions());
        }
        return toResponse(examRepo.save(exam));
    }

    public ExamResponse changeStatus(UUID examId, UUID actorId, Role actorRole, String status) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);

        if (Exam.STATUS_OPEN.equals(status)) {
            List<ExamQuestion> questions = questionRepo.findAllByExamIdOrderByOrderIndexAsc(examId);
            if (questions.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Add the exam's questions before opening it — a scan is checked against them.");
            }
            BigDecimal sum = questions.stream()
                    .map(ExamQuestion::getMaxMarks)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (sum.compareTo(exam.getTotalMarks()) != 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "The questions add up to " + sum + " but the exam is out of "
                        + exam.getTotalMarks() + ". Fix one or the other before opening.");
            }
        }
        exam.setStatus(status);
        return toResponse(examRepo.save(exam));
    }

    public void deleteExam(UUID examId, UUID actorId, Role actorRole) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        requireNoResults(exam, "delete this exam");
        examRepo.delete(exam);
    }

    @Transactional(readOnly = true)
    public ExamResponse getExam(UUID examId) {
        return toResponse(findExamOrThrow(examId));
    }

    @Transactional(readOnly = true)
    public List<ExamResponse> listExams(UUID pscId) {
        return examRepo.findAllByPscIdOrderByExamDateDesc(pscId).stream()
                .map(this::toResponse)
                .toList();
    }

    // ── Questions ────────────────────────────────────────────────────────────

    /**
     * Replace the exam's question list wholesale.
     *
     * <p>Wholesale rather than incremental because the list is a single
     * coherent structure — the labels have to stay unique and the maxima have to
     * add up — and reconciling a partial edit against existing marks is a
     * problem best avoided by refusing the edit once marks exist.
     */
    private void replaceQuestions(Exam exam, List<ExamQuestionRequest> requested) {
        Set<String> seen = new HashSet<>();
        for (ExamQuestionRequest q : requested) {
            String label = q.getQuestionNo().trim();
            if (!seen.add(label.toUpperCase())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Question '" + label + "' is listed twice. Labels are how a scanned row is "
                        + "matched to a question, so each must appear once.");
            }
        }

        questionRepo.deleteAllByExamId(exam.getId());
        questionRepo.flush();

        int order = 1;
        for (ExamQuestionRequest req : requested) {
            ExamQuestion question = new ExamQuestion();
            question.setExamId(exam.getId());
            question.setQuestionNo(req.getQuestionNo().trim());
            question.setMaxMarks(req.getMaxMarks());
            question.setOrderIndex(order++);
            ExamQuestion savedQuestion = questionRepo.save(question);

            for (CloMappingRequest mapping : req.getCloMappings()) {
                if (!dataRepo.cloBelongsToOffering(exam.getPscId(), mapping.getCloId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "CLO " + mapping.getCloId() + " does not belong to this course.");
                }
                cloMappingRepo.save(ExamQuestionCloMapping.of(
                        savedQuestion, mapping.getCloId(), mapping.getWeight()));
            }
        }
    }

    // ── Recording marks ──────────────────────────────────────────────────────

    /** Manual mark entry, for a copy that was not scanned. */
    public ExamResultResponse recordMarks(UUID examId, UUID actorId, Role actorRole,
                                          RecordMarksRequest req) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        ExamResult result = writeResult(exam, req.getStudentId(), req.getMarks(), actorId,
                ExamResult.SOURCE_MANUAL, null, req.getRemarks());
        return toResultResponse(exam, result);
    }

    /**
     * Write or overwrite one student's marks for an exam.
     *
     * <p>The single gate every mark passes through, whether it was typed in or
     * confirmed from a scan. An existing result is updated rather than
     * duplicated, so re-scanning a copy corrects the record instead of adding a
     * second one.
     *
     * @param source   {@link ExamResult#SOURCE_SCAN} or {@link ExamResult#SOURCE_MANUAL}
     * @param scanId   the originating scan when {@code source} is SCAN, else null
     */
    ExamResult writeResult(Exam exam, UUID studentId, List<QuestionMarkEntry> entries,
                           UUID recordedBy, String source, UUID scanId, String remarks) {

        if (!exam.acceptsMarks()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    Exam.STATUS_LOCKED.equals(exam.getStatus())
                            ? "This exam is locked; its marks can no longer be changed."
                            : "Open the exam before recording marks against it.");
        }
        if (!dataRepo.isEnrolled(exam.getPscId(), studentId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "That student is not enrolled in this course offering.");
        }

        Map<UUID, ExamQuestion> questions = questionRepo
                .findAllByExamIdOrderByOrderIndexAsc(exam.getId()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        // Validate the whole submission before writing any of it, so a bad row
        // cannot leave a half-recorded result behind.
        Set<UUID> seen = new HashSet<>();
        for (QuestionMarkEntry entry : entries) {
            ExamQuestion question = questions.get(entry.getQuestionId());
            if (question == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Question " + entry.getQuestionId() + " does not belong to this exam.");
            }
            if (!seen.add(entry.getQuestionId())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Question '" + question.getQuestionNo() + "' was submitted twice.");
            }
            BigDecimal mark = entry.getMarksObtained();
            if (mark != null && mark.compareTo(question.getMaxMarks()) > 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Question '" + question.getQuestionNo() + "' was given " + mark
                        + " but is only out of " + question.getMaxMarks() + ".");
            }
        }

        BigDecimal total = entries.stream()
                .map(QuestionMarkEntry::getMarksObtained)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        ExamResult result = resultRepo.findByExamIdAndStudentId(exam.getId(), studentId)
                .orElseGet(ExamResult::new);
        result.setExamId(exam.getId());
        result.setStudentId(studentId);
        result.setTotalObtained(total);
        result.setSource(source);
        result.setScanId(scanId);
        result.setRecordedBy(recordedBy);
        result.setRecordedAt(LocalDateTime.now());
        result.setRemarks(remarks);
        ExamResult saved = resultRepo.save(result);

        // Replace the per-question rows outright: a correction that removes a
        // question's mark must not leave the old value behind.
        questionMarkRepo.deleteAllByResultId(saved.getId());
        questionMarkRepo.flush();
        for (QuestionMarkEntry entry : entries) {
            ExamQuestionMark mark = new ExamQuestionMark();
            mark.setResultId(saved.getId());
            mark.setQuestionId(entry.getQuestionId());
            mark.setMarksObtained(entry.getMarksObtained());
            questionMarkRepo.save(mark);
        }

        log.info("Exam marks recorded: exam={} student={} total={} source={} by={}",
                exam.getId(), studentId, total, source, recordedBy);
        return saved;
    }

    // ── Results ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ExamResultResponse> listResults(UUID examId, UUID actorId, Role actorRole) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        Map<UUID, StudentRow> roster = rosterById(exam.getPscId());
        return resultRepo.findAllByExamId(examId).stream()
                .map(r -> toResultResponse(exam, r, roster))
                .sorted(Comparator.comparing(
                        ExamResultResponse::getStudentNumber,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();
    }

    @Transactional(readOnly = true)
    public ExamResultResponse getStudentResult(UUID examId, UUID studentId) {
        Exam exam = findExamOrThrow(examId);
        ExamResult result = resultRepo.findByExamIdAndStudentId(examId, studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No marks recorded for that student on this exam."));
        return toResultResponse(exam, result);
    }

    public void deleteResult(UUID examId, UUID studentId, UUID actorId, Role actorRole) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        requireNotLocked(exam);
        resultRepo.findByExamIdAndStudentId(examId, studentId).ifPresent(result -> {
            questionMarkRepo.deleteAllByResultId(result.getId());
            resultRepo.delete(result);
        });
    }

    @Transactional(readOnly = true)
    public List<RosterEntryResponse> listRoster(UUID examId, UUID actorId, Role actorRole) {
        Exam exam = findExamOrThrow(examId);
        requireOfferingAccess(exam.getPscId(), actorId, actorRole);

        Set<UUID> withResults = resultRepo.findAllByExamId(examId).stream()
                .map(ExamResult::getStudentId)
                .collect(Collectors.toSet());

        return dataRepo.findRoster(exam.getPscId()).stream()
                .map(s -> RosterEntryResponse.builder()
                        .studentId(s.studentId())
                        .name(s.name())
                        .studentNumber(s.studentNumber())
                        .hasResult(withResults.contains(s.studentId()))
                        .build())
                .toList();
    }

    /** The CLOs a question may be mapped to, for the exam setup form. */
    @Transactional(readOnly = true)
    public List<CloMappingResponse> listAvailableClos(UUID examId) {
        Exam exam = findExamOrThrow(examId);
        return dataRepo.findClosByPscId(exam.getPscId()).stream()
                .map(c -> CloMappingResponse.builder()
                        .cloId(c.id())
                        .cloCode(c.code())
                        .cloTitle(c.title())
                        .build())
                .toList();
    }

    // ── Guards ───────────────────────────────────────────────────────────────

    Exam findExamOrThrow(UUID examId) {
        return examRepo.findById(examId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Exam not found: " + examId));
    }

    /**
     * A teacher may only touch offerings they run. Marks are an academic record,
     * so this is checked per offering rather than left to the role alone —
     * {@code hasRole('TEACHER')} would otherwise let any teacher record marks on
     * any course.
     */
    void requireOfferingAccess(UUID pscId, UUID actorId, Role actorRole) {
        if (actorRole == Role.ADMIN) {
            return;
        }
        if (!dataRepo.canManageOffering(pscId, actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "You do not teach this course offering.");
        }
    }

    private void requireNotLocked(Exam exam) {
        if (Exam.STATUS_LOCKED.equals(exam.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This exam is locked; unlock it before making changes.");
        }
    }

    private void requireNoResults(Exam exam, String action) {
        if (resultRepo.countByExamId(exam.getId()) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Marks have already been recorded for this exam, so you cannot " + action
                    + ". Remove the recorded marks first.");
        }
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    ExamResponse toResponse(Exam exam) {
        List<ExamQuestion> questions = questionRepo.findAllByExamIdOrderByOrderIndexAsc(exam.getId());

        Map<UUID, CloRow> clos = dataRepo.findClosByPscId(exam.getPscId()).stream()
                .collect(Collectors.toMap(CloRow::id, Function.identity()));

        Map<UUID, List<ExamQuestionCloMapping>> mappingsByQuestion = questions.isEmpty()
                ? Map.of()
                : cloMappingRepo.findAllByQuestionIds(questions.stream().map(ExamQuestion::getId).toList())
                        .stream()
                        .collect(Collectors.groupingBy(m -> m.getId().getQuestionId()));

        List<ExamQuestionResponse> questionResponses = questions.stream()
                .map(q -> ExamQuestionResponse.builder()
                        .id(q.getId())
                        .questionNo(q.getQuestionNo())
                        .maxMarks(q.getMaxMarks())
                        .orderIndex(q.getOrderIndex())
                        .cloMappings(mappingsByQuestion.getOrDefault(q.getId(), List.of()).stream()
                                .map(m -> {
                                    CloRow clo = clos.get(m.getId().getCloId());
                                    return CloMappingResponse.builder()
                                            .cloId(m.getId().getCloId())
                                            .cloCode(clo != null ? clo.code() : null)
                                            .cloTitle(clo != null ? clo.title() : null)
                                            .weight(m.getWeight())
                                            .build();
                                })
                                .toList())
                        .build())
                .toList();

        OfferingRow offering = dataRepo.findOffering(exam.getPscId()).orElse(null);

        return ExamResponse.builder()
                .id(exam.getId())
                .pscId(exam.getPscId())
                .courseCode(offering != null ? offering.courseCode() : null)
                .courseName(offering != null ? offering.courseName() : null)
                .createdBy(exam.getCreatedBy())
                .title(exam.getTitle())
                .examType(exam.getExamType())
                .examDate(exam.getExamDate())
                .totalMarks(exam.getTotalMarks())
                .status(exam.getStatus())
                .questions(questionResponses)
                .questionMarksTotal(questions.stream()
                        .map(ExamQuestion::getMaxMarks)
                        .reduce(BigDecimal.ZERO, BigDecimal::add))
                .resultsRecorded(resultRepo.countByExamId(exam.getId()))
                .rosterSize(dataRepo.countRoster(exam.getPscId()))
                .createdAt(exam.getCreatedAt())
                .updatedAt(exam.getUpdatedAt())
                .build();
    }

    ExamResultResponse toResultResponse(Exam exam, ExamResult result) {
        return toResultResponse(exam, result, rosterById(exam.getPscId()));
    }

    private Map<UUID, StudentRow> rosterById(UUID pscId) {
        return dataRepo.findRoster(pscId).stream()
                .collect(Collectors.toMap(StudentRow::studentId, Function.identity()));
    }

    /**
     * The roster is passed in rather than looked up per result: rendering a
     * class list otherwise ran one roster query per student.
     */
    private ExamResultResponse toResultResponse(Exam exam, ExamResult result,
                                                Map<UUID, StudentRow> roster) {
        Map<UUID, ExamQuestion> questions = questionRepo
                .findAllByExamIdOrderByOrderIndexAsc(exam.getId()).stream()
                .collect(Collectors.toMap(ExamQuestion::getId, Function.identity()));

        List<ExamResultResponse.QuestionMarkResponse> marks =
                questionMarkRepo.findAllByResultId(result.getId()).stream()
                        .map(m -> {
                            ExamQuestion q = questions.get(m.getQuestionId());
                            return ExamResultResponse.QuestionMarkResponse.builder()
                                    .questionId(m.getQuestionId())
                                    .questionNo(q != null ? q.getQuestionNo() : null)
                                    .maxMarks(q != null ? q.getMaxMarks() : null)
                                    .marksObtained(m.getMarksObtained())
                                    .build();
                        })
                        .sorted(Comparator.comparingInt(m -> {
                            ExamQuestion q = questions.get(m.getQuestionId());
                            return q != null ? q.getOrderIndex() : Integer.MAX_VALUE;
                        }))
                        .toList();

        StudentRow student = roster.get(result.getStudentId());

        Double percentage = exam.getTotalMarks().compareTo(BigDecimal.ZERO) > 0
                ? result.getTotalObtained()
                        .divide(exam.getTotalMarks(), 4, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(2, RoundingMode.HALF_UP)
                        .doubleValue()
                : null;

        return ExamResultResponse.builder()
                .id(result.getId())
                .examId(result.getExamId())
                .studentId(result.getStudentId())
                .studentName(student != null ? student.name() : null)
                .studentNumber(student != null ? student.studentNumber() : null)
                .totalObtained(result.getTotalObtained())
                .totalMarks(exam.getTotalMarks())
                .percentage(percentage)
                .source(result.getSource())
                .scanId(result.getScanId())
                .recordedBy(result.getRecordedBy())
                .recordedAt(result.getRecordedAt())
                .remarks(result.getRemarks())
                .marks(marks)
                .build();
    }
}
