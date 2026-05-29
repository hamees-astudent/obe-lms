package com.lms.modules.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRecordRepository extends JpaRepository<AttendanceRecord, UUID> {

    List<AttendanceRecord> findAllBySessionId(UUID sessionId);

    List<AttendanceRecord> findAllByStudentId(UUID studentId);

    Optional<AttendanceRecord> findBySessionIdAndStudentId(UUID sessionId, UUID studentId);

    /**
     * Computes the attended / total counts for a student across all sessions
     * of a course offering. EXCUSED records are excluded from both counts.
     * PRESENT and LATE increment the attended counter.
     */
    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN ar.status IN ('PRESENT','LATE') THEN 1 ELSE 0 END), 0)  AS attended,
                COALESCE(SUM(CASE WHEN ar.status <> 'EXCUSED'          THEN 1 ELSE 0 END), 0)  AS total
            FROM attendance_records ar
            JOIN attendance_sessions s ON s.id = ar.session_id
            WHERE s.psc_id = :pscId AND ar.student_id = :studentId
            """, nativeQuery = true)
    AttendanceSummaryView computeSummary(
            @Param("pscId") UUID pscId,
            @Param("studentId") UUID studentId);
}
