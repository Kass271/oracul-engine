package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row #8 of research-pipeline.md "Slice 07_evidence-pack": the pack is not ready until the RANKING step commits. */
// @trace FR-18
@TestPropertySource(properties = {
    "oracul.run.min-stage-duration=PT30S",
    "oracul.run.executor-threads=16",
})
class EvidencePackPendingIT extends AbstractEvidenceIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // the run is pinned on purpose
    }

    @Test
    void thePackIs409WhileTheRunIsStillBeforeRanking() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        assertThat(run.get("status")).isIn("QUEUED", "RUNNING");
        assertNotReady(getPack(sid, id));
        assertThat(run).doesNotContainKey("evidencePackId");
    }
}
