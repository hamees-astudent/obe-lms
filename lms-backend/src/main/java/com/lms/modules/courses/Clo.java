package com.lms.modules.courses;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * Course Learning Outcome — a measurable competency the course must deliver.
 * CLOs are defined at the catalog-course level so they are reused across semester offerings.
 * They are mapped to {@link com.lms.modules.programs.Plo PLOs} via {@link CloPloMapping}.
 */
@Entity
@Table(name = "clos")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Clo extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** Short code, unique within the course, e.g. "CLO-1". */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    /** 1-based display order within the course. Unique per course. */
    @Column(name = "order_index", nullable = false)
    private int orderIndex;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
