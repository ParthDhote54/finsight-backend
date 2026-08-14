package com.finsight.finsight_ai.ai.chat.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IntentClassifierTest {

    private final IntentClassifier classifier = new IntentClassifier();

    @Test
    void classifiesExplanationQueries() {
        assertThat(classifier.classify("Why did my food spending increase this month?"))
                .isEqualTo(IntentBucket.EXPLANATION);
        assertThat(classifier.classify("What caused the driving factor in expenses?"))
                .isEqualTo(IntentBucket.EXPLANATION);
    }

    @Test
    void classifiesComparisonQueries() {
        assertThat(classifier.classify("Compare my food spending this month with last month"))
                .isEqualTo(IntentBucket.COMPARISON);
        assertThat(classifier.classify("What is the difference between June and July spending?"))
                .isEqualTo(IntentBucket.COMPARISON);
    }

    @Test
    void classifiesLookupQueries() {
        assertThat(classifier.classify("Show my last 5 Amazon transactions"))
                .isEqualTo(IntentBucket.LOOKUP);
        assertThat(classifier.classify("List recent purchases"))
                .isEqualTo(IntentBucket.LOOKUP);
    }

    @Test
    void classifiesRecommendationQueries() {
        assertThat(classifier.classify("How can I save money on dining out?"))
                .isEqualTo(IntentBucket.RECOMMENDATION);
    }

    @Test
    void classifiesAggregateQueries() {
        assertThat(classifier.classify("How much did I spend on food in July?"))
                .isEqualTo(IntentBucket.AGGREGATE);
    }

    @Test
    void classifiesGeneralQueries() {
        assertThat(classifier.classify("Hello there!"))
                .isEqualTo(IntentBucket.GENERAL);
    }
}
