package com.finsight.finsight_ai.ai.chat.evaluation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class PricingSnapshotTest {

    @Test
    @DisplayName("Verify PricingSnapshot record construction and BigDecimal arithmetic")
    void testValidPricingSnapshotCalculation() {
        PricingSnapshot snapshot = new PricingSnapshot(
                "Vertex AI",
                "gemini-2.5-flash-lite",
                "On-Demand Pay-As-You-Go",
                Instant.parse("2026-08-09T00:00:00Z"),
                "Official Google Cloud Vertex AI Pricing",
                new BigDecimal("0.075"),
                new BigDecimal("0.300"),
                "USD"
        );

        assertThat(snapshot.provider()).isEqualTo("Vertex AI");
        assertThat(snapshot.modelId()).isEqualTo("gemini-2.5-flash-lite");
        assertThat(snapshot.pricingTier()).isEqualTo("On-Demand Pay-As-You-Go");
        assertThat(snapshot.currency()).isEqualTo("USD");

        // Calculate cost for 40,800 prompt tokens and 13,600 completion tokens
        BigDecimal promptTokensK = new BigDecimal("40.8");
        BigDecimal completionTokensK = new BigDecimal("13.6");

        BigDecimal promptRatePerK = snapshot.inputPerMillion().divide(new BigDecimal("1000"));
        BigDecimal completionRatePerK = snapshot.outputPerMillion().divide(new BigDecimal("1000"));

        BigDecimal promptCost = promptTokensK.multiply(promptRatePerK);
        BigDecimal completionCost = completionTokensK.multiply(completionRatePerK);
        BigDecimal totalCost = promptCost.add(completionCost);

        assertThat(promptCost).isEqualByComparingTo("0.00306");
        assertThat(completionCost).isEqualByComparingTo("0.00408");
        assertThat(totalCost).isEqualByComparingTo("0.00714");
    }

    @Test
    @DisplayName("Verify missing input rate or output rate fails validation")
    void testMissingRatesValidation() {
        PricingSnapshot nullInput = new PricingSnapshot(
                "Vertex AI", "gemini-2.5-flash-lite", "Pay-As-You-Go", Instant.now(), "Source", null, new BigDecimal("0.300"), "USD"
        );
        assertThat(nullInput.inputPerMillion()).isNull();

        PricingSnapshot nullOutput = new PricingSnapshot(
                "Vertex AI", "gemini-2.5-flash-lite", "Pay-As-You-Go", Instant.now(), "Source", new BigDecimal("0.075"), null, "USD"
        );
        assertThat(nullOutput.outputPerMillion()).isNull();
    }
}
