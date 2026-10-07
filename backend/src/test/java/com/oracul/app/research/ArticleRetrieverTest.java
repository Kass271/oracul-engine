package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.ArticleApi.Outcome;
import com.oracul.app.research.SafeFetcherSupport.Fx;
import com.oracul.app.research.SafeFetcherSupport.Resolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;
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
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * FR-54 "Publisher article retrieval", the {@code ArticleRetriever} (article-retrieval.md slice 08 delta) over the in-process stub: the
 * Google-link classes, one outcome per failure class, the eight-permit semaphore (parameterized concurrency 1 and 8), the run guard before every
 * request, the budget, an interrupt, and startup validation. Reached through {@link ArticleApi} (the class does not exist before the slice is
 * built).
 */
// @trace FR-54
@Timeout(120)
class ArticleRetrieverTest {

    private final StubNews news = StubNews.INSTANCE;
    private final Resolver resolver = new Resolver();

    @BeforeEach
    void reset() {
        news.reset();
        resolver.map("localhost", "127.0.0.1");
    }

    @AfterEach
    void release() {
        news.reset(); // never leave a held handler behind
    }

    private String base() {
        return news.baseUrl();
    }

    private Object retriever(int concurrency, Duration timeout) {
        Fx fx = SafeFetcherSupport.fetcher(resolver, Set.of("127.0.0.1", "localhost"), timeout, 2 * 1024 * 1024, 5);
        Object decoder = ArticleApi.decoder(base() + "/_/DotsSplashUi/data/batchexecute", base());
        return ArticleApi.retriever(fx.raw(), decoder, base(), timeout, concurrency, Clock.systemUTC());
    }

    private static Object budget(Duration stage) {
        return ParallelSearchSupport.retrievalBudget(Clock.systemUTC(), Instant.now(), stage, null);
    }

    private List<Outcome> retrieve(Object retriever, List<String> links) {
        return ArticleApi.retrieveAll(retriever, links, budget(Duration.ofSeconds(90)), () -> true);
    }

    private Outcome one(String link) {
        List<Outcome> out = retrieve(retriever(8, Duration.ofSeconds(5)), List.of(link));
        assertThat(out).hasSize(1);
        return out.get(0);
    }

    // ---- (a) Google-link classes -------------------------------------------------------------------------------------------

    static Stream<Arguments> googleLinks() {
        // base kind, link, expected (host only, port ignored; the base host or news.google.com)
        return Stream.of(
            Arguments.of("stub", "https://news.google.com/rss/articles/X", true),
            Arguments.of("stub", "https://NEWS.GOOGLE.COM/x", true),
            Arguments.of("stub", "http://127.0.0.1:{port}/rss/articles/x", true),
            Arguments.of("stub", "http://127.0.0.1:1/x", true),
            Arguments.of("stub", "http://localhost:{port}/x", false),
            Arguments.of("stub", "https://www.reuters.com/x", false),
            Arguments.of("stub", "https://news.google.com.evil.org/x", false));
    }

    @ParameterizedTest(name = "base stub: {1} -> {2}")
    @MethodSource("googleLinks")
    void aLinkIsAGoogleLinkWhenItsHostIsNewsGoogleComOrTheHostOfTheConfiguredBase(String kind, String link, boolean expected) {
        assertThat(ArticleApi.isGoogleLink(retriever(8, Duration.ofSeconds(5)), link.replace("{port}", String.valueOf(news.port())))).isEqualTo(expected);
    }

    @Test
    void withTheRealGoogleBaseTheStubHostIsNoGoogleHost() {
        Fx fx = SafeFetcherSupport.fetcher(resolver, Set.of("127.0.0.1"), Duration.ofSeconds(5), 2 * 1024 * 1024, 5);
        Object decoder = ArticleApi.decoder("https://news.google.com/_/DotsSplashUi/data/batchexecute", "https://news.google.com");
        Object r = ArticleApi.retriever(fx.raw(), decoder, "https://news.google.com", Duration.ofSeconds(5), 8, Clock.systemUTC());
        assertThat(ArticleApi.isGoogleLink(r, "https://news.google.com/rss/articles/X")).isTrue();
        assertThat(ArticleApi.isGoogleLink(r, "http://127.0.0.1:" + news.port() + "/x")).isFalse();
        assertThat(ArticleApi.isGoogleLink(r, "https://www.reuters.com/x")).isFalse();
    }

