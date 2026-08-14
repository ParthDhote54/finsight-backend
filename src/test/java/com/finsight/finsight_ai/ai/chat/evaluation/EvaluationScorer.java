package com.finsight.finsight_ai.ai.chat.evaluation;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class EvaluationScorer {

    public EvaluationRunResult score(
            String evaluationRunId,
            EvaluationCase evaluationCase,
            int attemptNumber,
            EvaluationExecutionTrace trace
    ) {
        EnumSet<EvaluationFailureReason> reasons = EnumSet.noneOf(EvaluationFailureReason.class);
        boolean modelSelectionEligible = evaluationCase.expectsModelToolSelection();
        List<String> modelToolNames = trace.modelToolCalls().stream()
                .map(ToolInvocationTrace::toolName)
                .toList();
        List<String> allToolNames = trace.allToolCalls().stream()
                .map(ToolInvocationTrace::toolName)
                .toList();

        if (trace.executionError() != null) {
            if (EvaluationFailureReason.AUDIT_NOT_FOUND.name().equals(trace.executionError())) {
                reasons.add(EvaluationFailureReason.AUDIT_NOT_FOUND);
            } else if (EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS.name().equals(trace.executionError())) {
                reasons.add(EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS);
            } else {
                reasons.add(EvaluationFailureReason.EXECUTION_ERROR);
            }
        }
        if (evaluationCase.expectedIntent() != null && evaluationCase.expectedIntent() != trace.actualIntent()) {
            reasons.add(EvaluationFailureReason.INTENT_MISMATCH);
        }

        addToolFailures(evaluationCase, trace, reasons, modelToolNames, allToolNames);
        addArgumentFailures(evaluationCase, trace, reasons);
        addRagFailures(evaluationCase, trace, reasons);
        addEvidenceAndValidationFailures(evaluationCase, trace, reasons);

        if (evaluationCase.safeRefusalExpected() && !trace.safeRefusalObserved()) {
            reasons.add(EvaluationFailureReason.SAFE_REFUSAL_EXPECTED_NOT_OBSERVED);
        }
        if (trace.unsupportedNumberEscaped()) {
            reasons.add(EvaluationFailureReason.UNSUPPORTED_NUMBER_ESCAPED);
        }

        boolean modelSelectionPassed = modelSelectionPassed(modelSelectionEligible, reasons);
        boolean systemOutcomePassed = systemOutcomePassed(reasons, evaluationCase);

        return new EvaluationRunResult(
                evaluationRunId,
                evaluationCase.id(),
                attemptNumber,
                trace.conversationId(),
                trace.requestCorrelationId(),
                trace.modelId(),
                trace.promptVersion(),
                trace.actualIntent(),
                trace.modelToolCalls(),
                trace.recoveryToolCalls(),
                trace.ragUsed(),
                trace.numericValidation(),
                trace.citationValidation(),
                trace.semanticValidation(),
                trace.correctionTriggered(),
                evaluationCase.financialEvidenceRequired(),
                trace.financialEvidencePresent(),
                evaluationCase.safeRefusalExpected(),
                trace.safeRefusalObserved(),
                trace.unsupportedNumberEscaped(),
                trace.recoveryTriggered(),
                modelSelectionEligible,
                systemOutcomePassed,
                modelSelectionPassed,
                reasons,
                trace.totalLatencyMs(),
                trace.tokenUsage());
    }

    private void addToolFailures(
            EvaluationCase evaluationCase,
            EvaluationExecutionTrace trace,
            Set<EvaluationFailureReason> reasons,
            List<String> modelToolNames,
            List<String> allToolNames
    ) {
        for (String forbidden : evaluationCase.forbiddenTools()) {
            if (allToolNames.contains(forbidden)) {
                reasons.add(EvaluationFailureReason.FORBIDDEN_TOOL_USED);
            }
        }

        if (!evaluationCase.allowedTools().isEmpty()) {
            boolean outsideAllowed = allToolNames.stream()
                    .anyMatch(tool -> !evaluationCase.allowedTools().contains(tool));
            if (outsideAllowed) {
                reasons.add(EvaluationFailureReason.INVALID_TOOL_PATH);
            }
        }

        for (String required : evaluationCase.requiredTools()) {
            if (!modelToolNames.contains(required)) {
                if (trace.recoveryToolCalls().stream().anyMatch(call -> required.equals(call.toolName()))) {
                    reasons.add(EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED);
                } else {
                    reasons.add(EvaluationFailureReason.REQUIRED_TOOL_MISSING);
                    reasons.add(EvaluationFailureReason.MODEL_TOOL_MISS_UNRECOVERED);
                }
            }
        }

        if (!evaluationCase.allowedToolPaths().isEmpty()
                && evaluationCase.allowedToolPaths().stream().noneMatch(path -> path.matches(modelToolNames))) {
            if (trace.recoveryTriggered() && evaluationCase.allowedToolPaths().stream()
                    .anyMatch(path -> path.matches(trace.recoveryToolCalls().stream()
                            .map(ToolInvocationTrace::toolName)
                            .toList()))) {
                reasons.add(EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED);
            } else {
                reasons.add(EvaluationFailureReason.INVALID_TOOL_PATH);
            }
        }

        if (!evaluationCase.expectsModelToolSelection() && trace.recoveryTriggered()) {
            reasons.add(EvaluationFailureReason.UNEXPECTED_RECOVERY);
        }
    }

    private void addArgumentFailures(
            EvaluationCase evaluationCase,
            EvaluationExecutionTrace trace,
            Set<EvaluationFailureReason> reasons
    ) {
        List<ToolInvocationTrace> allCalls = trace.allToolCalls();
        if (allCalls.stream().anyMatch(call -> !call.argumentsValid())) {
            reasons.add(EvaluationFailureReason.TOOL_ARGUMENT_INVALID);
        }
        for (ArgumentConstraint constraint : evaluationCase.argumentConstraints()) {
            boolean relevantToolCalled = allCalls.stream()
                    .anyMatch(call -> constraint.toolName().equals(call.toolName()));
            if (relevantToolCalled && allCalls.stream().anyMatch(call -> !constraint.satisfiedBy(call))) {
                reasons.add(EvaluationFailureReason.TOOL_ARGUMENT_INVALID);
            }
        }
    }

    private void addRagFailures(
            EvaluationCase evaluationCase,
            EvaluationExecutionTrace trace,
            Set<EvaluationFailureReason> reasons
    ) {
        if (evaluationCase.ragExpectation() == RagExpectation.REQUIRED && !trace.ragUsed()) {
            reasons.add(EvaluationFailureReason.RAG_REQUIRED_NOT_USED);
        }
        if (evaluationCase.ragExpectation() == RagExpectation.FORBIDDEN && trace.ragUsed()) {
            reasons.add(EvaluationFailureReason.RAG_FORBIDDEN_BUT_USED);
        }
    }

    private void addEvidenceAndValidationFailures(
            EvaluationCase evaluationCase,
            EvaluationExecutionTrace trace,
            Set<EvaluationFailureReason> reasons
    ) {
        if (evaluationCase.financialEvidenceRequired() && !trace.financialEvidencePresent()) {
            reasons.add(EvaluationFailureReason.FINANCIAL_EVIDENCE_MISSING);
        }
        if (trace.numericValidation() == EvaluationValidationStatus.FAILED
                && !evaluationCase.numericFailureSafeFallbackAcceptable()) {
            reasons.add(EvaluationFailureReason.NUMERIC_VALIDATION_FAILED);
        }
        if (trace.citationValidation() == EvaluationValidationStatus.FAILED) {
            reasons.add(EvaluationFailureReason.CITATION_VALIDATION_FAILED);
        }
        if (trace.semanticValidation() == EvaluationValidationStatus.FAILED) {
            reasons.add(EvaluationFailureReason.SEMANTIC_VALIDATION_FAILED);
        }
    }

    private boolean modelSelectionPassed(boolean modelSelectionEligible, Set<EvaluationFailureReason> reasons) {
        if (!modelSelectionEligible) {
            return true;
        }
        return disjoint(reasons, EnumSet.of(
                EvaluationFailureReason.REQUIRED_TOOL_MISSING,
                EvaluationFailureReason.FORBIDDEN_TOOL_USED,
                EvaluationFailureReason.INVALID_TOOL_PATH,
                EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED,
                EvaluationFailureReason.MODEL_TOOL_MISS_UNRECOVERED,
                EvaluationFailureReason.AUDIT_NOT_FOUND,
                EvaluationFailureReason.AUDIT_CORRELATION_AMBIGUOUS));
    }

    private boolean systemOutcomePassed(Set<EvaluationFailureReason> reasons, EvaluationCase evaluationCase) {
        EnumSet<EvaluationFailureReason> blocking = EnumSet.noneOf(EvaluationFailureReason.class);
        blocking.addAll(reasons);
        blocking.remove(EvaluationFailureReason.MODEL_TOOL_MISS_RECOVERED);
        if (evaluationCase.numericFailureSafeFallbackAcceptable()) {
            blocking.remove(EvaluationFailureReason.NUMERIC_VALIDATION_FAILED);
        }
        return blocking.isEmpty();
    }

    private boolean disjoint(Set<EvaluationFailureReason> reasons, EnumSet<EvaluationFailureReason> target) {
        return new ArrayList<>(reasons).stream().noneMatch(target::contains);
    }
}
