package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.TransactionSummaryResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RecentTransactionsTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;

    public RecentTransactionsTool(FinancialAnalyticsPort financialAnalyticsPort) {
        this.financialAnalyticsPort = financialAnalyticsPort;
    }

    @Override
    public String name() {
        return "recent_transactions";
    }

    @Override
    public String description() {
        return "Retrieves a list of recent transactions, optionally filtered by merchant name. Use this when the user asks to see recent purchases or transactions at a specific store.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "merchant", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "Optional merchant name filter"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 20,
                                "description", "Max results to return (default 5, max 20)")
                ),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        String merchant = ToolArguments.optionalString(args, "merchant", 100);
        int limit = ToolArguments.optionalInteger(args, "limit", 5, 1, 20);

        List<TransactionSummaryResult> results = financialAnalyticsPort.getRecentTransactions(userId, merchant, limit);
        List<NumericEvidence> evidence = new java.util.ArrayList<>();
        java.util.Set<UUID> transactionIds = new java.util.LinkedHashSet<>();
        for (int index = 0; index < results.size(); index++) {
            TransactionSummaryResult transaction = results.get(index);
            transactionIds.add(transaction.transactionId());
            evidence.add(NumericEvidence.monetary(
                    name(), "transactions[" + index + "].amount",
                    transaction.amount(), transaction.currency()));
        }
        evidence.add(NumericEvidence.count(name(), "transactionCount", results.size()));
        return new FinanceToolResult(results, evidence, transactionIds);
    }
}
