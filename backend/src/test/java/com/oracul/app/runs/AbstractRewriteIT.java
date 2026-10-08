package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.result.AbstractStoryIT;
import com.oracul.app.result.StoryFixtures;
import com.oracul.app.style.WritingMode;
import com.oracul.app.style.WritingStyleProfile;
import com.oracul.app.style.WritingStyles;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared driver of the phase-04 slice 04_story-rewrite tests (story-rewrite.md FR-67): startRewriteRun over HTTP only,
 * seeded parents, row snapshots and the WRITING STYLE section helpers. Nothing here imports a class of the slice.
 */
public abstract class AbstractRewriteIT extends AbstractStoryIT {

    protected static final WritingStyles STYLES = new WritingStyles("classpath:writing-styles.json", new DefaultResourceLoader());

    protected static final String NOT_REWRITABLE = "Only a completed future can be rewritten";
    protected static final String ACTIVE_MSG = "A generation is already running";
    protected static final String NOT_JSON_MSG = "Request body is not valid JSON";
    protected static final String STYLE_MSG = "Unknown writing style";
    protected static final String LANGUAGE_MSG = "Unknown story language";
    protected static final String NOT_CONNECTED_MSG = "Connect ChatGPT to generate";
    protected static final String NOT_FOUND_MSG = "Future not found";
    protected static final String INTERNAL_MSG = "Something went wrong — try again";

    /** Profile ids of the shipped file in API order. */
    protected static final List<String> PROFILE_IDS =
        List.of("official-briefing", "neutral-narrative", "poe", "kafka", "wells", "verne", "orwell", "clarke", "harrison");

    protected static String modeOf(String profileId) {
        return "official-briefing".equals(profileId) ? "OFFICIAL" : "NARRATIVE";
    }

    protected static String styleJson(String mode, String profileId) {
        return "{\"mode\":\"" + mode + "\",\"profileId\":\"" + profileId + "\"}";
    }

    /** RewriteRequest body of a profile of the shipped file (its own mode). */
    protected static String body(String profileId, String language) {
        return body(modeOf(profileId), profileId, language);
    }

    protected static String body(String mode, String profileId, String language) {
        return "{\"writingStyle\":" + styleJson(mode, profileId) + ",\"language\":\"" + language + "\"}";
    }

    /** The WRITING STYLE section of the request for a profile and a language, built from the style file. */
    protected static String sectionOf(String profileId, String language) {
        WritingStyleProfile p = STYLES.find(profileId).orElseThrow();
        return StoryFixtures.styleSection(p.mode() == WritingMode.OFFICIAL, p.label(), p.characteristics(), p.instructions(), "uk".equals(language));
    }

    /** The label of a profile of the shipped file. */
    protected static String labelOf(String profileId) {
        return STYLES.find(profileId).orElseThrow().label();
    }

