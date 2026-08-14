package com.finsight.finsight_ai.analytics.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.UUID;

@Builder
public record CategoryPercentageDto(
        UUID categoryId,
        String categoryName,
        BigDecimal amount,
        BigDecimal percentage

) {
}
