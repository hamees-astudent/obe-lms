package com.lms.modules.courses;

import com.lms.shared.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * A content item in a course's knowledge-base feed.
 *
 * <p>Six types are supported: DOCUMENT, URL, ASSIGNMENT, QUIZ, ANNOUNCEMENT, VIDEO_LINK.
 * Type-specific data is stored in the {@code content} JSONB column.</p>
 *
 * <pre>Expected JSONB shapes:
 *   DOCUMENT     { "objectKey": "...", "fileName": "...", "fileSize": 1234, "mimeType": "..." }
 *   URL          { "url": "https://...", "linkText": "..." }
 *   ASSIGNMENT   { "assignmentId": "<uuid>" }
 *   QUIZ         { "quizId": "<uuid>" }
 *   ANNOUNCEMENT { "body": "<text>" }
 *   VIDEO_LINK   { "url": "https://...", "platform": "YOUTUBE|VIMEO|OTHER", "durationSeconds": 0 }
 * </pre>
 */
@Entity
@Table(name = "course_materials")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class CourseMaterial extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "psc_id", nullable = false)
    private ProgramSemesterCourse psc;

    /** FK → users.id (users module) — stored as plain UUID. */
    @Column(name = "uploaded_by", nullable = false)
    private UUID uploadedBy;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 255)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb", nullable = false)
    private Map<String, Object> content = new HashMap<>();

    @Column(nullable = false)
    private boolean visible = true;

    @Column(name = "order_index", nullable = false)
    private int orderIndex = 0;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
