package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubNews;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * wildcard-evidence.md "Slice 09_wildcard-results" test plan 3 (FR-59 acceptance 3 / NFR-10): the search window cuts the queries of
 * W02, which are FAILED (never EMPTY) with {@code articlesReturned} 0; W01 answers at once; the note names exactly the wildcard whose
 * group is empty and the run completes with a story. Windows: query generation PT1S, search PT2S, stage budget PT3S.
 */
// @trace FR-59
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.news.google.rate-limit-wait=PT0.05S",
    "oracul.news.article-fetch-timeout=PT1S",
    "oracul.search.query-generation-window=PT1S",
    "oracul.search.search-window=PT2S",
    "oracul.search.stage-budget=PT3S",
})
class MissingWildcardSourcesWindowIT extends AbstractStoryIT {

    @SuppressWarnings("unchecked")
    @Test
    void queriesCutOffByTheSearchWindowAreFailedAndTheirWildcardIsNamed() throws Exception {
        news.reset();
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= 4; i++) {
            arts.add(new Art("w-" + i, "reuters.com", "Window test article " + i));
            news.site("w-" + i, "Publisher " + i);
        }
        Function<StubNews.Request, StubNews.Reply> w1 = StubNews.firstQueryItems(List.of(arts.stream().map(this::itemOf).toList()));
        // a request of pipeline 2 ("W02 stub query <i>") is answered only after 4 s, the others at once
        news.responder = req -> {
            StubNews.Reply reply = w1.apply(req);
            int[] q = StubNews.stubQuery(req);
            return q != null && q[0] == 2 ? new StubNews.Reply(reply.status(), reply.contentType(), reply.body(), 4000) : reply;
        };
        Ran r = run(MissingWildcardSourcesIT.PE);
        assertStoryCompleted(r.run());

        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        List<Map<String, Object>> pipelines = (List<Map<String, Object>>) plan.get("pipelines");
        List<Map<String, Object>> q1 = (List<Map<String, Object>>) pipelines.get(0).get("queries");
        List<Map<String, Object>> q2 = (List<Map<String, Object>>) pipelines.get(1).get("queries");
        assertThat(q1.get(0).get("id")).isEqualTo("Q01");
        assertThat(q1.get(0).get("status")).isEqualTo("OK");
        assertThat(((Number) q1.get(0).get("articlesReturned")).intValue()).isEqualTo(4);
        assertThat(q2.stream().map(q -> q.get("id")).toList()).containsExactly("Q04", "Q05", "Q06");
        for (Map<String, Object> q : q2) {
            assertThat(q.get("status")).as("cut off or never sent: FAILED, never EMPTY (" + q.get("id") + ")").isEqualTo("FAILED");
            assertThat(((Number) q.get("articlesReturned")).intValue()).isZero();
        }
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("kind", "MISSING_WILDCARD_SOURCES");
        expected.put("message", "No current sources found for: Energy crisis. This part of the future is speculative.");
        expected.put("coreItems", 4);
        expected.put("coreNeeded", 0);
        expected.put("wildcardsWithoutSources", List.of("Energy crisis"));
        assertThat(r.run().get("evidenceNote")).isEqualTo(expected);
        assertThat(((Number) counts(r.run()).get("searches")).intValue()).isEqualTo(6);
    }
}
