package com.finsight.finsight_ai.ai.chat.evaluation;

import com.finsight.finsight_ai.ai.chat.application.IntentBucket;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record EvaluationCase(
        String id,
        EvaluationCategory category,
        List<EvaluationTurn> turns,
        IntentBucket expectedIntent,
        Set<String> allowedTools,
        Set<String> requiredTools,
        Set<String> forbiddenTools,
        List<AllowedToolPath> allowedToolPaths,
        RagExpectation ragExpectation,
        boolean financialEvidenceRequired,
        boolean safeRefusalExpected,
        List<ArgumentConstraint> argumentConstraints,
        Set<ExpectedOutcomeProperty> expectedOutcomeProperties
) {
    public EvaluationCase {
        Objects.requireNonNull(id, "id is required");
        Objects.requireNonNull(category, "category is required");
        turns = turns == null ? List.of() : List.copyOf(turns);
        if (turns.isEmpty()) {
            throw new IllegalArgumentException("EvaluationCase requires at least one turn");
        }
        allowedTools = allowedTools == null ? Set.of() : Set.copyOf(allowedTools);
        requiredTools = requiredTools == null ? Set.of() : Set.copyOf(requiredTools);
        forbiddenTools = forbiddenTools == null ? Set.of() : Set.copyOf(forbiddenTools);
        allowedToolPaths = allowedToolPaths == null ? List.of() : List.copyOf(allowedToolPaths);
        ragExpectation = ragExpectation == null ? RagExpectation.OPTIONAL : ragExpectation;
        argumentConstraints = argumentConstraints == null ? List.of() : List.copyOf(argumentConstraints);
        expectedOutcomeProperties = expectedOutcomeProperties == null
                ? Set.of()
                : Set.copyOf(expectedOutcomeProperties);
    }

    public static EvaluationCase singleTurn(
            String id,
            EvaluationCategory category,
            String question,
            IntentBucket expectedIntent,
            Set<String> allowedTools,
            Set<String> requiredTools,
            Set<String> forbiddenTools,
            List<AllowedToolPath> allowedToolPaths,
            RagExpectation ragExpectation,
            boolean financialEvidenceRequired,
            boolean safeRefusalExpected,
            List<ArgumentConstraint> argumentConstraints,
            Set<ExpectedOutcomeProperty> expectedOutcomeProperties
    ) {
        return new EvaluationCase(
                id,
                category,
                List.of(new EvaluationTurn(id + "-turn-1", question)),
                expectedIntent,
                allowedTools,
                requiredTools,
                forbiddenTools,
                allowedToolPaths,
                ragExpectation,
                financialEvidenceRequired,
                safeRefusalExpected,
                argumentConstraints,
                expectedOutcomeProperties);
    }

    boolean expectsModelToolSelection() {
        return !requiredTools.isEmpty()
                || !allowedToolPaths.isEmpty()
                || expectedOutcomeProperties.contains(ExpectedOutcomeProperty.MODEL_TOOL_SELECTION_EXPECTED);
    }

    boolean numericFailureSafeFallbackAcceptable() {
        return expectedOutcomeProperties.contains(ExpectedOutcomeProperty.NUMERIC_FAILURE_SAFE_FALLBACK_ACCEPTABLE)
                || expectedOutcomeProperties.contains(ExpectedOutcomeProperty.VALIDATOR_BLOCK_EXPECTED);
    }
}
