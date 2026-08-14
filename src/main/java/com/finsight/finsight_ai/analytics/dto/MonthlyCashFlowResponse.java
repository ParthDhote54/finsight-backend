package com.finsight.finsight_ai.analytics.dto;

import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record MonthlyCashFlowResponse(
        LocalDate periodStart,
        LocalDate periodEnd,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netCashFlow,
        String currency
) {
}
