package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-36: callback rules 1-9 and 11-13 — limits, issued client id, token-response classes, outcome invariants. */
class ChatGptCallbackIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final Set<String> OUTCOMES = Set.of(CONNECTED, NOT_COMPLETED, NOT_ELIGIBLE, NOT_VERIFIED, EXPIRED);

    private String registeredClientId() {
        return jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class);
    }

    private void assertOutcomeInvariant(String location, Started s) {
        assertThat(s.nonce()).as("the authorize request carries a nonce").isNotNull();
        assertThat(OUTCOMES).as("Location is exactly one of the 5 outcome URLs").contains(location);
        assertThat(location).doesNotContain(s.state()).doesNotContain(s.nonce()).doesNotContain("error")
            .doesNotContain("code=").doesNotContain("token");
    }

    // ---- rule 1: parameter lengths (boundary each side) ----

    static Stream<Arguments> lengthBoundaries() {
        // name, length, first outcome, token requests, outcome of a second callback with the proper query
        return Stream.of(
            Arguments.of("code", 4096, "connected", 1, "not_completed"),
            Arguments.of("code", 4097, "not_completed", 0, "connected"),
            Arguments.of("state", 512, "not_completed", 0, "connected"),
            Arguments.of("state", 513, "not_completed", 0, "connected"),
            Arguments.of("error", 256, "not_completed", 0, "not_completed"),
            Arguments.of("error", 257, "not_completed", 0, "connected"),
            Arguments.of("error_description", 2048, "connected", 1, "not_completed"),
            Arguments.of("error_description", 2049, "not_completed", 0, "connected"),
            Arguments.of("client_id", 256, "connected", 1, "not_completed"),
            Arguments.of("client_id", 257, "not_completed", 0, "connected"));
    }

    // @trace FR-36
    @ParameterizedTest(name = "{0} of {1} chars")
    @MethodSource("lengthBoundaries")
    void parameterLengthBoundaries(String name, int len, String first, int tokenRequests, String second) throws Exception {
        resetRegistration();
        Started s = start(newSid());
        String filler = "a".repeat(len);
        String q = switch (name) {
            case "code" -> "state=" + enc(s.state()) + "&code=" + filler + "&client_id=" + ISSUED_ID;
            case "state" -> "state=" + filler + "&code=c&client_id=" + ISSUED_ID;
            case "error" -> "state=" + enc(s.state()) + "&code=c&client_id=" + ISSUED_ID + "&error=" + filler;
            case "error_description" ->
                "state=" + enc(s.state()) + "&code=c&client_id=" + ISSUED_ID + "&error_description=" + filler;
            default -> "state=" + enc(s.state()) + "&code=c&client_id=oaiapp_" + "a".repeat(len - "oaiapp_".length());
        };
        String loc = callbackLocation(q);
        assertThat(loc).isEqualTo(FRONTEND + "/?chatgpt=" + first);
        assertOutcomeInvariant(loc, s);
        assertThat(stub.requests).hasSize(tokenRequests);
        // the pending entry is removed only by rules 3+ (not by an over-long parameter)
        stub.requests.clear();
        assertThat(callbackLocation(returnQuery(s, "c2"))).isEqualTo(FRONTEND + "/?chatgpt=" + second);
    }

    // ---- rule 6: exchange client id ----

    // @trace FR-36
    @ParameterizedTest(name = "first registration accepts {0}")
    @ValueSource(strings = {"oaiapp_a", "oaiapp_Zz09_-x"})
    void firstRegistrationAcceptsAnIssuedClientId(String clientId) throws Exception {
        resetRegistration();
        Started s = start(newSid());
        assertThat(callbackLocation("code=c&state=" + enc(s.state()) + "&client_id=" + enc(clientId))).isEqualTo(CONNECTED);
        assertThat(stub.requests).hasSize(1);
        assertThat(stub.requests.get(0).form().get("client_id")).isEqualTo(clientId);
        assertThat(registeredClientId()).isEqualTo(clientId);
    }

    // @trace FR-36
    @Test
    void firstRegistrationAcceptsTheLongestIssuedClientId() throws Exception {
        resetRegistration();
        String longest = "oaiapp_" + ("A1_-".repeat(63)).substring(0, 249); // 249 chars after the prefix
        assertThat(longest).hasSize(256);
        Started s = start(newSid());
        assertThat(callbackLocation("code=c&state=" + enc(s.state()) + "&client_id=" + enc(longest))).isEqualTo(CONNECTED);
        assertThat(registeredClientId()).isEqualTo(longest);
    }

    static Stream<String> rejectedFirstRegistrationClientIds() {
        return Stream.of(null, "", "oaiapp_", "dynamic_agent_client", "oaiapp_bad!id", "evil client", "OAIAPP_x",
            "other_abc");
    }

    // @trace FR-36
    @ParameterizedTest(name = "first registration rejects client_id [{0}]")
    @MethodSource("rejectedFirstRegistrationClientIds")
    void firstRegistrationRejectsAnythingButAnIssuedClientId(String clientId) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        String q = "code=c&state=" + enc(s.state()) + (clientId == null ? "" : "&client_id=" + enc(clientId));
        String loc = callbackLocation(q);
        assertThat(loc).isEqualTo(NOT_COMPLETED);
        assertOutcomeInvariant(loc, s);
        assertThat(stub.requests).isEmpty();
        assertThat(registeredClientId()).isNull();
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-36
    @ParameterizedTest(name = "reauthorization with callback client_id [{0}]")
    @ValueSource(strings = {"<absent>", "oaiapp_stored", "oaiapp_other", "dynamic_agent_client", "oaiapp_bad!id", "other"})
    void reauthorizationAcceptsOnlyAbsentOrTheStoredClientId(String clientId) throws Exception {
        resetRegistration();
        String sid = newSid();
        Started first = start(sid);
        assertThat(callbackLocation("code=c0&state=" + enc(first.state()) + "&client_id=oaiapp_stored")).isEqualTo(CONNECTED);
        stub.requests.clear();

        Started s = start(sid);
        assertThat(s.params().get("client_id")).isEqualTo("oaiapp_stored");
        boolean ok = clientId.equals("<absent>") || clientId.equals("oaiapp_stored");
        String q = "code=c&state=" + enc(s.state()) + (clientId.equals("<absent>") ? "" : "&client_id=" + enc(clientId));
        String loc = callbackLocation(q);
        assertThat(loc).isEqualTo(ok ? CONNECTED : NOT_COMPLETED);
        assertOutcomeInvariant(loc, s);
        assertThat(stub.requests).hasSize(ok ? 1 : 0);
        if (ok) assertThat(stub.requests.get(0).form().get("client_id")).isEqualTo("oaiapp_stored");
        assertThat(registeredClientId()).isEqualTo("oaiapp_stored");
    }

    // ---- rules 7-9: token request and token-response classes ----

    static Stream<Arguments> tokenResponses() {
        return Stream.of(
            Arguments.of(400, "{\"error\":\"invalid_grant\"}", "expired"),
            Arguments.of(401, "{\"error\":\"invalid_grant\"}", "expired"),
            Arguments.of(400, "{\"error\":{\"code\":\"invalid_grant\"}}", "expired"),
            Arguments.of(403, "{\"error\":\"invalid_grant\",\"error_description\":\"x\"}", "expired"),
            Arguments.of(400, "{\"error\":\"invalid_client\"}", "not_completed"),
            Arguments.of(400, "{\"error\":{\"code\":\"other\"}}", "not_completed"),
            Arguments.of(429, "{\"error\":\"rate_limited\"}", "not_completed"),
            Arguments.of(400, "not json", "not_completed"),
            Arguments.of(500, "{\"error\":\"server_error\"}", "not_completed"),
            Arguments.of(503, "{\"error\":\"invalid_grant\"}", "not_completed"),
            Arguments.of(302, "{\"error\":\"invalid_grant\"}", "not_completed"),
            Arguments.of(200, "not json at all", "not_completed"),
            Arguments.of(200, "{}", "not_completed"),
            Arguments.of(200, "{\"access_token\":\"\"}", "not_completed"),
            Arguments.of(200, "{\"access_token\":null}", "not_completed"));
    }

    // @trace FR-36
    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("tokenResponses")
    void tokenResponseClassesDecideTheOutcomeAndStoreNothing(int status, String body, String outcome) throws Exception {
        resetRegistration();
        stub.responder = r -> StubOpenAi.status(status, body);
        String sid = newSid();
        Started s = start(sid);
        String loc = callbackLocation(returnQuery(s, "c"));
        assertThat(loc).isEqualTo(FRONTEND + "/?chatgpt=" + outcome);
        assertOutcomeInvariant(loc, s);
        assertThat(stub.requests).hasSize(1);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
    }

    // @trace FR-36
    @ParameterizedTest(name = "previous credentials survive {0} {1}")
    @MethodSource("tokenResponses")
    void aFailedExchangeLeavesThePreviousConnectionUntouched(int status, String body, String outcome) throws Exception {
        String sid = newSid();
        connect(sid);
        String stored = registeredClientId();
        stub.responder = r -> StubOpenAi.status(status, body);
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(FRONTEND + "/?chatgpt=" + outcome);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
        assertThat(registeredClientId()).isEqualTo(stored);
    }

    // @trace FR-36
    @Test
    void theTokenRequestFormIsExactlyTheDocumentedOne() throws Exception {
        resetRegistration();
        Started s = start(newSid());
        assertThat(callbackLocation(returnQuery(s, "the-code"))).isEqualTo(CONNECTED);
        assertThat(stub.requests).hasSize(1);
        var req = stub.requests.get(0);
        assertThat(req.contentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(req.accept()).contains("application/json");
        assertThat(req.form().keySet()).containsExactlyInAnyOrder(
            "grant_type", "code", "client_id", "code_verifier", "redirect_uri", "resource");
        assertThat(req.form().get("grant_type")).isEqualTo("authorization_code");
        assertThat(req.form().get("code")).isEqualTo("the-code");
        assertThat(req.form().get("client_id")).isEqualTo(ISSUED_ID);
        assertThat(req.form().get("redirect_uri")).isEqualTo(s.params().get("redirect_uri"));
        assertThat(req.form().get("resource")).isEqualTo("https://api.openai.com/v1");
    }

    // ---- rules 11-13: persistence and scopes ----

    // @trace FR-36
    @Test
    void notEligibleStillPersistsTheIssuedClientIdAndDropsPreviousCredentials() throws Exception {
        resetRegistration();
        stub.responder = r -> stub.ok(3600, "openid profile email offline_access resource.invoke", true);
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_ELIGIBLE);
        assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
        assertThat(stateOf(sid)).isEqualTo("PLAN_NOT_ELIGIBLE");
    }

    // @trace FR-36
    @Test
    void aConcurrentlyStoredClientIdIsNeverOverwritten() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started a = start(sid);
        Started b = start(sid);
        assertThat(callbackLocation("code=ca&state=" + enc(a.state()) + "&client_id=oaiapp_first")).isEqualTo(CONNECTED);
        assertThat(callbackLocation("code=cb&state=" + enc(b.state()) + "&client_id=oaiapp_second")).isEqualTo(CONNECTED);
        assertThat(registeredClientId()).isEqualTo("oaiapp_first");
        assertThat(registeredClientId()).isNotEqualTo("dynamic_agent_client");
    }

    // ---- invariants over a series of callbacks ----

    // @trace FR-36
    @Test
    void everyCallbackRedirectsToOneOfTheFiveOutcomesAndNeverEchoesAnything() throws Exception {
        resetRegistration();
        String sid = newSid();
        List<String> queries = new java.util.ArrayList<>();
        Started a = start(sid);
        Started b = start(sid);
        Started c = start(sid);
        queries.add(returnQuery(a, "ok-1"));
        queries.add("code=c&state=" + enc(b.state()) + "&error=access_denied&error_description=SECRETDESC");
        queries.add("code=SECRETCODE&state=" + enc(c.state()) + "&client_id=bad");
        queries.add("code=SECRETCODE&state=nope");
        queries.add("");
        for (String q : queries) {
            String loc = callbackLocation(q);
            assertThat(OUTCOMES).contains(loc);
            assertThat(loc).doesNotContain("SECRET").doesNotContain("state=").doesNotContain("nonce")
                .doesNotContain(a.state()).doesNotContain(b.state()).doesNotContain(c.state());
        }
        // state is usable at most once
        stub.requests.clear();
        assertThat(callbackLocation(returnQuery(a, "again"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
    }
}
