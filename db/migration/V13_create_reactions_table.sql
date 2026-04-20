CREATE TABLE IF NOT EXISTS public.reactions
(
    id uuid NOT NULL,
    user_id uuid NOT NULL,
    post_id uuid NOT NULL,
    reaction_type character varying(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    CONSTRAINT reactions_pkey PRIMARY KEY (id),
    CONSTRAINT reactions_user_id_fk FOREIGN KEY (user_id)
    REFERENCES public.users (id)
    CONSTRAINT reactions_post_id_fk FOREIGN KEY (post_id)
    REFERENCES public.posts (id)
    ON UPDATE NO ACTION
    ON DELETE CASCADE
    );

ALTER TABLE public.avatars OWNER to app;