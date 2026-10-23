package com.lms.infrastructure.nlp;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class BloomClassifierTest {

    private final BloomClassifier classifier = new BloomClassifier(new TextAnalyzer());

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Define a primary key.|SHORT_ANSWER|REMEMBER",
            "What is the time complexity of binary search?|MCQ|REMEMBER",
            "Which of the following is a NoSQL database?|MCQ|REMEMBER",
            "Explain how TCP establishes a connection.|SHORT_ANSWER|UNDERSTAND",
            "Briefly describe the role of an index.|SHORT_ANSWER|UNDERSTAND",
            "Calculate the subnet mask for 30 hosts.|SHORT_ANSWER|APPLY",
            "Compare stacks and queues with examples.|LONG_ANSWER|ANALYZE",
            "What is the difference between a process and a thread?|SHORT_ANSWER|ANALYZE",
            "Evaluate whether normalisation always improves performance.|LONG_ANSWER|EVALUATE",
            "Design a database schema for a library system.|LONG_ANSWER|CREATE",
            "Q3: Justify the choice of a B-tree for disk indexes.|LONG_ANSWER|EVALUATE",
            "The data model of a relational database is based on|MCQ|REMEMBER",
    })
    void classifiesByActionVerb(String text, String type, BloomLevel expected) {
        assertThat(classifier.classify(text, type).level()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "Apply normalisation rules to remove redundancy|APPLY",
            "Design and implement a relational schema|CREATE",
            "Explain image formation and basic image processing operations|UNDERSTAND",
    })
    void classifiesOutcomes(String clo, BloomLevel expected) {
        assertThat(classifier.classifyOutcome(clo)).contains(expected);
    }
}
