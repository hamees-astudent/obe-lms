package com.lms.shared;

/**
 * Application-wide role enum.
 * Stored as a STRING in the {@code users.role} column.
 * Spring Security authorities are prefixed: {@code ROLE_ADMIN}, etc.
 */
public enum Role {
    ADMIN,
    TEACHER,
    ASSISTANT,
    STUDENT
}
