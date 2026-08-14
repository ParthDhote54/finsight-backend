package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.application.IntentBucket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EvaluationCaseModelTest {

    @Test
    void evaluationCaseRequiresAtLeastOneTurnAndNormalizesCollections() {
        assertThatThrownBy(() -> new EvaluationCase(
                "bad",
                EvaluationCategory.GENERAL,
                List.of(),
                IntentBucket.GENERAL,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least one turn");

        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "general",
                EvaluationCategory.GENERAL,
                "Hello",
                IntentBucket.GENERAL,
                null,
                null,
                null,
                null,
                null,
                false,
                false,
                null,
                null);

        assertThat(evaluationCase.allowedTools()).isEmpty();
        assertThat(evaluationCase.ragExpectation()).isEqualTo(RagExpectation.OPTIONAL);
        assertThat(evaluationCase.expectsModelToolSelection()).isFalse();
    }

    @Test
    void allowedToolPathSupportsExactAndOrderedAdditionalMatching() {
        assertThat(AllowedToolPath.exact("spending_delta_explainer")
                .matches(List.of("spending_delta_explainer"))).isTrue();
        assertThat(AllowedToolPath.exact("spending_delta_explainer")
                .matches(List.of("spending_delta_explainer", "merchant_breakdown"))).isFalse();
        assertThat(AllowedToolPath.allowingAdditional("spending_delta_explainer", "merchant_breakdown")
                .matches(List.of("spending_delta_explainer", "top_merchants", "merchant_breakdown"))).isTrue();
    }

    @Test
    void argumentConstraintsCoverExistsAbsentEqualsAllowedRangeAndMustNotEqual() {
        ToolInvocationTrace invocation = ToolInvocationTrace.model(
                "balance_reconciler",
                Map.of("startingBalance", 50000, "accountId", "checking"));

        assertThat(ArgumentConstraint.exists("balance_reconciler", "startingBalance")
                .satisfiedBy(invocation)).isTrue();
        assertThat(ArgumentConstraint.absent("balance_reconciler", "dangerous")
                .satisfiedBy(invocation)).isTrue();
        assertThat(ArgumentConstraint.equalsValue("balance_reconciler", "startingBalance", "50000.0")
                .satisfiedBy(invocation)).isTrue();
        assertThat(ArgumentConstraint.inValues("balance_reconciler", "accountId", Set.of("checking", "savings"))
                .satisfiedBy(invocation)).isTrue();
        assertThat(ArgumentConstraint.numericRange(
                        "balance_reconciler",
                        "startingBalance",
                        BigDecimal.valueOf(10000),
                        BigDecimal.valueOf(60000))
                .satisfiedBy(invocation)).isTrue();
        assertThat(ArgumentConstraint.mustNotEqual("balance_reconciler", "startingBalance", 50000)
                .satisfiedBy(invocation)).isFalse();
    }
}
