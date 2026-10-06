package com.oracul.app.chatgpt;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.test.context.DynamicPropertyRegistry;
import tools.jackson.databind.json.JsonMapper;

/** In-process stand-in for the OpenAI token endpoint (NFR-7). One instance per JVM, reset before every test. */
public final class StubOpenAi {

    public static final String ALL_SCOPES =
        "openid profile email offline_access resource.invoke chatgpt.tokens.use.direct";

    public record TokenRequest(Map<String, String> form, String contentType, String accept) {}

    public record Reply(int status, String body, long delayMs) {}

    /** One call of the revocation endpoint (kept apart from the token requests). */
    public record Revocation(Map<String, String> form, String contentType) {}

    public static final String KID = "stub-key-1";

    public static final StubOpenAi INSTANCE = new StubOpenAi();

    private final HttpServer server;
    private final AtomicInteger counter = new AtomicInteger();
    public final List<TokenRequest> requests = new CopyOnWriteArrayList<>();
    /** Every code/token value this stub ever issued (for secret scans). */
    public final List<String> issued = new CopyOnWriteArrayList<>();
    public volatile Function<TokenRequest, Reply> responder = defaultResponder();

    /** Revocation calls (spec FR-37 fixtures): separate list so token-request counts stay. */
    public final List<Revocation> revocations = new CopyOnWriteArrayList<>();
    public volatile Function<Revocation, Reply> revokeResponder = r -> new Reply(200, "", 0);
    /** Number of GET /jwks calls since the last reset. */
    public final java.util.concurrent.atomic.AtomicInteger jwksRequests = new java.util.concurrent.atomic.AtomicInteger();
    /** null = the default JWKS (one RSA key, kid stub-key-1). */
    public volatile Reply jwksReply;

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ThreadLocal<TokenRequest> CURRENT = new ThreadLocal<>();
    /** The signing key whose public half is served at /jwks. */
    public final KeyPair keyPair = newKeyPair();
    /** code -> nonce of the attempt that code belongs to (cleared on reset). */
    public final Map<String, String> nonceByCode = new ConcurrentHashMap<>();
    /** state -> nonce of the authorize attempt (never cleared; states are unique). */
    public final Map<String, String> nonceByState = new ConcurrentHashMap<>();

