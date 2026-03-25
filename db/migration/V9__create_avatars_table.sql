CREATE TABLE IF NOT EXISTS public.avatars
(
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    mime_type character varying(50) NOT NULL,
    status character varying(20) NOT NULL DEFAULT 'UPLOADING',
    is_active boolean NOT NULL DEFAULT false,
    object_key character varying(1024) NOT NULL,
    size_bytes bigint NOT NULL,
    CONSTRAINT avatars_pkey PRIMARY KEY (id),
    CONSTRAINT avatars_object_key_uq UNIQUE (object_key),
    CONSTRAINT avatars_user_id_fk FOREIGN KEY (user_id)
    REFERENCES public.users (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE
);

CREATE UNIQUE INDEX one_active_avatar_per_user
    ON avatars(user_id)
    WHERE is_active = true;

ALTER TABLE public.avatars OWNER to app;