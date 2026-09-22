package com.lms.modules.exams;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * One photographed exam copy and what was read from it.
 *
 * <p>A scan never writes marks. It records what the extractor read; a teacher
 * reviews that, corrects it if needed, and confirms — only then does an
 * {@link ExamResult} exist. The row is kept after confirmation as the evidence
 * behind those marks: the stored image plus the raw extraction can be
 * re-examined if a student disputes a mark.
 *
 * <p>The {@code read*} fields hold values exactly as the extractor saw them —
 * unnormalised and unresolved — so a dispute can be traced to what was actually
 * on the page rather than to what the system made of it.
 */
@Entity
@Table(name = "exam_scans")
@Getter
@Setter
@NoArgsConstructor
public class ExamScan extends BaseEntity {

    /**
     * FK → exams.id. Null until resolved: a scan captured from within an exam
     * knows it immediately, one captured from the standalone entry point is
     * resolved from the extracted course code and exam date.
     */
    @Column(name = "exam_id")
    private UUID examId;

    /** FK → users.id — the teacher who captured the page. */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    /**
     * S3/MinIO object key of the captured page, from a prior upload to
     * {@code /api/files} — the same reference {@code AssignmentSubmission}
     * keeps, so the files module stays the owner of the object.
     */
    @Column(name = "image_key", nullable = false, length = 512)
    private String imageKey;

    @Column(name = "image_name", length = 255)
    private String imageName;

    @Column(name = "image_size")
    private Long imageSize;

    /** PENDING | EXTRACTED | CONFIRMED | FAILED | DISCARDED. */
    @Column(nullable = false, length = 10)
    private String status = STATUS_PENDING;

    /** Image stored, extraction not finished. */
    public static final String STATUS_PENDING   = "PENDING";
    /** Extraction succeeded; awaiting teacher review. */
    public static final String STATUS_EXTRACTED = "EXTRACTED";
    /** Teacher accepted (possibly after edits); a result exists. */
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    /** Extraction errored, or the page was unreadable. */
    public static final String STATUS_FAILED    = "FAILED";
    /** Teacher rejected the scan. */
    public static final String STATUS_DISCARDED = "DISCARDED";

    // ── What the extractor read, verbatim ────────────────────────────────────

    @Column(name = "read_roll_number", length = 50)
    private String readRollNumber;

    @Column(name = "read_student_name", length = 255)
    private String readStudentName;

    @Column(name = "read_course_code", length = 30)
    private String readCourseCode;

    @Column(name = "read_exam_date")
    private LocalDate readExamDate;

    /** Full extractor output, including per-question marks and confidence. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> extraction;

    /** Identifier of the extraction model, so a re-read is distinguishable. */
    @Column(length = 100)
    private String extractor;

    /** FK → users.id — the student the roll number resolved to; null if unresolved. */
    @Column(name = "matched_student_id")
    private UUID matchedStudentId;

    /**
     * Mismatches found while checking the extraction against the exam.
     * Advisory: shown on the review screen, never a hard failure, because the
     * teacher is the one who decides what the copy actually says.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private List<String> warnings = new ArrayList<>();

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
