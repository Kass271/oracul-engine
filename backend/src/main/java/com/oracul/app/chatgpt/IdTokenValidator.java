package com.oracul.app.chatgpt;

import java.math.BigInteger;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPublicKeySpec;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Validates the OpenID ID token: RS256 signature vs the issuer's JWKS, iss, aud, exp and nonce. */
@Component
class IdTokenValidator {

    private static final Logger log = LoggerFactory.getLogger(IdTokenValidator.class);

    private final ChatGptProperties props;
    private final Clock clock;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private Map<String, PublicKey> cache = Map.of();
    private Instant fetchedAt = Instant.MIN;

    IdTokenValidator(ChatGptProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        this.http = HttpClient.newBuilder().connectTimeout(props.httpTimeout())
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** True only when every check passes; never throws. */
    boolean isValid(String idToken, String audience, String expectedNonce) {
        try {
            return validate(idToken, audience, expectedNonce);
        } catch (Exception e) {
            log.warn("chatgpt id token invalid: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    private boolean validate(String idToken, String audience, String expectedNonce) throws Exception {
        if (idToken == null || expectedNonce == null) {
            return false;
        }
        String[] parts = idToken.split("\\.", -1);
        if (parts.length != 3 || parts[0].isEmpty() || parts[1].isEmpty() || parts[2].isEmpty()) {
            return false;
        }
        Base64.Decoder dec = Base64.getUrlDecoder();
        Map<?, ?> header = object(new String(dec.decode(parts[0]), StandardCharsets.UTF_8));
        if (header == null || !"RS256".equals(header.get("alg"))
            || !(header.get("kid") instanceof String kid) || kid.isEmpty()) {
            return false;
        }
        PublicKey key = key(kid);
        if (key == null) {
            return false;
        }
        Signature sig = Signature.getInstance("SHA256withRSA");
        sig.initVerify(key);
        sig.update((parts[0] + "." + parts[1]).getBytes(StandardCharsets.US_ASCII));
        if (!sig.verify(dec.decode(parts[2]))) {
            return false;
        }
        Map<?, ?> claims = object(new String(dec.decode(parts[1]), StandardCharsets.UTF_8));
        if (claims == null || !props.issuer().equals(claims.get("iss"))) {
            return false;
        }
        Object aud = claims.get("aud");
        boolean audOk = audience.equals(aud) || (aud instanceof List<?> l && l.contains(audience));
        if (!audOk || !(claims.get("exp") instanceof Number exp)
            || exp.doubleValue() <= clock.instant().getEpochSecond()) {
            return false;
        }
        return expectedNonce.equals(claims.get("nonce"));
    }

    private Map<?, ?> object(String text) {
        Object v = json.readValue(text, Object.class);
        return v instanceof Map<?, ?> m ? m : null;
    }

    private synchronized PublicKey key(String kid) {
        Instant now = clock.instant();
        boolean fresh = fetchedAt.plus(props.jwksCacheTtl()).isAfter(now);
        if (fresh && cache.containsKey(kid)) {
            return cache.get(kid);
        }
        Map<String, PublicKey> fetched = fetch();
        if (fetched != null) {
            cache = fetched;
            fetchedAt = now;
        }
        return fetched == null ? null : fetched.get(kid);
    }

    private Map<String, PublicKey> fetch() {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.jwksUrl()))
                .timeout(props.httpTimeout()).header("Accept", "application/json").GET().build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("chatgpt jwks request failed: status={}", response.statusCode());
                return null;
            }
            Map<?, ?> body = object(response.body());
            if (body == null || !(body.get("keys") instanceof List<?> keys)) {
                return null;
            }
            Map<String, PublicKey> out = new HashMap<>();
            KeyFactory rsa = KeyFactory.getInstance("RSA");
            for (Object k : keys) {
                if (k instanceof Map<?, ?> m && "RSA".equals(m.get("kty")) && m.get("kid") instanceof String id
                    && m.get("n") instanceof String n && m.get("e") instanceof String e) {
                    Base64.Decoder dec = Base64.getUrlDecoder();
                    out.put(id, rsa.generatePublic(new RSAPublicKeySpec(
                        new BigInteger(1, dec.decode(n)), new BigInteger(1, dec.decode(e)))));
                }
            }
            return out.isEmpty() ? null : out;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        } catch (Exception e) {
            log.warn("chatgpt jwks request failed: {}", e.getClass().getSimpleName());
            return null;
        }
    }
}
