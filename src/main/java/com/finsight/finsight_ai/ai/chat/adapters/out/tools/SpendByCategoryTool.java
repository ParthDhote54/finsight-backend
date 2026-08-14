package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.CategorySpendResult;
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
public class SpendByCategoryTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public SpendByCategoryTool(FinancialAnalyticsPort financialAnalyticsPort,
                               ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "spend_by_category";
    }

    @Override
    public String description() {
        return "Calculates total spending for a specific category within a given month. Use this when the user asks about spending totals for a category.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "category", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "The spending category, e.g. food"),
                        "month", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Calendar month in YYYY-MM format")
                ),
                "required", List.of("category"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        String category = ToolArguments.requiredString(args, "category", 100);
        ToolMonthParser.MonthRange month = monthParser.optionalMonthOrAllTime(args.get("month"), "month");

        CategorySpendResult result = financialAnalyticsPort.getSpendByCategory(
                userId, category, month.startDate(), month.endDate());
        List<NumericEvidence> evidence = new java.util.ArrayList<>();
        evidence.add(NumericEvidence.monetary(name(), "totalAmount", result.totalAmount(), result.currency()));
        evidence.add(NumericEvidence.count(name(), "transactionCount", result.transactionCount()));
        if (result.largestCategoryAmount() != null) {
            evidence.add(NumericEvidence.monetary(name(), "largestCategoryAmount", result.largestCategoryAmount(), result.currency()));
        }
        return FinanceToolResult.of(result, evidence);
    }
}
