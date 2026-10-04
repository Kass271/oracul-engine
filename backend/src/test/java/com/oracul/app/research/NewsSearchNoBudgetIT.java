package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/** phase-02 news-search.md FR-44: no request starts at or after the search-budget end; a budget of 0 sends nothing. */
// @trace FR-44, FR-47
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=10",
    "oracul.news.search-budget=PT0S",
})
class NewsSearchNoBudgetIT extends AbstractRunIT {

    @Test
    @SuppressWarnings("unchecked")
    void aBudgetOfZeroSendsNoRequestAndTheRunGoesOnSpeculatively(CapturedOutput out) throws Exception {
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        // run-control.md FR-47: nothing could be sent -> every query FAILED, 0 sources, the run goes on (no NEWS_UNAVAILABLE)
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(gdelt.requests).isEmpty();
        assertThat(gdelt.rssRequests).as("Google is not asked either").isEmpty();
        List<Map<String, Object>> queries =
            (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(20).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("FAILED"));
        assertThat(responses.requests.stream().map(StubResponses::purpose).toList())
            .containsExactly("QUERY_EXPANSION", "SCENARIO_GENERATION", "SCENARIO_CRITIC", "STORY_WRITING");
        assertThat(out.getOut() + out.getErr()).contains("news request skipped: search budget exhausted").doesNotContain("stub query");
    }
}
