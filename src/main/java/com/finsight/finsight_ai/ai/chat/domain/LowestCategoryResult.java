package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record LowestCategoryResult(
        UUID userId,
        String categoryName,
        BigDecimal totalAmount,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        boolean hasData
) {
    public static LowestCategoryResult empty(UUID userId, LocalDate startDate, LocalDate endDate) {
        return new LowestCategoryResult(userId, null, BigDecimal.ZERO, "INR", startDate, endDate, false);
    }
}
