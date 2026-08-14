package com.finsight.finsight_ai.ai.chat.evaluation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record ArgumentConstraint(
        String toolName,
        String argumentName,
        Type type,
        Object expectedValue,
        Set<Object> allowedValues,
        BigDecimal min,
        BigDecimal max
) {
    public ArgumentConstraint {
        Objects.requireNonNull(toolName, "toolName is required");
        Objects.requireNonNull(argumentName, "argumentName is required");
        Objects.requireNonNull(type, "type is required");
        allowedValues = allowedValues == null ? Set.of() : Set.copyOf(allowedValues);
    }

    public static ArgumentConstraint exists(String toolName, String argumentName) {
        return new ArgumentConstraint(toolName, argumentName, Type.EXISTS, null, Set.of(), null, null);
    }

    public static ArgumentConstraint absent(String toolName, String argumentName) {
        return new ArgumentConstraint(toolName, argumentName, Type.ABSENT, null, Set.of(), null, null);
    }

    public static ArgumentConstraint equalsValue(String toolName, String argumentName, Object expectedValue) {
        return new ArgumentConstraint(toolName, argumentName, Type.EQUALS, expectedValue, Set.of(), null, null);
    }

    public static ArgumentConstraint inValues(String toolName, String argumentName, Set<Object> allowedValues) {
        return new ArgumentConstraint(toolName, argumentName, Type.IN_VALUES, null, allowedValues, null, null);
    }

    public static ArgumentConstraint numericRange(String toolName, String argumentName, BigDecimal min, BigDecimal max) {
        return new ArgumentConstraint(toolName, argumentName, Type.NUMERIC_RANGE, null, Set.of(), min, max);
    }

    public static ArgumentConstraint mustNotEqual(String toolName, String argumentName, Object forbiddenValue) {
        return new ArgumentConstraint(toolName, argumentName, Type.MUST_NOT_EQUAL, forbiddenValue, Set.of(), null, null);
    }

    boolean satisfiedBy(ToolInvocationTrace invocation) {
        if (!toolName.equals(invocation.toolName())) {
            return true;
        }
        Map<String, Object> arguments = invocation.arguments();
        boolean present = arguments.containsKey(argumentName);
        Object actual = arguments.get(argumentName);
        return switch (type) {
            case EXISTS -> present;
            case ABSENT -> !present;
            case EQUALS -> present && valuesEqual(actual, expectedValue);
            case IN_VALUES -> present && allowedValues.stream().anyMatch(value -> valuesEqual(actual, value));
            case NUMERIC_RANGE -> present && withinRange(actual);
            case MUST_NOT_EQUAL -> !present || !valuesEqual(actual, expectedValue);
        };
    }

    private boolean valuesEqual(Object actual, Object expected) {
        if (actual == null || expected == null) {
            return actual == expected;
        }
        BigDecimal actualNumber = decimal(actual);
        BigDecimal expectedNumber = decimal(expected);
        if (actualNumber != null && expectedNumber != null) {
            return actualNumber.compareTo(expectedNumber) == 0;
        }
        return String.valueOf(actual).equalsIgnoreCase(String.valueOf(expected));
    }

    private boolean withinRange(Object actual) {
        BigDecimal number = decimal(actual);
        if (number == null) {
            return false;
        }
        return (min == null || number.compareTo(min) >= 0)
                && (max == null || number.compareTo(max) <= 0);
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    public enum Type {
        EXISTS,
        ABSENT,
        EQUALS,
        IN_VALUES,
        NUMERIC_RANGE,
        MUST_NOT_EQUAL
    }
}
