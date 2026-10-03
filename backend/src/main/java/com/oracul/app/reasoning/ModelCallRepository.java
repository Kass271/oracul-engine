package com.oracul.app.reasoning;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** model_call rows: the request body as sent (never headers) and the final HTTP status. */
@Repository
public class ModelCallRepository {

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    ModelCallRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    public long insert(UUID runId, String purpose, int attempt, Map<String, Object> body, OffsetDateTime now) {
        return jdbc.queryForObject("insert into model_call (run_id, purpose, attempt, request_body, created_at) "
                + "values (?, ?, ?, cast(? as jsonb), ?) returning id", Long.class,
            runId, purpose, attempt, json.writeValueAsString(body), now);
    }

    public void setStatus(long id, Integer status) {
        jdbc.update("update model_call set response_status = ? where id = ?", status, id);
    }
}
