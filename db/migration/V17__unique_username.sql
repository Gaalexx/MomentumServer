CREATE UNIQUE INDEX IF NOT EXISTS users_username_idx ON users(username) WHERE username IS NOT NULL;
