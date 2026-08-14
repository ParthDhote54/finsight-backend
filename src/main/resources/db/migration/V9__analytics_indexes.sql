-- V2__analytics_indexes.sql
-- Composite partial index to support the Analytics read-model queries.
-- Filters out soft-deleted records at the index level to prevent them from ever hitting memory.

CREATE INDEX idx_transactions_account_date
ON transactions (account_id, transaction_date)
WHERE deleted_at IS NULL;