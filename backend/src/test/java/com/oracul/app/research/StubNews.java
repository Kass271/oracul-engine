package com.oracul.app.research;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.URLDecoder;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.test.context.DynamicPropertyRegistry;

/** In-process stand-in for Google News RSS plus the article pages it points to (NFR-7). One instance per JVM. */
public final class StubNews {

    /**
     * A Google News RSS request ({@code GET /rss/search}): arrival number (1-based), raw query string, decoded parameters,
     * the decoded {@code q}, the OR elements of {@code q} (the " when:<N>d" suffix and the outer parentheses removed, split
     * on " OR ", unquoted), the 1-based global index of the first element (= number of elements of all earlier requests + 1),
     * the lower-cased request headers and the arrival time (System.nanoTime).
     */
    public record Request(int number, String rawQuery, Map<String, String> params, String q, List<String> elements,
                          int firstElement, Map<String, String> headers, long arrivedNanos) {}

    /** status 0 = drop the connection without answering. */
    public record Reply(int status, String contentType, String body, long delayMs) {}

    /** One request of any route: its path and arrival time (System.nanoTime). */
    public record Arrival(String path, long nanos) {}

    /** A custom article page for /articles/<name>. */
    public record Page(int status, String contentType, String body) {}

    /** FR-61: one request of {@code /rss/articles/<id>}: the 302 (redirect true) or the Google page (false); {@code rawQuery} as sent. */
    public record GooglePageHit(String id, String rawQuery, boolean redirect) {}

    /** FR-61: one batchexecute request; {@code id} / {@code ts} / {@code sg} are null when the {@code f.req} value did not carry them. */
    public record Decode(String id, String ts, String sg, String contentType, String userAgent, String fReq, int status) {}

    /** Paragraphs of the stub's publisher page (FR-61): the generic ones, and the ones that hold the remembered match text. */
    public static final String P1 = "Opening paragraph of this publisher page. It introduces the report in plain words for every reader.";
    public static final String P2_GENERIC = "Paragraph two continues the opening with neutral filler text and no particular subject at all.";
    public static final String P3 = "Background paragraph three adds neutral filler text so that the page reads like a real article.";
    public static final String P4_GENERIC = "Paragraph four continues with neutral filler text, written only to give the page enough length.";
    public static final String P5 = "Paragraph five repeats neutral filler text to keep the article long enough for the extraction rules.";
    public static final String P6 = "Closing paragraph six ends the article with a short summary in neutral and ordinary language here.";

    /** P2 for the match text {@code m}. */
    public static String p2(String m) {
        return m + " is the subject of this second paragraph, which gives the details the reader asked about.";
    }

    /** P4 for the match text {@code m}. */
    public static String p4(String m) {
        return "Further details on " + m + " follow in the fourth paragraph, with dates, names and a short quote.";
    }

    /** The Google page of FR-61 for an id; a null {@code ts} / {@code sg} / {@code id} leaves that attribute out. */
    public static String googlePage(String id, String ts, String sg) {
        return "<!doctype html><html><head><title>Google News</title><meta property=\"og:site_name\" content=\"Google News\">"
            + "<meta property=\"og:description\" content=\"Comprehensive up-to-date news coverage, aggregated from sources all over the world by Google News.\">"
            + "</head><body><c-wiz><div jscontroller=\"aLI87\"" + (id == null ? "" : " data-n-a-id=\"" + id + "\"")
            + (ts == null ? "" : " data-n-a-ts=\"" + ts + "\"") + (sg == null ? "" : " data-n-a-sg=\"" + sg + "\"")
            + "></div></c-wiz></body></html>";
    }

