package com.finsight.finsight_ai.analytics.projection;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TrendProjection(
        LocalDate periodStart, // Maps from period_start
        BigDecimal totalIncome, // Maps from total_income
        BigDecimal totalExpense, // Maps from total_expense
        String minimumCurrency,
        String maximumCurrency
) {}
