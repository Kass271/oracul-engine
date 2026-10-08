package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared driver of the phase-04 slice 05_style-labels tests (story-rewrite.md FR-68): run bodies and stored configurations with
 * a chosen {@code output.writingStyle} / {@code output.language} (key absent, JSON null or a value), the metadata of
 * getFutureResult, the items of listRecentRuns and startAlternativeRun. HTTP and the database only.
 */
public abstract class AbstractLabelsIT extends AbstractRewriteIT {

    protected static final String LEGACY = "Official / Briefing (legacy)";
    protected static final String OFFICIAL = "Official / Briefing";
    protected static final String KAFKA = "Narrative — inspired by Franz Kafka";
    protected static final String POE = "Narrative — inspired by Edgar Allan Poe";
    protected static final String ENGLISH = "English";
    protected static final String UKRAINIAN = "Ukrainian";
    protected static final String ALT_NOT_COMPLETED_MSG = "Only a completed future can have an alternative";

    private static final String OUTPUT_A = "\"output\":{\"story\":true,\"illustration\":false}";

    /** Body A whose output has the given raw JSON values; {@code null} leaves the key out ("null" is the JSON null). */
    protected static String bodyWithOutput(String styleJson, String languageJson) {
        StringBuilder out = new StringBuilder("\"output\":{\"story\":true,\"illustration\":false");
        if (styleJson != null) out.append(",\"writingStyle\":").append(styleJson);
        if (languageJson != null) out.append(",\"language\":").append(languageJson);
        out.append('}');
        assertThat(A).contains(OUTPUT_A);
        return A.replace(OUTPUT_A, out.toString());
    }

    /** Overwrites the configuration of a run row (no other column). */
    protected void storeConfiguration(String runId, String configurationJson) {
        int n = jdbc.update("update generation_run set configuration = cast(? as jsonb) where id = cast(? as uuid)", configurationJson, runId);
        assertThat(n).isEqualTo(1);
    }

    /** metadata of getFutureResult (200 expected). */
    protected Map<String, Object> metadataOf(Ran r) throws Exception {
        return map(result(r).get("metadata"));
    }

    protected Map<String, Object> metadataOf(String sid, String id) throws Exception {
        return metadataOf(new Ran(sid, id, Map.of()));
    }

    /** The items of listRecentRuns of a session (200 expected). */
    protected List<Map<String, Object>> listItems(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/runs").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(r.getResponse().getStatus()).as("listRecentRuns: " + r.getResponse().getContentAsString()).isEqualTo(200);
        return list(json(r.getResponse().getContentAsString()).get("items"));
    }

    protected Map<String, Object> listItem(String sid, String id) throws Exception {
        return listItems(sid).stream().filter(i -> id.equals(i.get("id"))).findFirst()
            .orElseThrow(() -> new AssertionError("run " + id + " is not in the Recent futures list of its session"));
    }

    /** A run row of the session with a stored configuration, listed by Recent futures (no pipeline involved). */
    protected UUID seedListed(String sid, String status, String configurationJson, OffsetDateTime createdAt) {
        UUID id = UUID.randomUUID();
        boolean completed = "COMPLETED".equals(status);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, headline, "
                + "deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), 'STANDARD', ?, cast(? as jsonb), "
                + "cast(? as jsonb), ?, ?, ?, ?, ?)",
            id, "ORC-LB-" + id.toString().replace("-", "").substring(20), sid, status, configurationJson, ZERO_COUNTS,
            completed ? "Seeded " + id : null, OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), createdAt, createdAt, createdAt.plusMinutes(1));
        return id;
    }

    protected Http alternativeOn(String sid, String runId) throws Exception {
        var b = post("/api/runs/" + runId + "/alternatives");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        MvcResult res = mvc.perform(b).andReturn();
        return new Http(res.getResponse().getStatus(), res.getResponse().getContentAsString());
    }

    /** Status and body of one response. */
    protected record Http(int status, String body) {}

    protected static String joinedLabel(Map<String, Object> m) {
        return m.get("styleLabel") + " · " + m.get("languageLabel");
    }
}
