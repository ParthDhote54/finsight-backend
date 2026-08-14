package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LowestTransactionResult(
        UUID userId,
        UUID transactionId,
        String description,
        BigDecimal amount,
        String categoryName,
        String currency,
        LocalDate transactionDate,
        boolean hasData
) {
    public static LowestTransactionResult empty(UUID userId, LocalDate startDate, LocalDate endDate) {
        return new LowestTransactionResult(userId, null, null, BigDecimal.ZERO, null, "INR", null, false);
    }
}
