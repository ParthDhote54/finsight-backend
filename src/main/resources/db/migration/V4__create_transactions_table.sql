CREATE TABLE transactions(
    id UUID PRIMARY KEY,
    account_id UUID NOT NULL,
    category_id UUID,
    amount NUMERIC(19, 4) NOT NULL,
    description VARCHAR(255),
    transaction_date DATE NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,


    --Foreign key1 : the Strict Parent(Account)
    CONSTRAINT fk_transactions_account_id FOREIGN KEY(account_id) REFERENCES accounts (id) ON DELETE RESTRICT,


    --Foreign key2 : The Optional Tag(Category)
    CONSTRAINT fk_transactions_category_id FOREIGN KEY(category_id) REFERENCES categories (id) ON DELETE RESTRICT
);


--Index 1 : The Security FireWall (Fetching a user's ledger)
CREATE INDEX idx_transactions_account_id ON transactions(account_id);
CREATE INDEX idx_transaction_category_id ON transactions(category_id);