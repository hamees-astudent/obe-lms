package com.lms.modules.transcript;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lms.modules.transcript.TranscriptDataRepository.*;
import com.lms.modules.transcript.dto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptService {

    private final TranscriptRepository       transcriptRepo;
    private final TranscriptDataRepository   dataRepo;
    private final GradingScaleRepository     scaleRepo;
    private final GradingScaleEntryRepository entryRepo;
    private final TranscriptPdfGenerator     pdfGenerator;
    private final ObjectMapper               objectMapper;

    // ── Kafka-triggered bulk generation ─────────────────────────────────────

    /**
     * Called asynchronously on semester CLOSED event.
     * Generates (or re-generates) a transcript for every enrolled student.
     */
    @Async
    @Transactional
    public void generateTranscriptsForSemester(UUID semesterId, UUID programId, String triggeredByEmail) {
        log.info("Generating transcripts: semesterId={} programId={}", semesterId, programId);

        UUID generatedBy = dataRepo.findUserIdByEmail(triggeredByEmail)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot resolve generatedBy — user not found for email: " + triggeredByEmail));

        SemesterRow semesterInfo = dataRepo.findSemesterInfo(semesterId)
                .orElseThrow(() -> new IllegalStateException("Semester not found: " + semesterId));

        GradingScale scale = scaleRepo.findActiveScaleForProgram(programId)
                .orElseThrow(() -> new IllegalStateException(
                        "No default grading scale found for programId=" + programId));

        List<GradingScaleEntry> entries = entryRepo.findByScaleIdOrderByOrderIndexAsc(scale.getId());
        if (entries.isEmpty()) {
            throw new IllegalStateException("Grading scale has no entries: scaleId=" + scale.getId());
        }

        List<UUID> studentIds = dataRepo.findEnrolledStudentIds(semesterId);
        log.info("Generating transcripts for {} students in semester {}", studentIds.size(), semesterId);

        for (UUID studentId : studentIds) {
            try {
                generateStudentTranscript(studentId, semesterId, programId,
                        generatedBy, scale, entries, semesterInfo);
            } catch (Exception e) {
                log.error("Failed to generate transcript for student={} semester={}: {}",
                        studentId, semesterId, e.getMessage(), e);
            }
        }
    }

    /** Admin-triggered manual re-generation for a whole semester. */
    @Async
    @Transactional
    public void regenerateTranscriptsForSemester(UUID semesterId, UUID programId, String adminEmail) {
        generateTranscriptsForSemester(semesterId, programId, adminEmail);
    }

    // ── Single-student generation ────────────────────────────────────────────

    @Transactional
    void generateStudentTranscript(UUID studentId, UUID semesterId, UUID programId,
                                   UUID generatedBy, GradingScale scale,
                                   List<GradingScaleEntry> entries, SemesterRow semesterInfo) {
        // Delete existing row (upsert via delete + insert)
        transcriptRepo.deleteByStudentIdAndSemesterId(studentId, semesterId);
        transcriptRepo.flush();

        StudentRow studentInfo = dataRepo.findStudentInfo(studentId).orElseGet(
                () -> new StudentRow("Unknown", "N/A"));

        List<CourseRow> courses = dataRepo.findStudentCoursesForSemester(semesterId, studentId);
        if (courses.isEmpty()) {
            log.debug("No courses for student={} in semester={}, skipping", studentId, semesterId);
            return;
        }

        List<CourseTranscriptDetail> courseDetails = new ArrayList<>();
        for (CourseRow course : courses) {
            courseDetails.add(buildCourseDetail(course, studentId, entries));
        }

        // Semester GPA
        double semGpaVal  = computeSemesterGpa(courseDetails);
        int    totalCH    = courseDetails.stream().mapToInt(CourseTranscriptDetail::getCreditHours).sum();
        int    earnedCH   = courseDetails.stream()
                .filter(c -> c.getGradePoints() != null && c.getGradePoints() > 0)
                .mapToInt(CourseTranscriptDetail::getCreditHours)
                .sum();

        // Cumulative GPA
        List<GpaRow> prevGpaRows = dataRepo.findPreviousGpaRows(studentId, programId, semesterId);
        double cumGpaVal = computeCumulativeGpa(prevGpaRows, semGpaVal, totalCH);

        // PLO attainment
        List<CloAttainmentWithId> allCloAttainments = courseDetails.stream()
                .flatMap(c -> {
                    // Re-fetch CLO IDs from dataRepo for this PSC
                    List<CloRow> cloRows = dataRepo.findClosByPscId(c.getPscId());
                    List<CloAttainmentDetail> details = c.getCloAttainment();
                    List<CloAttainmentWithId> result = new ArrayList<>();
                    for (int i = 0; i < cloRows.size() && i < details.size(); i++) {
                        result.add(new CloAttainmentWithId(
                                cloRows.get(i).id(),
                                details.get(i).getAttainmentPercentage()));
                    }
                    return result.stream();
                })
                .toList();

        List<PloAttainmentDetail> ploAttainment = computePloAttainment(allCloAttainments);

        TranscriptSnapshotData snapshotData = TranscriptSnapshotData.builder()
                .studentName(studentInfo.name())
                .studentNumber(studentInfo.studentNumber())
                .programName(semesterInfo.programName())
                .semesterName(semesterInfo.semesterName())
                .gradingScaleName(scale.getName())
                .semesterGpa(semGpaVal)
                .cumulativeGpa(cumGpaVal)
                .totalCreditHours(totalCH)
                .earnedCreditHours(earnedCH)
                .courses(courseDetails)
                .ploAttainment(ploAttainment)
                .build();

        Map<String, Object> snapshot = objectMapper.convertValue(snapshotData,
                new TypeReference<Map<String, Object>>() {});

        Transcript transcript = new Transcript();
        transcript.setStudentId(studentId);
        transcript.setSemesterId(semesterId);
        transcript.setProgramId(programId);
        transcript.setGradingScaleId(scale.getId());
        transcript.setSnapshot(snapshot);
        transcript.setSemesterGpa(BigDecimal.valueOf(semGpaVal).setScale(2, RoundingMode.HALF_UP));
        transcript.setCumulativeGpa(BigDecimal.valueOf(cumGpaVal).setScale(2, RoundingMode.HALF_UP));
        transcript.setTotalCreditHours(totalCH);
        transcript.setEarnedCreditHours(earnedCH);
        transcript.setGeneratedAt(LocalDateTime.now());
        transcript.setGeneratedBy(generatedBy);

        transcriptRepo.save(transcript);
    }

    // ── Course detail builder ────────────────────────────────────────────────

    private CourseTranscriptDetail buildCourseDetail(CourseRow course,
                                                     UUID studentId,
                                                     List<GradingScaleEntry> entries) {
        // Attendance
        AttendanceRow att = dataRepo.findAttendanceSummary(course.pscId(), studentId);
        double attendancePct = att.total() > 0
                ? att.attended() * 100.0 / att.total()
                : 100.0;

        // Marks
        double totalMarks    = dataRepo.findAssignmentsTotalMarks(course.pscId())
                             + dataRepo.findQuizzesTotalMarks(course.pscId());
        double obtainedMarks = dataRepo.findStudentAssignmentMarks(course.pscId(), studentId)
                             + dataRepo.findStudentQuizMarks(course.pscId(), studentId);
        double percentage    = totalMarks > 0 ? obtainedMarks / totalMarks * 100.0 : 0.0;

        // Grade
        GradingScaleEntry matchedEntry = findGradeEntry(percentage, entries);
        String  gradeLetter = matchedEntry != null ? matchedEntry.getGradeLetter() : "—";
        double  gradePoints = matchedEntry != null ? matchedEntry.getGradePoints().doubleValue() : 0.0;

        // CLO attainment
        List<CloAttainmentDetail> cloDetails = computeCloDetails(course.pscId(), studentId, entries);

        return CourseTranscriptDetail.builder()
                .pscId(course.pscId())
                .courseCode(course.courseCode())
                .courseName(course.courseName())
                .creditHours(course.creditHours())
                .attendancePercentage(round2(attendancePct))
                .totalMarks(round2(totalMarks))
                .marksObtained(round2(obtainedMarks))
                .percentage(round2(percentage))
                .gradeLetter(gradeLetter)
                .gradePoints(gradePoints)
                .cloAttainment(cloDetails)
                .build();
    }

    // ── CLO attainment ───────────────────────────────────────────────────────

    private record CloAttainmentWithId(UUID cloId, Double attainmentPct) {}

    private List<CloAttainmentDetail> computeCloDetails(UUID pscId, UUID studentId,
                                                        List<GradingScaleEntry> entries) {
        List<CloRow> cloRows = dataRepo.findClosByPscId(pscId);
        List<CloAttainmentDetail> result = new ArrayList<>();

        for (CloRow clo : cloRows) {
            List<AssessmentContrib> aContribs = dataRepo.findAssignmentCloContributions(pscId, studentId, clo.id());
            List<AssessmentContrib> qContribs = dataRepo.findQuizCloContributions(pscId, studentId, clo.id());

            List<AssessmentContrib> all = new ArrayList<>();
            all.addAll(aContribs);
            all.addAll(qContribs);

            if (all.isEmpty()) continue;

            double weightedSum = 0.0;
            double weightTotal = 0.0;
            for (AssessmentContrib c : all) {
                double w    = c.weight() != null ? c.weight() : 1.0;
                double pct  = c.totalMarks() > 0 ? c.marksObtained() / c.totalMarks() * 100.0 : 0.0;
                weightedSum += pct * w;
                weightTotal += w;
            }
            double attainment = weightTotal > 0 ? weightedSum / weightTotal : 0.0;

            result.add(CloAttainmentDetail.builder()
                    .cloCode(clo.code())
                    .cloTitle(clo.title())
                    .attainmentPercentage(round2(attainment))
                    .build());
        }
        return result;
    }

    // ── PLO attainment ───────────────────────────────────────────────────────

    private List<PloAttainmentDetail> computePloAttainment(List<CloAttainmentWithId> cloList) {
        if (cloList.isEmpty()) return Collections.emptyList();

        List<UUID> cloIds = cloList.stream().map(CloAttainmentWithId::cloId).distinct().toList();
        List<PloRow> ploRows = dataRepo.findPloMappingsByCloIds(cloIds);

        // Build CLO-id → attainment lookup
        Map<UUID, Double> cloAttainmentMap = cloList.stream()
                .collect(Collectors.toMap(CloAttainmentWithId::cloId,
                        CloAttainmentWithId::attainmentPct,
                        (a, b) -> (a + b) / 2));

        // Group by PLO
        Map<String, List<Double>> ploAttainments = new LinkedHashMap<>();
        Map<String, String>       ploTitles      = new LinkedHashMap<>();

        for (PloRow row : ploRows) {
            Double cloAtt = cloAttainmentMap.get(row.cloId());
            if (cloAtt == null) continue;
            ploAttainments.computeIfAbsent(row.ploCode(), k -> new ArrayList<>()).add(cloAtt);
            ploTitles.putIfAbsent(row.ploCode(), row.ploTitle());
        }

        return ploAttainments.entrySet().stream()
                .map(e -> {
                    double avg = e.getValue().stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                    return PloAttainmentDetail.builder()
                            .ploCode(e.getKey())
                            .ploTitle(ploTitles.getOrDefault(e.getKey(), ""))
                            .attainmentPercentage(round2(avg))
                            .build();
                })
                .toList();
    }

    // ── GPA helpers ──────────────────────────────────────────────────────────

    private double computeSemesterGpa(List<CourseTranscriptDetail> courses) {
        double weightedSum = 0.0;
        int    totalCH     = 0;
        for (CourseTranscriptDetail c : courses) {
            weightedSum += (c.getGradePoints() != null ? c.getGradePoints() : 0.0) * c.getCreditHours();
            totalCH     += c.getCreditHours();
        }
        return totalCH > 0 ? round2(weightedSum / totalCH) : 0.0;
    }

    private double computeCumulativeGpa(List<GpaRow> prevRows, double currSemGpa, int currCH) {
        double weightedSum = currSemGpa * currCH;
        int    totalCH     = currCH;
        for (GpaRow row : prevRows) {
            weightedSum += row.semesterGpa() * row.creditHours();
            totalCH     += row.creditHours();
        }
        return totalCH > 0 ? round2(weightedSum / totalCH) : 0.0;
    }

    // ── Grade look-up ────────────────────────────────────────────────────────

    private GradingScaleEntry findGradeEntry(double percentage, List<GradingScaleEntry> entries) {
        GradingScaleEntry last = null;
        for (GradingScaleEntry e : entries) {
            double min = e.getMinPercentage().doubleValue();
            double max = e.getMaxPercentage().doubleValue();
            // Top entry (max == 100) is inclusive
            if (percentage >= min && (percentage < max || (max == 100.0 && percentage <= 100.0))) {
                return e;
            }
            last = e;
        }
        // Fallback: lowest-ordered entry (F or equivalent)
        return last;
    }

    // ── Transcript queries (REST) ────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<TranscriptSummaryResponse> getStudentTranscripts(UUID studentId) {
        return transcriptRepo.findAllByStudentIdOrderByCreatedAtDesc(studentId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TranscriptSummaryResponse> getSemesterTranscripts(UUID semesterId) {
        return transcriptRepo.findAllBySemesterId(semesterId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public TranscriptResponse getTranscript(UUID id, Authentication auth) {
        Transcript t = findOrThrow(id);
        assertAccess(t, auth);
        return toResponse(t);
    }

    @Transactional(readOnly = true)
    public byte[] getTranscriptPdf(UUID id, Authentication auth) {
        Transcript t = findOrThrow(id);
        assertAccess(t, auth);
        TranscriptSnapshotData snapshot = objectMapper.convertValue(t.getSnapshot(), TranscriptSnapshotData.class);
        return pdfGenerator.generatePdf(snapshot);
    }

    // ── Mapping helpers ──────────────────────────────────────────────────────

    private TranscriptResponse toResponse(Transcript t) {
        TranscriptSnapshotData snapshot = objectMapper.convertValue(t.getSnapshot(), TranscriptSnapshotData.class);
        return TranscriptResponse.builder()
                .id(t.getId())
                .studentId(t.getStudentId())
                .semesterId(t.getSemesterId())
                .programId(t.getProgramId())
                .gradingScaleId(t.getGradingScaleId())
                .semesterGpa(t.getSemesterGpa())
                .cumulativeGpa(t.getCumulativeGpa())
                .totalCreditHours(t.getTotalCreditHours())
                .earnedCreditHours(t.getEarnedCreditHours())
                .generatedAt(t.getGeneratedAt())
                .generatedBy(t.getGeneratedBy())
                .createdAt(t.getCreatedAt())
                .snapshot(snapshot)
                .build();
    }

    private TranscriptSummaryResponse toSummary(Transcript t) {
        return TranscriptSummaryResponse.builder()
                .id(t.getId())
                .studentId(t.getStudentId())
                .semesterId(t.getSemesterId())
                .programId(t.getProgramId())
                .semesterGpa(t.getSemesterGpa())
                .cumulativeGpa(t.getCumulativeGpa())
                .totalCreditHours(t.getTotalCreditHours())
                .earnedCreditHours(t.getEarnedCreditHours())
                .generatedAt(t.getGeneratedAt())
                .createdAt(t.getCreatedAt())
                .build();
    }

    // ── Security helpers ─────────────────────────────────────────────────────

    public UUID resolveStudentId(String email) {
        return dataRepo.findUserIdByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "User not found: " + email));
    }

    private void assertAccess(Transcript t, Authentication auth) {
        boolean isAdmin = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(a -> a.equals("ROLE_ADMIN") || a.equals("ROLE_TEACHER"));
        if (isAdmin) return;

        // Students can only see their own transcript
        String email = auth.getName();
        dataRepo.findUserIdByEmail(email).ifPresent(userId -> {
            if (!userId.equals(t.getStudentId())) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Access denied");
            }
        });
    }

    private Transcript findOrThrow(UUID id) {
        return transcriptRepo.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transcript not found"));
    }

    // ── Utility ──────────────────────────────────────────────────────────────

    private double round2(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }
}
