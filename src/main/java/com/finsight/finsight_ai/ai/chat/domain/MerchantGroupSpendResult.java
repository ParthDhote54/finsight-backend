package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record MerchantGroupSpendResult(
        UUID userId,
        String merchantGroup,
        BigDecimal totalAmount,
        Long transactionCount,
        String currency,
        LocalDate startDate,
        LocalDate endDate
) {}
