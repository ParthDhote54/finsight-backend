package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record SavingsProjectionResult(
        UUID userId,
        String categoryOrGroup,
        String currency,
        BigDecimal baselineMonthlySpend,
        BigDecimal reductionPercentage,
        BigDecimal proposedMonthlyReduction,
        BigDecimal projectedMonthlySavings,
        int timeHorizonMonths,
        BigDecimal totalHorizonSavings,
        String disclaimer
) {}
