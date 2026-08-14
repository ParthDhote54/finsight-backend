package com.finsight.finsight_ai.ai.chat.domain;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/*
represents an explicit function execution request returned by the LLM.
 */
public record ToolCallRequest(
        String callId,
        String toolName,
        Map<String, Object> arguments,
        String rawArguments,
        String argumentParsingError
) {
    public ToolCallRequest(String callId, String toolName, Map<String, Object> arguments) {
        this(callId, toolName, arguments, null, null);
    }

    public ToolCallRequest {
        if (callId == null || callId.isBlank()) {
            callId = "generated-" + UUID.randomUUID();
        }
        Objects.requireNonNull(toolName, "Tool name is required");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public boolean hasValidArgumentsJson() {
        return argumentParsingError == null;
    }
}


