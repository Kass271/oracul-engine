package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** startRun rows that need terminal runs (stage delay PT0S). */
// @trace FR-10
@TestPropertySource(properties = "oracul.run.placeholder-stage-delay=PT0S")
class StartRunCompletedIT extends AbstractRunIT {

    // #8
    @Test
    void completedRunReleasesTheSlot() throws Exception {
        String sid = connectedSid();
        Map<String, Object> first = startOk(sid, B);
        awaitTerminal(sid, (String) first.get("id"));
        Map<String, Object> second = startOk(sid, B);
        assertThat(second.get("id")).isNotEqualTo(first.get("id"));
        assertThat(second.get("status")).isEqualTo("QUEUED");
    }

    // #1: the 202 body is the committed QUEUED row, never a later state, even when the pipeline is instant
    @Test
    void responseBodyIsAlwaysTheQueuedState() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, B);
        assertThat(run.get("status")).isEqualTo("QUEUED");
        assertThat(run.get("stageIndex")).isEqualTo(0);
        assertThat(absent(run, "stage")).isTrue();
        assertThat(absent(run, "completedAt")).isTrue();
    }
}
