package com.finsight.finsight_ai.analytics.projection;

import java.math.BigDecimal;
import java.util.UUID;

public record CategoryTotalProjection(

    UUID categoryId,
    String categoryName,
    BigDecimal total,
    Long count,
    String minimumCurrency,
    String maximumCurrency)
{
}

