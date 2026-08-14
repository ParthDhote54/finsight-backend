package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.SubscriptionDetectionResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class SubscriptionDetectorTool implements FinanceTool {

    private final FinancialAnalyticsPort analyticsPort;

    public SubscriptionDetectorTool(FinancialAnalyticsPort analyticsPort) {
        this.analyticsPort = analyticsPort;
    }

    @Override
    public String name() {
        return "subscription_detector";
    }

    @Override
    public String description() {
        return "Detects recurring subscription spending patterns (monthly, weekly, etc.) from user transaction history.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 50,
                                "description", "Maximum subscriptions to return (default 10)")
                ),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        int limit = ToolArguments.optionalInteger(args, "limit", 10, 1, 50);

        SubscriptionDetectionResult result = analyticsPort.detectSubscriptions(userId, limit);

        List<NumericEvidence> evidence = new ArrayList<>();
        List<UUID> transactionIds = new ArrayList<>();

        for (SubscriptionDetectionResult.SubscriptionItem item : result.subscriptions()) {
            evidence.add(NumericEvidence.monetary(name(), "averageAmount", item.averageAmount(), result.currency()));
            evidence.add(NumericEvidence.count(name(), "occurrenceCount", item.occurrenceCount()));
            if (item.transactionIds() != null) {
                transactionIds.addAll(item.transactionIds());
            }
        }

        return FinanceToolResult.of(result, evidence, Set.copyOf(transactionIds));
    }
}
