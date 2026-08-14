package com.finsight.finsight_ai.ai.chat.domain;

import java.math.BigDecimal;
import java.util.Currency;
import java.util.Objects;

public record NumericEvidence(
        String toolName,
        String field,
        Kind kind,
        BigDecimal value,
        Currency currency
) {
    public NumericEvidence {
        Objects.requireNonNull(toolName, "Evidence tool name is required");
        Objects.requireNonNull(field, "Evidence field is required");
        Objects.requireNonNull(kind, "Evidence kind is required");
        Objects.requireNonNull(value, "Evidence value is required");
        if (kind == Kind.MONETARY) {
            Objects.requireNonNull(currency, "Monetary evidence currency is required");
        } else if (currency != null) {
            throw new IllegalArgumentException("Only monetary evidence may carry currency");
        }
    }

    public static NumericEvidence monetary(String toolName, String field,
                                           BigDecimal value, String currencyCode) {
        return new NumericEvidence(
                toolName, field, Kind.MONETARY, value, Currency.getInstance(currencyCode));
    }

    public static NumericEvidence percentage(String toolName, String field, BigDecimal value) {
        return new NumericEvidence(toolName, field, Kind.PERCENTAGE, value, null);
    }

    public static NumericEvidence count(String toolName, String field, long value) {
        return new NumericEvidence(toolName, field, Kind.COUNT, BigDecimal.valueOf(value), null);
    }

    public static NumericEvidence rank(String toolName, String field, int value) {
        return new NumericEvidence(toolName, field, Kind.RANK, BigDecimal.valueOf(value), null);
    }

    public enum Kind {
        MONETARY,
        PERCENTAGE,
        COUNT,
        RANK
    }
}
