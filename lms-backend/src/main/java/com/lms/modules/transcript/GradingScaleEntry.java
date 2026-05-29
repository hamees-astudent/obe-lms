package com.lms.modules.transcript;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "grading_scale_entries")
@Getter
@Setter
@NoArgsConstructor
public class GradingScaleEntry extends BaseEntity {

    /** FK → grading_scales.id (same module). */
    @Column(name = "scale_id", nullable = false)
    private UUID scaleId;

    @Column(name = "grade_letter", nullable = false, length = 3)
    private String gradeLetter;

    /** min_percentage ≤ score < max_percentage (top entry max is inclusive at 100). */
    @Column(name = "min_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal minPercentage;

    @Column(name = "max_percentage", nullable = false, precision = 5, scale = 2)
    private BigDecimal maxPercentage;

    @Column(name = "grade_points", nullable = false, precision = 4, scale = 2)
    private BigDecimal gradePoints;

    /** Display and sort order within the scale (1 = highest grade). */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
