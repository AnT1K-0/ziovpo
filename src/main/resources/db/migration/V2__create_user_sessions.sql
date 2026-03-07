CREATE TABLE user_sessions (
                               id BIGSERIAL PRIMARY KEY,
                               user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                               refresh_token TEXT NOT NULL UNIQUE,
                               status TEXT NOT NULL CHECK (status IN ('ACTIVE', 'REFRESHED', 'REVOKED', 'EXPIRED')),
                               created_at TIMESTAMPTZ NOT NULL,
                               expires_at TIMESTAMPTZ NOT NULL,
                               user_agent TEXT,
                               ip_address TEXT
);

CREATE INDEX idx_user_sessions_user ON user_sessions(user_id);
CREATE INDEX idx_user_sessions_status ON user_sessions(status);