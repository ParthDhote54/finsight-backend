package com.finsight.finsight_ai.ai.chat.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MerchantNormalizerTest {

    private final MerchantNormalizer normalizer = new MerchantNormalizer();

    @Test
    void normalizesKnownMerchantsAndGroups() {
        var starbucks = normalizer.normalize("RAZORPAY*STARBUCKS PVT LTD");
        assertThat(starbucks.normalizedMerchant()).isEqualTo("starbucks");
        assertThat(starbucks.merchantGroup()).isEqualTo("coffee");

        var swiggy = normalizer.normalize("SWIGGY IN 12345");
        assertThat(swiggy.normalizedMerchant()).isEqualTo("swiggy");
        assertThat(swiggy.merchantGroup()).isEqualTo("food_delivery");

        var uber = normalizer.normalize("UBER RIDES TRIP #9876");
        assertThat(uber.normalizedMerchant()).isEqualTo("uber");
        assertThat(uber.merchantGroup()).isEqualTo("ride_hailing");
    }

    @Test
    void fallbackNormalizesUnknownMerchantsCleanly() {
        var unknown = normalizer.normalize("LOCAL GROCERY STORE #12");
        assertThat(unknown.normalizedMerchant()).isEqualTo("local grocery store 12");
        assertThat(unknown.merchantGroup()).isEqualTo("uncategorized");
    }
}
