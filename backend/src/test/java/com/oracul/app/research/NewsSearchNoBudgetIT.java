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
 * RESEARCH_STRATEGY). The test holds QUERY_EXPANSION, advances the {@code MutableClock} by 61 s (the 3 minute run deadline
 * is not reached) and releases it: the search window is over before the first request, so nothing is sent.
 */
// @trace FR-44, FR-47, FR-49, FR-52
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
        responses.gate("QUERY_EXPANSION");
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived("QUERY_EXPANSION", 1, Duration.ofSeconds(10))).as("QUERY_EXPANSION arrived").isTrue();
        clock.advance(d(61));
        responses.release("QUERY_EXPANSION");
        Map<String, Object> run = awaitDone(sid, id);
        // run-control.md FR-47: nothing could be sent -> every query FAILED, 0 sources, the run goes on (no NEWS_UNAVAILABLE)
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).as("Google is not asked").isEmpty();
        assertThat(news.paths).as("no request of any kind reaches the news stub").doesNotContain("/rss/search");
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(20).allSatisfy(q -> assertThat(q.get("status")).as("never EMPTY: nothing was asked").isEqualTo("FAILED"));
        assertThat(responses.requests.stream().map(StubResponses::purpose).toList())
            .containsExactly("QUERY_EXPANSION", "SCENARIO_GENERATION", "SCENARIO_CRITIC", "STORY_WRITING");
        assertThat(out.getOut() + out.getErr()).contains("google news request skipped: search window ended").doesNotContain("stub query");
    }
}
