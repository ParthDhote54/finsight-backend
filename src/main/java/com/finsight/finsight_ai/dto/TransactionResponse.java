package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.Transaction;
import com.finsight.finsight_ai.entity.TransactionType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
public class TransactionResponse {
    private UUID id;
    private UUID accountId;
    private BigDecimal amount;
    private TransactionType transactionType;
    private String category;
    private String description;
    private LocalDateTime transactionDate;
    private BigDecimal newAccountBalance; //crucial for frontend updates.
}
