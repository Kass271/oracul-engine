package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * Row 10 and the ArticleMetadataFetcher rules (og/meta extraction, fallbacks, failure cases) as changed by article-retrieval.md slice 08
 * (FR-54, release finding R2): a Google link is decoded (Google page, batchexecute) to the publisher URL, whose page is fetched safely
 * (FR-56) and read; a failed page keeps the publisher URL with PAGE_FAILED, a failed decode keeps the Google link with DECODE_FAILED.
 */
// @trace FR-13
// @trace FR-56
// @trace FR-53
// @trace FR-54
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=9",
})
class SourceMetadataIT extends AbstractRunIT {

    private final Instant seenAt = Instant.now().minus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

    /** A Google News item for this link; the publisher is who.int (source text and url). */
    private String art(String link, String title) {
        return StubNews.rssItem(title, link, StubNews.pubDate(seenAt), "who.int", "https://who.int");
    }

    private Map<String, Map<String, Object>> runWith(List<String> articles) throws Exception {
        // FR-53: at most 4 sources per pipeline, so the articles are spread over three pipelines (the first query of each, <= 12 items)
        assertThat(articles.size()).isLessThanOrEqualTo(12);
        List<List<String>> perPipeline = new ArrayList<>();
        for (int from = 0; from < articles.size(); from += 4) perPipeline.add(articles.subList(from, Math.min(from + 4, articles.size())));
        news.responder = StubNews.firstQueryItems(perPipeline);
        String sid = connectedSid();
        String id = (String) startOk(sid, AbstractEventIT.wildcardsBody(3)).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("fetch failures never fail the run: " + run).isEqualTo("COMPLETED");
        Map<String, Map<String, Object>> byUrl = new HashMap<>();
        for (Map<String, Object> s : sourceItems(sid, id)) byUrl.put((String) s.get("url"), s);
        return byUrl;
    }

