package com.finsight.finsight_ai.ai.chat.adapters.out.tools;

import com.finsight.finsight_ai.ai.chat.domain.MerchantBreakdownResult;
import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import com.finsight.finsight_ai.ai.chat.domain.SpendingDeltaExplainerResult;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceTool;
import com.finsight.finsight_ai.ai.chat.domain.tools.FinanceToolResult;
import com.finsight.finsight_ai.ai.chat.ports.out.FinancialAnalyticsPort;
import com.finsight.finsight_ai.ai.chat.support.TenantContext;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
public class SpendingDeltaExplainerTool implements FinanceTool {

    private final FinancialAnalyticsPort financialAnalyticsPort;
    private final ToolMonthParser monthParser;

    public SpendingDeltaExplainerTool(FinancialAnalyticsPort financialAnalyticsPort,
                                       ToolMonthParser monthParser) {
        this.financialAnalyticsPort = financialAnalyticsPort;
        this.monthParser = monthParser;
    }

    @Override
    public String name() {
        return "spending_delta_explainer";
    }

    @Override
    public String description() {
        return "Explains the spending delta between two periods for a category or merchant group, identifying top contributing merchants.";
    }

    @Override
    public Map<String, Object> jsonSchema() {
        return Map.of(
                "type", "object",
                "properties", Map.of(
                        "categoryOrGroup", Map.of("type", "string", "minLength", 1, "maxLength", 100,
                                "description", "Category label or merchant group"),
                        "periodA", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "First comparison month in YYYY-MM format"),
                        "periodB", Map.of("type", "string", "pattern", "^[0-9]{4}-(0[1-9]|1[0-2])$",
                                "description", "Second comparison month in YYYY-MM format"),
                        "limit", Map.of("type", "integer", "minimum", 1, "maximum", 20,
                                "description", "Maximum top contributors to include")
                ),
                "required", List.of("categoryOrGroup", "periodA", "periodB"),
                "additionalProperties", false
        );
    }

    @Override
    public FinanceToolResult execute(Map<String, Object> args) {
        UUID userId = TenantContext.require();
        String categoryOrGroup = ToolArguments.requiredString(args, "categoryOrGroup", 100);
        ToolMonthParser.MonthRange rangeA = monthParser.requiredMonth(args.get("periodA"), "periodA");
        ToolMonthParser.MonthRange rangeB = monthParser.requiredMonth(args.get("periodB"), "periodB");
        int limit = ToolArguments.optionalInteger(args, "limit", 5, 1, 20);

        MerchantBreakdownResult breakdownA = financialAnalyticsPort.getMerchantBreakdown(
                userId, categoryOrGroup, rangeA.startDate(), rangeA.endDate());
        MerchantBreakdownResult breakdownB = financialAnalyticsPort.getMerchantBreakdown(
                userId, categoryOrGroup, rangeB.startDate(), rangeB.endDate());

        BigDecimal totalA = breakdownA.items().stream()
                .map(MerchantBreakdownResult.MerchantItem::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalB = breakdownB.items().stream()
                .map(MerchantBreakdownResult.MerchantItem::totalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal delta = totalB.subtract(totalA);
        BigDecimal pctChange = totalA.signum() == 0
                ? BigDecimal.ZERO.setScale(2)
                : delta.multiply(BigDecimal.valueOf(100)).divide(totalA, 2, RoundingMode.HALF_UP);

        String currency = breakdownB.currency() != null ? breakdownB.currency() : breakdownA.currency();

        Map<String, BigDecimal> mapA = new LinkedHashMap<>();
        breakdownA.items().forEach(item -> mapA.put(item.merchantName(), item.totalAmount()));

        Map<String, BigDecimal> mapB = new LinkedHashMap<>();
        breakdownB.items().forEach(item -> mapB.put(item.merchantName(), item.totalAmount()));

        Set<String> allMerchants = new LinkedHashSet<>();
        allMerchants.addAll(mapA.keySet());
        allMerchants.addAll(mapB.keySet());

        List<SpendingDeltaExplainerResult.ContributorItem> contributors = new ArrayList<>();
        for (String merchant : allMerchants) {
            BigDecimal amtA = mapA.getOrDefault(merchant, BigDecimal.ZERO);
            BigDecimal amtB = mapB.getOrDefault(merchant, BigDecimal.ZERO);
            BigDecimal itemDelta = amtB.subtract(amtA);
            contributors.add(new SpendingDeltaExplainerResult.ContributorItem(merchant, amtA, amtB, itemDelta));
        }

        contributors.sort(Comparator.comparing((SpendingDeltaExplainerResult.ContributorItem c) -> c.delta().abs()).reversed());
        if (contributors.size() > limit) {
            contributors = contributors.subList(0, limit);
        }

        SpendingDeltaExplainerResult result = new SpendingDeltaExplainerResult(
                userId, categoryOrGroup,
                rangeA.startDate(), rangeA.endDate(),
                rangeB.startDate(), rangeB.endDate(),
                totalA, totalB, delta, pctChange, currency, contributors
        );

        List<NumericEvidence> evidence = new ArrayList<>();
        evidence.add(NumericEvidence.monetary(name(), "periodATotal", totalA, currency));
        evidence.add(NumericEvidence.monetary(name(), "periodBTotal", totalB, currency));
        evidence.add(NumericEvidence.monetary(name(), "delta", delta, currency));
        evidence.add(NumericEvidence.percentage(name(), "percentageChange", pctChange));

        for (int i = 0; i < contributors.size(); i++) {
            var c = contributors.get(i);
            String path = "topContributors[" + i + "]";
            evidence.add(NumericEvidence.monetary(name(), path + ".periodAAmount", c.periodAAmount(), currency));
            evidence.add(NumericEvidence.monetary(name(), path + ".periodBAmount", c.periodBAmount(), currency));
            evidence.add(NumericEvidence.monetary(name(), path + ".delta", c.delta(), currency));
        }

        return FinanceToolResult.of(result, evidence);
    }
}
