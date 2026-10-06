package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.oracul.app.research.SafeFetcherSupport.Fx;
import com.oracul.app.research.SafeFetcherSupport.Res;
import com.oracul.app.research.SafeFetcherSupport.Resolver;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;

/**
 * Safe article fetching (article-retrieval.md FR-56): scheme, address, redirect, size and timeout rules of
 * {@code SafeFetcher} against local HTTP servers and a scripted resolver (no internet, NFR-7). The class is reached
 * through {@link SafeFetcherSupport} (it does not exist while this test is written), so the test compiles and fails on
 * its assertions.
 */
// @trace FR-56
@Timeout(60)
class SafeFetcherTest {

    private static final int MB = 1024 * 1024;
    private static final Duration T5 = Duration.ofSeconds(5);

    private final List<Srv> servers = new ArrayList<>();
    private final CountDownLatch release = new CountDownLatch(1);

    @AfterEach
    void stop() {
        release.countDown();
        servers.forEach(Srv::stop);
    }

    // ---------------------------------------------------------------- local HTTP server

    /** Local server: counts every request; routes are described at {@link #handle}. */
    private final class Srv {
        final HttpServer http;
        final List<String> paths = new CopyOnWriteArrayList<>();
        final List<Map<String, String>> headers = new CopyOnWriteArrayList<>();
        volatile int hopDelayMs;
        volatile int otherPort;

        Srv() throws IOException {
            http = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            http.setExecutor(Executors.newCachedThreadPool());
            http.createContext("/", ex -> {
                try {
                    handle(ex);
                } catch (IOException ignored) {
                    // the client closed the connection (reading stopped)
                } finally {
                    ex.close();
                }
            });
            http.start();
            servers.add(this);
        }

        int port() {
            return http.getAddress().getPort();
        }

        void stop() {
            http.stop(0);
            ((java.util.concurrent.ExecutorService) http.getExecutor()).shutdownNow();
        }

        /**
         * /chain/N: N&gt;0 redirects to /chain/N-1, /chain/0 answers 200 "end" · /loop: redirects to itself ·
         * /end: 200 · /code/C?loc=L: status C with Location L (optional) · /body/N: 200 text/html of N bytes (stalls
         * after 3 MB until the test ends) · /trickle: one byte per 100 ms · /hang: never answers · /to-b: 302 to
         * the other server's /actuator/health · /to-file: 302 to file:///etc/passwd · /dir/a: 302 to relative "b".
         */
        private void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            paths.add(path);
            Map<String, String> h = new LinkedHashMap<>();
            ex.getRequestHeaders().forEach((k, v) -> h.put(k.toLowerCase(), String.join(",", v)));
            headers.add(h);
            if (hopDelayMs > 0 && (path.startsWith("/chain/") || path.equals("/loop"))) {
                sleep(hopDelayMs);
            }
            if (path.startsWith("/chain/")) {
                int n = Integer.parseInt(path.substring("/chain/".length()));
                if (n <= 0) {
                    reply(ex, 200, "end");
                } else {
                    redirect(ex, 302, "/chain/" + (n - 1));
                }
            } else if (path.equals("/loop")) {
                redirect(ex, 302, "/loop");
            } else if (path.equals("/end")) {
                reply(ex, 200, "end");
            } else if (path.equals("/dir/a")) {
                redirect(ex, 302, "b");
            } else if (path.equals("/dir/b")) {
                reply(ex, 200, "dir-b");
            } else if (path.equals("/to-b")) {
                redirect(ex, 302, "http://127.0.0.1:" + otherPort + "/actuator/health");
            } else if (path.equals("/to-file")) {
                redirect(ex, 302, "file:///etc/passwd");
            } else if (path.startsWith("/code/")) {
                int code = Integer.parseInt(path.substring("/code/".length()));
                String q = ex.getRequestURI().getRawQuery();
                if (q != null && q.startsWith("loc=")) {
                    ex.getResponseHeaders().add("Location", URLDecoder.decode(q.substring(4), StandardCharsets.UTF_8));
                }
                reply(ex, code, code == 304 ? "" : "body-" + code);
            } else if (path.startsWith("/body/")) {
                body(ex, Long.parseLong(path.substring("/body/".length())));
            } else if (path.equals("/trickle")) {
                ex.getResponseHeaders().add("Content-Type", "text/html");
                ex.sendResponseHeaders(200, 0);
                OutputStream os = ex.getResponseBody();
                for (int i = 0; i < 100; i++) {
                    os.write('t');
                    os.flush();
                    sleep(100);
                }
            } else if (path.equals("/hang")) {
                await(release, 30);
            } else {
                reply(ex, 404, "not found");
            }
        }

        private void body(HttpExchange ex, long n) throws IOException {
            ex.getResponseHeaders().add("Content-Type", "text/html");
            if (n == 0) {
                ex.sendResponseHeaders(200, -1);
                return;
            }
            ex.sendResponseHeaders(200, n);
            OutputStream os = ex.getResponseBody();
            byte[] chunk = new byte[64 * 1024];
            Arrays.fill(chunk, (byte) 'x');
            long sent = 0;
            while (sent < n) {
                if (sent >= 3L * MB && n > 3L * MB) {
                    await(release, 30); // a client that drains the whole body waits here until its timeout
                }
                int len = (int) Math.min(chunk.length, n - sent);
                os.write(chunk, 0, len);
                sent += len;
            }
        }

        private void reply(HttpExchange ex, int status, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            ex.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
            if (bytes.length > 0) {
                ex.getResponseBody().write(bytes);
            }
        }

