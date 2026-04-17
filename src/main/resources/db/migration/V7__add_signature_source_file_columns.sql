ALTER TABLE signatures
    ADD COLUMN IF NOT EXISTS source_file_object_key TEXT;

ALTER TABLE signatures
    ADD COLUMN IF NOT EXISTS source_file_name TEXT;
