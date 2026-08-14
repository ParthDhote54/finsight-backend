package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.tools.ToolArgumentException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ToolArguments {

    private ToolArguments() {
    }

    static String requiredString(Map<String, Object> arguments, String field, int maxLength) {
        Object raw = arguments.get(field);
        if (raw == null) {
            throw error("MISSING_REQUIRED_ARGUMENT", field, field + " is required");
        }
        if (!(raw instanceof String value)) {
            throw error("INVALID_ARGUMENT_TYPE", field, field + " must be a string");
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw error("INVALID_ARGUMENT_VALUE", field, field + " must not be blank");
        }
        if (normalized.length() > maxLength) {
            throw error("INVALID_ARGUMENT_VALUE", field, field + " must be at most " + maxLength + " characters");
        }
        return normalized;
    }

    static String optionalString(Map<String, Object> arguments, String field, int maxLength) {
        if (!arguments.containsKey(field) || arguments.get(field) == null) {
            return null;
        }
        return requiredString(arguments, field, maxLength);
    }

    static int optionalInteger(Map<String, Object> arguments, String field,
                               int defaultValue, int minimum, int maximum) {
        if (!arguments.containsKey(field) || arguments.get(field) == null) {
            return defaultValue;
        }

        Object raw = arguments.get(field);
        long value;
        if (raw instanceof Byte || raw instanceof Short || raw instanceof Integer || raw instanceof Long) {
            value = ((Number) raw).longValue();
        } else if (raw instanceof Float || raw instanceof Double) {
            double numeric = ((Number) raw).doubleValue();
            if (!Double.isFinite(numeric) || numeric % 1 != 0) {
                throw error("INVALID_ARGUMENT_TYPE", field, field + " must be an integer");
            }
            value = ((Number) raw).longValue();
        } else if (raw instanceof BigInteger integer) {
            try {
                value = integer.longValueExact();
            } catch (ArithmeticException exception) {
                throw error("INVALID_ARGUMENT_VALUE", field, field + " is outside the supported range");
            }
        } else if (raw instanceof BigDecimal decimal && decimal.stripTrailingZeros().scale() <= 0) {
            try {
                value = decimal.longValueExact();
            } catch (ArithmeticException exception) {
                throw error("INVALID_ARGUMENT_VALUE", field, field + " is outside the supported range");
            }
        } else {
            throw error("INVALID_ARGUMENT_TYPE", field, field + " must be an integer");
        }

        if (value < minimum || value > maximum) {
            throw error("INVALID_ARGUMENT_VALUE", field,
                    field + " must be between " + minimum + " and " + maximum);
        }
        return (int) value;
    }

    static List<UUID> requiredUniqueUuidList(Map<String, Object> arguments, String field,
                                             int minimumItems, int maximumItems) {
        Object raw = arguments.get(field);
        if (raw == null) {
            throw error("MISSING_REQUIRED_ARGUMENT", field, field + " is required");
        }
        if (!(raw instanceof List<?> values)) {
            throw error("INVALID_ARGUMENT_TYPE", field, field + " must be an array");
        }
        if (values.size() < minimumItems || values.size() > maximumItems) {
            throw error("INVALID_ARGUMENT_VALUE", field,
                    field + " must contain between " + minimumItems + " and " + maximumItems + " items");
        }

        LinkedHashSet<UUID> uniqueIds = new LinkedHashSet<>();
        for (Object value : values) {
            if (!(value instanceof String rawId)) {
                throw error("INVALID_ARGUMENT_TYPE", field, field + " must contain only UUID strings");
            }
            UUID id;
            try {
                id = UUID.fromString(rawId);
            } catch (IllegalArgumentException exception) {
                throw error("INVALID_ARGUMENT_VALUE", field, field + " contains an invalid UUID");
            }
            if (!uniqueIds.add(id)) {
                throw error("INVALID_ARGUMENT_VALUE", field, field + " must not contain duplicate IDs");
            }
        }
        return new ArrayList<>(uniqueIds);
    }

    static UUID requiredUuid(Map<String, Object> arguments, String field) {
        String raw = requiredString(arguments, field, 36);
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            throw error("INVALID_ARGUMENT_VALUE", field, field + " must be a valid UUID");
        }
    }

    static BigDecimal optionalPositiveDecimal(Map<String, Object> arguments, String field) {
        if (!arguments.containsKey(field) || arguments.get(field) == null) {
            return null;
        }
        Object raw = arguments.get(field);
        BigDecimal value;
        if (raw instanceof Number number) {
            value = new BigDecimal(number.toString());
        } else if (raw instanceof String text) {
            try {
                value = new BigDecimal(text);
            } catch (NumberFormatException e) {
                throw error("INVALID_ARGUMENT_VALUE", field, field + " must be a valid number");
            }
        } else {
            throw error("INVALID_ARGUMENT_TYPE", field, field + " must be a number");
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw error("INVALID_ARGUMENT_VALUE", field, field + " must be non-negative");
        }
        return value;
    }

    private static ToolArgumentException error(String code, String field, String message) {
        return new ToolArgumentException(code, field, message);
    }
}
