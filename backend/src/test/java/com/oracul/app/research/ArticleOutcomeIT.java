package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.ArticleContentStatus;
import com.oracul.app.api.model.Source;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-54 "Publisher article retrieval", the outcome rows of article-retrieval.md slice 08 range (e) and the timing classes (f): one kept
 * source each through {@code SourceRetrieval.readSources} against the in-process stub (modes and maps of FR-61), asserting status, stored url,
 * publisher host, the number of decode / publisher requests, the metadata flag, the excerpt rule and the log lines (a fixed reason word, never
 * a URL, a page, the {@code f.req} value or the decode answer). {@code article-fetch-timeout} is PT1S here.
 */
// @trace FR-54
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.news.article-fetch-timeout=PT1S",
})
class ArticleOutcomeIT extends AbstractNewsSearchIT {

    private static final String REUTERS = "https://www.reuters.com";

    private String base() {
        return news.baseUrl();
    }

    private String encoded(String url) {
        return URLEncoder.encode(url, StandardCharsets.UTF_8);
    }

    /** One kept source for the feed link: the first query of the (phase-01 shaped) plan answers one item, the others an empty feed. */
    private Source read(String link, String title) throws Exception {
        var plan = planOf(List.of("solar flare"));
        String first = plan.getQueries().get(0).getText();
        news.responder = req -> req.elements().contains(first)
            ? StubNews.rss(StubNews.rssItem(title, link, StubNews.pubDate(Instant.now().minus(1, ChronoUnit.DAYS)), "Reuters", REUTERS))
            : StubNews.rss();
        var outcome = search(plan);
        List<Source> out = new ArrayList<>();
        for (var st : retrieval.readSources(outcome, com.oracul.app.api.model.HorizonCode._1Y)) out.add(st.source());
        assertThat(out).as("one kept source").hasSize(1);
        return out.get(0);
    }

    /** What a row expects. {@code articles} < 0: not checked. */
    private record Exp(String link, ArticleContentStatus status, String url, boolean host, int decodes, int articles) {
    }

