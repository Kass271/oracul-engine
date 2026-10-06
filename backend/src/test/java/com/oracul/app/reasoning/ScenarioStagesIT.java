package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row #18 of scenario-reasoning.md "Slice 08_validated-scenario": stages 7-9 are real and observable in order. */
// @trace FR-19
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class ScenarioStagesIT extends AbstractReasoningIT {

    @Test
    void theRunPassesExploringChallengingAndConstructingInOrder() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        List<String> stages = new ArrayList<>();
        Map<String, Object> run = Map.of();
        long end = System.currentTimeMillis() + 60_000;
        while (System.currentTimeMillis() < end) {
            run = json(getRun(sid, id).andReturn().getResponse().getContentAsString());
            String stage = (String) run.get("stage");
            if (stage != null && (stages.isEmpty() || !stages.get(stages.size() - 1).equals(stage))) stages.add(stage);
            if ("COMPLETED".equals(run.get("status")) || "FAILED".equals(run.get("status"))) break;
            Thread.sleep(200);
        }
        assertThat(run.get("status")).as("run: " + run + " stages: " + stages).isEqualTo("COMPLETED");
        List<String> seen = stages.stream().filter(s -> List.of("EXPLORING_FUTURES", "CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO").contains(s)).toList();
        assertThat(seen).as("observed stages " + stages).containsExactly("EXPLORING_FUTURES", "CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO");
        assertThat(requests(GEN)).hasSize(1);
    }
}
