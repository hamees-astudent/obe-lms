package com.lms.modules.users;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StudentProfileRepository extends JpaRepository<StudentProfile, UUID> {

    Optional<StudentProfile> findByStudentNumber(String studentNumber);

    boolean existsByStudentNumber(String studentNumber);
}
