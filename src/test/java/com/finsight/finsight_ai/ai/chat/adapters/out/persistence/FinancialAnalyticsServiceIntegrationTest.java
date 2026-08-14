package com.finsight.finsight_ai.ai.chat.adapters.out.persistence;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.chat.domain.MerchantBreakdownResult;
import com.finsight.finsight_ai.ai.chat.domain.SumByIdsResult;
import com.finsight.finsight_ai.analytics.repository.AnalyticsRepository;
import com.finsight.finsight_ai.analytics.service.AnalyticsService;
import com.finsight.finsight_ai.entity.TransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({TestcontainersConfiguration.class, FinancialAnalyticsService.class,
        AnalyticsRepository.class, AnalyticsService.class})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Testcontainers(disabledWithoutDocker = true)
class FinancialAnalyticsServiceIntegrationTest {

    @Autowired
    private FinancialAnalyticsService financialAnalyticsService;
    @Autowired
    private AnalyticsService analyticsService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private UUID userA;
    private UUID userB;
    private UUID accountA;
    private UUID deletedAccountA;
    private UUID accountB;
    private UUID diningA;
    private UUID diningB;
    private UUID foodDiningA;
    private UUID petFoodA;
    private UUID januaryExpenseOne;
    private UUID januaryExpenseTwo;
    private UUID januaryIncome;
    private UUID deletedTransaction;
    private UUID deletedAccountTransaction;
    private UUID otherTenantTransaction;
    private UUID uncategorizedExpense;

    @BeforeEach
    void setUp() {
        userA = insertUser("analytics-a");
        userB = insertUser("analytics-b");
        accountA = insertAccount(userA, false);
        deletedAccountA = insertAccount(userA, true);
        accountB = insertAccount(userB, false);
        diningA = insertCategory(userA, "Dining", TransactionType.EXPENSE, false);
        diningB = insertCategory(userB, "Dining", TransactionType.EXPENSE, false);
        foodDiningA = insertCategory(userA, "Food & Dining", TransactionType.EXPENSE, false);
        petFoodA = insertCategory(userA, "Pet Food", TransactionType.EXPENSE, false);

        januaryExpenseOne = insertTransaction(accountA, diningA, "Alpha", "10.1000",
                LocalDate.of(2026, 1, 1), TransactionType.EXPENSE, false);
        januaryExpenseTwo = insertTransaction(accountA, diningA, "Beta", "20.2000",
                LocalDate.of(2026, 1, 31), TransactionType.EXPENSE, false);
        januaryIncome = insertTransaction(accountA, diningA, "Employer", "1000.0000",
                LocalDate.of(2026, 1, 15), TransactionType.INCOME, false);
        uncategorizedExpense = insertTransaction(accountA, null, "Corner Shop", "3.3333",
                LocalDate.of(2026, 1, 20), TransactionType.EXPENSE, false);
        deletedTransaction = insertTransaction(accountA, diningA, "Deleted", "40.0000",
                LocalDate.of(2026, 1, 10), TransactionType.EXPENSE, true);
        deletedAccountTransaction = insertTransaction(deletedAccountA, diningA, "Closed account", "50.0000",
                LocalDate.of(2026, 1, 11), TransactionType.EXPENSE, false);
        otherTenantTransaction = insertTransaction(accountB, diningB, "Other tenant", "60.0000",
                LocalDate.of(2026, 1, 12), TransactionType.EXPENSE, false);
        insertTransaction(accountA, diningA, "February", "8.0000",
                LocalDate.of(2026, 2, 1), TransactionType.EXPENSE, false);
        insertTransaction(accountA, foodDiningA, "Swiggy", "25.0000",
                LocalDate.of(2026, 3, 1), TransactionType.EXPENSE, false);
        insertTransaction(accountA, foodDiningA, "Zomato", "15.0000",
                LocalDate.of(2026, 3, 2), TransactionType.EXPENSE, false);
        insertTransaction(accountA, petFoodA, "Pet Store", "99.0000",
                LocalDate.of(2026, 3, 3), TransactionType.EXPENSE, false);
    }

