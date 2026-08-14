package com.finsight.finsight_ai.ai.chat;

import com.finsight.finsight_ai.ai.chat.adapters.out.tools.*;
import com.finsight.finsight_ai.ai.chat.domain.*;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import com.finsight.finsight_ai.entity.Account;
import com.finsight.finsight_ai.entity.AccountType;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.repository.AccountRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AllFinanceToolsIntegrationTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final UUID txId1 = UUID.randomUUID();
    private final UUID txId2 = UUID.randomUUID();
    private final FinancialAnalyticsPort analytics = mock(FinancialAnalyticsPort.class);
    private final AccountRepository accountRepository = mock(AccountRepository.class);
    private final ToolMonthParser monthParser = new ToolMonthParser();

    @BeforeEach
    void setUp() {
        TenantContext.set(userId);

        Account account = new Account();
        account.setId(accountId);
        account.setUser(new com.finsight.finsight_ai.entity.User());
        account.setName("Checking");
        account.setType(AccountType.CHECKING);
        account.setBalance(new BigDecimal("100000.00"));
        account.setCurrency("INR");

        when(accountRepository.findAllByUserId(userId)).thenReturn(List.of(account));

        when(analytics.getSpendByCategory(eq(userId), eq("Dining"), any(), any()))
                .thenReturn(new CategorySpendResult(userId, "Dining", new BigDecimal("5700.00"), 2L, "INR", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        when(analytics.getRecentTransactions(eq(userId), any(), any()))
                .thenReturn(List.of(new TransactionSummaryResult(txId1, LocalDate.of(2026, 8, 5), "Swiggy", new BigDecimal("1200.00"), "INR", "Dining", TransactionType.EXPENSE)));

        when(analytics.compareSpendingPeriods(eq(userId), any(), any(), any(), any()))
                .thenReturn(new MonthComparisonResult(new BigDecimal("1000.00"), new BigDecimal("5700.00"), new BigDecimal("4700.00"), new BigDecimal("470.0"), "INR"));

        when(analytics.getSpendByMerchantGroup(eq(userId), eq("Dining"), any(), any()))
                .thenReturn(new MerchantGroupSpendResult(userId, "Dining", new BigDecimal("5700.00"), 2L, "INR", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        when(analytics.getMerchantBreakdown(eq(userId), eq("Dining"), any(), any()))
                .thenReturn(new MerchantBreakdownResult(userId, "Dining", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "INR", List.of(new MerchantBreakdownResult.MerchantItem("Swiggy", new BigDecimal("5700.00"), 2L, new BigDecimal("100.00")))));

        when(analytics.getTopMerchants(eq(userId), any(), any(), eq(5)))
                .thenReturn(new TopMerchantsResult(userId, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "INR", List.of(new TopMerchantsResult.MerchantRankItem(1, "Swiggy", new BigDecimal("5700.00"), 2L))));

        when(analytics.sumByTransactionIds(eq(userId), any()))
                .thenReturn(new SumByIdsResult(userId, new BigDecimal("5700.00"), "INR", 2, List.of(txId1, txId2), List.of()));

        when(analytics.detectSubscriptions(eq(userId), eq(10)))
                .thenReturn(new SubscriptionDetectionResult(userId, "INR", List.of(new SubscriptionDetectionResult.SubscriptionItem("Netflix", new BigDecimal("649.00"), "MONTHLY", 1, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), "RECURRING", List.of(txId1)))));

        when(analytics.projectSavings(eq(userId), any(), any(), any(), eq(6)))
                .thenReturn(new SavingsProjectionResult(userId, "Dining", "INR", new BigDecimal("5700.00"), new BigDecimal("10.00"), new BigDecimal("570.00"), new BigDecimal("5130.00"), 6, new BigDecimal("3420.00"), "Disclaimer"));

        when(analytics.reconcileBalance(eq(userId), eq(accountId), any(), any()))
                .thenReturn(new BalanceReconciliationResult(userId, accountId, "Checking", "INR", new BigDecimal("100000.00"), BalanceReconciliationResult.StartingBalanceSource.USER_PROVIDED, BigDecimal.ZERO, new BigDecimal("5700.00"), new BigDecimal("94300.00"), new BigDecimal("94300.00"), BigDecimal.ZERO, true, "RECONCILED"));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void test1_SpendByCategoryTool() {
        FinanceTool tool = new SpendByCategoryTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("category", "Dining", "month", "2026-08"));
        assertThat(result.data()).isNotNull();
        assertThat(result.numericEvidence()).isNotEmpty();
    }

    @Test
    void test2_RecentTransactionsTool() {
        FinanceTool tool = new RecentTransactionsTool(analytics);
        FinanceToolResult result = tool.execute(Map.of("limit", 5));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test3_CompareMonthsTool() {
        FinanceTool tool = new CompareMonthsTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("month1", "2026-07", "month2", "2026-08"));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test4_SpendByMerchantGroupTool() {
        FinanceTool tool = new SpendByMerchantGroupTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("merchantGroup", "Dining", "month", "2026-08"));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test5_MerchantBreakdownTool() {
        FinanceTool tool = new MerchantBreakdownTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("categoryOrGroup", "Dining", "month", "2026-08"));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test6_TopMerchantsTool() {
        FinanceTool tool = new TopMerchantsTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("month", "2026-08", "limit", 5));
        assertThat(result.data()).isNotNull();
        assertThat(result.numericEvidence()).isNotEmpty();
    }

    @Test
    void test7_SumByTransactionIdsTool() {
        FinanceTool tool = new SumByTransactionIdsTool(analytics);
        FinanceToolResult result = tool.execute(Map.of("transaction_ids", List.of(txId1.toString(), txId2.toString())));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test8_SpendingDeltaExplainerTool() {
        FinanceTool tool = new SpendingDeltaExplainerTool(analytics, monthParser);
        FinanceToolResult result = tool.execute(Map.of("periodA", "2026-07", "periodB", "2026-08", "categoryOrGroup", "Dining"));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test9_SubscriptionDetectorTool() {
        FinanceTool tool = new SubscriptionDetectorTool(analytics);
        FinanceToolResult result = tool.execute(Map.of());
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test10_SavingsProjectorTool() {
        FinanceTool tool = new SavingsProjectorTool(analytics);
        FinanceToolResult result = tool.execute(Map.of("categoryOrGroup", "Dining", "reductionPercentage", 10, "timeHorizonMonths", 6));
        assertThat(result.data()).isNotNull();
    }

    @Test
    void test11_BalanceReconcilerTool() {
        FinanceTool tool = new BalanceReconcilerTool(analytics, accountRepository);
        FinanceToolResult result = tool.execute(Map.of("startingBalance", 100000));
        assertThat(result.data()).isNotNull();
    }
}
