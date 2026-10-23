package com.lms.modules.assessment;

import com.lms.modules.assessment.dto.CreateQuizQuestionRequest;
import com.lms.modules.assessment.dto.CreateQuizRequest;
import com.lms.modules.assessment.dto.QuizResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * What other modules may do to quizzes: create one and add questions to it.
 *
 * <p>The question generator saves accepted drafts through here rather than
 * writing the tables itself, so the quiz's invariants — staff-only writes,
 * question validation, the cached total, unique order — stay this module's.
 */
@Service
@RequiredArgsConstructor
public class QuizAuthoringApi {

    private final AssessmentService assessmentService;
    private final QuizQuestionRepository questionRepository;
    private final QuestionCloMappingRepository cloMappings;

    public QuizResponse createQuiz(UUID pscId, String title, String description, Integer durationMinutes,
                                   UUID actorId, boolean isAdmin) {
        CreateQuizRequest req = new CreateQuizRequest();
        req.setPscId(pscId);
        req.setTitle(title);
        req.setDescription(description);
        req.setDurationMinutes(durationMinutes);
        return assessmentService.createQuiz(actorId, isAdmin, req);
    }

    public QuizResponse getQuiz(UUID quizId) {
        return assessmentService.getQuiz(quizId);
    }

    /**
     * Appends questions after the quiz's existing ones, in order, then derives
     * quiz-level CLO mappings from the questions' own.
     *
     * @return the new question ids, in the order given
     */
    @Transactional
    public List<UUID> addQuestions(UUID quizId, List<CreateQuizQuestionRequest> questions,
                                   UUID actorId, boolean isAdmin) {
        int next = questionRepository.findAllByQuizIdOrderByOrderIndexAsc(quizId).stream()
                .mapToInt(QuizQuestion::getOrderIndex).max().orElse(-1) + 1;
        List<UUID> ids = new java.util.ArrayList<>();
        for (CreateQuizQuestionRequest q : questions) {
            q.setOrderIndex(next++);
            ids.add(assessmentService.addQuestion(quizId, q, actorId, isAdmin).getId());
        }
        cloMappings.addMissingQuizMappings(quizId);
        return ids;
    }
}
