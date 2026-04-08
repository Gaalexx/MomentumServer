CREATE TABLE IF NOT EXISTS public.settings (
    user_id UUID PRIMARY KEY REFERENCES public.users(id),
    in_app_notifications BOOLEAN NOT NULL DEFAULT FALSE,
    publications_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    reactions_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    recommend_to_contacts BOOLEAN NOT NULL DEFAULT FALSE,
    allow_add_from_anyone BOOLEAN NOT NULL DEFAULT FALSE
);
