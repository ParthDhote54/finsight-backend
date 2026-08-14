package com.finsight.finsight_ai.analytics.projection;

import com.finsight.finsight_ai.entity.TransactionType;

import java.math.BigDecimal;

public record TypeTotalProjection(
    TransactionType type,
    BigDecimal total,
    String minimumCurrency,
    String maximumCurrency
) {}
