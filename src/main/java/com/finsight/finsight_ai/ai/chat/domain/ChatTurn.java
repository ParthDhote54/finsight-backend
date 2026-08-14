package com.finsight.finsight_ai.ai.chat.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ChatTurn(
        Role role,
        String content,
        List<ToolCallRequest> toolCalls,
        List<ToolCallResult> toolResults,
        Instant timestamp
) {
    public ChatTurn(Role role, String content) {
        this(role, content, List.of(), List.of(), Instant.now());
    }

    public static ChatTurn assistant(String content, List<ToolCallRequest> toolCalls) {
        return new ChatTurn(Role.ASSISTANT, content == null ? "" : content,
                toolCalls, List.of(), Instant.now());
    }

    public static ChatTurn toolResults(List<ToolCallResult> toolResults) {
        return new ChatTurn(Role.TOOL, "", List.of(), toolResults, Instant.now());
    }

    public ChatTurn {
        Objects.requireNonNull(role, "Role cannot be null");
        Objects.requireNonNull(content, "Content cannot be null");
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        toolResults = toolResults == null ? List.of() : List.copyOf(toolResults);
    }
}
