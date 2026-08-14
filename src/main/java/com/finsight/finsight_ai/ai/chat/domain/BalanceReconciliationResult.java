package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record BalanceReconciliationResult(
        UUID userId,
        UUID accountId,
        String accountName,
        String currency,
        BigDecimal startingBalance,
        StartingBalanceSource startingBalanceSource,
        BigDecimal totalIncome,
        BigDecimal totalExpense,
        BigDecimal expectedEndingBalance,
        BigDecimal actualEndingBalance,
        BigDecimal difference,
        boolean reconciled,
        String status
) {
    public enum StartingBalanceSource {
        PERSISTED_TRUSTED,
        USER_PROVIDED,
        UNVERIFIED,
        UNAVAILABLE
    }
}
