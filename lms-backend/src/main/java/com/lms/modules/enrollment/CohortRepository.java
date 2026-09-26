package com.lms.modules.enrollment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CohortRepository extends JpaRepository<Cohort, UUID> {

    boolean existsByNameIgnoreCase(String name);

    boolean existsByNameIgnoreCaseAndIdNot(String name, UUID id);

    List<Cohort> findAllByOrderByNameAsc();

    // ── Membership (native: every read joins users / student_profiles) ────────

    @Query(value = """
            SELECT CAST(cohort_id AS text) AS cohortId, COUNT(*) AS memberCount
            FROM cohort_members
            GROUP BY cohort_id
            """, nativeQuery = true)
    List<CohortSizeView> countMembers();

    @Query(value = "SELECT COUNT(*) FROM cohort_members WHERE cohort_id = :cohortId",
           nativeQuery = true)
    long countMembers(@Param("cohortId") UUID cohortId);

    /** Members in roll-number order, the order registers are kept in. */
    @Query(value = """
            SELECT CAST(u.id AS text)  AS studentId,
                   u.name              AS name,
                   u.email             AS email,
                   sp.student_number   AS studentNumber,
                   u.role              AS role,
                   u.status            AS status
            FROM cohort_members m
            JOIN users u                  ON u.id = m.student_id
            LEFT JOIN student_profiles sp ON sp.user_id = m.student_id
            WHERE m.cohort_id = :cohortId
            ORDER BY sp.student_number NULLS LAST, u.name
            """, nativeQuery = true)
    List<CohortMemberView> findMembers(@Param("cohortId") UUID cohortId);

    @Query(value = """
            SELECT CAST(u.id AS text)  AS studentId,
                   u.name              AS name,
                   u.email             AS email,
                   sp.student_number   AS studentNumber,
                   u.role              AS role,
                   u.status            AS status
            FROM users u
            LEFT JOIN student_profiles sp ON sp.user_id = u.id
            WHERE u.id IN (:ids)
            """, nativeQuery = true)
    List<CohortMemberView> findUsersByIds(@Param("ids") Collection<UUID> ids);

    /** Matches roll numbers case-insensitively; callers pass them lower-cased. */
    @Query(value = """
            SELECT CAST(u.id AS text)  AS studentId,
                   u.name              AS name,
                   u.email             AS email,
                   sp.student_number   AS studentNumber,
                   u.role              AS role,
                   u.status            AS status
            FROM student_profiles sp
            JOIN users u ON u.id = sp.user_id
            WHERE lower(sp.student_number) IN (:numbers)
            """, nativeQuery = true)
    List<CohortMemberView> findUsersByStudentNumbers(@Param("numbers") Collection<String> numbers);

    /** @return 1 if added, 0 if the student was already a member. */
    @Modifying
    @Query(value = """
            INSERT INTO cohort_members (cohort_id, student_id)
            VALUES (:cohortId, :studentId)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int addMember(@Param("cohortId") UUID cohortId, @Param("studentId") UUID studentId);

    @Modifying
    @Query(value = "DELETE FROM cohort_members WHERE cohort_id = :cohortId AND student_id = :studentId",
           nativeQuery = true)
    int removeMember(@Param("cohortId") UUID cohortId, @Param("studentId") UUID studentId);
}
