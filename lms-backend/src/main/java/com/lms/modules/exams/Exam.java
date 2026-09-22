package com.lms.modules.exams;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * A paper exam held for one course offering.
 *
 * <p>Unlike an {@code Assignment} or {@code Quiz}, nothing is submitted through
 * the system: the student writes on paper and the teacher marks it by hand.
 * Marks re-enter the system when the copy's first page is photographed and
 * confirmed — see {@link ExamScan}.
 *
 * <p>The exam and its {@link ExamQuestion} list are defined <em>before</em> any
 * copy is scanned. That ordering is what makes the scan safe to trust: an
 * extracted marks table is checked against a known set of question labels and
 * maxima, so a misread "18" against a question worth 10 is caught rather than
 * stored.
 */
@Entity
@Table(name = "exams")
@Getter
@Setter
@NoArgsConstructor
public class Exam extends BaseEntity {

    /** FK → program_semester_courses.id (courses module) — plain UUID. */
    @Column(name = "psc_id", nullable = false)
    private UUID pscId;

    /** FK → users.id (users module) — plain UUID. */
    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(nullable = false, length = 255)
    private String title;

    /** MIDTERM | FINAL | SESSIONAL | MAKEUP — see {@link #TYPES}. */
    @Column(name = "exam_type", nullable = false, length = 20)
    private String examType = TYPE_MIDTERM;

    public static final String TYPE_MIDTERM   = "MIDTERM";
    public static final String TYPE_FINAL     = "FINAL";
    public static final String TYPE_SESSIONAL = "SESSIONAL";
    public static final String TYPE_MAKEUP    = "MAKEUP";

    public static final Set<String> TYPES =
            Set.of(TYPE_MIDTERM, TYPE_FINAL, TYPE_SESSIONAL, TYPE_MAKEUP);

    /** The date printed on the copy; cross-checked against every scan. */
    @Column(name = "exam_date", nullable = false)
    private LocalDate examDate;

    @Column(name = "total_marks", nullable = false, precision = 7, scale = 2)
    private BigDecimal totalMarks;

    /** DRAFT | OPEN | LOCKED — see {@link #STATUSES}. */
    @Column(nullable = false, length = 10)
    private String status = STATUS_DRAFT;

    /** Question list still being edited; scanning is refused. */
    public static final String STATUS_DRAFT  = "DRAFT";
    /** Accepting scans and manual mark entry. */
    public static final String STATUS_OPEN   = "OPEN";
    /** Marks finalised; no further results may be written. */
    public static final String STATUS_LOCKED = "LOCKED";

    public static final Set<String> STATUSES =
            Set.of(STATUS_DRAFT, STATUS_OPEN, STATUS_LOCKED);

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** Whether marks may currently be written against this exam. */
    public boolean acceptsMarks() {
        return STATUS_OPEN.equals(status);
    }
}
