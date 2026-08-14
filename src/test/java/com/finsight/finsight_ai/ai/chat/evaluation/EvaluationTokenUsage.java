package com.finsight.finsight_ai.ai.chat.evaluation;

public record EvaluationTokenUsage(
        Integer promptTokens,
        Integer completionTokens,
        Integer totalTokens
) {
}
