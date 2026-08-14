package com.finsight.finsight_ai.ai.chat.application;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class Phase4EvaluationDatasetTest {

    private IntentClassifier intentClassifier;
    private FinancialQueryDetector financialQueryDetector;

    @BeforeEach
    void setUp() {
        intentClassifier = new IntentClassifier();
        financialQueryDetector = new FinancialQueryDetector();
    }

    record TestCase(
            String query,
            IntentBucket expectedIntent,
            boolean requiresEvidence
    ) {}

    static Stream<Arguments> evaluationDataset() {
        return Stream.of(
                // 1. AGGREGATE (1-5)
                Arguments.of(new TestCase("How much did I spend on food in July?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("What was my total spend last month?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Total spending on groceries in 2026-06", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("How much money went to utilities?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Show sum of dining out expenses", IntentBucket.AGGREGATE, true)),

                // 2. LOOKUP (6-10)
                Arguments.of(new TestCase("Show my last 5 Amazon transactions", IntentBucket.LOOKUP, true)),
                Arguments.of(new TestCase("List recent payments to Uber", IntentBucket.LOOKUP, true)),
                Arguments.of(new TestCase("Find transactions from Starbucks", IntentBucket.LOOKUP, true)),
                Arguments.of(new TestCase("Show recent transactions in June", IntentBucket.LOOKUP, true)),
                Arguments.of(new TestCase("Get transaction history for Swiggy", IntentBucket.LOOKUP, true)),

                // 3. COMPARISON (11-15)
                Arguments.of(new TestCase("Compare June and July food spending", IntentBucket.COMPARISON, true)),
                Arguments.of(new TestCase("Did I spend more in July than June?", IntentBucket.COMPARISON, true)),
                Arguments.of(new TestCase("Compare spending between 2026-05 and 2026-06", IntentBucket.COMPARISON, true)),
                Arguments.of(new TestCase("Was shopping higher this month?", IntentBucket.COMPARISON, true)),
                Arguments.of(new TestCase("Difference in transport costs between months", IntentBucket.COMPARISON, true)),

                // 4. EXPLANATION (16-20)
                Arguments.of(new TestCase("Why did my food spending increase this month?", IntentBucket.EXPLANATION, true)),
                Arguments.of(new TestCase("Explain why my expenses went up in July", IntentBucket.EXPLANATION, true)),
                Arguments.of(new TestCase("Why is my total spend higher than last month?", IntentBucket.EXPLANATION, true)),
                Arguments.of(new TestCase("What drove the increase in coffee spending?", IntentBucket.EXPLANATION, true)),
                Arguments.of(new TestCase("Reason for high shopping bills in June", IntentBucket.EXPLANATION, true)),

                // 5. MERCHANT (21-25)
                Arguments.of(new TestCase("Top merchants by spending", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("How much did I spend at Starbucks?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Merchant breakdown for food", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Show spending for merchant group coffee", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("List top 10 merchants", IntentBucket.AGGREGATE, true)),

                // 6. SUBSCRIPTION (26-30)
                Arguments.of(new TestCase("What subscriptions do I appear to have?", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("List my recurring monthly charges", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("How much do my subscriptions cost each month?", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Detect recurring bills in my account", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Find active streaming subscriptions", IntentBucket.RECOMMENDATION, true)),

                // 7. PROJECTION (31-35)
                Arguments.of(new TestCase("If I cut food delivery by 20%, how much could I save in six months?", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("How much will I save if I reduce coffee spending by 50%?", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Project savings for 10% reduction in shopping over 1 year", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("If I save ₹1000 per month, what is my 6 month projection?", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Estimate annual savings from cutting subscriptions by 30%", IntentBucket.RECOMMENDATION, true)),

                // 8. RECONCILIATION (36-40)
                Arguments.of(new TestCase("Why doesn't this account balance reconcile?", IntentBucket.EXPLANATION, true)),
                Arguments.of(new TestCase("Reconcile account balance with transaction history", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Check balance discrepancy for checking account", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Verify account starting and ending balance", IntentBucket.RECOMMENDATION, true)),
                Arguments.of(new TestCase("Is my account balance reconciled?", IntentBucket.RECOMMENDATION, true)),

                // 9. SEMANTIC FUZZY (41-45)
                Arguments.of(new TestCase("What did I spend on late-night junk food?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("How much went to weekend wellness trips?", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Show spending for date-night dinners", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Fuzzy search for coffee shop vibes", IntentBucket.AGGREGATE, true)),
                Arguments.of(new TestCase("Expenses related to similar fitness apps", IntentBucket.AGGREGATE, true)),

                // 10. GENERAL / NON-FINANCIAL (46-50)
                Arguments.of(new TestCase("Hello FinSight", IntentBucket.GENERAL, false)),
                Arguments.of(new TestCase("What can you do?", IntentBucket.GENERAL, false)),
                Arguments.of(new TestCase("How does budget tracking work?", IntentBucket.GENERAL, false)),
                Arguments.of(new TestCase("Thank you for your help", IntentBucket.GENERAL, false)),
                Arguments.of(new TestCase("What is a credit score?", IntentBucket.GENERAL, false))
        );
    }

    @ParameterizedTest
    @MethodSource("evaluationDataset")
    @DisplayName("Evaluate 50-Question Dataset against Intent & Evidence Requirements")
    void evaluateQuestionDataset(TestCase testCase) {
        IntentBucket actualIntent = intentClassifier.classify(testCase.query());
        boolean actualEvidenceReq = financialQueryDetector.requiresToolEvidence(testCase.query());

        assertThat(actualIntent)
                .as("Query: %s", testCase.query())
                .isEqualTo(testCase.expectedIntent());

        assertThat(actualEvidenceReq)
                .as("Query: %s", testCase.query())
                .isEqualTo(testCase.requiresEvidence());
    }
}
