package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record MerchantBreakdownResult(
        UUID userId,
        String categoryOrGroup,
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        List<MerchantItem> items
) {
    public record MerchantItem(
            String merchantName,
            BigDecimal totalAmount,
            Long transactionCount,
            BigDecimal percentageOfTotal
    ) {}
}
