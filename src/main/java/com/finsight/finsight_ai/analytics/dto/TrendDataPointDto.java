package com.finsight.finsight_ai.analytics.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record TrendDataPointDto(LocalDate periodStart,
                                BigDecimal totalIncome,
                                BigDecimal totalExpense,
                                BigDecimal net) {
}
