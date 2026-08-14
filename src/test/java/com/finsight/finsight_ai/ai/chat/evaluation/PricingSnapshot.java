package com.finsight.finsight_ai.ai.chat.evaluation;

import java.math.BigDecimal;
import java.time.Instant;

public record PricingSnapshot(
        String provider,
        String modelId,
        String pricingTier,
        Instant retrievedAt,
        String source,
        BigDecimal inputPerMillion,
        BigDecimal outputPerMillion,
        String currency
) {
}
