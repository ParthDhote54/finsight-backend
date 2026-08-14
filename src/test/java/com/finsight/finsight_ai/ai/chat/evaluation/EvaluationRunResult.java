package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.application.IntentBucket;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record EvaluationRunResult(
        String evaluationRunId,
        String caseId,
        int attemptNumber,
        UUID conversationId,
        String requestCorrelationId,
        String modelId,
        String promptVersion,
        IntentBucket actualIntent,
        List<ToolInvocationTrace> modelToolCalls,
        List<ToolInvocationTrace> recoveryToolCalls,
        boolean ragUsed,
        EvaluationValidationStatus numericValidation,
        EvaluationValidationStatus citationValidation,
        EvaluationValidationStatus semanticValidation,
        boolean correctionTriggered,
        boolean financialEvidenceRequired,
        boolean financialEvidencePresent,
        boolean safeRefusalExpected,
        boolean safeRefusalObserved,
        boolean unsupportedNumberEscaped,
        boolean recoveryTriggered,
        boolean modelSelectionEligible,
        boolean systemOutcomePassed,
        boolean modelSelectionPassed,
        Set<EvaluationFailureReason> failureReasons,
        Long totalLatencyMs,
        EvaluationTokenUsage tokenUsage
) {
    public EvaluationRunResult {
        modelToolCalls = modelToolCalls == null ? List.of() : List.copyOf(modelToolCalls);
        recoveryToolCalls = recoveryToolCalls == null ? List.of() : List.copyOf(recoveryToolCalls);
        failureReasons = failureReasons == null ? Set.of() : Set.copyOf(failureReasons);
    }

    List<ToolInvocationTrace> allToolCalls() {
        java.util.ArrayList<ToolInvocationTrace> all = new java.util.ArrayList<>(modelToolCalls);
        all.addAll(recoveryToolCalls);
        return List.copyOf(all);
    }
}
