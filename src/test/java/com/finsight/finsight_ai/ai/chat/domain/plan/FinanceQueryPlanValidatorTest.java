package com.finsight.finsight_ai.ai.chat.domain.plan;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinanceQueryPlanValidatorTest {

    private final Clock clock = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneId.of("UTC"));

    @Test
    void validatesAndResolvesThisMonthPeriod() {
        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                "this month",
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                10,
                null,
                null,
                "EXPENSE",
                false,
                null,
                0.95
        );

        FinanceQueryPlanValidator.ValidationResult result = FinanceQueryPlanValidator.validate(plan, clock);

        assertThat(result.valid()).isTrue();
        assertThat(result.validatedPlan().period()).isEqualTo("2026-08");
        assertThat(result.validatedPlan().transactionType()).isEqualTo("EXPENSE");
    }

    @Test
    void validatesAndResolvesLastMonthPeriod() {
        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.COMPARE,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.PERIOD,
                FinanceQueryPlan.Metric.SUM_AMOUNT,
                List.of("coffee", "pizza"),
                null,
                "last month",
                "this month",
                FinanceQueryPlan.Order.NONE,
                null,
                null,
                null,
                null,
                false,
                null,
                1.0
        );

        FinanceQueryPlanValidator.ValidationResult result = FinanceQueryPlanValidator.validate(plan, clock);

        assertThat(result.valid()).isTrue();
        assertThat(result.validatedPlan().periodA()).isEqualTo("2026-07");
        assertThat(result.validatedPlan().periodB()).isEqualTo("2026-08");
        assertThat(result.validatedPlan().isEntityComparison()).isTrue();
    }

    @Test
    void detectsNeedsClarification() {
        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                null,
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                5,
                null,
                null,
                "EXPENSE",
                true,
                "PERIOD",
                0.5
        );

        FinanceQueryPlanValidator.ValidationResult result = FinanceQueryPlanValidator.validate(plan, clock);

        assertThat(result.valid()).isFalse();
        assertThat(result.reason()).isEqualTo("NEEDS_CLARIFICATION:PERIOD");
    }
}
