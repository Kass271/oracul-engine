package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** future-result.md "Slice 09_future-story" IT #11: stage 10 is observable and no result exists before COMPLETED. */
// @trace FR-23
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class StoryWritingStageIT extends AbstractStoryIT {

    @Test
    void writingStoryIsObservedRunningWithoutHeadlineAndTheResultIs409UntilCompleted() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        boolean sawWriting = false;
        Map<String, Object> run = Map.of();
        long end = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < end) {
            run = json(getRun(sid, id).andReturn().getResponse().getContentAsString());
            if ("WRITING_STORY".equals(run.get("stage")) && "RUNNING".equals(run.get("status"))) {
                sawWriting = true;
                assertThat(absent(run, "headline")).as("no headline while writing: " + run).isTrue();
                assertResultNotReady(getResult(sid, id));
            }
            if ("COMPLETED".equals(run.get("status")) || "FAILED".equals(run.get("status"))) break;
            Thread.sleep(200);
        }
        assertThat(sawWriting).as("WRITING_STORY observed with status RUNNING").isTrue();
        assertStoryCompleted(run);
        assertThat(getResult(sid, id).andReturn().getResponse().getStatus()).isEqualTo(200);
    }
}
