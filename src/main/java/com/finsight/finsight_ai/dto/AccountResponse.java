package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.AccountType;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;


@Data
@Builder
public class AccountResponse {
    private UUID id;
    private String accountName;
    private AccountType accountType;
    private BigDecimal balance;
    private String currency;

    private Instant createdAt;



}
