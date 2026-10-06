package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.TestPropertySource;

/** Row 15 (timeout variant): a Responses call slower than oracul.openai.timeout is retried twice (3 requests), then CHATGPT_UNAVAILABLE. */
// @trace FR-14, FR-15, FR-39
@TestPropertySource(properties = "oracul.openai.timeout=PT0.5S")
class EventTimeoutIT extends AbstractEventIT {

    @ParameterizedTest(name = "timeout twice on {0}")
    @ValueSource(strings = {NORMALIZATION, CLASSIFICATION})
    void aTimeoutTwiceFailsTheRunWithChatGptUnavailable(String purpose) throws Exception {
        newsArticles(v4());
        if (!NORMALIZATION.equals(purpose)) script(NORMALIZATION, N_V4);
        if (!CLASSIFICATION.equals(purpose)) script(CLASSIFICATION, C_V4);
        always(purpose, StubResponses.delayed(StubResponses.completed("{}"), 2000));
        Ran r = run(A);
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(
            "{\"code\":\"CHATGPT_UNAVAILABLE\",\"message\":\"ChatGPT is temporarily unavailable — try again in a few minutes\"}"));
        assertThat(run.get("stage")).isEqualTo("CONNECTING_SIGNALS");
        assertThat(run.get("stageIndex")).isEqualTo(5);
        assertThat(requests(purpose)).as("first attempt + 2 retries").hasSize(3);
        assertThat(json(eventsRaw(r.sid(), r.id()))).isEqualTo(json("{\"items\":[]}"));
        assertThat(counts(run).get("uniqueEvents")).isEqualTo(0);
        for (Map<String, Object> s : sourceItems(r.sid(), r.id())) assertThat((List<?>) s.get("entities")).isEmpty();
    }
}
