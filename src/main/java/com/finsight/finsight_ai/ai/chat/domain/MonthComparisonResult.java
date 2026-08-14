package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;

public record MonthComparisonResult(
        BigDecimal period1Total,
        BigDecimal period2Total,
        BigDecimal absoluteDifference,
        BigDecimal percentageChange,
        String currency
) {}


