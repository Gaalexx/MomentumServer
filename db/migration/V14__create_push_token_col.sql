ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS push_token character varying(256);