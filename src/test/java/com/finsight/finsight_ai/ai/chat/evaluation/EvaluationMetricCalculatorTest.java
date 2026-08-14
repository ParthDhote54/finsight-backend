package com.finsight.finsight_ai.ai.chat.evaluation;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetricCalculatorTest {

    private final EvaluationMetricCalculator calculator = new EvaluationMetricCalculator();

    @Test
    void metricDenominatorsUseApplicableCaseCounts() {
        List<EvaluationRunResult> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            boolean modelEligible = i < 6;
            boolean modelPassed = i < 4;
            boolean recovered = i >= 4 && i < 6;
            boolean safeRefusalExpected = i >= 6 && i < 9;
            boolean citationPass = i < 4;
            results.add(result(
                    "case-" + i,
                    true,
                    modelEligible,
                    modelPassed,
                    recovered,
                    true,
                    safeRefusalExpected,
                    safeRefusalExpected,
                    EvaluationValidationStatus.PASSED,
                    i < 5 ? (citationPass ? EvaluationValidationStatus.PASSED : EvaluationValidationStatus.FAILED)
                            : EvaluationValidationStatus.NOT_RUN,
                    EvaluationValidationStatus.PASSED));
        }

        EvaluationMetrics metrics = calculator.calculate(results);

        assertThat(metrics.casesExecuted()).isEqualTo(10);
        assertThat(metrics.modelToolSelectionEligibleCases()).isEqualTo(6);
        assertThat(metrics.modelToolSelectionSuccessCount()).isEqualTo(4);
        assertThat(metrics.modelToolSelectionSuccessRate()).isEqualTo(4.0 / 6.0);
        assertThat(metrics.recoveryTriggeredCount()).isEqualTo(2);
        assertThat(metrics.recoveryRate()).isEqualTo(2.0 / 6.0);
        assertThat(metrics.safeRefusalCases()).isEqualTo(3);
        assertThat(metrics.safeRefusalSuccessCount()).isEqualTo(3);
        assertThat(metrics.safeRefusalRate()).isEqualTo(1.0);
        assertThat(metrics.citationValidationPassCount()).isEqualTo(4);
        assertThat(metrics.citationValidationFailureCount()).isEqualTo(1);
    }

    @Test
    void correlationFailureDoesNotCreateFalseSuccessMetrics() {
        EvaluationRunResult correlationFailure = result(
                "audit-failure",
                false,
                true,
                false,
                false,
                true,
                false,
                false,
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                EvaluationValidationStatus.NOT_RUN,
                Set.of(EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS));

        EvaluationMetrics metrics = calculator.calculate(List.of(correlationFailure));

        assertThat(metrics.systemSuccessCount()).isZero();
        assertThat(metrics.modelToolSelectionSuccessCount()).isZero();
        assertThat(metrics.recoveryTriggeredCount()).isZero();
        assertThat(metrics.toolArgumentValidCount()).isZero();
        assertThat(metrics.financialEvidenceSatisfiedCases()).isZero();
    }

    private EvaluationRunResult result(
            String caseId,
            boolean systemPassed,
            boolean modelEligible,
            boolean modelPassed,
            boolean recovered,
            boolean financialEvidenceRequired,
            boolean safeRefusalExpected,
            boolean safeRefusalObserved,
            EvaluationValidationStatus numeric,
            EvaluationValidationStatus citation,
            EvaluationValidationStatus semantic
    ) {
        List<ToolInvocationTrace> modelCalls = modelEligible && modelPassed
                ? List.of(ToolInvocationTrace.model("tool", java.util.Map.of()))
                : List.of();
        List<ToolInvocationTrace> recoveryCalls = recovered
                ? List.of(ToolInvocationTrace.recovery("tool", java.util.Map.of()))
                : List.of();
        return new EvaluationRunResult(
                "eval",
                caseId,
                1,
                UUID.randomUUID(),
                "audit-" + caseId,
                "model",
                null,
                null,
                modelCalls,
                recoveryCalls,
                false,
                numeric,
                citation,
                semantic,
                false,
                financialEvidenceRequired,
                financialEvidenceRequired,
                safeRefusalExpected,
                safeRefusalObserved,
                false,
                recovered,
                modelEligible,
                systemPassed,
                modelPassed,
                Set.of(),
                100L,
                new EvaluationTokenUsage(10, 5, 15));
    }

    private EvaluationRunResult result(
            String caseId,
            boolean systemPassed,
            boolean modelEligible,
            boolean modelPassed,
            boolean recovered,
            boolean financialEvidenceRequired,
            boolean safeRefusalExpected,
            boolean safeRefusalObserved,
            EvaluationValidationStatus numeric,
            EvaluationValidationStatus citation,
            EvaluationValidationStatus semantic,
            Set<EvaluationFailureReason> failureReasons
    ) {
        return new EvaluationRunResult(
                "eval",
                caseId,
                1,
                UUID.randomUUID(),
                null,
                "model",
                null,
                null,
                List.of(),
                recovered ? List.of(ToolInvocationTrace.recovery("tool", java.util.Map.of())) : List.of(),
                false,
                numeric,
                citation,
                semantic,
                false,
                financialEvidenceRequired,
                false,
                safeRefusalExpected,
                safeRefusalObserved,
                false,
                recovered,
                modelEligible,
                systemPassed,
                modelPassed,
                failureReasons,
                100L,
                null);
    }
}
