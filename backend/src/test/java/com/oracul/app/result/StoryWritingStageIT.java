package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * future-result.md "Slice 09_future-story" IT #11: stage 10 is observable and no result exists before COMPLETED.
 * The STORY_WRITING answer is held at the stub (gate) so the stage is observed deterministically, without racing a stage timer.
 */
// @trace FR-23
class StoryWritingStageIT extends AbstractStoryIT {

    @Test
    void writingStoryIsObservedRunningWithoutHeadlineAndTheResultIs409UntilCompleted() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        responses.gate(STORY);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run;
        try {
            assertThat(responses.awaitArrived(STORY, 1, Duration.ofSeconds(30))).as("STORY_WRITING request arrived and is held").isTrue();
            run = awaitRun(sid, id, 10_000, m -> "WRITING_STORY".equals(m.get("stage")) && "RUNNING".equals(m.get("status")));
            assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
            assertThat(run.get("status")).isEqualTo("RUNNING");
            assertThat(run.get("stageIndex")).isEqualTo(10);
            assertThat(absent(run, "headline")).as("no headline while writing: " + run).isTrue();
            assertResultNotReady(getResult(sid, id));
        } finally {
            responses.release(STORY);
        }
        run = awaitRun(sid, id, 30_000, m -> "COMPLETED".equals(m.get("status")) || "FAILED".equals(m.get("status")));
        assertStoryCompleted(run);
        assertThat(getResult(sid, id).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
