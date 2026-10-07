package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** Row 3 of the FR-12 integration table, timeout variant: the call outlives oracul.openai.timeout. */
// @trace FR-12, FR-44, FR-47, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=7",
    "oracul.openai.timeout=PT0.5S",
})
class ResearchPlanTimeoutIT extends AbstractRunIT {

    @Test
    void expansionBeyondTheTimeoutFallsBackToTemplates() throws Exception {
        // run-control.md FR-47: the empty-news run goes on speculatively, so only the expansion is slow
        var fallback = responses.defaultResponder();
        responses.responder = req -> "QUERY_EXPANSION".equals(StubResponses.purpose(req))
            ? StubResponses.delayed(StubResponses.completed("{\"queries\":[]}"), 3000)
            : fallback.apply(req);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        @SuppressWarnings("unchecked")
        Map<String, Object> plan = (Map<String, Object>) researchBody(sid, id).get("searchPlan");
        assertThat(plan.get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat((java.util.List<?>) plan.get("queries")).hasSize(20);
        assertThat(news.requests).as("20 template queries as 20 requests").hasSize(20);
        assertThat(responses.requests.stream().filter(r -> "QUERY_EXPANSION".equals(StubResponses.purpose(r))).count())
            .as("no retry").isEqualTo(1);
    }
}
