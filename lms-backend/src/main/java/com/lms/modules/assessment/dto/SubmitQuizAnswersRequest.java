package com.lms.modules.assessment.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class SubmitQuizAnswersRequest {

    /**
     * Map of question-id (string) → selected option-id list.
     * Example: {"&lt;question-uuid&gt;": ["a"], "&lt;question-uuid&gt;": ["b","c"]}
     */
    @NotNull
    private Map<String, List<String>> answers;
}
