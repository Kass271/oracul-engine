package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Integration row of scenario-panel.md "Slice 18_custom-wildcards-output". */
// @trace FR-5
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=6",
})
class CustomWildcardRunIT extends AbstractRunIT {

    private static final String BODY = """
        {"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],
         "customWildcards":[{"label":"  Ocean desalination boom ","intensity":7},{"label":"Mars colony","intensity":3}],
         "output":{"story":true,"illustration":false}}""";

    private static final String TRIMMED = """
        [{"label":"Ocean desalination boom","intensity":7},{"label":"Mars colony","intensity":3}]""";

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    // @trace FR-5
    @Test
    void acceptedRunStoresTrimmedLabelsAndResearchesCustomTopics() throws Exception {
        String sid = connectedSid();
        Map<String, Object> started = startOk(sid, BODY);
        String id = (String) started.get("id");
        assertThat(((Map<String, Object>) started.get("configuration")).get("customWildcards"))
            .isEqualTo(json("{\"c\":" + TRIMMED + "}").get("c"));
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(((Map<String, Object>) run.get("configuration")).get("customWildcards"))
            .isEqualTo(json("{\"c\":" + TRIMMED + "}").get("c"));
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");

        Map<String, Object> research = researchBody(sid, id);
        Map<String, Object> profile = (Map<String, Object>) research.get("profile");
        assertThat(profile.get("topics")).isEqualTo(json("{\"t\":["
            + "{\"key\":\"custom-1\",\"label\":\"Ocean desalination boom\",\"category\":\"custom\",\"weight\":0.7,\"custom\":true},"
            + "{\"key\":\"custom-2\",\"label\":\"Mars colony\",\"category\":\"custom\",\"weight\":0.3,\"custom\":true}]}").get("t"));

        List<Map<String, Object>> intents = list(((Map<String, Object>) research.get("searchPlan")).get("intents"));
        assertThat(intents.get(0).get("bucket")).isEqualTo("WILDCARD");
        assertThat(intents.get(0).get("topicKey")).isEqualTo("custom-1");
        assertThat(intents.get(0).get("description")).isEqualTo("Current developments related to Ocean desalination boom");
        assertThat(intents.get(0).get("drivenBy")).asList().first().isEqualTo("Ocean desalination boom 7/10");
        assertThat(intents.get(1).get("bucket")).isEqualTo("WILDCARD");
        assertThat(intents.get(1).get("topicKey")).isEqualTo("custom-2");
        assertThat(intents.get(1).get("description")).isEqualTo("Current developments related to Mars colony");
        assertThat(intents.get(1).get("drivenBy")).asList().first().isEqualTo("Mars colony 3/10");
        for (Map<String, Object> i : intents) {
            if ("ADJACENT".equals(i.get("bucket"))) assertThat(i.get("category")).isNull();
        }
    }

    // @trace FR-5
    @Test
    void rejectedBodyCreatesNoRun() throws Exception {
        String sid = connectedSid();
        int before = runCount();
        String four = BODY.replace("\"customWildcards\":[", "\"customWildcards\":[{\"label\":\"C\",\"intensity\":1},"
            + "{\"label\":\"D\",\"intensity\":1},");
        startRun(sid, four).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        String dup = BODY.replace("Mars colony", "ocean desalination BOOM");
        startRun(sid, dup).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        assertThat(runCount()).isEqualTo(before);
    }
}
