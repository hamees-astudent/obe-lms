/**
 * Study assistant and question generator — the LMS's NLP features.
 *
 * <ul>
 *   <li><b>Ingestion</b>: teacher-uploaded PDF/PPTX/DOCX material is read page
 *       by page and slide by slide, split into passages, indexed for
 *       PostgreSQL full-text search, and summarised into topics.</li>
 *   <li><b>Study assistant</b>: students ask questions about a course they are
 *       enrolled in and get answers grounded in, and citing, that course's
 *       material — and can generate mock quizzes and papers to practise on.</li>
 *   <li><b>Question generator</b>: teachers generate questions from chosen
 *       topics with a type mix, difficulty, Bloom levels, CLOs and a marks
 *       limit; every question gets a suggested CLO, Bloom level and marks. The
 *       teacher reviews them into a quiz or a printable question paper whose
 *       sets are shuffled to make copying harder.</li>
 * </ul>
 *
 * <p>Everything runs on this server: no third-party API is called. Other
 * modules' tables (materials, CLOs, enrollments) are read with plain SQL, as
 * elsewhere in the codebase; quizzes are written only through
 * {@code assessment.QuizAuthoringApi}.
 */
@org.springframework.modulith.ApplicationModule
package com.lms.modules.studyai;
