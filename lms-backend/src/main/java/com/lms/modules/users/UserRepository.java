package com.lms.modules.users;

import com.lms.shared.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Public API of the users module — accessible by {@code infrastructure.security}
 * to load users during JWT authentication.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    // ── Paginated list queries with optional filters ──────────────────────────

    Page<User> findAllByRole(Role role, Pageable pageable);

    Page<User> findAllByStatus(String status, Pageable pageable);

    Page<User> findAllByRoleAndStatus(Role role, String status, Pageable pageable);
}
