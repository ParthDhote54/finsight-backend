package com.finsight.finsight_ai.ai.chat.evaluation;

public record EvaluationMetrics(
        int casesExecuted,
        int systemSuccessCount,
        Double systemSuccessRate,
        int modelToolSelectionEligibleCases,
        int modelToolSelectionSuccessCount,
        Double modelToolSelectionSuccessRate,
        int recoveryTriggeredCount,
        Double recoveryRate,
        int toolArgumentTotal,
        int toolArgumentValidCount,
        Double toolArgumentValidityRate,
        int financialEvidenceRequiredCases,
        int financialEvidenceSatisfiedCases,
        int safeRefusalCases,
        int safeRefusalSuccessCount,
        Double safeRefusalRate,
        int numericValidationPassCount,
        int numericValidationFailureCount,
        int citationValidationPassCount,
        int citationValidationFailureCount,
        int semanticValidationPassCount,
        int semanticValidationFailureCount,
        int unsupportedNumberEscapeCount,
        int correctionTriggeredCount,
        Integer totalPromptTokens,
        Integer totalCompletionTokens,
        Integer totalTokens,
        Double averageLatencyMs
) {
    static Double rate(int numerator, int denominator) {
        return denominator == 0 ? null : (double) numerator / denominator;
    }
}
