package com.lms.modules.files;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves a user's UUID from their email (JWT subject).
 * Uses plain JDBC to avoid a JPA dependency on the users module.
 */
@Component
@RequiredArgsConstructor
class FileUserResolver {

    private final NamedParameterJdbcTemplate jdbc;

    UUID resolveId(String email) {
        List<UUID> ids = jdbc.query(
                "SELECT id FROM users WHERE email = :email",
                Map.of("email", email),
                (rs, i) -> UUID.fromString(rs.getString("id")));
        if (ids.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "User not found: " + email);
        }
        return ids.get(0);
    }
}
