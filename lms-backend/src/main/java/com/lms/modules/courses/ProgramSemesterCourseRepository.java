package com.lms.modules.courses;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProgramSemesterCourseRepository extends JpaRepository<ProgramSemesterCourse, UUID> {

    List<ProgramSemesterCourse> findAllBySemesterId(UUID semesterId);

    List<ProgramSemesterCourse> findAllByCourse_Id(UUID courseId);

    List<ProgramSemesterCourse> findAllByTeacherId(UUID teacherId);

    boolean existsBySemesterIdAndCourse_Id(UUID semesterId, UUID courseId);

    Optional<ProgramSemesterCourse> findBySemesterIdAndCourse_Id(UUID semesterId, UUID courseId);
}
