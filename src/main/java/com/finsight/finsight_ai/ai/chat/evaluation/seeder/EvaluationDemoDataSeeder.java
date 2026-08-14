package com.finsight.finsight_ai.ai.chat.evaluation.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.Timestamp;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

import static com.finsight.finsight_ai.ai.chat.evaluation.seeder.DemoDatasetGroundTruth.*;

@Component
public class EvaluationDemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(EvaluationDemoDataSeeder.class);

    private final JdbcTemplate jdbcTemplate;

    public EvaluationDemoDataSeeder(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SeedSummary(
            int usersCreated,
            int accountsCreated,
            int categoriesCreated,
            int transactionsSeeded,
            boolean alreadySeeded
    ) {}

    @Transactional
    public SeedSummary seed() {
        log.info("Starting EvaluationDemoDataSeeder execution...");

        boolean userExists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)",
                        Boolean.class,
                        DEMO_USER_ID
                )
        );

        int usersCount = 0;
        int accountsCount = 0;
        int categoriesCount = 0;
        int transactionsCount = 0;

        // 1. Seed Demo Users
        if (!userExists) {
            insertUser(DEMO_USER_ID, DEMO_USER_EMAIL, "Demo User", CURRENCY);
            usersCount++;
        }

        boolean foreignUserExists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM users WHERE id = ?)",
                        Boolean.class,
                        FOREIGN_USER_ID
                )
        );

        if (!foreignUserExists) {
            insertUser(FOREIGN_USER_ID, "foreign.user@finsight.ai", "Foreign User", "INR");
            usersCount++;
        }

        // 2. Seed Accounts
        accountsCount += seedAccount(PRIMARY_CHECKING_ACCOUNT_ID, DEMO_USER_ID, "HDFC Salary Account", "CHECKING", CURRENCY, BigDecimal.ZERO, null);
        accountsCount += seedAccount(SAVINGS_ACCOUNT_ID, DEMO_USER_ID, "ICICI Savings Account", "SAVINGS", CURRENCY, new BigDecimal("450000.00"), null);
        accountsCount += seedAccount(CREDIT_CARD_ACCOUNT_ID, DEMO_USER_ID, "Axis Credit Card", "CREDIT_CARD", CURRENCY, new BigDecimal("-32400.00"), null);
        accountsCount += seedAccount(DELETED_ACCOUNT_ID, DEMO_USER_ID, "Closed Old Account", "CHECKING", CURRENCY, BigDecimal.ZERO, Timestamp.from(Instant.now()));
        accountsCount += seedAccount(USD_ACCOUNT_ID, DEMO_USER_ID, "USD Travel Account", "CHECKING", "USD", new BigDecimal("1250.00"), null);
        accountsCount += seedAccount(FOREIGN_ACCOUNT_ID, FOREIGN_USER_ID, "Foreign Checking", "CHECKING", CURRENCY, new BigDecimal("100000.00"), null);

        // 3. Resolve Category IDs
        Map<String, UUID> categoryMap = ensureMasterCategories();
        categoriesCount = categoryMap.size();

        // 4. Seed Transactions (~350 items)
        transactionsCount = seedTransactions(categoryMap);

        // 5. Update Primary Checking Account Balance for Balance Reconciliation Ground Truth
        BigDecimal totalIncome = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE account_id = ? AND transaction_type = 'INCOME' AND deleted_at IS NULL",
                BigDecimal.class,
                PRIMARY_CHECKING_ACCOUNT_ID
        );
        BigDecimal totalExpense = jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount), 0) FROM transactions WHERE account_id = ? AND transaction_type = 'EXPENSE' AND deleted_at IS NULL",
                BigDecimal.class,
                PRIMARY_CHECKING_ACCOUNT_ID
        );

        BigDecimal actualEndingBalance = STARTING_BALANCE_PRIMARY_CHECKING
                .add(totalIncome != null ? totalIncome : BigDecimal.ZERO)
                .subtract(totalExpense != null ? totalExpense : BigDecimal.ZERO);

        jdbcTemplate.update(
                "UPDATE accounts SET balance = ? WHERE id = ?",
                actualEndingBalance,
                PRIMARY_CHECKING_ACCOUNT_ID
        );

        log.info("EvaluationDemoDataSeeder completed successfully. Users: {}, Accounts: {}, Categories: {}, Transactions: {}",
                usersCount, accountsCount, categoriesCount, transactionsCount);

        return new SeedSummary(usersCount, accountsCount, categoriesCount, transactionsCount, userExists);
    }

    private void insertUser(UUID userId, String email, String name, String currency) {
        jdbcTemplate.update(
                """
                INSERT INTO users (id, email, password_hash, display_name, currency_preference, created_at, updated_at)
                VALUES (?, ?, '$2a$10$e846h9r9t7.P0s.1s8x8eeZgW0g9W0g9W0g9W0g9W0g9W0g9W0g9W', ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (id) DO NOTHING
                """,
                userId, email, name, currency
        );
    }

    private int seedAccount(UUID accountId, UUID userId, String name, String type, String currency, BigDecimal initialBalance, Timestamp deletedAt) {
        boolean exists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM accounts WHERE id = ?)", Boolean.class, accountId)
        );
        if (!exists) {
            jdbcTemplate.update(
                    """
                    INSERT INTO accounts (id, user_id, name, type, balance, currency, deleted_at, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    accountId, userId, name, type, initialBalance, currency, deletedAt
            );
            return 1;
        }
        return 0;
    }

    private Map<String, UUID> ensureMasterCategories() {
        Map<String, UUID> map = new HashMap<>();

        List<Object[]> rows = jdbcTemplate.query(
                "SELECT name, id FROM categories WHERE user_id IS NULL AND deleted_at IS NULL",
                (rs, rowNum) -> new Object[]{rs.getString("name"), UUID.fromString(rs.getString("id"))}
        );

        for (Object[] r : rows) {
            map.put((String) r[0], (UUID) r[1]);
        }

        // Add any missing essential category names if migration didn't include them
        String[][] missing = {
                {"Food & Dining", "EXPENSE"},
                {"Pet Care", "EXPENSE"}
        };

        for (String[] item : missing) {
            String catName = item[0];
            String type = item[1];
            if (!map.containsKey(catName)) {
                UUID newId = UUID.randomUUID();
                jdbcTemplate.update(
                        "INSERT INTO categories (id, user_id, name, type, created_at, updated_at) VALUES (?, NULL, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                        newId, catName, type
                );
                map.put(catName, newId);
            }
        }

        return map;
    }

    private int seedTransactions(Map<String, UUID> categories) {
        UUID foodCategory = categories.getOrDefault("Dining Out & Cafes", categories.get("Food & Dining"));
        UUID groceryCategory = categories.get("Groceries");
        UUID salaryCategory = categories.get("Salary");
        UUID rentCategory = categories.get("Rent & Housing");
        UUID subscriptionCategory = categories.get("Subscriptions & Streaming");
        UUID internetCategory = categories.get("Internet & Phone");
        UUID transportCategory = categories.get("Transportation & Rideshare");
        UUID shoppingCategory = categories.get("Shopping & Apparel");
        UUID utilityCategory = categories.get("Utilities & Bills");
        UUID healthCategory = categories.get("Health & Fitness");
        UUID softwareCategory = categories.get("Software & Digital Services");
        UUID entertainmentCategory = categories.get("Entertainment & Media");
        UUID petCategory = categories.get("Pet Care");
        UUID miscCategory = categories.get("Other / Miscellaneous");

        int txIndex = 1;
        int count = 0;

        // --- Monthly Salary & Subscriptions across 6 months (Jan 2026 - Jun 2026) ---
        for (int month = 1; month <= 6; month++) {
            LocalDate monthStart = LocalDate.of(2026, month, 1);

            // 1. Salary (Income ₹1,50,000)
            UUID salaryId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(salaryId, PRIMARY_CHECKING_ACCOUNT_ID, salaryCategory, new BigDecimal("150000.00"),
                    "Monthly Salary Credit - Tech Corp Ltd", monthStart, "INCOME", null, null);

            // 2. Rent (Expense ₹35,000) - Recurring
            UUID rentId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(rentId, PRIMARY_CHECKING_ACCOUNT_ID, rentCategory, new BigDecimal("35000.00"),
                    "Prestige Rentals Rent Payment", monthStart.plusDays(4), "EXPENSE", "Prestige Rentals", "Rent");

            // 3. Netflix Subscription (Expense ₹649.00) - Recurring
            UUID netflixId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(netflixId, CREDIT_CARD_ACCOUNT_ID, subscriptionCategory, new BigDecimal("649.00"),
                    month % 2 == 0 ? "Netflix.com Subscription" : "Netflix Monthly Plan", monthStart.plusDays(14), "EXPENSE", "Netflix", "Streaming");

            // 4. Spotify Subscription (Expense ₹179.00) - Recurring
            UUID spotifyId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(spotifyId, CREDIT_CARD_ACCOUNT_ID, subscriptionCategory, new BigDecimal("179.00"),
                    "Spotify Premium Subscription", monthStart.plusDays(17), "EXPENSE", "Spotify", "Music");

            // 5. Airtel Broadband (Expense ₹1,499.00) - Recurring
            UUID airtelId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(airtelId, PRIMARY_CHECKING_ACCOUNT_ID, internetCategory, new BigDecimal("1499.00"),
                    "Airtel Broadband Fiber Bill", monthStart.plusDays(9), "EXPENSE", "Airtel Broadband", "Utilities");

            // 6. Cult.fit Gym (Expense ₹2,500.00) - Recurring
            UUID gymId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(gymId, CREDIT_CARD_ACCOUNT_ID, healthCategory, new BigDecimal("2500.00"),
                    "Cult.fit Gym Monthly Pass", monthStart, "EXPENSE", "Cult.fit Gym", "Fitness");

            // 7. Utilities (BESCOM Electricity)
            UUID utilId = generateTxUuid(txIndex++);
            count += insertTxIfMissing(utilId, PRIMARY_CHECKING_ACCOUNT_ID, utilityCategory, new BigDecimal("2400.00"),
                    "BESCOM Electricity Bill Payment", monthStart.plusDays(11), "EXPENSE", "BESCOM", "Utilities");
        }

        // --- FLAGSHIP GROUND TRUTH: May 2026 vs June 2026 Food Spending ---

        // MAY 2026 FOOD SPENDING (Total: ₹8,500.00)
        // Swiggy (4 txs = ₹3,200.00)
        count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, new BigDecimal("800.00"), "Swiggy Order #101", LocalDate.of(2026, 5, 3), "EXPENSE", "Swiggy", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, new BigDecimal("950.00"), "Swiggy Dinner", LocalDate.of(2026, 5, 10), "EXPENSE", "Swiggy", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, new BigDecimal("650.00"), "Swiggy Orders Lunch", LocalDate.of(2026, 5, 18), "EXPENSE", "Swiggy", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, new BigDecimal("800.00"), "Swiggy Dineout", LocalDate.of(2026, 5, 25), "EXPENSE", "Swiggy", "Food Delivery");

        // Zomato (3 txs = ₹2,800.00)
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("1100.00"), "Zomato Ltd Delivery", LocalDate.of(2026, 5, 6), "EXPENSE", "Zomato", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("850.00"), "Zomato Weekend Treat", LocalDate.of(2026, 5, 15), "EXPENSE", "Zomato", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("850.00"), "ZOMATO Food Order", LocalDate.of(2026, 5, 28), "EXPENSE", "Zomato", "Food Delivery");

        // Starbucks (3 txs = ₹1,500.00)
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("500.00"), "Starbucks Coffee", LocalDate.of(2026, 5, 5), "EXPENSE", "Starbucks", "Cafes");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("500.00"), "Starbucks Store #402", LocalDate.of(2026, 5, 14), "EXPENSE", "Starbucks", "Cafes");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("500.00"), "Starbucks Coffee", LocalDate.of(2026, 5, 22), "EXPENSE", "Starbucks", "Cafes");

        // Third Wave Coffee (2 txs = ₹1,000.00)
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("500.00"), "Third Wave Coffee Roasters", LocalDate.of(2026, 5, 8), "EXPENSE", "Third Wave Coffee", "Cafes");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("500.00"), "Third Wave Coffee Roasters", LocalDate.of(2026, 5, 24), "EXPENSE", "Third Wave Coffee", "Cafes");

        // JUNE 2026 FOOD SPENDING (Total: ₹24,250.00)
        // Swiggy (12 txs = ₹10,800.00)
        BigDecimal[] swiggyJune = {new BigDecimal("900.00"), new BigDecimal("1100.00"), new BigDecimal("850.00"), new BigDecimal("1200.00"),
                new BigDecimal("750.00"), new BigDecimal("950.00"), new BigDecimal("800.00"), new BigDecimal("1050.00"),
                new BigDecimal("600.00"), new BigDecimal("900.00"), new BigDecimal("1000.00"), new BigDecimal("700.00")};
        for (int i = 0; i < swiggyJune.length; i++) {
            count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, swiggyJune[i],
                    "Swiggy", LocalDate.of(2026, 6, (i * 2) + 2), "EXPENSE", "Swiggy", "Food Delivery");
        }

        // Zomato (9 txs = ₹8,450.00)
        BigDecimal[] zomatoJune = {new BigDecimal("1200.00"), new BigDecimal("950.00"), new BigDecimal("1100.00"), new BigDecimal("800.00"),
                new BigDecimal("1300.00"), new BigDecimal("750.00"), new BigDecimal("900.00"), new BigDecimal("850.00"), new BigDecimal("600.00")};
        for (int i = 0; i < zomatoJune.length; i++) {
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, zomatoJune[i],
                    "Zomato", LocalDate.of(2026, 6, (i * 3) + 1), "EXPENSE", "Zomato", "Food Delivery");
        }

        // Starbucks (5 txs = ₹3,200.00)
        BigDecimal[] sbJune = {new BigDecimal("650.00"), new BigDecimal("650.00"), new BigDecimal("700.00"), new BigDecimal("600.00"), new BigDecimal("600.00")};
        for (int i = 0; i < sbJune.length; i++) {
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, sbJune[i],
                    "Starbucks", LocalDate.of(2026, 6, (i * 5) + 3), "EXPENSE", "Starbucks", "Cafes");
        }

        // Third Wave Coffee (3 txs = ₹1,800.00)
        for (int i = 0; i < 3; i++) {
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("600.00"),
                    "Third Wave Coffee Roasters", LocalDate.of(2026, 6, (i * 8) + 5), "EXPENSE", "Third Wave Coffee", "Cafes");
        }

        // --- GENERAL TRANSACTIONS ACROSS JAN - APR 2026 (~200 txs) ---
        for (int month = 1; month <= 4; month++) {
            // Groceries (Blinkit, Zepto, BigBasket)
            for (int day = 2; day <= 28; day += 5) {
                count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, groceryCategory,
                        new BigDecimal(1200 + (day * 30)), "Blinkit Quick Grocery Delivery", LocalDate.of(2026, month, day), "EXPENSE", "Blinkit", "Groceries");
                count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, groceryCategory,
                        new BigDecimal(850 + (day * 20)), "Zepto Daily Essentials", LocalDate.of(2026, month, Math.min(day + 2, 28)), "EXPENSE", "Zepto", "Groceries");
            }

            // Transport (Uber, Ola)
            for (int day = 1; day <= 28; day += 4) {
                count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, transportCategory,
                        new BigDecimal(250 + (day * 10)), "Uber Trip Rideshare", LocalDate.of(2026, month, day), "EXPENSE", "Uber", "Transport");
            }

            // Shopping Noise (Amazon, Myntra)
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, shoppingCategory,
                    new BigDecimal(1299 + (month * 500)), "Amazon Marketplace Order", LocalDate.of(2026, month, 12), "EXPENSE", "Amazon", "Shopping");
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, shoppingCategory,
                    new BigDecimal(2499 + (month * 200)), "Myntra Fashion Shopping", LocalDate.of(2026, month, 22), "EXPENSE", "Myntra", "Shopping");

            // Food in Jan-Apr (Moderate amounts ~₹6,000-₹7,500 per month)
            count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory,
                    new BigDecimal("1500.00"), "Swiggy Weekend Order", LocalDate.of(2026, month, 7), "EXPENSE", "Swiggy", "Food Delivery");
            count += insertTxIfMissing(generateTxUuid(txIndex++), PRIMARY_CHECKING_ACCOUNT_ID, foodCategory,
                    new BigDecimal("1800.00"), "Zomato Dinner", LocalDate.of(2026, month, 14), "EXPENSE", "Zomato", "Food Delivery");
            count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory,
                    new BigDecimal("1200.00"), "Starbucks Coffee", LocalDate.of(2026, month, 21), "EXPENSE", "Starbucks", "Cafes");
        }

        // --- SEMANTIC & FUZZY QUERY FIXTURES ---
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("450.00"),
                "late-night junk food delivery from midnight diner", LocalDate.of(2026, 6, 12), "EXPENSE", "Midnight Diner", "Food Delivery");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, foodCategory, new BigDecimal("750.00"),
                "weekend coffee with friends at Blue Tokai", LocalDate.of(2026, 6, 14), "EXPENSE", "Blue Tokai Coffee", "Cafes");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, subscriptionCategory, new BigDecimal("1200.00"),
                "annual streaming subscription for documentary channel", LocalDate.of(2026, 5, 20), "EXPENSE", "DocuStream", "Subscriptions");
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, shoppingCategory, new BigDecimal("4999.00"),
                "online shopping for marathon running shoes", LocalDate.of(2026, 6, 8), "EXPENSE", "Nike Store", "Shopping");

        // --- ADVERSARIAL FIXTURES ---

        // 1. Soft-deleted transaction fixture
        count += insertTxIfMissingWithDeletedAt(SOFT_DELETED_TX_ID, PRIMARY_CHECKING_ACCOUNT_ID, foodCategory, new BigDecimal("999.00"),
                "Cancelled Order Refund Test", LocalDate.of(2026, 6, 1), "EXPENSE", "Test Merchant", null, Timestamp.from(Instant.now()));

        // 2. Uncategorized transaction fixture (null category)
        count += insertTxIfMissing(UNCATEGORIZED_TX_ID, PRIMARY_CHECKING_ACCOUNT_ID, null, new BigDecimal("1500.00"),
                "Uncategorized Cash Transfer", LocalDate.of(2026, 6, 10), "EXPENSE", "ATM Withdrawal", "Misc");

        // 3. Mixed Currency fixture (USD account)
        count += insertTxIfMissing(USD_TX_ID, USD_ACCOUNT_ID, softwareCategory, new BigDecimal("150.00"),
                "AWS Web Services Cloud Hosting", LocalDate.of(2026, 6, 1), "EXPENSE", "AWS", "Cloud");

        // 4. Pet Food collision fixture
        count += insertTxIfMissing(generateTxUuid(txIndex++), CREDIT_CARD_ACCOUNT_ID, petCategory, new BigDecimal("2300.00"),
                "Pet Care Superstore Pet Food Purchase", LocalDate.of(2026, 6, 15), "EXPENSE", "Pet Care Superstore", "Pet Food");

        // 5. Foreign Tenant transaction fixture
        count += insertTxIfMissing(FOREIGN_USER_TX_ID, FOREIGN_ACCOUNT_ID, foodCategory, new BigDecimal("50000.00"),
                "Foreign User Private Dining Event", LocalDate.of(2026, 6, 15), "EXPENSE", "Luxury Dining", "Food");

        return count;
    }

    private UUID generateTxUuid(int index) {
        return UUID.fromString(String.format("11111111-1111-1111-4444-%012d", index));
    }

    private int insertTxIfMissing(UUID txId, UUID accountId, UUID categoryId, BigDecimal amount, String description,
                                  LocalDate date, String type, String normalizedMerchant, String merchantGroup) {
        return insertTxIfMissingWithDeletedAt(txId, accountId, categoryId, amount, description, date, type, normalizedMerchant, merchantGroup, null);
    }

    private int insertTxIfMissingWithDeletedAt(UUID txId, UUID accountId, UUID categoryId, BigDecimal amount, String description,
                                               LocalDate date, String type, String normalizedMerchant, String merchantGroup, Timestamp deletedAt) {
        boolean exists = Boolean.TRUE.equals(
                jdbcTemplate.queryForObject("SELECT EXISTS(SELECT 1 FROM transactions WHERE id = ?)", Boolean.class, txId)
        );
        if (!exists) {
            jdbcTemplate.update(
                    """
                    INSERT INTO transactions (id, account_id, category_id, amount, description, transaction_date,
                                              transaction_type, normalized_merchant, merchant_group, deleted_at, version, created_at, updated_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    txId, accountId, categoryId, amount, description, Date.valueOf(date), type, normalizedMerchant, merchantGroup, deletedAt
            );
            return 1;
        }
        return 0;
    }
}
