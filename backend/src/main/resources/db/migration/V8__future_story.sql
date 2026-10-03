CREATE TABLE future_story (
    run_id      UUID PRIMARY KEY REFERENCES generation_run (id) ON DELETE CASCADE,
    headline    TEXT NOT NULL,
    dateline    VARCHAR(64) NOT NULL,
    future_date DATE NOT NULL,
    body        TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL
);
