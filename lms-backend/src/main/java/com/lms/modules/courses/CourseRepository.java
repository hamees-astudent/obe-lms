package com.lms.modules.courses;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseRepository extends JpaRepository<Course, UUID> {

    boolean existsByCode(String code);

    Optional<Course> findByCode(String code);

    Page<Course> findAllByStatus(String status, Pageable pageable);
}
