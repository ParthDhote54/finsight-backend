package com.finsight.finsight_ai.ai.chat.domain;

import com.finsight.finsight_ai.entity.TransactionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record TransactionSummaryResult(
        UUID transactionId,
        LocalDate date,
        String merchant,
        BigDecimal amount,
        String currency,
        String category,
        TransactionType transactionType
) {}