    @Test
    void allSpendOperationsAreExpenseOnlyTenantScopedAndSoftDeleteAware() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        var category = financialAnalyticsService.getSpendByCategory(userA, "Dining", start, end);
        assertThat(category.totalAmount()).isEqualByComparingTo("30.3000");
        assertThat(category.transactionCount()).isEqualTo(2);

        var group = financialAnalyticsService.getSpendByMerchantGroup(userA, "Dining", start, end);
        assertThat(group.totalAmount()).isEqualByComparingTo("30.3000");
        assertThat(group.transactionCount()).isEqualTo(2);

        MerchantBreakdownResult breakdown = financialAnalyticsService.getMerchantBreakdown(
                userA, "Dining", start, end);
        assertThat(breakdown.items()).extracting(MerchantBreakdownResult.MerchantItem::merchantName)
                .containsExactly("Beta", "Alpha");
        assertThat(breakdown.items()).extracting(MerchantBreakdownResult.MerchantItem::totalAmount)
                .usingComparatorForType(BigDecimal::compareTo, BigDecimal.class)
                .containsExactly(new BigDecimal("20.2000"), new BigDecimal("10.1000"));

        var top = financialAnalyticsService.getTopMerchants(userA, start, end, 20);
        assertThat(top.merchants()).extracting(item -> item.merchantName())
                .containsExactly("Beta", "Alpha", "Corner Shop");

