package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.MonthComparisonResult;
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
public class CompareMonthsTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public CompareMonthsTool(FinancialAnalyticsPort financialAnalyticsPort,
                             ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "compare_months";
    }

    @Override
    public String description() {
        return "Compares total spending between two calendar months. Use this when the user asks for spending comparison between months.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "month1", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "First calendar month in YYYY-MM format"),
                        "month2", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Second calendar month in YYYY-MM format")
                ),
                "required", List.of("month1", "month2"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();

        ToolMonthParser.MonthRange month1 = monthParser.requiredMonth(args.get("month1"), "month1");
        ToolMonthParser.MonthRange month2 = monthParser.requiredMonth(args.get("month2"), "month2");

        MonthComparisonResult result = financialAnalyticsPort.compareSpendingPeriods(
                userId, month1.startDate(), month1.endDate(), month2.startDate(), month2.endDate());
        return FinanceToolResult.of(result, List.of(
                NumericEvidence.monetary(
                        name(), "period1Total", result.period1Total(), result.currency()),
                NumericEvidence.monetary(
                        name(), "period2Total", result.period2Total(), result.currency()),
                NumericEvidence.monetary(
                        name(), "absoluteDifference", result.absoluteDifference(), result.currency()),
                NumericEvidence.percentage(name(), "percentageChange", result.percentageChange())));
    }
}
