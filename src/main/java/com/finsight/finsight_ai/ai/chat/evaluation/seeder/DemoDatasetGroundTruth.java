package com.finsight.finsight_ai.ai.chat.evaluation.seeder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Ground truth facts for the Phase 5C deterministic demo dataset.
 * Used by evaluation runners and test suites to verify system behavior.
 */
public final class DemoDatasetGroundTruth {

    private DemoDatasetGroundTruth() {
        // Utility class
    }

    public static final UUID DEMO_USER_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    public static final String DEMO_USER_EMAIL = "demo.user@finsight.ai";
    public static final String CURRENCY = "INR";

    public static final UUID PRIMARY_CHECKING_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-222222222201");
    public static final UUID SAVINGS_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-222222222202");
    public static final UUID CREDIT_CARD_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-222222222203");
    public static final UUID DELETED_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-222222222204");
    public static final UUID USD_ACCOUNT_ID = UUID.fromString("11111111-1111-1111-1111-222222222205");

    public static final UUID FOREIGN_USER_ID = UUID.fromString("22222222-2222-2222-2222-222222222222");
    public static final UUID FOREIGN_ACCOUNT_ID = UUID.fromString("22222222-2222-2222-2222-222222222201");

    // Special Adversarial Fixture IDs
    public static final UUID SOFT_DELETED_TX_ID = UUID.fromString("11111111-1111-1111-3333-000000000099");
    public static final UUID UNCATEGORIZED_TX_ID = UUID.fromString("11111111-1111-1111-3333-000000000098");
    public static final UUID USD_TX_ID = UUID.fromString("11111111-1111-1111-3333-000000000097");
    public static final UUID FOREIGN_USER_TX_ID = UUID.fromString("22222222-2222-2222-3333-000000000001");

    // Flagship Query Parameters & Ground Truth Facts
    public static final String FLAGSHIP_CATEGORY = "Dining Out & Cafes";
    public static final String FLAGSHIP_PERIOD_MAY = "2026-05";
    public static final String FLAGSHIP_PERIOD_JUNE = "2026-06";
    public static final LocalDate MAY_START = LocalDate.of(2026, 5, 1);
    public static final LocalDate MAY_END = LocalDate.of(2026, 5, 31);
    public static final LocalDate JUNE_START = LocalDate.of(2026, 6, 1);
    public static final LocalDate JUNE_END = LocalDate.of(2026, 6, 30);

    public static final BigDecimal MAY_FOOD_TOTAL = new BigDecimal("8500.00");
    public static final BigDecimal JUNE_FOOD_TOTAL = new BigDecimal("25450.00");
    public static final BigDecimal FOOD_DELTA = new BigDecimal("16950.00");
    public static final BigDecimal FOOD_PCT_CHANGE = new BigDecimal("199.41"); // 16950 / 8500 * 100

    public static final String TOP_CONTRIBUTOR_MERCHANT = "Swiggy";
    public static final BigDecimal SWIGGY_MAY_AMOUNT = new BigDecimal("3200.00");
    public static final BigDecimal SWIGGY_JUNE_AMOUNT = new BigDecimal("10800.00");
    public static final BigDecimal SWIGGY_DELTA = new BigDecimal("7600.00");

    public static final String SECOND_CONTRIBUTOR_MERCHANT = "Zomato";
    public static final BigDecimal ZOMATO_MAY_AMOUNT = new BigDecimal("2800.00");
    public static final BigDecimal ZOMATO_JUNE_AMOUNT = new BigDecimal("8450.00");
    public static final BigDecimal ZOMATO_DELTA = new BigDecimal("5650.00");

    public static final List<String> RECURRING_SUBSCRIPTION_MERCHANTS = List.of(
            "Netflix", "Spotify", "Airtel Broadband", "Cult.fit Gym", "Prestige Rentals"
    );

    public static final List<String> NON_RECURRING_NOISE_MERCHANTS = List.of(
            "Amazon", "Zara", "Myntra", "PVR Cinemas", "Apollo Pharmacy"
    );

    public static final BigDecimal STARTING_BALANCE_PRIMARY_CHECKING = new BigDecimal("50000.00");
}
