package com.oracul.app.reasoning;

import com.oracul.app.api.model.GuardReport;
import com.oracul.app.api.model.ScenarioAttemptReason;
import com.oracul.app.api.model.StructuredScenario;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** scenario_attempt rows: one per SCENARIO_GENERATION call that produced an answer. */
@Repository
public class ScenarioAttemptRepository {

    /** A stored attempt; scenario is the model output as parsed, cleaned the guard's cleaned scenario. */
    public record Attempt(int attempt, ScenarioAttemptReason reason, Optional<StructuredScenario> scenario,
                          List<String> schemaErrors, Optional<GuardReport> guardReport,
                          Optional<StructuredScenario> cleaned) {
    }

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    ScenarioAttemptRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    void insert(UUID runId, int attempt, ScenarioAttemptReason reason, Optional<StructuredScenario> scenario,
                List<String> schemaErrors, OffsetDateTime now) {
        jdbc.update("insert into scenario_attempt (run_id, attempt, reason, structured_scenario, schema_errors, "
                + "created_at) values (?, ?, ?, cast(? as jsonb), cast(? as jsonb), ?)",
            runId, attempt, reason.getValue(), scenario.map(json::writeValueAsString).orElse(null),
            json.writeValueAsString(schemaErrors), now);
    }

    void storeGuard(UUID runId, int attempt, GuardReport report, StructuredScenario cleaned) {
        jdbc.update("update scenario_attempt set guard_report = cast(? as jsonb), cleaned_scenario = cast(? as jsonb) "
                + "where run_id = ? and attempt = ?",
            json.writeValueAsString(report), json.writeValueAsString(cleaned), runId, attempt);
    }

    public List<Attempt> list(UUID runId) {
        return jdbc.query("select attempt, reason, structured_scenario, schema_errors, guard_report, cleaned_scenario "
                + "from scenario_attempt where run_id = ? order by attempt",
            (rs, i) -> new Attempt(rs.getInt("attempt"), ScenarioAttemptReason.fromValue(rs.getString("reason")),
                Optional.ofNullable(rs.getString("structured_scenario"))
                    .map(v -> json.readValue(v, StructuredScenario.class)),
                json.readValue(rs.getString("schema_errors"), new TypeReference<List<String>>() { }),
                Optional.ofNullable(rs.getString("guard_report")).map(v -> json.readValue(v, GuardReport.class)),
                Optional.ofNullable(rs.getString("cleaned_scenario"))
                    .map(v -> json.readValue(v, StructuredScenario.class))),
            runId);
    }

    public Optional<Integer> finalAttempt(UUID runId) {
        return jdbc.query("select final_attempt from generation_run where id = ?",
            (rs, i) -> Optional.ofNullable((Integer) rs.getObject("final_attempt")), runId).stream().findFirst()
            .flatMap(o -> o);
    }
}
