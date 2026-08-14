package com.finsight.finsight_ai.analytics.dto;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.List;

public record CashflowTrendResponse(
        List<MonthlyDataPoint> dataPoints
) {
    public record MonthlyDataPoint(
            YearMonth month,
            BigDecimal totalIncome,
            BigDecimal totalExpense,
            BigDecimal net
    ){}
}
