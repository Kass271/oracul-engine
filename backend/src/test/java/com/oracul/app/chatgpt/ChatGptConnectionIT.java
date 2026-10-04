package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** FR-8: connection status, sign-out, and the startRun connection check. */
class ChatGptConnectionIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final String NOT_CONNECTED_MSG = "Connect ChatGPT to generate";
    private static final String NOT_ELIGIBLE_MSG = "Your ChatGPT plan is not eligible for ORACUL";
    private static final String EXPIRED_MSG = "ChatGPT session expired — please reconnect";

    private void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    private void assertUsable(ResultActions r) throws Exception {
        int s = r.andReturn().getResponse().getStatus();
        assertThat(s).as("startRun with a usable connection").isNotIn(400, 401, 403);
    }

    // ---- status ----

    // @trace FR-8
    @Test
    void freshSessionIsNotConnectedWithExactlyTwoProperties() throws Exception {
        String sid = newSid();
        assertThat(connection(sid)).isEqualTo(Map.of("state", "NOT_CONNECTED", "canGenerate", false));
    }

    // @trace FR-8
    @Test
    void connectedSessionCanGenerate() throws Exception {
        String sid = newSid();
        connect(sid);
        assertThat(connection(sid)).isEqualTo(Map.of("state", "CONNECTED", "canGenerate", true));
    }

    // @trace FR-8
    @Test
    void connectionStateNeverCallsOpenAiAndNeverRefreshes() throws Exception {
        stub.responder = r -> stub.ok(30, StubOpenAi.ALL_SCOPES, true); // already inside the refresh skew
        String sid = newSid();
        connect(sid);
        stub.requests.clear();
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
        assertThat(stub.requests).isEmpty();
    }

    // @trace FR-8
    @Test
    void sessionsAreIsolated() throws Exception {
        String a = newSid();
        String b = newSid();
        connect(a);
        assertThat(stateOf(a)).isEqualTo("CONNECTED");
        assertThat(stateOf(b)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-8
    @Test
    void restartLosesAllCredentialsButKeepsTheSessionRow() throws Exception {
        String sid = newSid();
        connect(sid);
        clearStore();
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(jdbc.queryForObject(
            "select count(*) from browser_session where cast(id as text) = ?", Integer.class, sid)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class))
            .isGreaterThanOrEqualTo(1);
    }

    // ---- eligibility ----

    // @trace FR-8
    @Test
    void scopeWithoutRequiredScopeIsNotEligible() throws Exception {
        stub.responder = r -> stub.ok(3600, "openid profile email offline_access resource.invoke", true);
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_ELIGIBLE);
        assertThat(connection(sid)).isEqualTo(Map.of("state", "PLAN_NOT_ELIGIBLE", "canGenerate", false));
        assertError(startRun(sid), 403, "CHATGPT_PLAN_NOT_ELIGIBLE", NOT_ELIGIBLE_MSG);
    }

    // @trace FR-8
    @Test
    void absentScopeMeansRequestedScopesAreGranted() throws Exception {
        stub.responder = r -> stub.ok(3600, null, true);
        String sid = newSid();
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(CONNECTED);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }

    // @trace FR-8
    @Test
    void notEligibleDropsPreviousCredentialsAndNextSuccessfulExchangeClearsTheFlag() throws Exception {
        String sid = newSid();
        connect(sid);
        stub.responder = r -> stub.ok(3600, "openid", true);
        Started s = start(sid);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_ELIGIBLE);
        assertThat(stateOf(sid)).isEqualTo("PLAN_NOT_ELIGIBLE");
        stub.reset();
        connect(sid);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
        assertUsable(startRun(sid));
    }

    // ---- disconnect ----

    // @trace FR-8
    @Test
    void disconnectAnswers204AndIsIdempotent() throws Exception {
        String sid = newSid();
        connect(sid);
        disconnect(sid).andExpect(status().isNoContent()).andExpect(content().string(""));
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-8
    @Test
    void disconnectClearsTheExpiredAndNotEligibleFlags() throws Exception {
        stub.responder = r -> stub.ok(3600, "openid", true);
        String sid = newSid();
        Started s = start(sid);
        callbackLocation(returnQuery(s, "c"));
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-8
    @Test
    void disconnectOfOneSessionLeavesAnotherConnected() throws Exception {
        String a = newSid();
        String b = newSid();
        connect(a);
        connect(b);
        disconnect(a).andExpect(status().isNoContent());
        assertThat(stateOf(a)).isEqualTo("NOT_CONNECTED");
        assertThat(stateOf(b)).isEqualTo("CONNECTED");
    }

    // ---- startRun connection check ----

    // @trace FR-8
    @Test
    void startRunWithoutConnectionIs401NotConnected() throws Exception {
        assertError(startRun(newSid()), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
    }

    // @trace FR-8
    @Test
    void startRunAfterDisconnectIs401NotConnected() throws Exception {
        String sid = newSid();
        connect(sid);
        disconnect(sid);
        assertError(startRun(sid), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
    }

    // @trace FR-8
    @Test
    void bodyValidationComesBeforeTheConnectionCheck() throws Exception {
        mvc.perform(post("/api/runs").contentType(MediaType.APPLICATION_JSON)
                .content(VALID_RUN.replace("\"darkness\":5", "\"darkness\":0")))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    // @trace FR-8
    @Test
    void startRunWithUsableConnectionPassesTheCheck() throws Exception {
        String sid = newSid();
        connect(sid);
        assertUsable(startRun(sid));
        assertThat(stub.grant("refresh_token")).isEmpty();
    }

    // @trace FR-8
    @Test
    void accessTokenWithoutExpiresInDefaultsToOneHour() throws Exception {
        stub.responder = r -> stub.ok(null, StubOpenAi.ALL_SCOPES, true);
        String sid = newSid();
        connect(sid);
        assertUsable(startRun(sid));
        assertThat(stub.grant("refresh_token")).isEmpty();
    }

    // @trace FR-8, FR-38
    @Test
    void tokenInsideRefreshSkewIsRefreshedAndStaysConnected() throws Exception {
        stub.responder = r -> stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = newSid();
        resetRegistration();
        Started s = start(sid);
        callbackLocation("code=c&state=" + enc(s.state()) + "&client_id=oaiapp_stub_r");
        String firstRefreshToken = stub.issued.stream().filter(v -> v.startsWith("rt-")).findFirst().orElseThrow();
        assertUsable(startRun(sid));
        // the run task resolves the model right after the 202 (phase-02 FR-38), which may refresh once more: only the
        // first refresh is certain here, and every refresh uses the refresh token the previous answer returned
        var refreshes = stub.grant("refresh_token");
        assertThat(refreshes).isNotEmpty();
        assertThat(refreshes.get(0).form().get("refresh_token")).isEqualTo(firstRefreshToken);
        assertThat(refreshes.get(0).form().get("client_id")).isEqualTo("oaiapp_stub_r");
        assertThat(refreshes.get(0).form().get("resource")).isEqualTo("https://api.openai.com/v1");
        assertThat(refreshes.get(0).form().keySet()).containsExactlyInAnyOrder("grant_type", "refresh_token", "client_id", "resource");
        assertThat(refreshes.get(0).contentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");

        // rotation: the next refresh uses the refresh token returned by the previous one
        assertUsable(startRun(sid));
        var all = stub.grant("refresh_token");
        var heldTokens = stub.issued.stream().filter(v -> v.startsWith("rt-")).toList();
        assertThat(all).hasSizeGreaterThanOrEqualTo(1);
        for (int i = 0; i < all.size(); i++) {
            assertThat(all.get(i).form().get("refresh_token")).as("refresh " + i + " uses the token of the previous answer")
                .isEqualTo(heldTokens.get(i));
        }
    }

    // @trace FR-8
    @Test
    void failedRefreshExpiresTheSession() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
            : stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = newSid();
        connect(sid);
        assertError(startRun(sid), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(connection(sid)).isEqualTo(Map.of("state", "SESSION_EXPIRED", "canGenerate", false));
        int refreshes = stub.grant("refresh_token").size();
        assertError(startRun(sid), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(stub.grant("refresh_token")).hasSize(refreshes);
    }

    // @trace FR-8
    @Test
    void expiredTokenWithoutRefreshTokenExpiresTheSession() throws Exception {
        stub.responder = r -> stub.ok(30, StubOpenAi.ALL_SCOPES, false);
        String sid = newSid();
        connect(sid);
        assertError(startRun(sid), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(stub.grant("refresh_token")).isEmpty();
        assertThat(stateOf(sid)).isEqualTo("SESSION_EXPIRED");
    }

    // @trace FR-8
    @Test
    void refreshAnswerWithoutAccessTokenExpiresTheSession() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? StubOpenAi.status(200, "{\"token_type\":\"Bearer\"}")
            : stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = newSid();
        connect(sid);
        assertError(startRun(sid), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(stateOf(sid)).isEqualTo("SESSION_EXPIRED");
    }

    // @trace FR-8
    @Test
    void reconnectingAfterExpiryClearsTheFlag() throws Exception {
        stub.responder = r -> stub.ok(30, null, false);
        String sid = newSid();
        connect(sid);
        startRun(sid);
        assertThat(stateOf(sid)).isEqualTo("SESSION_EXPIRED");
        stub.reset();
        connect(sid);
        assertThat(stateOf(sid)).isEqualTo("CONNECTED");
    }
}
