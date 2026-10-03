package com.oracul.app.runs;

import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.RunKind;
import com.oracul.app.api.model.RunStage;
import com.oracul.app.api.model.RunStatus;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.SearchPlan;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class GenerationRunRepository {

    /** Persisted run row. */
    public record Row(UUID id, String generationId, UUID sessionId, RunKind kind, UUID parentRunId, RunStatus status,
                      RunStage stage, ScenarioConfiguration configuration, ResearchProfile researchProfile,
                      ResearchCounts counts, String failureCode, String failureMessage, Integer suggestedRealism,
                      String headline, OffsetDateTime createdAt, OffsetDateTime updatedAt,
                      OffsetDateTime completedAt, SearchPlan searchPlan) {
    }

    private static final String COLUMNS = "id, generation_id, session_id, kind, parent_run_id, status, stage, "
        + "configuration, research_profile, counts, failure_code, failure_message, suggested_realism, headline, "
        + "created_at, updated_at, completed_at, search_plan";

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    GenerationRunRepository(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    boolean generationIdExists(String generationId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists (select 1 from generation_run where generation_id = ?)", Boolean.class, generationId));
    }

    boolean hasActiveRun(UUID sessionId) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists (select 1 from generation_run where session_id = ? and status in ('QUEUED','RUNNING'))",
            Boolean.class, sessionId));
    }

    void insertQueued(UUID id, String generationId, UUID sessionId, ScenarioConfiguration cfg,
                      OffsetDateTime now, OffsetDateTime deadline) {
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, "
                + "deadline_at, created_at, updated_at) values (?, ?, ?, 'STANDARD', 'QUEUED', cast(? as jsonb), "
                + "cast(? as jsonb), ?, ?, ?)",
            id, generationId, sessionId, write(cfg), write(new ResearchCounts(0, 0, 0, 0, 0, 0, 0)), deadline, now, now);
    }

    /** Stage transition of an active run. */
    void markStage(UUID id, RunStage stage, OffsetDateTime now) {
        jdbc.update("update generation_run set status = 'RUNNING', stage = ?, updated_at = ? "
            + "where id = ? and status in ('QUEUED','RUNNING')", stage.getValue(), now, id);
    }

    void storeProfile(UUID id, ResearchProfile profile, OffsetDateTime now) {
        jdbc.update("update generation_run set research_profile = cast(? as jsonb), updated_at = ? where id = ?",
            write(profile), now, id);
    }

    void storeSearchPlan(UUID id, SearchPlan plan, OffsetDateTime now) {
        jdbc.update("update generation_run set search_plan = cast(? as jsonb), updated_at = ? where id = ?",
            write(plan), now, id);
    }

    void storeSearchResults(UUID id, SearchPlan plan, ResearchCounts counts, OffsetDateTime now) {
        jdbc.update("update generation_run set search_plan = cast(? as jsonb), counts = cast(? as jsonb), "
            + "updated_at = ? where id = ?", write(plan), write(counts), now, id);
    }

    void storeCounts(UUID id, ResearchCounts counts, OffsetDateTime now) {
        jdbc.update("update generation_run set counts = cast(? as jsonb), updated_at = ? where id = ?",
            write(counts), now, id);
    }

    /** Stage transition that only applies while the run is RUNNING; false when the run was ended meanwhile. */
    boolean markStageIfRunning(UUID id, RunStage stage, OffsetDateTime now) {
        return jdbc.update("update generation_run set stage = ?, updated_at = ? where id = ? and status = 'RUNNING'",
            stage.getValue(), now, id) == 1;
    }

    /** Commits RUN_TIMEOUT when the run is still RUNNING and its deadline has passed; false otherwise. */
    boolean failTimedOut(UUID id, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'FAILED', failure_code = 'RUN_TIMEOUT', "
            + "failure_message = ?, updated_at = ?, completed_at = ? where id = ? and status = 'RUNNING' "
            + "and deadline_at <= ?", "Generation took too long — try again", now, now, id, now) == 1;
    }

    boolean markCompleted(UUID id, RunStage stage, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'COMPLETED', stage = ?, updated_at = ?, completed_at = ? "
            + "where id = ? and status = 'RUNNING'", stage.getValue(), now, now, id) == 1;
    }

    void markFailed(UUID id, String code, String message, OffsetDateTime now) {
        jdbc.update("update generation_run set status = 'FAILED', failure_code = ?, failure_message = ?, "
            + "updated_at = ?, completed_at = ? where id = ? and status in ('QUEUED','RUNNING')",
            code, message, now, now, id);
    }

    public Optional<Row> find(UUID id, UUID sessionId) {
        List<Row> rows = jdbc.query("select " + COLUMNS + " from generation_run where id = ? and session_id = ?",
            (rs, i) -> new Row(
                rs.getObject("id", UUID.class), rs.getString("generation_id"), rs.getObject("session_id", UUID.class),
                RunKind.fromValue(rs.getString("kind")), rs.getObject("parent_run_id", UUID.class),
                RunStatus.fromValue(rs.getString("status")),
                rs.getString("stage") == null ? null : RunStage.fromValue(rs.getString("stage")),
                read(rs.getString("configuration"), ScenarioConfiguration.class),
                rs.getString("research_profile") == null ? null
                    : read(rs.getString("research_profile"), ResearchProfile.class),
                read(rs.getString("counts"), ResearchCounts.class),
                rs.getString("failure_code"), rs.getString("failure_message"),
                (Integer) rs.getObject("suggested_realism"), rs.getString("headline"),
                utc(rs.getObject("created_at", OffsetDateTime.class)),
                utc(rs.getObject("updated_at", OffsetDateTime.class)),
                utc(rs.getObject("completed_at", OffsetDateTime.class)),
                rs.getString("search_plan") == null ? null : read(rs.getString("search_plan"), SearchPlan.class)),
            id, sessionId);
        return rows.stream().findFirst();
    }

    private static OffsetDateTime utc(OffsetDateTime t) {
        return t == null ? null : t.withOffsetSameInstant(ZoneOffset.UTC);
    }

    private String write(Object value) {
        return json.writeValueAsString(value);
    }

    private <T> T read(String value, Class<T> type) {
        return json.readValue(value, type);
    }
}
