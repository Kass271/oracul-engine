package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;

/**
 * Wildcard-search.md FR-50 acceptance 5: a source is attributed to exactly the pipelines among whose usable search results its
 * normalised link appeared - {@code Source.pipelineIds} ascending and never empty, {@code queryIds} within those pipelines'
 * queries, {@code topic} = topicKey of the first pipeline; and the plan is committed before the first Google request.
 */
// @trace FR-50
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
})
class PipelineAttributionIT extends AbstractEventIT {

    private static final String THREE_BODY = "{\"realism\":8,\"darkness\":9,\"optimism\":2,\"horizon\":\"5y\",\"wildcards\":["
        + "{\"wildcardId\":\"ai-agi-breakthrough\",\"intensity\":5},{\"wildcardId\":\"robotics-humanoid-boom\",\"intensity\":6},"
        + "{\"wildcardId\":\"biology-new-pandemic\",\"intensity\":7}],\"customWildcards\":[],"
        + "\"output\":{\"story\":true,\"illustration\":false}}";

    private static final List<String> THREE_TOPICS = List.of("ai-agi-breakthrough", "robotics-humanoid-boom", "biology-new-pandemic");

    private String item(String name, String title) {
        return StubNews.rssItem(title, news.baseUrl() + "/rss/articles/" + name, StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS)),
            "Reuters", "https://www.reuters.com");
    }

    /** The pipeline (W01...) whose query text this request carries ("W01 stub query 2" -> "W01"). */
    private static String pipelineOfRequest(StubNews.Request req) {
        return req.elements().get(0).substring(0, 3);
    }

    private static String queryNumber(StubNews.Request req) {
        return req.elements().get(0).substring(req.elements().get(0).length() - 1);
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        return (List<String>) o;
    }

    private static Map<String, Object> byUrl(List<Map<String, Object>> sources, String urlSuffix) {
        return sources.stream().filter(s -> ((String) s.get("url")).endsWith(urlSuffix)).findFirst()
            .orElseThrow(() -> new AssertionError("no source ending with " + urlSuffix + " in " + sources));
    }

    /** Invariants of every source of a new run (FR-50 ranges b). */
    private void assertAttributionInvariants(Map<String, Object> research, List<Map<String, Object>> sources) {
        List<Map<String, Object>> pipelines = PlanJson.pipelines(research);
        for (Map<String, Object> s : sources) {
            String label = (String) s.get("id");
            List<String> pipelineIds = strings(s.get("pipelineIds"));
            List<String> queryIds = strings(s.get("queryIds"));
            assertThat(pipelineIds).as(label + " pipelineIds are never empty").isNotEmpty();
            assertThat(pipelineIds).as(label + " pipelineIds ascending").isSorted().doesNotHaveDuplicates();
            Set<String> owners = new TreeSet<>();
            for (String q : queryIds) {
                Map<String, Object> owner = pipelines.stream()
                    .filter(p -> PlanJson.queriesOf(p).stream().anyMatch(x -> q.equals(x.get("id")))).findFirst()
                    .orElseThrow(() -> new AssertionError(label + " names the query " + q + " that no pipeline owns"));
                owners.add((String) owner.get("id"));
            }
            assertThat(pipelineIds).as(label + ": pipelineIds = the pipelines that own its queryIds").containsExactlyElementsOf(owners);
            Map<String, Object> first = pipelines.stream().filter(p -> pipelineIds.get(0).equals(p.get("id"))).findFirst().orElseThrow();
            assertThat(s.get("topic")).as(label + " topic = topicKey of its first pipeline").isEqualTo(first.get("topicKey"));
        }
    }

    // FR-50 ranges (b), the classes of the spec
    @Test
    void sourcesAreAttributedToTheExactPipelinesWhoseQueriesFoundThem() throws Exception {
        // W01's three queries answer X plus one own article each; W02's three answer X and Y
        news.responder = req -> {
            String w = pipelineOfRequest(req);
            if (w.equals("W01")) return StubNews.rss(item("x-shared", "Shared article X"), item("own-w01-" + queryNumber(req), "Own article of W01 " + queryNumber(req)));
            return StubNews.rss(item("x-shared", "Shared article X"), item("y-w02", "Article Y of W02"));
        };
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        Map<String, Object> research = researchBody(r.sid(), r.id());
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).as("X, Y and W01's three own articles").hasSize(5);

        Map<String, Object> x = byUrl(sources, "/articles/x-shared");
        assertThat(strings(x.get("pipelineIds"))).containsExactly("W01", "W02");
        assertThat(x.get("topic")).as("one source, topic of W01").isEqualTo("biology-new-pandemic");
        assertThat(strings(x.get("queryIds"))).containsExactly("Q01", "Q02", "Q03", "Q04", "Q05", "Q06");
        Map<String, Object> y = byUrl(sources, "/articles/y-w02");
        assertThat(strings(y.get("pipelineIds"))).containsExactly("W02");
        assertThat(y.get("topic")).isEqualTo("robotics-humanoid-boom");
        assertThat(strings(y.get("queryIds"))).containsExactly("Q04", "Q05", "Q06");
        for (int k = 1; k <= 3; k++) {
            Map<String, Object> own = byUrl(sources, "/articles/own-w01-" + k);
            assertThat(strings(own.get("pipelineIds"))).as("own article " + k).containsExactly("W01");
            assertThat(own.get("topic")).isEqualTo("biology-new-pandemic");
            assertThat(strings(own.get("queryIds"))).containsExactly("Q0" + k);
        }
        assertAttributionInvariants(research, sources);
        assertThat(PlanJson.plan(research).get("queries")).as("the queries stay in the pipelines after SEARCHING").isEqualTo(List.of());
        assertThat(PlanJson.queries(research)).hasSize(6).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("OK");
            assertThat(q.get("articlesReturned")).isEqualTo(2);
        });
    }

    /** Every non-empty subset of three pipelines: the pipelines whose answers carry the article. */
    static Stream<Arguments> everySubsetOfThreePipelines() {
        List<Arguments> out = new ArrayList<>();
        for (int mask = 1; mask < 8; mask++) {
            List<String> subset = new ArrayList<>();
            for (int i = 0; i < 3; i++) if ((mask & (1 << i)) != 0) subset.add(String.format("W%02d", i + 1));
            out.add(Arguments.of(mask, subset));
        }
        return out.stream();
    }

    // the pipelineIds of a source = exactly the pipelines that found it, for all 7 non-empty subsets of 3 pipelines
    @ParameterizedTest(name = "found by {1}")
    @MethodSource("everySubsetOfThreePipelines")
    void aSourceIsAttributedToExactlyTheSubsetOfPipelinesThatFoundIt(int mask, List<String> subset) throws Exception {
        news.responder = req -> {
            String w = pipelineOfRequest(req);
            List<String> items = new ArrayList<>();
            if (subset.contains(w)) items.add(item("attr-" + mask, "Attributed article " + mask));
            items.add(item("own-" + w + "-" + queryNumber(req), "Own article of " + w + " " + queryNumber(req)));
            return StubNews.rss(items.toArray(String[]::new));
        };
        Ran r = run(THREE_BODY);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        Map<String, Object> research = researchBody(r.sid(), r.id());
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(PlanJson.pipelines(research)).hasSize(3);
        Map<String, Object> shared = byUrl(sources, "/articles/attr-" + mask);
        assertThat(strings(shared.get("pipelineIds"))).as("pipelineIds of the attributed source").containsExactlyElementsOf(subset);
        assertThat(shared.get("topic")).isEqualTo(THREE_TOPICS.get(Integer.parseInt(subset.get(0).substring(1)) - 1));
        List<String> expectedQueries = new ArrayList<>();
        for (String w : subset) {
            int p = Integer.parseInt(w.substring(1));
            for (int k = 1; k <= 3; k++) expectedQueries.add(String.format("Q%02d", 3 * (p - 1) + k));
        }
        assertThat(strings(shared.get("queryIds"))).as("all queries of the finding pipelines").containsExactlyElementsOf(expectedQueries);
        assertThat(sources).as("the attributed source + 9 own articles").hasSize(10);
        assertAttributionInvariants(research, sources);
    }

    // FR-50 ranges (c): the plan is committed before the first Google request, every query PENDING, nothing selected yet
    @Test
    void thePlanReadWhileSearchingIsHeldHasEveryQueryPendingAndNoQueriesArray() throws Exception {
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch open = new CountDownLatch(1);
        news.responder = req -> {
            arrived.countDown();
            try {
                open.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StubNews.rss(item("held-" + req.number(), "Held article " + req.number()));
        };
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        try {
            assertThat(arrived.await(15, TimeUnit.SECONDS)).as("the first Google request arrived: SEARCHING is held").isTrue();
            Map<String, Object> research = researchBody(sid, id);
            assertThat(PlanJson.pipelines(research)).hasSize(2);
            assertThat(PlanJson.queries(research)).hasSize(6).allSatisfy(q -> {
                assertThat(q.get("status")).as("committed before any Google request: PENDING").isEqualTo("PENDING");
                assertThat(q.get("articlesReturned")).isEqualTo(0);
            });
            for (Map<String, Object> p : PlanJson.pipelines(research)) {
                assertThat(p).doesNotContainKey("candidatesConsidered").doesNotContainKey("sourceIds");
            }
            assertThat(PlanJson.plan(research).get("queries")).isEqualTo(List.of());
            assertThat(sourceItems(sid, id)).isEmpty();
        } finally {
            open.countDown();
        }
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> research = researchBody(sid, id);
        assertThat(PlanJson.plan(research).get("queries")).as("still [] after SEARCHING").isEqualTo(List.of());
        assertThat(PlanJson.queries(research)).allSatisfy(q -> assertThat(q.get("status")).isEqualTo("OK"));
        assertThat(PlanJson.queries(research).stream().mapToInt(q -> ((Number) q.get("articlesReturned")).intValue()).sum()).isEqualTo(6);
    }
}
