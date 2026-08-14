package com.finsight.finsight_ai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.ToString;

@Data //lombok generates getters and setters and a toString() for us.
public class UserRegisterationRequest {

    @NotBlank(message = "email is required")
    @Email(message = "email must be valid")
    private String Email;
    @NotBlank(message = "password is required")
    @ToString.Exclude
    private String rawPassword;
    @NotBlank(message = "display name is required")
    private String displayName;
}
