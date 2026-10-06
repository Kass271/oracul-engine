package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/** Article metadata fetch rules against a loopback stub (no internet): failures give an empty result (FR-13, FR-48). */
// @trace FR-13
// @trace FR-48
// @trace FR-56
@Timeout(30)
class ArticleMetadataFetcherTest {

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
        ((java.util.concurrent.ExecutorService) server.getExecutor()).shutdownNow();
    }

    private String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private void reply(HttpExchange ex, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        if (type != null) {
            ex.getResponseHeaders().add("Content-Type", type);
        }
        ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            ex.getResponseBody().write(bytes);
        }
        ex.close();
    }

    /** The real fetcher on a SafeFetcher that exempts the loopback address of the local server (FR-56). */
    private ArticleMetadataFetcher fetcher(Duration timeout) {
        return SafeFetcherSupport.metadataFetcher(SafeFetcherSupport.fetcher(
            new SafeFetcherSupport.Resolver(), Set.of("127.0.0.1"), timeout, 2097152, 5));
    }

    /** Without the exemption the loopback server is a blocked address: every fetch is empty and nothing is requested. */
    // @trace FR-56
    @Test
    void withoutTheExemptionEveryFetchOfALoopbackServerIsEmpty() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/page", ex -> {
            requests.incrementAndGet();
            reply(ex, 200, "text/html", "<meta name=\"description\" content=\"secret\">");
        });
        server.createContext("/moved", ex -> {
            requests.incrementAndGet();
            ex.getResponseHeaders().add("Location", "/page");
            reply(ex, 302, null, "");
        });
        server.start();
        ArticleMetadataFetcher f = SafeFetcherSupport.metadataFetcher(SafeFetcherSupport.fetcher(
            new SafeFetcherSupport.Resolver(), Set.of(), Duration.ofSeconds(5), 2097152, 5));
        assertThat(f.fetch(url("/page"))).isEmpty();
        assertThat(f.fetchDetailed(url("/page"), 5)).isEmpty();
        assertThat(f.fetchDetailed(url("/moved"), 5)).isEmpty();
        assertThat(f.fetch("file:///etc/passwd")).isEmpty();
        assertThat(requests.get()).as("no request reached the blocked address").isZero();
    }

    /** A redirect from an exempt host to a blocked one is refused at the hop. */
    // @trace FR-56
    @Test
    void aRedirectFromTheExemptHostToABlockedAddressGivesNoResult() {
        AtomicInteger requests = new AtomicInteger();
        server.createContext("/start", ex -> {
            requests.incrementAndGet();
            ex.getResponseHeaders().add("Location", "http://10.0.0.5/internal");
            reply(ex, 302, null, "");
        });
        server.start();
        assertThat(fetcher(Duration.ofSeconds(5)).fetchDetailed(url("/start"), 5)).isEmpty();
        assertThat(requests.get()).isEqualTo(1);
    }

    /** The redirect limit is min(argument, article-max-redirects), and fetch(String) uses article-max-redirects (5, was 3). */
    // @trace FR-56
    @Test
    void fetchOfAStringFollowsFiveRedirectsAndTheArgumentLowersTheLimit() {
        server.createContext("/hop/", ex -> {
            int n = Integer.parseInt(ex.getRequestURI().getPath().substring("/hop/".length()));
            if (n == 0) {
                reply(ex, 200, "text/html", "<meta name=\"description\" content=\"Arrived\">");
            } else {
                ex.getResponseHeaders().add("Location", "/hop/" + (n - 1));
                reply(ex, 302, null, "");
            }
        });
        server.start();
        ArticleMetadataFetcher f = fetcher(Duration.ofSeconds(5));
        assertThat(f.fetch(url("/hop/5"))).as("5 redirects with fetch(String)").isPresent();
        assertThat(f.fetch(url("/hop/6"))).as("6 redirects with fetch(String)").isEmpty();
        assertThat(f.fetchDetailed(url("/hop/4"), 4)).isPresent();
        assertThat(f.fetchDetailed(url("/hop/5"), 4)).as("the argument lowers the limit").isEmpty();
        assertThat(f.fetchDetailed(url("/hop/6"), 50)).as("the argument cannot raise the limit above 5").isEmpty();
    }

    /** Only the bytes read are parsed: the page is cut at 2 MB (was 512 KB). */
    // @trace FR-56
    @Test
    void onlyTheFirstTwoMegabytesOfAPageAreParsed() {
        String filler = "x".repeat(2097152 - 200);
        server.createContext("/inside", ex -> reply(ex, 200, "text/html",
            "<meta property=\"og:description\" content=\"Inside\">" + filler + "<meta property=\"og:site_name\" content=\"Late\">"));
        server.createContext("/beyond", ex -> reply(ex, 200, "text/html",
            filler + filler + "<meta property=\"og:description\" content=\"Beyond\">"));
        server.start();
        ArticleMetadataFetcher f = fetcher(Duration.ofSeconds(5));
        var inside = f.fetch(url("/inside"));
        assertThat(inside).isPresent();
        assertThat(inside.get().description()).isEqualTo("Inside");
        var beyond = f.fetch(url("/beyond"));
        assertThat(beyond).isPresent();
        assertThat(beyond.get().description()).as("the tag lies beyond the 2 MB read").isNull();
    }

    private static final int TWO_MB = 2097152;

    static Stream<String> pathologicalBodies() {
        String tail = "<meta property=\"og:description\" content=\"Found\">";
        return Stream.of(
            "<meta ".repeat(TWO_MB / 6),
            "<meta " + "a".repeat(TWO_MB - 6),
            "<meta name=a content=b ".repeat(TWO_MB / 23),
            "<meta a=\"".repeat(TWO_MB / 9),
            "<meta" + " ".repeat(TWO_MB - 5),
            "<meta x=y".repeat(TWO_MB / 9) + tail);
    }

    /** A pathological 2 MB page (unclosed or endless meta tags) cannot stall the fetch: it returns within seconds. */
    // @trace FR-56
    @ParameterizedTest
    @MethodSource("pathologicalBodies")
    void aPathologicalTwoMegabytePageCannotStallTheFetch(String body) {
        server.createContext("/evil", ex -> reply(ex, 200, "text/html", body));
        server.start();
        ArticleMetadataFetcher f = fetcher(Duration.ofSeconds(1));
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            f.fetch(url("/evil"));
        }, "fetch must return in a few seconds");
    }

    private static final int MAX_TAG_LENGTH = 4096;
    private static final String FOUND_TAIL = "<meta property=\"og:description\" content=\"Found\">";

    /** A closed meta tag of exactly {@code total} characters ("&lt;meta " + filler + "&gt;"), the filler built from {@code unit}. */
    private static String closedTag(String unit, int total) {
        int fill = total - 7;
        StringBuilder sb = new StringBuilder("<meta ");
        while (sb.length() < 6 + fill) {
            sb.append(unit);
        }
        sb.setLength(6 + fill);
        return sb.append('>').toString();
    }

    /** Many closed tags just under the length cap, repeated up to 2 MB, then a normal description tag. */
    private static String manyClosedTagsThenTail(String unit) {
        String tag = closedTag(unit, MAX_TAG_LENGTH);
        StringBuilder sb = new StringBuilder(TWO_MB);
        while (sb.length() + tag.length() + FOUND_TAIL.length() <= TWO_MB) {
            sb.append(tag);
        }
        return sb.append(FOUND_TAIL).toString();
    }

    static Stream<String> closedTagBodiesNearTheCap() {
        return Stream.of("a", "a=", "a ", "a =", "\"a", "'a").map(ArticleMetadataFetcherTest::manyClosedTagsThenTail);
    }

    /** Many closed tags just under the cap (where the cost sits) are parsed quickly and a later normal tag is still found. */
    // @trace FR-56
    @ParameterizedTest
    @MethodSource("closedTagBodiesNearTheCap")
    void manyClosedTagsNearTheLengthCapAreParsedQuicklyAndALaterTagIsStillFound(String body) {
        assertThat(body.length()).isLessThanOrEqualTo(TWO_MB);
        var meta = assertTimeoutPreemptively(Duration.ofSeconds(2), () -> ArticleMetadataFetcher.parse(body),
            "parse must return within about 2 seconds");
        assertThat(meta.description()).as("the normal tag after the filler is found").isEqualTo("Found");
    }

    /** The same bodies over HTTP: the fetch returns quickly and the later tag is still found. */
    // @trace FR-56
    @ParameterizedTest
    @MethodSource("closedTagBodiesNearTheCap")
    void fetchOfManyClosedTagsNearTheLengthCapReturnsQuicklyWithTheLaterTag(String body) {
        server.createContext("/evil", ex -> reply(ex, 200, "text/html", body));
        server.start();
        ArticleMetadataFetcher f = fetcher(Duration.ofSeconds(5));
        var meta = assertTimeoutPreemptively(Duration.ofSeconds(5), () -> f.fetch(url("/evil")),
            "fetch must return in a few seconds");
        assertThat(meta).isPresent();
        assertThat(meta.get().description()).isEqualTo("Found");
    }

    private static final String DESCRIPTION_HEAD = "<meta property=\"og:description\" content=\"";

    private static String descriptionTagOfLength(int total) {
        String head = DESCRIPTION_HEAD;
        String tail = "\">";
        return head + "x".repeat(total - head.length() - tail.length()) + tail;
    }

    /** A tag of exactly the cap is parsed; a tag clearly over it (cap + 2, safe for either way of counting) is skipped. */
    // @trace FR-56
    @Test
    void aTagOfExactlyTheCapIsParsedAndATagOverTheCapIsSkipped() {
        String atCap = descriptionTagOfLength(MAX_TAG_LENGTH);
        assertThat(atCap.length()).isEqualTo(MAX_TAG_LENGTH);
        assertThat(ArticleMetadataFetcher.parse(atCap).description()).as("tag of exactly the cap").hasSize(MAX_TAG_LENGTH - DESCRIPTION_HEAD.length() - 2);
        String over = descriptionTagOfLength(MAX_TAG_LENGTH + 2);
        assertThat(ArticleMetadataFetcher.parse(over).description()).as("tag over the cap is skipped").isNull();
        assertThat(ArticleMetadataFetcher.parse(over + FOUND_TAIL).description())
            .as("parsing continues after a skipped tag").isEqualTo("Found");
    }

    /** Pathological bodies fed to the parser directly also finish within seconds. */
    // @trace FR-56
    @ParameterizedTest
    @MethodSource("pathologicalBodies")
    void theParserFinishesQuicklyOnAPathologicalTwoMegabyteBody(String body) {
        assertTimeoutPreemptively(Duration.ofSeconds(5), () -> {
            ArticleMetadataFetcher.parse(body);
        }, "parse must return in a few seconds");
    }

    @Test
    void fetchReadsDescriptionAndSiteNameOfAnHtmlPage() {
        server.createContext("/page", ex -> reply(ex, 200, "text/html; charset=utf-8",
            "<html><head><meta property=\"og:description\" content=\"A &amp; B\">"
                + "<meta property=\"og:site_name\" content=\"Daily\"></head></html>"));
        server.start();
        Optional<ArticleMetadataFetcher.Metadata> meta = fetcher(Duration.ofSeconds(5)).fetch(url("/page"));
        assertThat(meta).isPresent();
        assertThat(meta.get().description()).isEqualTo("A & B");
        assertThat(meta.get().siteName()).isEqualTo("Daily");
    }

    @Test
    void aRedirectWithoutLocationGivesNoResult() {
        server.createContext("/moved", ex -> reply(ex, 302, null, ""));
        server.start();
        assertThat(fetcher(Duration.ofSeconds(5)).fetchDetailed(url("/moved"), 3)).isEmpty();
    }

    @Test
    void aRedirectIsFollowedAndCounted() {
        server.createContext("/start", ex -> {
            ex.getResponseHeaders().add("Location", "/end");
            reply(ex, 302, null, "");
        });
        server.createContext("/end", ex -> reply(ex, 200, "text/html", "<meta name=\"description\" content=\"Done\">"));
        server.start();
        var fetched = fetcher(Duration.ofSeconds(5)).fetchDetailed(url("/start"), 3);
        assertThat(fetched).isPresent();
        assertThat(fetched.get().redirects()).isEqualTo(1);
        assertThat(fetched.get().finalUrl()).isEqualTo(url("/end"));
        assertThat(fetched.get().metadata().description()).isEqualTo("Done");
    }

    @Test
    void moreRedirectsThanAllowedGiveNoResult() {
        server.createContext("/loop", ex -> {
            ex.getResponseHeaders().add("Location", "/loop");
            reply(ex, 302, null, "");
        });
        server.start();
        assertThat(fetcher(Duration.ofSeconds(5)).fetchDetailed(url("/loop"), 3)).isEmpty();
    }

    @Test
    void aPageThatAnswersAfterTheTimeoutGivesNoResult() {
        server.createContext("/slow", ex -> {
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            reply(ex, 200, "text/html", "<meta name=\"description\" content=\"late\">");
        });
        server.start();
        long started = System.nanoTime();
        Optional<ArticleMetadataFetcher.Fetched> result = fetcher(Duration.ofMillis(300)).fetchDetailed(url("/slow"), 3);
        assertThat(result).isEmpty();
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(5));
    }

    @Test
    void anInterruptedCallerGetsNoResultAndKeepsItsInterruptFlag() throws Exception {
        CountDownLatch arrived = new CountDownLatch(1);
        server.createContext("/hang", ex -> {
            arrived.countDown();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            ex.close();
        });
        server.start();
        AtomicReference<Optional<ArticleMetadataFetcher.Fetched>> result = new AtomicReference<>();
        AtomicBoolean flag = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            result.set(fetcher(Duration.ofSeconds(20)).fetchDetailed(url("/hang"), 3));
            flag.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        assertThat(arrived.await(10, TimeUnit.SECONDS)).isTrue();
        caller.interrupt();
        caller.join(10_000);
        assertThat(caller.isAlive()).isFalse();
        assertThat(result.get()).isEmpty();
        assertThat(flag.get()).as("interrupt flag restored").isTrue();
    }

    @Test
    void anUnknownCharsetFallsBackToUtf8() {
        server.createContext("/cs", ex -> reply(ex, 200, "text/html; charset=no-such-charset",
            "<meta property=\"og:site_name\" content=\"Zeitung über alles\">"));
        server.start();
        var meta = fetcher(Duration.ofSeconds(5)).fetch(url("/cs"));
        assertThat(meta).isPresent();
        assertThat(meta.get().siteName()).isEqualTo("Zeitung über alles");
    }

    @Test
    void nonHtmlAndErrorAnswersGiveNoResult() {
        server.createContext("/json", ex -> reply(ex, 200, "application/json", "{}"));
        server.createContext("/gone", ex -> reply(ex, 404, "text/html", "<html></html>"));
        server.start();
        ArticleMetadataFetcher f = fetcher(Duration.ofSeconds(5));
        assertThat(f.fetch(url("/json"))).isEmpty();
        assertThat(f.fetch(url("/gone"))).isEmpty();
    }

    @Test
    void anUnreachableHostGivesNoResult() {
        int port;
        try (java.net.ServerSocket probe = new java.net.ServerSocket(0, 1, java.net.InetAddress.getByName("127.0.0.1"))) {
            port = probe.getLocalPort();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // the port is closed again: the connection is refused at once
        assertThat(fetcher(Duration.ofSeconds(2)).fetch("http://127.0.0.1:" + port + "/x")).isEmpty();
        assertThat(fetcher(Duration.ofSeconds(2)).fetch("not a url")).isEmpty();
    }

    @Test
    void metaTagsWithoutContentAreSkippedAndOgDescriptionWins() {
        var meta = ArticleMetadataFetcher.parse(
            "<meta property=\"og:description\"><meta name=\"description\" content=\"plain\">"
                + "<meta property=\"og:site_name\"><meta property=\"og:site_name\" content=\"Site\">"
                + "<meta property=\"og:description\" content=\"og text\">");
        assertThat(meta.description()).isEqualTo("og text");
        assertThat(meta.siteName()).isEqualTo("Site");
        var none = ArticleMetadataFetcher.parse("<meta name=\"description\">");
        assertThat(none.description()).isNull();
        assertThat(none.siteName()).isNull();
    }
}
