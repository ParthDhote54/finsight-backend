package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SubscriptionDetectionResult(
        UUID userId,
        String currency,
        List<SubscriptionItem> subscriptions
) {
    public record SubscriptionItem(
            String merchant,
            BigDecimal averageAmount,
            String frequency,
            int occurrenceCount,
            LocalDate firstOccurrence,
            LocalDate lastOccurrence,
            String classification,
            List<UUID> transactionIds
    ) {}
}
