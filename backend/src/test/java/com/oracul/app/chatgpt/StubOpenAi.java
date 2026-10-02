package com.oracul.app.chatgpt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.test.context.DynamicPropertyRegistry;

/** In-process stand-in for the OpenAI token endpoint (NFR-7). One instance per JVM, reset before every test. */
public final class StubOpenAi {

    public static final String ALL_SCOPES =
        "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";

    public record TokenRequest(Map<String, String> form, String contentType, String accept) {}

    public record Reply(int status, String body, long delayMs) {}

    public static final StubOpenAi INSTANCE = new StubOpenAi();

    private final HttpServer server;
    private final AtomicInteger counter = new AtomicInteger();
    public final List<TokenRequest> requests = new CopyOnWriteArrayList<>();
    /** Every code/token value this stub ever issued (for secret scans). */
    public final List<String> issued = new CopyOnWriteArrayList<>();
    public volatile Function<TokenRequest, Reply> responder = defaultResponder();

    private StubOpenAi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/oauth/token", this::handle);
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String tokenUrl() {
        return "http://127.0.0.1:" + port() + "/oauth/token";
    }

    String authorizeUrl() {
        return "http://localhost:" + port() + "/oauth/authorize";
    }

    static void registerTokenUrl(DynamicPropertyRegistry r) {
        r.add("oracul.chatgpt.token-url", INSTANCE::tokenUrl);
    }

    public static void registerAll(DynamicPropertyRegistry r) {
        registerTokenUrl(r);
        r.add("oracul.chatgpt.authorize-url", INSTANCE::authorizeUrl);
    }

    public void reset() {
        requests.clear();
        issued.clear();
        responder = defaultResponder();
    }

    int count() {
        return counter.incrementAndGet();
    }

    private Function<TokenRequest, Reply> defaultResponder() {
        return req -> ok(3600, ALL_SCOPES, true);
    }

    /** 200 with STUBSECRET tokens. scope null = property absent. */
    public Reply ok(Integer expiresIn, String scope, boolean refresh) {
        int n = count();
        StringBuilder sb = new StringBuilder("{\"access_token\":\"at-STUBSECRET-" + n + "\"");
        issued.add("at-STUBSECRET-" + n);
        if (refresh) {
            sb.append(",\"refresh_token\":\"rt-STUBSECRET-").append(n).append("\"");
            issued.add("rt-STUBSECRET-" + n);
        }
        sb.append(",\"id_token\":\"id-STUBSECRET-").append(n).append("\"");
        issued.add("id-STUBSECRET-" + n);
        sb.append(",\"token_type\":\"Bearer\"");
        if (expiresIn != null) sb.append(",\"expires_in\":").append(expiresIn);
        if (scope != null) sb.append(",\"scope\":\"").append(scope).append("\"");
        sb.append("}");
        return new Reply(200, sb.toString(), 0);
    }

    public static Reply status(int status, String body) {
        return new Reply(status, body, 0);
    }

    public List<TokenRequest> grant(String grantType) {
        return requests.stream().filter(r -> grantType.equals(r.form().get("grant_type"))).toList();
    }

    private void handle(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            String k = URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8);
            String v = i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8);
            form.put(k, v);
        }
        TokenRequest req = new TokenRequest(
            form, ex.getRequestHeaders().getFirst("Content-Type"), ex.getRequestHeaders().getFirst("Accept"));
        requests.add(req);
        Reply reply = responder.apply(req);
        try {
            if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        try {
            ex.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) ex.getResponseBody().write(out);
        } catch (IOException ignored) {
            // client already timed out
        } finally {
            ex.close();
        }
    }
}