    private StubOpenAi() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
        server.createContext("/oauth/token", this::handle);
        server.createContext("/jwks", this::handleJwks);
        server.createContext("/oauth/revoke", this::handleRevoke);
        server.start();
    }

    int port() {
        return server.getAddress().getPort();
    }

    String tokenUrl() {
        return "http://127.0.0.1:" + port() + "/oauth/token";
    }

    String jwksUrl() {
        return "http://127.0.0.1:" + port() + "/jwks";
    }

    String issuer() {
        return "http://127.0.0.1:" + port();
    }

    String revocationUrl() {
        return "http://127.0.0.1:" + port() + "/oauth/revoke";
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
        r.add("oracul.chatgpt.jwks-url", INSTANCE::jwksUrl);
        r.add("oracul.chatgpt.issuer", INSTANCE::issuer);
        r.add("oracul.chatgpt.revocation-url", INSTANCE::revocationUrl);
        com.oracul.app.research.StubResponses.registerAll(r);
        com.oracul.app.research.StubNews.registerAll(r);
    }

    public void reset() {
        com.oracul.app.research.StubResponses.INSTANCE.reset();
        com.oracul.app.research.StubNews.INSTANCE.reset();
        requests.clear();
        revocations.clear();
        issued.clear();
        nonceByCode.clear();
        jwksRequests.set(0);
        jwksReply = null;
        responder = defaultResponder();
        revokeResponder = r -> new Reply(200, "", 0);
    }

    /** Test helper: the callback with this code belongs to the attempt with this nonce. */
    public void expectNonce(String code, String nonce) {
        if (code != null && nonce != null) nonceByCode.put(code, nonce);
    }

    // ---- ID tokens ----

    public static KeyPair newKeyPair() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            return g.generateKeyPair();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public String issuerValue() {
        return issuer();
    }

    /** Header of a valid ID token. */
    public Map<String, Object> validHeader() {
        Map<String, Object> h = new LinkedHashMap<>();
        h.put("alg", "RS256");
        h.put("typ", "JWT");
        h.put("kid", KID);
        return h;
    }

    /** Claims of a valid ID token for the given exchange client id and attempt nonce. */
    public Map<String, Object> validClaims(String clientId, String nonce) {
        long now = Instant.now().getEpochSecond();
        Map<String, Object> c = new LinkedHashMap<>();
        c.put("iss", issuer());
        c.put("aud", clientId);
        c.put("sub", "stub-user");
        c.put("exp", now + 3600);
        c.put("iat", now);
        if (nonce != null) c.put("nonce", nonce);
        return c;
    }

    /** Signs header.claims with the given key; alg decides the algorithm (RS256, RS512, HS256, none). */
    public String sign(Map<String, Object> header, Map<String, Object> claims, PrivateKey key) {
        String input = b64(json(header)) + "." + b64(json(claims));
        String alg = String.valueOf(header.get("alg"));
        try {
            byte[] sig;
            switch (alg) {
                case "none" -> sig = new byte[0];
                case "HS256" -> {
                    Mac mac = Mac.getInstance("HmacSHA256");
                    mac.init(new SecretKeySpec("stub-hmac-secret-stub-hmac-secret".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
                    sig = mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
                }
                default -> {
                    Signature s = Signature.getInstance("RS512".equals(alg) ? "SHA512withRSA" : "SHA256withRSA");
                    s.initSign(key);
                    s.update(input.getBytes(StandardCharsets.US_ASCII));
                    sig = s.sign();
                }
            }
            return input + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sig);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A fully valid ID token for this client id and nonce, signed with the served key. */
    public String idToken(String clientId, String nonce) {
        return sign(validHeader(), validClaims(clientId, nonce), keyPair.getPrivate());
    }

    private static String json(Object o) {
        return JSON.writeValueAsString(o);
    }

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    /** Client id and nonce of the token request currently being answered (only valid inside a responder). */
    public String currentClientId() {
        TokenRequest r = CURRENT.get();
        return r == null ? null : r.form().get("client_id");
    }

    public String currentNonce() {
        TokenRequest r = CURRENT.get();
        if (r == null || r.form().get("code") == null) return null;
        return nonceByCode.get(r.form().get("code"));
    }

    private String jwks() {
        RSAPublicKey pub = (RSAPublicKey) keyPair.getPublic();
        Map<String, Object> key = new LinkedHashMap<>();
        key.put("kty", "RSA");
        key.put("kid", KID);
        key.put("use", "sig");
        key.put("alg", "RS256");
        key.put("n", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(pub.getModulus().toByteArray())));
        key.put("e", Base64.getUrlEncoder().withoutPadding().encodeToString(unsigned(pub.getPublicExponent().toByteArray())));
        return json(Map.of("keys", List.of(key)));
    }

    private static byte[] unsigned(byte[] b) {
        if (b.length > 1 && b[0] == 0) return java.util.Arrays.copyOfRange(b, 1, b.length);
        return b;
    }

    int count() {
        return counter.incrementAndGet();
    }

    private Function<TokenRequest, Reply> defaultResponder() {
        return req -> ok(3600, ALL_SCOPES, true);
    }

    /** 200 with STUBSECRET tokens and a valid signed ID token (aud = request client_id, nonce of the attempt). */
    public Reply ok(Integer expiresIn, String scope, boolean refresh) {
        String cid = currentClientId();
        return okWithIdToken(expiresIn, scope, refresh, idToken(cid == null ? "oaiapp_unknown" : cid, currentNonce()));
    }

    /** 200 whose id_token is exactly the given value (null = no id_token property). */
    public Reply okWithIdToken(String idToken) {
        return okWithIdToken(3600, ALL_SCOPES, true, idToken);
    }

    /** 200 with STUBSECRET tokens. scope null = property absent. */
    public Reply okWithIdToken(Integer expiresIn, String scope, boolean refresh, String idToken) {
        int n = count();
        StringBuilder sb = new StringBuilder("{\"access_token\":\"at-STUBSECRET-" + n + "\"");
        issued.add("at-STUBSECRET-" + n);
        if (refresh) {
            sb.append(",\"refresh_token\":\"rt-STUBSECRET-").append(n).append("\"");
            issued.add("rt-STUBSECRET-" + n);
        }
        if (idToken != null) {
            sb.append(",\"id_token\":\"").append(idToken).append("\"");
            issued.add(idToken);
        }
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
        Reply reply;
        CURRENT.set(req);
        try {
            reply = responder.apply(req);
        } finally {
            CURRENT.remove();
        }
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

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : raw.split("&")) {
            if (pair.isEmpty()) continue;
            int i = pair.indexOf('=');
            form.put(URLDecoder.decode(i < 0 ? pair : pair.substring(0, i), StandardCharsets.UTF_8),
                i < 0 ? "" : URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    private void respond(HttpExchange ex, Reply reply) throws IOException {
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

    private void handleJwks(HttpExchange ex) throws IOException {
        jwksRequests.incrementAndGet();
        ex.getRequestBody().readAllBytes();
        Reply custom = jwksReply;
        respond(ex, custom != null ? custom : new Reply(200, jwks(), 0));
    }

    private void handleRevoke(HttpExchange ex) throws IOException {
        String raw = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Revocation rev = new Revocation(parseForm(raw), ex.getRequestHeaders().getFirst("Content-Type"));
        revocations.add(rev);
        respond(ex, revokeResponder.apply(rev));
    }
}
