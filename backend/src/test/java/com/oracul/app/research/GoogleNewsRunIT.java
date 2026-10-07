package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-48 + FR-46 through whole runs (phase-03 FR-49: Google is the only provider): Google News RSS
 * answers every query of fixture F240 (F240_BODY: 9 wildcards x 2 queries; items with " - Reuters" title suffixes), the run keeps 28 of the 205 usable
 * candidates (FR-53: shared + 3 per pipeline), resolves their links through the article fetch and exposes publisherUrl; failing and empty feeds.
 */
// @trace FR-46, FR-48, FR-49, FR-50, FR-52, FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
})
class GoogleNewsRunIT extends AbstractEventIT {

    private static final String REUTERS = "https://www.reuters.com";

    @SuppressWarnings("unchecked")
    @Test
    void f240ThroughGoogleKeepsTwentyEightResolvedSourcesWithPublisherUrl() throws Exception {
        newsF240();
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("every item of the answered queries").isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).as("FR-53: distinct usable candidates of all pipelines").isEqualTo(205);
        assertThat(counts.get("sourcesKept")).as("FR-53: shared + 3 x 9 own, the 4th round is not cut (28 < 30)").isEqualTo(28);
        assertThat(news.requests).as("18 queries go out as 18 requests").hasSize(18);
        assertThat(news.requests.stream().map(r -> r.elements().size()).distinct().toList()).as("one text per request").containsExactly(1);
        assertThat(news.paths).as("no request to a former provider path").noneMatch(p -> p.startsWith("/api/v2/doc"));

        Map<String, Object> research = researchBody(sid, id);
        List<Map<String, Object>> pipelines = PlanJson.pipelines(research);
        assertThat(pipelines).as("F240_BODY: nine pipelines of two queries").hasSize(9)
            .allSatisfy(p -> assertThat(PlanJson.queriesOf(p)).hasSize(2));
        int returned = 0;
        for (Map<String, Object> p : pipelines) {
            for (Map<String, Object> q : PlanJson.queriesOf(p)) {
                assertThat(q.get("status")).as("every query was answered").isEqualTo("OK");
                returned += ((Number) q.get("articlesReturned")).intValue();
            }
        }
        assertThat(returned).isEqualTo(240);

