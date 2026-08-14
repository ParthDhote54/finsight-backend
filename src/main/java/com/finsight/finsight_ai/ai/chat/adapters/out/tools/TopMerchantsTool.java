package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.TopMerchantsResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

@Component
public class TopMerchantsTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public TopMerchantsTool(FinancialAnalyticsPort financialAnalyticsPort,
                            ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "top_merchants";
    }

    @Override
    public String description() {
        return "Ranks the top merchants by total spending for a given month or overall. Use this whenever the user asks for top merchants, highest spending merchant, merchant ranking, or where they spent most.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "month", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Optional calendar month in YYYY-MM format. Omit this argument for overall or all-time top merchants."),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 20,
                                "description", "Max results (default 5, max 20)")
                ),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();

        int limit = ToolArguments.optionalInteger(args, "limit", 5, 1, 20);
        ToolMonthParser.MonthRange range = monthParser.optionalMonthOrAllTime(args.get("month"), "month");

        TopMerchantsResult result = financialAnalyticsPort.getTopMerchants(
                userId, range.startDate(), range.endDate(), limit);
        java.util.List<NumericEvidence> evidence = new java.util.ArrayList<>();
        for (int index = 0; index < result.merchants().size(); index++) {
            TopMerchantsResult.MerchantRankItem merchant = result.merchants().get(index);
            String path = "merchants[" + index + "]";
            evidence.add(NumericEvidence.rank(name(), path + ".rank", merchant.rank()));
            evidence.add(NumericEvidence.monetary(
                    name(), path + ".totalSpend", merchant.totalSpend(), result.currency()));
            evidence.add(NumericEvidence.count(
                    name(), path + ".transactionCount", merchant.transactionCount()));
        }
        return FinanceToolResult.of(result, evidence);
    }
}
