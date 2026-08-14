CREATE TABLE categories(

    id UUID PRIMARY KEY,
    user_id UUID,
    name VARCHAR(255) NOT NULL,
    type VARCHAR(20) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_At TIMESTAMP WITH TIME ZONE NOT NULL,

    CONSTRAINT fk_categories_user_id FOREIGN KEY(user_id) REFERENCES users(id) ON DELETE RESTRICT
);

INSERT INTO CATEGORIES(id, user_id, name, type, created_at, updated_At) VALUES
(gen_random_uuid(), NULL, 'Salary', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), NULL, 'Investments', 'INCOME', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), NULL, 'Groceries', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), NULL, 'Rent', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), NULL, 'Utilities', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(gen_random_uuid(), NULL, 'Dining Out', 'EXPENSE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);