    /** The publisher page of FR-61: {@code match} = the remembered match text M (null: the generic page); {@code site} = og:site_name. */
    public static String publisherPage(String name, String match, String site) {
        String m = match == null ? "" : " " + match;
        return "<!doctype html><html><head><title>Stub article " + name + "</title><meta property=\"og:site_name\" content=\"" + site + "\">"
            + "<meta property=\"og:description\" content=\"Summary of " + name.replaceAll("[^\\w-]", "") + "\">"
            + "<style>body { color: black } /* STYLE TEXT" + m + " */</style><script>var x = 'SCRIPT TEXT" + m + "';</script></head>"
            + "<body><nav><p>NAVIGATION TEXT home world business" + m + "</p></nav><header><p>HEADER TEXT" + m + "</p></header>"
            + "<article><p>" + P1 + "</p><p>" + (match == null ? P2_GENERIC : p2(match)) + "</p><p>" + P3 + "</p><p>"
            + (match == null ? P4_GENERIC : p4(match)) + "</p><p>" + P5 + "</p><p>" + P6 + "</p></article>"
            + "<footer><p>FOOTER TEXT" + m + "</p></footer></body></html>";
    }

    public static final StubNews INSTANCE = new StubNews();

    private final HttpServer server;
    private final AtomicInteger counter = new AtomicInteger();
    private final AtomicInteger elementCounter = new AtomicInteger();
    /** The {@code /rss/search} requests in arrival order. */
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    /** Every request path the stub receives (any route, also unknown ones), in arrival order; cleared by {@link #reset()}. */
    public final List<String> paths = new CopyOnWriteArrayList<>();
    /** Every request of any route as "&lt;path&gt; &lt;User-Agent header&gt;" in arrival order (FR-54: every hop of a fetch sends the same User-Agent); cleared by {@link #reset()}. */
    public final List<String> userAgents = new CopyOnWriteArrayList<>();
    /** Every request of any route in arrival order with its arrival time; cleared by {@link #reset()}. */
    public final List<Arrival> arrivals = new CopyOnWriteArrayList<>();
    /** FR-52: request number to System.nanoTime() right after the answer was written or the connection was dropped. */
    public final Map<Integer, Long> finishedNanos = new ConcurrentHashMap<>();
    private final AtomicInteger open = new AtomicInteger();
    /** Incremented by reset(): a handler that entered before a reset must not touch the counters of the next test. */
    private volatile int epoch;
    private final AtomicInteger maxOpen = new AtomicInteger();
    /** Names of the article pages (/articles/<name>) that were requested, in arrival order. */
    public final List<String> articleRequests = new CopyOnWriteArrayList<>();
    /** When set, every article page request waits until the latch is counted down (at most 60 s). */
    public volatile java.util.concurrent.CountDownLatch articleGate;
    public final Map<String, Page> pages = new ConcurrentHashMap<>();
    public final Map<String, String> sites = new ConcurrentHashMap<>();
    // ---- FR-61 / FR-54: the Google article page, the batchexecute decode and the publisher page ------------------------
    /** id of {@code /rss/articles/<id>} to the answer given instead of the default Google page (after the 302 step). */
    public final Map<String, Page> googlePages = new ConcurrentHashMap<>();
    /** The requests of {@code /rss/articles/<id>} in arrival order (the 302 step and the page step). */
    public final List<GooglePageHit> googlePageRequests = new CopyOnWriteArrayList<>();
    /** When set, every Google page answer (not the 302) waits until the latch is counted down (at most 60 s). */
    public volatile java.util.concurrent.CountDownLatch googlePageGate;
    public volatile long googlePageDelayMs;
    /** id to the URL the decode answer returns instead of {@code baseUrl()/articles/<id>} (any string). */
    public final Map<String, String> decoded = new ConcurrentHashMap<>();
    /** ok | fail (500) | google-host | no-url (200 structured answer with a null URL). */
    public volatile String decodeMode = "ok";
    public volatile long decodeDelayMs;
    public final List<Decode> decodeRequests = new CopyOnWriteArrayList<>();
    public volatile java.util.concurrent.CountDownLatch decodeGate;
    /** Number of decode requests that reached the stub, counted on arrival (before {@link #decodeGate}); {@link #decodeRequests} is filled only after the answer. */
    public final java.util.concurrent.atomic.AtomicInteger decodeArrivals = new java.util.concurrent.atomic.AtomicInteger();
    /** name of {@code /articles/<name>} to the match text M (remembered by {@link #googleLike()} or set by a test). */
    public final Map<String, String> articleText = new ConcurrentHashMap<>();
    public final Map<String, Long> articleDelays = new ConcurrentHashMap<>();
    public volatile long publisherDelayMs;
    /**
     * Body stall: the headers (and the first body byte) are sent on time, the rest of the body only after this many ms. Per route: the Google
     * page of {@code /rss/articles/<id>} (not its 302 step), the decode answer and the publisher page of {@code /articles/<name>}.
     */
    public volatile long googlePageBodyStallMs;
    public volatile long decodeBodyStallMs;
    public volatile long publisherBodyStallMs;
    /** ok | fail (every publisher page answers 503). */
    private volatile String publisherMode = "ok";
    private final AtomicInteger retrievalOpen = new AtomicInteger();
    private final AtomicInteger retrievalMaxOpen = new AtomicInteger();
    /** Answer of GET /rss/search; the default is an empty channel (200), like a Google answer without items. */
    public volatile Function<Request, Reply> responder = defaultResponder();

