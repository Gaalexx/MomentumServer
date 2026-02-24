ALTER TABLE IF EXISTS public.users DROP COLUMN IF EXISTS name;

ALTER TABLE IF EXISTS public.users
ALTER COLUMN password TYPE character varying(300);

ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS has_premium boolean NOT NULL DEFAULT false;

ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS registered_at date NOT NULL DEFAULT CURRENT_DATE;

ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS telephone character varying(20);

ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS email character varying(255) NOT NULL DEFAULT '';

ALTER TABLE IF EXISTS public.users
    ADD COLUMN IF NOT EXISTS username character varying(50);