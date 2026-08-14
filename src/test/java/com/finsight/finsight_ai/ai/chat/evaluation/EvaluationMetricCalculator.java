package com.finsight.finsight_ai.ai.chat.evaluation;

import java.util.List;
import java.util.Objects;

public class EvaluationMetricCalculator {

    public EvaluationMetrics calculate(List<EvaluationRunResult> results) {
        List<EvaluationRunResult> safeResults = results == null ? List.of() : List.copyOf(results);
        int casesExecuted = safeResults.size();
        int systemSuccess = (int) safeResults.stream().filter(EvaluationRunResult::systemOutcomePassed).count();
        int modelEligible = (int) safeResults.stream().filter(EvaluationRunResult::modelSelectionEligible).count();
        int modelSuccess = (int) safeResults.stream()
                .filter(EvaluationRunResult::modelSelectionEligible)
                .filter(EvaluationRunResult::modelSelectionPassed)
                .count();
        int recoveryTriggered = (int) safeResults.stream().filter(EvaluationRunResult::recoveryTriggered).count();

        List<ToolInvocationTrace> toolCalls = safeResults.stream()
                .flatMap(result -> result.allToolCalls().stream())
                .toList();
        int toolArgumentTotal = toolCalls.size();
        int toolArgumentValid = (int) toolCalls.stream().filter(ToolInvocationTrace::argumentsValid).count();

        int financialEvidenceRequired = (int) safeResults.stream()
                .filter(EvaluationRunResult::financialEvidenceRequired)
                .count();
        int financialEvidenceSatisfied = (int) safeResults.stream()
                .filter(EvaluationRunResult::financialEvidenceRequired)
                .filter(EvaluationRunResult::financialEvidencePresent)
                .count();

        int safeRefusalCases = (int) safeResults.stream()
                .filter(EvaluationRunResult::safeRefusalExpected)
                .count();
        int safeRefusalSuccess = (int) safeResults.stream()
                .filter(EvaluationRunResult::safeRefusalExpected)
                .filter(EvaluationRunResult::systemOutcomePassed)
                .count();

        int promptTokens = safeResults.stream()
                .map(EvaluationRunResult::tokenUsage)
                .filter(Objects::nonNull)
                .map(EvaluationTokenUsage::promptTokens)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int completionTokens = safeResults.stream()
                .map(EvaluationRunResult::tokenUsage)
                .filter(Objects::nonNull)
                .map(EvaluationTokenUsage::completionTokens)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        int totalTokens = safeResults.stream()
                .map(EvaluationRunResult::tokenUsage)
                .filter(Objects::nonNull)
                .map(EvaluationTokenUsage::totalTokens)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
        long latencyCount = safeResults.stream().map(EvaluationRunResult::totalLatencyMs).filter(Objects::nonNull).count();
        long latencySum = safeResults.stream()
                .map(EvaluationRunResult::totalLatencyMs)
                .filter(Objects::nonNull)
                .mapToLong(Long::longValue)
                .sum();

        return new EvaluationMetrics(
                casesExecuted,
                systemSuccess,
                EvaluationMetrics.rate(systemSuccess, casesExecuted),
                modelEligible,
                modelSuccess,
                EvaluationMetrics.rate(modelSuccess, modelEligible),
                recoveryTriggered,
                EvaluationMetrics.rate(recoveryTriggered, modelEligible),
                toolArgumentTotal,
                toolArgumentValid,
                EvaluationMetrics.rate(toolArgumentValid, toolArgumentTotal),
                financialEvidenceRequired,
                financialEvidenceSatisfied,
                safeRefusalCases,
                safeRefusalSuccess,
                EvaluationMetrics.rate(safeRefusalSuccess, safeRefusalCases),
                countValidation(safeResults, EvaluationValidationStatus.PASSED, ValidationKind.NUMERIC),
                countValidation(safeResults, EvaluationValidationStatus.FAILED, ValidationKind.NUMERIC),
                countValidation(safeResults, EvaluationValidationStatus.PASSED, ValidationKind.CITATION),
                countValidation(safeResults, EvaluationValidationStatus.FAILED, ValidationKind.CITATION),
                countValidation(safeResults, EvaluationValidationStatus.PASSED, ValidationKind.SEMANTIC),
                countValidation(safeResults, EvaluationValidationStatus.FAILED, ValidationKind.SEMANTIC),
                (int) safeResults.stream().filter(EvaluationRunResult::unsupportedNumberEscaped).count(),
                (int) safeResults.stream().filter(EvaluationRunResult::correctionTriggered).count(),
                promptTokens == 0 ? null : promptTokens,
                completionTokens == 0 ? null : completionTokens,
                totalTokens == 0 ? null : totalTokens,
                latencyCount == 0 ? null : (double) latencySum / latencyCount);
    }

    private int countValidation(
            List<EvaluationRunResult> results,
            EvaluationValidationStatus status,
            ValidationKind kind
    ) {
        return (int) results.stream()
                .filter(result -> switch (kind) {
                    case NUMERIC -> result.numericValidation() == status;
                    case CITATION -> result.citationValidation() == status;
                    case SEMANTIC -> result.semanticValidation() == status;
                })
                .count();
    }

    private enum ValidationKind {
        NUMERIC,
        CITATION,
        SEMANTIC
    }
}
