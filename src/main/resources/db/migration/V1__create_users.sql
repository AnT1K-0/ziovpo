CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       username TEXT NOT NULL UNIQUE,
                       password TEXT NOT NULL,
                       role TEXT NOT NULL CHECK (role IN ('ROLE_USER', 'ROLE_ADMIN')),
                       created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);