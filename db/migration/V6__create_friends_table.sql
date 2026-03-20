CREATE TABLE friend_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    from_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    to_user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    status VARCHAR(20) NOT NULL DEFAULT 'pending',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    CONSTRAINT unique_friend_request UNIQUE (from_user_id, to_user_id),
    CONSTRAINT check_request_not_self CHECK (from_user_id <> to_user_id),
    CONSTRAINT check_request_status CHECK (status IN ('pending', 'accepted', 'rejected', 'cancelled'))
);

CREATE INDEX idx_friend_requests_from_user ON friend_requests(from_user_id);
CREATE INDEX idx_friend_requests_to_user ON friend_requests(to_user_id);
CREATE INDEX idx_friend_requests_to_user_status ON friend_requests(to_user_id, status);
CREATE INDEX idx_friend_requests_from_user_status ON friend_requests(from_user_id, status);
CREATE INDEX idx_friend_requests_created_at ON friend_requests(created_at DESC);

CREATE TABLE friendships (
    user_id1 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    user_id2 UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    
    PRIMARY KEY (user_id1, user_id2),
    CONSTRAINT check_ordered CHECK (user_id1 < user_id2)
);

CREATE INDEX idx_friendships_user1 ON friendships(user_id1);
CREATE INDEX idx_friendships_user2 ON friendships(user_id2);
CREATE INDEX idx_friendships_created_at ON friendships(created_at DESC);

CREATE OR REPLACE FUNCTION add_friendship(u1 UUID, u2 UUID)
RETURNS VOID AS $$
BEGIN
    INSERT INTO friendships (user_id1, user_id2, created_at)
    VALUES (LEAST(u1, u2), GREATEST(u1, u2), NOW())
    ON CONFLICT DO NOTHING;
END;
$$ LANGUAGE plpgsql;