CREATE TABLE browser_session (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMPTZ NOT NULL,
    last_seen_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE chatgpt_client_registration (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    singleton  BOOLEAN NOT NULL DEFAULT TRUE CHECK (singleton),
    host_id    UUID NOT NULL,
    client_id  VARCHAR(256),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_chatgpt_client_registration_singleton UNIQUE (singleton)
);
