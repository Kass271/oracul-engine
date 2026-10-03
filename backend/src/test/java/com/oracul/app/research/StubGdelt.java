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

    /** A GDELT request: arrival number (1-based), raw query string, decoded parameters. */
    public record Request(int number, String rawQuery, Map<String, String> params) {}

    /** status 0 = drop the connection without answering. */
    public record Reply(int status, String contentType, String body, long delayMs) {}

    /** A custom article page for /articles/<name>. */
    public record Page(int status, String contentType, String body) {}

    public static final StubGdelt INSTANCE = new StubGdelt();
    public static final DateTimeFormatter SEENDATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'").withZone(ZoneOffset.UTC);

    private final HttpServer server;
    private final AtomicInteger counter = new AtomicInteger();
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public final Map<String, Page> pages = new ConcurrentHashMap<>();
    public volatile Function<Request, Reply> responder = defaultResponder();

    private StubGdelt() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/api/v2/doc/doc", this::handleSearch);
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
    }

    public void reset() {
        requests.clear();
        pages.clear();
        counter.set(0);
        responder = defaultResponder();
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
        return "<html><head><meta property=\"og:site_name\" content=\"Stub Site\"><meta property=\"og:description\" content=\"Summary of "
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
        Request req = new Request(counter.incrementAndGet(), raw, params);
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

    private void handleArticle(HttpExchange ex) throws IOException {
        String name = ex.getRequestURI().getPath().substring("/articles/".length());
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
        send(ex, 200, "text/html; charset=utf-8", html(name).getBytes(StandardCharsets.UTF_8));
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
