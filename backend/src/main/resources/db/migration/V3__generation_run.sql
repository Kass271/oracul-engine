CREATE TABLE generation_run (
    id                 UUID PRIMARY KEY,
    generation_id      VARCHAR(32) NOT NULL,
    session_id         UUID NOT NULL REFERENCES browser_session (id),
    kind               VARCHAR(16) NOT NULL,
    parent_run_id      UUID REFERENCES generation_run (id),
    status             VARCHAR(32) NOT NULL,
    stage              VARCHAR(32),
    configuration      JSONB NOT NULL,
    research_profile   JSONB,
    search_plan        JSONB,
    evidence_pack_id   UUID,
    counts             JSONB NOT NULL,
    failure_code       VARCHAR(64),
    failure_message    VARCHAR(512),
    suggested_realism  INTEGER,
    headline           TEXT,
    deadline_at        TIMESTAMPTZ NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL,
    updated_at         TIMESTAMPTZ NOT NULL,
    completed_at       TIMESTAMPTZ,
    CONSTRAINT uq_generation_run_generation_id UNIQUE (generation_id)
);

CREATE UNIQUE INDEX one_active_run_per_session ON generation_run (session_id)
    WHERE status IN ('QUEUED', 'RUNNING');

CREATE INDEX idx_generation_run_session ON generation_run (session_id, created_at DESC);
