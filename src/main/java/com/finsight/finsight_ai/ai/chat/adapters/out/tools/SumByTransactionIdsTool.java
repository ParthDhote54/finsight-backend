package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.SumByIdsResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Tier-3 semantic-fallback safety valve: accepts an explicit transaction_ids
 * allowlist (never a fuzzy category label) and sums exactly via SQL.
 * This structural guardrail prevents embedding similarity from ever deciding
 * which rows count toward an arithmetic result.
 */
@Component
public class SumByTransactionIdsTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;

    public SumByTransactionIdsTool(FinancialAnalyticsPort financialAnalyticsPort) {
        this.financialAnalyticsPort = financialAnalyticsPort;
    }

    @Override
    public String name() {
        return "sum_by_transaction_ids";
    }

    @Override
    public String description() {
        return "Calculates the exact sum of a specific set of transactions by their IDs. Use this as a safety valve when summing a set of transactions identified by semantic search.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "transaction_ids", Map.of("type", "array",
                                "items", Map.of("type", "string", "format", "uuid"),
                                "minItems", 1, "maxItems", 100, "uniqueItems", true,
                                "description", "List of transaction UUIDs to sum")
                ),
                "required", List.of("transaction_ids"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        List<UUID> transactionIds = ToolArguments.requiredUniqueUuidList(
                args, "transaction_ids", 1, 100);

        SumByIdsResult result = financialAnalyticsPort.sumByTransactionIds(userId, transactionIds);
        return new FinanceToolResult(result, List.of(
                NumericEvidence.monetary(
                        name(), "totalAmount", result.totalAmount(), result.currency()),
                NumericEvidence.count(name(), "transactionCount", result.transactionCount())),
                new java.util.LinkedHashSet<>(result.matchedIds()));
    }
}
