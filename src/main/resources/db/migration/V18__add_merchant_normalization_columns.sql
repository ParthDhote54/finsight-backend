-- Add normalized_merchant and merchant_group columns to transactions table
ALTER TABLE transactions
    ADD COLUMN normalized_merchant VARCHAR(100),
    ADD COLUMN merchant_group VARCHAR(100);

CREATE INDEX idx_transactions_merchant_group ON transactions (merchant_group) WHERE deleted_at IS NULL;
CREATE INDEX idx_transactions_normalized_merchant ON transactions (normalized_merchant) WHERE deleted_at IS NULL;
