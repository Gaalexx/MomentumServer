CREATE TABLE IF NOT EXISTS public.posts
(
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    created_at timestamp with time zone NOT NULL DEFAULT now(),
    text character varying(120),
    in_use boolean NOT NULL DEFAULT true,
    CONSTRAINT posts_pkey PRIMARY KEY (id),
    CONSTRAINT posts_user_id_fk FOREIGN KEY (user_id)
    REFERENCES public.users (id)
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
    );

CREATE INDEX IF NOT EXISTS idx_posts_user_created_at
    ON public.posts (user_id, created_at DESC);

ALTER TABLE public.posts OWNER TO app;

CREATE TABLE IF NOT EXISTS public.media
(
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    post_id uuid,
    media_type character varying(16) NOT NULL,
    mime_type character varying(150) NOT NULL,
    status character varying(20) NOT NULL DEFAULT 'UPLOADING',
    object_key character varying(1024) NOT NULL,
    size_bytes bigint NOT NULL,
    duration_ms bigint,
    CONSTRAINT media_pkey PRIMARY KEY (id),
    CONSTRAINT media_object_key_uq UNIQUE (object_key),
    CONSTRAINT media_user_id_fk FOREIGN KEY (user_id)
    REFERENCES public.users (id)
    ON UPDATE NO ACTION
    ON DELETE NO ACTION,
    CONSTRAINT media_post_id_fk FOREIGN KEY (post_id)
    REFERENCES public.posts (id)
    ON UPDATE NO ACTION
    ON DELETE NO ACTION
    );

CREATE INDEX IF NOT EXISTS idx_media_user_id ON public.media(user_id);
CREATE INDEX IF NOT EXISTS idx_media_post_id_not_null ON public.media(post_id) WHERE post_id IS NOT NULL;

ALTER TABLE public.media OWNER TO app;