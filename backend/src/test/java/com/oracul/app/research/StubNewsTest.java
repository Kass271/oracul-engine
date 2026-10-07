package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-61 "E2E stub answers like real Google", in-process half ({@code StubNews}, wildcard-search.md "Slice 08_article-text - FR-61 delta"):
 * the {@code q} classes of the pure matcher, the shape of the feed, the Google page, the decode answer and the publisher page, and the
 * control modes. The same classes are walked against the Docker stub by {@code e2e/tests/stub-google.spec.ts}.
 */
// @trace FR-61
@Timeout(60)
class StubNewsTest {

    private final StubNews news = StubNews.INSTANCE;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).connectTimeout(Duration.ofSeconds(5)).build();

    @BeforeEach
    void reset() {
        news.reset();
        news.responder = StubNews.googleLike();
    }

    private HttpResponse<String> get(String pathAndQuery) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(news.baseUrl() + pathAndQuery)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> search(String q) throws Exception {
        return get("/rss/search?hl=en-US&gl=US&ceid=US:en&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8));
    }

    private static int items(String rss) {
        Matcher m = Pattern.compile("<item>").matcher(rss);
        int n = 0;
        while (m.find()) n++;
        return n;
    }

    // ---- the q classes ----------------------------------------------------------------------------------------------------

    static Stream<Arguments> qClasses() {
        return Stream.of(
            Arguments.of("vaccines when:90d", 5),
            Arguments.of("energy crisis supply shortage warnings when:90d", 5),
            Arguments.of("\"mRNA vaccine approval\" when:90d", 5),
            Arguments.of("fusion OR fission when:90d", 10),
            Arguments.of("(fusion OR fission) when:90d", 0),
            Arguments.of("(energy crisis OR geopolitical fragmentation) when:90d", 0),
            Arguments.of("\"mRNA vaccine\" OR \"fusion plant\" when:90d", 0),
            Arguments.of("energy crisis OR fusion when:90d", 0),
            Arguments.of("vaccines", 5));
    }

    @ParameterizedTest(name = "[{0}] -> {1} items")
    @MethodSource("qClasses")
    void theMatcherAndTheFeedAgreeOnEveryQClass(String q, int expected) throws Exception {
        assertThat(StubNews.answersItems(q)).as("answersItems").isEqualTo(expected > 0);
        HttpResponse<String> r = search(q);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(items(r.body())).as("items in the feed").isEqualTo(expected);
    }

    @Test
    void theFeedItemsHaveTheShapeOfRealGoogle() throws Exception {
        String body = search("vaccines when:90d").body();
        assertThat(body).contains("Shared stub article - Reuters");
        assertThat(body).contains(news.baseUrl() + "/rss/articles/shared?utm_source=1");
        assertThat(body).contains("<source url=\"https://www.reuters.com\">Reuters</source>");
        assertThat(body).contains("<description>").contains("&lt;a href=").contains("&lt;font color=");
        Matcher m = Pattern.compile("<link>" + Pattern.quote(news.baseUrl()) + "/rss/articles/([0-9a-f]{8})-([2-5])</link>").matcher(body);
        int n = 0;
        while (m.find()) n++;
        assertThat(n).as("items 2..5 link to <key>-<a>").isEqualTo(4);
        assertThat(news.articleText).as("every <key>-<a> remembers its element").hasSize(4).containsValue("vaccines");
    }

    // ---- the Google page, the decode answer and the publisher page ------------------------------------------------------------

    @Test
    void theGooglePageIsA302ToTheSamePathWithHlThenAPageWithTheThreeAttributes() throws Exception {
        HttpResponse<String> redirect = get("/rss/articles/abc?oc=5");
        assertThat(redirect.statusCode()).isEqualTo(302);
        assertThat(redirect.headers().firstValue("Location")).contains("/rss/articles/abc?oc=5&hl=en-US&gl=US&ceid=US:en");
        HttpResponse<String> page = get("/rss/articles/abc?oc=5&hl=en-US&gl=US&ceid=US:en");
        assertThat(page.statusCode()).isEqualTo(200);
        assertThat(page.headers().firstValue("Content-Type").orElse("")).startsWith("text/html");
        assertThat(page.body()).contains("data-n-a-id=\"abc\"").contains("data-n-a-ts=\"1759737600\"").contains("data-n-a-sg=\"sig-abc\"")
            .contains("og:site_name\" content=\"Google News\"");
        assertThat(get("/rss/articles/abc").headers().firstValue("Location")).contains("/rss/articles/abc?hl=en-US&gl=US&ceid=US:en");
        assertThat(news.googlePageRequests).extracting(StubNews.GooglePageHit::redirect).containsExactly(true, false, true);
    }

    private HttpResponse<String> decode(String id, String sg) throws Exception {
        String inner = "[\"garturlreq\",[[\"X\"]],\"" + id + "\",1759737600,\"" + sg + "\"]";
        String value = "[[[\"Fbv4je\",\"" + inner.replace("\\", "\\\\").replace("\"", "\\\"") + "\",null,\"generic\"]]]";
        return client.send(HttpRequest.newBuilder(URI.create(news.baseUrl() + "/_/DotsSplashUi/data/batchexecute"))
            .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
            .POST(HttpRequest.BodyPublishers.ofString("f.req=" + URLEncoder.encode(value, StandardCharsets.UTF_8))).build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void theDecodeAnswersTheStructuredPublisherUrlOr400() throws Exception {
        HttpResponse<String> ok = decode("abc", "sig-abc");
        assertThat(ok.statusCode()).isEqualTo(200);
        assertThat(ok.body()).startsWith(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\",\\\"" + news.baseUrl() + "/articles/abc\\\",1]\"");
        assertThat(decode("abc", "sig-x").statusCode()).isEqualTo(400);
        assertThat(news.decodeRequests).extracting(StubNews.Decode::status).containsExactly(200, 400);
        assertThat(news.decodeRequests.get(0).id()).isEqualTo("abc");
    }

    @Test
    void thePublisherPageHasNavScriptStyleAndSixParagraphsAndCarriesTheMatchTextOnlyOutsideTheArticleToo() throws Exception {
        news.articleText.put("abc", "W01 stub query 1");
        String body = get("/articles/abc").body();
        assertThat(body).contains("<nav><p>NAVIGATION TEXT home world business W01 stub query 1</p></nav>").contains("STYLE TEXT W01 stub query 1")
            .contains("SCRIPT TEXT W01 stub query 1").contains("<footer><p>FOOTER TEXT W01 stub query 1</p></footer>");
        assertThat(body).contains("<p>" + StubNews.P1 + "</p>").contains("<p>" + StubNews.p2("W01 stub query 1") + "</p>")
            .contains("<p>" + StubNews.p4("W01 stub query 1") + "</p>").contains("<p>" + StubNews.P6 + "</p>");
        String generic = get("/articles/unknown-name").body();
        assertThat(generic).contains(StubNews.P2_GENERIC).contains(StubNews.P4_GENERIC).doesNotContain("W01 stub query 1");
        assertThat(news.articleRequests).containsExactly("abc", "unknown-name");
    }

    @Test
    void thePublisherParagraphsAreTheLengthsOfTheSpec() {
        assertThat(StubNews.P1).hasSize(99);
        assertThat(StubNews.P2_GENERIC).hasSize(94);
        assertThat(StubNews.P3).hasSize(95);
        assertThat(StubNews.P4_GENERIC).hasSize(95);
        assertThat(StubNews.P5).hasSize(100);
        assertThat(StubNews.P6).hasSize(98);
    }

    // ---- control modes -------------------------------------------------------------------------------------------------------------

    static Stream<Arguments> modes() {
        return Stream.of(
            Arguments.of("empty", "vaccines when:90d", 200, 0),
            Arguments.of("down", "vaccines when:90d", 503, -1),
            Arguments.of("malformed", "vaccines when:90d", 200, -2),
            Arguments.of("ok", "vaccines when:90d", 200, 5));
    }

    @ParameterizedTest(name = "mode {0}: status {2}")
    @MethodSource("modes")
    void everySearchModeProducesItsEffect(String mode, String q, int status, int items) throws Exception {
        news.mode(mode);
        HttpResponse<String> r = search(q);
        assertThat(r.statusCode()).isEqualTo(status);
        if (items >= 0) assertThat(items(r.body())).isEqualTo(items);
        if (items == -2) assertThat(r.body()).contains("<item><title>broken");
    }

    @Test
    void emptyForAnswersZeroItemsOnlyForTheTerm() throws Exception {
        news.mode("empty-for", "W02");
        assertThat(items(search("W02 stub query 1 when:90d").body())).isZero();
        assertThat(items(search("W01 stub query 1 when:90d").body())).isEqualTo(5);
    }

    @Test
    void rateLimitedOnceAnswers429OnceThenItems() throws Exception {
        news.mode("rate-limited-once");
        assertThat(search("vaccines").statusCode()).isEqualTo(429);
        assertThat(items(search("vaccines").body())).isEqualTo(5);
    }

    @Test
    void slowAnswersAfterTheGivenMilliseconds() throws Exception {
        news.mode("slow", 500L);
        long t0 = System.nanoTime();
        assertThat(search("vaccines").statusCode()).isEqualTo(200);
        assertThat((System.nanoTime() - t0) / 1_000_000).isGreaterThanOrEqualTo(450);
    }

    @Test
    void decodeAndPublisherModes() throws Exception {
        news.mode("decode-fail");
        assertThat(decode("abc", "sig-abc").statusCode()).isEqualTo(500);
        news.mode("decode-google-host");
        assertThat(decode("abc", "sig-abc").body()).contains("https://news.google.com/rss/articles/abc");
        news.mode("publisher-fail");
        assertThat(get("/articles/abc").statusCode()).isEqualTo(503);
        news.mode("ok");
        assertThat(get("/articles/abc").statusCode()).isEqualTo(200);
        assertThat(decode("abc", "sig-abc").statusCode()).isEqualTo(200);
    }

    @ParameterizedTest(name = "unknown mode [{0}]")
    @ValueSource(strings = {"nope", "", "OK"})
    void anUnknownModeIsRejected(String mode) {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> news.mode(mode));
    }

    @Test
    void resetReturnsToOkAndClearsEveryRecord() throws Exception {
        news.mode("decode-fail");
        decode("abc", "sig-abc");
        get("/articles/abc");
        get("/rss/articles/abc");
        news.reset();
        assertThat(news.decodeRequests).isEmpty();
        assertThat(news.articleRequests).isEmpty();
        assertThat(news.googlePageRequests).isEmpty();
        assertThat(news.decodeMode).isEqualTo("ok");
        assertThat(news.retrievalMaxOpen()).isZero();
        assertThat(search("vaccines").statusCode()).as("the default responder answers an empty feed").isEqualTo(200);
        assertThat(items(search("vaccines").body())).isZero();
    }
}
