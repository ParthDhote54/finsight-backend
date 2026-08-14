DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM transactions
        WHERE amount <= 0
           OR transaction_type NOT IN ('INCOME', 'EXPENSE', 'TRANSFER')
    ) THEN
        RAISE EXCEPTION 'Cannot install financial constraints: invalid transactions exist';
    END IF;
END $$;

ALTER TABLE transactions
    ADD CONSTRAINT chk_transactions_amount_positive CHECK (amount > 0),
    ADD CONSTRAINT chk_transactions_type_allowed
        CHECK (transaction_type IN ('INCOME', 'EXPENSE', 'TRANSFER'));

UPDATE accounts SET version = 0 WHERE version IS NULL;
UPDATE transactions SET version = 0 WHERE version IS NULL;

ALTER TABLE accounts
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;

ALTER TABLE transactions
    ALTER COLUMN version SET DEFAULT 0,
    ALTER COLUMN version SET NOT NULL;
