package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.util.UUID;

public record TransactionCitation(
        UUID transactionId,
        String merchant,
        BigDecimal amount,
        String currency,
        String date
) {}
