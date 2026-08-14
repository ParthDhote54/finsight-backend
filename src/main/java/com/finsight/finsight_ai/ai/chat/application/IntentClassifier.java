package com.finsight.finsight_ai.ai.chat.application;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Lightweight deterministic rule-based classifier that categorizes user queries
 * into IntentBuckets without LLM calls.
 */
@Component
public class IntentClassifier {

    private static final Pattern EXPLANATION_PATTERN = Pattern.compile(
            "\\b(why|cause|caused|reason|driving|how come|explain|drove)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern RECOMMENDATION_PATTERN = Pattern.compile(
            "\\b(recommend|recommendation|suggest|suggestion|advice|should i|tips|optimize|save|savings|subscription|subscriptions|recurring|project|projection|reduce|cut|reconcil\\w*|discrepancy|verify)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern COMPARISON_PATTERN = Pattern.compile(
            "\\b(compare|comparison|versus|vs|difference|between|increase|decrease|higher|lower|more|less|change|trend)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern AGGREGATE_PATTERN = Pattern.compile(
            "\\b(how much|how many|amount|total|sum|average|count|spend\\w*|spent|spending\\w*|cost\\w*|bills|expense\\w*|breakdown|top|fuzzy|where did.*money go|where.*money went|where money went)\\b", Pattern.CASE_INSENSITIVE);

    private static final Pattern LOOKUP_PATTERN = Pattern.compile(
            "\\b(show|list|recent|last|latest|find|search|transactions|purchases|history|merchants)\\b", Pattern.CASE_INSENSITIVE);

    public IntentBucket classify(String message) {
        if (message == null || message.isBlank()) {
            return IntentBucket.GENERAL;
        }

        String normalized = message.toLowerCase(Locale.ROOT);

        if (EXPLANATION_PATTERN.matcher(normalized).find()) {
            return IntentBucket.EXPLANATION;
        }
        if (RECOMMENDATION_PATTERN.matcher(normalized).find()) {
            return IntentBucket.RECOMMENDATION;
        }
        if (COMPARISON_PATTERN.matcher(normalized).find()) {
            return IntentBucket.COMPARISON;
        }
        if (AGGREGATE_PATTERN.matcher(normalized).find()) {
            return IntentBucket.AGGREGATE;
        }
        if (LOOKUP_PATTERN.matcher(normalized).find()) {
            return IntentBucket.LOOKUP;
        }

        return IntentBucket.GENERAL;
    }
}
