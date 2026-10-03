package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row #16 of scenario-reasoning.md "Slice 08_validated-scenario": no scenario while the run is still researching. */
// @trace FR-20
@TestPropertySource(properties = {
    "oracul.run.min-stage-duration=PT30S",
    "oracul.run.executor-threads=16",
})
class StructuredScenarioPendingIT extends AbstractReasoningIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // the run is pinned on purpose
    }

    @Test
    void theScenarioIs409WhileTheRunIsStillInResearchStrategy() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        assertThat(run.get("status")).isIn("QUEUED", "RUNNING");
        assertScenarioNotReady(getStructured(sid, id));
    }
}
