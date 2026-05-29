package com.lms.modules.programs;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SemesterRepository extends JpaRepository<Semester, UUID> {

    List<Semester> findAllByProgramIdOrderByStartDateDesc(UUID programId);

    List<Semester> findAllByProgramIdAndStatus(UUID programId, String status);

    Optional<Semester> findByProgramIdAndStatus(UUID programId, String status);

    boolean existsByProgramIdAndStatus(UUID programId, String status);

    boolean existsByProgramIdAndName(UUID programId, String name);
}
