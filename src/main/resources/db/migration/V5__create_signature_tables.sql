CREATE TABLE signatures (
                            id UUID PRIMARY KEY,
                            threat_name TEXT NOT NULL,
                            first_bytes_hex TEXT NOT NULL,
                            remainder_hash_hex TEXT NOT NULL,
                            remainder_length BIGINT NOT NULL CHECK (remainder_length >= 0),
                            file_type TEXT NOT NULL,
                            offset_start BIGINT NOT NULL CHECK (offset_start >= 0),
                            offset_end BIGINT NOT NULL CHECK (offset_end >= offset_start),
                            updated_at TIMESTAMPTZ NOT NULL,
                            status TEXT NOT NULL CHECK (status IN ('ACTUAL', 'DELETED')),
                            digital_signature_base64 TEXT NOT NULL
);

CREATE TABLE signatures_history (
                                    history_id BIGSERIAL PRIMARY KEY,
                                    signature_id UUID NOT NULL REFERENCES signatures(id) ON DELETE CASCADE,
                                    version_created_at TIMESTAMPTZ NOT NULL,
                                    threat_name TEXT NOT NULL,
                                    first_bytes_hex TEXT NOT NULL,
                                    remainder_hash_hex TEXT NOT NULL,
                                    remainder_length BIGINT NOT NULL,
                                    file_type TEXT NOT NULL,
                                    offset_start BIGINT NOT NULL,
                                    offset_end BIGINT NOT NULL,
                                    updated_at TIMESTAMPTZ NOT NULL,
                                    status TEXT NOT NULL CHECK (status IN ('ACTUAL', 'DELETED')),
                                    digital_signature_base64 TEXT NOT NULL
);

CREATE TABLE signatures_audit (
                                  audit_id BIGSERIAL PRIMARY KEY,
                                  signature_id UUID NOT NULL REFERENCES signatures(id) ON DELETE CASCADE,
                                  changed_by TEXT NOT NULL,
                                  changed_at TIMESTAMPTZ NOT NULL,
                                  fields_changed TEXT,
                                  description TEXT NOT NULL
);

CREATE INDEX idx_signatures_status ON signatures(status);
CREATE INDEX idx_signatures_updated_at ON signatures(updated_at);
CREATE INDEX idx_signatures_history_signature_id ON signatures_history(signature_id);
CREATE INDEX idx_signatures_history_version_created_at ON signatures_history(version_created_at);
CREATE INDEX idx_signatures_audit_signature_id ON signatures_audit(signature_id);
CREATE INDEX idx_signatures_audit_changed_at ON signatures_audit(changed_at);