package com.finsight.finsight_ai.security;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;

import java.util.UUID;

public class UserPrincipal {
    private UUID userId;
    private String email;

    public UserPrincipal() {}
    public UserPrincipal(UUID userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
