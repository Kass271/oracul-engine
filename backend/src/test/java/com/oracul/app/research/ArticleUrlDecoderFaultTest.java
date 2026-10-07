package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * FR-54 "Publisher article retrieval", {@code ArticleUrlDecoder} edge classes (article-retrieval.md "Component ArticleUrlDecoder"):
 * the {@code f.req} escaping of every control character, answers that hold no usable candidate, a base URL that is no URL, and an
 * answer that comes too late, is cut off, or is awaited by an interrupted thread. A loopback {@code HttpServer} plays the endpoint.
 */
// @trace FR-54
@Timeout(60)
class ArticleUrlDecoderFaultTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String BASE = "https://news.google.com";
    private static final ArticleUrlDecoder.Attributes ATTRS = new ArticleUrlDecoder.Attributes("CBMiX", 1759737600L, "sig-X");

    private HttpServer server;
    private final CountDownLatch release = new CountDownLatch(1);
    private final CountDownLatch arrived = new CountDownLatch(1);

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
    }

    @AfterEach
    void stop() {
        release.countDown();
        server.stop(0);
    }

    private ArticleUrlDecoder decoder() {
        server.start();
        return new ArticleUrlDecoder("http://127.0.0.1:" + server.getAddress().getPort() + "/decode", BASE);
    }

    private void handle(HttpHandler handler) {
        server.createContext("/decode", handler);
    }

    private static void answer(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(bytes);
        }
    }

    private static String structured(String url) {
        String inner = "[\"garturlres\"," + JSON.writeValueAsString(url) + "]";
        return ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\"," + JSON.writeValueAsString(inner) + ",null,null,null,\"generic\"]]";
    }

    // ---- f.req escaping ------------------------------------------------------------------------------------------------

    static Stream<Integer> escapedCharacters() {
        // every control character, the two JSON metacharacters, DEL, and a non-ASCII letter
        return Stream.concat(IntStream.rangeClosed(0, 0x20).boxed(), Stream.of((int) '"', (int) '\\', 0x7f, 0xe9));
    }

    @ParameterizedTest(name = "U+{0} in an attribute value")
    @MethodSource("escapedCharacters")
    void everyCharacterOfAnAttributeValueSurvivesTheDoubleJsonEncodingOfFReq(int ch) {
        String id = "a" + (char) ch + "b";
        String sg = "s" + (char) ch + (char) ch + "g";
        String fReq = ArticleUrlDecoder.fReq(new ArticleUrlDecoder.Attributes(id, 42L, sg));
        assertThat(fReq.chars()).as("no raw control character in the f.req value").noneMatch(c -> c < 0x20);
        JsonNode outer = JSON.readTree(fReq);
        assertThat(outer.get(0).get(0).get(0).asString()).isEqualTo("Fbv4je");
        assertThat(outer.get(0).get(0).get(3).asString()).isEqualTo("generic");
        JsonNode inner = JSON.readTree(outer.get(0).get(0).get(1).asString());
        assertThat(inner.get(0).asString()).isEqualTo("garturlreq");
        assertThat(inner.get(2).asString()).isEqualTo(id);
        assertThat(inner.get(3).asLong()).isEqualTo(42L);
        assertThat(inner.get(4).asString()).isEqualTo(sg);
    }

    @Test
    void aCarriageReturnAndAControlCharacterAreEscapedAsJsonEscapes() {
        String fReq = ArticleUrlDecoder.fReq(new ArticleUrlDecoder.Attributes("a\rb\u0001c\u001fd", 1L, "s"));
        // inside the inner string the escapes are \r and \u0001 / \u001f; the outer string escapes each backslash again
        assertThat(fReq).contains("a\\\\rb\\\\u0001c\\\\u001fd");
    }

    // ---- answers without a usable candidate -------------------------------------------------------------------------------

    @Test
    void aMissingAnswerHasNoPublisherUrl() {
        assertThat(ArticleUrlDecoder.publisherUrl(null, BASE)).isEmpty();
    }

    @ParameterizedTest(name = "candidate {0}")
    @ValueSource(strings = {"http://exa[mple.com/x", "http://bad host/x", "http://exa mple.com/", "https://pub.example/a|b"})
    void aStructuredCandidateThatIsNoValidUriIsNoUrl(String candidate) {
        assertThat(ArticleUrlDecoder.publisherUrl(structured(candidate), BASE)).isEmpty();
    }

    @ParameterizedTest(name = "raw candidate {0}")
    @ValueSource(strings = {"http://exa[mple.com/x", "https://pub.example/a|b", "https://pub.example/a{b}"})
    void aRawCandidateThatIsNoValidUriIsNoUrl(String candidate) {
        assertThat(ArticleUrlDecoder.publisherUrl("x " + candidate + " y", BASE)).isEmpty();
    }

    @Test
    void anAnswerThatIsNotValidJsonFallsBackToTheFirstUrlInTheBody() {
        String body = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\" see https://pub.example.com/a?b=1 ,";
        assertThat(ArticleUrlDecoder.publisherUrl(body, BASE)).contains("https://pub.example.com/a?b=1");
    }

    @Test
    void anAnswerWhoseInnerValueIsNotValidJsonFallsBackToTheFirstUrlInTheBody() {
        String body = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"not json https://pub.example.com/b\",null]]";
        assertThat(ArticleUrlDecoder.publisherUrl(body, BASE)).contains("https://pub.example.com/b");
    }

    @Test
    void anAnswerThatIsNotValidJsonAndHoldsNoUrlHasNoPublisherUrl() {
        assertThat(ArticleUrlDecoder.publisherUrl(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\" nothing here", BASE)).isEmpty();
        assertThat(ArticleUrlDecoder.publisherUrl("[", BASE)).isEmpty();
        assertThat(ArticleUrlDecoder.publisherUrl("]", BASE)).isEmpty();
    }

    @ParameterizedTest(name = "answer {0}")
    @ValueSource(strings = {"[]", "[1,2,3]", "[[\"wrb.fr\"]]", "[[\"wrb.fr\",\"Other\",\"x\"]]", "[[\"wrb.fr\",\"Fbv4je\",5]]",
        "[[\"wrb.fr\",\"Fbv4je\",\"[]\"]]", "[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",null]\"]]"})
    void aWellFormedAnswerWithoutAUrlHasNoPublisherUrl(String body) {
        assertThat(ArticleUrlDecoder.publisherUrl(")]}'\n\n" + body, BASE)).isEmpty();
    }

    // ---- Google host with a base that is no URL ------------------------------------------------------------------------------

    @ParameterizedTest(name = "base {0}")
    @ValueSource(strings = {"ht tp://bad base", "http://exa mple.com", "%%", ""})
    void aBaseUrlThatCannotBeParsedMakesNoHostAGoogleBaseHost(String base) {
        assertThat(ArticleUrlDecoder.isGoogleHost(URI.create("https://pub.example.com/rss/articles/x"), base)).isFalse();
        assertThat(ArticleUrlDecoder.publisherUrl(structured("https://pub.example.com/rss/articles/x"), base))
            .contains("https://pub.example.com/rss/articles/x");
        assertThat(ArticleUrlDecoder.isGoogleHost(URI.create("https://news.google.com/rss/articles/x"), base)).as("google.com is always Google").isTrue();
    }

    @Test
    void aNullBaseUrlMakesNoHostAGoogleBaseHost() {
        assertThat(ArticleUrlDecoder.isGoogleHost(URI.create("https://pub.example.com/rss/articles/x"), null)).isFalse();
    }

    @Test
    void theArticleLinkOfAWellFormedBaseIsAGoogleHostButItsOtherPathsAreNot() {
        String base = "http://127.0.0.1:4010";
        assertThat(ArticleUrlDecoder.isGoogleHost(URI.create("http://127.0.0.1:4010/rss/articles/x"), base)).isTrue();
        assertThat(ArticleUrlDecoder.isGoogleHost(URI.create("http://127.0.0.1:4010/articles/x"), base)).isFalse();
    }

    // ---- the POST against a loopback endpoint ------------------------------------------------------------------------------------

    @Test
    void aStructuredAnswerIsTheDecodedUrl() {
        handle(ex -> answer(ex, 200, structured("https://pub.example.com/a")));
        ArticleUrlDecoder.Result r = decoder().decode(ATTRS, Duration.ofSeconds(5));
        assertThat(r).isEqualTo(new ArticleUrlDecoder.Result("https://pub.example.com/a", null));
    }

    @Test
    void anAnswerWithoutAnyUrlIsNoUrl() {
        handle(ex -> answer(ex, 200, ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",null]]"));
        assertThat(decoder().decode(ATTRS, Duration.ofSeconds(5))).isEqualTo(new ArticleUrlDecoder.Result(null, "no-url"));
    }

    @Test
    void anAnswerWhoseOnlyUrlIsAGoogleHostIsGoogleHost() {
        handle(ex -> answer(ex, 200, structured("https://www.google.com/url?q=x")));
        assertThat(decoder().decode(ATTRS, Duration.ofSeconds(5))).isEqualTo(new ArticleUrlDecoder.Result(null, "google-host"));
    }

    @Test
    void anAnswerWhoseCandidateIsNoUriHasNoUrl() {
        handle(ex -> answer(ex, 200, "x http://exa[mple.com/x y"));
        ArticleUrlDecoder.Result r = decoder().decode(ATTRS, Duration.ofSeconds(5));
        assertThat(r.url()).isNull();
        assertThat(r.reason()).isNotNull();
    }

    @Test
    void anAnswerThatArrivesAfterTheTimeoutIsATimeout() {
        handle(ex -> {
            arrived.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            answer(ex, 200, structured("https://pub.example.com/a"));
        });
        long t0 = System.nanoTime();
        ArticleUrlDecoder.Result r = decoder().decode(ATTRS, Duration.ofMillis(400));
        assertThat(r).isEqualTo(new ArticleUrlDecoder.Result(null, "timeout"));
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).as("returned at the timeout, not when the endpoint answers").isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void anAnswerBodyThatStallsAfterItsHeadersIsATimeout() {
        handle(ex -> {
            ex.sendResponseHeaders(200, 0); // chunked
            OutputStream out = ex.getResponseBody();
            out.write("[[\"wrb.fr\"".getBytes(StandardCharsets.UTF_8));
            out.flush();
            arrived.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                out.close();
            } catch (IOException ignored) {
                // the client has gone
            }
        });
        long t0 = System.nanoTime();
        ArticleUrlDecoder.Result r = decoder().decode(ATTRS, Duration.ofMillis(500));
        assertThat(r).isEqualTo(new ArticleUrlDecoder.Result(null, "timeout"));
        assertThat(Duration.ofNanos(System.nanoTime() - t0)).isLessThan(Duration.ofSeconds(10));
    }

    @Test
    void aConnectionThatIsCutOffInTheMiddleOfTheAnswerIsAnError() {
        handle(ex -> {
            ex.sendResponseHeaders(200, 100_000); // promises 100 kB
            OutputStream out = ex.getResponseBody();
            out.write(new byte[10]);
            out.flush();
            ex.close(); // fewer bytes than promised: the connection ends
        });
        ArticleUrlDecoder.Result r = decoder().decode(ATTRS, Duration.ofSeconds(10));
        assertThat(r.url()).isNull();
        assertThat(r.reason()).isEqualTo("error");
    }

    @Test
    void anInterruptWhileWaitingForTheAnswerIsAnErrorAndKeepsTheInterruptFlag() throws Exception {
        handle(ex -> {
            arrived.countDown();
            try {
                release.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            answer(ex, 200, structured("https://pub.example.com/a"));
        });
        ArticleUrlDecoder decoder = decoder();
        AtomicReference<ArticleUrlDecoder.Result> result = new AtomicReference<>();
        AtomicBoolean flag = new AtomicBoolean();
        Thread caller = new Thread(() -> {
            result.set(decoder.decode(ATTRS, Duration.ofSeconds(30)));
            flag.set(Thread.currentThread().isInterrupted());
        });
        caller.start();
        assertThat(arrived.await(15, TimeUnit.SECONDS)).as("the request reached the endpoint").isTrue();
        caller.interrupt();
        caller.join(10_000);
        assertThat(caller.isAlive()).as("decode returned after the interrupt").isFalse();
        assertThat(result.get()).isEqualTo(new ArticleUrlDecoder.Result(null, "error"));
        assertThat(flag).as("the interrupt flag is kept").isTrue();
    }

    @Test
    void aNonSuccessAnswerIsItsStatus() {
        handle(ex -> answer(ex, 500, "no"));
        assertThat(decoder().decode(ATTRS, Duration.ofSeconds(5))).isEqualTo(new ArticleUrlDecoder.Result(null, "status=500"));
    }
}
