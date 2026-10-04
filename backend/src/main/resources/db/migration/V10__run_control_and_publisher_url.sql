ALTER TABLE generation_run
    ADD COLUMN evidence_note_kind VARCHAR(32) NULL,
    ADD COLUMN evidence_core_items INTEGER NULL,
    ADD COLUMN evidence_core_needed INTEGER NULL;

ALTER TABLE source ADD COLUMN publisher_url TEXT NULL;
