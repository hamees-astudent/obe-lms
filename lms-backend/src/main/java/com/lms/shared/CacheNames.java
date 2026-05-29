package com.lms.shared;

/**
 * String constants for all Spring Cache names used across the application.
 *
 * <p>Use these in {@code @Cacheable}, {@code @CacheEvict}, and {@code @CachePut}
 * annotations to avoid magic strings and typos.
 *
 * <pre>{@code
 *   @Cacheable(CacheNames.COURSES)
 *   public CourseDto findById(UUID id) { ... }
 *
 *   @CacheEvict(value = CacheNames.COURSES, key = "#id")
 *   public void deleteById(UUID id) { ... }
 * }</pre>
 */
public final class CacheNames {

    /** User lookups by email / ID — 30 min TTL */
    public static final String USERS = "users";

    /** Course catalog — 2 h TTL */
    public static final String COURSES = "courses";

    /** Program catalog — 4 h TTL */
    public static final String PROGRAMS = "programs";

    /** Grading scale definitions (rarely changes) — 4 h TTL */
    public static final String GRADING_SCALES = "grading-scales";

    /** Course materials per program_semester_course — 1 h TTL */
    public static final String COURSE_MATERIALS = "course-materials";

    /** Student enrollment per program_semester_course — 30 min TTL */
    public static final String ENROLLMENT = "enrollment";

    /** Computed attendance summary per student per course — 15 min TTL */
    public static final String ATTENDANCE_SUMMARY = "attendance-summary";

    private CacheNames() {
        // utility class — no instances
    }
}
