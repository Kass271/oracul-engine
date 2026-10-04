package com.oracul.app.research;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.test.context.DynamicPropertyRegistry;

/** In-process stand-in for GDELT DOC 2.0 plus the article pages it points to (NFR-7). One instance per JVM. */
public final class StubGdelt {

    /**
     * A GDELT request: arrival number (1-based), raw query string, decoded parameters, the OR elements of the decoded
     * {@code query} (unquoted, in order; the " sourcelang:english" suffix and the outer parentheses removed),
     * the 1-based global index of its first element (= number of elements of all earlier requests + 1) and the
     * arrival time (System.nanoTime).
     */
    public record Request(int number, String rawQuery, Map<String, String> params, List<String> elements, int firstElement,
                          long arrivedNanos) {}

    /**
     * A Google News RSS request (news-search.md FR-48): arrival number (1-based), raw query string, decoded parameters,
     * the decoded {@code q}, the OR elements of {@code q} (the " when:<N>d" suffix and the outer parentheses removed, split
     * on " OR ", unquoted), the lower-cased request headers and the arrival time (System.nanoTime).
     */
    public record RssRequest(int number, String rawQuery, Map<String, String> params, String q, List<String> elements,
                             Map<String, String> headers, long arrivedNanos) {}

    /** status 0 = drop the connection without answering. */
    public record Reply(int status, String contentType, String body, long delayMs) {}

    /** A custom article page for /articles/<name>. */
    public record Page(int status, String contentType, String body) {}

    public static final StubGdelt INSTANCE = new StubGdelt();
    public static final DateTimeFormatter SEENDATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final HttpServer server;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger elementCounter = new AtomicInteger();
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    private final AtomicInteger rssCounter = new AtomicInteger();
    /** Google News RSS requests, recorded apart from {@link #requests} (GDELT). */
    public final List<RssRequest> rssRequests = new CopyOnWriteArrayList<>();
    /** Names of the article pages (/articles/<name>) that were requested, in arrival order. */
    public final List<String> articleRequests = new CopyOnWriteArrayList<>();
    /** When set, every article page request waits until the latch is counted down (at most 60 s). */
    public volatile java.util.concurrent.CountDownLatch articleGate;
    public final Map<String, Page> pages = new ConcurrentHashMap<>();
    public final Map<String, String> sites = new ConcurrentHashMap<>();
    public volatile Function<Request, Reply> responder = defaultResponder();
    /** Answer of GET /rss/search; the default is HTTP 503 so that every group of an older test falls back to GDELT. */
    public volatile Function<RssRequest, Reply> rssResponder = defaultRssResponder();

    private StubGdelt() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v2/doc/doc", this::handleSearch);
        server.createContext("/rss/search", this::handleRssSearch);
        server.createContext("/rss/articles/", this::handleRssArticle);
        server.createContext("/articles/", this::handleArticle);
        server.createContext("/redirect/", this::handleRedirect);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public static void registerAll(DynamicPropertyRegistry r) {
        r.add("oracul.news.gdelt.base-url", INSTANCE::baseUrl);
        // news-search.md FR-48: tests never call the real Google News
        r.add("oracul.news.google.base-url", INSTANCE::baseUrl);
        r.add("oracul.news.google.request-spacing", () -> "PT0S");
        // spec news-search.md: no 5 s waits in ITs
        r.add("oracul.news.request-spacing", () -> "PT0S");
        r.add("oracul.news.rate-limit-wait", () -> "PT0S");
    }

    public void reset() {
        java.util.concurrent.CountDownLatch gate = articleGate;
        articleGate = null;
        if (gate != null) gate.countDown(); // never leave a handler thread parked
        rssRequests.clear();
        rssCounter.set(0);
        rssResponder = defaultRssResponder();
        articleRequests.clear();
        requests.clear();
        pages.clear();
        sites.clear();
        counter.set(0);
        elementCounter.set(0);
        responder = defaultResponder();
    }

    /** Registers the og:site_name of /articles/<name>; unregistered pages keep "Stub Site". */
    public void site(String name, String siteName) {
        sites.put(name, siteName);
    }

