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

/** Row 10 and the ArticleMetadataFetcher rules: og/meta extraction, fallbacks, failure cases; safe fetching (FR-56). */
// @trace FR-13
// @trace FR-56
// @trace FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=9",
    "oracul.news.article-fetch-timeout=PT0.5S",
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
        // links as the feed gives them: the redirecting ones lead to /articles/<name>, the unreachable one is a plain link
        String[] links = {base + "/rss/articles/slow", "http://127.0.0.1:1/unreachable", base + "/rss/articles/pdf", base + "/rss/articles/ok"};
        List<String> articles = new ArrayList<>();
        for (String u : links) articles.add(art(u, "Title " + u.substring(u.lastIndexOf('/') + 1)));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(4);
        // a failed page keeps the link of the feed as url; the page that answers resolves to /articles/ok
        String[] urls = {links[0], links[1], links[2], base + "/articles/ok"};
        for (String u : urls) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s).as(u).isNotNull();
            assertThat(s.get("sourceType")).isEqualTo("OFFICIAL");
            assertThat(s.get("sourceQuality")).isEqualTo(0.95);
            assertThat(OffsetDateTime.parse((String) s.get("publishedAt")).toInstant()).isEqualTo(seenAt);
        }
        for (String u : List.of(urls[0], urls[1], urls[2])) {
            Map<String, Object> s = byUrl.get(u);
            assertThat(s.get("metadataFetched")).as(u).isEqualTo(false);
            assertThat(s.get("publisher")).as(u).isEqualTo("who.int");
            assertThat(s.get("summary")).as(u).isEqualTo(s.get("title"));
        }
        Map<String, Object> ok = byUrl.get(urls[3]);
        assertThat(ok.get("metadataFetched")).isEqualTo(true);
        assertThat(ok.get("publisher")).isEqualTo("Stub Site");
        assertThat(ok.get("summary")).isEqualTo("Summary of ok");
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
        news.pages.put("script", new StubNews.Page(200, "text/html",
            "<html><head><meta property=\"og:description\" content=\"Safe text\"><script>throw new Error('boom')</script></head></html>"));
        String[] names = {"desc-only", "og-wins", "no-meta", "blank-site", "long", "err404", "xhtml", "text-plain", "script"};
        List<String> articles = new ArrayList<>();
        for (String n : names) articles.add(art(base + "/rss/articles/" + n, "Title " + n));
        articles.add(art(base + "/redirect/3", "Title redirect3"));
        articles.add(art(base + "/redirect/6", "Title redirect6"));
        Map<String, Map<String, Object>> byUrl = runWith(articles);
        assertThat(byUrl).hasSize(11);
        // a page that was read resolves the feed link to /articles/<name>; a page that failed keeps the feed link
        java.util.function.Function<String, Map<String, Object>> s = n ->
            byUrl.containsKey(base + "/articles/" + n) ? byUrl.get(base + "/articles/" + n) : byUrl.get(base + "/rss/articles/" + n);

        assertThat(s.apply("desc-only").get("summary")).isEqualTo("Plain description here");
        assertThat(s.apply("desc-only").get("publisher")).isEqualTo("who.int");
        assertThat(s.apply("desc-only").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("og-wins").get("summary")).isEqualTo("OG text");
        assertThat(s.apply("og-wins").get("publisher")).isEqualTo("Site X");
        assertThat(s.apply("no-meta").get("summary")).isEqualTo("Title no-meta");
        assertThat(s.apply("no-meta").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("blank-site").get("publisher")).isEqualTo("who.int");
        assertThat(((String) s.apply("long").get("summary")).length()).isEqualTo(600);
        assertThat(s.apply("err404").get("metadataFetched")).isEqualTo(false);
        assertThat(s.apply("err404").get("summary")).isEqualTo("Title err404");
        assertThat(s.apply("xhtml").get("metadataFetched")).isEqualTo(true);
        assertThat(s.apply("xhtml").get("summary")).isEqualTo("XHTML text");
        assertThat(s.apply("text-plain").get("metadataFetched")).isEqualTo(false);
        // the former "big" case (600,000 bytes, cut at 512 KB) is no longer cut at 2 MB: see bigPagesAreReadUpToTwoMegabytes
        assertThat(s.apply("script").get("summary")).isEqualTo("Safe text");

        assertThat(byUrl.get(base + "/redirect/0").get("metadataFetched")).as("3 redirects are followed to the page").isEqualTo(true);
        assertThat(byUrl.get(base + "/redirect/0").get("summary")).isEqualTo("Summary of redirected");
        assertThat(byUrl.get(base + "/redirect/6").get("metadataFetched")).as("more than 5 redirects: the link stays").isEqualTo(false);
    }

    private String viaRedirect(String location) {
        return news.baseUrl() + "/redirect-to?location=" + URLEncoder.encode(location, StandardCharsets.UTF_8);
    }

    // @trace FR-56
    @Test
    void refusedRedirectTargetsLeaveTheFeedLinkAsContentNotRetrieved() throws Exception {
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
            assertThat(s.get("metadataFetched")).as(refused[i]).isEqualTo(false);
            assertThat(s.get("summary")).as(refused[i]).isEqualTo("Refused " + i);
            assertThat(s.get("publisher")).as(refused[i]).isEqualTo("who.int");
        }
        assertThat(news.articleRequests).as("the refused hop is never requested").doesNotContain("internal-1", "internal-2");
        Map<String, Object> ok = byUrl.get(base + "/articles/ok");
        assertThat(ok).as("a feed link on the exempt host 127.0.0.1 keeps resolving").isNotNull();
        assertThat(ok.get("metadataFetched")).isEqualTo(true);
        assertThat(ok.get("summary")).isEqualTo("Summary of ok");
    }

    // @trace FR-56
    @Test
    void bigPagesAreReadUpToTwoMegabytesAndNoFurther() throws Exception {
        String base = news.baseUrl();
        String small = base + "/big/2200000";
        String beyond = base + "/big/2200000?meta-at=2100000";
        String inside = base + "/big/2000000?meta-at=1900000";
        Map<String, Map<String, Object>> byUrl = runWith(List.of(
            art(viaRedirect(small), "Title small-meta"), art(viaRedirect(beyond), "Title late-meta"),
            art(viaRedirect(inside), "Title inside-meta")));
        assertThat(byUrl).hasSize(3);
        Map<String, Object> first = byUrl.get(small);
        assertThat(first).as("resolved to the page").isNotNull();
        assertThat(first.get("metadataFetched")).isEqualTo(true);
        assertThat(first.get("summary")).as("meta tag at byte 0 of a 2.2 MB page").isEqualTo("Big page text");
        Map<String, Object> late = byUrl.get(beyond);
        assertThat(late).as("resolved to the page").isNotNull();
        assertThat(late.get("metadataFetched")).isEqualTo(true);
        assertThat(late.get("summary")).as("the tag lies beyond the 2 MB read").isEqualTo("Title late-meta");
        Map<String, Object> within = byUrl.get(inside);
        assertThat(within).as("resolved to the page").isNotNull();
        assertThat(within.get("metadataFetched")).isEqualTo(true);
        assertThat(within.get("summary")).as("meta at 1.9 MB was cut at 512 KB before").isEqualTo("Big page text");
    }
}
