CREATE TABLE accounts(
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(50) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0.000,
    currency VARCHAR(3) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    deleted_at TIMESTAMP WITH TIME ZONE,

    CONSTRAINT fk_accounts_user_id FOREIGN KEY(user_id) REFERENCES users(id) on DELETE RESTRICT
);

CREATE INDEX idx_accounts_user_id ON accounts(user_id);


