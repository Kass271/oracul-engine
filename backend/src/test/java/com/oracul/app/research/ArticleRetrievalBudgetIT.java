package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.result.AbstractStoryIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-54 / NFR-10 part 3, range (j) of article-retrieval.md: the stage budget (query-generation PT1S, search PT2S, stage-budget PT3S)
 * with an article-fetch-timeout (PT10S) longer than the remaining budget, so that a request still held when the budget expires is cut
 * by the budget (NOT_ATTEMPTED), not by its own fetch timeout (that case is PAGE_FAILED / DECODE_FAILED, range (f)).
 */
// @trace FR-54
// @trace FR-61
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.news.google.rate-limit-wait=PT0.05S",
    "oracul.news.article-fetch-timeout=PT10S",
    "oracul.search.query-generation-window=PT1S",
    "oracul.search.search-window=PT2S",
    "oracul.search.stage-budget=PT3S",
})
class ArticleRetrievalBudgetIT extends AbstractStoryIT {

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> excerptsOf(Map<String, Object> source) {
        return (List<Map<String, Object>>) source.get("excerpts");
    }

    private static boolean isGoogleHost(String url) {
        String host = java.net.URI.create(url).getHost().toLowerCase();
        return host.equals("google.com") || host.endsWith(".google.com") || host.equals("gstatic.com") || host.endsWith(".gstatic.com")
            || host.equals("googleusercontent.com") || host.endsWith(".googleusercontent.com");
    }

    /** The invariants of every run (article-retrieval.md range (j), "Invariants for every run of every IT and E2E"). */
    @SuppressWarnings("unchecked")
    private void assertRunInvariants(Ran r) throws Exception {
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        Map<String, Object> counts = counts(r.run());
        int retrieved = 0;
        String base = news.baseUrl();
        List<String> urls = new ArrayList<>();
        for (Map<String, Object> s : sources) {
            String status = (String) s.get("contentStatus");
            assertThat(status).as(s.get("id") + ": contentStatus is present on every source of a new run").isNotNull();
            List<Map<String, Object>> excerpts = excerptsOf(s);
            assertThat(excerpts).as(s.get("id") + ": excerpts present (possibly empty)").isNotNull();
            boolean ok = "RETRIEVED".equals(status);
            assertThat(!excerpts.isEmpty()).as(s.get("id") + ": RETRIEVED iff excerpts non-empty").isEqualTo(ok);
            if (ok) retrieved++;
            assertThat(s.get("metadataFetched")).as(s.get("id") + ": metadataFetched iff RETRIEVED / NO_TEXT")
                .isEqualTo("RETRIEVED".equals(status) || "NO_TEXT".equals(status));
            String url = (String) s.get("url");
            urls.add(url);
            boolean googleLink = url.startsWith(base + "/rss/articles/");
            assertThat(s.containsKey("publisherHost")).as(s.get("id") + ": publisherHost present iff the url is not the Google link").isEqualTo(!googleLink);
            if (googleLink) {
                assertThat(List.of("DECODE_FAILED", "REFUSED", "NOT_ATTEMPTED")).as(s.get("id") + ": a Google link only with a failed decode").contains(status);
            }
            assertThat(isGoogleHost(url)).as(s.get("id") + ": no stored url on a Google host").isFalse();
            List<String> pipelineIds = (List<String>) s.get("pipelineIds");
            for (Map<String, Object> e : excerpts) {
                assertThat(pipelineIds).as(s.get("id") + ": every excerpt belongs to a pipeline of the source").contains((String) e.get("pipelineId"));
                assertThat((List<String>) e.get("fragments")).as(s.get("id") + ": fragments").isNotEmpty().hasSizeLessThanOrEqualTo(3);
                assertThat(((List<String>) e.get("fragments")).stream().mapToInt(String::length).sum()).isLessThanOrEqualTo(1200);
            }
        }
        assertThat(urls.stream().distinct().count()).as("stored urls are distinct").isEqualTo(urls.size());
        assertThat(counts.get("sourcesWithContent")).as("sourcesWithContent = number of RETRIEVED sources").isEqualTo(retrieved);
        assertThat(counts.get("sourcesKept")).isEqualTo(sources.size());
        assertThat(retrieved).isLessThanOrEqualTo(sources.size());
        long googleLinks = sources.size();
        assertThat(news.decodeRequests.size()).as("the decode endpoint is called at most once per kept Google-link source").isLessThanOrEqualTo((int) googleLinks);
        assertThat(news.articleRequests.size()).as("article requests <= kept sources").isLessThanOrEqualTo(sources.size());
    }

    // ---- NFR-10 part 3: the stage budget (query-generation PT1S, search PT2S, stage-budget PT3S) -------------------------------

    private int sourceRows(String runId) {
        return jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, runId);
    }

    @Test
    @Timeout(60)
    void aPublisherThatNeverAnswersEndsTheRetrievalAtTheStageBudgetAndTheRunContinues() throws Exception {
        news.reset();
        news.mode("ok");
        news.articleGate = new CountDownLatch(1);
        String sid = connectedSid();
        long t0 = System.currentTimeMillis();
        String id = (String) startOk(sid, A).get("id");
        long end = t0 + 15_000;
        while (sourceRows(id) == 0 && System.currentTimeMillis() < end) Thread.sleep(25);
        long committedAfter = System.currentTimeMillis() - t0;
        assertThat(sourceRows(id)).as("READING_SOURCES committed").isEqualTo(7);
        assertThat(committedAfter).as("the commit comes at t0 + the 3 s stage budget (+-1 s, plus the stages before it)").isBetween(2_000L, 4_800L);
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("headline")).isNotNull();
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).hasSize(7);
        for (Map<String, Object> s : sources) {
            assertThat(s.get("contentStatus")).isEqualTo("NOT_ATTEMPTED");
            assertThat((String) s.get("url")).as("the decoded publisher URL is known").startsWith(news.baseUrl() + "/articles/");
            assertThat(s.get("publisherHost")).isEqualTo("127.0.0.1");
            assertThat(s.get("metadataFetched")).isEqualTo(false);
            assertThat(excerptsOf(s)).isEmpty();
            assertThat(s.get("summary")).as("snippet else title").isNotNull();
        }
        assertThat(counts(run).get("sourcesWithContent")).isEqualTo(0);
    }

    @Test
    @Timeout(60)
    void aGooglePageThatNeverAnswersLeavesTheGoogleLinkAndNoPublisherHost() throws Exception {
        news.reset();
        news.mode("ok");
        news.googlePageGate = new CountDownLatch(1);
        Ran r = runWithin(A, 30_000);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).hasSize(7);
        for (Map<String, Object> s : sources) {
            assertThat(s.get("contentStatus")).isEqualTo("NOT_ATTEMPTED");
            assertThat((String) s.get("url")).startsWith(news.baseUrl() + "/rss/articles/");
            assertThat(s).doesNotContainKey("publisherHost");
            assertThat(s.get("metadataFetched")).isEqualTo(false);
        }
        assertThat(news.decodeRequests).isEmpty();
        assertThat(news.articleRequests).isEmpty();
        assertRunInvariants(r);
    }
}
