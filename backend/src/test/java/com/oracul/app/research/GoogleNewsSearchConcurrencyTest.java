package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.research.ParallelSearchSupport.QR;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;

/**
 * FR-52 (slice 03 delta): {@code GoogleNewsSearch} without Spring over the in-process stub — the semaphore bound on open
 * requests (parameterized concurrency x queries), the guard classes (d), the startup classes (b) through both
 * constructors, and the join. Reached reflectively: the class does not exist while this test is written.
 */
// @trace FR-52
// @trace FR-54
@Timeout(60)
class GoogleNewsSearchConcurrencyTest {

    private final StubNews news = StubNews.INSTANCE;

    private static final String WINDOW_LINE = "google news request skipped: search window ended";

    private final Logger searchLog = (Logger) LoggerFactory.getLogger(GoogleNewsSearch.class);
    private final ListAppender<ILoggingEvent> logged = new ListAppender<>();
    private Level levelBefore;

    @BeforeEach
    void reset() {
        news.reset();
        levelBefore = searchLog.getLevel();
        searchLog.setLevel(Level.DEBUG);
        logged.start();
        searchLog.addAppender(logged);
    }

    @AfterEach
    void detach() {
        searchLog.detachAppender(logged);
        logged.stop();
        searchLog.setLevel(levelBefore);
    }

    private long windowLines() {
        return logged.list.stream().filter(e -> e.getLevel() == Level.WARN && WINDOW_LINE.equals(e.getFormattedMessage())).count();
    }

    /** A clock the test moves by hand. */
    private static final class MutableClock extends Clock {
        private final AtomicReference<Instant> now;

        MutableClock(Instant start) {
            this.now = new AtomicReference<>(start);
        }

        void set(Instant t) {
            now.set(t);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    }

    private Object search(int concurrency) {
        return ParallelSearchSupport.googleSearch(new GoogleNewsProvider(news.baseUrl()), concurrency, Duration.ofSeconds(10), Duration.ZERO);
    }

    private static Object noWindowBudget() {
        return ParallelSearchSupport.budget(Clock.systemUTC(), Instant.now(), Duration.ofSeconds(60), null);
    }

    private static List<String> texts(int n) {
        return IntStream.rangeClosed(1, n).mapToObj(i -> "concurrency" + i + " topic" + i).toList();
    }

    static Stream<Arguments> grid() {
        List<Arguments> out = new ArrayList<>();
        for (int c : new int[] {1, 2, 8}) {
            for (int n : new int[] {9, 20}) {
                out.add(Arguments.of(c, n));
            }
        }
        return out.stream();
    }

    // the measured maximum equals min(concurrency, queries) when the stub holds every answer
    @ParameterizedTest(name = "concurrency {0}, {1} queries")
    @MethodSource("grid")
    void atMostConcurrencyRequestsAreOpenAtOnce(int concurrency, int n) throws Exception {
        news.responder = StubNews.slow(300, req -> StubNews.rss());
        List<QR> out = ParallelSearchSupport.search(search(concurrency), texts(n), HorizonCode._1Y, noWindowBudget(), () -> true);
        assertThat(out).hasSize(n);
        assertThat(news.requests).as("one request per query").hasSize(n);
        assertThat(news.maxOpen()).as("max open = min(concurrency, queries)").isEqualTo(Math.min(concurrency, n));
        assertThat(out).allSatisfy(r -> assertThat(r.status()).isEqualTo(SearchQueryStatus.EMPTY));
    }

    @Test
    void theCallReturnsOnlyWhenEveryRequestHasEnded() throws Exception {
        // the last query (concurrency9) answers much later than the others: the call must wait for it
        news.responder = req -> {
            long delay = req.q().contains("concurrency9 ") ? 800 : 100;
            return new StubNews.Reply(200, "application/rss+xml", StubNews.rss(
                StubNews.rssItem("t " + req.q(), "https://news.example.org/" + Math.abs(req.q().hashCode()), null, "Reuters", "https://www.reuters.com")).body(),
                delay);
        };
        long start = System.nanoTime();
        List<QR> out = ParallelSearchSupport.search(search(8), texts(9), HorizonCode._1Y, noWindowBudget(), () -> true);
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertThat(elapsedMs).as("the join: the call returned only after the slowest request (800 ms) ended").isGreaterThanOrEqualTo(800);
        assertThat(out).hasSize(9);
        assertThat(out).allSatisfy(r -> {
            assertThat(r.status()).isEqualTo(SearchQueryStatus.OK);
            assertThat(r.articles()).hasSize(1);
        });
    }

