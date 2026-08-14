package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import com.finsight.finsight_ai.ai.chat.domain.*;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.entity.TransactionType;
import com.finsight.finsight_ai.exception.MixedCurrencyAggregationException;
import com.finsight.finsight_ai.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class FinancialAnalyticsService implements FinancialAnalyticsPort {

    private final EntityManager entityManager;

    @Override
    public CategorySpendResult getSpendByCategory(UUID userId, String category,
                                                   LocalDate startDate, LocalDate endDate) {
        List<String> categoryTerms = categoryTerms(category);
        boolean isAllCategories = isAllCategoriesQuery(category);

        String sql = """
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t.id),
                       COALESCE(MIN(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId)),
                       COALESCE(MAX(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id
                                 AND c.deleted_at IS NULL
                                 AND (c.user_id IS NULL OR c.user_id = :userId)
                WHERE a.user_id = :userId
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                """ + (isAllCategories ? "" : "  AND LOWER(c.name) IN (:categoryTerms)\n");

        var query = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate);

        if (!isAllCategories) {
            query.setParameter("categoryTerms", categoryTerms);
        }

        List<?> rows = query.getResultList();

        if (rows.isEmpty()) {
            return new CategorySpendResult(userId, category, BigDecimal.ZERO, 0L, getUserCurrency(userId), startDate, endDate);
        }

        Object[] row = (Object[]) rows.get(0);
        BigDecimal total = toBigDecimal(row[0]);
        long count = ((Number) row[1]).longValue();
        String currency = requireSingleCurrency((String) row[2], (String) row[3], userId);

        String largestCategory = null;
        BigDecimal largestCategoryAmount = null;
        if (isAllCategories) {
            List<?> topCatRows = entityManager.createNativeQuery("""
                    SELECT COALESCE(c.name, 'Uncategorized'), SUM(t.amount)
                    FROM transactions t
                    JOIN accounts a ON t.account_id = a.id
                    LEFT JOIN categories c ON t.category_id = c.id AND c.deleted_at IS NULL
                    WHERE a.user_id = :userId
                      AND t.transaction_type = 'EXPENSE'
                      AND t.deleted_at IS NULL
                      AND a.deleted_at IS NULL
                      AND t.transaction_date >= :startDate
                      AND t.transaction_date <= :endDate
                    GROUP BY COALESCE(c.name, 'Uncategorized')
                    ORDER BY SUM(t.amount) DESC
                    """)
                    .setParameter("userId", userId)
                    .setParameter("startDate", startDate)
                    .setParameter("endDate", endDate)
                    .setMaxResults(1)
                    .getResultList();
            if (!topCatRows.isEmpty()) {
                Object[] topRow = (Object[]) topCatRows.get(0);
                largestCategory = (String) topRow[0];
                largestCategoryAmount = toBigDecimal(topRow[1]);
            }
        }

        return new CategorySpendResult(userId, category, total, count, currency, startDate, endDate, largestCategory, largestCategoryAmount);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<TransactionSummaryResult> getRecentTransactions(UUID userId, String merchant, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 20) : 5;

        String sql = """
                SELECT t.id,
                       t.transaction_date as date,
                       t.description as merchant_name,
                       t.amount,
                       UPPER(a.currency),
                       COALESCE(c.name, 'Uncategorized') as category,
                       t.transaction_type
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id
                                      AND c.deleted_at IS NULL
                                      AND (c.user_id IS NULL OR c.user_id = :userId)
                WHERE a.user_id = :userId
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                """ + (merchant != null && !merchant.isBlank() ? " AND LOWER(t.description) LIKE LOWER(:merchant)" : "") + """
                
                ORDER BY t.transaction_date DESC, t.id DESC
                LIMIT :limit
                """;

        var query = entityManager.createNativeQuery(sql)
                .setParameter("userId", userId)
                .setParameter("limit", effectiveLimit);

        if (merchant != null && !merchant.isBlank()) {
            query.setParameter("merchant", "%" + merchant.trim() + "%");
        }

        List<Object[]> rows = query.getResultList();

        return rows.stream()
                .map(r -> new TransactionSummaryResult(
                        (UUID) r[0],
                        toLocalDate(r[1]),
                        (String) r[2],
                        toBigDecimal(r[3]),
                        (String) r[4],
                        (String) r[5],
                        TransactionType.valueOf((String) r[6])
                ))
                .toList();
    }

    @Override
    public MonthComparisonResult compareSpendingPeriods(UUID userId,
                                                        LocalDate period1Start, LocalDate period1End,
                                                        LocalDate period2Start, LocalDate period2End) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(t.amount) FILTER (
                           WHERE t.transaction_date >= :period1Start
                             AND t.transaction_date <= :period1End
                       ), 0),
                       COALESCE(SUM(t.amount) FILTER (
                           WHERE t.transaction_date >= :period2Start
                             AND t.transaction_date <= :period2End
                       ), 0),
                       COALESCE(MIN(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId)),
                       COALESCE(MAX(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                  AND a.deleted_at IS NULL
                  AND t.deleted_at IS NULL
                  AND t.transaction_type = 'EXPENSE'
                  AND t.transaction_date >= :overallStart
                  AND t.transaction_date <= :overallEnd
                  AND ((t.transaction_date >= :period1Start AND t.transaction_date <= :period1End)
                    OR (t.transaction_date >= :period2Start AND t.transaction_date <= :period2End))
                """)
                .setParameter("userId", userId)
                .setParameter("period1Start", period1Start)
                .setParameter("period1End", period1End)
                .setParameter("period2Start", period2Start)
                .setParameter("period2End", period2End)
                .setParameter("overallStart", period1Start.isBefore(period2Start) ? period1Start : period2Start)
                .setParameter("overallEnd", period1End.isAfter(period2End) ? period1End : period2End)
                .getResultList();

        if (rows.isEmpty()) {
            String currency = getUserCurrency(userId);
            return new MonthComparisonResult(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO.setScale(2), currency);
        }

        Object[] totals = (Object[]) rows.get(0);
        BigDecimal period1Total = toBigDecimal(totals[0]);
        BigDecimal period2Total = toBigDecimal(totals[1]);
        String currency = requireSingleCurrency((String) totals[2], (String) totals[3], userId);
        BigDecimal diff = period2Total.subtract(period1Total);
        BigDecimal pctChange = period1Total.signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : diff.multiply(BigDecimal.valueOf(100))
                        .divide(period1Total, 2, RoundingMode.HALF_UP);

        return new MonthComparisonResult(period1Total, period2Total, diff, pctChange, currency);
    }

    @Override
    public MerchantGroupSpendResult getSpendByMerchantGroup(UUID userId, String merchantGroup,
                                                             LocalDate startDate, LocalDate endDate) {
        // For v1, merchant group maps directly to category (Tier-1 registry will refine later)
        List<String> groupTerms = categoryTerms(merchantGroup);
        List<?> rows = entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(t.amount), 0), COUNT(t.id),
                       COALESCE(MIN(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId)),
                       COALESCE(MAX(UPPER(a.currency)),
                                (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                JOIN categories c ON t.category_id = c.id
                                 AND c.deleted_at IS NULL
                                 AND (c.user_id IS NULL OR c.user_id = :userId)
                WHERE a.user_id = :userId
                  AND LOWER(c.name) IN (:groupTerms)
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                """)
                .setParameter("userId", userId)
                .setParameter("groupTerms", groupTerms)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        if (rows.isEmpty()) {
            return new MerchantGroupSpendResult(
                    userId, merchantGroup, BigDecimal.ZERO, 0L, getUserCurrency(userId), startDate, endDate);
        }

        Object[] row = (Object[]) rows.get(0);
        BigDecimal total = toBigDecimal(row[0]);
        long count = ((Number) row[1]).longValue();
        String currency = requireSingleCurrency((String) row[2], (String) row[3], userId);

        return new MerchantGroupSpendResult(
                userId, merchantGroup, total, count, currency, startDate, endDate);
    }

    @Override
    @SuppressWarnings("unchecked")
    public MerchantBreakdownResult getMerchantBreakdown(UUID userId, String categoryOrGroup,
                                                         LocalDate startDate, LocalDate endDate) {
        List<String> categoryTerms = categoryTerms(categoryOrGroup);
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT COALESCE(NULLIF(BTRIM(t.description), ''), 'Unknown merchant') as merchant_name,
                       COALESCE(SUM(t.amount), 0),
                       COUNT(t.id),
                       MIN(UPPER(a.currency)),
                       MAX(UPPER(a.currency))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id
                                      AND c.deleted_at IS NULL
                                      AND (c.user_id IS NULL OR c.user_id = :userId)
                WHERE a.user_id = :userId
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                  AND ((:uncategorized = TRUE AND c.id IS NULL)
                    OR (:uncategorized = FALSE AND c.name IS NOT NULL
                        AND LOWER(c.name) IN (:categoryTerms)))
                GROUP BY COALESCE(NULLIF(BTRIM(t.description), ''), 'Unknown merchant')
                ORDER BY SUM(t.amount) DESC, merchant_name ASC
                """)
                .setParameter("userId", userId)
                .setParameter("categoryTerms", categoryTerms)
                .setParameter("uncategorized", "uncategorized".equalsIgnoreCase(categoryOrGroup.trim()))
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        BigDecimal grandTotal = rows.stream()
                .map(r -> toBigDecimal(r[1]))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        String currency = rows.isEmpty()
                ? getUserCurrency(userId)
                : requireSingleCurrency(
                        rows.stream().map(row -> (String) row[3]).min(String::compareTo).orElse(null),
                        rows.stream().map(row -> (String) row[4]).max(String::compareTo).orElse(null),
                        userId);

        List<MerchantBreakdownResult.MerchantItem> items = rows.stream()
                .map(r -> {
                    BigDecimal amount = toBigDecimal(r[1]);
                    BigDecimal percentage = grandTotal.signum() == 0
                            ? BigDecimal.ZERO.setScale(2)
                            : amount.multiply(BigDecimal.valueOf(100))
                                    .divide(grandTotal, 2, RoundingMode.HALF_UP);
                    return new MerchantBreakdownResult.MerchantItem(
                            (String) r[0], amount, ((Number) r[2]).longValue(), percentage
                    );
                })
                .toList();

        return new MerchantBreakdownResult(
                userId, categoryOrGroup, startDate, endDate, currency, items);
    }

    @Override
    @SuppressWarnings("unchecked")
    public TopMerchantsResult getTopMerchants(UUID userId, LocalDate startDate, LocalDate endDate, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                WITH eligible AS (
                    SELECT COALESCE(NULLIF(BTRIM(t.description), ''), 'Unknown merchant') AS merchant_name,
                           t.amount,
                           UPPER(a.currency) AS currency
                    FROM transactions t
                    JOIN accounts a ON t.account_id = a.id
                    WHERE a.user_id = :userId
                      AND t.transaction_type = 'EXPENSE'
                      AND t.deleted_at IS NULL
                      AND a.deleted_at IS NULL
                      AND t.transaction_date >= :startDate
                      AND t.transaction_date <= :endDate
                ),
                currency_stats AS (
                    SELECT MIN(currency) AS min_currency, MAX(currency) AS max_currency
                    FROM eligible
                )
                SELECT e.merchant_name,
                       SUM(e.amount),
                       COUNT(*),
                       cs.min_currency,
                       cs.max_currency
                FROM eligible e
                CROSS JOIN currency_stats cs
                GROUP BY e.merchant_name, cs.min_currency, cs.max_currency
                ORDER BY SUM(e.amount) DESC, e.merchant_name ASC
                LIMIT :limit
                """)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setParameter("limit", limit)
                .getResultList();

        List<TopMerchantsResult.MerchantRankItem> merchants = new java.util.ArrayList<>();
        String currency = rows.isEmpty()
                ? getUserCurrency(userId)
                : requireSingleCurrency(
                        rows.stream().map(row -> (String) row[3]).min(String::compareTo).orElse(null),
                        rows.stream().map(row -> (String) row[4]).max(String::compareTo).orElse(null),
                        userId);
        for (int i = 0; i < rows.size(); i++) {
            Object[] r = rows.get(i);
            merchants.add(new TopMerchantsResult.MerchantRankItem(
                    i + 1,
                    (String) r[0],
                    toBigDecimal(r[1]),
                    ((Number) r[2]).longValue()
            ));
        }

        return new TopMerchantsResult(userId, startDate, endDate, currency, merchants);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SumByIdsResult sumByTransactionIds(UUID userId, List<UUID> transactionIds) {
        if (transactionIds == null || transactionIds.isEmpty()) {
            return new SumByIdsResult(
                    userId, BigDecimal.ZERO, getUserCurrency(userId), 0, List.of(), List.of());
        }

        if (transactionIds.size() > 100) {
            throw new IllegalArgumentException("At most 100 transaction IDs may be summed");
        }

        List<UUID> safeIds = transactionIds.stream()
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();

        if (safeIds.isEmpty()) {
            return new SumByIdsResult(
                    userId, BigDecimal.ZERO, getUserCurrency(userId), 0, List.of(), List.of());
        }

        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT t.id,
                       SUM(t.amount) OVER () AS total_amount,
                       MIN(UPPER(a.currency)) OVER () AS min_currency,
                       MAX(UPPER(a.currency)) OVER () AS max_currency
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                  AND t.id IN (:ids)
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                ORDER BY t.id
                """)
                .setParameter("userId", userId)
                .setParameter("ids", safeIds)
                .getResultList();

        Set<UUID> matchedSet = new HashSet<>();
        rows.forEach(row -> matchedSet.add((UUID) row[0]));
        BigDecimal total = rows.isEmpty() ? BigDecimal.ZERO : toBigDecimal(rows.get(0)[1]);
        String currency = rows.isEmpty()
                ? getUserCurrency(userId)
                : requireSingleCurrency((String) rows.get(0)[2], (String) rows.get(0)[3], userId);

        List<UUID> matched = safeIds.stream().filter(matchedSet::contains).toList();
        List<UUID> unmatched = safeIds.stream().filter(id -> !matchedSet.contains(id)).toList();

        return new SumByIdsResult(userId, total, currency, matched.size(), matched, unmatched);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SubscriptionDetectionResult detectSubscriptions(UUID userId, int limit) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT COALESCE(NULLIF(BTRIM(t.description), ''), 'Unknown merchant') as merchant_name,
                       t.id,
                       t.amount,
                       t.transaction_date,
                       UPPER(a.currency) as currency
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                ORDER BY merchant_name, t.transaction_date ASC
                """)
                .setParameter("userId", userId)
                .getResultList();

        if (rows.isEmpty()) {
            return new SubscriptionDetectionResult(userId, getUserCurrency(userId), List.of());
        }

        Set<String> currencies = rows.stream().map(r -> (String) r[4]).filter(Objects::nonNull).collect(Collectors.toSet());
        if (currencies.size() > 1) {
            throw new MixedCurrencyAggregationException();
        }
        String currency = currencies.isEmpty() ? getUserCurrency(userId) : currencies.iterator().next();

        Map<String, List<Object[]>> byMerchant = rows.stream()
                .collect(Collectors.groupingBy(r -> (String) r[0]));

        List<SubscriptionDetectionResult.SubscriptionItem> items = new ArrayList<>();

        for (Map.Entry<String, List<Object[]>> entry : byMerchant.entrySet()) {
            List<Object[]> txs = entry.getValue();
            if (txs.size() < 2) continue;

            List<LocalDate> dates = txs.stream().map(r -> toLocalDate(r[3])).sorted().toList();
            List<UUID> ids = txs.stream().map(r -> (UUID) r[1]).toList();
            BigDecimal totalAmount = txs.stream().map(r -> toBigDecimal(r[2])).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal avgAmount = totalAmount.divide(BigDecimal.valueOf(txs.size()), 2, RoundingMode.HALF_UP);

            long totalDaysDiff = 0;
            for (int i = 1; i < dates.size(); i++) {
                totalDaysDiff += java.time.temporal.ChronoUnit.DAYS.between(dates.get(i - 1), dates.get(i));
            }
            double avgInterval = (double) totalDaysDiff / (dates.size() - 1);

            String frequency;
            String classification;

            if (avgInterval >= 20 && avgInterval <= 40) {
                frequency = "MONTHLY";
                classification = "LIKELY_RECURRING";
            } else if (avgInterval >= 5 && avgInterval <= 10) {
                frequency = "WEEKLY";
                classification = "LIKELY_RECURRING";
            } else if (avgInterval >= 80 && avgInterval <= 100) {
                frequency = "QUARTERLY";
                classification = "POSSIBLE_RECURRING";
            } else if (dates.size() >= 3) {
                frequency = "IRREGULAR";
                classification = "POSSIBLE_RECURRING";
            } else {
                continue;
            }

            items.add(new SubscriptionDetectionResult.SubscriptionItem(
                    entry.getKey(),
                    avgAmount,
                    frequency,
                    txs.size(),
                    dates.get(0),
                    dates.get(dates.size() - 1),
                    classification,
                    ids
            ));
        }

        items.sort(Comparator.comparing(SubscriptionDetectionResult.SubscriptionItem::averageAmount).reversed());
        int max = Math.min(limit > 0 ? limit : 10, items.size());

        return new SubscriptionDetectionResult(userId, currency, items.subList(0, max));
    }

    @Override
    @SuppressWarnings("unchecked")
    public SavingsProjectionResult projectSavings(UUID userId, String categoryOrGroup,
                                                  BigDecimal reductionPercentage, BigDecimal reductionAmount,
                                                  int timeHorizonMonths) {
        if (timeHorizonMonths <= 0 || timeHorizonMonths > 120) {
            throw new IllegalArgumentException("Time horizon months must be between 1 and 120");
        }
        if (reductionPercentage != null && (reductionPercentage.compareTo(BigDecimal.ZERO) < 0 || reductionPercentage.compareTo(new BigDecimal("100")) > 0)) {
            throw new IllegalArgumentException("Reduction percentage must be between 0 and 100");
        }

        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(3);

        MerchantGroupSpendResult baselineResult = getSpendByMerchantGroup(userId, categoryOrGroup, startDate, endDate);
        BigDecimal baselineMonthlySpend = baselineResult.totalAmount().divide(new BigDecimal("3"), 2, RoundingMode.HALF_UP);

        BigDecimal proposedMonthlyReduction;
        BigDecimal effectivePercentage;

        if (reductionPercentage != null) {
            effectivePercentage = reductionPercentage;
            proposedMonthlyReduction = baselineMonthlySpend.multiply(reductionPercentage)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
        } else if (reductionAmount != null) {
            proposedMonthlyReduction = reductionAmount;
            effectivePercentage = baselineMonthlySpend.signum() == 0
                    ? BigDecimal.ZERO
                    : reductionAmount.multiply(new BigDecimal("100"))
                    .divide(baselineMonthlySpend, 2, RoundingMode.HALF_UP);
        } else {
            throw new IllegalArgumentException("Either reduction percentage or reduction amount is required");
        }

        BigDecimal horizonSavings = proposedMonthlyReduction.multiply(BigDecimal.valueOf(timeHorizonMonths));

        return new SavingsProjectionResult(
                userId,
                categoryOrGroup,
                baselineResult.currency(),
                baselineMonthlySpend,
                effectivePercentage,
                proposedMonthlyReduction,
                proposedMonthlyReduction,
                timeHorizonMonths,
                horizonSavings,
                "Projection based on historical spending over the past 3 months; not guaranteed future savings."
        );
    }

    @Override
    @SuppressWarnings("unchecked")
    public BalanceReconciliationResult reconcileBalance(UUID userId, UUID accountId, BigDecimal startingBalance,
                                                 BalanceReconciliationResult.StartingBalanceSource startingBalanceSource) {
        List<Object[]> accRows = entityManager.createNativeQuery("""
                SELECT a.id, a.name, a.balance, UPPER(a.currency)
                FROM accounts a
                WHERE a.id = :accountId AND a.user_id = :userId AND a.deleted_at IS NULL
                """)
                .setParameter("accountId", accountId)
                .setParameter("userId", userId)
                .getResultList();

        if (accRows.isEmpty()) {
            throw new ResourceNotFoundException("Account not found for reconciliation");
        }

        Object[] acc = accRows.get(0);
        String accountName = (String) acc[1];
        BigDecimal actualEndingBalance = toBigDecimal(acc[2]);
        String currency = (String) acc[3];

        List<Object[]> txRows = entityManager.createNativeQuery("""
                SELECT t.transaction_type, SUM(t.amount), MIN(UPPER(a.currency)), MAX(UPPER(a.currency))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE t.account_id = :accountId AND t.deleted_at IS NULL AND a.deleted_at IS NULL
                GROUP BY t.transaction_type
                """)
                .setParameter("accountId", accountId)
                .getResultList();

        Set<String> currencies = new HashSet<>();
        currencies.add(currency);

        BigDecimal totalIncome = BigDecimal.ZERO;
        BigDecimal totalExpense = BigDecimal.ZERO;

        for (Object[] r : txRows) {
            String type = (String) r[0];
            BigDecimal sum = toBigDecimal(r[1]);
            if (r[2] != null) currencies.add(((String) r[2]).toUpperCase(Locale.ROOT));
            if (r[3] != null) currencies.add(((String) r[3]).toUpperCase(Locale.ROOT));

            if ("INCOME".equalsIgnoreCase(type)) {
                totalIncome = sum;
            } else if ("EXPENSE".equalsIgnoreCase(type)) {
                totalExpense = sum;
            }
        }

        if (currencies.size() > 1) {
            throw new MixedCurrencyAggregationException();
        }

        BalanceReconciliationResult.StartingBalanceSource resolvedSource =
                startingBalanceSource != null ? startingBalanceSource : BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE;

        if (startingBalance == null || resolvedSource == BalanceReconciliationResult.StartingBalanceSource.UNVERIFIED
                || resolvedSource == BalanceReconciliationResult.StartingBalanceSource.UNAVAILABLE) {
            return new BalanceReconciliationResult(
                    userId, accountId, accountName, currency, null,
                    resolvedSource,
                    totalIncome, totalExpense, null, actualEndingBalance,
                    null, false, "INSUFFICIENT_DATA"
            );
        }

        BigDecimal expectedEndingBalance = startingBalance.add(totalIncome).subtract(totalExpense);
        BigDecimal difference = actualEndingBalance.subtract(expectedEndingBalance);
        boolean reconciled = difference.signum() == 0;
        String status = reconciled ? "RECONCILED" : "DISCREPANCY_DETECTED";

        return new BalanceReconciliationResult(
                userId, accountId, accountName, currency, startingBalance,
                resolvedSource,
                totalIncome, totalExpense, expectedEndingBalance, actualEndingBalance,
                difference, reconciled, status
        );
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        return new BigDecimal(value.toString());
    }

    private static LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate date) {
            return date;
        }
        return ((java.sql.Date) value).toLocalDate();
    }

    private boolean isAllCategoriesQuery(String category) {
        if (category == null || category.isBlank()) return true;
        String norm = category.trim().toLowerCase(Locale.ROOT);
        return norm.equals("all") || norm.equals("total") || norm.equals("spending")
                || norm.equals("expenses") || norm.equals("overall") || norm.equals("all categories")
                || norm.equals("everything") || norm.equals("total spending");
    }

    private static List<String> categoryTerms(String category) {
        if (category == null) {
            return List.of("");
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        if ("food".equals(normalized) || "food & dining".equals(normalized)) {
            return List.of("food", "food & dining");
        }
        return List.of(normalized);
    }

    @Override
    public CashflowResult getCashflowSummary(UUID userId, LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new IllegalArgumentException("startDate cannot be after endDate");
        }

        List<?> rows = entityManager.createNativeQuery("""
                SELECT
                    t.transaction_type,
                    COALESCE(SUM(t.amount), 0),
                    COALESCE(MIN(UPPER(a.currency)), (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId)),
                    COALESCE(MAX(UPPER(a.currency)), (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                WHERE a.user_id = :userId
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                GROUP BY t.transaction_type
                """)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        BigDecimal income = BigDecimal.ZERO;
        BigDecimal expense = BigDecimal.ZERO;
        String minCurr = null;
        String maxCurr = null;

        for (Object item : rows) {
            Object[] row = (Object[]) item;
            String typeStr = row[0].toString();
            BigDecimal amount = toBigDecimal(row[1]);
            if (minCurr == null) minCurr = (String) row[2];
            if (maxCurr == null) maxCurr = (String) row[3];
            if ("INCOME".equalsIgnoreCase(typeStr)) {
                income = amount;
            } else if ("EXPENSE".equalsIgnoreCase(typeStr)) {
                expense = amount;
            }
        }

        String currency = requireSingleCurrency(minCurr, maxCurr, userId);
        BigDecimal net = income.subtract(expense);

        return new CashflowResult(userId, income, expense, net, currency, startDate, endDate);
    }

    private String getUserCurrency(UUID userId) {
        try {
            List<?> list = entityManager.createNativeQuery("""
                    SELECT UPPER(u.currency_preference)
                    FROM users u
                    WHERE u.id = :userId
                    """)
                    .setParameter("userId", userId)
                    .getResultList();
            if (!list.isEmpty() && list.get(0) != null) {
                return (String) list.get(0);
            }
        } catch (Exception ignored) {
        }
        return "INR";
    }

    private String requireSingleCurrency(String minimumCurrency, String maximumCurrency, UUID userId) {
        if (minimumCurrency == null || maximumCurrency == null) {
            return getUserCurrency(userId);
        }
        if (!minimumCurrency.equalsIgnoreCase(maximumCurrency)) {
            throw new MixedCurrencyAggregationException();
        }
        return minimumCurrency.toUpperCase(java.util.Locale.ROOT);
    }

    @Override
    @SuppressWarnings("unchecked")
    public LowestCategoryResult getLowestCategorySpend(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<?> rows = entityManager.createNativeQuery("""
                SELECT COALESCE(c.name, 'Uncategorized'), SUM(t.amount),
                       COALESCE(MIN(UPPER(a.currency)), (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId)),
                       COALESCE(MAX(UPPER(a.currency)), (SELECT UPPER(u.currency_preference) FROM users u WHERE u.id = :userId))
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id AND c.deleted_at IS NULL
                WHERE a.user_id = :userId
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                GROUP BY COALESCE(c.name, 'Uncategorized')
                HAVING SUM(t.amount) > 0
                ORDER BY SUM(t.amount) ASC
                """)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .setMaxResults(1)
                .getResultList();

        if (rows.isEmpty()) {
            return LowestCategoryResult.empty(userId, startDate, endDate);
        }

        Object[] row = (Object[]) rows.get(0);
        String categoryName = (String) row[0];
        BigDecimal totalAmount = toBigDecimal(row[1]);
        String currency = requireSingleCurrency((String) row[2], (String) row[3], userId);

        return new LowestCategoryResult(userId, categoryName, totalAmount, currency, startDate, endDate, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    public LowestTransactionResult getLowestTransaction(UUID userId, LocalDate startDate, LocalDate endDate) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT t.id,
                       t.description,
                       t.amount,
                       COALESCE(c.name, 'Uncategorized'),
                       UPPER(a.currency),
                       t.transaction_date
                FROM transactions t
                JOIN accounts a ON t.account_id = a.id
                LEFT JOIN categories c ON t.category_id = c.id AND c.deleted_at IS NULL
                WHERE a.user_id = :userId
                  AND t.transaction_type = 'EXPENSE'
                  AND t.deleted_at IS NULL
                  AND a.deleted_at IS NULL
                  AND t.transaction_date >= :startDate
                  AND t.transaction_date <= :endDate
                ORDER BY t.amount ASC, t.transaction_date ASC, t.id ASC
                LIMIT 1
                """)
                .setParameter("userId", userId)
                .setParameter("startDate", startDate)
                .setParameter("endDate", endDate)
                .getResultList();

        if (rows.isEmpty()) {
            return LowestTransactionResult.empty(userId, startDate, endDate);
        }

        Object[] row = rows.get(0);
        UUID transactionId = (UUID) row[0];
        String description = (String) row[1];
        BigDecimal amount = toBigDecimal(row[2]);
        String categoryName = (String) row[3];
        String currency = (String) row[4];
        LocalDate transactionDate = toLocalDate(row[5]);

        return new LowestTransactionResult(userId, transactionId, description, amount, categoryName, currency, transactionDate, true);
    }
}
