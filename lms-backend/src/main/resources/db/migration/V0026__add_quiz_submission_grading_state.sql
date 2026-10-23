-- ============================================================
-- V0026__add_quiz_submission_grading_state.sql
--
-- A quiz with written-answer questions cannot be finished by the auto-grader:
-- the choice questions are scored on submit, the written ones wait for the
-- teacher. grading_status makes that state explicit:
--
--   IN_PROGRESS     the student has not submitted yet
--   PENDING_REVIEW  submitted; written answers are waiting for the teacher
--   GRADED          final score recorded
--
-- question_scores holds the per-question marks and feedback:
--   {"<question-uuid>": {"score": 2.5, "feedback": "...", "gradedBy": "TEACHER"}}
--
-- Transcript queries count only GRADED submissions, so a half-marked quiz
-- never reaches a student's grade.
--
-- The existing qsub_grading_consistency_chk already allows a submitted attempt
-- with no score while is_auto_graded is FALSE, which is exactly PENDING_REVIEW.
-- ============================================================

ALTER TABLE quiz_submissions
    ADD COLUMN grading_status  VARCHAR(16) NOT NULL DEFAULT 'GRADED'
        CONSTRAINT qsub_grading_status_chk
        CHECK (grading_status IN ('IN_PROGRESS', 'PENDING_REVIEW', 'GRADED')),
    ADD COLUMN question_scores JSONB       NOT NULL DEFAULT '{}',
    ADD COLUMN graded_by       UUID,
    ADD COLUMN graded_at       TIMESTAMP;

-- Every attempt so far was MCQ/MSQ: submitted ones are graded, the rest are open
UPDATE quiz_submissions SET grading_status = 'IN_PROGRESS' WHERE submitted_at IS NULL;

ALTER TABLE quiz_submissions
    ADD CONSTRAINT qsub_graded_by_fk
        FOREIGN KEY (graded_by) REFERENCES users (id)
        ON DELETE SET NULL;

-- A score exists exactly when the attempt is graded
ALTER TABLE quiz_submissions
    ADD CONSTRAINT qsub_status_score_chk
        CHECK (
            (grading_status = 'GRADED'         AND submitted_at IS NOT NULL AND score IS NOT NULL) OR
            (grading_status = 'PENDING_REVIEW' AND submitted_at IS NOT NULL AND score IS NULL) OR
            (grading_status = 'IN_PROGRESS'    AND submitted_at IS NULL)
        );

-- The teacher's review queue
CREATE INDEX idx_qsub_pending_review
    ON quiz_submissions (quiz_id)
    WHERE grading_status = 'PENDING_REVIEW';
