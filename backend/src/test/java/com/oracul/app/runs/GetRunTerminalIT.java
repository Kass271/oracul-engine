package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** getRun row #2: pipeline without delay ends COMPLETED at WRITING_STORY. */
// @trace FR-24
// slice 05: stage SEARCHING runs the 20 queries of the default budget (every one EMPTY), so counts.searches is 20.
@TestPropertySource(properties = "oracul.run.placeholder-stage-delay=PT0S")
class GetRunTerminalIT extends AbstractRunIT {

    @Test
    void finishedRunIsCompletedAtTheLastStage() throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, B).get("id");
        Map<String, Object> run = awaitTerminal(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(run.get("stageIndex")).isEqualTo(10);
        assertThat(run.get("stageLabel")).isEqualTo("Writing from the future…");
        assertThat(run.get("stageCount")).isEqualTo(10);
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(absent(run, "failure")).isTrue();
        assertThat(absent(run, "headline")).isTrue();
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS.replace("\"searches\":0", "\"searches\":20")));
        assertThat(run.get("configuration")).isEqualTo(json(B));
    }
}