    /** The input text without the lines from "WRITING STYLE" up to the line before "TASK". */
    protected static String withoutStyleSection(String input) {
        String[] lines = input.split("\n", -1);
        int from = -1;
        int to = -1;
        for (int i = 0; i < lines.length; i++) {
            if (from < 0 && "WRITING STYLE".equals(lines[i])) from = i;
            else if (from >= 0 && "TASK".equals(lines[i])) {
                to = i;
                break;
            }
        }
        assertThat(from).as("a WRITING STYLE line in the STORY_WRITING input").isGreaterThanOrEqualTo(0);
        assertThat(to).as("a TASK line after the WRITING STYLE section").isGreaterThan(from);
        List<String> kept = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) if (i < from || i >= to) kept.add(lines[i]);
        return String.join("\n", kept);
    }

    // ---- HTTP -----------------------------------------------------------------------------------------------------

    protected ResultActions rewrite(String sid, String runId, String body) throws Exception {
        return rewriteWith(sid, runId, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), MediaType.APPLICATION_JSON);
    }

    protected ResultActions rewriteWith(String sid, String runId, byte[] content, MediaType contentType) throws Exception {
        var b = post("/api/runs/" + runId + "/rewrites").contentType(contentType).content(content);
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    /** Starts a rewrite and returns its 202 body; fails with the real status otherwise. */
    protected Map<String, Object> rewriteOk(String sid, String runId, String body) throws Exception {
        MvcResult r = rewrite(sid, runId, body).andReturn();
        assertThat(r.getResponse().getStatus()).as("startRewriteRun: " + r.getResponse().getContentAsString()).isEqualTo(202);
        return json(r.getResponse().getContentAsString());
    }

    protected static boolean ended(Map<String, Object> m) {
        return !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status"));
    }

    /** Waits for the terminal state of a run, recording every stageIndex seen on the way. */
    protected Map<String, Object> awaitEnded(String sid, String id, Set<Object> stageIndexes) throws Exception {
        return awaitRun(sid, id, 20_000, m -> {
            stageIndexes.add(m.get("stageIndex"));
            return ended(m);
        });
    }

    protected Ran awaitRewrite(String sid, String id) throws Exception {
        return new Ran(sid, id, awaitRun(sid, id, 20_000, AbstractRewriteIT::ended));
    }

    /** A rewrite started and awaited. */
    protected Ran rewriteRun(Ran parent, String body) throws Exception {
        Map<String, Object> created = rewriteOk(parent.sid(), parent.id(), body);
        return awaitRewrite(parent.sid(), (String) created.get("id"));
    }

    /** P: a standard run (body A, no writing style) awaited COMPLETED with its headline. */
    protected Ran parent() throws Exception {
        Ran p = runV4(A);
        assertStoryCompleted(p.run());
        return p;
    }

    protected Ran alternativeOf(Ran p) throws Exception {
        var res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as("startAlternativeRun: " + res.getResponse().getContentAsString()).isEqualTo(202);
        String id = (String) json(res.getResponse().getContentAsString()).get("id");
        return new Ran(p.sid(), id, awaitRun(p.sid(), id, 20_000, AbstractRewriteIT::ended));
    }

    // ---- rows -----------------------------------------------------------------------------------------------------

    /** A run row of the session; with a story row when {@code withStory}; no pack and no final attempt. */
    protected UUID seed(String sid, String status, boolean withStory) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        boolean terminal = !"QUEUED".equals(status) && !"RUNNING".equals(status);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, headline, "
                + "deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), 'STANDARD', ?, cast(? as jsonb), "
                + "cast(? as jsonb), ?, ?, ?, ?, ?)",
            id, "ORC-RW-" + id.toString().replace("-", "").substring(20), sid, status, A, ZERO_COUNTS,
            "COMPLETED".equals(status) ? "Seeded headline" : null, now.plusDays(1), now.minusMinutes(5), now.minusMinutes(4),
            terminal ? now.minusMinutes(4) : null);
        if (withStory) {
            jdbc.update("insert into future_story (run_id, headline, dateline, future_date, body, created_at) values (?, ?, ?, "
                + "cast(? as date), ?, ?)", id, "Seeded headline", "ORACUL FUTURE — March 1, 2099", "2099-03-01", StoryFixtures.body(3), now.minusMinutes(4));
        }
        return id;
    }

    protected void failActive(UUID id) {
        jdbc.update("update generation_run set status = 'FAILED' where id = ? and status in ('QUEUED','RUNNING')", id);
    }

    /** Every column of the run row. */
    protected Map<String, Object> runRow(String id) {
        return jdbc.queryForMap("select * from generation_run where id = cast(? as uuid)", id);
    }

    /** The row of the run and every row the pipeline writes for it, as the database holds them. */
    protected Map<String, Object> snapshot(String runId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("generation_run", runRow(runId));
        out.put("future_story", jdbc.queryForList("select * from future_story where run_id = cast(? as uuid)", runId));
        out.put("scenario_attempt", jdbc.queryForList("select * from scenario_attempt where run_id = cast(? as uuid) order by attempt", runId));
        out.put("model_call", jdbc.queryForList("select * from model_call where run_id = cast(? as uuid) order by id", runId));
        out.put("source", jdbc.queryForList("select * from source where run_id = cast(? as uuid) order by id", runId));
        out.put("event", jdbc.queryForList("select * from event where run_id = cast(? as uuid) order by id", runId));
        out.put("evidence_pack", jdbc.queryForList("select * from evidence_pack where run_id = cast(? as uuid) order by id", runId));
        return out;
    }

    protected int rowsOf(String table, String runId) {
        return jdbc.queryForObject("select count(*) from " + table + " where run_id = cast(? as uuid)", Integer.class, runId);
    }

    protected Map<String, Object> configurationOf(Map<String, Object> run) {
        return map(run.get("configuration"));
    }
}
