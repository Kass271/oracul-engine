CREATE TABLE event (
    run_id            UUID NOT NULL REFERENCES generation_run (id) ON DELETE CASCADE,
    id                VARCHAR(16) NOT NULL,
    event_date        DATE,
    category          TEXT NOT NULL,
    entities          JSONB NOT NULL,
    summary           TEXT NOT NULL,
    disagreement      TEXT,
    source_ids        JSONB NOT NULL,
    confidence        DOUBLE PRECISION NOT NULL,
    classification    JSONB,
    ranking           JSONB,
    selection_section VARCHAR(16),
    evidence_id       VARCHAR(8),
    excluded_reason   VARCHAR(64),
    PRIMARY KEY (run_id, id)
);
