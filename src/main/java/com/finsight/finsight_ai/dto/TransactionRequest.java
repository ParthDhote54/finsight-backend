package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.TransactionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Getter
@Setter
public class TransactionRequest {
    @NotNull(message = "account id is required")
    private UUID accountId;
    private UUID categoryId;

    @Positive(message = "Amount must be strictly greater than 0")
    @NotNull(message = "amount is required")
    @Digits(integer = 15, fraction = 4, message = "amount supports at most 15 integer and 4 decimal digits")
    private BigDecimal amount;

    @NotNull(message = "transaction type is required")
    private TransactionType transactionType;
    @Size(max = 255, message = "description cannot exceed 255 characters")
    private String description;

    public UUID getAccountId() { return accountId; }
    public void setAccountId(UUID accountId) { this.accountId = accountId; }

    public UUID getCategoryId() { return categoryId; }
    public void setCategoryId(UUID categoryId) { this.categoryId = categoryId; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
