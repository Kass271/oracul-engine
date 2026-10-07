package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.QueryExpansionMode;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.Mockito;

/**
 * SourceRetrieval without Spring or internet: the search window, a failing article fetch, an interrupted wait (FR-13, FR-48,
 * FR-52, FR-54). The constructor {@code SourceRetrieval(NewsSearchProvider, ArticleRetriever, SourceQualityTable, Clock,
 * Duration searchWindow, Duration stageBudget)} (slice 08) and {@code GoogleNewsSearch} are reached reflectively (they do not
 * exist while the RED tests are written).
 */
// @trace FR-13
// @trace FR-48
// @trace FR-52
// @trace FR-54
// @trace FR-56
@Timeout(30)
class SourceRetrievalUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    /**
     * Article retriever double (article-retrieval.md slice 08: {@code SourceRetrieval} calls {@code ArticleRetriever.retrieveAll} instead of
     * {@code ArticleMetadataFetcher.fetchDetailed}): a Mockito mock of the real class, so no constructor runs; {@code retrieveAll} counts the
     * call and, when {@code blocking}, waits until released (an interrupt of the waiting thread throws InterruptedException, as the real
     * one does). The class does not exist while the RED tests are written, so it is reached by name.
     */
    private static final class FakeFetcher {
        final CountDownLatch release = new CountDownLatch(1);
        final boolean blocking;
        final AtomicInteger calls = new AtomicInteger();
        final Object fetcher;

        @SuppressWarnings("unchecked")
        FakeFetcher(boolean blocking) {
            this.blocking = blocking;
            fetcher = Mockito.mock((Class<Object>) ParallelSearchSupport.cls(ParallelSearchSupport.RETRIEVER), inv -> {
                if (inv.getMethod().getName().equals("retrieveAll")) {
                    calls.incrementAndGet();
                    if (blocking) {
                        release.await(20, TimeUnit.SECONDS);
                    }
                    return List.of();
                }
                return inv.getMethod().getReturnType() == boolean.class ? Boolean.FALSE : null;
            });
        }
    }

    private final List<FakeFetcher> fetchers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        fetchers.forEach(f -> f.release.countDown());
    }

    private SourceRetrieval retrieval(NewsProvider provider, FakeFetcher fetcher, Duration window, Clock clock) {
        fetchers.add(fetcher);
        SourceQualityTable table = new SourceQualityTable("who.int", "nature.com", "reuters.com", "medium.com");
        Object search = ParallelSearchSupport.googleSearch(provider, 1, Duration.ofSeconds(1), Duration.ZERO);
        return ParallelSearchSupport.retrieval(search, fetcher.fetcher, table, clock, window, Duration.ofSeconds(90));
    }

    private SourceRetrieval retrieval(NewsProvider provider, FakeFetcher fetcher, Duration window) {
        return retrieval(provider, fetcher, window, CLOCK);
    }

    private static SearchPlan plan(String... texts) {
        SearchIntent intent = new SearchIntent("I01", QueryBucket.MAJOR, "Major current world events", List.of("Horizon 1 year"));
        List<SearchQuery> queries = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            queries.add(new SearchQuery(String.format("Q%02d", i + 1), "I01", QueryBucket.MAJOR, texts[i],
                SearchQueryStatus.PENDING, 0));
        }
        return new SearchPlan(texts.length, QueryExpansionMode.TEMPLATE_FALLBACK, new ArrayList<>(), List.of(intent), queries);
    }

    private static SourceRetrieval.SearchOutcome outcome(String... urls) {
        SearchPlan plan = plan("world news");
        List<NewsProvider.Article> articles = new ArrayList<>();
        List<SourceRetrieval.Attributed> ordered = new ArrayList<>();
        for (String url : urls) {
            NewsProvider.Article a = new NewsProvider.Article(url, "Headline of " + url, null, "Wire", null);
            articles.add(a);
            ordered.add(new SourceRetrieval.Attributed("Q01", a));
        }
        Map<String, List<NewsProvider.Article>> byQuery = new LinkedHashMap<>();
        byQuery.put("Q01", articles);
        return new SourceRetrieval.SearchOutcome(plan, byQuery, ordered);
    }

    @Test
    void noRequestStartsWhenTheSearchWindowEndsWhileTheRunGuardIsBeingAsked() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        NewsProvider provider = (q, max, timeout) -> {
            requests.incrementAndGet();
            return new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of());
        };
        // a real clock: the 40 ms window is over while the guard sleeps 150 ms
        SourceRetrieval retrieval = retrieval(provider, new FakeFetcher(false), Duration.ofMillis(40), Clock.systemUTC());
        SourceRetrieval.SearchOutcome out = retrieval.search(plan("alpha", "beta", "gamma", "delta"), HorizonCode._1Y, () -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        assertThat(requests).hasValue(0);
        assertThat(out.allFailed()).isTrue();
        assertThat(out.articlesRetrieved()).isZero();
        assertThat(out.plan().getQueries()).extracting(SearchQuery::getStatus)
            .containsOnly(SearchQueryStatus.FAILED);
    }

    @Test
    void aFixedTestClockNeverEndsTheSearchWindow() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        NewsProvider provider = (q, max, timeout) -> {
            requests.incrementAndGet();
            return new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of());
        };
        SourceRetrieval retrieval = retrieval(provider, new FakeFetcher(false), Duration.ofMillis(1));
        SourceRetrieval.SearchOutcome out = retrieval.search(plan("alpha", "beta", "gamma", "delta"), HorizonCode._1Y, () -> {
            try {
                Thread.sleep(30);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return true;
        });
        assertThat(requests).as("the clock never moves, so the 1 ms window is never over").hasValue(4);
        assertThat(out.plan().getQueries()).extracting(SearchQuery::getStatus).containsOnly(SearchQueryStatus.EMPTY);
    }

    @Test
    void everyQueryOfThePlanIsSentOnceAndAttributedToItself() throws Exception {
        NewsProvider provider = (q, max, timeout) -> new NewsProvider.Result(SearchQueryStatus.OK,
            List.of(new NewsProvider.Article("https://example.com/" + q.replace(' ', '-').replace(':', '_'), "Headline " + q, null, "Wire", null)));
        SourceRetrieval retrieval = retrieval(provider, new FakeFetcher(false), Duration.ofSeconds(30));
        SourceRetrieval.SearchOutcome out = retrieval.search(plan("alpha", "beta", "gamma"), HorizonCode._1Y);
        assertThat(out.plan().getQueries()).extracting(SearchQuery::getStatus).containsOnly(SearchQueryStatus.OK);
        assertThat(out.ordered()).extracting(SourceRetrieval.Attributed::queryId).containsExactly("Q01", "Q02", "Q03");
        assertThat(out.articles().get("Q02")).extracting(NewsProvider.Article::title).containsExactly("Headline beta when:90d");
        assertThat(out.searches()).isEqualTo(3);
    }

    @Test
    void anInterruptedWaitForTheFetchesIsReportedAndStopsTheFetchesStillRunning() {
        FakeFetcher fetcher = new FakeFetcher(true);
        SourceRetrieval retrieval = retrieval((q, max, timeout) -> NewsProvider.Result.failed(), fetcher, Duration.ofSeconds(5));
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> retrieval.readSources(outcome("https://a.example.com/one", "https://c.example.com/three"),
                HorizonCode._1Y)).isInstanceOf(InterruptedException.class);
        } finally {
            Thread.interrupted();
        }
    }
}
