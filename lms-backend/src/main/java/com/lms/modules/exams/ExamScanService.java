package com.lms.modules.exams;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.infrastructure.storage.StorageService;
import com.lms.modules.exams.ExamDataRepository.OfferingRow;
import com.lms.modules.exams.ExamDataRepository.StudentRow;
import com.lms.modules.exams.MarksSheetExtractor.MarksExtractionException;
import com.lms.modules.exams.MarksSheetExtractor.QuestionExpectation;
import com.lms.modules.exams.dto.*;
import com.lms.modules.exams.dto.ExtractedMarksSheet.ExtractedQuestionMark;
import com.lms.shared.InstitutionalCodes;
import com.lms.shared.Role;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Reading a photographed exam copy and turning it into marks a teacher confirms.
 *
 * <p>The flow is deliberately two-step. {@link #createScan} reads the page and
 * produces a <em>proposal</em>; {@link #confirmScan} is what actually records
 * marks, and only a teacher can call it. Nothing the extractor returns reaches
 * a student's record without a person having looked at it, because a
 * probabilistic reader is not an acceptable sole author of an academic record.
 *
 * <p>Everything the reading disagrees with the system about is surfaced as a
 * warning rather than an error. A mismatch usually means the page is right and
 * the expectation is wrong (a student sat under the wrong roll number, the
 * date was mis-set when the exam was created), and the teacher is the one able
 * to tell — so the review screen shows the conflict instead of refusing the
 * scan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ExamScanService {

    private final ExamScanRepository       scanRepo;
    private final ExamRepository           examRepo;
    private final ExamQuestionRepository   questionRepo;
    private final ExamResultRepository     resultRepo;
    private final ExamDataRepository       dataRepo;
    private final ExamService              examService;
    private final StorageService           storageService;
    private final ObjectMapper             objectMapper;

    /**
     * Optional: absent when no API key is configured, in which case scanning is
     * refused with an explanation and manual entry still works.
     */
    private final ObjectProvider<MarksSheetExtractor> extractorProvider;

    // ── Capture and read ─────────────────────────────────────────────────────

    /**
     * Read one captured page and return the proposal for the teacher to review.
     *
     * <p>The scan row is written whatever happens — including on a failed read —
     * so the captured page is never lost and a teacher can see why a copy did
     * not go through.
     */
    public ScanReviewResponse createScan(UUID actorId, Role actorRole, CreateScanRequest req) {
        MarksSheetExtractor extractor = extractorProvider.getIfAvailable();
        if (extractor == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Marks scanning is not configured on this server. Enter the marks manually.");
        }

        ExamScan scan = new ExamScan();
        scan.setUploadedBy(actorId);
        scan.setImageKey(req.getImageKey());
        scan.setImageName(req.getImageName());
        scan.setImageSize(req.getImageSize());
        scan.setStatus(ExamScan.STATUS_PENDING);
        scan.setExtractor(extractor.identifier());

        // When the exam is known up front, authorise against it now — before
        // spending an extraction call on a copy the caller may not touch.
        Exam exam = null;
        if (req.getExamId() != null) {
            exam = examService.findExamOrThrow(req.getExamId());
            examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);
            scan.setExamId(exam.getId());
        }

        List<QuestionExpectation> expectations = exam == null ? List.of()
                : questionRepo.findAllByExamIdOrderByOrderIndexAsc(exam.getId()).stream()
                        .map(q -> new QuestionExpectation(
                                q.getQuestionNo(), q.getMaxMarks().stripTrailingZeros().toPlainString()))
                        .toList();

        ExtractedMarksSheet sheet;
        try {
            byte[] image = readImage(req.getImageKey());
            sheet = extractor.extract(image, contentTypeFor(req.getImageName()), expectations);
        } catch (MarksExtractionException e) {
            scan.setStatus(ExamScan.STATUS_FAILED);
            scan.setErrorMessage(e.getMessage());
            ExamScan failed = scanRepo.save(scan);
            log.warn("Exam scan {} failed to read: {}", failed.getId(), e.getMessage());
            return toReview(failed, exam, List.of());
        }

        return buildProposal(scan, exam, sheet, actorId, actorRole);
    }

    /** Assemble the reviewable proposal from a successful reading. */
    private ScanReviewResponse buildProposal(ExamScan scan, Exam exam, ExtractedMarksSheet sheet,
                                             UUID actorId, Role actorRole) {
        List<String> warnings = new ArrayList<>();

        scan.setReadRollNumber(trimToNull(sheet.rollNumber()));
        scan.setReadStudentName(trimToNull(sheet.studentName()));
        scan.setReadCourseCode(InstitutionalCodes.normalise(trimToNull(sheet.courseCode())));
        scan.setReadExamDate(parseDate(sheet.examDate(), warnings));
        scan.setExtraction(objectMapper.convertValue(sheet, Map.class));

        if ("LOW".equalsIgnoreCase(sheet.confidence())) {
            warnings.add("The reader was not confident about this page — check every value.");
        }
        if (sheet.notes() != null) {
            sheet.notes().stream().filter(n -> n != null && !n.isBlank()).forEach(warnings::add);
        }

        // Resolve the exam if the scan did not arrive with one.
        if (exam == null) {
            exam = resolveExam(scan, actorId, warnings);
            if (exam != null) {
                examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);
                scan.setExamId(exam.getId());
            }
        }

        if (exam == null) {
            scan.setStatus(ExamScan.STATUS_EXTRACTED);
            scan.setWarnings(warnings);
            return toReview(scanRepo.save(scan), null, List.of());
        }

        crossCheckAgainstExam(scan, exam, sheet, warnings);
        resolveStudent(scan, exam, warnings);
        List<ScanReviewResponse.ProposedMark> proposed = matchMarks(exam, sheet, warnings);

        scan.setStatus(ExamScan.STATUS_EXTRACTED);
        scan.setWarnings(warnings);
        ExamScan saved = scanRepo.save(scan);
        log.info("Exam scan {} read: exam={} roll={} warnings={}",
                saved.getId(), exam.getId(), saved.getReadRollNumber(), warnings.size());
        return toReview(saved, exam, proposed);
    }

    /**
     * Find the exam a standalone scan belongs to, from the course code and date
     * on the page.
     *
     * <p>Restricted to offerings the caller teaches, so a page can never resolve
     * onto another teacher's course. Ambiguity is reported rather than guessed
     * at — filing marks against the wrong exam is worse than asking.
     */
    private Exam resolveExam(ExamScan scan, UUID actorId, List<String> warnings) {
        if (scan.getReadCourseCode() == null || scan.getReadExamDate() == null) {
            warnings.add("Could not tell which exam this page belongs to — "
                         + "the course code or exam date was unreadable. Choose the exam yourself.");
            return null;
        }

        List<UUID> pscIds = dataRepo.findOfferingsForTeacher(actorId).stream()
                .filter(o -> scan.getReadCourseCode().equalsIgnoreCase(
                        InstitutionalCodes.normalise(o.courseCode())))
                .map(OfferingRow::pscId)
                .toList();

        if (pscIds.isEmpty()) {
            warnings.add("The page reads course " + scan.getReadCourseCode()
                         + ", which is not one of your offerings. Choose the exam yourself.");
            return null;
        }

        List<Exam> candidates = examRepo.findAllByPscIdInAndExamDate(pscIds, scan.getReadExamDate());
        if (candidates.isEmpty()) {
            warnings.add("No exam for " + scan.getReadCourseCode() + " on " + scan.getReadExamDate()
                         + ". Choose the exam yourself.");
            return null;
        }
        if (candidates.size() > 1) {
            warnings.add("More than one exam matches " + scan.getReadCourseCode() + " on "
                         + scan.getReadExamDate() + ". Choose which one this copy belongs to.");
            return null;
        }
        return candidates.get(0);
    }

    /** Check the page's own identifying details against the exam it is filed under. */
    private void crossCheckAgainstExam(ExamScan scan, Exam exam, ExtractedMarksSheet sheet,
                                       List<String> warnings) {
        OfferingRow offering = dataRepo.findOffering(exam.getPscId()).orElse(null);

        if (offering != null && scan.getReadCourseCode() != null
                && !scan.getReadCourseCode().equalsIgnoreCase(
                        InstitutionalCodes.normalise(offering.courseCode()))) {
            warnings.add("The page reads course " + scan.getReadCourseCode()
                         + " but this exam is for " + offering.courseCode()
                         + ". Check you have the right copy.");
        }

        if (scan.getReadExamDate() != null && !scan.getReadExamDate().equals(exam.getExamDate())) {
            warnings.add("The page is dated " + scan.getReadExamDate()
                         + " but this exam is recorded as " + exam.getExamDate() + ".");
        }

        // The marker's own total is an independent check on the individual
        // marks: if the two disagree, one of them was misread.
        if (sheet.writtenTotal() != null) {
            BigDecimal readSum = sheet.questionMarks() == null ? BigDecimal.ZERO
                    : sheet.questionMarks().stream()
                            .map(ExtractedQuestionMark::marksObtained)
                            .filter(Objects::nonNull)
                            .map(BigDecimal::valueOf)
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal written = BigDecimal.valueOf(sheet.writtenTotal());
            if (written.compareTo(readSum) != 0) {
                warnings.add("The total written on the page (" + written.stripTrailingZeros().toPlainString()
                             + ") does not match the marks read from the table ("
                             + readSum.stripTrailingZeros().toPlainString() + ").");
            }
            if (written.compareTo(exam.getTotalMarks()) > 0) {
                warnings.add("The total written on the page (" + written.stripTrailingZeros().toPlainString()
                             + ") is above this exam's maximum of " + exam.getTotalMarks() + ".");
            }
        }
    }

    /** Match the roll number to a student on the exam's roster. */
    private void resolveStudent(ExamScan scan, Exam exam, List<String> warnings) {
        if (scan.getReadRollNumber() == null) {
            warnings.add("No roll number could be read — pick the student yourself.");
            return;
        }

        Optional<StudentRow> match =
                dataRepo.findEnrolledStudentByRollNumber(exam.getPscId(), scan.getReadRollNumber());
        if (match.isEmpty()) {
            warnings.add("Roll number " + scan.getReadRollNumber()
                         + " is not on this course's roster — pick the student yourself.");
            return;
        }

        StudentRow student = match.get();
        scan.setMatchedStudentId(student.studentId());

        // The name is corroboration, not identification: the roll number is the
        // key, so a name that does not look like the matched student's is worth
        // flagging but never overrides the match.
        if (scan.getReadStudentName() != null && !namesLookAlike(scan.getReadStudentName(), student.name())) {
            warnings.add("The page reads the name '" + scan.getReadStudentName()
                         + "' but roll number " + student.studentNumber()
                         + " belongs to " + student.name() + ".");
        }

        if (resultRepo.findByExamIdAndStudentId(exam.getId(), student.studentId()).isPresent()) {
            warnings.add("Marks are already recorded for " + student.name()
                         + " on this exam. Confirming will replace them.");
        }
    }

    /**
     * Line the rows read off the page up against the exam's question list.
     *
     * <p>Matching is by label, case- and punctuation-insensitive, because a
     * marker writes "Q3" where the exam says "3". A question with no matching
     * row comes back as unread rather than zero — the teacher fills it in.
     */
    private List<ScanReviewResponse.ProposedMark> matchMarks(Exam exam, ExtractedMarksSheet sheet,
                                                            List<String> warnings) {
        List<ExamQuestion> questions = questionRepo.findAllByExamIdOrderByOrderIndexAsc(exam.getId());

        Map<String, ExtractedQuestionMark> readByLabel = new LinkedHashMap<>();
        if (sheet.questionMarks() != null) {
            for (ExtractedQuestionMark row : sheet.questionMarks()) {
                if (row.questionNo() != null) {
                    readByLabel.putIfAbsent(normaliseLabel(row.questionNo()), row);
                }
            }
        }

        List<ScanReviewResponse.ProposedMark> proposed = new ArrayList<>();
        Set<String> consumed = new HashSet<>();

        for (ExamQuestion question : questions) {
            String key = normaliseLabel(question.getQuestionNo());
            ExtractedQuestionMark row = readByLabel.get(key);
            if (row != null) {
                consumed.add(key);
            }

            BigDecimal mark = row == null || row.marksObtained() == null
                    ? null
                    : BigDecimal.valueOf(row.marksObtained());

            if (mark != null && mark.compareTo(question.getMaxMarks()) > 0) {
                warnings.add("Question " + question.getQuestionNo() + " reads "
                             + mark.stripTrailingZeros().toPlainString() + " but is out of "
                             + question.getMaxMarks().stripTrailingZeros().toPlainString() + ".");
            }
            if (row == null) {
                warnings.add("No mark was found for question " + question.getQuestionNo() + ".");
            } else if (row.marksObtained() == null) {
                warnings.add("Question " + question.getQuestionNo()
                             + " was left blank or could not be read.");
            }

            proposed.add(ScanReviewResponse.ProposedMark.builder()
                    .questionId(question.getId())
                    .questionNo(question.getQuestionNo())
                    .maxMarks(question.getMaxMarks())
                    .marksObtained(mark)
                    .unread(row == null || row.marksObtained() == null)
                    .build());
        }

        readByLabel.keySet().stream()
                .filter(label -> !consumed.contains(label))
                .forEach(label -> warnings.add(
                        "The page has a row for question '" + label
                        + "', which is not part of this exam. It was ignored."));

        return proposed;
    }

    // ── Confirm / discard ────────────────────────────────────────────────────

    /**
     * Record the marks a teacher confirmed for a scan.
     *
     * <p>This is the only path from a scan to a student's record. The marks
     * written are the ones in the request — the teacher's — not the ones the
     * extractor read.
     */
    public ExamResultResponse confirmScan(UUID scanId, UUID actorId, Role actorRole,
                                          ConfirmScanRequest req) {
        ExamScan scan = findScanOrThrow(scanId);
        if (scan.getExamId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "This scan is not attached to an exam yet. Choose the exam first.");
        }
        if (ExamScan.STATUS_DISCARDED.equals(scan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This scan was discarded. Capture the page again.");
        }

        Exam exam = examService.findExamOrThrow(scan.getExamId());
        examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);

        ExamResult result = examService.writeResult(exam, req.getStudentId(), req.getMarks(),
                actorId, ExamResult.SOURCE_SCAN, scan.getId(), req.getRemarks());

        scan.setStatus(ExamScan.STATUS_CONFIRMED);
        scan.setConfirmedAt(LocalDateTime.now());
        scan.setMatchedStudentId(req.getStudentId());
        scanRepo.save(scan);

        return examService.toResultResponse(exam, result);
    }

    /** Attach an unresolved scan to an exam the teacher picks, then re-review it. */
    public ScanReviewResponse assignExam(UUID scanId, UUID examId, UUID actorId, Role actorRole) {
        ExamScan scan = findScanOrThrow(scanId);
        Exam exam = examService.findExamOrThrow(examId);
        examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);

        if (ExamScan.STATUS_CONFIRMED.equals(scan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This scan is already confirmed; its marks are recorded against an exam.");
        }

        scan.setExamId(exam.getId());
        List<String> warnings = new ArrayList<>();
        ExtractedMarksSheet sheet = readBackExtraction(scan);
        if (sheet == null) {
            scan.setWarnings(List.of("The original reading is no longer available. Enter the marks by hand."));
            return toReview(scanRepo.save(scan), exam, List.of());
        }

        crossCheckAgainstExam(scan, exam, sheet, warnings);
        resolveStudent(scan, exam, warnings);
        List<ScanReviewResponse.ProposedMark> proposed = matchMarks(exam, sheet, warnings);
        scan.setWarnings(warnings);
        return toReview(scanRepo.save(scan), exam, proposed);
    }

    public void discardScan(UUID scanId, UUID actorId, Role actorRole) {
        ExamScan scan = findScanOrThrow(scanId);
        if (scan.getExamId() != null) {
            Exam exam = examService.findExamOrThrow(scan.getExamId());
            examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        } else if (actorRole != Role.ADMIN && !scan.getUploadedBy().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That is not your scan.");
        }
        if (ExamScan.STATUS_CONFIRMED.equals(scan.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This scan is confirmed. Delete the recorded marks instead.");
        }
        scan.setStatus(ExamScan.STATUS_DISCARDED);
        scanRepo.save(scan);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ScanSummaryResponse> listScans(UUID examId, UUID actorId, Role actorRole) {
        Exam exam = examService.findExamOrThrow(examId);
        examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);

        Map<UUID, String> names = dataRepo.findRoster(exam.getPscId()).stream()
                .collect(Collectors.toMap(StudentRow::studentId, StudentRow::name));

        return scanRepo.findAllByExamIdOrderByCreatedAtDesc(examId).stream()
                .map(s -> toSummary(s, names))
                .toList();
    }

    @Transactional(readOnly = true)
    public ScanReviewResponse getScan(UUID scanId, UUID actorId, Role actorRole) {
        ExamScan scan = findScanOrThrow(scanId);
        Exam exam = null;
        if (scan.getExamId() != null) {
            exam = examService.findExamOrThrow(scan.getExamId());
            examService.requireOfferingAccess(exam.getPscId(), actorId, actorRole);
        } else if (actorRole != Role.ADMIN && !scan.getUploadedBy().equals(actorId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "That is not your scan.");
        }

        ExtractedMarksSheet sheet = readBackExtraction(scan);
        List<ScanReviewResponse.ProposedMark> proposed = (exam == null || sheet == null)
                ? List.of()
                : matchMarks(exam, sheet, new ArrayList<>());
        return toReview(scan, exam, proposed);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private ExamScan findScanOrThrow(UUID scanId) {
        return scanRepo.findById(scanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Scan not found: " + scanId));
    }

    private byte[] readImage(String objectKey) {
        try (InputStream in = storageService.download(objectKey)) {
            return in.readAllBytes();
        } catch (IOException | RuntimeException e) {
            throw new MarksExtractionException(
                    "Could not read the uploaded page back from storage: " + e.getMessage(), e);
        }
    }

    /** Re-hydrate the stored reading so a scan can be re-matched without a second API call. */
    private ExtractedMarksSheet readBackExtraction(ExamScan scan) {
        if (scan.getExtraction() == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(scan.getExtraction(), ExtractedMarksSheet.class);
        } catch (IllegalArgumentException e) {
            log.warn("Stored extraction for scan {} is unreadable: {}", scan.getId(), e.getMessage());
            return null;
        }
    }

    /**
     * Question labels differ cosmetically between paper and system: a marker
     * writes "Q3." or "Q 3" where the exam is set up with "3". Punctuation and
     * spacing are dropped and a leading "Q" is stripped, so those all meet at
     * "3" — otherwise a correctly read mark would be reported as missing purely
     * because of how the marker labelled the column.
     */
    private static String normaliseLabel(String label) {
        if (label == null) {
            return "";
        }
        String stripped = label.replaceAll("[^A-Za-z0-9]", "").toUpperCase();
        // Only when something follows it, so a column headed just "Q" is left alone.
        if (stripped.length() > 1 && stripped.charAt(0) == 'Q'
                && Character.isDigit(stripped.charAt(1))) {
            stripped = stripped.substring(1);
        }
        return stripped;
    }

    /**
     * A deliberately loose comparison. The point is to catch a copy filed under
     * the wrong student, not to police spelling — handwritten names are read
     * imperfectly and middle names come and go, so a shared surname or first
     * name is treated as corroboration.
     */
    private static boolean namesLookAlike(String read, String actual) {
        Set<String> readParts = tokenise(read);
        Set<String> actualParts = tokenise(actual);
        return readParts.stream().anyMatch(actualParts::contains);
    }

    private static Set<String> tokenise(String name) {
        if (name == null) return Set.of();
        return Arrays.stream(name.toUpperCase().split("[^A-Z]+"))
                .filter(part -> part.length() > 2)
                .collect(Collectors.toSet());
    }

    private LocalDate parseDate(String raw, List<String> warnings) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw.trim());
        } catch (DateTimeParseException e) {
            warnings.add("Could not make sense of the date '" + raw + "' on the page.");
            return null;
        }
    }

    private static String trimToNull(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /** The stored name is the only type hint available; default to JPEG. */
    private static String contentTypeFor(String fileName) {
        if (fileName == null) return "image/jpeg";
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".gif"))  return "image/gif";
        return "image/jpeg";
    }

    // ── Mapping ──────────────────────────────────────────────────────────────

    private ScanReviewResponse toReview(ExamScan scan, Exam exam,
                                        List<ScanReviewResponse.ProposedMark> proposed) {
        StudentRow matched = null;
        if (scan.getMatchedStudentId() != null && exam != null) {
            matched = dataRepo.findRoster(exam.getPscId()).stream()
                    .filter(s -> s.studentId().equals(scan.getMatchedStudentId()))
                    .findFirst().orElse(null);
        }

        ExtractedMarksSheet sheet = readBackExtraction(scan);
        BigDecimal proposedTotal = proposed.stream()
                .map(ScanReviewResponse.ProposedMark::getMarksObtained)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return ScanReviewResponse.builder()
                .id(scan.getId())
                .examId(scan.getExamId())
                .examTitle(exam != null ? exam.getTitle() : null)
                .status(scan.getStatus())
                .imageKey(scan.getImageKey())
                .readRollNumber(scan.getReadRollNumber())
                .readStudentName(scan.getReadStudentName())
                .readCourseCode(scan.getReadCourseCode())
                .readExamDate(scan.getReadExamDate())
                .readWrittenTotal(sheet != null && sheet.writtenTotal() != null
                        ? BigDecimal.valueOf(sheet.writtenTotal()) : null)
                .confidence(sheet != null ? sheet.confidence() : null)
                .extractorNotes(sheet != null && sheet.notes() != null ? sheet.notes() : List.of())
                .matchedStudentId(scan.getMatchedStudentId())
                .matchedStudentName(matched != null ? matched.name() : null)
                .matchedStudentNumber(matched != null ? matched.studentNumber() : null)
                .proposedMarks(proposed)
                .proposedTotal(proposedTotal)
                .warnings(scan.getWarnings() == null ? List.of() : scan.getWarnings())
                .errorMessage(scan.getErrorMessage())
                .createdAt(scan.getCreatedAt())
                .build();
    }

    private ScanSummaryResponse toSummary(ExamScan scan, Map<UUID, String> studentNames) {
        return ScanSummaryResponse.builder()
                .id(scan.getId())
                .examId(scan.getExamId())
                .status(scan.getStatus())
                .imageKey(scan.getImageKey())
                .readRollNumber(scan.getReadRollNumber())
                .readStudentName(scan.getReadStudentName())
                .matchedStudentId(scan.getMatchedStudentId())
                .matchedStudentName(scan.getMatchedStudentId() == null ? null
                        : studentNames.get(scan.getMatchedStudentId()))
                .warningCount(scan.getWarnings() == null ? 0 : scan.getWarnings().size())
                .errorMessage(scan.getErrorMessage())
                .createdAt(scan.getCreatedAt())
                .confirmedAt(scan.getConfirmedAt())
                .build();
    }
}
