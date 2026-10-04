package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.PrivateKey;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-36 rule 10: ID-token validation (RS256 signature vs JWKS, iss, aud, exp, nonce) before anything is stored. */
class ChatGptIdTokenIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final PrivateKey OTHER_KEY = StubOpenAi.newKeyPair().getPrivate();

    /** Mutable pieces of the ID token the stub signs for one token request. */
    static final class Ctx {
        final Map<String, Object> header;
        final Map<String, Object> claims;
        PrivateKey key;
        final String clientId;
        final String otherNonce;

        Ctx(Map<String, Object> header, Map<String, Object> claims, PrivateKey key, String clientId, String otherNonce) {
            this.header = header;
            this.claims = claims;
            this.key = key;
            this.clientId = clientId;
            this.otherNonce = otherNonce;
        }
    }

    private static long now() {
        return Instant.now().getEpochSecond();
    }

    private static Arguments c(String name, boolean valid, Consumer<Ctx> mutate) {
        return Arguments.of(name, valid, mutate);
    }

    static Stream<Arguments> classes() {
        return Stream.of(
            c("alg RS256", true, x -> { }),
            // exp is in whole seconds; +2 keeps the "just in the future" class stable on the real clock
            c("exp just in the future", true, x -> x.claims.put("exp", now() + 2)),
            c("aud array containing the client id", true, x -> x.claims.put("aud", List.of("other", x.clientId))),
            c("alg none", false, x -> x.header.put("alg", "none")),
            c("alg HS256", false, x -> x.header.put("alg", "HS256")),
            c("alg RS512", false, x -> x.header.put("alg", "RS512")),
            c("missing kid", false, x -> x.header.remove("kid")),
            c("signed with another key", false, x -> x.key = OTHER_KEY),
            c("iss other", false, x -> x.claims.put("iss", "https://evil.example")),
            c("iss with trailing slash", false, x -> x.claims.put("iss", x.claims.get("iss") + "/")),
            c("iss missing", false, x -> x.claims.remove("iss")),
            c("aud other string", false, x -> x.claims.put("aud", "oaiapp_someone_else")),
            c("aud array without the client id", false, x -> x.claims.put("aud", List.of("x", "y"))),
            c("aud missing", false, x -> x.claims.remove("aud")),
            c("exp now", false, x -> x.claims.put("exp", now())),
            c("exp one second ago", false, x -> x.claims.put("exp", now() - 1)),
            c("exp missing", false, x -> x.claims.remove("exp")),
            c("nonce of another pending attempt", false, x -> x.claims.put("nonce", x.otherNonce)),
            c("nonce missing", false, x -> x.claims.remove("nonce")),
            c("nonce empty", false, x -> x.claims.put("nonce", "")));
    }

    private String registeredClientId() {
        return jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class);
    }

    // @trace FR-36
    @ParameterizedTest(name = "{0} -> valid={1}")
    @MethodSource("classes")
    void idTokenClassesAloneDecideTheOutcome(String name, boolean valid, Consumer<Ctx> mutate) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started other = start(sid);
        Started s = start(sid);
        stub.responder = r -> {
            Ctx x = new Ctx(stub.validHeader(), stub.validClaims(stub.currentClientId(), stub.currentNonce()),
                stub.keyPair.getPrivate(), stub.currentClientId(), other.nonce());
            mutate.accept(x);
            return stub.okWithIdToken(stub.sign(x.header, x.claims, x.key));
        };
        String loc = callbackLocation(returnQuery(s, "c"));
        assertThat(stub.requests).hasSize(1);
        if (valid) {
            assertThat(loc).isEqualTo(CONNECTED);
            assertThat(stateOf(sid)).isEqualTo("CONNECTED");
            assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
        } else {
            assertThat(loc).isEqualTo(NOT_VERIFIED);
            assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
            assertThat(registeredClientId()).isNull();
        }
    }

    // @trace FR-36
    @ParameterizedTest(name = "id_token [{0}]")
    @MethodSource("malformedIdTokens")
    void aMissingOrMalformedIdTokenIsNotVerified(String token) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        stub.responder = r -> stub.okWithIdToken(token);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
    }

    static Stream<String> malformedIdTokens() {
        return Stream.of(null, "", "abc", "a.b", "a.b.c.d", "...", "not.a.jwt");
    }

    private interface TokenEdit {
        String apply(String token);
    }

    private static String[] parts(String token) {
        return token.split("\\.");
    }

    static Stream<Arguments> tampered() {
        TokenEdit payload = t -> {
            String[] p = parts(t);
            String json = new String(Base64.getUrlDecoder().decode(p[1]), StandardCharsets.UTF_8)
                .replace("stub-user", "stub-evil");
            return p[0] + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(json.getBytes(StandardCharsets.UTF_8))
                + "." + p[2];
        };
        TokenEdit signature = t -> {
            String[] p = parts(t);
            return p[0] + "." + p[1] + "." + (p[2].charAt(0) == 'A' ? 'B' : 'A') + p[2].substring(1);
        };
        return Stream.of(Arguments.of("tampered payload", payload), Arguments.of("tampered signature", signature));
    }

    // @trace FR-36
    @ParameterizedTest(name = "{0}")
    @MethodSource("tampered")
    void aTamperedIdTokenIsNotVerified(String name, TokenEdit edit) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        stub.responder = r ->
            stub.okWithIdToken(edit.apply(stub.idToken(stub.currentClientId(), stub.currentNonce())));
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
    }

    private void respondWithKid(String kid) {
        stub.responder = r -> {
            var h = stub.validHeader();
            h.put("kid", kid);
            return stub.okWithIdToken(stub.sign(h, stub.validClaims(stub.currentClientId(), stub.currentNonce()),
                stub.keyPair.getPrivate()));
        };
    }

    // @trace FR-36
    @Test
    void anUnknownKidTriggersExactlyOneJwksRefetchAndThenFails() throws Exception {
        connect(newSid()); // warms the JWKS cache
        stub.jwksRequests.set(0);
        respondWithKid("kid-that-the-jwks-does-not-have");
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stub.jwksRequests.get()).isEqualTo(1);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    static Stream<Arguments> brokenJwks() {
        return Stream.of(
            Arguments.of("500", new StubOpenAi.Reply(500, "{\"error\":\"boom\"}", 0)),
            Arguments.of("not JSON", new StubOpenAi.Reply(200, "this is not json", 0)),
            Arguments.of("no keys", new StubOpenAi.Reply(200, "{\"keys\":[]}", 0)),
            Arguments.of("wrong shape", new StubOpenAi.Reply(200, "{\"keys\":\"x\"}", 0)));
    }

    // @trace FR-36
    @ParameterizedTest(name = "JWKS {0}")
    @MethodSource("brokenJwks")
    void anUnusableJwksMakesTheIdTokenInvalid(String name, StubOpenAi.Reply jwks) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        respondWithKid("kid-not-in-the-cache-" + name.replace(' ', '-'));
        stub.jwksReply = jwks;
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
    }

    // @trace FR-36
    @Test
    void anInvalidIdTokenLeavesThePreviousConnectionUntouched() throws Exception {
        String sid = newSid();
        connect(sid);
        stub.responder = r -> stub.okWithIdToken("a.b.c");
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }

    // @trace FR-36
    @Test
    void idTokenValidationComesBeforeTheScopeCheck() throws Exception {
        String sid = newSid();
        connect(sid);
        stub.responder = r -> stub.okWithIdToken(3600, "openid profile", true, "a.b.c");
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_VERIFIED);
        // no scope verdict was made: the previous connection and no PLAN_NOT_ELIGIBLE flag
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }

    // @trace FR-36
    @Test
    void theNonceBelongsToExactlyOneAttempt() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started a = start(sid);
        Started b = start(sid);
        // the token for attempt b carries the nonce of attempt a
        stub.responder = r -> stub.okWithIdToken(stub.idToken(stub.currentClientId(), a.nonce()));
        assertThat(callbackLocation(returnQuery(b, "cb"))).isEqualTo(NOT_VERIFIED);
        // attempt a is still pending and its own nonce verifies
        stub.reset();
        assertThat(callbackLocation(returnQuery(a, "ca"))).isEqualTo(CONNECTED);
    }
}
