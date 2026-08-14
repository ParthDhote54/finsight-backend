package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.MerchantGroupSpendResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class SpendByMerchantGroupTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public SpendByMerchantGroupTool(FinancialAnalyticsPort financialAnalyticsPort,
                                    ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "spend_by_merchant_group";
    }

    @Override
    public String description() {
        return "Calculates total expense for a category label within a calendar month. This version does not expand semantic merchant groups.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "merchantGroup", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "A category label"),
                        "month", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Calendar month in YYYY-MM format")
                ),
                "required", java.util.List.of("merchantGroup"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        // Enforce strict TenantContext retrieval (LLM cannot pass userId)
        UUID userId = TenantContext.require();

        String merchantGroup = ToolArguments.requiredString(args, "merchantGroup", 100);
        ToolMonthParser.MonthRange month = monthParser.optionalMonthOrAllTime(args.get("month"), "month");

        MerchantGroupSpendResult result = financialAnalyticsPort.getSpendByMerchantGroup(
                userId, merchantGroup, month.startDate(), month.endDate());

        return FinanceToolResult.of(result, java.util.List.of(
                NumericEvidence.monetary(name(), "totalAmount", result.totalAmount(), result.currency()),
                NumericEvidence.count(name(), "transactionCount", result.transactionCount())));
    }
}
