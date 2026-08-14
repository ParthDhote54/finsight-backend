package com.finsight.finsight_ai.dto;

import com.finsight.finsight_ai.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String email,
        String displayName,
        String currencyPreference,
        Instant createdAt
) {
    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getCurrencyPreference(),
                user.getCreatedAt()
        );
    }
}
