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
 * A course in the academic catalog. Independent of any particular semester.
 * Courses are reused across semesters via {@link ProgramSemesterCourse} offerings.
 */
@Entity
@Table(name = "courses")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class Course extends BaseEntity {

    /** Institutional code, e.g. "CS101". Unique across the catalog. */
    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "credit_hours", nullable = false)
    private int creditHours;

    /** ACTIVE | INACTIVE */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
