package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.application.IntentBucket;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record EvaluationExecutionTrace(
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
        boolean financialEvidencePresent,
        boolean safeRefusalObserved,
        boolean unsupportedNumberEscaped,
        Long totalLatencyMs,
        EvaluationTokenUsage tokenUsage,
        String executionError
) {
    public EvaluationExecutionTrace {
        modelToolCalls = modelToolCalls == null ? List.of() : List.copyOf(modelToolCalls);
        recoveryToolCalls = recoveryToolCalls == null ? List.of() : List.copyOf(recoveryToolCalls);
        numericValidation = numericValidation == null ? EvaluationValidationStatus.NOT_RUN : numericValidation;
        citationValidation = citationValidation == null ? EvaluationValidationStatus.NOT_RUN : citationValidation;
        semanticValidation = semanticValidation == null ? EvaluationValidationStatus.NOT_RUN : semanticValidation;
    }

    public static EvaluationExecutionTrace synthetic(
            IntentBucket actualIntent,
            List<ToolInvocationTrace> modelToolCalls,
            List<ToolInvocationTrace> recoveryToolCalls,
            boolean ragUsed,
            EvaluationValidationStatus numericValidation,
            EvaluationValidationStatus citationValidation,
            EvaluationValidationStatus semanticValidation,
            boolean financialEvidencePresent,
            boolean safeRefusalObserved,
            boolean unsupportedNumberEscaped
    ) {
        return new EvaluationExecutionTrace(
                UUID.randomUUID(),
                "synthetic-" + UUID.randomUUID(),
                "synthetic-model",
                null,
                Objects.requireNonNull(actualIntent, "actualIntent is required"),
                modelToolCalls,
                recoveryToolCalls,
                ragUsed,
                numericValidation,
                citationValidation,
                semanticValidation,
                false,
                financialEvidencePresent,
                safeRefusalObserved,
                unsupportedNumberEscaped,
                10L,
                new EvaluationTokenUsage(1, 1, 2),
                null);
    }

    boolean recoveryTriggered() {
        return !recoveryToolCalls.isEmpty();
    }

    List<ToolInvocationTrace> allToolCalls() {
        java.util.ArrayList<ToolInvocationTrace> all = new java.util.ArrayList<>(modelToolCalls);
        all.addAll(recoveryToolCalls);
        return List.copyOf(all);
    }
}
