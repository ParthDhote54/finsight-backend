package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CategorySpendResult(
        UUID userId,
        String category,
        BigDecimal totalAmount,
        Long transactionCount,
        String currency,
        LocalDate startDate,
        LocalDate endDate,
        String largestCategory,
        BigDecimal largestCategoryAmount
){
    public CategorySpendResult(UUID userId, String category, BigDecimal totalAmount, Long transactionCount, String currency, LocalDate startDate, LocalDate endDate) {
        this(userId, category, totalAmount, transactionCount, currency, startDate, endDate, null, null);
    }
}
