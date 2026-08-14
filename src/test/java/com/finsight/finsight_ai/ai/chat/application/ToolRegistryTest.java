package com.finsight.finsight_ai.ai.chat.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.finsight.finsight_ai.ai.chat.adapters.out.tools.*;
import com.finsight.finsight_ai.ai.chat.domain.ToolCallRequest;
import com.finsight.finsight_ai.ai.chat.domain.ToolExecutionResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ToolRegistryTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private final FinancialAnalyticsPort analytics = mock(FinancialAnalyticsPort.class);
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        ToolMonthParser monthParser = new ToolMonthParser();
        registry = new ToolRegistry(List.of(
                new SpendByCategoryTool(analytics, monthParser),
                new SpendByMerchantGroupTool(analytics, monthParser),
                new CompareMonthsTool(analytics, monthParser),
                new MerchantBreakdownTool(analytics, monthParser),
                new TopMerchantsTool(analytics, monthParser),
                new RecentTransactionsTool(analytics),
                new SumByTransactionIdsTool(analytics),
                new SubscriptionDetectorTool(analytics),
                new SavingsProjectorTool(analytics)
        ), objectMapper);
        TenantContext.set(UUID.randomUUID());
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void everyToolSpecContainsStandardValidJsonSchemaWithoutTenantInput() throws Exception {
        assertThat(registry.getToolSpecs()).hasSize(9).allSatisfy(spec -> {
            JsonNode schema;
            try {
                schema = objectMapper.readTree(spec.jsonSchemaParameters());
            } catch (Exception exception) {
                throw new AssertionError(exception);
            }
            assertThat(schema.path("type").asText()).isEqualTo("object");
            assertThat(schema.path("additionalProperties").asBoolean()).isFalse();
            assertThat(spec.jsonSchemaParameters()).doesNotContain("userId", "user_id");
        });
    }

    @Test
    void unknownToolReturnsStructuredModelCorrectableError() throws Exception {
        ToolExecutionResult result = registry.execute(
                new ToolCallRequest("call-1", "does_not_exist", Map.of()));

        assertThat(result.status()).isEqualTo(ToolExecutionResult.Status.MODEL_CORRECTABLE_ERROR);
        assertThat(result.errorCode()).isEqualTo("UNKNOWN_TOOL");
        assertThat(objectMapper.readTree(result.responseJson()).path("code").asText())
                .isEqualTo("UNKNOWN_TOOL");
        verifyNoInteractions(analytics);
    }

    @Test
    void malformedArgumentsAreNotReinterpretedAsEmptyArguments() {
        ToolExecutionResult result = registry.execute(new ToolCallRequest(
                "call-1", "top_merchants", Map.of(), "{broken", "invalid JSON"));

        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENTS");
        verifyNoInteractions(analytics);
    }

    @Test
    void missingWrongTypeAndUnexpectedArgumentsFailBeforeExecution() {
        assertThat(execute("spend_by_category", Map.of("month", "2026-08")).errorCode())
                .isEqualTo("MISSING_REQUIRED_ARGUMENT");
        assertThat(execute("top_merchants", Map.of("limit", "five")).errorCode())
                .isEqualTo("INVALID_ARGUMENT_TYPE");
        assertThat(execute("recent_transactions", Map.of("userId", UUID.randomUUID().toString())).errorCode())
                .isEqualTo("UNKNOWN_ARGUMENT");
        verifyNoInteractions(analytics);
    }

    @Test
    void invalidLimitAndDuplicateIdsReturnStructuredArgumentErrors() {
        UUID id = UUID.randomUUID();

        assertThat(execute("top_merchants", Map.of("limit", 21)).errorCode())
                .isEqualTo("INVALID_ARGUMENT_VALUE");
        assertThat(execute("sum_by_transaction_ids",
                Map.of("transaction_ids", List.of(id.toString(), id.toString()))).errorCode())
                .isEqualTo("INVALID_ARGUMENT_VALUE");
        verifyNoInteractions(analytics);
    }

    @Test
    void validArgumentsExecuteExactlyOnce() {
        UUID tenant = TenantContext.require();
        when(analytics.getRecentTransactions(tenant, "Cafe", 5)).thenReturn(List.of());

        ToolExecutionResult result = execute(
                "recent_transactions", Map.of("merchant", " Cafe ", "limit", 5));

        assertThat(result.successful()).isTrue();
        assertThat(result.responseJson()).isEqualTo("[]");
    }

    @Test
    void providerStyleIntegralFloatingPointIntegerArgumentsAreAccepted() {
        UUID tenant = TenantContext.require();
        when(analytics.detectSubscriptions(tenant, 10)).thenReturn(
                new com.finsight.finsight_ai.ai.chat.domain.SubscriptionDetectionResult(
                        tenant, "INR", List.of()));

        ToolExecutionResult result = execute("subscription_detector", Map.of("limit", 10.0d));

        assertThat(result.successful()).isTrue();
    }

    @Test
    void fractionalFloatingPointIntegerArgumentsAreRejected() {
        ToolExecutionResult result = execute("subscription_detector", Map.of("limit", 10.5d));

        assertThat(result.errorCode()).isEqualTo("INVALID_ARGUMENT_TYPE");
    }

    @Test
    void floatingIntegerNormalizationStillRejectsNonFiniteOverflowStringAndBounds() {
        assertThat(execute("subscription_detector", Map.of("limit", Double.NaN)).errorCode())
                .isEqualTo("INVALID_ARGUMENT_TYPE");
        assertThat(execute("subscription_detector", Map.of("limit", Double.POSITIVE_INFINITY)).errorCode())
                .isEqualTo("INVALID_ARGUMENT_TYPE");
        assertThat(execute("subscription_detector", Map.of("limit", Double.MAX_VALUE)).errorCode())
                .isEqualTo("INVALID_ARGUMENT_VALUE");
        assertThat(execute("subscription_detector", Map.of("limit", "6.0")).errorCode())
                .isEqualTo("INVALID_ARGUMENT_TYPE");
        assertThat(execute("subscription_detector", Map.of("limit", 51.0d)).errorCode())
                .isEqualTo("INVALID_ARGUMENT_VALUE");
    }

    private ToolExecutionResult execute(String toolName, Map<String, Object> arguments) {
        return registry.execute(new ToolCallRequest("call-1", toolName, arguments));
    }
}
