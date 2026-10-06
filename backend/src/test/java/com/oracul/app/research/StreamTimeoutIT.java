package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** phase-02 chatgpt-inference.md FR-38 step 3: a stream longer than oracul.openai.stream-timeout is CHATGPT_INCOMPLETE. */
// @trace FR-38
@TestPropertySource(properties = {"oracul.openai.stream-timeout=PT1S", "oracul.openai.timeout=PT5S"})
class StreamTimeoutIT extends AbstractPlanUsageIT {

    /** A valid, complete stream whose pieces arrive 400 ms apart: 5 pieces take longer than the 1 s stream timeout. */
    private static StubResponses.Reply slowCompleteStream(String text) {
        String completed = "event: response.completed\ndata: " + StubResponses.completedEvent(text) + "\n\n";
        return StubResponses.sseChunks(400, "event: response.created\ndata: " + StubResponses.createdEvent() + "\n\n",
            "event: response.output_text.delta\ndata: " + StubResponses.deltaEvent("x") + "\n\n", ": keep-alive\n\n",
            ": keep-alive\n\n", completed);
    }

    @Test
    void aStreamLongerThanTheStreamTimeoutFailsTheStageEvenIfItWouldComplete() throws Exception {
        newsArticles(v4());
        script(CLASSIFICATION, C_V4);
        always(NORMALIZATION, req -> slowCompleteStream(N_V4));
        Ran r = runWithin(A, 20_000);
        assertFailure(r.run(), "CHATGPT_INCOMPLETE", M_INCOMPLETE, null);
        assertThat(requests(NORMALIZATION)).as("a stream timeout is not retried").hasSize(1);
        assertThat(eventRows(r.id())).isEqualTo(0);
    }

    @Test
    void aStreamThatFitsIntoTheStreamTimeoutIsAnswered() throws Exception {
        newsArticles(v4());
        script(CLASSIFICATION, C_V4);
        always(NORMALIZATION, req -> StubResponses.sseChunks(100, "event: response.created\ndata: " + StubResponses.createdEvent() + "\n\n",
            ": keep-alive\n\n", "event: response.completed\ndata: " + StubResponses.completedEvent(N_V4) + "\n\n"));
        Ran r = runWithin(A, 20_000);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        Map<String, Object> counts = counts(r.run());
        assertThat(counts.get("uniqueEvents")).isEqualTo(2);
    }

    @Test
    void aSlowStreamOfTheQueryExpansionFallsBackToTemplates() throws Exception {
        responses.responder = req -> "QUERY_EXPANSION".equals(StubResponses.purpose(req))
            ? slowCompleteStream("{\"queries\":[]}") : responses.defaultResponder().apply(req);
        Ran r = runWithin(A, 20_000);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        assertThat(plan.get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
    }
}
