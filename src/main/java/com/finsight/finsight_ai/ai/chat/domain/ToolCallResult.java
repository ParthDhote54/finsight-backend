package com.finsight.finsight_ai.ai.chat.domain;

import java.util.Objects;

public record ToolCallResult(
        String callId,
        String toolName,
        String responseJson
) {
    public ToolCallResult {
        Objects.requireNonNull(callId, "Tool call ID is required");
        Objects.requireNonNull(toolName, "Tool name is required");
        Objects.requireNonNull(responseJson, "Tool response JSON is required");
    }
}
