ALTER TABLE IF EXISTS public.sessions
ALTER COLUMN expires_at DROP NOT NULL;

ALTER TABLE IF EXISTS public.sessions
ALTER COLUMN expires_at SET DEFAULT null;

ALTER TABLE IF EXISTS public.sessions
ALTER COLUMN revoked_at SET DEFAULT null;