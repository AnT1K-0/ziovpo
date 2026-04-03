ALTER TABLE signatures_history
    DROP CONSTRAINT IF EXISTS signatures_history_signature_id_fkey;

ALTER TABLE signatures_history
    ADD CONSTRAINT signatures_history_signature_id_fkey
        FOREIGN KEY (signature_id) REFERENCES signatures(id) ON DELETE RESTRICT;

ALTER TABLE signatures_audit
    DROP CONSTRAINT IF EXISTS signatures_audit_signature_id_fkey;

ALTER TABLE signatures_audit
    ADD CONSTRAINT signatures_audit_signature_id_fkey
        FOREIGN KEY (signature_id) REFERENCES signatures(id) ON DELETE RESTRICT;
