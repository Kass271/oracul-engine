package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** getRun rows of generation-runs.md "Slice 04_run-start" with a run that stays active. */
// @trace FR-24
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT30S",
    "oracul.run.executor-threads=32",
})
class GetRunIT extends AbstractRunIT {

    private void assertNotFound(ResultActions r) throws Exception {
        r.andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("RUN_NOT_FOUND"))
            .andExpect(jsonPath("$.message").value("Future not found"));
    }

    // #1
    @Test
    void activeRunReportsTheSecondStageWithLabelAndConfiguration() throws Exception {
        String sid = connectedSid();
        Map<String, Object> started = startOk(sid, A);
        String id = (String) started.get("id");
        Map<String, Object> run = awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        assertThat(run.get("id")).isEqualTo(id);
        assertThat(run.get("status")).isEqualTo("RUNNING");
        assertThat(run.get("stage")).isEqualTo("RESEARCH_STRATEGY");
        assertThat(run.get("stageIndex")).isEqualTo(2);
        assertThat(run.get("stageLabel")).isEqualTo("Building research strategy…");
        assertThat(run.get("stageCount")).isEqualTo(10);
        assertThat(run.get("kind")).isEqualTo("STANDARD");
        assertThat(run.get("configuration")).isEqualTo(json(A));
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS));
        assertThat(OffsetDateTime.parse((String) run.get("updatedAt")))
            .isAfterOrEqualTo(OffsetDateTime.parse((String) run.get("createdAt")));
        assertThat(absent(run, "completedAt")).isTrue();
    }

    // #1: body never carries technical text
    @Test
    void runBodyContainsNoUrlStackTraceClassNameOrToken() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 1);
        String body = getRun(sid, id).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContainPattern("https?://").doesNotContain("Exception").doesNotContain("com.oracul")
            .doesNotContain("\tat ");
        for (String t : stub.issued) assertThat(body).doesNotContain(t);
    }

    // #4
    @Test
    void unknownUuidIs404() throws Exception {
        assertNotFound(getRun(connectedSid(), NO_RUN));
    }

    // #5
    @Test
    void malformedIdIs404NotBadRequest() throws Exception {
        assertNotFound(getRun(connectedSid(), "abc"));
    }

    // #6
    @Test
    void anotherSessionsRunAndMissingCookieAreNotFound() throws Exception {
        String x = connectedSid();
        String y = connectedSid();
        String id = (String) startOk(x, B).get("id");
        assertNotFound(getRun(y, id));
        assertNotFound(getRun(null, id));
        getRun(x, id).andExpect(status().isOk());
    }
}
