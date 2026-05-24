ALTER TABLE public.settings
    RENAME COLUMN recommend_to_contacts TO friend_request_enabled;
    RENAME COLUMN allow_add_from_anyone TO default_theme_enabled;
    ALTER COLUMN in_app_notifications SET DEFAULT TRUE,
    ALTER COLUMN publications_enabled SET DEFAULT TRUE,
    ALTER COLUMN reactions_enabled SET DEFAULT TRUE,
    ALTER COLUMN friend_request_enabled SET DEFAULT TRUE,
    ALTER COLUMN default_theme_enabled SET DEFAULT TRUE;