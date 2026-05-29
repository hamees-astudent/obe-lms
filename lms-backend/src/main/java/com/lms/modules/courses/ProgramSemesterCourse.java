package com.lms.modules.courses;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A specific offering of a {@link Course} in one semester, assigned to a primary teacher.
 * Abbreviated PSC (Program–Semester–Course).
 * <p>
 * {@code semesterId} and {@code teacherId} are plain UUIDs to avoid cross-module ORM joins
 * (they reference the {@code programs} and {@code users} modules respectively).
 */
@Entity
@Table(name = "program_semester_courses")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class ProgramSemesterCourse extends BaseEntity {

    /** FK → semesters.id (programs module) — stored as plain UUID. */
    @Column(name = "semester_id", nullable = false)
    private UUID semesterId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    /** FK → users.id (users module) — stored as plain UUID. */
    @Column(name = "teacher_id", nullable = false)
    private UUID teacherId;

    /** 0 means unlimited. */
    @Column(name = "max_capacity", nullable = false)
    private int maxCapacity = 0;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
