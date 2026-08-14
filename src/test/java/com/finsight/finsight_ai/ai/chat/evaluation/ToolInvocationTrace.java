package com.finsight.finsight_ai.ai.chat.evaluation;

import java.util.Map;
import java.util.Objects;

public record ToolInvocationTrace(
        String toolName,
        ToolInvocationSource source,
        Map<String, Object> arguments,
        boolean argumentsValid,
        EvaluationToolStatus status,
        String errorCode
) {
    public ToolInvocationTrace {
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(source, "source is required");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        status = status == null ? EvaluationToolStatus.SUCCESS : status;
    }

    public static ToolInvocationTrace model(String toolName, Map<String, Object> arguments) {
        return new ToolInvocationTrace(
                toolName,
                ToolInvocationSource.MODEL,
                arguments,
                true,
                EvaluationToolStatus.SUCCESS,
                null);
    }

    public static ToolInvocationTrace recovery(String toolName, Map<String, Object> arguments) {
        return new ToolInvocationTrace(
                toolName,
                ToolInvocationSource.DETERMINISTIC_RECOVERY,
                arguments,
                true,
                EvaluationToolStatus.SUCCESS,
                null);
    }

    public static ToolInvocationTrace invalidModelArguments(
            String toolName,
            Map<String, Object> arguments,
            String errorCode
    ) {
        return new ToolInvocationTrace(
                toolName,
                ToolInvocationSource.MODEL,
                arguments,
                false,
                EvaluationToolStatus.MODEL_CORRECTABLE_ERROR,
                errorCode);
    }
}
