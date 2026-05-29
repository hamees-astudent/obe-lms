package com.lms.modules.users;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Extension table for TEACHER (and ASSISTANT) users.
 *
 * Uses {@code @MapsId} so the primary key is shared with the parent {@link User} row.
 */
@Entity
@Table(name = "teacher_profiles")
@Getter
@Setter
@NoArgsConstructor
@EntityListeners(AuditingEntityListener.class)
public class TeacherProfile {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "employee_number", unique = true, length = 30)
    private String employeeNumber;

    @Column(length = 100)
    private String department;

    @Column(length = 100)
    private String designation;

    @Column(length = 30)
    private String phone;

    /** S3 / MinIO object key for the profile picture. */
    @Column(name = "profile_picture_key")
    private String profilePictureKey;

    @Column(name = "joining_date")
    private LocalDate joiningDate;

    @Column(columnDefinition = "TEXT")
    private String bio;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
