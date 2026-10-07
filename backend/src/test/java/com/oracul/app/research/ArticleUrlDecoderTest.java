package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.ArticleApi.Attrs;
import com.oracul.app.research.ArticleApi.Outcome;
import com.oracul.app.research.SafeFetcherSupport.Fx;
import com.oracul.app.research.SafeFetcherSupport.Resolver;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * FR-54 "Publisher article retrieval", the one class that knows the undocumented Google format ({@code ArticleUrlDecoder}, article-retrieval.md
 * slice 08 delta): the attribute classes of the Google page, the {@code f.req} value, the decode-answer classes and the POST itself against
 * the in-process stub. The class is reached through {@link ArticleApi} (it does not exist before the slice is built).
 */
// @trace FR-54
@Timeout(60)
class ArticleUrlDecoderTest {

    private final StubNews news = StubNews.INSTANCE;

    @BeforeEach
    void reset() {
        news.reset();
    }

    private String base() {
        return news.baseUrl();
    }

    private static final String FREQ = "[[[\"Fbv4je\",\"[\\\"garturlreq\\\",[[\\\"X\\\",\\\"X\\\",[\\\"X\\\",\\\"X\\\"],null,null,1,1,\\\"US:en\\\",null,1,null,null,"
        + "null,null,null,0,1],\\\"X\\\",\\\"X\\\",1,[1,1,1],1,1,null,0,0,null,0],\\\"%s\\\",%s,\\\"%s\\\"]\",null,\"generic\"]]]";

    // ---- (c) attribute classes ------------------------------------------------------------------------------------------

    private static String div(String attrs) {
        return "<div jscontroller=\"aLI87\"" + attrs + "></div>";
    }

    private static String doc(String inner) {
        return "<!doctype html><html><head><title>Google News</title></head><body><c-wiz>" + inner + "</c-wiz></body></html>";
    }

