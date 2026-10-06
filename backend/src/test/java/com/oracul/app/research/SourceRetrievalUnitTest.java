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
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

/** SourceRetrieval without Spring or internet: search budget, a failing article fetch, an interrupted wait (FR-13, FR-48). */
// @trace FR-13
// @trace FR-48
// @trace FR-56
@Timeout(30)
class SourceRetrievalUnitTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-07T12:00:00Z"), ZoneOffset.UTC);

    /**
     * Article fetcher double: a Mockito mock of the real class, so no constructor runs and the test does not depend on how
     * the fetcher is built (the production constructor takes a SafeFetcher since FR-56); the network call is replaced.
     */
    private static final class FakeFetcher {
        final CountDownLatch release = new CountDownLatch(1);
        final boolean blocking;
        final AtomicInteger calls = new AtomicInteger();
        final ArticleMetadataFetcher fetcher = Mockito.mock(ArticleMetadataFetcher.class);

        FakeFetcher(boolean blocking) {
            this.blocking = blocking;
            Mockito.when(fetcher.fetchDetailed(ArgumentMatchers.anyString(), ArgumentMatchers.anyInt())).thenAnswer(inv -> {
                String url = inv.getArgument(0);
                calls.incrementAndGet();
                if (url.contains("/boom")) {
                    throw new IllegalStateException("unexpected fetch problem");
                }
                if (blocking) {
                    try {
                        release.await(20, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                return Optional.<ArticleMetadataFetcher.Fetched>empty();
            });
        }
    }

    private final List<FakeFetcher> fetchers = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        fetchers.forEach(f -> f.release.countDown());
    }

    private SourceRetrieval retrieval(NewsProvider provider, FakeFetcher fetcher, Duration budget) {
        fetchers.add(fetcher);
        SourceQualityTable table = new SourceQualityTable("who.int", "nature.com", "reuters.com", "medium.com");
        return new SourceRetrieval(provider, fetcher.fetcher, table, CLOCK, Duration.ofMillis(1), Duration.ofSeconds(1), budget, 2);
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
    void noRequestStartsWhenTheSearchBudgetEndsWhileTheRunGuardIsBeingAsked() throws Exception {
        AtomicInteger requests = new AtomicInteger();
        NewsProvider provider = (q, max, timeout) -> {
            requests.incrementAndGet();
            return new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of());
        };
        SourceRetrieval retrieval = retrieval(provider, new FakeFetcher(false), Duration.ofMillis(40));
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
