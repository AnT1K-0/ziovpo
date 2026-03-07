CREATE TABLE product (
                         id BIGSERIAL PRIMARY KEY,
                         name TEXT NOT NULL UNIQUE,
                         is_blocked BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE license_type (
                              id BIGSERIAL PRIMARY KEY,
                              name TEXT NOT NULL UNIQUE,
                              default_duration_in_days INT NOT NULL CHECK (default_duration_in_days > 0),
                              description TEXT
);