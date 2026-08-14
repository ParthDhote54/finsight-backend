package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.MerchantBreakdownResult;
import com.finsight.finsight_ai.ai.chat.domain.SpendingDeltaExplainerResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
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

class SpendingDeltaExplainerToolTest {

    private final FinancialAnalyticsPort analyticsPort = mock(FinancialAnalyticsPort.class);
    private final ToolMonthParser monthParser = new ToolMonthParser();
    private SpendingDeltaExplainerTool tool;
    private UUID userId;

    @BeforeEach
    void setUp() {
        tool = new SpendingDeltaExplainerTool(analyticsPort, monthParser);
        userId = UUID.randomUUID();
        TenantContext.set(userId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void calculatesDeltaPercentageAndContributors() {
        LocalDate aStart = LocalDate.of(2026, 6, 1);
        LocalDate aEnd = LocalDate.of(2026, 6, 30);
        LocalDate bStart = LocalDate.of(2026, 7, 1);
        LocalDate bEnd = LocalDate.of(2026, 7, 31);

        MerchantBreakdownResult breakdownA = new MerchantBreakdownResult(
                userId, "food", aStart, aEnd, "INR",
                List.of(new MerchantBreakdownResult.MerchantItem("Swiggy", new BigDecimal("100.00"), 2L, new BigDecimal("100.00")))
        );

        MerchantBreakdownResult breakdownB = new MerchantBreakdownResult(
                userId, "food", bStart, bEnd, "INR",
                List.of(
                        new MerchantBreakdownResult.MerchantItem("Swiggy", new BigDecimal("150.00"), 3L, new BigDecimal("60.00")),
                        new MerchantBreakdownResult.MerchantItem("Starbucks", new BigDecimal("100.00"), 2L, new BigDecimal("40.00"))
                )
        );

        when(analyticsPort.getMerchantBreakdown(eq(userId), eq("food"), eq(aStart), eq(aEnd)))
                .thenReturn(breakdownA);
        when(analyticsPort.getMerchantBreakdown(eq(userId), eq("food"), eq(bStart), eq(bEnd)))
                .thenReturn(breakdownB);

        var result = tool.execute(Map.of(
                "categoryOrGroup", "food",
                "periodA", "2026-06",
                "periodB", "2026-07",
                "limit", 5
        ));

        assertThat(result.data()).isInstanceOf(SpendingDeltaExplainerResult.class);
        SpendingDeltaExplainerResult explainer = (SpendingDeltaExplainerResult) result.data();

        assertThat(explainer.periodATotal()).isEqualByComparingTo("100.00");
        assertThat(explainer.periodBTotal()).isEqualByComparingTo("250.00");
        assertThat(explainer.delta()).isEqualByComparingTo("150.00");
        assertThat(explainer.percentageChange()).isEqualByComparingTo("150.00");
        assertThat(explainer.topContributors()).hasSize(2);
        assertThat(result.numericEvidence()).isNotEmpty();
    }
}
