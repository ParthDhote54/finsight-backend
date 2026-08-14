package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record CashflowResult(
        UUID userId,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal netCashFlow,
        String currency,
        LocalDate startDate,
        LocalDate endDate
) {}
