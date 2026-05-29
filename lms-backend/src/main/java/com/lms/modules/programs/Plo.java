package com.lms.modules.programs;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Program Learning Outcome — describes what a graduate should be able to do.
 * CLOs in courses are mapped to PLOs to track attainment.
 */
@Entity
@Table(name = "plos")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Plo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    /** Short code, e.g. "PLO1". Unique within the program. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1-based display order within the program. Unique per program. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
