package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.MerchantBreakdownResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class MerchantBreakdownTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public MerchantBreakdownTool(FinancialAnalyticsPort financialAnalyticsPort,
                                 ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "merchant_breakdown";
    }

    @Override
    public String description() {
        return "Breaks down spending within a category or merchant group by individual merchants. Use this when the user asks where within a category they spent money.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "categoryOrGroup", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "Category label"),
                        "month", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Calendar month in YYYY-MM format")
                ),
                "required", List.of("categoryOrGroup"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        String categoryOrGroup = ToolArguments.requiredString(args, "categoryOrGroup", 100);
        ToolMonthParser.MonthRange range = monthParser.optionalMonthOrAllTime(args.get("month"), "month");

        MerchantBreakdownResult result = financialAnalyticsPort.getMerchantBreakdown(
                userId, categoryOrGroup, range.startDate(), range.endDate());
        List<NumericEvidence> evidence = new java.util.ArrayList<>();
        for (int index = 0; index < result.items().size(); index++) {
            MerchantBreakdownResult.MerchantItem item = result.items().get(index);
            String path = "items[" + index + "]";
            evidence.add(NumericEvidence.monetary(
                    name(), path + ".totalAmount", item.totalAmount(), result.currency()));
            evidence.add(NumericEvidence.count(
                    name(), path + ".transactionCount", item.transactionCount()));
            evidence.add(NumericEvidence.percentage(
                    name(), path + ".percentageOfTotal", item.percentageOfTotal()));
        }
        return FinanceToolResult.of(result, evidence);
    }
}
