package com.lms.modules.courses;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Teaching assistant or co-instructor on a {@link ProgramSemesterCourse}.
 * Composite PK: (psc_id, user_id).
 */
@Entity
@Table(name = "course_assistants")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CourseAssistant {

    @EmbeddedId
    private CourseAssistantId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("pscId")
    @JoinColumn(name = "psc_id")
    private ProgramSemesterCourse psc;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static CourseAssistant of(ProgramSemesterCourse psc, UUID userId) {
        var ca = new CourseAssistant();
        ca.psc = psc;
        ca.id = new CourseAssistantId(psc.getId(), userId);
        return ca;
    }

    public UUID getUserId() {
        return id.getUserId();
    }
}