    // ---- outcomes ------------------------------------------------------------------------------------------------------------

    @Test
    void aGoogleLinkIsDecodedAndThePublisherPageIsRead() {
        news.site("story-1", "The Site");
        Outcome o = one(base() + "/rss/articles/story-1?oc=5");
        assertThat(o.status()).isEqualTo("PAGE_READ");
        assertThat(o.publisherUrl()).isEqualTo(base() + "/articles/story-1");
        assertThat(o.contentType()).startsWith("text/html");
        assertThat(o.body()).contains(StubNews.P1);
        assertThat(o.description()).isEqualTo("Summary of story-1");
        assertThat(o.siteName()).as("never the Google page's og:site_name").isEqualTo("The Site");
        assertThat(o.endedAt()).isNotNull();
        assertThat(news.decodeRequests).hasSize(1);
        assertThat(news.articleRequests).containsExactly("story-1");
    }

    @Test
    void aLinkThatIsNotOnTheGoogleHostIsFetchedDirectlyWithoutADecodeCall() {
        Outcome o = one("http://localhost:" + news.port() + "/articles/direct-1");
        assertThat(o.status()).isEqualTo("PAGE_READ");
        assertThat(o.publisherUrl()).isEqualTo("http://localhost:" + news.port() + "/articles/direct-1");
        assertThat(news.decodeRequests).isEmpty();
        assertThat(news.googlePageRequests).isEmpty();
        assertThat(news.articleRequests).containsExactly("direct-1");
    }

