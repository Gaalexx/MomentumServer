BEGIN;

ALTER TABLE public.reactions
    RENAME COLUMN reaction_type TO action_type;

ALTER TABLE public.reactions
    RENAME CONSTRAINT reactions_pkey TO post_actions_pk;

ALTER TABLE public.reactions
    RENAME CONSTRAINT reactions_user_id_fk TO post_actions_user_id_fk;

ALTER TABLE public.reactions
    RENAME CONSTRAINT reactions_post_id_fk TO post_actions_post_id_fk;

CREATE UNIQUE INDEX one_hidden_post_per_pair_user_and_post
    ON public.reactions(user_id, post_id)
    WHERE action_type = 'HIDE';

ALTER TABLE public.reactions
    RENAME TO post_actions;

COMMIT;