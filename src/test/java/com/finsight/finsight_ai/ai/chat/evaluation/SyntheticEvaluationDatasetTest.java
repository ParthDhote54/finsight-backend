package com.finsight.finsight_ai.ai.chat.evaluation;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SyntheticEvaluationDatasetTest {

    @Test
    void smallDatasetCoversPhase5BScenariosWithoutBecomingFullBenchmark() {
        var cases = SyntheticEvaluationDataset.smallPhase5BDataset();

        assertThat(cases).hasSizeBetween(10, 15);
        assertThat(cases).extracting(EvaluationCase::category)
                .contains(
                        EvaluationCategory.AGGREGATE,
                        EvaluationCategory.COMPARISON,
                        EvaluationCategory.LOOKUP,
                        EvaluationCategory.EXPLANATION,
                        EvaluationCategory.SUBSCRIPTION,
                        EvaluationCategory.PROJECTION,
                        EvaluationCategory.RECONCILIATION,
                        EvaluationCategory.SEMANTIC,
                        EvaluationCategory.GENERAL,
                        EvaluationCategory.SAFE_REFUSAL);
        assertThat(cases)
                .anySatisfy(evaluationCase -> assertThat(evaluationCase.ragExpectation())
                        .isEqualTo(RagExpectation.REQUIRED))
                .anySatisfy(evaluationCase -> assertThat(evaluationCase.safeRefusalExpected()).isTrue())
                .anySatisfy(evaluationCase -> assertThat(evaluationCase.turns()).hasSize(2))
                .allSatisfy(evaluationCase -> assertThat(evaluationCase.turns()).isNotEmpty());
    }

    @Test
    void fullPhase5CDatasetValidationTest() {
        var cases = SyntheticEvaluationDataset.fullPhase5CDataset();

        assertThat(cases).hasSizeBetween(50, 75);

        // All IDs must be unique
        var ids = cases.stream().map(EvaluationCase::id).toList();
        assertThat(ids).doesNotContainNull().doesNotHaveDuplicates();

        // Must cover all categories
        assertThat(cases).extracting(EvaluationCase::category)
                .contains(
                        EvaluationCategory.AGGREGATE,
                        EvaluationCategory.LOOKUP,
                        EvaluationCategory.COMPARISON,
                        EvaluationCategory.EXPLANATION,
                        EvaluationCategory.MERCHANT,
                        EvaluationCategory.SUBSCRIPTION,
                        EvaluationCategory.PROJECTION,
                        EvaluationCategory.RECONCILIATION,
                        EvaluationCategory.SEMANTIC,
                        EvaluationCategory.GENERAL,
                        EvaluationCategory.SAFE_REFUSAL);

        // Validate structure of every case
        assertThat(cases).allSatisfy(evaluationCase -> {
            assertThat(evaluationCase.id()).isNotBlank();
            assertThat(evaluationCase.turns()).isNotEmpty();
            assertThat(evaluationCase.category()).isNotNull();
            assertThat(evaluationCase.expectedIntent()).isNotNull();
        });
    }
}
