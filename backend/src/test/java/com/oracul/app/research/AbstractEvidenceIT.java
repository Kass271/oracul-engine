package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared driver of the Evidence Pack tests (research-pipeline.md "Slice 07_evidence-pack", wildcard-evidence.md "Slice
 * 06_wildcard-pack"): fixture V4 with the scripted answers N-V4 / C-V4, the Evidence Pack endpoint, and the expected V4 pack
 * text of fixture (a). Talks HTTP only.
 */
public abstract class AbstractEvidenceIT extends AbstractEventIT {

    /** Body A-bright: body A with darkness 2 and optimism 9. */
    public static final String A_BRIGHT = A.replace("\"darkness\":9", "\"darkness\":2").replace("\"optimism\":2", "\"optimism\":9");

    public static final String EV1_SUMMARY =
        "Health regulators approved a new pandemic vaccine. Reports differ on the number of doses approved.";
    public static final String EV2_SUMMARY = "Dock workers strike over humanoid robots.";

    /**
     * Fresh news stub (FR-57 fixture (a): the first query of pipeline W01 answers the 4 items of V4, every other query the empty feed,
     * so under body A W01 holds S001...S004 and W02 nothing), scripted N-V4 and the given classification answers (default C-V4).
     */
    protected Ran runV4(String body) throws Exception {
        return runV4(body, N_V4, C_V4);
    }

    protected Ran runV4(String body, String normalization, String... classificationAnswers) throws Exception {
        news.reset();
        newsArticlesPerPipeline(v4(), 4);
        script(NORMALIZATION, normalization);
        script(CLASSIFICATION, classificationAnswers);
        return run(body);
    }

    /** Like {@link #runV4(String)} for another fixture: the first query of pipeline j answers the next {@code sizes[j-1]} articles. */
    protected Ran runArts(String body, List<Art> arts, int... sizes) throws Exception {
        news.reset();
        newsArticlesPerPipeline(arts, sizes);
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        return run(body);
    }

    protected ResultActions getPack(String sid, String runId) throws Exception {
        var b = get("/api/runs/" + runId + "/evidence-pack");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    /** Raw body of getEvidencePack (200 expected). */
    protected String packRaw(String sid, String runId) throws Exception {
        MvcResult r = getPack(sid, runId).andReturn();
        assertThat(r.getResponse().getStatus()).as("getEvidencePack: " + r.getResponse().getContentAsString()).isEqualTo(200);
        return r.getResponse().getContentAsString();
    }

    protected Map<String, Object> pack(Ran r) throws Exception {
        return json(packRaw(r.sid(), r.id()));
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> section(Map<String, Object> pack, String name) {
        return (List<Map<String, Object>>) pack.get(name);
    }

    protected static void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    protected static void assertNotReady(ResultActions r) throws Exception {
        assertError(r, 409, "EVIDENCE_PACK_NOT_READY", "The Evidence Pack is not ready yet");
    }

    /** Cutoff of a pack formatted as in the prompt text: yyyy-MM-dd'T'HH:mm'Z'. */
    protected static String promptCutoff(Object cutoff) {
        return OffsetDateTime.parse((String) cutoff).withOffsetSameInstant(ZoneOffset.UTC)
            .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm'Z'"));
    }

    /** Titles of S001...S004 of fixture V4 (the items E001...E004 of section W01). */
    public static final List<String> V4_TITLES = List.of("WHO approves new pandemic vaccine", "Regulators approve pandemic vaccine",
        "Pandemic vaccine gets approval", "Dock workers strike over humanoid robots");

    /** The lines of a pack text that every body-A pack starts with, up to and including the WILDCARDS values line. */
    protected static List<String> bodyAHead(String generationId, String cutoff) {
        return List.of(
            "ORACUL EVIDENCE PACK",
            "Generation: " + generationId,
            "Cutoff: " + cutoff,
            "SCENARIO",
            "Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years",
            "WILDCARDS",
            "New pandemic: 8 | Humanoid robot boom: 6");
    }

    /**
     * The expected prompt text of fixture (a) (V4 under body A, W01 = S001...S004, W02 empty); {@code sources} = the
     * {@code listRunSources} items S001...S004 in id order.
     */
    protected static String expectedV4Text(String generationId, String cutoff, List<Map<String, Object>> sources) {
        List<String> lines = new java.util.ArrayList<>(bodyAHead(generationId, cutoff));
        lines.add("Wildcard: New pandemic 8/10");
        for (int k = 0; k < 4; k++) {
            Map<String, Object> s = sources.get(k);
            lines.add(String.format("[E%03d] %s · %s · %s · %s", k + 1, V4_TITLES.get(k), s.get("publisher"),
                s.get("publishedAt") == null ? "unknown" : utcDate(s.get("publishedAt")), s.get("url")));
            lines.add("Content not retrieved. Snippet: " + s.get("summary"));
        }
        lines.add("Wildcard: Humanoid robot boom 6/10");
        lines.add("no current sources found");
        return String.join("\n", lines);
    }

    /** The expected prompt text of an empty pack under body A (fixture (b)), after the generation / cutoff lines. */
    protected static final String EMPTY_BODY_A_TAIL = "WILDCARDS\nNew pandemic: 8 | Humanoid robot boom: 6\nWildcard: New pandemic 8/10\n"
        + "no current sources found\nWildcard: Humanoid robot boom 6/10\nno current sources found";

    /** The distinct {@code evidenceId}s over all sections of a pack body, in order of first appearance. */
    protected static List<String> sectionIds(Map<String, Object> pack) {
        return wildcardSections(pack).stream().flatMap(s -> items(s).stream()).map(i -> (String) i.get("evidenceId")).distinct().toList();
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> wildcardSections(Map<String, Object> pack) {
        return (List<Map<String, Object>>) pack.get("wildcardSections");
    }

    @SuppressWarnings("unchecked")
    protected static List<Map<String, Object>> items(Map<String, Object> section) {
        return (List<Map<String, Object>>) section.get("items");
    }
}
