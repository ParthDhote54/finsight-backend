package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Result of summing transactions by an explicit ID set.
 * This is the Tier-3 semantic-fallback safety valve: the IDs are determined
 * by vector similarity, but the arithmetic is exact SQL over concrete rows.
 */
public record SumByIdsResult(
        UUID userId,
        BigDecimal totalAmount,
        String currency,
        long transactionCount,
        List<UUID> matchedIds,
        List<UUID> unmatchedIds
) {}
