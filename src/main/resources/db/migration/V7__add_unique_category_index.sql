-- 1. Add the missing deleted_at column to the existing table
ALTER TABLE categories
ADD COLUMN deleted_at TIMESTAMP WITH TIME ZONE;

-- 2. Add the unique index to prevent duplicate user categories
CREATE UNIQUE INDEX idx_unique_user_category
ON categories (user_id, name, type)
WHERE deleted_at IS NULL AND user_id IS NOT NULL;

-- 3. Add the unique index to prevent duplicate system categories
CREATE UNIQUE INDEX idx_unique_system_category
ON categories (name, type)
WHERE deleted_at IS NULL AND user_id IS NULL;