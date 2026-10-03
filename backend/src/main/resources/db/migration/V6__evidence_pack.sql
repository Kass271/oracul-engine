CREATE TABLE evidence_pack (
    id             UUID PRIMARY KEY,
    run_id         UUID NOT NULL REFERENCES generation_run (id) ON DELETE CASCADE,
    generation_id  VARCHAR(32) NOT NULL,
    cutoff         TIMESTAMPTZ NOT NULL,
    configuration  JSONB NOT NULL,
    profile        JSONB NOT NULL,
    items          JSONB NOT NULL,
    source_ids     JSONB NOT NULL,
    prompt_text    TEXT NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_evidence_pack_run ON evidence_pack (run_id);

ALTER TABLE generation_run
    ADD CONSTRAINT fk_generation_run_evidence_pack FOREIGN KEY (evidence_pack_id)
        REFERENCES evidence_pack (id) ON DELETE SET NULL;
