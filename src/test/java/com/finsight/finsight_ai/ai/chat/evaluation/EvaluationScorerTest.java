package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.application.IntentBucket;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationScorerTest {

    private final EvaluationScorer scorer = new EvaluationScorer();

    @Test
    void correctModelToolPassesSystemAndModelSelection() {
        EvaluationRunResult result = score(
                caseWithRequiredTool("aggregate", "spend_by_category"),
                trace(List.of(ToolInvocationTrace.model("spend_by_category", Map.of("month", "2026-06"))),
                        List.of(),
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(result.systemOutcomePassed()).isTrue();
        assertThat(result.modelSelectionPassed()).isTrue();
        assertThat(result.recoveryTriggered()).isFalse();
        assertThat(result.failureReasons()).isEmpty();
    }

    @Test
    void modelMissRecoveredKeepsSystemSuccessButFailsModelSelection() {
        EvaluationRunResult result = score(
                caseWithRequiredTool("reconcile", "balance_reconciler"),
                trace(List.of(),
                        List.of(ToolInvocationTrace.recovery("balance_reconciler", Map.of("startingBalance", 50000))),
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(result.systemOutcomePassed()).isTrue();
        assertThat(result.modelSelectionPassed()).isFalse();
        assertThat(result.recoveryTriggered()).isTrue();
        assertThat(result.failureReasons()).contains(EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED);
    }

    @Test
    void forbiddenToolFails() {
        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "forbidden-projection",
                EvaluationCategory.AGGREGATE,
                "How much did I spend on food?",
                IntentBucket.AGGREGATE,
                Set.of("spend_by_category"),
                Set.of("spend_by_category"),
                Set.of("savings_projector"),
                List.of(AllowedToolPath.exact("spend_by_category")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                Set.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        EvaluationRunResult result = score(evaluationCase,
                trace(List.of(ToolInvocationTrace.model("savings_projector", Map.of())),
                        List.of(),
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(result.systemOutcomePassed()).isFalse();
        assertThat(result.modelSelectionPassed()).isFalse();
        assertThat(result.failureReasons()).contains(EvaluationFailureReason.FORBIDDEN_TOOL_USED);
    }

    @Test
    void allowedAlternativeToolPathsPass() {
        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "explain-alternatives",
                EvaluationCategory.EXPLANATION,
                "Why did food spending increase?",
                IntentBucket.EXPLANATION,
                Set.of("spending_delta_explainer", "merchant_breakdown"),
                Set.of("spending_delta_explainer"),
                Set.of(),
                List.of(
                        AllowedToolPath.exact("spending_delta_explainer"),
                        AllowedToolPath.exact("spending_delta_explainer", "merchant_breakdown")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                Set.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        EvaluationRunResult singleTool = score(evaluationCase,
                trace(List.of(ToolInvocationTrace.model("spending_delta_explainer", Map.of())),
                        List.of(),
                        IntentBucket.EXPLANATION,
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));
        EvaluationRunResult twoTools = score(evaluationCase,
                trace(List.of(
                                ToolInvocationTrace.model("spending_delta_explainer", Map.of()),
                                ToolInvocationTrace.model("merchant_breakdown", Map.of())),
                        List.of(),
                        IntentBucket.EXPLANATION,
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(singleTool.systemOutcomePassed()).isTrue();
        assertThat(twoTools.systemOutcomePassed()).isTrue();
        assertThat(twoTools.modelSelectionPassed()).isTrue();
    }

    @Test
    void invalidArgumentsFailSystemButNotToolSelection() {
        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "invalid-args",
                EvaluationCategory.SUBSCRIPTION,
                "Detect subscriptions",
                IntentBucket.RECOMMENDATION,
                Set.of("subscription_detector"),
                Set.of("subscription_detector"),
                Set.of(),
                List.of(AllowedToolPath.exact("subscription_detector")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(ArgumentConstraint.numericRange(
                        "subscription_detector",
                        "limit",
                        BigDecimal.ONE,
                        BigDecimal.valueOf(50))),
                Set.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));

        EvaluationRunResult result = score(evaluationCase,
                trace(List.of(ToolInvocationTrace.invalidModelArguments(
                                "subscription_detector",
                                Map.of("limit", 51),
                                "INVALID_ARGUMENT_VALUE")),
                        List.of(),
                        IntentBucket.RECOMMENDATION,
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(result.modelSelectionPassed()).isTrue();
        assertThat(result.systemOutcomePassed()).isFalse();
        assertThat(result.failureReasons()).contains(EvaluationFailureReason.TOOL_ARGUMENT_INVALID);
    }

    @Test
    void numericValidatorBlockedHallucinationDoesNotCountAsEscapedNumberWhenExpected() {
        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "numeric-blocked",
                EvaluationCategory.LOOKUP,
                "Show Amazon transactions",
                IntentBucket.LOOKUP,
                Set.of("recent_transactions"),
                Set.of("recent_transactions"),
                Set.of(),
                List.of(AllowedToolPath.exact("recent_transactions")),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                Set.of(
                        ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED,
                        ExpectedOutcomeProperty.NUMERIC_FAILURE_SAFE_FALLBACK_ACCEPTABLE));

        EvaluationRunResult result = score(evaluationCase,
                trace(List.of(ToolInvocationTrace.model("recent_transactions", Map.of("merchant", "Amazon"))),
                        List.of(),
                        IntentBucket.LOOKUP,
                        false,
                        EvaluationValidationStatus.FAILED,
                        true,
                        true,
                        false));

        assertThat(result.systemOutcomePassed()).isTrue();
        assertThat(result.unsupportedNumberEscaped()).isFalse();
        assertThat(result.failureReasons()).doesNotContain(EvaluationFailureReason.UNSUPPORTED_NUMBER_ESCAPED);
    }

    @Test
    void unsupportedNumberEscapeIsHardFailure() {
        EvaluationRunResult result = score(
                caseWithRequiredTool("escaped", "recent_transactions"),
                trace(List.of(ToolInvocationTrace.model("recent_transactions", Map.of("merchant", "Amazon"))),
                        List.of(),
                        false,
                        EvaluationValidationStatus.FAILED,
                        true,
                        false,
                        true));

        assertThat(result.systemOutcomePassed()).isFalse();
        assertThat(result.failureReasons()).contains(EvaluationFailureReason.UNSUPPORTED_NUMBER_ESCAPED);
    }

    @Test
    void safeRefusalPassesWithoutToolEvidence() {
        EvaluationCase evaluationCase = EvaluationCase.singleTurn(
                "safe-refusal",
                EvaluationCategory.SAFE_REFUSAL,
                "Which stock should I buy?",
                IntentBucket.RECOMMENDATION,
                Set.of(),
                Set.of(),
                Set.of("savings_projector"),
                List.of(),
                RagExpectation.FORBIDDEN,
                false,
                true,
                List.of(),
                Set.of());

        EvaluationRunResult result = score(evaluationCase,
                trace(List.of(),
                        List.of(),
                        IntentBucket.RECOMMENDATION,
                        false,
                        EvaluationValidationStatus.PASSED,
                        false,
                        true,
                        false));

        assertThat(result.systemOutcomePassed()).isTrue();
        assertThat(result.safeRefusalExpected()).isTrue();
        assertThat(result.safeRefusalObserved()).isTrue();
    }

    @Test
    void ragRequiredAndForbiddenExpectationsAreDeterministic() {
        EvaluationRunResult requiredMissing = score(
                caseWithRag("rag-required", RagExpectation.REQUIRED),
                trace(List.of(ToolInvocationTrace.model("sum_by_transaction_ids", Map.of())),
                        List.of(),
                        false,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));
        EvaluationRunResult forbiddenUsed = score(
                caseWithRag("rag-forbidden", RagExpectation.FORBIDDEN),
                trace(List.of(ToolInvocationTrace.model("sum_by_transaction_ids", Map.of())),
                        List.of(),
                        true,
                        EvaluationValidationStatus.PASSED,
                        true,
                        false,
                        false));

        assertThat(requiredMissing.failureReasons()).contains(EvaluationFailureReason.RAG_REQUIRED_NOT_USED);
        assertThat(forbiddenUsed.failureReasons()).contains(EvaluationFailureReason.RAG_FORBIDDEN_BUT_USED);
    }

    private EvaluationRunResult score(EvaluationCase evaluationCase, EvaluationExecutionTrace trace) {
        return scorer.score("eval-test", evaluationCase, 1, trace);
    }

    private EvaluationCase caseWithRequiredTool(String id, String toolName) {
        return EvaluationCase.singleTurn(
                id,
                EvaluationCategory.AGGREGATE,
                "question",
                IntentBucket.AGGREGATE,
                Set.of(toolName),
                Set.of(toolName),
                Set.of(),
                List.of(AllowedToolPath.exact(toolName)),
                RagExpectation.OPTIONAL,
                true,
                false,
                List.of(),
                Set.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));
    }

    private EvaluationCase caseWithRag(String id, RagExpectation expectation) {
        return EvaluationCase.singleTurn(
                id,
                EvaluationCategory.SEMANTIC,
                "question",
                IntentBucket.AGGREGATE,
                Set.of("sum_by_transaction_ids"),
                Set.of("sum_by_transaction_ids"),
                Set.of(),
                List.of(AllowedToolPath.exact("sum_by_transaction_ids")),
                expectation,
                true,
                false,
                List.of(),
                Set.of(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED));
    }

    private EvaluationExecutionTrace trace(
            List<ToolInvocationTrace> modelCalls,
            List<ToolInvocationTrace> recoveryCalls,
            boolean ragUsed,
            EvaluationValidationStatus numericStatus,
            boolean financialEvidence,
            boolean safeRefusal,
            boolean unsupportedNumberEscaped
    ) {
        return EvaluationExecutionTrace.synthetic(
                IntentBucket.AGGREGATE,
                modelCalls,
                recoveryCalls,
                ragUsed,
                numericStatus,
                EvaluationValidationStatus.PASSED,
                EvaluationValidationStatus.PASSED,
                financialEvidence,
                safeRefusal,
                unsupportedNumberEscaped);
    }

    private EvaluationExecutionTrace trace(
            List<ToolInvocationTrace> modelCalls,
            List<ToolInvocationTrace> recoveryCalls,
            IntentBucket actualIntent,
            boolean ragUsed,
            EvaluationValidationStatus numericStatus,
            boolean financialEvidence,
            boolean safeRefusal,
            boolean unsupportedNumberEscaped
    ) {
        return EvaluationExecutionTrace.synthetic(
                actualIntent,
                modelCalls,
                recoveryCalls,
                ragUsed,
                numericStatus,
                EvaluationValidationStatus.PASSED,
                EvaluationValidationStatus.PASSED,
                financialEvidence,
                safeRefusal,
                unsupportedNumberEscaped);
    }
}
