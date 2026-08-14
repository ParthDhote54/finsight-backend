package com.finsight.finsight_ai.ai.chat.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DialogueState(
        String activeIntent,           // CATEGORY_SPENDING, TOP_MERCHANTS, MONTH_COMPARISON, CASHFLOW_COMPARISON, etc.
        String comparisonMode,         // MONTH_OVER_MONTH, CUSTOM_PERIODS
        String metric,                 // INCOME_VS_SPENDING, SPENDING_ONLY, CATEGORY, MERCHANT
        String period,                 // YYYY-MM
        String periodA,                // YYYY-MM
        String periodB,                // YYYY-MM
        String category,
        String categoryScope,          // UNSPECIFIED, ALL, SPECIFIC
        String merchantGroup,
        String lastToolName,
        String pendingAction,          // SHOW_SPENDING_BY_CATEGORY, COMPARE_MONTHS, CASHFLOW_SUMMARY, TOP_MERCHANTS, etc.
        String pendingSlot,            // PERIOD, PERIOD_A, PERIOD_B, METRIC, CATEGORY, MERCHANT, COMPARISON_TYPE
        String expectedReplyType,      // NONE, YES_NO, PERIOD, PERIOD_PAIR, METRIC, CATEGORY, MERCHANT, CHOICE
        String lastAssistantAct,       // ANSWER, CLARIFICATION, OFFER, CONFIRMATION, ERROR
        String lastAssistantQuestion,
        Map<String, Object> metadata
) {
    public static DialogueState empty() {
        return new DialogueState(null, null, null, null, null, null, null, null, null, null, null, null, "NONE", null, null, Map.of());
    }

    /** Returns a new state with all pending/clarification fields cleared, marking the action as answered. */
    public DialogueState withAnswerState() {
        return new DialogueState(
                activeIntent, comparisonMode, metric, period, periodA, periodB,
                category, categoryScope, merchantGroup, lastToolName,
                null, null, "NONE", "ANSWER", null, metadata == null ? Map.of() : metadata
        );
    }

    /** Returns a cleared ANSWER state from scratch. */
    public static DialogueState answered() {
        return new DialogueState(null, null, null, null, null, null, null, null, null, null, null, null, "NONE", "ANSWER", null, Map.of());
    }

    public DialogueState withPendingAction(String pendingAction, String expectedReplyType, String pendingSlot) {
        return new DialogueState(
                activeIntent, comparisonMode, metric, period, periodA, periodB,
                category, categoryScope, merchantGroup, lastToolName, pendingAction, pendingSlot,
                expectedReplyType, lastAssistantAct, lastAssistantQuestion, metadata
        );
    }
}
