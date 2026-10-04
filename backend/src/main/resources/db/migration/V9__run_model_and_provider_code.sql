ALTER TABLE generation_run
    ADD COLUMN model VARCHAR(128) NULL,
    ADD COLUMN failure_provider_code VARCHAR(64) NULL;
