package com.finsight.finsight_ai.ai.chat.evaluation.seeder;

import com.finsight.finsight_ai.TestcontainersConfiguration;
import com.finsight.finsight_ai.ai.chat.domain.MerchantBreakdownResult;
import com.finsight.finsight_ai.ai.chat.domain.MonthComparisonResult;
import com.finsight.finsight_ai.ai.chat.domain.SpendingDeltaExplainerResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static com.finsight.finsight_ai.ai.chat.evaluation.seeder.DemoDatasetGroundTruth.*;
import static org.assertj.core.api.Assertions.assertThat;

import com.finsight.finsight_ai.ai.AIGateway;
import com.finsight.finsight_ai.ai.chat.ports.out.ChatModelPort;
import com.finsight.finsight_ai.ai.chat.ports.out.EmbeddingPort;
import org.springframework.boot.test.mock.mockito.MockBean;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@Transactional
class EvaluationDemoDataSeederTest {

    @MockBean
    private AIGateway aiGateway;

    @MockBean
    private ChatModelPort chatModelPort;

    @MockBean
    private EmbeddingPort embeddingPort;

    @Autowired
    private EvaluationDemoDataSeeder seeder;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FinancialAnalyticsPort financialAnalyticsPort;

    @BeforeEach
    void setUp() {
        seeder.seed();
    }

    @Test
    @DisplayName("5C.1: Seeder is idempotent and safe to rerun")
    void seederIdempotencyTest() {
        Integer initialTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id IN (SELECT id FROM accounts WHERE user_id = ?)",
                Integer.class,
                DEMO_USER_ID
        );
        assertThat(initialTxCount).isGreaterThan(100);

        // Run seeder a second time
        EvaluationDemoDataSeeder.SeedSummary secondRun = seeder.seed();

        assertThat(secondRun.alreadySeeded()).isTrue();
        assertThat(secondRun.transactionsSeeded()).isEqualTo(0);

        Integer finalTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE account_id IN (SELECT id FROM accounts WHERE user_id = ?)",
                Integer.class,
                DEMO_USER_ID
        );
        assertThat(finalTxCount).isEqualTo(initialTxCount);
    }

    @Test
    @DisplayName("5C.2: Flagship spending increase ground truth for May vs June Food Spending")
    void flagshipGroundTruthTest() {
        try {
            TenantContext.set(DEMO_USER_ID);

            // 1. Merchant breakdown for May 2026
            MerchantBreakdownResult mayBreakdown = financialAnalyticsPort.getMerchantBreakdown(
                    DEMO_USER_ID, FLAGSHIP_CATEGORY, MAY_START, MAY_END);
            BigDecimal mayTotal = mayBreakdown.items().stream()
                    .map(MerchantBreakdownResult.MerchantItem::totalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(mayTotal).isEqualByComparingTo(MAY_FOOD_TOTAL);

            // 2. Merchant breakdown for June 2026
            MerchantBreakdownResult juneBreakdown = financialAnalyticsPort.getMerchantBreakdown(
                    DEMO_USER_ID, FLAGSHIP_CATEGORY, JUNE_START, JUNE_END);
            BigDecimal juneTotal = juneBreakdown.items().stream()
                    .map(MerchantBreakdownResult.MerchantItem::totalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(juneTotal).isEqualByComparingTo(JUNE_FOOD_TOTAL);

            // 3. Top contributor analysis
            assertThat(juneBreakdown.items()).isNotEmpty();
            assertThat(juneBreakdown.items().get(0).merchantName()).isEqualTo(TOP_CONTRIBUTOR_MERCHANT);
            assertThat(juneBreakdown.items().get(0).totalAmount()).isEqualByComparingTo(SWIGGY_JUNE_AMOUNT);

            assertThat(juneBreakdown.items().get(1).merchantName()).isEqualTo(SECOND_CONTRIBUTOR_MERCHANT);
            assertThat(juneBreakdown.items().get(1).totalAmount()).isEqualByComparingTo(ZOMATO_JUNE_AMOUNT);

        } finally {
            TenantContext.clear();
        }
    }

    @Test
    @DisplayName("5C.3: Soft-deleted, Uncategorized, and USD fixtures exist and are properly scoped")
    void specialFixturesTest() {
        // Soft deleted transaction should be filtered out by default view
        Integer deletedTxInView = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions WHERE id = ? AND deleted_at IS NULL",
                Integer.class,
                SOFT_DELETED_TX_ID
        );
        assertThat(deletedTxInView).isEqualTo(0);

        // Uncategorized transaction exists
        Boolean uncategorizedExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transactions WHERE id = ? AND category_id IS NULL)",
                Boolean.class,
                UNCATEGORIZED_TX_ID
        );
        assertThat(uncategorizedExists).isTrue();

        // USD transaction exists
        Boolean usdExists = jdbcTemplate.queryForObject(
                "SELECT EXISTS(SELECT 1 FROM transactions WHERE id = ? AND account_id = ?)",
                Boolean.class,
                USD_TX_ID, USD_ACCOUNT_ID
        );
        assertThat(usdExists).isTrue();
    }

    @Test
    @DisplayName("5C.4: Tenant isolation between Demo User and Foreign User")
    void tenantIsolationTest() {
        Integer foreignTxCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transactions t JOIN accounts a ON t.account_id = a.id WHERE a.user_id = ?",
                Integer.class,
                FOREIGN_USER_ID
        );
        assertThat(foreignTxCount).isEqualTo(1);

        try {
            TenantContext.set(DEMO_USER_ID);
            MerchantBreakdownResult demoBreakdown = financialAnalyticsPort.getMerchantBreakdown(
                    DEMO_USER_ID, FLAGSHIP_CATEGORY, JUNE_START, JUNE_END);
            // Should not include Luxury Dining (which belongs to foreign user)
            assertThat(demoBreakdown.items())
                    .noneMatch(item -> item.merchantName().equalsIgnoreCase("Luxury Dining"));
        } finally {
            TenantContext.clear();
        }
    }
}
