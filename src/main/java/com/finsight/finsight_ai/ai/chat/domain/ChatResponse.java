package com.finsight.finsight_ai.ai.chat.domain;

import java.util.List;
import java.util.UUID;

public record ChatResponse(
        UUID conversationId,
        String answer,
        List<TransactionCitation>citations,
        TokenUsageMetaData metaData
) {
    /*
    compact constructor to enforce null-safety invariants on collections.
     */
    public ChatResponse {
        citations = (citations == null) ? List.of() : List.copyOf(citations);
    }
}
