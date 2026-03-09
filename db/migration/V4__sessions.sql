CREATE TABLE IF NOT EXISTS public.sessions
(
    session_id uuid NOT NULL,
    user_id uuid NOT NULL,
    refrash_token_hash character varying(72) NOT NULL,
    expires_at timestamp with time zone NOT NULL,
    revoked_at timestamp with time zone NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    device_info character varying(255) NOT NULL,
    PRIMARY KEY (session_id),
    CONSTRAINT user_id_fk FOREIGN KEY (user_id)
        REFERENCES public.users (id) MATCH SIMPLE
        ON UPDATE NO ACTION
        ON DELETE NO ACTION
);

ALTER TABLE IF EXISTS public.sessions
    OWNER to app;