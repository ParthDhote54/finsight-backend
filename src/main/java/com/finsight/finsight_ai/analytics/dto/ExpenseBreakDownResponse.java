package com.finsight.finsight_ai.analytics.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Builder
public record ExpenseBreakDownResponse(
    List<CategoryPercentageDto> breakDown,
    BigDecimal totalExpenses,
    LocalDate start,
    LocalDate end,
    String currency
)
{}
