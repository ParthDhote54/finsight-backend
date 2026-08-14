package com.finsight.finsight_ai.ai.chat.application.plan;

import com.finsight.finsight_ai.ai.chat.domain.LowestCategoryResult;
import com.finsight.finsight_ai.ai.chat.domain.LowestTransactionResult;
import com.finsight.finsight_ai.ai.chat.domain.MerchantGroupSpendResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.plan.FinanceQueryPlan;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class FinanceQueryPlanExecutorTest {

    private final FinancialAnalyticsPort analyticsPort = mock(FinancialAnalyticsPort.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-12T10:00:00Z"), ZoneId.of("UTC"));
    private FinanceQueryPlanExecutor executor;

    @BeforeEach
    void setUp() {
        executor = new FinanceQueryPlanExecutor(analyticsPort, clock);
    }

    @Test
    void executesEntityComparisonDeterministically() {
        UUID userId = UUID.randomUUID();
        when(analyticsPort.getSpendByMerchantGroup(eq(userId), eq("coffee"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(userId, "coffee", new BigDecimal("120"), 2L, "INR", null, null));
        when(analyticsPort.getSpendByMerchantGroup(eq(userId), eq("pizza"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(userId, "pizza", new BigDecimal("450"), 3L, "INR", null, null));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.COMPARE,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.SUM_AMOUNT,
                List.of("coffee", "pizza"),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.NONE,
                null,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("coffee was ₹120");
        assertThat(result.textAnswer()).contains("pizza was ₹450");
        assertThat(result.textAnswer()).contains("spent ₹330 more on pizza");
        assertThat(result.numericEvidence()).hasSize(3);
    }

    @Test
    void executesLowestCategoryExcludingZeroSpend() {
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);

        when(analyticsPort.getLowestCategorySpend(eq(userId), eq(start), eq(end)))
                .thenReturn(new LowestCategoryResult(userId, "Groceries", new BigDecimal("150.00"), "INR", start, end, true));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                1,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).as(result.errorMessage()).isTrue();
        assertThat(result.textAnswer()).contains("lowest spending was Groceries at ₹150.00");
    }

    @Test
    void zeroSpendExclusion_whenCategoryHasNoTransactions_returnsNoCategoryExpensesMessage() {
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);

        when(analyticsPort.getLowestCategorySpend(eq(userId), eq(start), eq(end)))
                .thenReturn(LowestCategoryResult.empty(userId, start, end));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                1,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("no recorded category expenses for August 2026");
        assertThat(result.numericEvidence()).isEmpty();
    }

    @Test
    void entityComparison_whenOneEntityHasZeroSpend_handlesZeroCleanly() {
        UUID userId = UUID.randomUUID();
        when(analyticsPort.getSpendByMerchantGroup(eq(userId), eq("coffee"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(userId, "coffee", BigDecimal.ZERO, 0L, "INR", null, null));
        when(analyticsPort.getSpendByMerchantGroup(eq(userId), eq("pizza"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(userId, "pizza", new BigDecimal("250"), 2L, "INR", null, null));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.COMPARE,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.SUM_AMOUNT,
                List.of("coffee", "pizza"),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.NONE,
                null,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("coffee was ₹0");
        assertThat(result.textAnswer()).contains("pizza was ₹250");
        assertThat(result.textAnswer()).contains("spent ₹250 more on pizza");
        assertThat(result.numericEvidence()).extracting(NumericEvidence::value)
                .containsExactly(BigDecimal.ZERO, new BigDecimal("250"), new BigDecimal("250"));
    }

    @Test
    void lowestTransaction_whenTiedMinimumAmounts_returnsEarliestDatedTransactionDeterministically() {
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        UUID tiedEarliestTxId = UUID.randomUUID();

        when(analyticsPort.getLowestTransaction(eq(userId), eq(start), eq(end)))
                .thenReturn(new LowestTransactionResult(userId, tiedEarliestTxId, "Chai (Earliest)", new BigDecimal("10.00"), "Coffee Shops", "INR", start, true));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.TRANSACTION,
                FinanceQueryPlan.Dimension.TRANSACTION,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                1,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("lowest transaction in August 2026 was ₹10.00 for 'Chai (Earliest)'");
        assertThat(result.transactionEvidenceIds()).containsExactly(tiedEarliestTxId);
        assertThat(result.numericEvidence()).extracting(NumericEvidence::value).containsExactly(new BigDecimal("10.00"));
    }

    @Test
    void lowestTransaction_enforcesExpenseOnlySemantics_ignoringSmallerIncomeTransactions() {
        UUID userId = UUID.randomUUID();
        LocalDate start = LocalDate.of(2026, 8, 1);
        LocalDate end = LocalDate.of(2026, 8, 31);
        UUID smallestExpenseId = UUID.randomUUID();

        when(analyticsPort.getLowestTransaction(eq(userId), eq(start), eq(end)))
                .thenReturn(new LowestTransactionResult(userId, smallestExpenseId, "Bus Ticket", new BigDecimal("15.00"), "Travel", "INR", LocalDate.of(2026, 8, 5), true));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.RANK,
                FinanceQueryPlan.Subject.TRANSACTION,
                FinanceQueryPlan.Dimension.TRANSACTION,
                FinanceQueryPlan.Metric.MIN_AMOUNT,
                List.of(),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.ASC,
                1,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(userId, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("₹15.00 for 'Bus Ticket'");
        assertThat(result.textAnswer()).doesNotContain("₹5.00");
        assertThat(result.transactionEvidenceIds()).containsExactly(smallestExpenseId);
    }

    @Test
    void executor_enforcesMultiTenantIsolation_scopingQueryToTargetTenant() {
        UUID tenantTarget = UUID.randomUUID();
        UUID tenantOther = UUID.randomUUID();

        when(analyticsPort.getSpendByMerchantGroup(eq(tenantTarget), eq("coffee"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(tenantTarget, "coffee", new BigDecimal("100.00"), 1L, "INR", null, null));
        when(analyticsPort.getSpendByMerchantGroup(eq(tenantTarget), eq("pizza"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(tenantTarget, "pizza", new BigDecimal("200.00"), 1L, "INR", null, null));

        when(analyticsPort.getSpendByMerchantGroup(eq(tenantOther), eq("coffee"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(tenantOther, "coffee", new BigDecimal("10.00"), 1L, "INR", null, null));

        FinanceQueryPlan plan = new FinanceQueryPlan(
                FinanceQueryPlan.Operation.COMPARE,
                FinanceQueryPlan.Subject.SPENDING,
                FinanceQueryPlan.Dimension.CATEGORY,
                FinanceQueryPlan.Metric.SUM_AMOUNT,
                List.of("coffee", "pizza"),
                "2026-08",
                null,
                null,
                FinanceQueryPlan.Order.NONE,
                null,
                null,
                null,
                "EXPENSE",
                false,
                null,
                1.0
        );

        FinanceQueryPlanExecutor.ExecutionResult result = executor.execute(tenantTarget, plan);

        assertThat(result.success()).isTrue();
        assertThat(result.textAnswer()).contains("coffee was ₹100.00");
        assertThat(result.textAnswer()).contains("pizza was ₹200.00");
        assertThat(result.textAnswer()).doesNotContain("₹10.00");
        verify(analyticsPort, times(1)).getSpendByMerchantGroup(eq(tenantTarget), eq("coffee"), any(), any());
        verify(analyticsPort, never()).getSpendByMerchantGroup(eq(tenantOther), eq("coffee"), any(), any());
    }
}
