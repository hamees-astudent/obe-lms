package com.lms.modules.users;

import com.lms.shared.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    /**
     * Free-text lookup over name, email and institutional roll number, with the
     * same optional role/status filters as the plain listing.
     *
     * <p>Exists so staff can find a student the way they actually know them —
     * by name or roll number — instead of having to produce a 36-character UUID
     * that is never displayed anywhere.
     */
    @Query("""
            SELECT u FROM User u
            WHERE (:role IS NULL OR u.role = :role)
              AND (:status IS NULL OR u.status = :status)
              AND (LOWER(u.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(u.email) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR EXISTS (SELECT 1 FROM StudentProfile sp
                              WHERE sp.userId = u.id
                                AND LOWER(sp.studentNumber) LIKE LOWER(CONCAT('%', :q, '%'))))
            """)
    Page<User> search(@Param("q") String q,
                      @Param("role") Role role,
                      @Param("status") String status,
                      Pageable pageable);
}
