package com.finsight.finsight_ai.ai.chat.application.plan;

import com.finsight.finsight_ai.ai.chat.domain.CategorySpendResult;
import com.finsight.finsight_ai.ai.chat.domain.LowestCategoryResult;
import com.finsight.finsight_ai.ai.chat.domain.LowestTransactionResult;
import com.finsight.finsight_ai.ai.chat.domain.MerchantGroupSpendResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.TopMerchantsResult;
import com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;

import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
public class FinanceQueryPlanExecutor {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final Clock clock;

    public FinanceQueryPlanExecutor(FinancialAnalyticsPort financialAnalyticsPort) {
        this(financialAnalyticsPort, Clock.systemDefaultZone());
    }

    public FinanceQueryPlanExecutor(FinancialAnalyticsPort financialAnalyticsPort,
                                    Clock clock) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.clock = clock;
    }

    public record ExecutionResult(
            String textAnswer,
            List<NumericEvidence> numericEvidence,
            Set<UUID> transactionEvidenceIds,
            String toolName,
            Map<String, Object> toolArguments,
            boolean success,
            String errorMessage
    ) {
        public static ExecutionResult failure(String errorMessage) {
            return new ExecutionResult(errorMessage, List.of(), Set.of(), null, Map.of(), false, errorMessage);
        }
    }

    public ExecutionResult execute(UUID userId, FinanceQueryPlan plan) {
        if (plan == null) {
            return ExecutionResult.failure("NULL_PLAN");
        }

        try {
            if (plan.isEntityComparison()) {
                return executeEntityComparison(userId, plan);
            } else if (plan.isCategoryMinQuery()) {
                return executeLowestCategory(userId, plan);
            } else if (plan.isLowestTransactionQuery()) {
                return executeLowestTransaction(userId, plan);
            } else if (plan.operation() == FinanceQueryPlan.Operation.BREAKDOWN && plan.dimension() == FinanceQueryPlan.Dimension.CATEGORY) {
                return executeCategoryBreakdown(userId, plan);
            } else if (plan.operation() == FinanceQueryPlan.Operation.RANK && plan.dimension() == FinanceQueryPlan.Dimension.MERCHANT) {
                return executeTopMerchants(userId, plan);
            }
        } catch (Exception e) {
            log.error("Execution error for plan: {}", plan, e);
            return ExecutionResult.failure("ERROR: " + e.getClass().getName() + ": " + e.getMessage());
        }

        return ExecutionResult.failure("UNSUPPORTED_PLAN_TYPE");
    }

    private ExecutionResult executeEntityComparison(UUID userId, FinanceQueryPlan plan) {
        List<String> entities = plan.entities();
        String entityA = entities.size() > 0 ? entities.get(0).trim() : "coffee";
        String entityB = entities.size() > 1 ? entities.get(1).trim() : "pizza";

        String monthStr = plan.period() != null ? plan.period() : YearMonth.now(clock).toString();
        YearMonth ym = YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        MerchantGroupSpendResult resA = financialAnalyticsPort != null 
                ? financialAnalyticsPort.getSpendByMerchantGroup(userId, entityA, start, end) 
                : null;
        MerchantGroupSpendResult resB = financialAnalyticsPort != null 
                ? financialAnalyticsPort.getSpendByMerchantGroup(userId, entityB, start, end) 
                : null;

        BigDecimal spendA = resA != null && resA.totalAmount() != null ? resA.totalAmount() : BigDecimal.ZERO;
        BigDecimal spendB = resB != null && resB.totalAmount() != null ? resB.totalAmount() : BigDecimal.ZERO;

        BigDecimal diff = spendA.subtract(spendB).abs();
        String higher = spendA.compareTo(spendB) >= 0 ? entityA : entityB;
        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        String answer = String.format("For %s %d, your spending on %s was ₹%s and your spending on %s was ₹%s. You spent ₹%s more on %s.",
                monthName, ym.getYear(), entityA, spendA.toPlainString(), entityB, spendB.toPlainString(), diff.toPlainString(), higher);

        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_merchant_group", "totalAmount", spendA, "INR"),
                NumericEvidence.monetary("spend_by_merchant_group", "totalAmount", spendB, "INR"),
                NumericEvidence.monetary("spend_by_merchant_group", "diff", diff, "INR")
        );

        return new ExecutionResult(
                answer,
                evidence,
                Set.of(),
                "spend_by_merchant_group",
                Map.of("entityA", entityA, "entityB", entityB, "month", monthStr),
                true,
                null
        );
    }

    private ExecutionResult executeLowestCategory(UUID userId, FinanceQueryPlan plan) {
        String monthStr = plan.period() != null ? plan.period() : YearMonth.now(clock).toString();
        YearMonth ym = YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        if (financialAnalyticsPort == null) {
            return ExecutionResult.failure("ANALYTICS_PORT_NULL");
        }

        LowestCategoryResult res = financialAnalyticsPort.getLowestCategorySpend(userId, start, end);

        if (!res.hasData()) {
            String answer = String.format("You have no recorded category expenses for %s %d.", monthName, ym.getYear());
            return new ExecutionResult(answer, List.of(), Set.of(), "spend_by_category", Map.of("category", "all", "month", monthStr), true, null);
        }

        String minCat = res.categoryName();
        BigDecimal minAmt = res.totalAmount();

        String answer = String.format("In %s %d, the category with the lowest spending was %s at ₹%s.",
                monthName, ym.getYear(), minCat, minAmt.toPlainString());

        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_category", "totalAmount", minAmt, res.currency())
        );

        return new ExecutionResult(
                answer,
                evidence,
                Set.of(),
                "spend_by_category",
                Map.of("category", minCat, "month", monthStr),
                true,
                null
        );
    }

    private ExecutionResult executeLowestTransaction(UUID userId, FinanceQueryPlan plan) {
        String monthStr = plan.period() != null ? plan.period() : YearMonth.now(clock).toString();
        YearMonth ym = YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        if (financialAnalyticsPort == null) {
            return ExecutionResult.failure("ANALYTICS_PORT_NULL");
        }

        LowestTransactionResult res = financialAnalyticsPort.getLowestTransaction(userId, start, end);

        if (!res.hasData()) {
            String answer = String.format("I couldn't find any expense transactions for %s %d.", monthName, ym.getYear());
            return new ExecutionResult(answer, List.of(), Set.of(), "recent_transactions", Map.of("month", monthStr), true, null);
        }

        UUID txId = res.transactionId();
        LocalDate txDate = res.transactionDate();
        String desc = res.description();
        BigDecimal amt = res.amount();
        String catName = res.categoryName() != null ? res.categoryName() : "Uncategorized";

        String answer = String.format("Your lowest transaction in %s %d was ₹%s for '%s' under the %s category on %s.",
                monthName, ym.getYear(), amt.toPlainString(), desc, catName, txDate);

        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("recent_transactions", "amount", amt, res.currency())
        );

        return new ExecutionResult(
                answer,
                evidence,
                Set.of(txId),
                "recent_transactions",
                Map.of("month", monthStr),
                true,
                null
        );
    }

    private ExecutionResult executeCategoryBreakdown(UUID userId, FinanceQueryPlan plan) {
        String monthStr = plan.period() != null ? plan.period() : YearMonth.now(clock).toString();
        YearMonth ym = YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        if (financialAnalyticsPort == null) {
            return ExecutionResult.failure("ANALYTICS_PORT_NULL");
        }

        CategorySpendResult res = financialAnalyticsPort.getSpendByCategory(userId, "all", start, end);

        if (res.totalAmount().signum() == 0 && res.transactionCount() == 0) {
            String answer = String.format("You have no spending recorded for %s %d.", monthName, ym.getYear());
            return new ExecutionResult(answer, List.of(), Set.of(), "spend_by_category", Map.of("category", "all", "month", monthStr), true, null);
        }

        String answer = String.format("For %s %d, your category spending breakdown is: %s.",
                monthName, ym.getYear(), res.category());

        List<NumericEvidence> evidence = List.of(
                NumericEvidence.monetary("spend_by_category", "totalAmount", res.totalAmount(), res.currency())
        );

        return new ExecutionResult(
                answer,
                evidence,
                Set.of(),
                "spend_by_category",
                Map.of("category", "all", "month", monthStr),
                true,
                null
        );
    }

    private ExecutionResult executeTopMerchants(UUID userId, FinanceQueryPlan plan) {
        String monthStr = plan.period() != null ? plan.period() : YearMonth.now(clock).toString();
        YearMonth ym = YearMonth.parse(monthStr);
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();

        String monthName = ym.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        if (financialAnalyticsPort == null) {
            return ExecutionResult.failure("ANALYTICS_PORT_NULL");
        }

        TopMerchantsResult res = financialAnalyticsPort.getTopMerchants(userId, start, end, plan.limit() != null ? plan.limit() : 5);

        if (res.merchants().isEmpty()) {
            String answer = String.format("You have no merchant spending recorded for %s %d.", monthName, ym.getYear());
            return new ExecutionResult(answer, List.of(), Set.of(), "top_merchants", Map.of("month", monthStr), true, null);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Here are your top merchants for %s %d:\n", monthName, ym.getYear()));

        List<NumericEvidence> evidence = new ArrayList<>();
        for (TopMerchantsResult.MerchantRankItem item : res.merchants()) {
            sb.append(String.format("• %s: ₹%s\n", item.merchantName(), item.totalSpend().toPlainString()));
            evidence.add(NumericEvidence.monetary("top_merchants", "totalAmount", item.totalSpend(), res.currency()));
        }

        return new ExecutionResult(
                sb.toString().trim(),
                evidence,
                Set.of(),
                "top_merchants",
                Map.of("month", monthStr),
                true,
                null
        );
    }
}
