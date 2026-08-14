package com.finsight.finsight_ai.ai.chat.application;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Deterministic merchant normalization registry.
 * Maps raw transaction descriptions like "RAZORPAY*STARBUCKS PVT LTD"
 * to normalized merchant labels and merchant groups.
 */
@Component
public class MerchantNormalizer {

    public record NormalizedMerchant(String rawDescription, String normalizedMerchant, String merchantGroup) {}

    private record MerchantRule(String keyword, String normalizedMerchant, String merchantGroup, MatchType matchType) {}

    public enum MatchType {
        EXACT,
        PREFIX,
        CONTAINS
    }

    private static final List<MerchantRule> RULES = List.of(
            // Coffee
            new MerchantRule("starbucks", "starbucks", "coffee", MatchType.CONTAINS),
            new MerchantRule("blue tokai", "blue tokai", "coffee", MatchType.CONTAINS),
            new MerchantRule("cafe coffee day", "cafe coffee day", "coffee", MatchType.CONTAINS),
            new MerchantRule("ccd", "cafe coffee day", "coffee", MatchType.CONTAINS),

            // Food Delivery
            new MerchantRule("swiggy", "swiggy", "food_delivery", MatchType.CONTAINS),
            new MerchantRule("zomato", "zomato", "food_delivery", MatchType.CONTAINS),

            // Ride Hailing
            new MerchantRule("uber", "uber", "ride_hailing", MatchType.CONTAINS),
            new MerchantRule("ola", "ola", "ride_hailing", MatchType.CONTAINS),

            // Shopping
            new MerchantRule("amazon", "amazon", "shopping", MatchType.CONTAINS),
            new MerchantRule("flipkart", "flipkart", "shopping", MatchType.CONTAINS)
    );

    public NormalizedMerchant normalize(String rawDescription) {
        if (rawDescription == null || rawDescription.isBlank()) {
            return new NormalizedMerchant("", "unknown", "uncategorized");
        }

        String text = rawDescription.trim().toLowerCase(Locale.ROOT);

        for (MerchantRule rule : RULES) {
            boolean matched = switch (rule.matchType()) {
                case EXACT -> text.equals(rule.keyword());
                case PREFIX -> text.startsWith(rule.keyword());
                case CONTAINS -> text.contains(rule.keyword());
            };

            if (matched) {
                return new NormalizedMerchant(rawDescription, rule.normalizedMerchant(), rule.merchantGroup());
            }
        }

        // Default fallback: strip noise and return cleaned description
        String cleaned = text.replaceAll("[^a-z0-9\\s]", " ").replaceAll("\\s+", " ").trim();
        return new NormalizedMerchant(rawDescription, cleaned.isEmpty() ? "unknown" : cleaned, "uncategorized");
    }
}