        private void redirect(HttpExchange ex, int status, String location) throws IOException {
            ex.getResponseHeaders().add("Location", location);
            ex.sendResponseHeaders(status, -1);
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void await(CountDownLatch latch, int seconds) {
        try {
            latch.await(seconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private Fx fetcher(Resolver resolver, Set<String> allowed, Duration timeout, int maxBytes, int maxRedirects) {
        return SafeFetcherSupport.fetcher(resolver, allowed, timeout, maxBytes, maxRedirects);
    }

    /** `start.test`, `pinned.test` and friends resolve to the local servers (127.0.0.1) and are exempt. */
    private Fx localFetcher(int maxBytes, int maxRedirects, Duration timeout) {
        Resolver r = new Resolver().map("start.test", "127.0.0.1");
        return fetcher(r, Set.of("start.test"), timeout, maxBytes, maxRedirects);
    }

    private static String start(Srv s, String path) {
        return "http://start.test:" + s.port() + path;
    }

    // ---------------------------------------------------------------- (a) refused addresses

    static Stream<String> blockedUrls() {
        return Stream.of(
            "http://127.0.0.1/", "http://127.1.2.3/", "http://[::1]/", "http://0.0.0.0/", "http://[::]/",
            "http://10.0.0.5/", "http://172.16.0.1/", "http://172.31.255.255/", "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data", "http://[fe80::1]/", "http://[fd00::1]/", "http://[fc00::1]/",
            "http://[::ffff:10.0.0.1]/", "http://[::ffff:127.0.0.1]/", "http://internal.test/", "http://mixed.test/",
            "HTTP://LOCALHOST/");
    }

    private static final Pattern HOST_END = Pattern.compile("^(?i)(https?://(?:\\[[^\\]]+\\]|[^/:]+))(/.*)?$");

    /** Same URL with the local server's port after the host. */
    private static String withPort(String url, int port) {
        Matcher m = HOST_END.matcher(url);
        if (!m.matches()) {
            throw new IllegalArgumentException(url);
        }
        return m.group(1) + ":" + port + (m.group(2) == null ? "" : m.group(2));
    }

    private static Resolver blockedResolver() {
        return new Resolver()
            .map("internal.test", "10.0.0.7")
            .map("mixed.test", "93.184.216.34", "10.0.0.7")
            .map("localhost", "127.0.0.1");
    }

    // @trace FR-56
    @ParameterizedTest(name = "refused {0}")
    @MethodSource("blockedUrls")
    void blockedAddressesAreRefusedAndNeverConnected(String url) throws Exception {
        Srv server = new Srv();
        Fx f = fetcher(blockedResolver(), Set.of(), T5, 2 * MB, 5);
        Res plain = f.fetch(url);
        assertThat(plain.outcome()).as(url).isEqualTo("REFUSED_ADDRESS");
        assertThat(plain.body() == null || plain.body().length == 0).as("no body for " + url).isTrue();
        // the same host with the port of a server that is listening: it must receive nothing
        String ported = withPort(url, server.port());
        Res withPort = f.fetch(ported);
        assertThat(withPort.outcome()).as(ported).isEqualTo("REFUSED_ADDRESS");
        assertThat(server.paths).as("requests that reached the local server for " + ported).isEmpty();
    }

    @Test
    void aNameResolvingToAnyBlockedAddressIsRefusedWithoutAConnection() throws Exception {
        Srv server = new Srv();
        Resolver r = new Resolver().map("mixed.test", "93.184.216.34", "127.0.0.1");
        Fx f = fetcher(r, Set.of(), T5, 2 * MB, 5);
        assertThat(f.fetch("http://mixed.test:" + server.port() + "/x").outcome()).isEqualTo("REFUSED_ADDRESS");
        assertThat(server.paths).isEmpty();
    }

    @Test
    void anIpv4MappedIpv6AnswerOfTheResolverIsRefused() throws Exception {
        Srv server = new Srv();
        Resolver r = new Resolver()
            .mapAddresses("mapped-loopback.test", mapped(127, 0, 0, 1))
            .mapAddresses("mapped-private.test", mapped(10, 0, 0, 1));
        Fx f = fetcher(r, Set.of(), T5, 2 * MB, 5);
        for (String host : List.of("mapped-loopback.test", "mapped-private.test")) {
            assertThat(f.fetch("http://" + host + ":" + server.port() + "/x").outcome()).as(host).isEqualTo("REFUSED_ADDRESS");
        }
        assertThat(server.paths).isEmpty();
    }

    private static InetAddress mapped(int a, int b, int c, int d) throws Exception {
        byte[] bytes = new byte[16];
        bytes[10] = (byte) 0xff;
        bytes[11] = (byte) 0xff;
        bytes[12] = (byte) a;
        bytes[13] = (byte) b;
        bytes[14] = (byte) c;
        bytes[15] = (byte) d;
        return Inet6Address.getByAddress(null, bytes, -1); // stays an Inet6Address (getByName would give an Inet4Address)
    }

    // ---------------------------------------------------------------- (a)/(b) the pure check

    static Stream<String> blockedAddresses() {
        return Stream.of("127.0.0.1", "127.1.2.3", "::1", "0.0.0.0", "::", "10.0.0.5", "10.0.0.7", "172.16.0.1",
            "172.31.255.255", "192.168.1.1", "169.254.169.254", "fe80::1", "fd00::1", "fc00::1", "::ffff:10.0.0.1",
            "::ffff:127.0.0.1");
    }

    // @trace FR-56
    @ParameterizedTest(name = "isBlocked({0}) is true")
    @MethodSource("blockedAddresses")
    void isBlockedIsTrueForEveryBlockedClass(String literal) throws Exception {
        assertThat(SafeFetcherSupport.isBlocked(InetAddress.getByName(literal))).as(literal).isTrue();
    }

    static Stream<String> allowedAddresses() {
        return Stream.of("172.15.255.255", "172.32.0.1", "192.169.0.1", "11.0.0.1", "93.184.216.34", "2606:4700::1",
            "::ffff:93.184.216.34", "169.253.255.255", "169.255.0.1", "9.255.255.255", "192.167.255.255", "126.255.255.255");
    }

    // @trace FR-56
    @ParameterizedTest(name = "isBlocked({0}) is false")
    @MethodSource("allowedAddresses")
    void isBlockedIsFalseJustOutsideTheBlockedRanges(String literal) throws Exception {
        assertThat(SafeFetcherSupport.isBlocked(InetAddress.getByName(literal))).as(literal).isFalse();
    }

    // @trace FR-56
    @Test
    void isBlockedSeesThroughIpv6MappedForms() throws Exception {
        for (int[] a : new int[][] {{10, 0, 0, 1}, {127, 0, 0, 1}, {169, 254, 169, 254}, {0, 0, 0, 0}, {172, 16, 0, 1},
            {192, 168, 0, 1}}) {
            assertThat(SafeFetcherSupport.isBlocked(mapped(a[0], a[1], a[2], a[3]))).as(Arrays.toString(a)).isTrue();
        }
        assertThat(SafeFetcherSupport.isBlocked(mapped(93, 184, 216, 34))).isFalse();
        assertThat(SafeFetcherSupport.isBlocked(mapped(172, 32, 0, 1))).isFalse();
    }

    // ---------------------------------------------------------------- (c) schemes

    static Stream<String> forbiddenSchemes() {
        return Stream.of("file:///etc/passwd", "ftp://x/", "gopher://x", "javascript:alert(1)", "data:text/html,x",
            "mailto:a@b.c");
    }

    // @trace FR-56
    @ParameterizedTest(name = "scheme refused {0}")
    @MethodSource("forbiddenSchemes")
    void nonHttpSchemesAreRefusedWithoutLookupOrConnection(String url) {
        Resolver r = new Resolver().map("x", "93.184.216.34");
        Fx f = fetcher(r, Set.of("x"), T5, 2 * MB, 5);
        assertThat(f.fetch(url).outcome()).as(url).isEqualTo("REFUSED_SCHEME");
        assertThat(r.calls).as("the resolver is never called for " + url).isEmpty();
    }

    // @trace FR-56
    @Test
    void upperCaseHttpAndHttpsSchemesAreAccepted() throws Exception {
        Srv s = new Srv();
        Fx f = localFetcher(2 * MB, 5, Duration.ofSeconds(2));
        Res http = f.fetch("HTTP://start.test:" + s.port() + "/end");
        assertThat(http.outcome()).isEqualTo("OK");
        assertThat(http.status()).isEqualTo(200);
        // HTTPS to a plain-text server fails at the handshake, but never as a scheme or address refusal
        Res https = f.fetch("HTTPS://start.test:" + s.port() + "/end");
        assertThat(https.outcome()).isNotIn("REFUSED_SCHEME", "REFUSED_ADDRESS", "OK");
    }

    // ---------------------------------------------------------------- (d) redirect hops

    // @trace FR-56
    @Test
    void aRedirectToALoopbackAddressIsRefusedAtTheHopAndNeverRequested() throws Exception {
        Srv a = new Srv();
        Srv b = new Srv();
        a.otherPort = b.port();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/to-b"));
        assertThat(res.outcome()).isEqualTo("REFUSED_ADDRESS");
        assertThat(a.paths).containsExactly("/to-b");
        assertThat(b.paths).as("server B must receive no request").isEmpty();
    }

    // @trace FR-56
    @Test
    void aRedirectToAFileUrlIsRefusedByScheme() throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/to-file"));
        assertThat(res.outcome()).isEqualTo("REFUSED_SCHEME");
        assertThat(a.paths).containsExactly("/to-file");
    }

    // @trace FR-56
    @Test
    void aRelativeLocationResolvesAgainstTheCurrentUrl() throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/dir/a"));
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.redirects()).isEqualTo(1);
        assertThat(res.finalUri()).hasToString(start(a, "/dir/b"));
        assertThat(new String(res.body(), StandardCharsets.UTF_8)).isEqualTo("dir-b");
    }

    // @trace FR-56
    @ParameterizedTest(name = "status {0} is followed")
    @MethodSource("redirectStatuses")
    void everyRedirectStatusIsFollowed(int status) throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/code/" + status + "?loc=%2Fend"));
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.redirects()).isEqualTo(1);
        assertThat(res.finalUri()).hasToString(start(a, "/end"));
        assertThat(a.paths).containsExactly("/code/" + status, "/end");
    }

    static IntStream redirectStatuses() {
        return IntStream.of(301, 302, 303, 307, 308);
    }

    // @trace FR-56
    @ParameterizedTest(name = "status {0} is an answer, not a redirect")
    @MethodSource("plainAnswerStatuses")
    void otherStatusesAreReturnedAsOkAnswers(int status) throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/code/" + status + "?loc=%2Fend"));
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(status);
        assertThat(res.redirects()).isZero();
        assertThat(a.paths).as("the Location of a " + status + " is not requested").containsExactly("/code/" + status);
        if (status != 304) {
            assertThat(res.contentType()).startsWith("text/html");
            assertThat(new String(res.body(), StandardCharsets.UTF_8)).isEqualTo("body-" + status);
        }
    }

    static IntStream plainAnswerStatuses() {
        return IntStream.of(200, 300, 304, 404, 500);
    }

    // @trace FR-56
    @Test
    void aRedirectWithoutLocationFails() throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/code/302"));
        assertThat(res.outcome()).isEqualTo("FAILED");
    }

    // @trace FR-56
    @Test
    void failuresAreFailedNotExceptions() throws Exception {
        Resolver r = new Resolver().map("closed.test", "127.0.0.1");
        Fx f = fetcher(r, Set.of("closed.test"), Duration.ofSeconds(2), 2 * MB, 5);
        assertThat(f.fetch("http://nohost.test/").outcome()).as("unknown host").isEqualTo("FAILED");
        int closed;
        try (ServerSocket probe = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            closed = probe.getLocalPort();
        }
        assertThat(f.fetch("http://closed.test:" + closed + "/").outcome()).as("connection refused").isEqualTo("FAILED");
        try (ServerSocket garbage = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            Thread t = new Thread(() -> {
                try (Socket s = garbage.accept()) {
                    s.getOutputStream().write("this is not http\r\n\r\n".getBytes(StandardCharsets.UTF_8));
                    s.getOutputStream().flush();
                    sleep(300);
                } catch (IOException ignored) {
                    // test ends
                }
            });
            t.setDaemon(true);
            t.start();
            assertThat(f.fetch("http://closed.test:" + garbage.getLocalPort() + "/").outcome()).as("malformed answer")
                .isEqualTo("FAILED");
        }
    }

    // @trace FR-56
    @Test
    void anInterruptedCallerGetsFailedAndKeepsTheInterruptFlag() throws Exception {
        Srv a = new Srv();
        Fx f = localFetcher(2 * MB, 5, Duration.ofSeconds(20));
        AtomicReference<Res> result = new AtomicReference<>();
        AtomicReference<Boolean> flag = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            result.set(f.fetch(start(a, "/hang")));
            flag.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        long end = System.currentTimeMillis() + 10_000;
        while (a.paths.isEmpty() && System.currentTimeMillis() < end) {
            sleep(10);
        }
        assertThat(a.paths).containsExactly("/hang");
        caller.interrupt();
        caller.join(10_000);
        assertThat(caller.isAlive()).isFalse();
        assertThat(result.get().outcome()).isEqualTo("FAILED");
        assertThat(flag.get()).as("interrupt flag restored").isTrue();
    }

    // ---------------------------------------------------------------- (e) redirect counts

    static Stream<Arguments> chains() {
        return Stream.of(
            Arguments.of(0, 5, "OK", 0, 1), Arguments.of(1, 5, "OK", 1, 2), Arguments.of(4, 5, "OK", 4, 5),
            Arguments.of(5, 5, "OK", 5, 6), Arguments.of(6, 5, "TOO_MANY_REDIRECTS", -1, 6),
            Arguments.of(50, 5, "TOO_MANY_REDIRECTS", -1, 6),
            Arguments.of(0, 0, "OK", 0, 1), Arguments.of(1, 0, "TOO_MANY_REDIRECTS", -1, 1),
            Arguments.of(2, 1, "TOO_MANY_REDIRECTS", -1, 2), Arguments.of(1, 1, "OK", 1, 2));
    }

    // @trace FR-56
    @ParameterizedTest(name = "chain of {0} redirects, limit {1} gives {2}")
    @MethodSource("chains")
    void redirectChainsAreCountedAndCapped(int chain, int limit, String outcome, int redirects, int requests) throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, limit, T5).fetch(start(a, "/chain/" + chain));
        assertThat(res.outcome()).isEqualTo(outcome);
        if (redirects >= 0) {
            assertThat(res.redirects()).isEqualTo(redirects);
            assertThat(res.status()).isEqualTo(200);
        }
        assertThat(a.paths).as("at most maxRedirects + 1 requests, the over-limit Location is never requested")
            .hasSize(requests);
        assertThat(a.paths.size()).isLessThanOrEqualTo(limit + 1);
    }

    // @trace FR-56
    @Test
    void anEndlessRedirectLoopStopsAfterExactlySixRequests() throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/loop"));
        assertThat(res.outcome()).isEqualTo("TOO_MANY_REDIRECTS");
        assertThat(a.paths).hasSize(6);
    }

    // ---------------------------------------------------------------- (f) body size

    static Stream<Arguments> bodies() {
        return Stream.of(
            Arguments.of(0, 0, false), Arguments.of(1, 1, false), Arguments.of(2 * MB - 1, 2 * MB - 1, false),
            Arguments.of(2 * MB, 2 * MB, false), Arguments.of(2 * MB + 1, 2 * MB, true),
            Arguments.of(10 * MB, 2 * MB, true));
    }

    // @trace FR-56
    @ParameterizedTest(name = "body of {0} bytes: {1} read, truncated {2}")
    @MethodSource("bodies")
    void atMostTwoMegabytesOfABodyAreRead(int size, int expectedLength, boolean truncated) throws Exception {
        Srv a = new Srv();
        long t0 = System.nanoTime();
        Res res = localFetcher(2 * MB, 5, T5).fetch(start(a, "/body/" + size));
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.bodyLength()).isEqualTo(expectedLength);
        assertThat(res.truncated()).isEqualTo(truncated);
        assertThat(res.bodyLength()).as("maxBytes is the ceiling").isLessThanOrEqualTo(2 * MB);
        // the 10 MB server stalls after 3 MB: a client that drained the body would run into its 5 s timeout
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).as("reading stops, the body is not drained")
            .isLessThan(Duration.ofSeconds(4));
    }

    // @trace FR-56
    @Test
    void theByteLimitIsTheConfiguredOne() throws Exception {
        Srv a = new Srv();
        Res res = localFetcher(1000, 5, T5).fetch(start(a, "/body/1001"));
        assertThat(res.bodyLength()).isEqualTo(1000);
        assertThat(res.truncated()).isTrue();
    }

    // ---------------------------------------------------------------- (g) timeout per fetch

    // @trace FR-56
    @Test
    void aServerThatNeverAnswersEndsInFailedWithinTheTimeout() throws Exception {
        Srv a = new Srv();
        long t0 = System.nanoTime();
        Res res = localFetcher(2 * MB, 5, Duration.ofMillis(500)).fetch(start(a, "/hang"));
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(2));
    }

    // @trace FR-56
    @Test
    void aBodyTricklingOneBytePerHundredMillisecondsEndsInFailedWithinTheTimeout() throws Exception {
        Srv a = new Srv();
        long t0 = System.nanoTime();
        Res res = localFetcher(2 * MB, 5, Duration.ofMillis(500)).fetch(start(a, "/trickle"));
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(2));
    }

    // @trace FR-56
    @Test
    void fiveSlowHopsShareOneTimeout() throws Exception {
        Srv a = new Srv();
        a.hopDelayMs = 200;
        long t0 = System.nanoTime();
        Res res = localFetcher(2 * MB, 5, Duration.ofMillis(500)).fetch(start(a, "/chain/5"));
        assertThat(res.outcome()).as("5 hops of 200 ms exceed one 500 ms timeout").isEqualTo("FAILED");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(2));
    }

    // @trace FR-56
    @Test
    void theTimeoutArgumentOfTheTwoArgumentFetchReplacesTheConfiguredOne() throws Exception {
        Srv a = new Srv();
        Fx f = localFetcher(2 * MB, 5, Duration.ofSeconds(30));
        long t0 = System.nanoTime();
        Res res = f.fetch(start(a, "/hang"), Duration.ofMillis(400));
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(3));
    }

    // ---------------------------------------------------------------- (h) exemption list

    // @trace FR-56
    @Test
    void anExemptNameIsFetchedAndMatchedCaseInsensitivelyAndExactly() throws Exception {
        Srv s = new Srv();
        Resolver r = new Resolver().map("stub", "127.0.0.1").map("127.0.0.1", "127.0.0.1").map("stub2", "172.18.0.4")
            .map("x.stub", "172.18.0.4").map("stub.", "172.18.0.4");
        Fx f = fetcher(r, Set.of("stub", "127.0.0.1"), Duration.ofSeconds(2), 2 * MB, 5);
        assertThat(f.fetch("http://stub:" + s.port() + "/end").outcome()).isEqualTo("OK");
        assertThat(f.fetch("http://STUB:" + s.port() + "/end").outcome()).as("host names compare case-insensitively")
            .isEqualTo("OK");
        assertThat(f.fetch("http://127.0.0.1:" + s.port() + "/end").outcome()).as("a literal exempt address").isEqualTo("OK");
        for (String other : List.of("stub2", "x.stub", "stub.")) {
            assertThat(f.fetch("http://" + other + ":" + s.port() + "/end").outcome())
                .as(other + " is not on the list (no suffix, prefix or dot match)").isEqualTo("REFUSED_ADDRESS");
        }
        assertThat(s.paths).as("only the three exempt requests reached the server").hasSize(3);
    }

    // @trace FR-56
    @Test
    void anExemptNameResolvingToAPrivateDockerAddressPassesTheCheck() {
        Resolver r = new Resolver().map("stub", "172.18.0.3");
        Fx f = fetcher(r, Set.of("stub"), Duration.ofSeconds(1), 2 * MB, 5);
        // nothing listens at 172.18.0.3: the fetch fails after the check, it is not refused by it
        assertThat(f.fetch("http://stub:4010/x").outcome()).isNotEqualTo("REFUSED_ADDRESS").isNotEqualTo("REFUSED_SCHEME");
        assertThat(r.calls).as("the exempt host is still resolved").isNotEmpty();
    }

    // @trace FR-56
    @Test
    void localhostIsNotCoveredByTheExemptionOfTheLoopbackAddress() throws Exception {
        Srv s = new Srv();
        Resolver r = new Resolver().map("localhost", "127.0.0.1");
        Fx f = fetcher(r, Set.of("127.0.0.1"), T5, 2 * MB, 5);
        assertThat(f.fetch("http://localhost:" + s.port() + "/end").outcome()).isEqualTo("REFUSED_ADDRESS");
        assertThat(s.paths).isEmpty();
    }

    // @trace FR-56
    @Test
    void anEmptyListRefusesEveryBlockedAddress() throws Exception {
        Srv s = new Srv();
        Resolver r = new Resolver().map("start.test", "127.0.0.1");
        Fx f = fetcher(r, Set.of(), T5, 2 * MB, 5);
        assertThat(f.fetch("http://start.test:" + s.port() + "/end").outcome()).isEqualTo("REFUSED_ADDRESS");
        assertThat(f.fetch("http://127.0.0.1:" + s.port() + "/end").outcome()).isEqualTo("REFUSED_ADDRESS");
        assertThat(s.paths).isEmpty();
    }

    // @trace FR-56
    @Test
    void theExemptionListOfThePropertyIsTrimmedCaseInsensitiveAndExact() throws Exception {
        // bound to every local address, so "localhost" works whichever address the system resolver answers first
        HttpServer wide = HttpServer.create(new InetSocketAddress(0), 0);
        wide.createContext("/", ex -> {
            byte[] b = "wide".getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "text/html");
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        wide.start();
        try {
            int port = wide.getAddress().getPort();
            SafeFetcherSupport.withSpring(Map.of("oracul.news.fetch.allowed-private-hosts", " LocalHost , 127.0.0.1 ,"), f -> {
                assertThat(f.fetch("http://localhost:" + port + "/").outcome()).as("entry ' LocalHost ' trimmed").isEqualTo("OK");
                assertThat(f.fetch("http://LOCALHOST:" + port + "/").outcome()).isEqualTo("OK");
                assertThat(f.fetch("http://127.0.0.1:" + port + "/").outcome()).isEqualTo("OK");
            });
            SafeFetcherSupport.withSpring(Map.of("oracul.news.fetch.allowed-private-hosts", "127.0.0.1"), f -> {
                assertThat(f.fetch("http://127.0.0.1:" + port + "/").outcome()).isEqualTo("OK");
                assertThat(f.fetch("http://localhost:" + port + "/").outcome()).as("127.0.0.1 does not cover localhost")
                    .isEqualTo("REFUSED_ADDRESS");
            });
            SafeFetcherSupport.withSpring(Map.of("oracul.news.fetch.allowed-private-hosts", ""), f ->
                assertThat(f.fetch("http://127.0.0.1:" + port + "/").outcome()).as("empty list").isEqualTo("REFUSED_ADDRESS"));
            SafeFetcherSupport.withSpring(Map.of("oracul.news.article-fetch-timeout", "PT3S"), f ->
                assertThat(f.fetch("http://127.0.0.1:" + port + "/").outcome()).as("no list property at all")
                    .isEqualTo("REFUSED_ADDRESS"));
        } finally {
            wide.stop(0);
        }
    }

    // @trace FR-56
    @Test
    void theSpringConstructorDefaultsAre2MbAndFiveRedirectsAndTheProperties() throws Exception {
        Srv a = new Srv();
        SafeFetcherSupport.withSpring(Map.of("oracul.news.fetch.allowed-private-hosts", "127.0.0.1"), f -> {
            Res big = f.fetch("http://127.0.0.1:" + a.port() + "/body/" + (2 * MB + 1));
            assertThat(big.bodyLength()).as("default oracul.news.article-max-bytes is 2097152").isEqualTo(2 * MB);
            assertThat(big.truncated()).isTrue();
            assertThat(f.fetch("http://127.0.0.1:" + a.port() + "/chain/5").outcome()).as("default 5 redirects").isEqualTo("OK");
            assertThat(f.fetch("http://127.0.0.1:" + a.port() + "/chain/6").outcome()).isEqualTo("TOO_MANY_REDIRECTS");
        });
        SafeFetcherSupport.withSpring(Map.of(
            "oracul.news.fetch.allowed-private-hosts", "127.0.0.1",
            "oracul.news.article-max-bytes", "1000",
            "oracul.news.article-max-redirects", "1"), f -> {
            assertThat(f.fetch("http://127.0.0.1:" + a.port() + "/body/5000").bodyLength()).isEqualTo(1000);
            assertThat(f.fetch("http://127.0.0.1:" + a.port() + "/chain/1").outcome()).isEqualTo("OK");
            assertThat(f.fetch("http://127.0.0.1:" + a.port() + "/chain/2").outcome()).isEqualTo("TOO_MANY_REDIRECTS");
        });
    }

    // ---------------------------------------------------------------- (i) pinning

    // @trace FR-56
    @Test
    void theConnectionGoesToTheCheckedAddressWithOneLookupPerHopAndTheHostHeaderOfTheHop() throws Exception {
        Srv s = new Srv();
        Resolver r = new Resolver().map("pinned.test", "127.0.0.1");
        Fx f = fetcher(r, Set.of("pinned.test"), T5, 2 * MB, 5);
        Res one = f.fetch("http://pinned.test:" + s.port() + "/end");
        assertThat(one.outcome()).isEqualTo("OK");
        assertThat(s.paths).containsExactly("/end");
        assertThat(s.headers.get(0)).containsEntry("host", "pinned.test:" + s.port());
        assertThat(s.headers.get(0).get("accept")).isEqualTo("text/html,application/xhtml+xml");
        assertThat(r.calls).as("the resolver is called exactly once per hop").containsExactly("pinned.test");

        r.calls.clear();
        Res two = f.fetch("http://pinned.test:" + s.port() + "/chain/1");
        assertThat(two.outcome()).isEqualTo("OK");
        assertThat(two.redirects()).isEqualTo(1);
        assertThat(r.calls).as("one lookup for each of the two hops, none for the connection").hasSize(2);
        assertThat(s.headers).allSatisfy(h -> assertThat(h).containsEntry("host", "pinned.test:" + s.port()));
    }

    // ---------------------------------------------------------------- (j) log

    private List<ILoggingEvent> warnings(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger("com.oracul.app.research.SafeFetcher");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
        } finally {
            logger.detachAppender(appender);
        }
        return appender.list.stream().filter(e -> e.getLevel() == Level.WARN).toList();
    }

    // @trace FR-56
    @Test
    void aRefusedAddressLogsOneWarnWithTheHostAndNoQueryString() {
        Fx f = fetcher(blockedResolver(), Set.of(), T5, 2 * MB, 5);
        List<ILoggingEvent> logged = warnings(() ->
            assertThat(f.fetch("http://10.0.0.5/secret/path?token=abc123").outcome()).isEqualTo("REFUSED_ADDRESS"));
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getFormattedMessage()).isEqualTo("article fetch refused: blocked-address host=10.0.0.5");
        assertThat(logged.get(0).getFormattedMessage()).doesNotContain("token").doesNotContain("abc123").doesNotContain("?");
        List<ILoggingEvent> byName = warnings(() ->
            assertThat(f.fetch("http://internal.test/x?apikey=zzz").outcome()).isEqualTo("REFUSED_ADDRESS"));
        assertThat(byName).hasSize(1);
        assertThat(byName.get(0).getFormattedMessage()).isEqualTo("article fetch refused: blocked-address host=internal.test");
    }

    // @trace FR-56
    @Test
    void aRefusedSchemeLogsOneWarnWithTheSchemeAndNoQueryString() {
        Fx f = fetcher(blockedResolver(), Set.of(), T5, 2 * MB, 5);
        List<ILoggingEvent> logged = warnings(() ->
            assertThat(f.fetch("ftp://files.example/pub?token=abc123").outcome()).isEqualTo("REFUSED_SCHEME"));
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getFormattedMessage()).isEqualTo("article fetch refused: scheme scheme=ftp");
        assertThat(logged.get(0).getFormattedMessage()).doesNotContain("token").doesNotContain("?");
    }

    // @trace FR-56
    @Test
    void aRefusedRedirectHopLogsTheHopHost() throws Exception {
        Srv a = new Srv();
        Srv b = new Srv();
        a.otherPort = b.port();
        Fx f = localFetcher(2 * MB, 5, T5);
        List<ILoggingEvent> logged = warnings(() ->
            assertThat(f.fetch(start(a, "/to-b")).outcome()).isEqualTo("REFUSED_ADDRESS"));
        assertThat(logged).hasSize(1);
        assertThat(logged.get(0).getFormattedMessage()).isEqualTo("article fetch refused: blocked-address host=127.0.0.1");
    }

    // ---------------------------------------------------------------- (k) hand-written answers on a raw socket

    /** Raw stub on 127.0.0.1: reads the request head, records it, writes the scripted bytes and closes. */
    private final class Raw {
        final ServerSocket socket;
        final List<String> requests = new CopyOnWriteArrayList<>();
        final byte[] answer;

        Raw(byte[] answer) throws IOException {
            this.answer = answer;
            socket = new ServerSocket(0, 5, InetAddress.getByName("127.0.0.1"));
            Thread t = new Thread(this::serve, "raw-stub");
            t.setDaemon(true);
            t.start();
            rawStubs.add(this);
        }

        Raw(String answer) throws IOException {
            this(answer.getBytes(StandardCharsets.ISO_8859_1));
        }

        int port() {
            return socket.getLocalPort();
        }

        private void serve() {
            while (!socket.isClosed()) {
                try (Socket s = socket.accept()) {
                    s.setSoTimeout(5000);
                    StringBuilder head = new StringBuilder();
                    java.io.InputStream in = s.getInputStream();
                    while (!head.toString().endsWith("\r\n\r\n")) {
                        int c = in.read();
                        if (c < 0) {
                            break;
                        }
                        head.append((char) c);
                    }
                    requests.add(head.toString());
                    OutputStream out = s.getOutputStream();
                    out.write(answer);
                    out.flush();
                } catch (IOException ignored) {
                    // the client went away or the stub was closed
                }
            }
        }

        void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
                // nothing left to release
            }
        }
    }

    private final List<Raw> rawStubs = new ArrayList<>();

    @AfterEach
    void stopRawStubs() {
        rawStubs.forEach(Raw::close);
    }

    private Res fetchRaw(Raw raw, int maxBytes) {
        return localFetcher(maxBytes, 5, Duration.ofSeconds(3)).fetch("http://start.test:" + raw.port() + "/x");
    }

    private Res fetchRaw(String answer, int maxBytes) throws IOException {
        return fetchRaw(new Raw(answer), maxBytes);
    }

    private static String text(Res res) {
        return new String(res.body(), StandardCharsets.ISO_8859_1);
    }

    private static final String OK_HEAD = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n";

    // @trace FR-56
    @Test
    @Timeout(20)
    void aChunkedBodyIsDecodedAcrossChunksAndChunkExtensions() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Transfer-Encoding: chunked\r\n\r\n5;ext=1\r\nhello\r\n"
            + "6\r\n world\r\nA\r\n0123456789\r\n0\r\n\r\n", 1000);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.contentType()).isEqualTo("text/html");
        assertThat(text(res)).isEqualTo("hello world0123456789");
        assertThat(res.truncated()).isFalse();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aChunkedBodyOfExactlyMaxBytesIsNotTruncated() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Transfer-Encoding: Chunked\r\n\r\n5\r\nhello\r\n5\r\nworld\r\n0\r\n\r\n", 10);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("helloworld");
        assertThat(res.truncated()).isFalse();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aChunkedBodyLongerThanMaxBytesIsCutAtMaxBytesAndTruncated() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Transfer-Encoding: chunked\r\n\r\n5\r\nhello\r\n5\r\nworld\r\n5\r\nextra\r\n0\r\n\r\n", 10);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("helloworld");
        assertThat(res.truncated()).isTrue();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aChunkedBodyCutInsideAChunkLimitStillCountsBytesAcrossChunks() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Transfer-Encoding: chunked\r\n\r\nA\r\n0123456789\r\n0\r\n\r\n", 4);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("0123");
        assertThat(res.truncated()).isTrue();
    }

    static Stream<Arguments> brokenChunkedAnswers() {
        String head = OK_HEAD + "Transfer-Encoding: chunked\r\n\r\n";
        return Stream.of(
            Arguments.of("invalid hex size", head + "zz\r\nhello\r\n0\r\n\r\n"),
            Arguments.of("empty size", head + "\r\nhello\r\n0\r\n\r\n"),
            Arguments.of("closed inside a chunk", head + "a\r\nabc"),
            Arguments.of("closed before the size line", head),
            Arguments.of("closed before the chunk end", head + "3\r\nabc"));
    }

    // @trace FR-56
    @ParameterizedTest(name = "chunked: {0} gives FAILED")
    @MethodSource("brokenChunkedAnswers")
    @Timeout(20)
    void aBrokenChunkedBodyFails(String name, String answer) throws Exception {
        Res res = fetchRaw(answer, 1000);
        assertThat(res.outcome()).as(name).isEqualTo("FAILED");
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aBodyWithoutContentLengthRunsUntilTheServerCloses() throws Exception {
        Res res = fetchRaw(OK_HEAD + "\r\nuntil the end", 1000);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(200);
        assertThat(text(res)).isEqualTo("until the end");
        assertThat(res.truncated()).isFalse();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aBodyWithoutContentLengthOfExactlyMaxBytesIsNotTruncated() throws Exception {
        Res res = fetchRaw(OK_HEAD + "\r\n0123456789", 10);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("0123456789");
        assertThat(res.truncated()).isFalse();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aBodyWithoutContentLengthLongerThanMaxBytesIsCutAndTruncated() throws Exception {
        Res res = fetchRaw(OK_HEAD + "\r\n0123456789ABCDEFGHIJKLMNO", 10);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("0123456789");
        assertThat(res.truncated()).isTrue();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aContentLengthLargerThanTheBytesSentFails() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Content-Length: 100\r\n\r\nonly ten b", 1000);
        assertThat(res.outcome()).isEqualTo("FAILED");
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aContentLengthWithMatchingBytesIsReadInFull() throws Exception {
        Res res = fetchRaw(OK_HEAD + "Content-Length: 5\r\n\r\nhello", 1000);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("hello");
        assertThat(res.truncated()).isFalse();
    }

    static Stream<Arguments> malformedHeads() {
        return Stream.of(
            Arguments.of("non-numeric status code", "HTTP/1.1 abc OK\r\n\r\n"),
            Arguments.of("status line without code", "HTTP/1.1\r\n\r\n"),
            Arguments.of("not an HTTP version", "ICY 200 OK\r\n\r\n"),
            Arguments.of("non-numeric Content-Length", OK_HEAD + "Content-Length: x\r\n\r\nbody"),
            Arguments.of("closed before the blank line", OK_HEAD + "X-Half: 1\r\n"),
            Arguments.of("closed inside the status line", "HTTP/1.1 20"),
            Arguments.of("header line longer than 64 KB", OK_HEAD + "X-Big: " + "a".repeat(70_000) + "\r\n\r\nbody"),
            Arguments.of("status line longer than 64 KB", "HTTP/1.1 200 " + "a".repeat(70_000) + "\r\n\r\nbody"));
    }

    // @trace FR-56
    @ParameterizedTest(name = "malformed head: {0} gives FAILED")
    @MethodSource("malformedHeads")
    @Timeout(20)
    void aMalformedAnswerHeadFailsAndNeverThrows(String name, String answer) throws Exception {
        Res res = fetchRaw(answer, 1000);
        assertThat(res.outcome()).as(name).isEqualTo("FAILED");
        assertThat(res.body() == null || res.body().length == 0).as("no body for " + name).isTrue();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aHeaderLineWithoutAColonIsSkipped() throws Exception {
        Res res = fetchRaw(OK_HEAD + "this line has no colon\r\n: no name\r\nContent-Length: 2\r\n\r\nhi", 1000);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(200);
        assertThat(res.contentType()).isEqualTo("text/html");
        assertThat(text(res)).isEqualTo("hi");
    }

    static IntStream bodylessStatuses() {
        return IntStream.of(100, 101, 102, 199, 204, 304);
    }

    // @trace FR-56
    @ParameterizedTest(name = "status {0} has an empty body")
    @MethodSource("bodylessStatuses")
    @Timeout(20)
    void bodylessStatusesAreOkWithAnEmptyBody(int status) throws Exception {
        Res res = fetchRaw("HTTP/1.1 " + status + " X\r\nContent-Length: 4\r\n\r\nnope", 1000);
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(res.status()).isEqualTo(status);
        assertThat(res.body()).isEmpty();
        assertThat(res.truncated()).isFalse();
        assertThat(res.redirects()).isZero();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aResolverAnswerWithoutAddressesFailsWithoutAConnection() throws Exception {
        Raw raw = new Raw("HTTP/1.1 200 OK\r\n\r\n");
        Resolver r = new Resolver().map("empty.test");
        Fx f = fetcher(r, Set.of("empty.test"), Duration.ofSeconds(2), 1000, 5);
        Res res = f.fetch("http://empty.test:" + raw.port() + "/x");
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(r.calls).containsExactly("empty.test");
        assertThat(raw.requests).as("no connection was made").isEmpty();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aUrlWithoutAHostFailsWithoutAConnection() throws Exception {
        Raw raw = new Raw("HTTP/1.1 200 OK\r\n\r\n");
        Resolver r = new Resolver();
        Fx f = fetcher(r, Set.of(), Duration.ofSeconds(2), 1000, 5);
        for (String url : List.of("http:///x", "https:///x", "http:/x", "http:x")) {
            assertThat(f.fetch(url).outcome()).as(url).isEqualTo("FAILED");
        }
        assertThat(r.calls).isEmpty();
        assertThat(raw.requests).isEmpty();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void anEmptyPathWithAQueryIsRequestedAsSlashQueryWithTheHostAndPort() throws Exception {
        Raw raw = new Raw(OK_HEAD + "Content-Length: 2\r\n\r\nok");
        Res res = localFetcher(1000, 5, Duration.ofSeconds(3)).fetch("http://start.test:" + raw.port() + "?q=1&r=%20x");
        assertThat(res.outcome()).isEqualTo("OK");
        assertThat(text(res)).isEqualTo("ok");
        assertThat(raw.requests).hasSize(1);
        String head = raw.requests.get(0);
        assertThat(head).startsWith("GET /?q=1&r=%20x HTTP/1.1\r\n");
        assertThat(head).contains("\r\nHost: start.test:" + raw.port() + "\r\n");
        assertThat(head).contains("\r\nConnection: close\r\n").contains("\r\nAccept-Encoding: identity\r\n");
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void anErrorInsideTheFetchIsAnsweredWithFailedNotThrown() throws Exception {
        Raw raw = new Raw("HTTP/1.1 200 OK\r\n\r\n");
        Resolver r = new Resolver().map("start.test", "127.0.0.1");
        r.failWith = new LinkageError("resolver blew up");
        Fx f = fetcher(r, Set.of("start.test"), Duration.ofSeconds(2), 1000, 5);
        Res res = f.fetch("http://start.test:" + raw.port() + "/x");
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(res.body() == null || res.body().length == 0).isTrue();
        assertThat(raw.requests).as("no connection was made").isEmpty();
    }

    // @trace FR-56
    @Test
    @Timeout(20)
    void aFetchThatTimedOutDuringTheLookupNeverConnectsAfterwards() throws Exception {
        Raw raw = new Raw(OK_HEAD + "Content-Length: 2\r\n\r\nok");
        Resolver r = new Resolver().map("slow.test", "127.0.0.1");
        r.uninterruptibleDelayMs = 1000; // the lookup answers only after the 200 ms fetch timeout
        Fx f = fetcher(r, Set.of("slow.test"), Duration.ofMillis(200), 1000, 5);
        long t0 = System.nanoTime();
        Res res = f.fetch("http://slow.test:" + raw.port() + "/x");
        assertThat(res.outcome()).isEqualTo("FAILED");
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).as("the caller is released at the timeout")
            .isLessThan(Duration.ofMillis(800));
        sleep(1300); // the abandoned lookup finishes; the cancelled fetch must not open a connection
        assertThat(raw.requests).as("no request after the timeout").isEmpty();
    }
}
