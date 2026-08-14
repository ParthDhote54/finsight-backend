package com.finsight.finsight_ai.ai.chat.application.validation;

import com.finsight.finsight_ai.ai.chat.domain.NumericEvidence;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class NumericConsistencyValidatorTest {

    private final NumericConsistencyValidator validator = new NumericConsistencyValidator();

    @Test
    void matchesExactScaledMoneyAndRejectsNearbyValue() {
        var evidence = List.of(NumericEvidence.monetary(
                "spend_by_category", "totalAmount", new BigDecimal("1200.00"), "INR"));

        assertThat(validator.validate("You spent ₹1,200.00.", evidence).valid()).isTrue();
        assertThat(validator.validate("You spent 1200.", evidence).valid()).isTrue();
        assertThat(validator.validate("You spent ₹1,200.01.", evidence).valid()).isFalse();
    }

    @Test
    void retainsZeroAndRequiresMatchingCurrency() {
        var evidence = List.of(NumericEvidence.monetary(
                "spend_by_category", "totalAmount", BigDecimal.ZERO, "INR"));

        assertThat(validator.validate("Your total is ₹0.", evidence).valid()).isTrue();
        assertThat(validator.validate("Your total is $0.", evidence).valid()).isFalse();
        assertThat(validator.validate("Your total is ₹0.", List.of()).valid()).isFalse();
    }

    @Test
    void distinguishesMoneyPercentCountsAndRanks() {
        var evidence = List.of(
                NumericEvidence.monetary("compare_months", "difference", new BigDecimal("10"), "INR"),
                NumericEvidence.percentage("compare_months", "percentageChange", new BigDecimal("-10.00")),
                NumericEvidence.count("top_merchants", "transactionCount", 3),
                NumericEvidence.rank("top_merchants", "rank", 1));

        assertThat(validator.validate(
                "A 10% decrease across 3 transactions; rank #1 and ₹10 difference.", evidence).valid())
                .isTrue();
        assertThat(validator.validate("The change was 3%.", evidence).valid()).isFalse();
        assertThat(validator.validate("You spent ₹3.", evidence).valid()).isFalse();
    }

    @Test
    void masksDatesYearsUuidsAndListNumbersButNotCurrencyYearLikeAmounts() {
        var evidence = List.of(NumericEvidence.monetary(
                "sum_by_transaction_ids", "totalAmount", new BigDecimal("2026"), "INR"));
        String id = "c0a80101-1234-4abc-8def-1234567890ab";

        assertThat(validator.validate(
                "1. In July 2026 (2026-07-01), transaction " + id + " totals ₹2,026.", evidence).valid())
                .isTrue();
        assertThat(validator.validate("The unsupported total is 2027.", evidence).valid()).isFalse();
    }

    @Test
    void ignoresInlineLookupListNumbersButStillValidatesAmountsAndCounts() {
        var evidence = List.of(
                NumericEvidence.monetary("recent_transactions", "transactions[0].amount", new BigDecimal("1999.0000"), "INR"),
                NumericEvidence.monetary("recent_transactions", "transactions[1].amount", new BigDecimal("1999.0000"), "INR"),
                NumericEvidence.count("recent_transactions", "transactionCount", 2)
        );

        assertThat(validator.validate(
                "I found 2 transactions: 1) Amazon for INR 1,999 on June 12th; "
                        + "2) AMAZON PAY INDIA for INR 1,999 on June 12th.",
                evidence).valid()).isTrue();
        assertThat(validator.validate("Transaction 1 was INR 2,000.", evidence).valid()).isFalse();
    }

    @Test
    void structuralListNumbersDoNotHideUnsupportedMoneyOrPercentClaims() {
        var evidence = List.of(
                NumericEvidence.monetary("spending_delta_explainer", "periodBTotal", new BigDecimal("10500"), "INR"),
                NumericEvidence.monetary("spending_delta_explainer", "periodATotal", new BigDecimal("2000"), "INR"),
                NumericEvidence.monetary("spending_delta_explainer", "delta", new BigDecimal("8500"), "INR"),
                NumericEvidence.percentage("spending_delta_explainer", "percentageChange", new BigDecimal("425.00"))
        );

        assertThat(validator.validate("""
                1. June spend was INR 10,500
                2. May spend was INR 2,000
                3. Increase was INR 8,500
                """, evidence).valid()).isTrue();

        assertThat(validator.validate("""
                1. June spend was INR 10,500
                2. May spend was INR 2,000
                3. Increase was INR 8,500
                4. Another INR 777 came from miscellaneous purchases
                """, evidence).valid()).isFalse();

        assertThat(validator.validate("""
                1. Spending increased by 425%
                2. Delivery frequency increased by 37%
                """, evidence).valid()).isFalse();
    }

    @Test
    void decimalsAreNotMistakenForStructuralListMarkers() {
        var evidence = List.of(
                NumericEvidence.percentage("spending_delta_explainer", "percentageChange", new BigDecimal("1.50"))
        );

        assertThat(validator.validate("Spending increased by 1.5%.", evidence).valid()).isTrue();
        assertThat(validator.validate("The unsupported ratio was 1.25.", evidence).valid()).isFalse();
        assertThat(validator.validate("2.0 transactions were involved.", evidence).valid()).isFalse();
    }

    @Test
    void validatesSignDirectionForPercentages() {
        var evidence = List.of(
                NumericEvidence.percentage("compare_months", "percentageChange", new BigDecimal("15.00")),
                NumericEvidence.percentage("compare_months", "percentageChange", new BigDecimal("-20.00"))
        );

        assertThat(validator.validate("Your spending had a 15% increase.", evidence).valid()).isTrue();
        assertThat(validator.validate("Your spending had a 20% decrease.", evidence).valid()).isTrue();
        assertThat(validator.validate("Your spending had a 15% decrease.", evidence).valid()).isFalse();
    }

    @Test
    void validatesMultipleAmountsAndRejectsOneWrongAmount() {
        var evidence = List.of(
                NumericEvidence.monetary("spending_delta_explainer", "periodATotal", new BigDecimal("100.00"), "INR"),
                NumericEvidence.monetary("spending_delta_explainer", "periodBTotal", new BigDecimal("250.00"), "INR"),
                NumericEvidence.monetary("spending_delta_explainer", "delta", new BigDecimal("150.00"), "INR")
        );

        assertThat(validator.validate("Period A was ₹100, Period B was ₹250, delta is ₹150.", evidence).valid()).isTrue();
        assertThat(validator.validate("Period A was ₹100, Period B was ₹250, delta is ₹200.", evidence).valid()).isFalse();
    }
}
