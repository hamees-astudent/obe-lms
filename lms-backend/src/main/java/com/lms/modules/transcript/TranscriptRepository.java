package com.lms.modules.transcript;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TranscriptRepository extends JpaRepository<Transcript, UUID> {

    List<Transcript> findAllByStudentIdOrderByCreatedAtDesc(UUID studentId);

    List<Transcript> findAllBySemesterId(UUID semesterId);

    Optional<Transcript> findByStudentIdAndSemesterId(UUID studentId, UUID semesterId);

    @Modifying
    @Query("DELETE FROM Transcript t WHERE t.studentId = :studentId AND t.semesterId = :semesterId")
    void deleteByStudentIdAndSemesterId(
            @Param("studentId") UUID studentId,
            @Param("semesterId") UUID semesterId);
}
