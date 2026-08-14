package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record TopMerchantsResult(
        UUID userId,
        LocalDate startDate,
        LocalDate endDate,
        String currency,
        List<MerchantRankItem> merchants
) {
    public record MerchantRankItem(
            int rank,
            String merchantName,
            BigDecimal totalSpend,
            Long transactionCount
    ) {}
}
