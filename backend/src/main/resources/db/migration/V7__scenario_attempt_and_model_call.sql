CREATE TABLE scenario_attempt (
    id                  BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id              UUID NOT NULL REFERENCES generation_run (id) ON DELETE CASCADE,
    attempt             INTEGER NOT NULL,
    reason              VARCHAR(32) NOT NULL,
    structured_scenario JSONB,
    schema_errors       JSONB NOT NULL,
    guard_report        JSONB,
    cleaned_scenario    JSONB,
    critic_report       JSONB,
    created_at          TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_scenario_attempt UNIQUE (run_id, attempt)
);

CREATE TABLE model_call (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    run_id          UUID NOT NULL REFERENCES generation_run (id) ON DELETE CASCADE,
    purpose         VARCHAR(32) NOT NULL,
    attempt         INTEGER,
    request_body    JSONB NOT NULL,
    response_status INTEGER,
    created_at      TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_model_call_run ON model_call (run_id);

ALTER TABLE generation_run ADD COLUMN final_attempt INTEGER;
