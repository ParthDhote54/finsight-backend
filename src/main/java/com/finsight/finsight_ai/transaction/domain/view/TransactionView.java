package com.finsight.finsight_ai.transaction.domain.view;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;


public record TransactionView(
        UUID id,
        UUID userId,
        BigDecimal amount,
        String description,
        UUID categoryId,
        LocalDate transactionDate
) {
}
