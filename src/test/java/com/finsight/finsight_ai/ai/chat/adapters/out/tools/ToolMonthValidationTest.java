package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.domain.CategorySpendResult;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.application.ToolRegistry;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolMonthValidationTest {

    private final UUID userId = UUID.randomUUID();
    private final FinancialAnalyticsPort analytics = mock(FinancialAnalyticsPort.class);
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final ToolMonthParser monthParser = new ToolMonthParser(Clock.fixed(
            Instant.parse("2026-08-08T00:00:00Z"), ZoneOffset.UTC));

    @BeforeEach
    void bindTenant() {
        TenantContext.set(userId);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    @Test
    void everyMonthAwareToolRejectsMalformedSuppliedMonthWithoutQuerying() {
        List<ToolInvocation> invocations = List.of(
                new ToolInvocation(new SpendByCategoryTool(analytics, monthParser),
                        Map.of("category", "Dining", "month", "2026-99")),
                new ToolInvocation(new SpendByMerchantGroupTool(analytics, monthParser),
                        Map.of("merchantGroup", "Dining", "month", "2026-99")),
                new ToolInvocation(new MerchantBreakdownTool(analytics, monthParser),
                        Map.of("categoryOrGroup", "Dining", "month", "2026-99")),
                new ToolInvocation(new TopMerchantsTool(analytics, monthParser),
                        Map.of("month", "2026-99")),
                new ToolInvocation(new CompareMonthsTool(analytics, monthParser),
                        Map.of("month1", "2026-01", "month2", "2026-99"))
        );

        invocations.forEach(invocation -> assertThat(execute(invocation.tool(), invocation.arguments()))
                .contains("\"status\":\"ERROR\"")
                .contains("\"code\":\"INVALID_MONTH_FORMAT\"")
                .contains("must use YYYY-MM"));

        verifyNoInteractions(analytics);
    }

    @Test
    void missingRequiredComparisonMonthIsAValidationFailure() {
        FinanceTool tool = new CompareMonthsTool(analytics, monthParser);

        assertThat(execute(tool, Map.of("month1", "2026-01")))
                .contains("MISSING_REQUIRED_ARGUMENT");
        verifyNoInteractions(analytics);
    }

    @Test
    void absentOptionalMonthUsesTheEntireClockMonth() {
        CategorySpendResult expected = new CategorySpendResult(
                userId, "Dining", new BigDecimal("12.3400"), 1L,
                "INR",
                LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31));
        when(analytics.getSpendByCategory(
                userId, "Dining", LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31)))
                .thenReturn(expected);

        FinanceTool tool = new SpendByCategoryTool(analytics, monthParser);
        String result = execute(tool, Map.of("category", "Dining"));

        assertThat(result).contains("12.3400");
        verify(analytics).getSpendByCategory(
                userId, "Dining", LocalDate.of(2000, 1, 1), LocalDate.of(2099, 12, 31));
    }

    private record ToolInvocation(FinanceTool tool, Map<String, Object> arguments) {
    }

    private String execute(FinanceTool tool, Map<String, Object> arguments) {
        return new ToolRegistry(List.of(tool), objectMapper)
                .execute(new ToolCallRequest("test-call", tool.name(), arguments))
                .responseJson();
    }
}
