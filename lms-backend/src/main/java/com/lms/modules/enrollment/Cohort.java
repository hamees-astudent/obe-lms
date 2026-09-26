package com.lms.modules.enrollment;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * A named group of students that can be enrolled in an offering in one step.
 *
 * <p>Membership lives in {@code cohort_members} and is managed through native
 * queries on {@link CohortRepository}, because every read of it joins the
 * users table, which belongs to another module.</p>
 */
@Entity
@Table(name = "cohorts")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Cohort extends BaseEntity {

    @Column(nullable = false, length = 120)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
