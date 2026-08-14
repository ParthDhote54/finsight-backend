package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record SpendingDeltaExplainerResult(
        UUID userId,
        String categoryOrGroup,
        LocalDate periodAStart,
        LocalDate periodAEnd,
        LocalDate periodBStart,
        LocalDate periodBEnd,
        BigDecimal periodATotal,
        BigDecimal periodBTotal,
        BigDecimal delta,
        BigDecimal percentageChange,
        String currency,
        List<ContributorItem> topContributors
) {
    public record ContributorItem(
            String merchantName,
            BigDecimal periodAAmount,
            BigDecimal periodBAmount,
            BigDecimal delta
    ) {}
}
