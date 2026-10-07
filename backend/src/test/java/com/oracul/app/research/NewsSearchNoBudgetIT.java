package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractDeadlineIT;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 / FR-48 as changed by FR-52 / NFR-10: no request starts at or after the end of the search
 * window ({@code oracul.search.search-window}, default PT60S, measured on the injected Clock from the start of
 * RESEARCH_STRATEGY). The test holds QUERY_GENERATION (one call per pipeline, FR-51), advances the {@code MutableClock} by 61 s
 * (the 3 minute run deadline is not reached) and releases it: the query-generation window (30 s) and the search window are over
 * before the first request, so both pipelines use their templates (reason WINDOW) and nothing is sent.
 */
// @trace FR-44, FR-47, FR-49, FR-51, FR-52
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.run.executor-threads=10",
})
class NewsSearchNoBudgetIT extends AbstractDeadlineIT {

    @Test
    @SuppressWarnings("unchecked")
    void aSearchWindowThatEndedBeforeTheSearchSendsNoRequestAndTheRunGoesOnSpeculatively(CapturedOutput out) throws Exception {
        freshStubs();
        news.responder = req -> StubNews.rss();
        responses.gate("QUERY_GENERATION");
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived("QUERY_GENERATION", 2, Duration.ofSeconds(10))).as("both QUERY_GENERATION calls arrived").isTrue();
        clock.advance(d(61));
        responses.release("QUERY_GENERATION");
        Map<String, Object> run = awaitDone(sid, id);
        // run-control.md FR-47: nothing could be sent -> every query FAILED, 0 sources, the run goes on (no NEWS_UNAVAILABLE)
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).as("Google is not asked").isEmpty();
        assertThat(news.paths).as("no request of any kind reaches the news stub").doesNotContain("/rss/search");
        Map<String, Object> research = researchBody(sid, id);
        List<Map<String, Object>> queries = PlanJson.queries(research);
        assertThat(queries).hasSize(6).allSatisfy(q -> assertThat(q.get("status")).as("never EMPTY: nothing was asked").isEqualTo("FAILED"));
        // the generation window ended before the answers were read: templates for both pipelines
        assertThat(PlanJson.plan(research).get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(PlanJson.pipelines(research)).hasSize(2).allSatisfy(p -> assertThat(p.get("queryMode")).isEqualTo("TEMPLATE_FALLBACK"));
        assertThat(PlanJson.queryTexts(research)).containsExactly(
            "New pandemic extreme scenario disaster", "New pandemic unprecedented scale catastrophe", "New pandemic radical upheaval collapse",
            "Humanoid robot boom serious disruption crisis", "Humanoid robot boom major escalation conflict",
            "Humanoid robot boom growing concerns threat");
        assertThat(responses.requests.stream().map(StubResponses::purpose).toList())
            .containsExactly("QUERY_GENERATION", "QUERY_GENERATION", "SCENARIO_GENERATION", "SCENARIO_CRITIC", "STORY_WRITING");
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("google news request skipped: search window ended").doesNotContain("stub query");
        assertThat(log).contains("query generation fell back to templates: pipeline=W01 reason=WINDOW")
            .contains("query generation fell back to templates: pipeline=W02 reason=WINDOW")
            .as("the fallback line names no label, query text or answer").doesNotContain("extreme scenario");
    }
}
