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
 * Shared driver of the slice 07 tests (research-pipeline.md "Slice 07_evidence-pack"): fixture V4 with the scripted
 * answers N-V4 / C-V4, the Evidence Pack endpoint, and the expected V4 pack text. Talks HTTP only.
 */
public abstract class AbstractEvidenceIT extends AbstractEventIT {

    /** Body A-bright: body A with darkness 2 and optimism 9. */
    public static final String A_BRIGHT = A.replace("\"darkness\":9", "\"darkness\":2").replace("\"optimism\":2", "\"optimism\":9");

    public static final String EV1_SUMMARY =
        "Health regulators approved a new pandemic vaccine. Reports differ on the number of doses approved.";
    public static final String EV2_SUMMARY = "Dock workers strike over humanoid robots.";

    /** Fresh GDELT stub (first request returns V4), scripted N-V4 and the given classification answers (default C-V4). */
    protected Ran runV4(String body) throws Exception {
        return runV4(body, N_V4, C_V4);
    }

    protected Ran runV4(String body, String normalization, String... classificationAnswers) throws Exception {
        gdelt.reset();
        gdeltArticles(v4());
        script(NORMALIZATION, normalization);
        script(CLASSIFICATION, classificationAnswers);
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

    /** The expected prompt text of fixture V4 / N-V4 / C-V4 under body A. */
    protected static String expectedV4Text(String generationId, String cutoff, String s4Date) {
        return String.join("\n",
            "ORACUL EVIDENCE PACK",
            "Generation: " + generationId,
            "Cutoff: " + cutoff,
            "SCENARIO",
            "Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years",
            "WILDCARDS",
            "New pandemic: 8 | Humanoid robot boom: 6",
            "CORE EVIDENCE",
            "[E001] " + s4Date + " · labour · " + EV2_SUMMARY + " · sources: Stub Site (S004) · quality 0.85",
            "SUPPORTING EVIDENCE",
            "none",
            "COUNTER-SIGNALS",
            "[E002] 2026-10-01 · health · " + EV1_SUMMARY
                + " · sources: Stub Site (S001), Stub Site (S002), Stub Site (S003) · quality 0.95");
    }
}
