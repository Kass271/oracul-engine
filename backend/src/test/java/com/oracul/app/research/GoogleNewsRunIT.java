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
 * answers every query of fixture F240 (items with " - Reuters" title suffixes), the run keeps 30 of the 205 usable
 * candidates, resolves their links through the article fetch and exposes publisherUrl; failing and empty feeds.
 */
// @trace FR-46, FR-48, FR-49, FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=1000",
})
class GoogleNewsRunIT extends AbstractEventIT {

    private static final String REUTERS = "https://www.reuters.com";

    @SuppressWarnings("unchecked")
    @Test
    void f240ThroughGoogleKeepsThirtyResolvedSourcesWithPublisherUrl() throws Exception {
        newsF240();
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("every item of the answered queries").isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).isEqualTo(30);
        assertThat(news.requests).as("18 queries go out as 18 requests").hasSize(18);
        assertThat(news.requests.stream().map(r -> r.elements().size()).distinct().toList()).as("one text per request").containsExactly(1);
        assertThat(news.paths).as("no request to a former provider path").noneMatch(p -> p.startsWith("/api/v2/doc"));

        Map<String, Object> plan = (Map<String, Object>) researchBody(sid, id).get("searchPlan");
        List<Map<String, Object>> queries = (List<Map<String, Object>>) plan.get("queries");
        Map<String, Map<String, Object>> intents = new HashMap<>();
        for (Map<String, Object> in : (List<Map<String, Object>>) plan.get("intents")) intents.put((String) in.get("id"), in);
        List<String> topicOfQuery = new ArrayList<>();
        int returned = 0;
        for (Map<String, Object> q : queries) {
            assertThat(q.get("status")).as("every query was answered").isEqualTo("OK");
            returned += ((Number) q.get("articlesReturned")).intValue();
            topicOfQuery.add(F240Support.topicOf(intents.get((String) q.get("intentId"))));
        }
        assertThat(returned).isEqualTo(240);

        List<CapOracle.Cand> usable = F240Support.usableCandidates(topicOfQuery);
        List<Integer> keptIdx = CapOracle.select(usable);
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(30);
        String base = news.baseUrl();
        for (int i = 0; i < sources.size(); i++) {
            Map<String, Object> s = sources.get(i);
            CapOracle.Cand want = usable.get(keptIdx.get(i));
            assertThat(s.get("id")).isEqualTo(String.format("S%03d", i + 1));
            assertThat(s.get("url")).as("the Google link resolved to the article page").isEqualTo(base + "/articles/" + want.name());
            assertThat(s.get("metadataFetched")).isEqualTo(true);
            assertThat(s.get("publisher")).as("og:site_name of the page").isEqualTo("Stub Site");
            assertThat(s.get("publisherUrl")).isEqualTo(REUTERS);
            assertThat(s.get("sourceType")).isEqualTo("NEWS");
            assertThat(s.get("sourceQuality")).isEqualTo(0.85);
            assertThat(s.get("topic")).isEqualTo(want.topic());
            assertThat((String) s.get("title")).as("title without the source suffix").contains("Article r").doesNotContain(" - Reuters");
            assertThat(s.get("summary")).isEqualTo("Summary of " + want.name());
            assertThat((List<?>) s.get("queryIds")).isNotEmpty();
        }
        Map<String, Object> shared = sources.stream().filter(s -> (base + "/articles/shared").equals(s.get("url"))).findFirst().orElseThrow();
        assertThat((List<?>) shared.get("queryIds")).as("the shared article keeps all 18 queries").hasSize(18);
        assertThat(news.articleRequests.stream().filter(n -> n.equals("shared")).count()).as("fetched once").isEqualTo(1);
        assertThat(news.articleRequests).as("only kept candidates are fetched").hasSize(30);
        // the same Google link is not kept twice: every url unique
        assertThat(sources.stream().map(s -> s.get("url")).distinct().count()).isEqualTo(30);
    }

    @SuppressWarnings("unchecked")
    @Test
    void oneGoogleQueryFailingFailsOnlyThatQueryAndSendsNothingElse() throws Exception {
        news.responder = req -> req.number() == 2 ? StubNews.status(503) : f240(req);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per query, no fallback request, a 503 is not retried").hasSize(18);
        assertThat(news.paths).noneMatch(p -> p.startsWith("/api/v2/doc"));
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("240 minus the 14 items of the failed query (r2 has 14)").isEqualTo(226);
        assertThat(counts.get("articlesConsidered")).isEqualTo(30);
        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        for (int i = 0; i < queries.size(); i++) {
            Map<String, Object> q = queries.get(i);
            boolean failedQuery = i == 1; // request number 2 = Q02
            assertThat(q.get("status")).as(q.get("id").toString()).isEqualTo(failedQuery ? "FAILED" : "OK");
            if (failedQuery) assertThat(q.get("articlesReturned")).isEqualTo(0);
        }
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(30);
        assertThat(sources).allSatisfy(s -> assertThat(s.get("publisherUrl")).as("every source comes from a Google item").isEqualTo(REUTERS));
        assertThat(sources.stream().flatMap(s -> ((List<String>) s.get("queryIds")).stream()).filter(q -> q.equals("Q02")))
            .as("no source belongs to the failed query").isEmpty();
    }

    @SuppressWarnings("unchecked")
    @Test
    void anEmptyFeedEverywhereMakesEmptyQueriesAndASpeculativeRun() throws Exception {
        news.responder = req -> StubNews.rss();
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per query").hasSize(18);
        assertThat(news.paths).noneMatch(p -> p.startsWith("/api/v2/doc"));
        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
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
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(news.requests).as("one request per query, 18 queries").hasSize(18);
        assertThat(news.paths).as("no request to any other route").containsOnly("/rss/search");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("articlesRetrieved")).isEqualTo(0);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(18).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("FAILED");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
    }
}
