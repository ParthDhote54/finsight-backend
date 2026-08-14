package com.finsight.finsight_ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data
public class LoginRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String email;
    @NotBlank(message = "password is required")
    @ToString.Exclude
    private String rawPassword;

}