    static Stream<Arguments> attributeClasses() {
        String id = " data-n-a-id=\"CBMiX\"";
        String ts = " data-n-a-ts=\"1759737600\"";
        String sg = " data-n-a-sg=\"AU_yqLx\"";
        Attrs found = new Attrs("CBMiX", 1759737600L, "AU_yqLx");
        return Stream.of(
            Arguments.of("all three valid", doc(div(id + ts + sg)), found),
            Arguments.of("values are trimmed", doc(div(" data-n-a-id=\"  CBMiX \" data-n-a-ts=\" 1759737600 \" data-n-a-sg=\"\tAU_yqLx\"")), found),
            Arguments.of("18 digits are a valid timestamp", doc(div(id + " data-n-a-ts=\"175973760012345678\"" + sg)),
                new Attrs("CBMiX", 175973760012345678L, "AU_yqLx")),
            Arguments.of("two valid elements: the first", doc(div(id + ts + sg) + div(" data-n-a-id=\"other\" data-n-a-ts=\"1\" data-n-a-sg=\"s2\"")), found),
            Arguments.of("an invalid element before a valid one", doc(div(" data-n-a-id=\"bad\"") + div(id + ts + sg)), found),
            Arguments.of("id missing", doc(div(ts + sg)), null),
            Arguments.of("id blank", doc(div(" data-n-a-id=\"   \"" + ts + sg)), null),
            Arguments.of("ts missing", doc(div(id + sg)), null),
            Arguments.of("ts blank", doc(div(id + " data-n-a-ts=\" \"" + sg)), null),
            Arguments.of("ts with a letter", doc(div(id + " data-n-a-ts=\"17597a\"" + sg)), null),
            Arguments.of("ts negative", doc(div(id + " data-n-a-ts=\"-1\"" + sg)), null),
            Arguments.of("ts of 19 digits", doc(div(id + " data-n-a-ts=\"1759737600123456789\"" + sg)), null),
            Arguments.of("sg missing", doc(div(id + ts)), null),
            Arguments.of("sg blank", doc(div(id + ts + " data-n-a-sg=\"\"")), null),
            Arguments.of("attributes split over two elements", doc(div(id + ts) + div(sg)), null),
            Arguments.of("attributes split over nested elements", doc("<div" + id + ts + "><span" + sg + "></span></div>"), null),
            Arguments.of("inside an HTML comment", doc("<!-- " + div(id + ts + sg) + " -->"), null),
            Arguments.of("inside script text", doc("<script>var t = '" + div(id + ts + sg).replace('"', '\'') + "';</script>"), null),
            Arguments.of("as visible text", doc("<p>" + div(id + ts + sg).replace("<", "&lt;").replace(">", "&gt;") + "</p>"), null),
            Arguments.of("an empty page", "", null));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("attributeClasses")
    void theGooglePageYieldsTheAttributesOnlyWhenAllThreeAreValidOnOneElement(String name, String html, Attrs expected) {
        Optional<Attrs> actual = ArticleApi.attributes(html);
        if (expected == null) assertThat(actual).as(name).isEmpty();
        else assertThat(actual).as(name).contains(expected);
    }

    @Test
    void theStubsGooglePageIsReadAndItsAbsentAttributesAreHonoured() {
        assertThat(ArticleApi.attributes(StubNews.googlePage("abc", "1759737600", "sig-abc"))).contains(new Attrs("abc", 1759737600L, "sig-abc"));
        assertThat(ArticleApi.attributes(StubNews.googlePage(null, "1759737600", "sig-abc"))).isEmpty();
        assertThat(ArticleApi.attributes(StubNews.googlePage("abc", null, "sig-abc"))).isEmpty();
        assertThat(ArticleApi.attributes(StubNews.googlePage("abc", "1759737600", null))).isEmpty();
    }

    // ---- (d) f.req -------------------------------------------------------------------------------------------------------

    @Test
    void fReqIsTheFixedValueWithTheThreeFieldsFilledIn() {
        assertThat(ArticleApi.fReq(new Attrs("CBMiX", 1759737600L, "AU_yqLx"))).isEqualTo(String.format(FREQ, "CBMiX", "1759737600", "AU_yqLx"));
        assertThat(ArticleApi.fReq(new Attrs("x", 1L, "y"))).isEqualTo(String.format(FREQ, "x", "1", "y"));
    }

    @Test
    void anIdWithAQuoteIsEscapedSoTheValueStaysValidJson() {
        String value = ArticleApi.fReq(new Attrs("a\"b", 1759737600L, "s\\g"));
        String inner = JsonPath.read(value, "$[0][0][1]");
        assertThat((String) JsonPath.read(inner, "$[0]")).isEqualTo("garturlreq");
        assertThat((String) JsonPath.read(inner, "$[2]")).isEqualTo("a\"b");
        assertThat(((Number) JsonPath.read(inner, "$[3]")).longValue()).isEqualTo(1759737600L);
        assertThat((String) JsonPath.read(inner, "$[4]")).isEqualTo("s\\g");
        assertThat(inner).contains("a\\\"b");
    }

    // ---- (b) decode-answer classes -----------------------------------------------------------------------------------------

    private static String jsonString(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    /** The structured answer of batchexecute for an inner JSON text. */
    private static String structured(String innerJson) {
        return ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\"," + jsonString(innerJson) + ",null,null,null,\"generic\"]]";
    }

    private static String structuredUrl(String url) {
        return structured("[\"garturlres\"," + jsonString(url) + ",1]");
    }

    static Stream<Arguments> acceptedUrls() {
        return Stream.of(
            Arguments.of("https://notgoogle.com/x", "https://notgoogle.com/x"),
            Arguments.of("https://google.com.evil.org/x", "https://google.com.evil.org/x"),
            Arguments.of("https://www.reuters.com/world/story-1/?a=b&c=d", "https://www.reuters.com/world/story-1/?a=b&c=d"),
            Arguments.of("http://publisher.example/path", "http://publisher.example/path"));
    }

    @ParameterizedTest(name = "accepted, structured and raw: {0}")
    @MethodSource("acceptedUrls")
    void aPublisherUrlThatIsNotOnAGoogleHostIsAccepted(String url, String expected) {
        assertThat(ArticleApi.publisherUrl(structuredUrl(url), "https://news.google.com")).as("structured").contains(expected);
        assertThat(ArticleApi.publisherUrl("garbage " + url + " tail", "https://news.google.com")).as("raw").contains(expected);
    }

    @Test
    void theStubsBaseIsNotAGoogleHostForAnArticlePathButIsForAnArticleLinkOfTheBase() {
        String b = base();
        assertThat(ArticleApi.publisherUrl(structuredUrl(b + "/articles/x"), b)).contains(b + "/articles/x");
        assertThat(ArticleApi.publisherUrl(structuredUrl("http://127.0.0.1:" + news.port() + "/articles/x"), b)).isPresent();
        assertThat(ArticleApi.publisherUrl(structuredUrl(b + "/rss/articles/x"), b)).as("a Google article link of the configured base").isEmpty();
        assertThat(ArticleApi.publisherUrl("x " + b + "/rss/articles/x y", b)).isEmpty();
    }

    static Stream<String> googleHosts() {
        return Stream.of("https://news.google.com/rss/articles/x", "https://www.google.com/url?q=x", "https://google.com/", "https://consent.google.com/m",
            "https://fonts.gstatic.com/x", "https://lh3.googleusercontent.com/x", "http://google.com/x", "https://gstatic.com/a");
    }

    @ParameterizedTest(name = "Google host rejected: {0}")
    @MethodSource("googleHosts")
    void aUrlOnAGoogleHostIsNeverThePublisherUrl(String url) {
        assertThat(ArticleApi.publisherUrl(structuredUrl(url), "https://news.google.com")).as("structured").isEmpty();
        assertThat(ArticleApi.publisherUrl("x " + url + " y", "https://news.google.com")).as("raw").isEmpty();
    }

    @Test
    void anUnescapedStructuredUrlAndTheOtherAnswerShapes() {
        String escaped = structured("[\"garturlres\",\"https://pub.example/a?x\\u003d1\\u0026y\\u003d2\",1]");
        assertThat(ArticleApi.publisherUrl(escaped, "https://news.google.com")).contains("https://pub.example/a?x=1&y=2");
        assertThat(ArticleApi.publisherUrl(structured("[\"garturlres\",null,1]"), "https://news.google.com")).as("null URL").isEmpty();
        assertThat(ArticleApi.publisherUrl("x http://a.example/1 y https://b.example/2", "https://news.google.com")).as("raw: the first").contains("http://a.example/1");
        assertThat(ArticleApi.publisherUrl(")]}'", "https://news.google.com")).isEmpty();
        assertThat(ArticleApi.publisherUrl("", "https://news.google.com")).isEmpty();
        assertThat(ArticleApi.publisherUrl("x ftp://x/y z", "https://news.google.com")).isEmpty();
        assertThat(ArticleApi.publisherUrl("x file:///etc/passwd z", "https://news.google.com")).isEmpty();
        assertThat(ArticleApi.publisherUrl(structuredUrl("ftp://x/y"), "https://news.google.com")).isEmpty();
        assertThat(ArticleApi.publisherUrl(structuredUrl("/relative/path"), "https://news.google.com")).isEmpty();
    }

    // ---- decode(): the POST ------------------------------------------------------------------------------------------------

    private Object decoder() {
        return ArticleApi.decoder(base() + "/_/DotsSplashUi/data/batchexecute", base());
    }

    private static Attrs attrs(String id) {
        return new Attrs(id, 1759737600L, "sig-" + id);
    }

    @Test
    void theDecodePostCarriesTheFixedFormAndTheUserAgentAndReturnsThePublisherUrl() {
        String[] r = ArticleApi.decode(decoder(), attrs("CBMiX"), Duration.ofSeconds(5));
        assertThat(r[0]).isEqualTo(base() + "/articles/CBMiX");
        assertThat(r[1]).isNull();
        assertThat(news.decodeRequests).hasSize(1);
        StubNews.Decode d = news.decodeRequests.get(0);
        assertThat(d.contentType()).isEqualTo("application/x-www-form-urlencoded;charset=UTF-8");
        assertThat(d.userAgent()).isEqualTo("Mozilla/5.0 (compatible; ORACUL/1.0)");
        assertThat(d.fReq()).isEqualTo(ArticleApi.fReq(attrs("CBMiX")));
        assertThat(d.id()).isEqualTo("CBMiX");
        assertThat(d.ts()).isEqualTo("1759737600");
        assertThat(d.sg()).isEqualTo("sig-CBMiX");
    }

    static Stream<Arguments> decodeFailures() {
        return Stream.of(
            Arguments.of("answer 500", "fail", "status=500"),
            Arguments.of("structured answer without a URL", "no-url", "no-url"),
            Arguments.of("a Google host in the answer", "google-host", "google-host"));
    }

    @ParameterizedTest(name = "{0}: no URL, reason {2}")
    @MethodSource("decodeFailures")
    void aFailedDecodeHasNoUrlAndAFixedReasonWord(String name, String mode, String reason) {
        news.decodeMode = mode;
        String[] r = ArticleApi.decode(decoder(), attrs("abc"), Duration.ofSeconds(5));
        assertThat(r[0]).as(name).isNull();
        assertThat(r[1]).as(name).isEqualTo(reason);
    }

    @Test
    void aWrongSignatureIsAnswered400AndNothingIsReturned() {
        String[] r = ArticleApi.decode(decoder(), new Attrs("abc", 1759737600L, "wrong"), Duration.ofSeconds(5));
        assertThat(r[0]).isNull();
        assertThat(r[1]).isEqualTo("status=400");
        assertThat(news.decodeRequests).hasSize(1);
        assertThat(news.decodeRequests.get(0).status()).isEqualTo(400);
    }

    @Test
    void aDecodeAnswerAfterTheTimeoutIsATimeoutAndAnUnreachableEndpointAnError() {
        news.decodeDelayMs = 1500;
        String[] slow = ArticleApi.decode(decoder(), attrs("abc"), Duration.ofMillis(400));
        assertThat(slow[0]).isNull();
        assertThat(slow[1]).isEqualTo("timeout");
        String[] down = ArticleApi.decode(ArticleApi.decoder("http://127.0.0.1:1/batchexecute", base()), attrs("abc"), Duration.ofSeconds(5));
        assertThat(down[0]).isNull();
        assertThat(down[1]).isEqualTo("error");
    }

    @Test
    void aDecodeAnswerWhoseBodyStallsAfterTheHeadersIsATimeToo() {
        news.decodeBodyStallMs = 1500;
        long t0 = System.nanoTime();
        String[] slow = ArticleApi.decode(decoder(), attrs("abc"), Duration.ofMillis(400));
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(slow[0]).isNull();
        assertThat(slow[1]).isEqualTo("timeout");
        assertThat(ms).as("cut at the timeout, not when the stalled body ends").isLessThan(1200L);
    }

    // ---- the whole timing area of the retrieval timeouts (PT1S): each request kind, delay before the headers and a stalled body ------

    static Stream<Arguments> timingAreas() {
        // request kind, where the 1.3 s go, expected status of the link
        return Stream.of(
            Arguments.of("decode", "before the headers", "DECODE_FAILED"),
            Arguments.of("decode", "body stalled after the headers", "DECODE_FAILED"),
            Arguments.of("google page", "before the headers", "DECODE_FAILED"),
            Arguments.of("google page", "body stalled after the headers", "DECODE_FAILED"),
            Arguments.of("publisher", "before the headers", "PAGE_FAILED"),
            Arguments.of("publisher", "body stalled after the headers", "PAGE_FAILED"));
    }

    @ParameterizedTest(name = "{0}: 1.3 s {1} with PT1S -> {2}")
    @MethodSource("timingAreas")
    void aRequestOfAnyKindThatIsNotCompleteWithinTheTimeoutFailsItsStepAtTheTimeout(String kind, String where, String expected) {
        boolean stall = where.startsWith("body");
        switch (kind) {
            case "decode" -> { if (stall) news.decodeBodyStallMs = 1300; else news.decodeDelayMs = 1300; }
            case "google page" -> { if (stall) news.googlePageBodyStallMs = 1300; else news.googlePageDelayMs = 1300; }
            default -> { if (stall) news.publisherBodyStallMs = 1300; else news.publisherDelayMs = 1300; }
        }
        Resolver resolver = new Resolver();
        resolver.map("localhost", "127.0.0.1");
        Duration timeout = Duration.ofSeconds(1);
        Fx fx = SafeFetcherSupport.fetcher(resolver, Set.of("127.0.0.1", "localhost"), timeout, 2 * 1024 * 1024, 5);
        Object retriever = ArticleApi.retriever(fx.raw(), decoder(), base(), timeout, 8, Clock.systemUTC());
        Object budget = ParallelSearchSupport.retrievalBudget(Clock.systemUTC(), Instant.now(), Duration.ofSeconds(90), null);
        long t0 = System.nanoTime();
        List<Outcome> out = ArticleApi.retrieveAll(retriever, List.of(base() + "/rss/articles/stall"), budget, () -> true);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(out).hasSize(1);
        assertThat(out.get(0).status()).as(kind + ", " + where).isEqualTo(expected);
        assertThat(out.get(0).body()).as("no body of a failed step").isNull();
        assertThat(ms).as(kind + ", " + where + ": cut at the 1 s timeout, not after the 1.3 s").isBetween(900L, 1250L);
    }

    @Test
    void theDecodeIdOverrideOfTheStubIsReturned() {
        news.decoded.put("abc", "https://other.example/story");
        assertThat(ArticleApi.decode(decoder(), attrs("abc"), Duration.ofSeconds(5))[0]).isEqualTo("https://other.example/story");
    }
}
