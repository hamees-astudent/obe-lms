package com.lms.modules.assessment;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AssignmentSubmissionRepository extends JpaRepository<AssignmentSubmission, UUID> {

    List<AssignmentSubmission> findAllByAssignmentId(UUID assignmentId);

    List<AssignmentSubmission> findAllByStudentId(UUID studentId);

    Optional<AssignmentSubmission> findByAssignmentIdAndStudentId(UUID assignmentId, UUID studentId);

    /**
     * Identity of each given student, so staff see who submitted rather than a
     * bare UUID. Native SQL keeps the assessment module free of cross-module
     * repository injection. Callers must not pass an empty collection.
     */
    @Query(value = """
            SELECT u.id::text          AS studentId,
                   u.name              AS studentName,
                   u.email             AS studentEmail,
                   sp.student_number   AS studentNumber
            FROM users u
            LEFT JOIN student_profiles sp ON sp.user_id = u.id
            WHERE u.id IN (:studentIds)
            """, nativeQuery = true)
    List<SubmitterView> findSubmitters(@Param("studentIds") Collection<UUID> studentIds);
}
