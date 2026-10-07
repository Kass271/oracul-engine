package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-62 "usable without any other code change", seen from startRun: a context started with a style file that also
 * lists the NARRATIVE profile dickens accepts it (202, stored) and rejects it with mode OFFICIAL.
 */
// @trace FR-63
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT30S",
    "oracul.run.executor-threads=32",
})
class StartRunWritingStyleDickensIT extends AbstractRunIT {

    @DynamicPropertySource
    static void styleFile(DynamicPropertyRegistry registry) {
        registry.add("oracul.writing-styles.location", () -> DickensStyleFile.LOCATION);
    }

    @Override
    protected boolean awaitRunsAfterEach() {
        return false;
    }

    private static String body(String style) {
        return "{\"realism\":8,\"darkness\":5,\"optimism\":5,\"horizon\":\"1y\",\"wildcards\":[],\"customWildcards\":[],"
            + "\"output\":{\"story\":true,\"illustration\":false,\"writingStyle\":" + style + ",\"language\":\"en\"}}";
    }

    // @trace FR-63
    @Test
    @SuppressWarnings("unchecked")
    void narrativeDickensIsAcceptedAndStored() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, body("{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens\"}"));
        Map<String, Object> out = (Map<String, Object>) ((Map<String, Object>) run.get("configuration")).get("output");
        assertThat(out.get("writingStyle")).isEqualTo(Map.of("mode", "NARRATIVE", "profileId", "dickens"));
        Map<String, Object> got = awaitRun(sid, (String) run.get("id"), 5000, m -> true);
        assertThat(((Map<String, Object>) ((Map<String, Object>) got.get("configuration")).get("output")).get("writingStyle"))
            .isEqualTo(Map.of("mode", "NARRATIVE", "profileId", "dickens"));
    }

    // @trace FR-63
    @Test
    void officialDickensIsRejected() throws Exception {
        String sid = connectedSid();
        int before = runCount();
        startRun(sid, body("{\"mode\":\"OFFICIAL\",\"profileId\":\"dickens\"}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.message").value("Unknown writing style"));
        assertThat(runCount()).isEqualTo(before);
    }

    // @trace FR-63
    @Test
    void theShippedProfilesStillWorkWithTheExtendedFile() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, body("{\"mode\":\"OFFICIAL\",\"profileId\":\"official-briefing\"}"));
        assertThat(run.get("status")).isEqualTo("QUEUED");
    }
}
