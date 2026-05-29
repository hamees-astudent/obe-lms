package com.lms.modules.programs;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ProgramRepository extends JpaRepository<Program, UUID> {

    boolean existsByCode(String code);

    Optional<Program> findByCode(String code);

    Page<Program> findAllByStatus(String status, Pageable pageable);
}
