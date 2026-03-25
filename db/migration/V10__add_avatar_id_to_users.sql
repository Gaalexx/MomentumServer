ALTER TABLE IF EXISTS public.users
    ADD COLUMN avatar_id uuid,
    ADD CONSTRAINT fk_users_avatar
    FOREIGN KEY (avatar_id)
    REFERENCES avatars(id)
    ON DELETE SET NULL;

CREATE INDEX idx_users_avatar ON users(avatar_id);