package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 21 of research-pipeline.md "Slice 06_events": events are empty until the CONNECTING_SIGNALS step commits. */
// @trace FR-14
@TestPropertySource(properties = {
    "oracul.run.min-stage-duration=PT30S",
    "oracul.run.executor-threads=16",
})
class EventPendingIT extends AbstractEventIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // the run is pinned on purpose
    }

    @Test
    void eventsAreEmptyWhileTheRunIsStillBeforeConnectingSignals() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        assertThat(run.get("status")).isIn("QUEUED", "RUNNING");
        assertThat(json(eventsRaw(sid, id))).isEqualTo(json("{\"items\":[]}"));
    }
}
