# Plan: LMS System — Full Requirements & Architecture

## Decisions (Confirmed)
- Language: Java with Spring Boot 3.x
- Architecture: Modular Monolith using Spring Modulith + Hexagonal (Ports & Adapters) per module
- Frontend: React.js — bundled inside the Java project, served from Spring Boot static resources
- E2E Testing: Playwright (TypeScript) — separate project
- Database: PostgreSQL, single `users` table for all user types
- Grading scale: configurable per program via `grading_scales` + `grading_scale_entries` tables; nullable `program_id` = global default scale; scale is snapshot-referenced on transcript generation (immutable)
- Two projects to initialize: `lms-backend/`, `lms-e2e/`

---

## Key Tech Stack
- Spring Boot 3.x + Spring Modulith
- Spring Security + JWT (role-based; roles: ADMIN, TEACHER, ASSISTANT, STUDENT)
- Spring Data JPA (PostgreSQL) + Flyway
- Spring Cache + Redis
- Spring Cloud OpenFeign / WebClient (external API integration)
- Spring Cloud Stream + Kafka (notification/event bus)
- Spring Boot Actuator + Micrometer
- React.js + Vite — built output served from `src/main/resources/static/`

---

## Functional Requirements

### 1. User Management (Single Table)
- One `users` table for ALL user types: ADMIN, TEACHER, ASSISTANT, STUDENT
- Discriminator via `role` column (enum), not separate tables
- Common fields: id, name, email, password_hash, role, status, created_at
- Role-specific profile data in separate extension tables (`student_profiles`, `teacher_profiles`) linked by FK to `users`
- Spring Security loads roles from `users.role`

### 2. Program & Semester Structure
- `programs` table (e.g., BS Computer Science, MBA)
- `semesters` table — belongs to a program, has start/end dates and a `status` (OPEN, CLOSED)
- `courses` are independent entities; linked to programs and semesters via a `program_semester_courses` join table
- A course can appear in multiple programs and multiple semesters simultaneously
- `enrollments` table: student ↔ program_semester_course

### 3. Course Enrichment (Knowledge Base)
Teachers and assistants can attach resources to a course section. Supported types:
- DOCUMENT (PDF, Word — stored in file storage, S3/MinIO)
- URL (external link)
- ASSIGNMENT (with deadline, max marks, submission support)
- QUIZ (questions, options, correct answers, time limit)
- ANNOUNCEMENT (text/rich text)
- VIDEO_LINK (YouTube/Vimeo embed URL)
All stored in a single polymorphic `course_materials` table with a `material_type` discriminator and a `content` JSONB column for type-specific data

### 4. Attendance Module
- `attendance_sessions` — created by teacher/assistant per lecture slot (date, course section)
- `attendance_records` — per-student record per session (PRESENT, ABSENT, LATE, EXCUSED)
- Attendance summary per student per course computable on demand
- Threshold alert: notify student if attendance drops below configurable %

### 5. Assignments & Quizzes (Assessments)
- Assignments: students upload submission files or text; teacher/assistant grades with marks + feedback
- Quizzes: auto-graded MCQ/MSQ; results stored immediately on submission
- Both linked to PLO/CLO mappings

### 6. PLO / CLO Support
- `plos` (Program Learning Outcomes) — belong to a program
- `clos` (Course Learning Outcomes) — belong to a course
- `clo_plo_mappings` — many-to-many linking CLOs to PLOs
- `assessment_clo_mappings` — each assignment/quiz linked to one or more CLOs
- On semester closure, system computes CLO attainment per student per course and PLO attainment per student per program

### 7. Notification System
- Event-driven via Kafka internal topics
- Notification types: ASSIGNMENT_POSTED, QUIZ_OPEN, GRADE_RELEASED, ATTENDANCE_WARNING, SEMESTER_CLOSED, TRANSCRIPT_READY, ANNOUNCEMENT
- `notifications` table: recipient_id, type, title, body, is_read, created_at
- Delivery channels: in-app (REST polling or WebSocket), email (JavaMailSender)
- Recipients: STUDENT, TEACHER, ASSISTANT, ADMIN — targeted by role or individual

### 8. Teaching Assistants
- ASSISTANT role stored in `users.role`
- `course_assistants` join table: assistant ↔ program_semester_course (scoped, not global)
- Assistants can: post materials, create attendance sessions, grade assignments
- Assistants cannot: publish quizzes, close semester, manage enrollments

