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
 * phase-02 news-search.md FR-48 + FR-46 through whole runs: Google News RSS answers every group of fixture F240 (mirror of the
 * GDELT fixture with " - Reuters" title suffixes), the run keeps 30 of the 205 usable candidates, resolves their links through
 * the article fetch and exposes publisherUrl; failing and empty feeds.
 */
// @trace FR-46, FR-48
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.research.query-budget=18",
    "oracul.events.max-sources=1000",
})
class GoogleNewsRunIT extends AbstractEventIT {

    private static final String REUTERS = "https://www.reuters.com";

    /** The RSS mirror of f240: per element (global index r) the block of the GDELT fixture as Google News items. */
    private StubGdelt.Reply googleF240(StubGdelt.RssRequest req) {
        String base = gdelt.baseUrl();
        int first = 1 + gdelt.rssRequests.stream().filter(x -> x.number() < req.number()).mapToInt(x -> x.elements().size()).sum();
        List<String> items = new ArrayList<>();
        for (int e = 0; e < req.elements().size(); e++) {
            int r = first + e;
            int n = r <= 6 ? 14 : 13;
            for (int a = 1; a <= n; a++) {
                String link = a == 1 ? base + "/rss/articles/shared?utm_source=q" + r : base + "/rss/articles/r" + r + "-a" + a;
                String title = req.elements().get(e) + " Article r" + r + "-a" + a + " - Reuters";
                String pubDate = StubGdelt.pubDate(testNow.minus(1, ChronoUnit.DAYS));
                if (a == 2 && r <= 5) title = "  ";
                if (a == 2 && r >= 6 && r <= 10) link = "javascript:void(0)";
                if (a == 2 && r >= 11 && r <= 15) pubDate = StubGdelt.pubDate(testNow.minus(200, ChronoUnit.DAYS));
                if (a == 2 && r >= 16) link = base + "/rss/articles/r" + r + "-a3";
                items.add(StubGdelt.rssItem(title, link, pubDate, "Reuters", REUTERS));
            }
        }
        return StubGdelt.rss(items.toArray(String[]::new));
    }

    @SuppressWarnings("unchecked")
    @Test
    void f240ThroughGoogleKeepsThirtyResolvedSourcesWithPublisherUrlAndNeverCallsGdelt() throws Exception {
        gdelt.rssResponder = this::googleF240;
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = (Map<String, Object>) run.get("counts");
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesRetrieved")).as("every item of the answered groups").isEqualTo(240);
        assertThat(counts.get("articlesConsidered")).isEqualTo(30);
        assertThat(gdelt.rssRequests).as("18 queries go out as 4 OR-group requests (5,5,4,4)").hasSize(4);
        assertThat(gdelt.rssRequests.stream().map(r -> r.elements().size()).toList()).containsExactly(5, 5, 4, 4);
        assertThat(gdelt.requests).as("0 GDELT requests while Google answers").isEmpty();

        Map<String, Object> plan = (Map<String, Object>) researchBody(sid, id).get("searchPlan");
        List<Map<String, Object>> queries = (List<Map<String, Object>>) plan.get("queries");
        Map<String, Map<String, Object>> intents = new HashMap<>();
        for (Map<String, Object> in : (List<Map<String, Object>>) plan.get("intents")) intents.put((String) in.get("id"), in);
        List<String> topicOfQuery = new ArrayList<>();
        int returned = 0;
        for (Map<String, Object> q : queries) {
            assertThat(q.get("status")).as("same per-query statuses as the GDELT answer would give").isEqualTo("OK");
            returned += ((Number) q.get("articlesReturned")).intValue();
            topicOfQuery.add(F240Support.topicOf(intents.get((String) q.get("intentId"))));
        }
        assertThat(returned).isEqualTo(240);

        List<CapOracle.Cand> usable = F240Support.usableCandidates(topicOfQuery);
        List<Integer> keptIdx = CapOracle.select(usable);
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(30);
        String base = gdelt.baseUrl();
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
        assertThat(gdelt.articleRequests.stream().filter(n -> n.equals("shared")).count()).as("fetched once").isEqualTo(1);
        assertThat(gdelt.articleRequests).as("only kept candidates are fetched").hasSize(30);
        // the same Google link is not kept twice: every url unique
        assertThat(sources.stream().map(s -> s.get("url")).distinct().count()).isEqualTo(30);
    }

    @SuppressWarnings("unchecked")
    @Test
    void oneGoogleGroupFailingSendsOnlyThatGroupToGdelt() throws Exception {
        gdelt.rssResponder = req -> req.number() == 2 ? StubGdelt.status(503) : googleF240(req);
        gdelt.responder = req -> StubGdelt.json(f240(req));
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.requests).as("exactly one GDELT request: group 2").hasSize(1);
        assertThat(gdelt.requests.get(0).elements()).isEqualTo(gdelt.rssRequests.get(1).elements());
        assertThat(((Map<String, Object>) run.get("counts")).get("articlesConsidered")).isEqualTo(30);
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(30);
        assertThat(sources.stream().anyMatch(s -> s.get("publisherUrl") != null)).as("Google sources have a publisherUrl").isTrue();
        assertThat(sources.stream().anyMatch(s -> s.get("publisherUrl") == null)).as("GDELT sources have none").isTrue();
    }

    @SuppressWarnings("unchecked")
    @Test
    void anEmptyFeedEverywhereMakesEmptyQueriesNoGdeltRequestAndASpeculativeRun() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.rss();
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.requests).isEmpty();
        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("EMPTY"));
        assertThat(sourceItems(sid, id)).isEmpty();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
    }

    @Test
    void aLinkThatAnswersWithoutARedirectKeepsTheGoogleLinkInTheApi() throws Exception {
        String link = gdelt.baseUrl() + "/articles/no-redirect";
        gdelt.rssResponder = req -> req.number() == 1
            ? StubGdelt.rss(StubGdelt.rssItem(req.elements().get(0) + " story - Reuters", link,
                StubGdelt.pubDate(Instant.now().minusSeconds(3600)), "Reuters", REUTERS))
            : StubGdelt.rss();
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
    void googleAndGdeltBothDownFailEveryQueryAndTheRunGoesOn() throws Exception {
        gdelt.rssResponder = req -> StubGdelt.status(503);
        gdelt.responder = req -> StubGdelt.status(503);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("failure")).isNull();
        assertThat(noteKind(run)).isEqualTo("NO_EVIDENCE");
        assertThat(gdelt.rssRequests).hasSize(4);
        assertThat(gdelt.requests).hasSize(4);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> queries = (List<Map<String, Object>>) ((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries");
        assertThat(queries).hasSize(18).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("FAILED");
            assertThat(q.get("articlesReturned")).isEqualTo(0);
        });
    }
}
