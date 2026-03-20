ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS media_id UUID;

UPDATE posts p
SET media_id = m.id
    FROM media m
WHERE m.post_id = p.id
  AND p.media_id IS NULL;

ALTER TABLE posts
DROP CONSTRAINT IF EXISTS media_id_fk;

ALTER TABLE posts
    ADD CONSTRAINT media_id_fk
        FOREIGN KEY (media_id) REFERENCES media(id);

ALTER TABLE IF EXISTS public.media DROP COLUMN IF EXISTS post_id;