### 9. Semester Transcript
- Triggered when admin sets `semester.status = CLOSED`
- System generates a `transcripts` record per student per semester containing:
  - All courses, marks, grades, attendance %, CLO attainment
  - Weighted GPA for that semester
- Stored as a snapshot (JSONB or normalized rows) — immutable after generation
- Notification sent to student: TRANSCRIPT_READY
- Student can view/download transcript via REST endpoint; PDF generation via JasperReports or iText

### 10. Frontend — Bundled Inside Java Project
- React.js + Vite project lives at `src/main/frontend/`
- Maven build runs `npm run build` (via `frontend-maven-plugin`) before packaging
- Vite output dir set to `src/main/resources/static/`
- Spring Boot serves the SPA from `/`; all `/api/**` routes go to REST controllers
- Single deployable fat JAR contains both backend and frontend

---

## Module Map (Spring Modulith)

```
lms-backend/
  src/main/java/com/lms/
    modules/
      auth/           — JWT issue/refresh, login, logout, password reset
      users/          — user CRUD, role management, profile extensions
      programs/       — programs, semesters, lifecycle (OPEN→CLOSED)
      courses/        — course catalog, materials (knowledge base), PLO/CLO
      enrollment/     — student enrollment per program_semester_course
      attendance/     — sessions, records, summary, threshold alerts
      assessment/     — assignments, quizzes, submissions, grading
      transcript/     — semester closure trigger, snapshot generation, PDF export
      notifications/  — event consumers, delivery (in-app + email), read/unread
      files/          — S3/MinIO upload/download adapter
    infrastructure/   — security config, DB config, Kafka config, Redis config
    shared/           — base domain types, common value objects, event contracts
  src/main/frontend/  — React.js + Vite source
  src/main/resources/
    static/           — Vite build output (auto-generated, gitignored)
    db/migration/     — Flyway SQL migrations
    application.yml
```

---

## Database Schema (Key Tables)
- `users` — id, name, email, password_hash, role (ADMIN/TEACHER/ASSISTANT/STUDENT), status
- `student_profiles` — user_id FK, enrollment_no, program_id FK, batch_year
- `teacher_profiles` — user_id FK, department, designation
- `programs` — id, name, degree_type, total_credit_hours
- `plos` — id, program_id FK, code, description
- `semesters` — id, program_id FK, name, start_date, end_date, status
- `courses` — id, code, name, credit_hours, description
- `program_semester_courses` — id, program_id, semester_id, course_id, teacher_id FK
- `course_assistants` — program_semester_course_id FK, assistant_id FK
- `clos` — id, course_id FK, code, description
- `clo_plo_mappings` — clo_id FK, plo_id FK
- `enrollments` — student_id FK, program_semester_course_id FK, status
- `course_materials` — id, program_semester_course_id FK, posted_by FK, material_type (enum), title, content (JSONB), created_at
- `attendance_sessions` — id, program_semester_course_id FK, conducted_by FK, session_date
- `attendance_records` — session_id FK, student_id FK, status (PRESENT/ABSENT/LATE/EXCUSED)
- `assignments` — id, program_semester_course_id FK, title, description, deadline, max_marks
- `assignment_submissions` — id, assignment_id FK, student_id FK, submitted_at, file_url, marks, feedback
- `quizzes` — id, program_semester_course_id FK, title, time_limit_mins, total_marks, status
- `quiz_questions` — id, quiz_id FK, question_text, type (MCQ/MSQ), options (JSONB), correct_answer (JSONB)
- `quiz_submissions` — id, quiz_id FK, student_id FK, answers (JSONB), marks, submitted_at
- `assessment_clo_mappings` — assessment_type, assessment_id, clo_id FK
- `notifications` — id, recipient_id FK, type, title, body, is_read, created_at
- `grading_scales` — id, program_id FK (nullable = global default), name (e.g. "4.0 Scale"), is_default
- `grading_scale_entries` — id, scale_id FK, letter_grade (A/B+/B/C…), min_percentage, max_percentage, grade_points (e.g. 4.0/3.7…)
- `transcripts` — id, student_id FK, semester_id FK, grading_scale_id FK (immutable snapshot reference), data (JSONB), generated_at, status (DRAFT/FINAL)

---

## E2E Testing Project (lms-e2e — Playwright TypeScript)
```
lms-e2e/
  tests/
    auth/
    courses/
    assessment/
    attendance/
    transcript/
    notifications/
  fixtures/       — shared test data factories (API seeding)
  helpers/        — auth token setup, API request helpers
  playwright.config.ts
  tsconfig.json
  package.json
```
