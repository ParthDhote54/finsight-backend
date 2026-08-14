package com.finsight.finsight_ai.ai.chat.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialQueryDetectorTest {

    private final FinancialQueryDetector detector = new FinancialQueryDetector();

    @Test
    void detectsPersonalFinancialDataQuestions() {
        assertThat(detector.requiresToolEvidence("How much did I spend on food?")).isTrue();
        assertThat(detector.requiresToolEvidence("Compare my expenses this month.")).isTrue();
        assertThat(detector.requiresToolEvidence("Show the last 5 transactions.")).isTrue();
        assertThat(detector.requiresToolEvidence("Why did my spending increase?")).isTrue();
        assertThat(detector.requiresToolEvidence("What is my account balance?")).isTrue();
    }

    @Test
    void doesNotClassifyGreetingsOrGeneralEducation() {
        assertThat(detector.requiresToolEvidence("Hello there")).isFalse();
        assertThat(detector.requiresToolEvidence("What is compound interest?")).isFalse();
        assertThat(detector.requiresToolEvidence("Explain inflation")).isFalse();
    }
}
