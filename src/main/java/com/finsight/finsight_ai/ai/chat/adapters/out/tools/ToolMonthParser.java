package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.tools.ToolArgumentException;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;

@Component
public class ToolMonthParser {

    private final Clock clock;

    public ToolMonthParser() {
        this(Clock.systemDefaultZone());
    }

    public ToolMonthParser(Clock clock) {
        this.clock = clock;
    }

    public MonthRange optionalMonth(Object rawValue, String field) {
        if (rawValue == null) {
            return range(YearMonth.now(clock));
        }
        return parse(rawValue, field, false);
    }

    public MonthRange optionalMonthOrAllTime(Object rawValue, String field) {
        if (rawValue == null) {
            return new MonthRange(LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31));
        }
        return parse(rawValue, field, false);
    }

    public MonthRange requiredMonth(Object rawValue, String field) {
        if (rawValue == null) {
            throw new ToolArgumentException(
                    "MISSING_REQUIRED_ARGUMENT", field, field + " is required and must use YYYY-MM");
        }
        return parse(rawValue, field, true);
    }

    private MonthRange parse(Object rawValue, String field, boolean required) {
        if (!(rawValue instanceof String value)) {
            throw new ToolArgumentException(
                    "INVALID_ARGUMENT_TYPE", field, field + " must be a string using YYYY-MM");
        }
        if (value.isBlank()) {
            throw new ToolArgumentException(required ? "MISSING_REQUIRED_ARGUMENT" : "INVALID_MONTH_FORMAT",
                    field, field + " must use YYYY-MM");
        }

        try {
            return range(YearMonth.parse(value.trim()));
        } catch (java.time.format.DateTimeParseException exception) {
            throw new ToolArgumentException("INVALID_MONTH_FORMAT", field, field + " must use YYYY-MM");
        }
    }

    private static MonthRange range(YearMonth month) {
        return new MonthRange(month.atDay(1), month.atEndOfMonth());
    }

    public record MonthRange(LocalDate startDate, LocalDate endDate) {
    }
}
