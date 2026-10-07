package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** FR-51 (d), timeout variant: the QUERY_GENERATION calls outlive oracul.openai.timeout, every pipeline falls back to templates. */
// @trace FR-12, FR-44, FR-47, FR-51, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=7",
    "oracul.openai.timeout=PT0.5S",
})
class ResearchPlanTimeoutIT extends AbstractRunIT {

    @Test
    void generationBeyondTheTimeoutFallsBackToTemplates() throws Exception {
        // run-control.md FR-47: the empty-news run goes on speculatively, so only the query generation is slow
        var fallback = responses.defaultResponder();
        responses.responder = req -> "QUERY_GENERATION".equals(StubResponses.purpose(req))
            ? StubResponses.delayed(StubResponses.completed(StubResponses.defaultGeneration(req.inputText())), 3000)
            : fallback.apply(req);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).isEqualTo("COMPLETED");
        assertThat(absent(run, "failure")).isTrue();
        Map<String, Object> research = researchBody(sid, id);
        assertThat(PlanJson.plan(research).get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(PlanJson.pipelines(research)).hasSize(2).allSatisfy(p -> assertThat(p.get("queryMode")).isEqualTo("TEMPLATE_FALLBACK"));
        assertThat(PlanJson.queryTexts(research)).as("6 template queries").containsExactly(
            "New pandemic extreme scenario disaster", "New pandemic unprecedented scale catastrophe", "New pandemic radical upheaval collapse",
            "Humanoid robot boom serious disruption crisis", "Humanoid robot boom major escalation conflict",
            "Humanoid robot boom growing concerns threat");
        assertThat(news.requests).as("6 template queries as 6 requests").hasSize(6);
        assertThat(responses.requests.stream().filter(r -> "QUERY_GENERATION".equals(StubResponses.purpose(r))).count())
            .as("one call per pipeline, no retry").isEqualTo(2);
    }
}
