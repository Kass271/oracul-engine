package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** GET /api/runs/{runId}/research rows of research-pipeline.md "Slice 04_run-start - FR-11 test contract". */
// @trace FR-11
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT30S",
    "oracul.run.min-stage-duration=PT1H",
    "oracul.run.executor-threads=32",
})
class RunResearchIT extends AbstractRunIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // runs are kept pending on purpose
    }

    private static final String PROFILE_A = """
        {"darkness":0.9,"optimism":0.2,"realism":0.8,"horizon":"5y","topics":[
          {"key":"biology-new-pandemic","label":"New pandemic","category":"biology","weight":0.8,"custom":false},
          {"key":"robotics-humanoid-boom","label":"Humanoid robot boom","category":"robotics","weight":0.6,"custom":false}]}""";

    private Map<String, Object> researchOf(String sid, String id) throws Exception {
        awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        var r = getResearch(sid, id).andReturn().getResponse();
        assertThat(r.getStatus()).isEqualTo(200);
        return json(r.getContentAsString());
    }

    private void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    // #1
    @Test
    void acceptanceRunExposesItsProfileAndZeroCounts() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        awaitPlan(sid, id, 5000);
        Map<String, Object> body = researchOf(sid, id);
        assertThat(body.get("runId")).isEqualTo(id);
        assertThat(body.get("profile")).isEqualTo(json(PROFILE_A));
        assertThat(body.get("counts")).isEqualTo(json(ZERO_COUNTS));
        // slice 05: the plan is stored before searching (FR-12 row A of ResearchPlanIT carries the full table)
        assertThat(body.get("searchPlan")).isNotNull();
    }

    // #1 DB
    @Test
    void profileIsStoredWithTheRun() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        researchOf(sid, id);
        String stored = jdbc.queryForObject(
            "select cast(research_profile as text) from generation_run where id = cast(? as uuid)", String.class, id);
        assertThat(stored).isNotNull();
        assertThat(json(stored)).isEqualTo(json(PROFILE_A));
    }

    // #2
    @Test
    void runWithoutWildcardsHasEmptyTopics() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        @SuppressWarnings("unchecked")
        Map<String, Object> profile = (Map<String, Object>) researchOf(sid, id).get("profile");
        assertThat((List<?>) profile.get("topics")).isEmpty();
        assertThat(profile.get("horizon")).isEqualTo("1y");
        assertThat(profile.get("darkness")).isEqualTo(0.5);
        assertThat(profile.get("optimism")).isEqualTo(0.5);
        assertThat(profile.get("realism")).isEqualTo(0.8);
    }

    // #3
    @Test
    void profileNotYetStoredIs409ResearchNotReady() throws Exception {
        String sid = connectedSid();
        UUID id = UUID.randomUUID();
        jdbc.update("""
            insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, deadline_at,
                                        created_at, updated_at)
            values (?, 'ORC-2000-01-01-0000', cast(? as uuid), 'STANDARD', 'QUEUED', cast(? as jsonb), cast(? as jsonb),
                    now() + interval '3 minutes', now(), now())""", id, sid, B, ZERO_COUNTS);
        assertError(getResearch(sid, id.toString()), 409, "RESEARCH_NOT_READY", "Research has not started yet");
    }

    // #4
    @Test
    void unknownMalformedAndForeignRunsAre404() throws Exception {
        String x = connectedSid();
        String y = connectedSid();
        String id = (String) startOk(x, B).get("id");
        assertError(getResearch(x, NO_RUN), 404, "RUN_NOT_FOUND", "Future not found");
        assertError(getResearch(x, "abc"), 404, "RUN_NOT_FOUND", "Future not found");
        assertError(getResearch(y, id), 404, "RUN_NOT_FOUND", "Future not found");
    }
}
