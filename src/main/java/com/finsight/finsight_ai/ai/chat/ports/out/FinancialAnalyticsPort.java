package com.finsight.finsight_ai.ai.chat.ports.out;

import com.finsight.finsight_ai.ai.chat.domain.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface FinancialAnalyticsPort {

    CategorySpendResult getSpendByCategory(UUID userId, String category, LocalDate startDate, LocalDate endDate);

    List<TransactionSummaryResult> getRecentTransactions(UUID userId, String merchant, Integer limit);

    MonthComparisonResult compareSpendingPeriods(UUID userId, LocalDate period1Start, LocalDate period1End,
                                                 LocalDate period2Start, LocalDate period2End);

    MerchantGroupSpendResult getSpendByMerchantGroup(UUID userId, String merchantGroup, LocalDate startDate, LocalDate endDate);

    MerchantBreakdownResult getMerchantBreakdown(UUID userId, String categoryOrGroup, LocalDate startDate, LocalDate endDate);

    TopMerchantsResult getTopMerchants(UUID userId, LocalDate startDate, LocalDate endDate, int limit);

    SumByIdsResult sumByTransactionIds(UUID userId, List<UUID> transactionIds);

    SubscriptionDetectionResult detectSubscriptions(UUID userId, int limit);

    SavingsProjectionResult projectSavings(UUID userId, String categoryOrGroup, BigDecimal reductionPercentage,
                                           BigDecimal reductionAmount, int timeHorizonMonths);

    BalanceReconciliationResult reconcileBalance(UUID userId, UUID accountId, BigDecimal startingBalance,
                                                 BalanceReconciliationResult.StartingBalanceSource startingBalanceSource);

    CashflowResult getCashflowSummary(UUID userId, LocalDate startDate, LocalDate endDate);

    LowestCategoryResult getLowestCategorySpend(UUID userId, LocalDate startDate, LocalDate endDate);

    LowestTransactionResult getLowestTransaction(UUID userId, LocalDate startDate, LocalDate endDate);
}
