package com.oracul.app.result;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** future-result.md "Slice 09_future-story" IT #12 (active run): no result while the run is pinned in research. */
// @trace FR-23
@TestPropertySource(properties = {
    "oracul.run.min-stage-duration=PT30S",
    "oracul.run.executor-threads=16",
})
class FutureResultPendingIT extends AbstractStoryIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // the run is pinned on purpose
    }

    @Test
    void theResultIs409WhileTheRunIsActive() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        awaitRun(sid, id, 5000, m -> ((Number) m.get("stageIndex")).intValue() >= 2);
        assertResultNotReady(getResult(sid, id));
    }
}
