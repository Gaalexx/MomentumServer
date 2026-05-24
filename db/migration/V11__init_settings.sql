CREATE TABLE IF NOT EXISTS public.settings (
    user_id UUID PRIMARY KEY REFERENCES public.users(id),
    in_app_notifications BOOLEAN NOT NULL DEFAULT TRUE,
    publications_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    reactions_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    friend_request_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    default_theme_enabled BOOLEAN NOT NULL DEFAULT TRUE
);
