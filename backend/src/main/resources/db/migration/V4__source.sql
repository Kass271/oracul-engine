CREATE TABLE source (
    run_id            UUID NOT NULL REFERENCES generation_run (id) ON DELETE CASCADE,
    id                VARCHAR(16) NOT NULL,
    url               TEXT NOT NULL,
    publisher         TEXT NOT NULL,
    title             TEXT NOT NULL,
    published_at      TIMESTAMPTZ,
    retrieved_at      TIMESTAMPTZ NOT NULL,
    summary           TEXT,
    topic             VARCHAR(128),
    entities          JSONB NOT NULL,
    source_type       VARCHAR(16) NOT NULL,
    source_quality    DOUBLE PRECISION NOT NULL,
    metadata_fetched  BOOLEAN NOT NULL,
    language          VARCHAR(32),
    query_ids         JSONB NOT NULL,
    PRIMARY KEY (run_id, id),
    CONSTRAINT uq_source_run_url UNIQUE (run_id, url)
);
