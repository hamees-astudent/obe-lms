package com.lms.modules.users;

import com.lms.shared.BaseEntity;
import com.lms.shared.Role;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Core user entity — single table for all roles (ADMIN, TEACHER, ASSISTANT, STUDENT).
 * Role-specific profile data lives in {@code student_profiles} / {@code teacher_profiles}
 * extension tables (added in the users module task).
 *
 * {@code id} and {@code created_at} are inherited from {@link BaseEntity}.
 * Kept in the module root package so it is part of the public API and can be
 * referenced by {@code infrastructure.security}.
 */
@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
public class User extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    /** ACTIVE | INACTIVE | SUSPENDED */
    @Column(nullable = false, length = 20)
    private String status = "ACTIVE";
}