    public static Reply json(String body) {
        return new Reply(200, "application/json", body, 0);
    }

    public static Reply status(int status) {
        return new Reply(status, "application/json", "", 0);
    }

    public static Reply drop() {
        return new Reply(0, "application/json", "", 0);
    }

    public static Function<Request, Reply> defaultResponder() {
        return req -> json("{}");
    }

    public static Function<RssRequest, Reply> defaultRssResponder() {
        return req -> status(503);
    }

    /** A 200 Google News RSS 2.0 answer with these items (see {@link #rssItem}). */
    public static Reply rss(String... items) {
        return new Reply(200, "application/rss+xml; charset=utf-8", "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
            + "<rss version=\"2.0\"><channel><title>Google News</title><link>https://news.google.com</link>"
            + String.join("", items) + "</channel></rss>", 0);
    }

    /** A 200 answer with an arbitrary body. */
    public static Reply rssBody(String body) {
        return new Reply(200, "application/rss+xml; charset=utf-8", body, 0);
    }

    /** One RSS item; null title / link / pubDate are omitted, so is source when source and sourceUrl are both null (a null source with a sourceUrl gives an empty element). */
    public static String rssItem(String title, String link, String pubDate, String source, String sourceUrl) {
        StringBuilder sb = new StringBuilder("<item>");
        if (title != null) sb.append("<title>").append(xml(title)).append("</title>");
        if (link != null) sb.append("<link>").append(xml(link)).append("</link>");
        if (pubDate != null) sb.append("<pubDate>").append(xml(pubDate)).append("</pubDate>");
        if (source != null || sourceUrl != null) { // empty text with a url: <source url="..."></source>
            sb.append("<source").append(sourceUrl == null ? "" : " url=\"" + xml(sourceUrl) + "\"").append('>')
                .append(source == null ? "" : xml(source)).append("</source>");
        }
        return sb.append("</item>").toString();
    }

    /** RFC 1123 pubDate of an instant, e.g. "Sat, 03 Oct 2026 10:00:00 GMT". */
    public static String pubDate(Instant t) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(t.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String seendate(Instant t) {
        return SEENDATE.format(t.truncatedTo(ChronoUnit.SECONDS));
    }

    /** One GDELT article JSON object; null language / seendate are omitted. */
    public static String article(String url, String title, String domain, String language, String seendate) {
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"url\":").append(StubResponses.jsonString(url));
        sb.append(",\"url_mobile\":\"\"");
        sb.append(",\"title\":").append(StubResponses.jsonString(title));
        if (seendate != null) sb.append(",\"seendate\":").append(StubResponses.jsonString(seendate));
        sb.append(",\"socialimage\":\"\"");
        sb.append(",\"domain\":").append(StubResponses.jsonString(domain));
        if (language != null) sb.append(",\"language\":").append(StubResponses.jsonString(language));
        sb.append(",\"sourcecountry\":\"United States\"}");
        return sb.toString();
    }

    public static String articles(List<String> articleJson) {
        return "{\"articles\":[" + String.join(",", articleJson) + "]}";
    }

    public static String html(String name) {
        return html(name, "Stub Site");
    }

    public static String html(String name, String siteName) {
        return "<html><head><meta property=\"og:site_name\" content=\"" + siteName + "\"><meta property=\"og:description\" content=\"Summary of "
            + name + "\"></head><body>x</body></html>";
    }

