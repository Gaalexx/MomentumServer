CREATE TABLE IF NOT EXISTS public.verification_codes(
    identifier character varying(255) NOT NULL,
    code_type character varying(10)  NOT NULL,  --email/phone
    code character varying(10)  NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    expires_at timestamp with time zone NOT NULL,
    attempts integer NOT NULL DEFAULT 0,
    CONSTRAINT verification_codes_pkey PRIMARY KEY (identifier)
);

CREATE INDEX IF NOT EXISTS idx_verification_codes_expires_at
    ON public.verification_codes (expires_at);