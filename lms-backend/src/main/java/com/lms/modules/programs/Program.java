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
 * Academic program (e.g. "BS Computer Science").
 * PLOs, semesters and course offerings are all scoped to a program.
 */
@Entity
@Table(name = "programs")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Program extends BaseEntity {

    @Column(nullable = false, length = 180)
    private String name;

    /** Short unique identifier, e.g. "BSCS". */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    /** ACTIVE | INACTIVE */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
