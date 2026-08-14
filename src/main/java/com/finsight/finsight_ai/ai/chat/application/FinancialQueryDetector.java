package com.finsight.finsight_ai.ai.chat.application;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Conservative deterministic gate for questions that ask about the authenticated
 * user's financial data. Such questions may not be answered without a successful
 * deterministic finance-tool execution.
 */
@Component
public class FinancialQueryDetector {

    private static final Pattern PERSONAL = Pattern.compile(
            "\\b(my|mine|i|me|our|we)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern FINANCIAL_ENTITY = Pattern.compile(
            "\\b(spend|spent|spending|expense|expenses|income|transaction|transactions|merchant|merchants|category|categories|account|accounts|balance|balances|purchase|purchases|payment|payments|cash ?flow|cost|costs|bill|bills|charge|charges|subscription|subscriptions|saving|savings|projection|reconcile|reconciliation|discrepancy|food|groceries|shopping|coffee|trip|trips|dinner|dinners|wellness|fitness|utilities|apps|app|vibes|history)\\w*\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPERATION = Pattern.compile(
            "\\b(how much|how many|amount|total|sum|average|count|compare|comparison|difference|increase|decrease|change|percent(?:age)?|trend|top|most|last|recent|show|list|find|search|get|detect|project|check|verify|estimate|cut|reduce|why|was|did|went|reason|breakdown|reconcile|reconciliation|related|similar|fuzzy|vibe)\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern EDUCATIONAL = Pattern.compile(
            "\\b(what is|define|explain|how does|meaning of)\\b.*\\b(interest|inflation|stock|bond|mortgage|credit score|budgeting)\\b",
            Pattern.CASE_INSENSITIVE);

    public boolean requiresToolEvidence(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String normalized = message.toLowerCase(Locale.ROOT);
        if (EDUCATIONAL.matcher(normalized).find() && !PERSONAL.matcher(normalized).find()) {
            return false;
        }
        boolean financialEntity = FINANCIAL_ENTITY.matcher(normalized).find();
        boolean personal = PERSONAL.matcher(normalized).find();
        boolean operation = OPERATION.matcher(normalized).find();
        return financialEntity && (personal || operation);
    }
}
