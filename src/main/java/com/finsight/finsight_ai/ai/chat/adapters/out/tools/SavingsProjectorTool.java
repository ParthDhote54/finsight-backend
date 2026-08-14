package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.SavingsProjectionResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class SavingsProjectorTool implements FinanceTool {

    private final FinancialAnalyticsPort analyticsPort;

    public SavingsProjectorTool(FinancialAnalyticsPort analyticsPort) {
        this.analyticsPort = analyticsPort;
    }

    @Override
    public String name() {
        return "savings_projector";
    }

    @Override
    public String description() {
        return "Calculates deterministic savings projections based on percentage or amount spending reduction scenarios over a time horizon.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "categoryOrGroup", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "Category or merchant group name (e.g. food)"),
                        "reductionPercentage", Map.of("type", "number", "minimum", 0, "maximum", 100,
                                "description", "Percentage reduction target (0-100)"),
                        "reductionAmount", Map.of("type", "number", "minimum", 0,
                                "description", "Monetary reduction target per month"),
                        "timeHorizonMonths", Map.of("type", "integer", "minimum", 1, "maximum", 120,
                                "description", "Time horizon in months (default 6)")
                ),
                "required", List.of("categoryOrGroup"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        String categoryOrGroup = ToolArguments.requiredString(args, "categoryOrGroup", 100);
        BigDecimal reductionPercentage = ToolArguments.optionalPositiveDecimal(args, "reductionPercentage");
        BigDecimal reductionAmount = ToolArguments.optionalPositiveDecimal(args, "reductionAmount");
        int timeHorizonMonths = ToolArguments.optionalInteger(args, "timeHorizonMonths", 6, 1, 120);

        SavingsProjectionResult result = analyticsPort.projectSavings(
                userId, categoryOrGroup, reductionPercentage, reductionAmount, timeHorizonMonths);

        return FinanceToolResult.of(result, List.of(
                NumericEvidence.monetary(name(), "baselineMonthlySpend", result.baselineMonthlySpend(), result.currency()),
                NumericEvidence.percentage(name(), "reductionPercentage", result.reductionPercentage()),
                NumericEvidence.monetary(name(), "projectedMonthlySavings", result.projectedMonthlySavings(), result.currency()),
                NumericEvidence.monetary(name(), "totalHorizonSavings", result.totalHorizonSavings(), result.currency())
        ));
    }
}
