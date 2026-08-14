-- =================================================================
-- MASTER SYSTEM CATEGORIES SEED (WHERE NOT EXISTS)
-- =================================================================

-- INCOME
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Salary', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Salary' AND type = 'INCOME' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Investments', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Investments' AND type = 'INCOME' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Freelance & Side Hustles', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Freelance & Side Hustles' AND type = 'INCOME' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Refunds & Cashbacks', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Refunds & Cashbacks' AND type = 'INCOME' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Gifts & Grants', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Gifts & Grants' AND type = 'INCOME' AND user_id IS NULL);

-- EXPENSES - DIGITAL & ENTERTAINMENT
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Subscriptions & Streaming', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Subscriptions & Streaming' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Entertainment & Media', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Entertainment & Media' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Software & Digital Services', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Software & Digital Services' AND type = 'EXPENSE' AND user_id IS NULL);

-- EXPENSES - ESSENTIALS & LIVING
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Groceries', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Groceries' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Rent & Housing', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Rent & Housing' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Utilities & Bills', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Utilities & Bills' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Internet & Phone', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Internet & Phone' AND type = 'EXPENSE' AND user_id IS NULL);

-- EXPENSES - LIFESTYLE & FOOD
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Dining Out & Cafes', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Dining Out & Cafes' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Shopping & Apparel', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Shopping & Apparel' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Electronics & Gadgets', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Electronics & Gadgets' AND type = 'EXPENSE' AND user_id IS NULL);

-- EXPENSES - TRANSPORTATION & TRAVEL
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Transportation & Rideshare', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Transportation & Rideshare' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Travel & Lodging', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Travel & Lodging' AND type = 'EXPENSE' AND user_id IS NULL);

-- EXPENSES - HEALTH & FINANCIAL OBLIGATIONS
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Health & Fitness', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Health & Fitness' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Insurance & Financial Services', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Insurance & Financial Services' AND type = 'EXPENSE' AND user_id IS NULL);

INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'EMIs & Loan Repayments', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'EMIs & Loan Repayments' AND type = 'EXPENSE' AND user_id IS NULL);

-- EXPENSES - FALLBACK
INSERT INTO categories (id, user_id, name, type, created_at, updated_at)
SELECT gen_random_uuid(), NULL, 'Other / Miscellaneous', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM categories WHERE name = 'Other / Miscellaneous' AND type = 'EXPENSE' AND user_id IS NULL);