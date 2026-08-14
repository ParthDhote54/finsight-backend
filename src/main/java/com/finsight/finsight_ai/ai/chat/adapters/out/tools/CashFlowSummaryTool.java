package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.CashflowResult;
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
public class CashFlowSummaryTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public CashFlowSummaryTool(FinancialAnalyticsPort financialAnalyticsPort, ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "cashflow_summary";
    }

    @Override
    public String description() {
        return "Calculates total income, total expense, and net cash flow for a period or month. Use this for income vs spending or cashflow queries.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "month", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Calendar month in YYYY-MM format")
                ),
                "required", List.of(),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        ToolMonthParser.MonthRange month = monthParser.optionalMonthOrAllTime(args.get("month"), "month");

        CashflowResult result = financialAnalyticsPort.getCashflowSummary(
                userId, month.startDate(), month.endDate());
        return FinanceToolResult.of(result, List.of(
                NumericEvidence.monetary(name(), "totalIncome", result.totalIncome(), result.currency()),
                NumericEvidence.monetary(name(), "totalExpense", result.totalExpense(), result.currency()),
                NumericEvidence.monetary(name(), "netCashFlow", result.netCashFlow(), result.currency())));
    }
}
