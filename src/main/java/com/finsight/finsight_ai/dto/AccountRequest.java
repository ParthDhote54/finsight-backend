package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.AccountType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AccountRequest {
    //Notice what is missing : we do not ask for a userID interface.
    @NotBlank(message = "account name is required")
    @Size(max = 255, message = "account name cannot exceed 255 characters")
    private String accountName;
    @NotNull(message = "account type is required")
    private AccountType accountType;
    @NotBlank(message = "currency is required")
    @Size(min = 3, max = 3, message = "currency must be a 3-letter code")
    @Pattern(regexp = "[A-Za-z]{3}", message = "currency must contain exactly 3 letters")
    private String currency;
}
