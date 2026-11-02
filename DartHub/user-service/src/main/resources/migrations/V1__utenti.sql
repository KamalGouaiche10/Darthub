-- @version 1
ALTER TABLE users ADD COLUMN approval_status TEXT NOT NULL DEFAULT 'APPROVATO';

-- @version 2
ALTER TABLE users ADD COLUMN first_name TEXT;
ALTER TABLE users ADD COLUMN last_name TEXT;
