package com.finsight.finsight_ai.ai.chat.domain;

import java.util.List;

public record ChatModelOutput(
        String textAnswer,
        List<ToolCallRequest>toolCalls,
        int promptTokens,
        int completionTokens
) {
    public ChatModelOutput {
        toolCalls = (toolCalls == null) ? List.of() : List.copyOf(toolCalls);
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }
}