    // #10
    @Test
    void failingArticlePagesKeepProviderMetadata() throws Exception {
        String base = news.baseUrl();
        // links as the feed gives them: three Google links that decode to /articles/<name>, and a plain link nobody answers
        String[] links = {base + "/rss/articles/slow", "http://127.0.0.1:1/unreachable", base + "/rss/articles/pdf", base + "/rss/articles/ok"};
        List<String> articles = new ArrayList<>();
        for (String u : links) articles.add(art(u, "Title " + u.substring(u.lastIndexOf('/') + 1)));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(4);
        // a failed publisher page keeps the PUBLISHER url (PAGE_FAILED); a Google link that cannot be decoded keeps the Google link
        String[] urls = {base + "/articles/slow", links[1], base + "/articles/pdf", base + "/articles/ok"};
        for (String u : urls) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s).as(u).isNotNull();
            assertThat(s.get("sourceType")).isEqualTo("OFFICIAL");
            assertThat(s.get("sourceQuality")).isEqualTo(0.95);
            assertThat(OffsetDateTime.parse((String) s.get("publishedAt")).toInstant()).isEqualTo(seenAt);
        }
        for (String u : List.of(urls[1], urls[2])) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s.get("metadataFetched")).as(u).isEqualTo(false);
            assertThat(s.get("publisher")).as(u).isEqualTo("who.int");
            assertThat(s.get("summary")).as(u).isEqualTo(s.get("title"));
            assertThat(s.get("excerpts")).as(u).isEqualTo(List.of());
        }
        assertThat(byUrl.get(urls[1]).get("contentStatus")).as("unreachable Google link").isEqualTo("DECODE_FAILED");
        assertThat(byUrl.get(urls[1])).as("no publisher host while the url is the Google link").doesNotContainKey("publisherHost");
        assertThat(byUrl.get(urls[2]).get("contentStatus")).as("application/pdf").isEqualTo("PAGE_FAILED");
        assertThat(byUrl.get(urls[2]).get("publisherHost")).isEqualTo("127.0.0.1");
        // the 3 s page is read under the 8 s article-fetch-timeout (the phase-01 3 s gave up on it)
        for (String u : List.of(urls[0], urls[3])) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s.get("contentStatus")).as(u).isEqualTo("RETRIEVED");
            assertThat(s.get("metadataFetched")).as(u).isEqualTo(true);
            assertThat(s.get("publisher")).as(u).isEqualTo("Stub Site");
            assertThat(s.get("publisherHost")).as(u).isEqualTo("127.0.0.1");
        }
        assertThat(byUrl.get(urls[0]).get("summary")).isEqualTo("Summary of slow");
        assertThat(byUrl.get(urls[3]).get("summary")).isEqualTo("Summary of ok");
    }

    @Test
    void extractionRulesAndFallbacks() throws Exception {
        String base = news.baseUrl();
        news.pages.put("desc-only", new StubNews.Page(200, "text/html",
            "<html><head><meta name=\"description\" content=\"  Plain   description\n here \"></head><body></body></html>"));
        news.pages.put("og-wins", new StubNews.Page(200, "text/html; charset=utf-8",
            "<html><head><meta name=\"description\" content=\"Meta text\"><meta property=\"og:description\" content=\"OG text\">"
                + "<meta property=\"og:site_name\" content=\"  Site X  \"></head></html>"));
        news.pages.put("no-meta", new StubNews.Page(200, "text/html", "<html><head><title>t</title></head><body>hi</body></html>"));
        news.pages.put("blank-site", new StubNews.Page(200, "text/html",
            "<html><head><meta property=\"og:site_name\" content=\"   \"><meta property=\"og:description\" content=\"D\"></head></html>"));
        news.pages.put("long", new StubNews.Page(200, "text/html",
            "<html><head><meta property=\"og:description\" content=\"" + "w".repeat(1000) + "\"></head></html>"));
        news.pages.put("err404", new StubNews.Page(404, "text/html", StubNews.html("err404")));
        news.pages.put("xhtml", new StubNews.Page(200, "application/xhtml+xml",
            "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta property=\"og:description\" content=\"XHTML text\"/></head></html>"));
        news.pages.put("text-plain", new StubNews.Page(200, "text/plain", "just text"));
        // R2: text/plain pages are read now; a block of 80 characters is a fragment (the first paragraph of at least 80 characters; the
        // article is the first of the third pipeline of runWith)
        String block = "A plain text block of exactly eighty characters, written for the fallback rule..";
        assertThat(block).hasSize(80);
        news.pages.put("text-block", new StubNews.Page(200, "text/plain", block));
        news.pages.put("script", new StubNews.Page(200, "text/html",
            "<html><head><meta property=\"og:description\" content=\"Safe text\"><script>throw new Error('boom')</script></head></html>"));
        String[] names = {"desc-only", "og-wins", "no-meta", "blank-site", "long", "err404", "xhtml", "text-plain", "text-block", "script"};
        List<String> articles = new ArrayList<>();
        for (String n : names) articles.add(art(base + "/rss/articles/" + n, "Title " + n));
        articles.add(art(base + "/redirect/3", "Title redirect3"));
        articles.add(art(base + "/redirect/6", "Title redirect6"));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(12);
        // every Google link decodes to /articles/<name>; the publisher URL is kept whatever the page answered
        java.util.function.Function<String, Map<String, Object>> s = n -> byUrl.get(base + "/articles/" + n);

        assertThat(s.apply("desc-only").get("summary")).isEqualTo("Plain description here");
        assertThat(s.apply("desc-only").get("publisher")).isEqualTo("who.int");
        assertThat(s.apply("desc-only").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("desc-only").get("contentStatus")).as("a page without paragraphs").isEqualTo("NO_TEXT");
        assertThat(s.apply("og-wins").get("summary")).isEqualTo("OG text");
        assertThat(s.apply("og-wins").get("publisher")).isEqualTo("Site X");
        assertThat(s.apply("no-meta").get("summary")).isEqualTo("Title no-meta");
        assertThat(s.apply("no-meta").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("blank-site").get("publisher")).isEqualTo("who.int");
        assertThat(((String) s.apply("long").get("summary")).length()).isEqualTo(600);
        assertThat(s.apply("err404").get("metadataFetched")).isEqualTo(false);
        assertThat(s.apply("err404").get("summary")).isEqualTo("Title err404");
        assertThat(s.apply("err404").get("contentStatus")).isEqualTo("PAGE_FAILED");
        assertThat(s.apply("xhtml").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("xhtml").get("summary")).isEqualTo("XHTML text");
        assertThat(s.apply("text-plain").get("metadataFetched")).as("text/plain is read (R2)").isEqualTo(true);
        assertThat(s.apply("text-plain").get("contentStatus")).as("'just text' is no 80-character block").isEqualTo("NO_TEXT");
        assertThat(s.apply("text-plain").get("excerpts")).isEqualTo(List.of());
        assertThat(s.apply("text-block").get("contentStatus")).isEqualTo("RETRIEVED");
        assertThat(s.apply("text-block").get("excerpts")).isEqualTo(List.of(Map.of("pipelineId", "W03", "fragments", List.of(block))));
        // the former "big" case (600,000 bytes, cut at 512 KB) is no longer cut at 2 MB: see bigPagesAreReadUpToTwoMegabytes
        assertThat(s.apply("script").get("summary")).isEqualTo("Safe text");

        // /redirect/N are Google links whose page (after 3 same-host redirects) has no attributes: the link stays (DECODE_FAILED)
        for (String path : List.of("/redirect/3", "/redirect/6")) {
            Map<String, Object> r = byUrl.get(base + path);
            assertThat(r).as(path).isNotNull();
            assertThat(r.get("metadataFetched")).as(path).isEqualTo(false);
            assertThat(r.get("contentStatus")).as(path).isEqualTo("DECODE_FAILED");
            assertThat(r).as(path).doesNotContainKey("publisherHost");
        }
        assertThat(byUrl.get(base + "/redirect/0")).as("the page behind the chain is no publisher page").isNull();
    }

    private String viaRedirect(String location) {
        return news.baseUrl() + "/redirect-to?location=" + URLEncoder.encode(location, StandardCharsets.UTF_8);
    }

    // @trace FR-56
    @Test
    void anOffHostRedirectOfTheGooglePageIsNotFollowedAndTheFeedLinkStays() throws Exception {
        String base = news.baseUrl();
        int port = news.port();
        String[] refused = {
            "http://localhost:" + port + "/articles/internal-1",   // not exempt, resolves to loopback
            "http://169.254.169.254/latest/meta-data",
            "http://10.0.0.5/x",
            "http://[::1]:" + port + "/articles/internal-2",
            "file:///etc/passwd",
        };
        List<String> articles = new ArrayList<>();
        List<String> links = new ArrayList<>();
        for (int i = 0; i < refused.length; i++) {
            String link = viaRedirect(refused[i]);
            links.add(link);
            articles.add(art(link, "Refused " + i));
        }
        articles.add(art("http://127.0.0.1:" + port + "/rss/articles/ok", "Title ok"));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(6);
        for (int i = 0; i < refused.length; i++) {
            Map<String, Object> s = byUrl.get(links.get(i));
            assertThat(s).as("source with the feed link kept as url: " + refused[i]).isNotNull();
            // the Google page fetch follows redirects of the same host only: the 302 is the answer, never followed
            assertThat(s.get("contentStatus")).as(refused[i]).isEqualTo("DECODE_FAILED");
            assertThat(s).as(refused[i]).doesNotContainKey("publisherHost");
            assertThat(s.get("metadataFetched")).as(refused[i]).isEqualTo(false);
            assertThat(s.get("summary")).as(refused[i]).isEqualTo("Refused " + i);
            assertThat(s.get("publisher")).as(refused[i]).isEqualTo("who.int");
        }
        assertThat(news.articleRequests).as("no request reaches the other address").doesNotContain("internal-1", "internal-2");
        assertThat(news.decodeRequests).as("only the ok link reached the decode call").hasSize(1);
        Map<String, Object> ok = byUrl.get(base + "/articles/ok");
        assertThat(ok).as("a feed link on the exempt host 127.0.0.1 decodes and is read").isNotNull();
        assertThat(ok.get("metadataFetched")).isEqualTo(true);
        assertThat(ok.get("contentStatus")).isEqualTo("RETRIEVED");
        assertThat(ok.get("summary")).isEqualTo("Summary of ok");
    }

    // @trace FR-56
    @Test
    void refusedPublisherUrlsAreKeptWithStatusRefused() throws Exception {
        String base = news.baseUrl();
        int port = news.port();
        // decoded publisher URLs the safe fetcher refuses: loopback by another name, private, IPv6 loopback, and an exempt-host page that
        // redirects to the link-local metadata address (a refused redirect hop)
        String[] refused = {
            "http://localhost:" + port + "/articles/internal-1",
            "http://10.0.0.5/x",
            "http://[::1]:" + port + "/articles/internal-2",
            viaRedirect("http://169.254.169.254/"),
        };
        List<String> articles = new ArrayList<>();
        for (int i = 0; i < refused.length; i++) {
            news.decoded.put("dec-" + i, refused[i]);
            articles.add(art(base + "/rss/articles/dec-" + i, "Refused " + i));
        }
        // a feed link that is not on the Google host is a publisher URL: refused without any decode call
        String direct = "http://localhost:" + port + "/articles/direct";
        articles.add(art(direct, "Refused direct"));
        articles.add(art(base + "/rss/articles/ok", "Title ok"));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(6);
        for (String u : refused) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s).as("source with the decoded URL as url: " + u).isNotNull();
            assertThat(s.get("contentStatus")).as(u).isEqualTo("REFUSED");
            assertThat(s.get("metadataFetched")).as(u).isEqualTo(false);
            assertThat(s.get("excerpts")).as(u).isEqualTo(List.of());
            assertThat(s.get("publisher")).as(u).isEqualTo("who.int");
        }
        assertThat(byUrl.get(refused[0]).get("publisherHost")).isEqualTo("localhost");
        assertThat(byUrl.get(refused[1]).get("publisherHost")).isEqualTo("10.0.0.5");
        Map<String, Object> d = byUrl.get(direct);
        assertThat(d).as("the non-Google feed link").isNotNull();
        assertThat(d.get("contentStatus")).isEqualTo("REFUSED");
        assertThat(news.articleRequests).as("a refused address is never requested").doesNotContain("internal-1", "internal-2", "direct");
        assertThat(news.decodeRequests.stream().map(StubNews.Decode::id)).as("no decode call for the direct publisher link")
            .containsExactlyInAnyOrder("dec-0", "dec-1", "dec-2", "dec-3", "ok");
        assertThat(byUrl.get(base + "/articles/ok").get("contentStatus")).isEqualTo("RETRIEVED");
    }

    // @trace FR-56
    @Test
    void bigPagesAreReadUpToTwoMegabytesAndNoFurther() throws Exception {
        String base = news.baseUrl();
        String small = base + "/big/2200000";
        String beyond = base + "/big/2200000?meta-at=2100000";
        String inside = base + "/big/2000000?meta-at=1900000";
        news.decoded.put("big-a", small);
        news.decoded.put("big-b", beyond);
        news.decoded.put("big-c", inside);
        Map<String, Map<String, Object>> byUrl = runWith(List.of(
            art(base + "/rss/articles/big-a", "Title small-meta"), art(base + "/rss/articles/big-b", "Title late-meta"),
            art(base + "/rss/articles/big-c", "Title inside-meta")));
        assertThat(byUrl).hasSize(3);
        Map<String, Object> first = byUrl.get(small);
        assertThat(first).as("decoded to the page").isNotNull();
        assertThat(first.get("metadataFetched")).isEqualTo(true);
        assertThat(first.get("summary")).as("meta tag at byte 0 of a 2.2 MB page").isEqualTo("Big page text");
        Map<String, Object> late = byUrl.get(beyond);
        assertThat(late).as("decoded to the page").isNotNull();
        assertThat(late.get("metadataFetched")).isEqualTo(true);
        assertThat(late.get("summary")).as("the tag lies beyond the 2 MB read").isEqualTo("Title late-meta");
        Map<String, Object> within = byUrl.get(inside);
        assertThat(within).as("decoded to the page").isNotNull();
        assertThat(within.get("metadataFetched")).isEqualTo(true);
        assertThat(within.get("summary")).as("meta at 1.9 MB was cut at 512 KB before").isEqualTo("Big page text");
    }
}
