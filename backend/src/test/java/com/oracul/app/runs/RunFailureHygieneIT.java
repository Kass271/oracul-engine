package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.research.ResearchProfileFactory;
import com.oracul.app.research.StubResponses;
import com.oracul.app.session.SessionService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.ResultActions;

/** generation-runs.md "Slice 11_run-failures" RunFailureHygieneIT rows 1-4: no internals in failures or error bodies. */
// @trace FR-32
class RunFailureHygieneIT extends AbstractDeadlineIT {

    private static final String INTERNAL = "{\"code\":\"INTERNAL_ERROR\",\"message\":\"Something went wrong — try again\"}";

    @MockitoSpyBean
    ResearchProfileFactory profiles;
    @MockitoSpyBean
    RunService runService;
    @MockitoSpyBean
    SessionService sessionService;

    private void assertInternalError(ResultActions r) throws Exception {
        r.andExpect(status().isInternalServerError())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
            .andExpect(jsonPath("$.message").value("Something went wrong — try again"));
        assertThat(json(r)).isEqualTo(json(INTERNAL));
    }

    // #1
    @Test
    void anUnexpectedPipelineExceptionIsStoredAsInternalErrorWithoutItsText() throws Exception {
        freshStubs();
        doThrow(new IllegalStateException("boom http://internal.example {\"x\":1} Bearer sk-test at com.oracul.app.X"))
            .when(profiles).from(any());
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertFailed(run, "INTERNAL_ERROR", "Something went wrong — try again", "UNDERSTANDING", 1);
        String raw = getRun(sid, id).andReturn().getResponse().getContentAsString();
        for (String forbidden : new String[] {"boom http","IllegalState", "http://internal", "Bearer", "sk-test", "com.oracul"}) {
            assertThat(raw).as("raw getRun body").doesNotContain(forbidden);
        }
        assertSlotReleased(sid);
    }

    // #2
    @Test
    void aStoryRateLimitFailsTheRunWithoutRetryAndWithoutProviderText() throws Exception {
        freshStubs();
        always(STORY, StubResponses.status(429, PROVIDER_BODY));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 30_000, m -> "FAILED".equals(m.get("status")));
        assertThat(run.get("failure")).isEqualTo(json(
            "{\"code\":\"CHATGPT_RATE_LIMITED\",\"message\":\"ChatGPT plan limit reached — try again later\"}"));
        assertThat(requests(STORY)).hasSize(1);
        String raw = getRun(sid, id).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("rate_limited").doesNotContain("429").doesNotContain("PROVIDER-SECRET");
    }

    // #3
    @Test
    void anUnexpectedControllerExceptionAnswersTheFixedInternalErrorBody() throws Exception {
        String sid = connectedSid();
        doThrow(new RuntimeException("SELECT * FROM generation_run password=x")).when(runService).get(any(), any());
        assertInternalError(getRun(sid, NO_RUN));
        doThrow(new RuntimeException("SELECT * FROM generation_run password=x")).when(runService).start(any(), any());
        assertInternalError(startRun(sid, B));
    }

    // #4
    @Test
    void aFailingSessionLookupAnswersTheSameBody() throws Exception {
        String sid = connectedSid();
        doThrow(new RuntimeException("db down password=x")).when(sessionService).resolve(any());
        assertInternalError(getRun(sid, NO_RUN));
        assertInternalError(getRun(null, NO_RUN));
    }
}