    @Test
    void theResultsKeepThePlanOrderWhateverTheAnswerOrder() throws Exception {
        // reverse delays: the last text answers first
        news.responder = req -> {
            String q = req.elements().get(0);
            int i = Integer.parseInt(q.substring("concurrency".length(), q.indexOf(' ')));
            return new StubNews.Reply(200, "application/rss+xml", StubNews.rss(
                StubNews.rssItem("item " + i, "https://news.example.org/i" + i, null, "Reuters", "https://www.reuters.com")).body(),
                (10 - i) * 60L);
        };
        List<QR> out = ParallelSearchSupport.search(search(8), texts(8), HorizonCode._1Y, noWindowBudget(), () -> true);
        for (int i = 0; i < 8; i++) {
            assertThat(out.get(i).articles()).as("result " + (i + 1)).hasSize(1);
            assertThat(out.get(i).articles().get(0).title()).isEqualTo("item " + (i + 1));
        }
    }

    // (d) guard false from the start
    @Test
    void aGuardThatIsFalseFromTheStartSendsNothingAndFailsEveryQuery() throws Exception {
        List<QR> out = ParallelSearchSupport.search(search(8), texts(20), HorizonCode._1Y, noWindowBudget(), () -> false);
        assertThat(news.requests).isEmpty();
        assertThat(out).hasSize(20).allSatisfy(r -> assertThat(r.status()).isEqualTo(SearchQueryStatus.FAILED));
    }

    // (d) with concurrency 1 the guard turns false after k requests
    @ParameterizedTest(name = "guard turns false after {0} requests")
    @ValueSource(ints = {0, 1, 5})
    void aGuardThatTurnsFalseAfterKRequestsStopsExactlyThere(int k) throws Exception {
        news.responder = req -> StubNews.rss(
            StubNews.rssItem("t", "https://news.example.org/k" + req.number(), null, "Reuters", "https://www.reuters.com"));
        List<QR> out = ParallelSearchSupport.search(search(1), texts(8), HorizonCode._1Y, noWindowBudget(), () -> news.requests.size() < k);
        assertThat(news.requests).as("exactly k requests").hasSize(k);
        for (int i = 0; i < 8; i++) {
            assertThat(out.get(i).status()).as("Q" + (i + 1)).isEqualTo(i < k ? SearchQueryStatus.OK : SearchQueryStatus.FAILED);
        }
    }

    @Test
    void aNullTextIsNotSentAndIsEmpty() throws Exception {
        List<String> in = new ArrayList<>();
        in.add(null);
        in.add("solar flare");
        in.add("OR AND NOT");
        List<QR> out = ParallelSearchSupport.search(search(1), in, HorizonCode._1Y, noWindowBudget(), () -> true);
        assertThat(news.requests).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(out.get(0).articles()).isEmpty();
        assertThat(out.get(1).status()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(out.get(2).status()).isEqualTo(SearchQueryStatus.EMPTY);
    }

    // ---- (b) startup classes through both constructors --------------------------------------------------------------------

    static Stream<Arguments> concurrencies() {
        return Stream.of(0, 9, -1, 100, Integer.MIN_VALUE).map(Arguments::of);
    }

    @ParameterizedTest(name = "test constructor, concurrency {0} fails")
    @MethodSource("concurrencies")
    void theTestConstructorRejectsConcurrencyOutsideOneToEight(int concurrency) {
        assertThatThrownBy(() -> ParallelSearchSupport.googleSearch(new GoogleNewsProvider(news.baseUrl()), concurrency, Duration.ofSeconds(1), Duration.ZERO))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("oracul.news.google.concurrency");
    }

    @ParameterizedTest(name = "test constructor, concurrency {0} starts")
    @ValueSource(ints = {1, 2, 7, 8})
    void theTestConstructorAcceptsConcurrencyOneToEight(int concurrency) {
        assertThat(ParallelSearchSupport.googleSearch(new GoogleNewsProvider(news.baseUrl()), concurrency, Duration.ofSeconds(1), Duration.ZERO)).isNotNull();
    }

    @Test
    void theTestConstructorRejectsANegativeRateLimitWaitAndAcceptsZero() {
        assertThatThrownBy(() -> ParallelSearchSupport.googleSearch(new GoogleNewsProvider(news.baseUrl()), 8, Duration.ofSeconds(1), Duration.ofSeconds(-1)))
            .isInstanceOf(IllegalStateException.class).hasMessageContaining("oracul.news.google.rate-limit-wait");
        assertThat(ParallelSearchSupport.googleSearch(new GoogleNewsProvider(news.baseUrl()), 8, Duration.ofSeconds(1), Duration.ZERO)).isNotNull();
    }

    private static Throwable startup(Map<String, String> props) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean[] ran = {false};
        ParallelSearchSupport.googleSearchFromProperties(props, f -> {
            ran[0] = true;
            failure.set(f);
        });
        assertThat(ran[0]).isTrue();
        return failure.get();
    }