    private StubNews() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/", this::handleOther);
        server.createContext("/rss/search", this::handleSearch);
        server.createContext("/rss/articles/", this::handleRssArticle);
        server.createContext("/_/DotsSplashUi/data/batchexecute", this::handleDecode);
        server.createContext("/articles/", this::handleArticle);
        server.createContext("/redirect/", this::handleRedirect);
        server.createContext("/redirect-to", this::handleRedirectTo);
        server.createContext("/big/", this::handleBig);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port();
    }

    public static void registerAll(DynamicPropertyRegistry r) {
        registerBaseUrls(r);
    }

    /** Points the Google News client at this stub (NFR-7): a request never reaches the real internet. */
    public static void registerBaseUrls(DynamicPropertyRegistry r) {
        r.add("oracul.news.google.base-url", INSTANCE::baseUrl);
        // FR-61: the batchexecute decode call goes to the stub as well (NFR-7)
        r.add("oracul.news.google.decode-url", () -> INSTANCE.baseUrl() + "/_/DotsSplashUi/data/batchexecute");
        // FR-56: the in-process stub is on the loopback address; exactly this host name is exempt from the address check
        r.add("oracul.news.fetch.allowed-private-hosts", () -> "127.0.0.1");
    }

    public void reset() {
        java.util.concurrent.CountDownLatch gate = articleGate;
        articleGate = null;
        if (gate != null) gate.countDown(); // never leave a handler thread parked
        java.util.concurrent.CountDownLatch pageGate = googlePageGate;
        googlePageGate = null;
        if (pageGate != null) pageGate.countDown();
        java.util.concurrent.CountDownLatch decGate = decodeGate;
        decodeGate = null;
        if (decGate != null) decGate.countDown();
        requests.clear();
        paths.clear();
        userAgents.clear();
        arrivals.clear();
        finishedNanos.clear();
        epoch++;
        open.set(0);
        maxOpen.set(0);
        counter.set(0);
        elementCounter.set(0);
        responder = defaultResponder();
        articleRequests.clear();
        pages.clear();
        sites.clear();
        googlePages.clear();
        googlePageRequests.clear();
        googlePageDelayMs = 0;
        decoded.clear();
        decodeMode = "ok";
        decodeDelayMs = 0;
        decodeRequests.clear();
        decodeArrivals.set(0);
        articleText.clear();
        articleDelays.clear();
        publisherDelayMs = 0;
        googlePageBodyStallMs = 0;
        decodeBodyStallMs = 0;
        publisherBodyStallMs = 0;
        publisherMode = "ok";
        retrievalOpen.set(0);
        retrievalMaxOpen.set(0);
    }

    /** FR-61: the exchanges open right now over {@code /rss/articles/}, the decode route and {@code /articles/}. */
    public int retrievalOpen() {
        return retrievalOpen.get();
    }

    /** FR-61: the most exchanges open at the same time over those three routes since reset (handler entry until finished, delays included). */
    public int retrievalMaxOpen() {
        return retrievalMaxOpen.get();
    }

    /**
     * FR-61: the control modes of the E2E stub as in-process settings (one mode at a time: a call replaces the previous one):
     * ok, empty, down, malformed, rate-limited-once, slow (2000 ms), decode-fail, decode-google-host, publisher-fail, publisher-timeout.
     */
    public void mode(String mode) {
        mode(mode, mode.equals("slow") ? 2000L : 0L, null);
    }

    /** {@code empty-for} + term (searches whose decoded {@code q} contains the term, case-insensitive, answer 0 items). */
    public void mode(String mode, String term) {
        mode(mode, 0L, term);
    }

    /** {@code slow} + ms. */
    public void mode(String mode, long ms) {
        mode(mode, ms, null);
    }

    private void mode(String mode, long ms, String term) {
        responder = googleLike();
        decodeMode = "ok";
        publisherMode = "ok";
        publisherDelayMs = 0;
        switch (mode) {
            case "ok" -> { }
            case "empty" -> responder = req -> rss();
            case "empty-for" -> {
                String t = term.toLowerCase();
                Function<Request, Reply> inner = googleLike();
                responder = req -> req.q().toLowerCase().contains(t) ? rss() : inner.apply(req);
            }
            case "down" -> responder = req -> status(503);
            case "malformed" -> responder = req -> rssBody("<rss><channel><item><title>broken");
            case "rate-limited-once" -> {
                AtomicInteger first = new AtomicInteger();
                Function<Request, Reply> inner = googleLike();
                responder = req -> first.getAndIncrement() == 0 ? tooMany() : inner.apply(req);
            }
            case "slow" -> responder = slow(ms, googleLike());
            case "decode-fail" -> decodeMode = "fail";
            case "decode-google-host" -> decodeMode = "google-host";
            case "publisher-fail" -> publisherMode = "fail";
            case "publisher-timeout" -> publisherDelayMs = 10_000;
            default -> throw new IllegalArgumentException("unknown mode " + mode);
        }
    }

    private static final java.util.regex.Pattern WHEN = java.util.regex.Pattern.compile(" when:\\d+d$");

    /**
     * FR-61, the pure matcher of {@code /rss/search}: after removing a trailing " when:&lt;N&gt;d", a query holding "(" or ")" answers 0
     * items, so does a query with " OR " in which any element has more than one word or starts with a quote; every other query answers items.
     */
    public static boolean answersItems(String q) {
        String t = WHEN.matcher(q == null ? "" : q.trim()).replaceFirst("").trim();
        if (t.contains("(") || t.contains(")")) return false;
        if (t.contains(" OR ")) {
            for (String element : t.split(" OR ")) {
                String e = element.trim();
                if (e.startsWith("\"") || e.split("\\s+").length > 1) return false;
            }
        }
        return true;
    }

    private static String sha1Key(String text) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.substring(0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * FR-61: a responder that answers like the E2E stub: the matcher of {@link #answersItems} first (0 items), else 5 items per element
     * (item 1 "Shared stub article - Reuters" at {@code <base>/rss/articles/shared?utm_source=<n>}, items 2...5 "&lt;e&gt; stub article
     * &lt;key&gt;-&lt;a&gt; - Reuters" at {@code <base>/rss/articles/<key>-<a>}, key = sha1(e)[0..8]); every key-a remembers its element as
     * the match text of the publisher page.
     */
    public static Function<Request, Reply> googleLike() {
        return req -> {
            if (!answersItems(req.q())) return rss();
            String base = INSTANCE.baseUrl();
            String date = pubDate(Instant.now().minus(1, ChronoUnit.DAYS));
            List<String> items = new ArrayList<>();
            for (String e : req.elements()) {
                String key = sha1Key(e);
                for (int a = 1; a <= 5; a++) {
                    String title = a == 1 ? "Shared stub article - Reuters" : e + " stub article " + key + "-" + a + " - Reuters";
                    String link = a == 1 ? base + "/rss/articles/shared?utm_source=" + req.number() : base + "/rss/articles/" + key + "-" + a;
                    if (a > 1) INSTANCE.articleText.put(key + "-" + a, e);
                    String description = "<a href=\"" + link + "\" target=\"_blank\">" + title + "</a>&nbsp;&nbsp;<font color=\"#6f6f6f\">Reuters</font>";
                    items.add(rssItem(title, link, date, "Reuters", "https://www.reuters.com", description));
                }
            }
            return rss(items.toArray(String[]::new));
        };
    }


    /** Registers the og:site_name of /articles/<name>; unregistered pages keep "Stub Site". */
    public void site(String name, String siteName) {
        sites.put(name, siteName);
    }

    /** The {@code /rss/search} exchanges open right now (handler entry until the answer was written); 0 = nothing in flight. */
    public int open() {
        return open.get();
    }

    /** FR-52: the most {@code /rss/search} exchanges open at the same time since reset (open = handler entry until finished, incl. the reply delay). */
    public int maxOpen() {
        return maxOpen.get();
    }

    private static final java.util.regex.Pattern STUB_QUERY = java.util.regex.Pattern.compile("^W(\\d+) stub query (\\d+)$");

    /** {pipeline number, query number within the pipeline} of the stub query text "W&lt;nn&gt; stub query &lt;i&gt;" the request carries, else null. */
    public static int[] stubQuery(Request req) {
        if (req.elements().size() != 1) return null;
        java.util.regex.Matcher m = STUB_QUERY.matcher(req.elements().get(0));
        return m.matches() ? new int[] {Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2))} : null;
    }

    /**
     * FR-53 fixture shape: the FIRST query of pipeline j ("W&lt;jj&gt; stub query 1") answers {@code itemsPerPipeline.get(j-1)} (RSS item
     * XML), every other query answers an empty feed. So every pipeline holds exactly the items given for it.
     */
    public static Function<Request, Reply> firstQueryItems(List<List<String>> itemsPerPipeline) {
        return req -> {
            int[] q = stubQuery(req);
            if (q == null || q[1] != 1 || q[0] > itemsPerPipeline.size()) return rss();
            return rss(itemsPerPipeline.get(q[0] - 1).toArray(String[]::new));
        };
    }

    /** A Google answer of HTTP 429 (text/plain "Too Many Requests"). */
    public static Reply tooMany() {
        return new Reply(429, "text/plain", "Too Many Requests", 0);
    }

    /** The first request of each distinct {@code q} gets 429, later requests of that {@code q} get {@code then}. */
    public static Function<Request, Reply> rateLimitedOnce(Function<Request, Reply> then) {
        java.util.Set<String> seen = ConcurrentHashMap.newKeySet();
        return req -> seen.add(req.q()) ? tooMany() : then.apply(req);
    }

    /** The reply of {@code then}, delayed by {@code ms}. */
    public static Function<Request, Reply> slow(long ms, Function<Request, Reply> then) {
        return req -> {
            Reply r = then.apply(req);
            return new Reply(r.status(), r.contentType(), r.body(), ms);
        };
    }

    private void arrive(HttpExchange ex) {
        String path = ex.getRequestURI().getPath();
        paths.add(path);
        userAgents.add(path + " " + ex.getRequestHeaders().getFirst("User-Agent"));
        arrivals.add(new Arrival(path, System.nanoTime()));
    }

    public static Reply status(int status) {
        return new Reply(status, "application/json", "", 0);
    }

    public static Reply drop() {
        return new Reply(0, "application/json", "", 0);
    }

    public static Function<Request, Reply> defaultResponder() {
        return req -> rss();
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

    /** Like {@link #rssItem(String, String, String, String, String)} plus a {@code <description>} (HTML text, escaped as XML text; null: no element). */
    public static String rssItem(String title, String link, String pubDate, String source, String sourceUrl, String description) {
        String base = rssItem(title, link, pubDate, source, sourceUrl);
        if (description == null) return base;
        return base.substring(0, base.length() - "</item>".length()) + "<description>" + xml(description) + "</description></item>";
    }

    /** RFC 1123 pubDate of an instant, e.g. "Sat, 03 Oct 2026 10:00:00 GMT". */
    public static String pubDate(Instant t) {
        return DateTimeFormatter.RFC_1123_DATE_TIME.format(t.truncatedTo(ChronoUnit.SECONDS).atOffset(ZoneOffset.UTC));
    }

    private static String xml(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    public static String html(String name) {
        return html(name, "Stub Site");
    }

    public static String html(String name, String siteName) {
        return "<html><head><meta property=\"og:site_name\" content=\"" + siteName + "\"><meta property=\"og:description\" content=\"Summary of "
            + name + "\"></head><body>x</body></html>";
    }

    /** Any path no route handles (answers 404); recorded so that a request to a removed route is visible. */
    private void handleOther(HttpExchange ex) throws IOException {
        arrive(ex);
        send(ex, 404, "text/plain", "not found".getBytes(StandardCharsets.UTF_8));
    }

    private void handleSearch(HttpExchange ex) throws IOException {
        arrive(ex);
        int myEpoch = epoch;
        int now = open.incrementAndGet();
        maxOpen.accumulateAndGet(now, Math::max);
        int number = -1;
        boolean counted = true;
        try {
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
            List<String> elements = elementsOf(q);
            int first = elementCounter.getAndAdd(elements.size()) + 1;
            Request req = new Request(counter.incrementAndGet(), raw, params, q, elements, first, headers, System.nanoTime());
            number = req.number();
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
            // no longer "open" once the client can see the answer (it may release its permit and send the next request at once)
            if (myEpoch == epoch) {
                open.decrementAndGet();
                counted = false;
            }
            if (reply.status() == 0) {
                ex.close();
                return;
            }
            send(ex, reply.status(), reply.contentType(), reply.body().getBytes(StandardCharsets.UTF_8));
        } finally {
            if (myEpoch == epoch) {
                if (number > 0) finishedNanos.put(number, System.nanoTime());
                if (counted) open.decrementAndGet();
            }
        }
    }

    /** Elements of a decoded Google query "(<e1> OR <e2>) when:7d" / "<e1> when:7d", unquoted. */
    static List<String> elementsOf(String q) {
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

    private void enterRetrieval() {
        retrievalMaxOpen.accumulateAndGet(retrievalOpen.incrementAndGet(), Math::max);
    }

    private void leaveRetrieval(int myEpoch) {
        if (myEpoch == epoch) retrievalOpen.decrementAndGet();
    }

    /** One retrieval exchange: it stops counting as open once the client can see the answer (it may release its permit and send the next request at once). */
    private final class Exchange {
        private final int myEpoch = epoch;
        private boolean left;

        Exchange() {
            enterRetrieval();
        }

        void leave() {
            if (!left) {
                left = true;
                leaveRetrieval(myEpoch);
            }
        }

        int epoch() {
            return myEpoch;
        }
    }

    private static void awaitGate(java.util.concurrent.CountDownLatch gate) {
        if (gate == null) return;
        try {
            gate.await(60, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long ms) {
        if (ms <= 0) return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * FR-61 /rss/articles/&lt;id&gt;: without the query parameter {@code hl} a 302 to the same path with {@code &hl=en-US&gl=US&ceid=US:en}
     * appended, with it the Google page carrying {@code data-n-a-id / -ts / -sg} (or the {@link #googlePages} override).
     */
    private void handleRssArticle(HttpExchange ex) throws IOException {
        arrive(ex);
        Exchange x = new Exchange();
        try {
            String rawPath = ex.getRequestURI().getRawPath();
            String id = rawPath.substring("/rss/articles/".length());
            String raw = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
            boolean hasHl = false;
            for (String pair : raw.split("&")) {
                if (pair.equals("hl") || pair.startsWith("hl=")) hasHl = true;
            }
            googlePageRequests.add(new GooglePageHit(id, raw, !hasHl));
            if (!hasHl) {
                ex.getResponseHeaders().add("Location", rawPath + "?" + (raw.isEmpty() ? "" : raw + "&") + "hl=en-US&gl=US&ceid=US:en");
                x.leave();
                ex.sendResponseHeaders(302, -1);
                ex.close();
                return;
            }
            awaitGate(googlePageGate);
            sleep(googlePageDelayMs);
            Page page = googlePages.get(id);
            x.leave();
            if (page != null) {
                send(ex, page.status(), page.contentType(), page.body().getBytes(StandardCharsets.UTF_8), googlePageBodyStallMs);
                return;
            }
            send(ex, 200, "text/html; charset=utf-8", googlePage(id, "1759737600", "sig-" + id).getBytes(StandardCharsets.UTF_8), googlePageBodyStallMs);
        } finally {
            x.leave();
        }
    }

    /** FR-61 batchexecute: form field f.req (JSON) holds id, ts and sg at entries 2, 3 and 4 of its inner array. */
    private void handleDecode(HttpExchange ex) throws IOException {
        arrive(ex);
        Exchange x = new Exchange();
        try {
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String fReq = null;
            for (String pair : body.split("&")) {
                if (pair.startsWith("f.req=")) fReq = URLDecoder.decode(pair.substring("f.req=".length()), StandardCharsets.UTF_8);
            }
            String id = null;
            String ts = null;
            String sg = null;
            try {
                String inner = com.jayway.jsonpath.JsonPath.read(fReq, "$[0][0][1]");
                Object i = com.jayway.jsonpath.JsonPath.read(inner, "$[2]");
                Object t = com.jayway.jsonpath.JsonPath.read(inner, "$[3]");
                Object g = com.jayway.jsonpath.JsonPath.read(inner, "$[4]");
                id = i == null ? null : String.valueOf(i);
                ts = t == null ? null : String.valueOf(t);
                sg = g == null ? null : String.valueOf(g);
            } catch (RuntimeException e) {
                // unparsable: answered 400 below, the values stay null
            }
            boolean valid = id != null && ts != null && sg != null && ts.matches("\\d+") && sg.equals("sig-" + id);
            if (x.epoch() == epoch) decodeArrivals.incrementAndGet();
            awaitGate(decodeGate);
            sleep(decodeDelayMs);
            int status;
            String reply;
            String contentType = "application/json;charset=utf-8";
            if (!valid) {
                status = 400;
                reply = "bad request";
                contentType = "text/plain";
            } else if ("fail".equals(decodeMode)) {
                status = 500;
                reply = "decode failed";
                contentType = "text/plain";
            } else {
                status = 200;
                String url;
                if ("google-host".equals(decodeMode)) url = "https://news.google.com/rss/articles/" + id;
                else if ("no-url".equals(decodeMode)) url = null;
                else url = decoded.getOrDefault(id, baseUrl() + "/articles/" + id);
                String urlJson = url == null ? "null" : "\\\"" + url.replace("\\", "\\\\\\\\").replace("\"", "\\\\\\\"") + "\\\"";
                reply = ")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",\"[\\\"garturlres\\\"," + urlJson + ",1]\",null,null,null,\"generic\"]]";
            }
            if (x.epoch() == epoch) {
                decodeRequests.add(new Decode(id, ts, sg, ex.getRequestHeaders().getFirst("Content-Type"),
                    ex.getRequestHeaders().getFirst("User-Agent"), fReq, status));
            }
            x.leave();
            send(ex, status, contentType, reply.getBytes(StandardCharsets.UTF_8), decodeBodyStallMs);
        } finally {
            x.leave();
        }
    }

    private void handleArticle(HttpExchange ex) throws IOException {
        arrive(ex);
        Exchange x = new Exchange();
        try {
            String name = ex.getRequestURI().getPath().substring("/articles/".length());
            articleRequests.add(name);
            awaitGate(articleGate);
            sleep(publisherDelayMs);
            Long delay = articleDelays.get(name);
            if (delay != null) sleep(delay);
            Page page = pages.get(name);
            if ("slow".equals(name) && page == null && !"fail".equals(publisherMode)) sleep(3000);
            x.leave();
            if (page != null) {
                send(ex, page.status(), page.contentType(), page.body().getBytes(StandardCharsets.UTF_8), publisherBodyStallMs);
                return;
            }
            if ("fail".equals(publisherMode)) {
                send(ex, 503, "text/plain", "unavailable".getBytes(StandardCharsets.UTF_8), publisherBodyStallMs);
                return;
            }
            if ("pdf".equals(name)) {
                send(ex, 200, "application/pdf", "%PDF-1.4 stub".getBytes(StandardCharsets.UTF_8), publisherBodyStallMs);
                return;
            }
            send(ex, 200, "text/html; charset=utf-8",
                publisherPage(name, articleText.get(name), sites.getOrDefault(name, "Stub Site")).getBytes(StandardCharsets.UTF_8), publisherBodyStallMs);
        } finally {
            x.leave();
        }
    }

    /** /redirect/N answers 302 to /redirect/N-1; /redirect/0 is an article page "redirected". */
    private void handleRedirect(HttpExchange ex) throws IOException {
        arrive(ex);
        int n = Integer.parseInt(ex.getRequestURI().getPath().substring("/redirect/".length()));
        if (n <= 0) {
            send(ex, 200, "text/html", html("redirected").getBytes(StandardCharsets.UTF_8));
            return;
        }
        ex.getResponseHeaders().add("Location", "/redirect/" + (n - 1));
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    /** /redirect-to?location=<url-encoded> answers 302 with that Location verbatim (FR-56 cases: any scheme, host, port). */
    private void handleRedirectTo(HttpExchange ex) throws IOException {
        arrive(ex);
        String raw = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
        String location = "";
        for (String pair : raw.split("&")) {
            if (pair.startsWith("location=")) {
                location = URLDecoder.decode(pair.substring("location=".length()), StandardCharsets.UTF_8);
            }
        }
        ex.getResponseHeaders().add("Location", location);
        ex.sendResponseHeaders(302, -1);
        ex.close();
    }

    /**
     * /big/&lt;bytes&gt;?meta-at=&lt;offset&gt; answers 200 text/html of exactly &lt;bytes&gt; bytes with
     * {@code <meta property="og:description" content="Big page text">} starting at byte &lt;offset&gt; (default 0).
     */
    private void handleBig(HttpExchange ex) throws IOException {
        arrive(ex);
        int size = Integer.parseInt(ex.getRequestURI().getPath().substring("/big/".length()));
        int offset = 0;
        String raw = ex.getRequestURI().getRawQuery() == null ? "" : ex.getRequestURI().getRawQuery();
        for (String pair : raw.split("&")) {
            if (pair.startsWith("meta-at=")) {
                offset = Integer.parseInt(pair.substring("meta-at=".length()));
            }
        }
        byte[] out = new byte[size];
        java.util.Arrays.fill(out, (byte) 'x');
        byte[] meta = "<meta property=\"og:description\" content=\"Big page text\">".getBytes(StandardCharsets.UTF_8);
        if (offset + meta.length <= size) {
            System.arraycopy(meta, 0, out, offset, meta.length);
        }
        send(ex, 200, "text/html; charset=utf-8", out);
    }

    private static void send(HttpExchange ex, int status, String contentType, byte[] out) throws IOException {
        send(ex, status, contentType, out, 0);
    }

    /** As above; with {@code stallMs} > 0 the headers and the first byte go out at once, the rest of the body after the stall. */
    private static void send(HttpExchange ex, int status, String contentType, byte[] out, long stallMs) throws IOException {
        if (contentType != null) ex.getResponseHeaders().add("Content-Type", contentType);
        try {
            ex.sendResponseHeaders(status, out.length == 0 ? -1 : out.length);
            if (out.length > 0 && stallMs > 0 && out.length > 1) {
                ex.getResponseBody().write(out, 0, 1);
                ex.getResponseBody().flush();
                sleep(stallMs);
                ex.getResponseBody().write(out, 1, out.length - 1);
            } else if (out.length > 0) {
                ex.getResponseBody().write(out);
            }
        } catch (IOException ignored) {
            // client gave up
        } finally {
            ex.close();
        }
    }
}