    private void handleSearch(HttpExchange ex) throws IOException {
        String raw = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            params.put(URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8),
                i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        List<String> elements = elementsOf(params.get("query"));
        int first = elementCounter.getAndAdd(elements.size()) + 1;
        Request req = new Request(counter.incrementAndGet(), raw, params, elements, first, System.nanoTime());
        requests.add(req);
        Reply reply;
        try {
            reply = responder.apply(req);
        } catch (RuntimeException e) {
            reply = status(500);
        }
        try {
            if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (reply.status() == 0) {
            ex.close();
            return;
        }
        send(ex, reply.status(), reply.contentType(), reply.body().getBytes(StandardCharsets.UTF_8));
    }

    private void handleRssSearch(HttpExchange ex) throws IOException {
        String raw = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
        Map<String, String> params = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            params.put(URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8),
                i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        Map<String, String> headers = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        String q = params.getOrDefault("q", "");
        RssRequest req = new RssRequest(rssCounter.incrementAndGet(), raw, params, q, rssElementsOf(q), headers, System.nanoTime());
        rssRequests.add(req);
        Reply reply;
        try {
            reply = rssResponder.apply(req);
        } catch (RuntimeException e) {
            reply = status(500);
        }
        try {
            if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (reply.status() == 0) {
            ex.close();
            return;
        }
        send(ex, reply.status(), reply.contentType(), reply.body().getBytes(StandardCharsets.UTF_8));
    }

    /** Elements of a decoded Google query "(<e1> OR <e2>) when:7d" / "<e1> when:7d", unquoted. */
    static List<String> rssElementsOf(String q) {
        String t = q == null ? "" : q.trim().replaceAll(" when:\\d+d$", "").trim();
        if (t.startsWith("(") && t.endsWith(")")) t = t.substring(1, t.length() - 1);
        List<String> out = new java.util.ArrayList<>();
        for (String part : t.split(" OR ")) {
            String p = part.trim();
            if (p.length() >= 2 && p.startsWith("\"") && p.endsWith("\"")) p = p.substring(1, p.length() - 1);
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    /** /rss/articles/<rest> answers 302 to {base}/articles/<rest> (the query string is dropped). */
    private void handleRssArticle(HttpExchange ex) throws IOException {
        String rest = ex.getRequestURI().getPath().substring("/rss/articles/".length());
        ex.getResponseHeaders().add("Location", baseUrl() + "/articles/" + rest);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    /** OR elements of a decoded query "(<e1> OR <e2>) sourcelang:english" / "<e1> sourcelang:english", unquoted. */
    static List<String> elementsOf(String query) {
        if (query == null) return List.of();
        String q = query.trim();
        if (q.endsWith(" sourcelang:english")) q = q.substring(0, q.length() - " sourcelang:english".length()).trim();
        if (q.startsWith("(") && q.endsWith(")")) q = q.substring(1, q.length() - 1);
        List<String> out = new java.util.ArrayList<>();
        for (String part : q.split(" OR ")) {
            String p = part.trim();
            if (p.length() >= 2 && p.startsWith("\"") && p.endsWith("\"")) p = p.substring(1, p.length() - 1);
            if (!p.isEmpty()) out.add(p);
        }
        return out;
    }

    private void handleArticle(HttpExchange ex) throws IOException {
        String name = ex.getRequestURI().getPath().substring("/articles/".length());
        articleRequests.add(name);
        java.util.concurrent.CountDownLatch gate = articleGate;
        if (gate != null) {
            try {
                gate.await(60, java.util.concurrent.TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Page page = pages.get(name);
        if (page != null) {
            send(ex, page.status(), page.contentType(), page.body().getBytes(StandardCharsets.UTF_8));
            return;
        }
        if ("slow".equals(name)) {
            try {
                Thread.sleep(3000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        }
        if ("pdf".equals(name)) {
            send(ex, 200, "application/pdf", "%PDF-1.4 stub".getBytes(StandardCharsets.UTF_8));
            return;
        }
        send(ex, 200, "text/html; charset=utf-8", html(name, sites.getOrDefault(name, "Stub Site")).getBytes(StandardCharsets.UTF_8));
    }

    /** /redirect/N answers 302 to /redirect/N-1; /redirect/0 is an article page "redirected". */
    private void handleRedirect(HttpExchange ex) throws IOException {
        int n = Integer.parseInt(ex.getRequestURI().getPath().substring("/redirect/".length()));
        if (n <= 0) {
            send(ex, 200, "text/html", html("redirected").getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().add("Location", "/redirect/" + (n - 1));
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] out) throws IOException {
        ex.getResponseHeaders().add("Content-Type", contentType);
        try {
            ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0) ex.getResponseBody().write(out);
        } catch (IOException ignored) {
            // client gave up
        } finally {
            ex.close();
        }
    }
}