    @ParameterizedTest(name = "property oracul.news.google.concurrency={0} fails startup naming it")
    @ValueSource(strings = {"0", "9", "-1", "100"})
    void aConcurrencyPropertyOutsideOneToEightFailsStartup(String value) {
        Throwable failure = startup(Map.of("oracul.news.google.concurrency", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(ParallelSearchSupport.chain(failure)).contains("oracul.news.google.concurrency");
    }

    @ParameterizedTest(name = "property oracul.news.google.concurrency={0} starts")
    @ValueSource(strings = {"1", "8"})
    void aConcurrencyPropertyOfOneOrEightStarts(String value) {
        assertThat(startup(Map.of("oracul.news.google.concurrency", value))).isNull();
    }

    @Test
    void theDefaultsStart() {
        assertThat(startup(Map.of())).isNull();
    }

    @Test
    void aRateLimitWaitPropertyOfZeroStartsAndANegativeOneFailsNamingIt() {
        assertThat(startup(Map.of("oracul.news.google.rate-limit-wait", "PT0S"))).isNull();
        Throwable failure = startup(Map.of("oracul.news.google.rate-limit-wait", "-PT1S"));
        assertThat(failure).isNotNull();
        assertThat(ParallelSearchSupport.chain(failure)).contains("oracul.news.google.rate-limit-wait");
    }

    private static Throwable startupOfRetrieval(Map<String, String> props) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        boolean[] ran = {false};
        ParallelSearchSupport.retrievalFromProperties(props, f -> {
            ran[0] = true;
            failure.set(f);
        });
        assertThat(ran[0]).isTrue();
        return failure.get();
    }

    @ParameterizedTest(name = "oracul.search.search-window={0} fails startup naming it")
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void aSearchWindowOfZeroOrLessFailsStartup(String value) {
        Throwable failure = startupOfRetrieval(Map.of("oracul.search.search-window", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(ParallelSearchSupport.chain(failure)).contains("oracul.search.search-window");
    }

    @ParameterizedTest(name = "oracul.search.search-window={0} starts")
    @ValueSource(strings = {"PT0.001S", "PT60S"})
    void aPositiveSearchWindowStarts(String value) {
        assertThat(startupOfRetrieval(Map.of("oracul.search.search-window", value))).isNull();
    }

    @Test
    void theSearchWindowDefaultsToSixtySeconds() {
        assertThat(startupOfRetrieval(Map.of())).isNull();
    }

    // article-retrieval.md slice 08: oracul.search.stage-budget (PT90S) is read by SourceRetrieval like the search window
    @ParameterizedTest(name = "oracul.search.stage-budget={0} fails startup naming it")
    @ValueSource(strings = {"PT0S", "-PT1S"})
    void aStageBudgetOfZeroOrLessFailsStartup(String value) {
        Throwable failure = startupOfRetrieval(Map.of("oracul.search.stage-budget", value));
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(ParallelSearchSupport.chain(failure)).contains("oracul.search.stage-budget");
    }

    @ParameterizedTest(name = "oracul.search.stage-budget={0} starts")
    @ValueSource(strings = {"PT0.001S", "PT90S"})
    void aPositiveStageBudgetStarts(String value) {
        assertThat(startupOfRetrieval(Map.of("oracul.search.stage-budget", value))).isNull();
    }

    // ---- the 429 retry re-takes a permit and re-checks the guard and the window (wildcard-search.md FR-52) ------------------

    // the guard is false at the retry: no second request, no window line
    @Test
    void aGuardThatTurnsFalseBeforeTheRetryFailsTheQueryWithoutASecondRequest() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NewsProvider provider = (q, max, timeout) -> {
            calls.incrementAndGet();
            return NewsProvider.Result.tooManyRequests();
        };
        AtomicInteger guardCalls = new AtomicInteger();
        BooleanSupplier guard = () -> guardCalls.incrementAndGet() == 1;
        Object google = ParallelSearchSupport.googleSearch(provider, 1, Duration.ofSeconds(10), Duration.ZERO);
        List<QR> out = ParallelSearchSupport.search(google, List.of("solar flare"), HorizonCode._1Y, noWindowBudget(), guard);
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(calls.get()).as("no retry once the guard is false").isEqualTo(1);
        assertThat(guardCalls.get()).as("dispatch and retry each ask the guard").isEqualTo(2);
        assertThat(windowLines()).isZero();
    }

    // the window ends after the retry permit was taken: the retry is not sent, the window line is logged
    @Test
    void aWindowThatEndsAfterTheRetryPermitWasTakenFailsTheQueryWithTheWindowLine() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        NewsProvider provider = (q, max, timeout) -> {
            calls.incrementAndGet();
            return NewsProvider.Result.tooManyRequests();
        };
        Instant t0 = Instant.now();
        MutableClock clock = new MutableClock(t0);
        Object budget = ParallelSearchSupport.budget(clock, t0, Duration.ofSeconds(60), null);
        AtomicInteger guardCalls = new AtomicInteger();
        BooleanSupplier guard = () -> {
            if (guardCalls.incrementAndGet() == 2) {
                clock.set(t0.plusSeconds(60));
            }
            return true;
        };
        Object google = ParallelSearchSupport.googleSearch(provider, 1, Duration.ofSeconds(10), Duration.ZERO);
        List<QR> out = ParallelSearchSupport.search(google, List.of("solar flare"), HorizonCode._1Y, budget, guard);
        assertThat(out.get(0).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(calls.get()).as("the retry is not sent after the window ended").isEqualTo(1);
        assertThat(guardCalls.get()).isEqualTo(2);
        assertThat(windowLines()).isEqualTo(1);
    }

    // the retry permit is not available before the window ends: no retry, the window line
    @Test
    void aRetryThatGetsNoPermitBeforeTheWindowEndsIsNotSent() throws Exception {
        Duration window = Duration.ofMillis(500);
        Thread searching = Thread.currentThread();
        AtomicInteger firstQueryCalls = new AtomicInteger();
        CountDownLatch never = new CountDownLatch(1);
        NewsProvider provider = (q, max, timeout) -> {
            if (q.startsWith("alpha")) {
                if (firstQueryCalls.incrementAndGet() == 1) {
                    // answer 429 only once the dispatching thread waits for the permit of query 2 (it is queued first)
                    long giveUp = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                    while (searching.getState() != Thread.State.TIMED_WAITING && System.nanoTime() < giveUp) {
                        Thread.onSpinWait();
                    }
                }
                return NewsProvider.Result.tooManyRequests();
            }
            try {
                // holds the only permit past the window, ignoring the timeout
                never.await(window.toMillis() + 500, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of());
        };
        Object budget = ParallelSearchSupport.budget(Clock.systemUTC(), Instant.now(), window, null);
        Object google = ParallelSearchSupport.googleSearch(provider, 1, Duration.ofSeconds(10), Duration.ZERO);
        List<QR> out = ParallelSearchSupport.search(google, List.of("alpha one", "beta two"), HorizonCode._1Y, budget, () -> true);
        assertThat(out.get(0).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(firstQueryCalls.get()).as("query 1: exactly one request, no retry").isEqualTo(1);
        assertThat(out.get(1).status()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(windowLines()).as("the window line").isGreaterThanOrEqualTo(1);
    }

    // a provider that throws leaves its slot FAILED; the others keep their answers and the call returns
    @Test
    void aProviderThatThrowsFailsOnlyItsSlot() throws Exception {
        NewsProvider provider = (q, max, timeout) -> {
            if (q.startsWith("boom")) {
                throw new IllegalStateException("provider exploded");
            }
            return new NewsProvider.Result(SearchQueryStatus.EMPTY, List.of());
        };
        Object google = ParallelSearchSupport.googleSearch(provider, 2, Duration.ofSeconds(10), Duration.ZERO);
        List<QR> out = ParallelSearchSupport.search(google, List.of("alpha one", "boom two", "gamma three"), HorizonCode._1Y,
            noWindowBudget(), () -> true);
        assertThat(out).hasSize(3);
        assertThat(out.get(0).status()).isEqualTo(SearchQueryStatus.EMPTY);
        assertThat(out.get(1).status()).isEqualTo(SearchQueryStatus.FAILED);
        assertThat(out.get(2).status()).isEqualTo(SearchQueryStatus.EMPTY);
    }

    // an interrupt while the call joins the requests ends the call with InterruptedException and ends the requests
    @Test
    void anInterruptDuringTheJoinThrowsInterruptedExceptionAndEndsTheRequests() throws Exception {
        CountDownLatch inProvider = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch providerEnded = new CountDownLatch(1);
        NewsProvider provider = (q, max, timeout) -> {
            inProvider.countDown();
            try {
                release.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                providerEnded.countDown();
            }
            return NewsProvider.Result.failed();
        };
        Object google = ParallelSearchSupport.googleSearch(provider, 1, Duration.ofSeconds(10), Duration.ZERO);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread searching = new Thread(() -> {
            try {
                ParallelSearchSupport.search(google, List.of("solar flare"), HorizonCode._1Y, noWindowBudget(), () -> true);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        searching.start();
        try {
            assertThat(inProvider.await(10, TimeUnit.SECONDS)).as("the request is open").isTrue();
            searching.interrupt();
            searching.join(10_000);
            assertThat(searching.isAlive()).as("the call ends after the interrupt").isFalse();
            assertThat(thrown.get()).isInstanceOf(InterruptedException.class);
            assertThat(providerEnded.await(10, TimeUnit.SECONDS)).as("the open request was interrupted").isTrue();
        } finally {
            release.countDown();
            searching.interrupt();
        }
    }
}
