package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** FR-7 / FR-35 / FR-36: Continue with ChatGPT (OAuth authorization code + PKCE) — start and callback. */
class ChatGptSignInIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final List<String> SCOPES = List.of(
        "openid", "profile", "email", "offline_access", "resource.invoke", "chatgpt.tokens.use.direct");

    // ---- start ----

    // @trace FR-7
    @Test
    void authorizeRedirectsToOpenAiWithPkceParameters() throws Exception {
        resetRegistration();
        Started s = start(newSid());
        assertThat(s.location()).startsWith(stub.authorizeUrl() + "?");
        assertThat(s.params().get("response_type")).isEqualTo("code");
        assertThat(s.params().get("code_challenge_method")).isEqualTo("S256");
        assertThat(s.params().get("redirect_uri")).isEqualTo(REDIRECT_URI);
        assertThat(s.params().get("resource")).isEqualTo("https://api.openai.com/v1");
        assertThat(List.of(s.params().get("scope").split(" "))).containsExactlyElementsOf(SCOPES);
        assertThat(s.location()).contains("%20").doesNotContain("scope=openid+");
        assertThat(s.state()).matches("[A-Za-z0-9_-]{43}");
        assertThat(s.challenge()).matches("[A-Za-z0-9_-]{43}");
        // @trace FR-35
        assertThat(s.nonce()).matches("[A-Za-z0-9_-]{43}");
        assertThat(s.params().get("ext_agent_host_id")).matches("urn:uuid:[0-9a-f-]{36}");
    }

    // @trace FR-7
    @Test
    void firstSignInUsesDynamicClientRegistrationWithStableHostId() throws Exception {
        resetRegistration();
        Started a = start(newSid());
        assertThat(a.params().get("client_id")).isEqualTo("dynamic_agent_client");
        assertThat(a.params().get("agent_name_hint")).isEqualTo("ORACUL");
        String hostId = a.params().get("ext_agent_host_id");
        // @trace FR-35  wire form is urn:uuid:<host_id>; the stored host_id stays the bare UUID
        assertThat(hostId).startsWith("urn:uuid:");
        assertThat(UUID.fromString(hostId.substring("urn:uuid:".length()))).isNotNull();
        Started b = start(newSid());
        assertThat(b.params().get("ext_agent_host_id")).isEqualTo(hostId);
        String stored = jdbc.queryForObject("select cast(host_id as text) from chatgpt_client_registration", String.class);
        assertThat("urn:uuid:" + stored).isEqualTo(hostId);
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class)).isEqualTo(1);
    }

    // @trace FR-7
    @Test
    void twoStartsGiveDifferentStateAndChallengeAndBothStayValid() throws Exception {
        String sid = newSid();
        Started a = start(sid);
        Started b = start(sid);
        assertThat(a.state()).isNotEqualTo(b.state());
        assertThat(a.challenge()).isNotEqualTo(b.challenge());
        assertThat(a.nonce()).as("nonce sent").isNotNull();
        assertThat(a.nonce()).isNotEqualTo(b.nonce());
        assertThat(callbackLocation(returnQuery(a, "c1"))).isEqualTo(CONNECTED);
        assertThat(callbackLocation(returnQuery(b, "c2"))).isEqualTo(CONNECTED);
    }

    // ---- callback happy path ----

    // @trace FR-7
    @Test
    void callbackExchangesCodeWithPkceVerifierAndRedirectsConnected() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started s = start(sid);
        callback(returnQuery(s, "code-1"))
            .andExpect(status().isFound())
            .andExpect(header().string("Location", CONNECTED))
            .andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(stub.requests).hasSize(1);
        var req = stub.requests.get(0);
        assertThat(req.contentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(req.accept()).contains("application/json");
        assertThat(req.form().get("grant_type")).isEqualTo("authorization_code");
        assertThat(req.form().get("code")).isEqualTo("code-1");
        assertThat(req.form().get("redirect_uri")).isEqualTo(REDIRECT_URI);
        // @trace FR-36  the exchange uses the issued client id from the callback and sends resource
        assertThat(req.form().get("client_id")).isEqualTo(ISSUED_ID);
        assertThat(req.form().get("resource")).isEqualTo("https://api.openai.com/v1");
        String verifier = req.form().get("code_verifier");
        assertThat(verifier).matches("[A-Za-z0-9_-]{86}");
        assertThat(s256(verifier)).isEqualTo(s.challenge());
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }

    // @trace FR-7
    @Test
    void callbackCreatesNoSessionAndSetsNoCookie() throws Exception {
        Started s = start(newSid());
        int before = jdbc.queryForObject("select count(*) from browser_session", Integer.class);
        callback(returnQuery(s, "c")).andExpect(header().doesNotExist("Set-Cookie"));
        assertThat(jdbc.queryForObject("select count(*) from browser_session", Integer.class)).isEqualTo(before);
    }

    // @trace FR-7
    @Test
    void issuedClientIdIsPersistedAndUsedByTheNextSignInWithoutDynamicParameters() throws Exception {
        resetRegistration();
        String sid = newSid();
        Started first = start(sid);
        String hostId = first.params().get("ext_agent_host_id");
        assertThat(callbackLocation("code=c1&state=" + enc(first.state()) + "&client_id=oaiapp_stub_1"))
            .isEqualTo(CONNECTED);
        assertThat(stub.requests.get(0).form().get("client_id")).isEqualTo("oaiapp_stub_1");
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class))
            .isEqualTo("oaiapp_stub_1");

        Started second = start(sid);
        assertThat(second.params().get("client_id")).isEqualTo("oaiapp_stub_1");
        // @trace FR-35  reauthorization: no agent_name_hint, but the same urn:uuid host id as before
        assertThat(second.params()).doesNotContainKey("agent_name_hint");
        assertThat(second.params().get("ext_agent_host_id")).isEqualTo(hostId);
        assertThat(jdbc.queryForObject("select cast(host_id as text) from chatgpt_client_registration", String.class))
            .isEqualTo(hostId.substring("urn:uuid:".length()));

        // @trace FR-36  reauthorization: a different callback client_id is rejected, the stored id stays
        int before = stub.requests.size();
        assertThat(callbackLocation("code=c2&state=" + enc(second.state()) + "&client_id=oaiapp_other"))
            .isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).hasSize(before);
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class))
            .isEqualTo("oaiapp_stub_1");
    }

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {"evil client", "oaiapp_", "other_abc", "oaiapp_bad!id"})
    void malformedCallbackClientIdIsNotCompleted(String clientId) throws Exception {
        // @trace FR-36  first registration without a valid issued client id: no token request, nothing persisted
        resetRegistration();
        Started s = start(newSid());
        assertThat(callbackLocation("code=c&state=" + enc(s.state()) + "&client_id=" + enc(clientId)))
            .isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class)).isNull();
    }

    // @trace FR-7
    @Test
    void failedExchangeDoesNotPersistTheClientId() throws Exception {
        resetRegistration();
        stub.responder = r -> StubOpenAi.status(500, "{\"error\":\"server_error\"}");
        Started s = start(newSid());
        assertThat(callbackLocation("code=c&state=" + enc(s.state()) + "&client_id=oaiapp_stub_9"))
            .isEqualTo(NOT_COMPLETED);
        assertThat(jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class)).isNull();
    }

    // ---- callback errors ----

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {"code=c", "code=c&state=unknown-state", "state=", "error=access_denied", ""})
    void missingOrUnknownStateIsNotCompletedWithoutTokenRequest(String query) throws Exception {
        assertThat(callbackLocation(query)).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
    }

    // @trace FR-7
    @Test
    void stateIsSingleUse() throws Exception {
        String sid = newSid();
        Started s = connect(sid);
        stub.requests.clear();
        assertThat(callbackLocation(returnQuery(s, "again"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
    }

    // @trace FR-7
    @Test
    void oauthErrorRemovesThePendingEntryAndMakesNoTokenRequest() throws Exception {
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation("error=access_denied&error_description=nope&state=" + enc(s.state())))
            .isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {"", "&code="})
    void missingOrEmptyCodeRemovesThePendingEntry(String codePart) throws Exception {
        Started s = start(newSid());
        assertThat(callbackLocation("state=" + enc(s.state()) + codePart)).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
    }

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {"code:4097", "state:513", "error:257", "error_description:2049", "client_id:257"})
    void parameterOverItsLimitIsNotCompletedAndRemovesNothing(String spec) throws Exception {
        String sid = newSid();
        Started s = start(sid);
        String name = spec.substring(0, spec.indexOf(':'));
        int len = Integer.parseInt(spec.substring(spec.indexOf(':') + 1));
        String big = "a".repeat(len);
        String q = switch (name) {
            case "state" -> "state=" + big + "&code=c";
            case "code" -> "state=" + enc(s.state()) + "&code=" + big;
            default -> "state=" + enc(s.state()) + "&code=c&" + name + "=" + big;
        };
        assertThat(callbackLocation(q)).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
        // nothing was removed: the same state still works
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(CONNECTED);
    }

    // @trace FR-7
    @ParameterizedTest
    @CsvSource({"500,not_completed", "400,expired", "401,expired", "302,not_completed"})
    void tokenEndpointNon2xxStoresNothing(int httpStatus, String outcome) throws Exception {
        // @trace FR-36  4xx invalid_grant is `expired`; 5xx and 3xx stay `not_completed`
        stub.responder = r -> StubOpenAi.status(httpStatus, "{\"error\":\"invalid_grant\"}");
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(FRONTEND + "/?chatgpt=" + outcome);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-7
    @ParameterizedTest
    @ValueSource(strings = {"not json at all", "{}", "{\"access_token\":\"\"}", "{\"access_token\":null}", "[]"})
    void tokenEndpointBodyWithoutAccessTokenStoresNothing(String body) throws Exception {
        stub.responder = r -> StubOpenAi.status(200, body);
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-7
    @Test
    void failedExchangeLeavesPreviousCredentialsUnchanged() throws Exception {
        String sid = newSid();
        connect(sid);
        stub.responder = r -> StubOpenAi.status(500, "");
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }

    // @trace FR-7
    @Test
    void callbackLocationNeverEchoesCodeStateErrorOrDescription() throws Exception {
        Started s = start(newSid());
        String loc = callbackLocation("error=SECRETERR&error_description=SECRETDESC&code=SECRETCODE&state=" + enc(s.state()));
        assertThat(loc).isEqualTo(NOT_COMPLETED);
        assertThat(loc).doesNotContain("SECRET").doesNotContain(s.state());
    }

    // @trace FR-7
    @Test
    void callbackWithUnreadableParametersStillRedirects() throws Exception {
        mvc.perform(get("/api/auth/chatgpt/callback?code=%E0%A4%A&state=%"))
            .andExpect(status().isFound());
    }

    // @trace FR-7
    @Test
    void disconnectDropsPendingAuthorizationsOfTheSession() throws Exception {
        String sid = newSid();
        Started s = start(sid);
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).isEmpty();
    }

    // @trace FR-7
    @Test
    void sessionCookieIsNotNeededToCompleteTheFlow() throws Exception {
        String sid = newSid();
        Started s = start(sid);
        callback(returnQuery(s, "c"))
            .andExpect(header().string("Location", CONNECTED));
        mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andExpect(status().isOk());
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }
}
