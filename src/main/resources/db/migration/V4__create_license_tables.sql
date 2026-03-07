CREATE TABLE license (
                         id BIGSERIAL PRIMARY KEY,
                         code TEXT NOT NULL UNIQUE,

                         user_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
                         owner_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,

                         product_id BIGINT NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
                         type_id BIGINT NOT NULL REFERENCES license_type(id) ON DELETE RESTRICT,

                         first_activation_date TIMESTAMPTZ,
                         ending_date TIMESTAMPTZ,

                         blocked BOOLEAN NOT NULL DEFAULT FALSE,
                         device_count INT NOT NULL CHECK (device_count > 0),
                         description TEXT
);

CREATE TABLE device (
                        id BIGSERIAL PRIMARY KEY,
                        name TEXT NOT NULL,
                        mac_address TEXT NOT NULL UNIQUE,
                        user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE
);

CREATE TABLE device_license (
                                id BIGSERIAL PRIMARY KEY,
                                license_id BIGINT NOT NULL REFERENCES license(id) ON DELETE CASCADE,
                                device_id BIGINT NOT NULL REFERENCES device(id) ON DELETE CASCADE,
                                activation_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),

                                CONSTRAINT uq_device_license UNIQUE (license_id, device_id)
);

CREATE TABLE license_history (
                                 id BIGSERIAL PRIMARY KEY,
                                 license_id BIGINT NOT NULL REFERENCES license(id) ON DELETE CASCADE,
                                 user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE RESTRICT,
                                 status TEXT NOT NULL,
                                 change_date TIMESTAMPTZ NOT NULL DEFAULT NOW(),
                                 description TEXT
);

CREATE INDEX idx_license_code ON license(code);
CREATE INDEX idx_license_user_id ON license(user_id);
CREATE INDEX idx_license_owner_id ON license(owner_id);
CREATE INDEX idx_license_product_id ON license(product_id);

CREATE INDEX idx_device_user_id ON device(user_id);
CREATE INDEX idx_device_mac_address ON device(mac_address);

CREATE INDEX idx_device_license_license_id ON device_license(license_id);
CREATE INDEX idx_device_license_device_id ON device_license(device_id);

CREATE INDEX idx_license_history_license_id ON license_history(license_id);
CREATE INDEX idx_license_history_user_id ON license_history(user_id);