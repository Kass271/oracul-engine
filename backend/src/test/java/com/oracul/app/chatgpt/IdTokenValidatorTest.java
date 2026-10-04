package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// @trace FR-36
class IdTokenValidatorTest {

    private static final Instant NOW = Instant.parse("2026-06-01T00:00:00Z");
    private static final String ISS = "https://issuer.example";
    private HttpServer server;
    private KeyPair pair;
    private volatile String jwks;
    private IdTokenValidator validator;

    private static String b64(String s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String b64(byte[] s) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(s);
    }

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
        g.initialize(2048);
        pair = g.generateKeyPair();
        RSAPublicKey pub = (RSAPublicKey) pair.getPublic();
        jwks = "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"k1\",\"n\":\"" + b64(pub.getModulus().toByteArray().length > 256
            ? java.util.Arrays.copyOfRange(pub.getModulus().toByteArray(), 1, 257) : pub.getModulus().toByteArray())
            + "\",\"e\":\"" + b64(pub.getPublicExponent().toByteArray()) + "\"}]}";
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] b = jwks.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(200, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/jwks";
        validator = new IdTokenValidator(ChatGptTestProps.of(url, url, url), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown() {
        Thread.interrupted();
        server.stop(0);
    }

    private String token(String header, String claims) throws Exception {
        String signed = b64(header) + "." + b64(claims);
        Signature s = Signature.getInstance("SHA256withRSA");
        s.initSign(pair.getPrivate());
        s.update(signed.getBytes(StandardCharsets.US_ASCII));
        return signed + "." + b64(s.sign());
    }

    private static final String HDR = "{\"alg\":\"RS256\",\"kid\":\"k1\"}";

    private static String claims(String iss, String aud, String exp, String nonce) {
        StringBuilder sb = new StringBuilder("{");
        if (iss != null) sb.append("\"iss\":\"").append(iss).append("\",");
        if (aud != null) sb.append("\"aud\":").append(aud).append(',');
        if (exp != null) sb.append("\"exp\":").append(exp).append(',');
        if (nonce != null) sb.append("\"nonce\":\"").append(nonce).append("\",");
        sb.append("\"x\":1}");
        return sb.toString();
    }

    private static final String FUTURE = String.valueOf(NOW.getEpochSecond() + 600);

    @Test
    void validTokenPasses() throws Exception {
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", FUTURE, "n1")), "cid", "n1")).isTrue();
        assertThat(validator.isValid(token(HDR, claims(ISS, "[\"zz\",\"cid\"]", FUTURE, "n1")), "cid", "n1")).isTrue();
    }

    @Test
    void invalidClaimsAreRejected() throws Exception {
        assertThat(validator.isValid(token(HDR, claims("https://evil", "\"cid\"", FUTURE, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(null, "\"cid\"", FUTURE, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"other\"", FUTURE, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "[\"other\"]", FUTURE, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, null, FUTURE, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", null, "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", "\"soon\"", "n1")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", String.valueOf(NOW.getEpochSecond()), "n1")),
            "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", FUTURE, "bad")), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, claims(ISS, "\"cid\"", FUTURE, null)), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, "[1]"), "cid", "n1")).isFalse();
    }

    @Test
    void invalidHeadersAndShapesAreRejected() throws Exception {
        String c = claims(ISS, "\"cid\"", FUTURE, "n1");
        assertThat(validator.isValid(token("{\"alg\":\"none\",\"kid\":\"k1\"}", c), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token("{\"alg\":\"RS256\"}", c), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token("{\"alg\":\"RS256\",\"kid\":\"\"}", c), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token("[1]", c), "cid", "n1")).isFalse();
        assertThat(validator.isValid(token("{\"alg\":\"RS256\",\"kid\":\"unknown\"}", c), "cid", "n1")).isFalse();
        assertThat(validator.isValid(null, "cid", "n1")).isFalse();
        assertThat(validator.isValid(token(HDR, c), "cid", null)).isFalse();
        assertThat(validator.isValid("a.b", "cid", "n1")).isFalse();
        assertThat(validator.isValid("a..c", "cid", "n1")).isFalse();
        assertThat(validator.isValid("!!.??.**", "cid", "n1")).isFalse();
        String t = token(HDR, c);
        assertThat(validator.isValid(t.substring(0, t.lastIndexOf('.') + 1) + b64("badsig"), "cid", "n1")).isFalse();
    }

    @Test
    void nonRsaOrMalformedJwksYieldsNoKey() throws Exception {
        String t = token(HDR, claims(ISS, "\"cid\"", FUTURE, "n1"));
        for (String body : new String[] {"{\"keys\":[{\"kty\":\"EC\",\"kid\":\"k1\"}]}", "{\"keys\":\"x\"}", "[1]",
            "{\"keys\":[1,{\"kty\":\"RSA\",\"kid\":\"k1\"}]}", "nonsense"}) {
            jwks = body;
            IdTokenValidator v = new IdTokenValidator(
                ChatGptTestProps.of("http://x", "http://127.0.0.1:" + server.getAddress().getPort() + "/j", "http://x"),
                Clock.fixed(NOW, ZoneOffset.UTC));
            assertThat(v.isValid(t, "cid", "n1")).as(body).isFalse();
        }
    }

    @Test
    void interruptedJwksFetchReturnsFalseAndRestoresFlag() throws Exception {
        String t = token(HDR, claims(ISS, "\"cid\"", FUTURE, "n1"));
        Thread.currentThread().interrupt();
        assertThat(validator.isValid(t, "cid", "n1")).isFalse();
        assertThat(Thread.interrupted()).isTrue();
    }
}