    private Exp prepare(String row) {
        String b = base();
        int p = news.port();
        String id = row;
        String link = b + "/rss/articles/" + id;
        String pub = b + "/articles/" + id;
        switch (row) {
            case "ok":
                return new Exp(link, ArticleContentStatus.RETRIEVED, pub, true, 1, 1);
            case "google-page-404":
                news.googlePages.put(id, new StubNews.Page(404, "text/html", "<html>gone</html>"));
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 0, 0);
            case "no-attributes":
                news.googlePages.put(id, new StubNews.Page(200, "text/html", StubNews.googlePage(id, null, null)));
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 0, 0);
            case "blank-attributes":
                news.googlePages.put(id, new StubNews.Page(200, "text/html", StubNews.googlePage(" ", " ", " ")));
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 0, 0);
            case "ts-not-digits":
                news.googlePages.put(id, new StubNews.Page(200, "text/html", StubNews.googlePage(id, "17597x", "sig-" + id)));
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 0, 0);
            case "direct-article-link":
                return new Exp(b + "/articles/" + id, ArticleContentStatus.DECODE_FAILED, b + "/articles/" + id, false, 0, -1);
            case "off-host-302": {
                String l = b + "/redirect-to?location=" + encoded("http://localhost:" + p + "/x");
                return new Exp(l, ArticleContentStatus.DECODE_FAILED, l, false, 0, 0);
            }
            case "six-redirects":
                return new Exp(b + "/redirect/6", ArticleContentStatus.DECODE_FAILED, b + "/redirect/6", false, 0, 0);
            case "decode-500":
                news.decodeMode = "fail";
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "decode-400":
                news.googlePages.put(id, new StubNews.Page(200, "text/html", StubNews.googlePage(id, "1759737600", "wrong-signature")));
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "decode-no-url":
                news.decodeMode = "no-url";
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "decode-google-host":
                news.decodeMode = "google-host";
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "decoded-google-search":
                news.decoded.put(id, "https://www.google.com/url?q=x");
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "decoded-again":
                news.decoded.put(id, b + "/rss/articles/again");
                return new Exp(link, ArticleContentStatus.DECODE_FAILED, link, false, 1, 0);
            case "publisher-404":
                news.pages.put(id, new StubNews.Page(404, "text/html", "<html>gone</html>"));
                return new Exp(link, ArticleContentStatus.PAGE_FAILED, pub, true, 1, 1);
            case "publisher-503":
                news.pages.put(id, new StubNews.Page(503, "text/html", "<html>down</html>"));
                return new Exp(link, ArticleContentStatus.PAGE_FAILED, pub, true, 1, 1);
            case "pdf":
                return new Exp(link, ArticleContentStatus.PAGE_FAILED, pub, true, 1, 1);
            case "no-content-type":
                news.pages.put(id, new StubNews.Page(200, null, "<html><body><p>no content type</p></body></html>"));
                return new Exp(link, ArticleContentStatus.PAGE_FAILED, pub, true, 1, 1);
            case "publisher-six-redirects":
                news.decoded.put(id, b + "/redirect/6");
                return new Exp(link, ArticleContentStatus.PAGE_FAILED, b + "/redirect/6", true, 1, 0);
            case "text-plain-block":
                news.pages.put(id, new StubNews.Page(200, "text/plain", "A plain text block of exactly eighty characters, written for the fallback rule.."));
                return new Exp(link, ArticleContentStatus.RETRIEVED, pub, true, 1, 1);
            case "text-plain-short":
                news.pages.put(id, new StubNews.Page(200, "text/plain", "just text"));
                return new Exp(link, ArticleContentStatus.NO_TEXT, pub, true, 1, 1);
            case "refused-localhost": {
                String d = "http://localhost:" + p + "/articles/x";
                news.decoded.put(id, d);
                return new Exp(link, ArticleContentStatus.REFUSED, d, true, 1, 0);
            }
            case "refused-private": {
                news.decoded.put(id, "http://10.0.0.5/x");
                return new Exp(link, ArticleContentStatus.REFUSED, "http://10.0.0.5/x", true, 1, 0);
            }
            case "refused-ipv6": {
                String d = "http://[::1]:" + p + "/x";
                news.decoded.put(id, d);
                return new Exp(link, ArticleContentStatus.REFUSED, d, true, 1, 0);
            }
            case "refused-redirect-hop": {
                String d = b + "/redirect-to?location=" + encoded("http://169.254.169.254/");
                news.decoded.put(id, d);
                return new Exp(link, ArticleContentStatus.REFUSED, d, true, 1, 0);
            }
            case "non-google-localhost": {
                String l = "http://localhost:" + p + "/articles/x";
                return new Exp(l, ArticleContentStatus.REFUSED, l, true, 0, 0);
            }
            default:
                throw new IllegalArgumentException(row);
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"ok", "google-page-404", "no-attributes", "blank-attributes", "ts-not-digits", "direct-article-link", "off-host-302",
        "six-redirects", "decode-500", "decode-400", "decode-no-url", "decode-google-host", "decoded-google-search", "decoded-again", "publisher-404",
        "publisher-503", "pdf", "no-content-type", "publisher-six-redirects", "text-plain-block", "text-plain-short", "refused-localhost",
        "refused-private", "refused-ipv6", "refused-redirect-hop", "non-google-localhost"})
    void everyOutcomeRowHasItsStatusUrlHostAndRequests(String row, CapturedOutput out) throws Exception {
        Exp e = prepare(row);
        Source s = read(e.link(), "Story " + row);
        assertThat(s.getContentStatus()).as(row + ": status").isEqualTo(e.status());
        assertThat(s.getUrl().toString()).as(row + ": url").isEqualTo(e.url());
        if (e.host()) assertThat(s.getPublisherHost()).as(row + ": publisherHost").isNotNull().isNotBlank();
        else assertThat(s.getPublisherHost()).as(row + ": no publisherHost while the url is the Google link").isNull();
        boolean read = e.status() == ArticleContentStatus.RETRIEVED || e.status() == ArticleContentStatus.NO_TEXT;
        assertThat(s.getMetadataFetched()).as(row + ": metadataFetched true iff the page was read").isEqualTo(read);
        assertThat(news.decodeRequests).as(row + ": decode requests").hasSize(e.decodes());
        if (e.articles() >= 0) assertThat(news.articleRequests).as(row + ": publisher requests").hasSize(e.articles());
        if (!read) assertThat(s.getSummary()).as(row + ": no page, no description: snippet else title").isEqualTo("Story " + row);
        // legacy plan without pipelines: no excerpts are keyed, so none is sent; RETRIEVED / NO_TEXT follows whether a fragment was found
        assertThat(s.getExcerpts()).as(row).isNullOrEmpty();
        if (e.status() == ArticleContentStatus.REFUSED) {
            assertThat(news.articleRequests).as(row + ": no request reaches the refused address").isEmpty();
        }
        if (row.equals("off-host-302")) assertThat(news.paths).as("the off-host Location is not requested").doesNotContain("/x");
        if (row.equals("decoded-again")) {
            assertThat(news.googlePageRequests.stream().map(StubNews.GooglePageHit::id)).as("no request follows the decoded Google link").doesNotContain("again");
        }
        String log = out.getOut() + out.getErr();
        assertThat(log).as(row + ": no URL, request value or answer in any log line").doesNotContain("f.req").doesNotContain("garturl").doesNotContain("/articles/")
            .doesNotContain("/rss/articles/");
    }

    @Test
    void anOkRowSendsTheFixedDecodeRequest() throws Exception {
        Source s = read(base() + "/rss/articles/ok", "Story ok");
        assertThat(s.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED);
        assertThat(news.decodeRequests).hasSize(1);
        StubNews.Decode d = news.decodeRequests.get(0);
        assertThat(d.id()).isEqualTo("ok");
        assertThat(d.ts()).isEqualTo("1759737600");
        assertThat(d.sg()).isEqualTo("sig-ok");
        assertThat(d.contentType()).isEqualTo("application/x-www-form-urlencoded;charset=UTF-8");
        assertThat(d.userAgent()).isEqualTo("Mozilla/5.0 (compatible; ORACUL/1.0)");
        assertThat(d.status()).isEqualTo(200);
        assertThat(news.googlePageRequests.stream().map(StubNews.GooglePageHit::redirect).toList()).containsExactly(true, false);
        assertThat(news.articleRequests).containsExactly("ok");
        assertThat(news.userAgents).as("every request of the retrieval sends the User-Agent of the Google News client")
            .filteredOn(l -> l.startsWith("/rss/articles/") || l.startsWith("/articles/") || l.startsWith("/_/")).isNotEmpty()
            .allSatisfy(l -> assertThat(l).endsWith(" Mozilla/5.0 (compatible; ORACUL/1.0)"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"decode-500", "decode-no-url", "decode-google-host", "no-attributes", "publisher-503", "pdf", "refused-private"})
    void theFailureLineNamesAFixedReasonWord(String row, CapturedOutput out) throws Exception {
        Exp e = prepare(row);
        read(e.link(), "Story " + row);
        String log = out.getOut() + out.getErr();
        switch (row) {
            case "decode-500" -> assertThat(log).contains("article decode failed: status=500");
            case "decode-no-url" -> assertThat(log).contains("article decode failed: no-url");
            case "decode-google-host" -> assertThat(log).contains("article decode failed: google-host");
            case "no-attributes" -> assertThat(log).contains("article decode failed: no-attributes");
            case "publisher-503" -> assertThat(log).contains("article fetch failed: status=503");
            case "pdf" -> assertThat(log).contains("article fetch failed: content-type");
            case "refused-private" -> assertThat(log).contains("article fetch refused: blocked-address host=10.0.0.5");
            default -> throw new IllegalArgumentException(row);
        }
    }

    // ---- (f) timing with article-fetch-timeout PT1S ---------------------------------------------------------------------------

    @Test
    void aPublisherAnswerAtSevenTenthsOfASecondIsUsedAndAtThirteenTenthsIsAFailure() throws Exception {
        news.publisherDelayMs = 700;
        Source fast = read(base() + "/rss/articles/t-fast", "Story fast");
        assertThat(fast.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED);
        news.reset();
        news.publisherDelayMs = 1300;
        Source slow = read(base() + "/rss/articles/t-slow", "Story slow");
        assertThat(slow.getContentStatus()).isEqualTo(ArticleContentStatus.PAGE_FAILED);
        assertThat(slow.getUrl().toString()).isEqualTo(base() + "/articles/t-slow");
        assertThat(slow.getPublisherHost()).isNotNull();
    }

    @Test
    void aDecodeAnswerAtSevenTenthsOfASecondIsUsedAndAtThirteenTenthsIsAFailure() throws Exception {
        news.decodeDelayMs = 700;
        Source fast = read(base() + "/rss/articles/d-fast", "Story fast");
        assertThat(fast.getContentStatus()).isEqualTo(ArticleContentStatus.RETRIEVED);
        news.reset();
        news.decodeDelayMs = 1300;
        Source slow = read(base() + "/rss/articles/d-slow", "Story slow");
        assertThat(slow.getContentStatus()).isEqualTo(ArticleContentStatus.DECODE_FAILED);
        assertThat(slow.getUrl().toString()).isEqualTo(base() + "/rss/articles/d-slow");
        assertThat(slow.getPublisherHost()).isNull();
    }

    @Test
    void theGooglePageAndTheDecodeShareOneTimeout() throws Exception {
        // 0.6 s for the Google page + 0.6 s for the decode: 1.2 s > the 1 s of steps 2 + 3 together
        news.googlePageDelayMs = 600;
        news.decodeDelayMs = 600;
        Source s = read(base() + "/rss/articles/shared-timeout", "Story shared");
        assertThat(s.getContentStatus()).isEqualTo(ArticleContentStatus.DECODE_FAILED);
    }
}
