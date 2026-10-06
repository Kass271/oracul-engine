package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/** Article metadata fetch rules against a loopback stub (no internet): failures give an empty result (FR-13, FR-48). */
// @trace FR-13
// @trace FR-48
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

    private ArticleMetadataFetcher fetcher(Duration timeout) {
        return new ArticleMetadataFetcher(timeout, 524288);
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
