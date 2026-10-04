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
                      OffsetDateTime completedAt, SearchPlan searchPlan, UUID evidencePackId, boolean hasOpenCriticIssues,
                      String model, String failureProviderCode, String evidenceNoteKind, Integer evidenceCoreItems,
                      Integer evidenceCoreNeeded) {
    }

    private static final String COLUMNS = "id, generation_id, session_id, kind, parent_run_id, status, stage, "
        + "configuration, research_profile, counts, failure_code, failure_message, suggested_realism, headline, "
        + "created_at, updated_at, completed_at, search_plan, evidence_pack_id, model, failure_provider_code, "
        + "evidence_note_kind, evidence_core_items, evidence_core_needed";

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

    /** True when the run is COMPLETED with an accepted attempt and an Evidence Pack. */
    boolean isCompletedWithScenario(UUID id) {
        return Boolean.TRUE.equals(jdbc.queryForObject("select exists (select 1 from generation_run where id = ? "
            + "and status = 'COMPLETED' and final_attempt is not null and evidence_pack_id is not null)",
            Boolean.class, id));
    }

    /** Inserts a QUEUED ALTERNATIVE run that copies the parent's configuration, research data and pack. */
    void insertAlternativeQueued(UUID id, String generationId, UUID sessionId, UUID parentId,
                                 OffsetDateTime now, OffsetDateTime deadline) {
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, parent_run_id, status, "
                + "configuration, research_profile, search_plan, counts, evidence_pack_id, suggested_realism, "
                + "evidence_note_kind, evidence_core_items, evidence_core_needed, deadline_at, created_at, "
                + "updated_at) select ?, ?, ?, 'ALTERNATIVE', p.id, 'QUEUED', p.configuration, p.research_profile, "
                + "p.search_plan, p.counts, p.evidence_pack_id, p.suggested_realism, p.evidence_note_kind, "
                + "p.evidence_core_items, p.evidence_core_needed, ?, ?, ? from generation_run p where p.id = ?",
            id, generationId, sessionId, deadline, now, now, parentId);
    }

    /** QUEUED to RUNNING at stage EXPLORING_FUTURES for ALTERNATIVE runs, only before the deadline. */
    boolean startRunningAlternative(UUID id, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'RUNNING', stage = 'EXPLORING_FUTURES', updated_at = ? "
            + "where id = ? and status = 'QUEUED' and deadline_at > ?", now, id, now) == 1;
    }

    /** Stage transition of an active run. */
    void markStage(UUID id, RunStage stage, OffsetDateTime now) {
        jdbc.update("update generation_run set stage = ?, updated_at = ? where id = ? and status = 'RUNNING'",
            stage.getValue(), now, id);
    }

    /** QUEUED to RUNNING at stage UNDERSTANDING, only before the deadline; false when nothing was updated. */
    boolean startRunning(UUID id, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'RUNNING', stage = 'UNDERSTANDING', updated_at = ? "
            + "where id = ? and status = 'QUEUED' and deadline_at > ?", now, id, now) == 1;
    }

    void storeProfile(UUID id, ResearchProfile profile, OffsetDateTime now) {
        jdbc.update("update generation_run set research_profile = cast(? as jsonb), updated_at = ? where id = ? and status = 'RUNNING'",
            write(profile), now, id);
    }

    void storeSearchPlan(UUID id, SearchPlan plan, OffsetDateTime now) {
        jdbc.update("update generation_run set search_plan = cast(? as jsonb), updated_at = ? where id = ? and status = 'RUNNING'",
            write(plan), now, id);
    }

    void storeSearchResults(UUID id, SearchPlan plan, ResearchCounts counts, OffsetDateTime now) {
        jdbc.update("update generation_run set search_plan = cast(? as jsonb), counts = cast(? as jsonb), "
            + "updated_at = ? where id = ? and status = 'RUNNING'", write(plan), write(counts), now, id);
    }

    void storeCounts(UUID id, ResearchCounts counts, OffsetDateTime now) {
        jdbc.update("update generation_run set counts = cast(? as jsonb), updated_at = ? where id = ? and status = 'RUNNING'",
            write(counts), now, id);
    }

    /** Points the run at its (already inserted) Evidence Pack and stores the counts. */
    public void storePack(UUID id, UUID packId, ResearchCounts counts, OffsetDateTime now) {
        jdbc.update("update generation_run set evidence_pack_id = ?, counts = cast(? as jsonb), updated_at = ? "
            + "where id = ?", packId, write(counts), now, id);
    }

    /** Stores the pack, the counts and the evidence note (FR-47) in the caller's transaction (run row locked). */
    public void storePack(UUID id, UUID packId, ResearchCounts counts, String noteKind, Integer coreItems,
                          Integer coreNeeded, Integer suggestedRealism, OffsetDateTime now) {
        jdbc.update("update generation_run set evidence_pack_id = ?, counts = cast(? as jsonb), "
                + "evidence_note_kind = ?, evidence_core_items = ?, evidence_core_needed = ?, "
                + "suggested_realism = ?, updated_at = ? where id = ? and status = 'RUNNING'",
            packId, write(counts), noteKind, coreItems, coreNeeded, suggestedRealism, now, id);
    }

    /** FR-45: STOPPED when the run is QUEUED or RUNNING; false when it was already terminal. */
    public boolean stop(UUID id, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'STOPPED', updated_at = ?, completed_at = ? "
            + "where id = ? and status in ('QUEUED','RUNNING')", now, now, id) == 1;
    }

    /** Stage transition that only applies while the run is RUNNING; false when the run was ended meanwhile. */
    public boolean markStageIfRunning(UUID id, RunStage stage, OffsetDateTime now) {
        return jdbc.update("update generation_run set stage = ?, updated_at = ? where id = ? and status = 'RUNNING'",
            stage.getValue(), now, id) == 1;
    }

    /** Commits RUN_TIMEOUT when the run is still RUNNING and its deadline has passed; false otherwise. */
    public boolean failTimedOut(UUID id, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'FAILED', failure_code = 'RUN_TIMEOUT', "
            + "failure_message = ?, updated_at = ?, completed_at = ? where id = ? and status in ('QUEUED','RUNNING') "
            + "and deadline_at <= ?", RunFailures.message(com.oracul.app.api.model.RunFailureCode.RUN_TIMEOUT), now, now, id, now) == 1;
    }

    /** Accepts an attempt: final_attempt and the counts in one statement; false when the run is no longer RUNNING. */
    public boolean storeAccepted(UUID id, int attempt, ResearchCounts counts, OffsetDateTime now) {
        return jdbc.update("update generation_run set final_attempt = ?, counts = cast(? as jsonb), updated_at = ? "
            + "where id = ? and status = 'RUNNING'", attempt, write(counts), now, id) == 1;
    }

    public boolean markCompleted(UUID id, RunStage stage, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'COMPLETED', stage = ?, updated_at = ?, completed_at = ? "
            + "where id = ? and status = 'RUNNING'", stage.getValue(), now, now, id) == 1;
    }

    /** Completes the run with its story headline; false when the run is no longer RUNNING. */
    public boolean markCompletedWithHeadline(UUID id, RunStage stage, String headline, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'COMPLETED', stage = ?, headline = ?, updated_at = ?, "
            + "completed_at = ? where id = ? and status = 'RUNNING'", stage.getValue(), headline, now, now, id) == 1;
    }

    /** Ends the run INSUFFICIENT_EVIDENCE at its current stage; false when it is no longer RUNNING. */
    public boolean markInsufficientEvidence(UUID id, String message, Integer suggestedRealism, OffsetDateTime now) {
        return jdbc.update("update generation_run set status = 'INSUFFICIENT_EVIDENCE', "
            + "failure_code = 'INSUFFICIENT_EVIDENCE', failure_message = ?, suggested_realism = ?, updated_at = ?, "
            + "completed_at = ? where id = ? and status = 'RUNNING'", message, suggestedRealism, now, now, id) == 1;
    }

    public void markFailed(UUID id, String code, String message, OffsetDateTime now) {
        jdbc.update("update generation_run set status = 'FAILED', failure_code = ?, failure_message = ?, "
            + "updated_at = ?, completed_at = ? where id = ? and status in ('QUEUED','RUNNING')",
            code, message, now, now, id);
    }

    /** Failure with the sanitized provider code (rows 5/6 of FR-39). */
    public void markFailed(UUID id, String code, String message, String providerCode, OffsetDateTime now) {
        jdbc.update("update generation_run set status = 'FAILED', failure_code = ?, failure_message = ?, "
            + "failure_provider_code = ?, updated_at = ?, completed_at = ? where id = ? and status in ('QUEUED','RUNNING')",
            code, message, providerCode, now, now, id);
    }

    /** Stores the model slug resolved for the run while the run is still active. */
    public boolean storeModel(UUID id, String model, OffsetDateTime now) {
        return jdbc.update("update generation_run set model = ?, updated_at = ? where id = ? "
            + "and status in ('QUEUED','RUNNING')", model, now, id) == 1;
    }

    /** The newest COMPLETED (with a headline) and STOPPED runs of the session, newest first (ties by id desc). */
    public List<Row> findRecent(UUID sessionId, int limit) {
        return jdbc.query("select " + COLUMNS + " from generation_run where session_id = ? and "
            + "((status = 'COMPLETED' and headline is not null) or status = 'STOPPED') "
            + "order by created_at desc, id desc limit ?", (rs, i) -> mapRow(rs, false),
            sessionId, limit);
    }

    /** Configuration of the newest run of the session, any status. */
    public Optional<ScenarioConfiguration> findNewestConfiguration(UUID sessionId) {
        return jdbc.query("select configuration from generation_run where session_id = ? "
            + "order by created_at desc, id desc limit 1",
            (rs, i) -> read(rs.getString("configuration"), ScenarioConfiguration.class), sessionId).stream().findFirst();
    }

    private Row mapRow(java.sql.ResultSet rs, boolean openCritic) throws java.sql.SQLException {
        return new Row(
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
            rs.getString("search_plan") == null ? null : read(rs.getString("search_plan"), SearchPlan.class),
            rs.getObject("evidence_pack_id", UUID.class), openCritic,
            rs.getString("model"), rs.getString("failure_provider_code"), rs.getString("evidence_note_kind"),
            (Integer) rs.getObject("evidence_core_items"), (Integer) rs.getObject("evidence_core_needed"));
    }

    public Optional<Row> find(UUID id, UUID sessionId) {
        List<Row> rows = jdbc.query("select " + COLUMNS + ", exists (select 1 from scenario_attempt a where a.run_id = generation_run.id "
                + "and a.attempt = generation_run.final_attempt and a.critic_report ->> 'verdict' = 'FAIL') "
                + "as open_critic from generation_run where id = ? and session_id = ?",
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
                rs.getString("search_plan") == null ? null : read(rs.getString("search_plan"), SearchPlan.class),
                rs.getObject("evidence_pack_id", UUID.class), rs.getBoolean("open_critic"),
                rs.getString("model"), rs.getString("failure_provider_code"), rs.getString("evidence_note_kind"),
                (Integer) rs.getObject("evidence_core_items"), (Integer) rs.getObject("evidence_core_needed")),
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
