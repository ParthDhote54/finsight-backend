package com.finsight.finsight_ai.ai.chat.domain;

import java.util.List;

/*
    *INPUT payload send across ChatModelPort to the LLM.
 */
public record ChatModelInput(
        String systemPrompt,
        List<ChatTurn> history,
        String userMessage,
        List<ToolSpec>availableTools
) {
    public ChatModelInput {
        history = (history == null) ? List.of() : List.copyOf(history);
        availableTools = (availableTools == null) ? List.of() : List.copyOf(availableTools);
    }
}