        List<String> kept = F240Support.keptNames(9, false);
        assertThat(kept).hasSize(28);
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(28);
        String base = news.baseUrl();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            String want = kept.get(i);
            assertThat(s.get("id")).as("Evidence order numbering").isEqualTo(String.format("S%03d", i + 1));
            assertThat(s.get("url")).as("the Google link resolved to the article page").isEqualTo(base + "/articles/" + want);
            assertThat(s.get("metadataFetched")).isEqualTo(true);
            assertThat(s.get("publisher")).as("og:site_name of the page").isEqualTo("Stub Site");
            assertThat(s.get("publisherUrl")).isEqualTo(REUTERS);
            assertThat(s.get("sourceType")).isEqualTo("NEWS");
            assertThat(s.get("sourceQuality")).isEqualTo(0.85);
            assertThat((String) s.get("title")).as("title without the source suffix").contains("Article r").doesNotContain(" - Reuters");
            assertThat(s.get("summary")).isEqualTo("Summary of " + want);
            if ("shared".equals(want)) {
                assertThat(s.get("topic")).as("topic of the first pipeline").isEqualTo(PlanSupport.TEN_WILDCARDS.get(0));
            } else {
                int r = Integer.parseInt(want.substring(1, want.indexOf('-')));
                int k = (r + 1) / 2;
                assertThat(s.get("queryIds")).as("exactly its own query").isEqualTo(List.of(String.format("Q%02d", r)));
                assertThat(s.get("pipelineIds")).isEqualTo(List.of(String.format("W%02d", k)));
                assertThat(s.get("topic")).isEqualTo(PlanSupport.TEN_WILDCARDS.get(k - 1));
            }
        }
        Map<String, Object> shared = sources.get(0);
        assertThat(shared.get("url")).isEqualTo(base + "/articles/shared");
        assertThat((List<?>) shared.get("queryIds")).as("the shared article keeps all 18 queries").hasSize(18);
        assertThat((List<?>) shared.get("pipelineIds")).as("and was found by all nine pipelines").hasSize(9);
        assertThat(news.articleRequests.stream().filter(n -> n.equals("shared")).count()).as("fetched once").isEqualTo(1);
        assertThat(news.articleRequests).as("only kept sources are fetched").hasSize(28);
        assertThat(sources.stream().map(s -> s.get("url")).distinct().count()).isEqualTo(28);
    }

    @SuppressWarnings("unchecked")
    @Test
    void oneGoogleQueryFailingFailsOnlyThatQueryAndSendsNothingElse() throws Exception {
        news.responder = req -> req.number() == 2 ? StubNews.status(503) : f240(req);
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per query, no fallback request, a 503 is not retried").hasSize(18);
        assertThat(news.paths).noneMatch(p -> p.startsWith("/api/v2/doc"));
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("240 minus the 14 items of the failed query (r2 has 14)").isEqualTo(226);
        assertThat(counts.get("articlesConsidered")).as("205 minus the 12 own candidates of Q02").isEqualTo(193);
        assertThat(counts.get("sourcesKept")).isEqualTo(28);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(18);
        for (int i = 0; i < queries.size(); i++) {
            Map<String, Object> q = queries.get(i);
            boolean failedQuery = i == 1; // request number 2 = Q02
            assertThat(q.get("status")).as(q.get("id").toString()).isEqualTo(failedQuery ? "FAILED" : "OK");
            if (failedQuery) assertThat(q.get("articlesReturned")).isEqualTo(0);
        }
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(28);
        assertThat(sources.stream().map(s -> ((String) s.get("url")).substring(((String) s.get("url")).lastIndexOf('/') + 1)).toList())
            .as("Q02 failed: W01 selects shared, r1-a3, r1-a4, r1-a5").containsExactlyElementsOf(F240Support.keptNames(9, true));
        assertThat(sources).allSatisfy(s -> assertThat(s.get("publisherUrl")).as("every source comes from a Google item").isEqualTo(REUTERS));
        assertThat(sources.stream().flatMap(s -> ((List<String>) s.get("queryIds")).stream()).filter(q -> q.equals("Q02")))
            .as("no source belongs to the failed query").isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void anEmptyFeedEverywhereMakesEmptyQueriesAndASpeculativeRun() throws Exception {
        news.responder = req -> StubNews.rss();
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per query").hasSize(18);
        assertThat(news.paths).noneMatch(p -> p.startsWith("/api/v2/doc"));
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(18).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
        assertThat(sourceItems(sid, id)).isEmpty();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void aLinkThatAnswersWithoutARedirectKeepsTheGoogleLinkInTheApi() throws Exception {
        String link = news.baseUrl() + "/articles/no-redirect";
        news.responder = req -> req.number() == 1
            ? StubNews.rss(StubNews.rssItem(req.elements().get(0) + " story - Reuters", link,
                StubNews.pubDate(Instant.now().minusSeconds(3600)), "Reuters", REUTERS))
            : StubNews.rss();
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(1);
        Map<String, Object> s = sources.get(0);
        assertThat(s.get("url")).isEqualTo(link);
        assertThat(s.get("metadataFetched")).isEqualTo(false);
        assertThat(s.get("publisher")).isEqualTo("Reuters");
        assertThat(s.get("publisherUrl")).isEqualTo(REUTERS);
        assertThat((String) s.get("summary")).isEqualTo(((String) s.get("title")));
    }

    @Test
    void googleDownFailsEveryQueryWithoutAnyFallbackAndTheRunGoesOn() throws Exception {
        news.responder = req -> StubNews.status(503);
        String sid = connectedSid();
        String id = (String) startOk(sid, F240_BODY).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).as("one request per query, 18 queries").hasSize(18);
        assertThat(news.paths).as("no request to any other route").containsOnly("/rss/search");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("articlesRetrieved")).isEqualTo(0);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(sid, id));
        assertThat(queries).hasSize(18).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("FAILED");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
    }
}
