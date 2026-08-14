package com.finsight.finsight_ai.ai.chat.domain.plan;

import java.time.Clock;
import java.time.YearMonth;

public class FinanceQueryPlanValidator {

    public record ValidationResult(boolean valid, String reason, FinanceQueryPlan validatedPlan) {}

    public static ValidationResult validate(FinanceQueryPlan plan, Clock clock) {
        if (plan == null) {
            return new ValidationResult(false, "NULL_PLAN", null);
        }

        if (plan.needsClarification()) {
            return new ValidationResult(false, "NEEDS_CLARIFICATION:" + plan.missingSlot(), plan);
        }

        String resolvedPeriod = resolveRelativePeriod(plan.period(), clock);
        String resolvedPeriodA = resolveRelativePeriod(plan.periodA(), clock);
        String resolvedPeriodB = resolveRelativePeriod(plan.periodB(), clock);

        String transactionType = plan.transactionType();
        if (transactionType == null || transactionType.isBlank()) {
            transactionType = (plan.subject() == FinanceQueryPlan.Subject.INCOME) ? "INCOME" : "EXPENSE";
        } else {
            transactionType = transactionType.toUpperCase();
        }

        FinanceQueryPlan sanitized = new FinanceQueryPlan(
                plan.operation(),
                plan.subject(),
                plan.dimension(),
                plan.metric(),
                plan.entities(),
                resolvedPeriod,
                resolvedPeriodA,
                resolvedPeriodB,
                plan.order(),
                plan.limit() != null && plan.limit() > 0 ? Math.min(plan.limit(), 50) : 5,
                plan.category(),
                plan.merchant(),
                transactionType,
                false,
                null,
                plan.confidence() != null ? plan.confidence() : 1.0
        );

        return new ValidationResult(true, "VALID", sanitized);
    }

    private static String resolveRelativePeriod(String rawPeriod, Clock clock) {
        if (rawPeriod == null || rawPeriod.isBlank()) {
            return null;
        }
        String lower = rawPeriod.trim().toLowerCase();
        if (lower.contains("this month") || lower.contains("current month") || lower.equals("current") || lower.equals("mtd")) {
            return YearMonth.now(clock).toString();
        } else if (lower.contains("last month") || lower.contains("previous month") || lower.contains("prior month")) {
            return YearMonth.now(clock).minusMonths(1).toString();
        }
        return rawPeriod;
    }
}
