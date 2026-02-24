ALTER TABLE IF EXISTS public.users DROP COLUMN IF EXISTS name;

ALTER TABLE public.users
ALTER COLUMN password TYPE character varying(300) COLLATE pg_catalog."default";

ALTER TABLE IF EXISTS public.users
    ADD COLUMN has_premium boolean NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS public.users
    ADD COLUMN registered_at date NOT NULL;

ALTER TABLE IF EXISTS public.users
    ADD COLUMN telephone character varying(20);

ALTER TABLE IF EXISTS public.users
    ADD COLUMN email character varying(255) NOT NULL;

ALTER TABLE IF EXISTS public.users
    ADD COLUMN username character varying(50);