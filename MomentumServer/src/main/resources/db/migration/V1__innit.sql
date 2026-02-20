CREATE TABLE IF NOT EXISTS public.users
(
    id uuid NOT NULL,
    name character varying(20) COLLATE pg_catalog."default" NOT NULL,
    password character varying(120) COLLATE pg_catalog."default" NOT NULL,
    CONSTRAINT "Users_pkey" PRIMARY KEY (id)
    );