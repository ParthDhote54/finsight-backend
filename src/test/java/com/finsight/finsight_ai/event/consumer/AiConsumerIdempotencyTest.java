package com.finsight.finsight_ai.event.consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiConsumerIdempotencyTest {

    // Instantiate with null dependencies as we are only testing pure text normalization logic
    private final AiProcessingConsumer consumer = new AiProcessingConsumer(null, null, null, null, null, null);

    @Test
    @DisplayName("Description normalization collapses multi-spaces, trims, and lowercases text")
    void testDescriptionNormalization_CollapsesWhitespaceAndCase() {
        String rawInput1 = "   AMAZON   PRIME   SUBSCRIPTION  ";
        String rawInput2 = "amazon prime subscription";
        String rawInput3 = "Amazon Prime Subscription";

        String normalized1 = consumer.normalizeDescription(rawInput1);
        String normalized2 = consumer.normalizeDescription(rawInput2);
        String normalized3 = consumer.normalizeDescription(rawInput3);

        assertEquals("amazon prime subscription", normalized1);
        assertEquals(normalized1, normalized2);
        assertEquals(normalized2, normalized3);
    }
}