    static Stream<Arguments> failures() {
        // name, setup id, expected status, publisher URL known (decoded), reason class
        return Stream.of(
            Arguments.of("Google page answers 404", "g404", "DECODE_FAILED", false),
            Arguments.of("Google page without attributes", "noattr", "DECODE_FAILED", false),
            Arguments.of("decode answers 500", "dec500", "DECODE_FAILED", false),
            Arguments.of("decode answers a Google host", "decgoogle", "DECODE_FAILED", false),
            Arguments.of("decode returns another Google article link", "decagain", "DECODE_FAILED", false),
            Arguments.of("publisher answers 404", "pub404", "PAGE_FAILED", true),
            Arguments.of("publisher answers 503", "pub503", "PAGE_FAILED", true),
            Arguments.of("publisher answers application/pdf", "pdf", "PAGE_FAILED", true),
            Arguments.of("publisher answers without a Content-Type", "nocontenttype", "PAGE_FAILED", true),
            Arguments.of("decoded address is private", "private", "REFUSED", true),
            Arguments.of("decoded address is loopback by another name", "loopback", "REFUSED", true));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("failures")
    void everyFailureEndsInItsOwnStatusAndKeepsWhatIsKnown(String name, String id, String status, boolean publisherKnown) {
        String link = base() + "/rss/articles/" + id;
        switch (id) {
            case "g404" -> news.googlePages.put(id, new StubNews.Page(404, "text/html", "<html>gone</html>"));
            case "noattr" -> news.googlePages.put(id, new StubNews.Page(200, "text/html", StubNews.googlePage(id, null, null)));
            case "dec500" -> news.decodeMode = "fail";
            case "decgoogle" -> news.decodeMode = "google-host";
            case "decagain" -> news.decoded.put(id, base() + "/rss/articles/again");
            case "pub404" -> news.pages.put(id, new StubNews.Page(404, "text/html", "<html>gone</html>"));
            case "pub503" -> news.pages.put(id, new StubNews.Page(503, "text/html", "<html>down</html>"));
            case "nocontenttype" -> news.pages.put(id, new StubNews.Page(200, null, "<html><body><p>no type</p></body></html>"));
            case "private" -> news.decoded.put(id, "http://10.0.0.5/x");
            case "loopback" -> news.decoded.put(id, "http://[::1]:" + news.port() + "/articles/x");
            default -> { }
        }
        Fx fx = SafeFetcherSupport.fetcher(resolver, Set.of("127.0.0.1"), Duration.ofSeconds(5), 2 * 1024 * 1024, 5); // localhost is not exempt here
        Object decoder = ArticleApi.decoder(base() + "/_/DotsSplashUi/data/batchexecute", base());
        Object retriever = ArticleApi.retriever(fx.raw(), decoder, base(), Duration.ofSeconds(5), 8, Clock.systemUTC());
        Outcome o = retrieve(retriever, List.of(link)).get(0);
        assertThat(o.status()).as(name).isEqualTo(status);
        if (publisherKnown) assertThat(o.publisherUrl()).as(name).isNotNull();
        else assertThat(o.publisherUrl()).as(name + ": nothing decoded").isNull();
        assertThat(o.body()).as(name).isNull();
        if (id.equals("decagain")) assertThat(news.googlePageRequests.stream().map(StubNews.GooglePageHit::id)).as("no request follows the decoded Google link").doesNotContain("again");
        if (id.equals("private") || id.equals("loopback")) assertThat(news.articleRequests).as("a refused address is never requested").isEmpty();
    }

    @Test
    void aGoogleLinkWhoseGooglePageRedirectsOffHostIsDecodeFailedAndNothingIsRequestedThere() {
        String target = "http://localhost:" + news.port() + "/articles/off-host";
        Outcome o = one(base() + "/redirect-to?location=" + java.net.URLEncoder.encode(target, java.nio.charset.StandardCharsets.UTF_8));
        assertThat(o.status()).isEqualTo("DECODE_FAILED");
        assertThat(news.articleRequests).isEmpty();
    }

    @Test
    void aGoogleLinkWithMoreThanFiveRedirectsIsDecodeFailed() {
        assertThat(one(base() + "/redirect/6").status()).isEqualTo("DECODE_FAILED");
    }

    @Test
    void aSlowPublisherPageIsAPageFailureAfterTheTimeout() {
        news.publisherDelayMs = 1500;
        Outcome o = retrieve(retriever(8, Duration.ofMillis(600)), List.of(base() + "/rss/articles/slow-pub")).get(0);
        assertThat(o.status()).isEqualTo("PAGE_FAILED");
        assertThat(o.publisherUrl()).isEqualTo(base() + "/articles/slow-pub");
    }

    @Test
    void aSlowDecodeIsADecodeFailureAfterTheTimeout() {
        news.decodeDelayMs = 1500;
        Outcome o = retrieve(retriever(8, Duration.ofMillis(600)), List.of(base() + "/rss/articles/slow-dec")).get(0);
        assertThat(o.status()).isEqualTo("DECODE_FAILED");
    }

    @Test
    void theOutcomesAreInTheOrderOfTheLinks() {
        news.pages.put("b", new StubNews.Page(404, "text/html", "<html>gone</html>"));
        List<Outcome> out = retrieve(retriever(8, Duration.ofSeconds(5)), List.of(base() + "/rss/articles/a", base() + "/rss/articles/b", base() + "/rss/articles/c"));
        assertThat(out.stream().map(Outcome::status).toList()).containsExactly("PAGE_READ", "PAGE_FAILED", "PAGE_READ");
        assertThat(out.stream().map(Outcome::publisherUrl).toList())
            .containsExactly(base() + "/articles/a", base() + "/articles/b", base() + "/articles/c");
    }

    // ---- (g) concurrency ----------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "concurrency {0}: 20 Google links, every answer delayed 200 ms")
    @ValueSource(ints = {1, 8})
    void neverMoreRetrievalRequestsAreOpenThanThePermits(int c) {
        news.googlePageDelayMs = 200;
        news.decodeDelayMs = 200;
        news.publisherDelayMs = 200;
        List<String> links = IntStream.rangeClosed(1, 20).mapToObj(i -> base() + "/rss/articles/c" + i).toList();
        List<Outcome> out = retrieve(retriever(c, Duration.ofSeconds(30)), links);
        assertThat(out).hasSize(20);
        assertThat(out.stream().map(Outcome::status)).containsOnly("PAGE_READ");
        assertThat(news.retrievalMaxOpen()).as("most requests open at once").isEqualTo(c);
        Set<String> firstPages = news.googlePageRequests.stream().filter(h -> !h.redirect()).limit(c).map(StubNews.GooglePageHit::id)
            .collect(Collectors.toSet());
        Set<String> expected = IntStream.rangeClosed(1, c).mapToObj(i -> "c" + i).collect(Collectors.toSet());
        assertThat(firstPages).as("the first c Google-page requests are for links 1..c").isEqualTo(expected);
        assertThat(news.decodeRequests).hasSize(20);
        assertThat(new HashSet<>(news.articleRequests)).hasSize(20);
    }

    @Test
    void threeLinksWithEightPermitsOpenAtMostThree() {
        news.googlePageDelayMs = 200;
        news.decodeDelayMs = 200;
        news.publisherDelayMs = 200;
        List<Outcome> out = retrieve(retriever(8, Duration.ofSeconds(30)), List.of(base() + "/rss/articles/t1", base() + "/rss/articles/t2", base() + "/rss/articles/t3"));
        assertThat(out.stream().map(Outcome::status)).containsOnly("PAGE_READ");
        assertThat(news.retrievalMaxOpen()).isBetween(1, 3);
    }

    // ---- (h) the run guard -----------------------------------------------------------------------------------------------------

    private int retrievalRequests() {
        return (int) news.googlePageRequests.stream().filter(h -> !h.redirect()).count() + news.decodeRequests.size() + news.articleRequests.size();
    }

    @Test
    void aGuardThatIsFalseFromTheStartMakesNoRequestAndEveryLinkIsNotAttempted() {
        List<Outcome> out = ArticleApi.retrieveAll(retriever(8, Duration.ofSeconds(5)),
            List.of(base() + "/rss/articles/g1", base() + "/rss/articles/g2", base() + "/rss/articles/g3"), budget(Duration.ofSeconds(90)), () -> false);
        assertThat(out.stream().map(Outcome::status)).containsOnly("NOT_ATTEMPTED");
        assertThat(out.stream().map(Outcome::publisherUrl)).containsOnlyNulls();
        assertThat(news.googlePageRequests).isEmpty();
        assertThat(news.decodeRequests).isEmpty();
        assertThat(news.articleRequests).isEmpty();
    }

    @ParameterizedTest(name = "the guard turns false after request {0} of one link")
    @ValueSource(ints = {1, 2})
    void aGuardThatTurnsFalseAfterTheKthRequestAllowsExactlyKRequests(int k) {
        BooleanSupplier guard = () -> retrievalRequests() < k;
        List<Outcome> out = ArticleApi.retrieveAll(retriever(8, Duration.ofSeconds(5)), List.of(base() + "/rss/articles/k1"), budget(Duration.ofSeconds(90)), guard);
        assertThat(out.get(0).status()).isEqualTo("NOT_ATTEMPTED");
        assertThat(retrievalRequests()).as("exactly k requests").isEqualTo(k);
        if (k == 1) assertThat(out.get(0).publisherUrl()).as("nothing decoded yet").isNull();
        else assertThat(out.get(0).publisherUrl()).as("the URL known so far").isEqualTo(base() + "/articles/k1");
    }

    // ---- the budget ----------------------------------------------------------------------------------------------------------------

    @Test
    void anExpiredBudgetMakesNoRequestAndEveryLinkIsNotAttempted() {
        Object expired = ParallelSearchSupport.retrievalBudget(Clock.systemUTC(), Instant.now().minusSeconds(100), Duration.ofSeconds(90), null);
        List<Outcome> out = ArticleApi.retrieveAll(retriever(8, Duration.ofSeconds(5)), List.of(base() + "/rss/articles/e1", base() + "/rss/articles/e2"), expired, () -> true);
        assertThat(out.stream().map(Outcome::status)).containsOnly("NOT_ATTEMPTED");
        assertThat(news.googlePageRequests).isEmpty();
    }

    @Test
    void aBudgetThatEndsWhileARequestIsHeldReturnsPromptlyWithTheUrlKnownSoFar() {
        news.articleGate = new CountDownLatch(1);
        long t0 = System.nanoTime();
        List<Outcome> out = ArticleApi.retrieveAll(retriever(8, Duration.ofSeconds(30)), List.of(base() + "/rss/articles/held"), budget(Duration.ofSeconds(1)), () -> true);
        long ms = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
        assertThat(out.get(0).status()).isEqualTo("NOT_ATTEMPTED");
        assertThat(out.get(0).publisherUrl()).isEqualTo(base() + "/articles/held");
        assertThat(ms).as("returned at the end of the 1 s budget, not at the 30 s request timeout").isBetween(900L, 2500L);
    }

    @Test
    void anInterruptOfTheCallerCancelsTheLinksAndIsRethrown() throws Exception {
        news.articleGate = new CountDownLatch(1);
        Object retriever = retriever(8, Duration.ofSeconds(30));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                ArticleApi.retrieveAll(retriever, List.of(base() + "/rss/articles/intr"), budget(Duration.ofSeconds(90)), () -> true);
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        caller.start();
        long end = System.currentTimeMillis() + 15_000;
        while (news.articleRequests.isEmpty() && System.currentTimeMillis() < end) Thread.sleep(20);
        assertThat(news.articleRequests).as("the link reached the held publisher request").isNotEmpty();
        caller.interrupt();
        caller.join(10_000);
        assertThat(caller.isAlive()).as("the call returned after the interrupt").isFalse();
        Throwable t = thrown.get();
        assertThat(t).as("an InterruptedException was rethrown").isNotNull();
        boolean interrupted = false;
        for (Throwable c = t; c != null; c = c.getCause() == c ? null : c.getCause()) interrupted |= c instanceof InterruptedException;
        assertThat(interrupted).as(String.valueOf(t)).isTrue();
    }

    // ---- startup validation ------------------------------------------------------------------------------------------------------------

    private static Throwable startup(String value) {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Class<?> type = ArticleApi.cls("ArticleRetriever");
        @SuppressWarnings({"unchecked", "rawtypes"})
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean((Class) SafeFetcherSupport.type(), () -> Mockito.mock(SafeFetcherSupport.type()))
            .withBean((Class) ArticleApi.cls("ArticleUrlDecoder"), () -> Mockito.mock(ArticleApi.cls("ArticleUrlDecoder")))
            .withUserConfiguration(type);
        runner.withPropertyValues(value == null ? new String[0] : new String[] {"oracul.news.article-fetch-concurrency=" + value})
            .run(ctx -> failure.set(ctx.getStartupFailure()));
        return failure.get();
    }

    @ParameterizedTest(name = "oracul.news.article-fetch-concurrency={0} fails startup naming it")
    @ValueSource(strings = {"0", "-1", "9", "100"})
    void aConcurrencyOutsideOneToEightFailsStartup(String value) {
        Throwable failure = startup(value);
        assertThat(failure).as("the context must not start").isNotNull();
        assertThat(ParallelSearchSupport.chain(failure)).contains("oracul.news.article-fetch-concurrency");
        Throwable root = failure;
        while (root.getCause() != null && root.getCause() != root) root = root.getCause();
        assertThat(root).isInstanceOf(IllegalStateException.class);
    }

    @ParameterizedTest(name = "oracul.news.article-fetch-concurrency={0} starts")
    @ValueSource(strings = {"1", "2", "8"})
    void aConcurrencyOfOneToEightStarts(String value) {
        assertThat(startup(value)).isNull();
    }

    @Test
    void theDefaultConcurrencyStarts() {
        assertThat(startup(null)).isNull();
    }
}