        var comparison = financialAnalyticsService.compareSpendingPeriods(
                userA, start, end, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28));
        assertThat(comparison.period1Total()).isEqualByComparingTo("33.6333");
        assertThat(comparison.period2Total()).isEqualByComparingTo("8.0000");
        assertThat(comparison.absoluteDifference()).isEqualByComparingTo("-25.6333");
        assertThat(comparison.percentageChange()).isEqualByComparingTo("-76.21");
    }

    @Test
    void recentTransactionsExposeTypeButExcludeDeletedAndForeignRows() {
        var recent = financialAnalyticsService.getRecentTransactions(userA, null, 20);

        assertThat(recent).extracting(result -> result.transactionId())
                .contains(januaryExpenseOne, januaryExpenseTwo, januaryIncome, uncategorizedExpense)
                .doesNotContain(deletedTransaction, deletedAccountTransaction, otherTenantTransaction);
        assertThat(recent).filteredOn(result -> result.transactionId().equals(januaryIncome))
                .singleElement()
                .satisfies(result -> assertThat(result.transactionType()).isEqualTo(TransactionType.INCOME));
        assertThat(recent).filteredOn(result -> result.transactionId().equals(uncategorizedExpense))
                .singleElement()
                .satisfies(result -> assertThat(result.category()).isEqualTo("Uncategorized"));
    }

    @Test
    void categoryAliasMatchesLongerUserCategoryLabels() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 3, 31);

        var category = financialAnalyticsService.getSpendByCategory(userA, "food", start, end);
        assertThat(category.totalAmount()).isEqualByComparingTo("40.0000");
        assertThat(category.transactionCount()).isEqualTo(2);

        var exactLongLabel = financialAnalyticsService.getSpendByCategory(userA, "Food & Dining", start, end);
        assertThat(exactLongLabel.totalAmount()).isEqualByComparingTo("40.0000");
        assertThat(exactLongLabel.transactionCount()).isEqualTo(2);

        var group = financialAnalyticsService.getSpendByMerchantGroup(userA, "food", start, end);
        assertThat(group.totalAmount()).isEqualByComparingTo("40.0000");
        assertThat(group.transactionCount()).isEqualTo(2);

        MerchantBreakdownResult breakdown = financialAnalyticsService.getMerchantBreakdown(
                userA, "food", start, end);
        assertThat(breakdown.items()).extracting(MerchantBreakdownResult.MerchantItem::merchantName)
                .containsExactly("Swiggy", "Zomato");

        var petFood = financialAnalyticsService.getSpendByCategory(userA, "Pet Food", start, end);
        assertThat(petFood.totalAmount()).isEqualByComparingTo("99.0000");
        assertThat(petFood.transactionCount()).isEqualTo(1);

        var unrelated = financialAnalyticsService.getSpendByCategory(userA, "pet", start, end);
        assertThat(unrelated.totalAmount()).isEqualByComparingTo("0");
        assertThat(unrelated.transactionCount()).isZero();
    }

    @Test
    void sumByIdsUsesPostgresDecimalAggregationAndReportsEveryUnmatchedId() {
        UUID nonexistent = UUID.randomUUID();

        SumByIdsResult result = financialAnalyticsService.sumByTransactionIds(userA, List.of(
                januaryExpenseOne, januaryIncome, deletedTransaction, deletedAccountTransaction,
                otherTenantTransaction, nonexistent, januaryExpenseOne));

        assertThat(result.totalAmount()).isEqualByComparingTo("1010.1000");
        assertThat(result.transactionCount()).isEqualTo(2);
        assertThat(result.matchedIds()).containsExactly(januaryExpenseOne, januaryIncome);
        assertThat(result.unmatchedIds()).containsExactly(
                deletedTransaction, deletedAccountTransaction, otherTenantTransaction, nonexistent);
    }

    @Test
    void expenseBreakdownIncludesUncategorizedAndMatchesCashflowTotal() {
        LocalDate start = LocalDate.of(2026, 1, 1);
        LocalDate end = LocalDate.of(2026, 1, 31);

        var cashflow = analyticsService.getCashFlowSummary(userA, start, end);
        var breakdown = analyticsService.getExpenseBreakDown(userA, start, end);

        assertThat(breakdown.totalExpenses()).isEqualByComparingTo(cashflow.totalExpense());
        assertThat(breakdown.totalExpenses()).isEqualByComparingTo("33.6333");
        assertThat(breakdown.breakDown())
                .anySatisfy(item -> {
                    assertThat(item.categoryId()).isNull();
                    assertThat(item.categoryName()).isEqualTo("Uncategorized");
                    assertThat(item.amount()).isEqualByComparingTo("3.3333");
                });
    }

    @Test
    void monthlyTrendHonorsPartialFirstAndLastMonthBoundaries() {
        var trend = analyticsService.getMonthlyTrend(
                userA, LocalDate.of(2026, 1, 15), LocalDate.of(2026, 2, 1));

        assertThat(trend.dataPoints()).hasSize(2);
        assertThat(trend.dataPoints().get(0).totalIncome()).isEqualByComparingTo("1000.0000");
        assertThat(trend.dataPoints().get(0).totalExpense()).isEqualByComparingTo("23.5333");
        assertThat(trend.dataPoints().get(1).totalExpense()).isEqualByComparingTo("8.0000");
    }

    private UUID insertUser(String prefix) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO users (id, email, password_hash, display_name, currency_preference, created_at, updated_at)
                VALUES (?, ?, 'test-hash', ?, 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, id, prefix + "-" + id + "@example.com", prefix);
        return id;
    }

    private UUID insertAccount(UUID userId, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO accounts (
                    id, user_id, name, type, balance, currency, created_at, updated_at, deleted_at, version
                ) VALUES (?, ?, 'Fixture', 'CHECKING', 0, 'INR', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                          CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, 0)
                """, id, userId, deleted);
        return id;
    }

    private UUID insertCategory(UUID userId, String name, TransactionType type, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO categories (id, user_id, name, type, created_at, updated_at, deleted_at)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                        CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END)
                """, id, userId, name, type.name(), deleted);
        return id;
    }

    private UUID insertTransaction(UUID accountId, UUID categoryId, String description, String amount,
                                   LocalDate date, TransactionType type, boolean deleted) {
        UUID id = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO transactions (
                    id, account_id, category_id, amount, description, transaction_date,
                    transaction_type, created_at, updated_at, deleted_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
                          CASE WHEN ? THEN CURRENT_TIMESTAMP ELSE NULL END, 0)
                """, id, accountId, categoryId, new BigDecimal(amount), description, date, type.name(), deleted);
        return id;
    }
}
