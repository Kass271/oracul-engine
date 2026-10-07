package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * wildcard-search.md FR-52 through whole runs (concurrency 8): 20 queries go out as 20 parallel requests, the join keeps
 * every selection / article request behind the last search request, every source belongs to the query that returned it,
 * and a STOP while eight requests are open ends the run for good — no further {@code /rss/search} arrives.
 */
// @trace FR-52
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
    "oracul.news.google.concurrency=8",
})
class ParallelSearchRunIT extends AbstractEventIT {

    private String name(String q) {
        return "pq" + Integer.toHexString(q.hashCode());
    }

    private static boolean awaitTrue(BooleanSupplier condition, long timeoutMs) throws InterruptedException {
        long end = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < end) {
            if (condition.getAsBoolean()) return true;
            Thread.sleep(10);
        }
        return condition.getAsBoolean();
    }

    @SuppressWarnings("unchecked")
    @Test
    void twentyQueriesRunInParallelJoinBeforeSelectionAndEverySourceBelongsToItsQuery() throws Exception {
        // a q-keyed responder: 3 items per query, unique per query, each answered after 150 ms
        news.responder = StubNews.slow(150, req -> {
            List<String> items = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                items.add(StubNews.rssItem("Item " + i + " of " + req.q(), news.baseUrl() + "/rss/articles/" + name(req.q()) + "-" + i,
                    StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS)), "Reuters", "https://www.reuters.com"));
            }
            return StubNews.rss(items.toArray(String[]::new));
        });
        Ran r = run(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(news.requests).as("one request per planned query").hasSize(20);
        assertThat(news.maxOpen()).as("20 queries, 8 permits, 150 ms answers: eight open at once").isEqualTo(8);

        // join: the first non-search request starts after the last search request ended
        long lastSearchEnd = news.finishedNanos.values().stream().mapToLong(Long::longValue).max().orElseThrow();
        assertThat(news.finishedNanos).hasSize(20);
        List<StubNews.Arrival> others = news.arrivals.stream().filter(a -> !a.path().equals("/rss/search")).toList();
        assertThat(others).as("article requests of the kept sources happened").isNotEmpty();
        assertThat(others).as("no selection / article request starts before the last search ended")
            .allSatisfy(a -> assertThat(a.nanos()).isGreaterThan(lastSearchEnd));

        Map<String, Object> plan = (Map<String, Object>) researchBody(r.sid(), r.id()).get("searchPlan");
        List<Map<String, Object>> queries = (List<Map<String, Object>>) plan.get("queries");
        assertThat(queries).hasSize(20).allSatisfy(q -> {
            assertThat(q.get("status")).isEqualTo("OK");
            assertThat(q.get("articlesReturned")).isEqualTo(3);
        });
        Map<String, String> idOfName = new HashMap<>();
        for (Map<String, Object> q : queries) {
            String sent = ParallelSearchSupport.text((String) q.get("text")) + " when:90d";
            idOfName.put(name(sent), (String) q.get("id"));
        }
        assertThat(idOfName).as("the 20 sent q values are distinct").hasSize(20);
        Map<String, Object> counts = (Map<String, Object>) r.run().get("counts");
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(60);
        assertThat(counts.get("articlesConsidered")).isEqualTo(30);
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).hasSize(30);
        for (Map<String, Object> s : sources) {
            String url = (String) s.get("url");
            String file = url.substring(url.lastIndexOf('/') + 1);
            String key = file.substring(0, file.lastIndexOf('-'));
            assertThat(s.get("queryIds")).as("source " + s.get("id") + " " + url).isEqualTo(List.of(idOfName.get(key)));
        }
    }

    @Test
    void aStopWhileEightRequestsAreOpenSendsNothingFurtherAndTheRunStaysStopped() throws Exception {
        CountDownLatch open = new CountDownLatch(1);
        news.responder = req -> {
            try {
                open.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return StubNews.rss();
        };
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        try {
            assertThat(awaitTrue(() -> news.maxOpen() >= 8, 15_000)).as("eight requests are open").isTrue();
            MvcResult stop = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/runs/" + id + "/stop")
                .cookie(new Cookie("ORACUL_SID", sid))).andReturn();
            assertThat(stop.getResponse().getStatus()).as(stop.getResponse().getContentAsString()).isEqualTo(200);
            assertThat(json(stop.getResponse().getContentAsString()).get("status")).isEqualTo("STOPPED");
            int sentAtStop = news.requests.size();
            assertThat(sentAtStop).as("no more than the eight permits were ever open").isEqualTo(8);

            open.countDown();
            long end = System.currentTimeMillis() + 2000;
            while (System.currentTimeMillis() < end) {
                assertThat(news.requests).as("after the release no further /rss/search request is sent").hasSize(sentAtStop);
                assertThat(news.paths).allMatch(p -> p.equals("/rss/search"));
                Thread.sleep(100);
            }
            assertThat(json(getRun(sid, id)).get("status")).isEqualTo("STOPPED");
        } finally {
            open.countDown();
        }
    }
}
