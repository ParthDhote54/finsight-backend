package com.finsight.finsight_ai.ai.chat.domain;
/*
*Operational metadata for usage tracking, cost transparency and execution path observability.
*
 */
public record TokenUsageMetaData(
        int promptTokens,
        int completionTokens,
        int totalTokens,
        boolean usedTool,
        boolean usedRag,
        boolean verifiedFinancialCalculation
) {
    public TokenUsageMetaData(int promptTokens, int completionTokens, int totalTokens, boolean usedTool, boolean usedRag) {
        this(promptTokens, completionTokens, totalTokens, usedTool, usedRag, false);
    }
}

