package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.oracul.app.api.model.RunStatus;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** FR-37: Reset ChatGPT connection (DELETE /api/auth/chatgpt/registration) and the revocation on disconnect. */
@ExtendWith(OutputCaptureExtension.class)
class ChatGptResetIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    private static final String ZERO_COUNTS =
        "{\"searches\":0,\"articlesRetrieved\":0,\"articlesConsidered\":0,\"uniqueEvents\":0,\"eventsSelected\":0,\"counterSignals\":0,\"sourcesUsed\":0}";

    private ResultActions reset(String sid) throws Exception {
        var b = delete("/api/auth/chatgpt/registration");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    private String registeredClientId() {
        return jdbc.queryForObject("select client_id from chatgpt_client_registration", String.class);
    }

    private String storedHostId() {
        return jdbc.queryForObject("select cast(host_id as text) from chatgpt_client_registration", String.class);
    }

    private int rows() {
        return jdbc.queryForObject("select count(*) from chatgpt_client_registration", Integer.class);
    }

    private void defaultTokens() {
        stub.responder = r -> stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
    }

    private void connectWithoutRefreshToken(String sid) throws Exception {
        stub.responder = r -> stub.ok(3600, StubOpenAi.ALL_SCOPES, false);
        connect(sid);
        defaultTokens();
    }

    private List<String> heldRefreshTokens() {
        return stub.issued.stream().filter(v -> v.startsWith("rt-")).toList();
    }

    private UUID seedRun(String sid, String status) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, "
                + "deadline_at, created_at, updated_at) values (?, ?, cast(? as uuid), 'STANDARD', ?, cast(? as jsonb), "
                + "cast(? as jsonb), ?, ?, ?)",
            id, "ORC-RST-" + id.toString().replace("-", "").substring(20), sid, status, VALID_RUN, ZERO_COUNTS,
            now.plusDays(1), now, now);
        return id;
    }

    // ---- happy path and invariants after every 204 ----

    /** Reset is installation-wide: sessions connected by earlier tests must not add revocation calls. */
    @org.junit.jupiter.api.BeforeEach
    void emptyCredentialStore() throws Exception {
        clearStore();
    }

    // @trace FR-37
    @Test
    void resetClearsEverySessionAndTheIssuedClientIdButKeepsTheHostId() throws Exception {
        resetRegistration();
        String a = newSid();
        String b = newSid();
        Started first = start(a);
        String hostUrn = first.params().get("ext_agent_host_id");
        assertThat(callbackLocation(returnQuery(first, "ca"))).isEqualTo(CONNECTED);
        connect(b);
        Started pending = start(a); // a pending authorization of the installation
        assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
        String host = storedHostId();
        List<String> refreshTokens = heldRefreshTokens();
        assertThat(refreshTokens).hasSize(2);
        int tokenRequests = stub.requests.size();

        reset(a).andExpect(status().isNoContent()).andExpect(content().string(""));

        assertThat(stateOf(a)).isEqualTo("NOT_CONNECTED");
        assertThat(stateOf(b)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
        assertThat(storedHostId()).isEqualTo(host);
        assertThat(rows()).isEqualTo(1);
        // revocation: one call per held refresh token with exactly token, token_type_hint, client_id
        assertThat(stub.revocations).hasSize(2);
        for (var rev : stub.revocations) {
            assertThat(rev.contentType()).startsWith("application/x-www-form-urlencoded");
            assertThat(rev.form().keySet()).containsExactlyInAnyOrder("token", "token_type_hint", "client_id");
            assertThat(rev.form().get("token_type_hint")).isEqualTo("refresh_token");
            assertThat(rev.form().get("client_id")).isEqualTo(ISSUED_ID);
        }
        assertThat(stub.revocations.stream().map(r -> r.form().get("token")).toList())
            .containsExactlyInAnyOrderElementsOf(refreshTokens);
        // every pending state is unusable
        assertThat(callbackLocation(returnQuery(pending, "cp"))).isEqualTo(NOT_COMPLETED);
        assertThat(stub.requests).hasSize(tokenRequests);
        // the next authorize is a first registration with the same host id
        Started next = start(a);
        assertThat(next.params().get("client_id")).isEqualTo("dynamic_agent_client");
        assertThat(next.params().get("agent_name_hint")).isEqualTo("ORACUL");
        assertThat(next.params().get("ext_agent_host_id")).isEqualTo(hostUrn);
    }

    // @trace FR-37
    @Test
    void resetIsIdempotentAndASecondResetRevokesNothing() throws Exception {
        resetRegistration();
        connect(newSid());
        reset(null).andExpect(status().isNoContent());
        stub.revocations.clear();
        String host = storedHostId();
        reset(null).andExpect(status().isNoContent());
        assertThat(stub.revocations).isEmpty();
        assertThat(registeredClientId()).isNull();
        assertThat(storedHostId()).isEqualTo(host);
        assertThat(rows()).isEqualTo(1);
    }

    // @trace FR-37
    @Test
    void resetWithoutAnyRegistrationRowAnswers204AndCreatesNoRow() throws Exception {
        resetRegistration();
        assertThat(rows()).isEqualTo(0);
        reset(null).andExpect(status().isNoContent());
        assertThat(rows()).isEqualTo(0);
        assertThat(stub.revocations).isEmpty();
    }

    // @trace FR-37
    @Test
    void sessionsWithoutARefreshTokenAddNoRevocationCall() throws Exception {
        resetRegistration();
        String withToken = newSid();
        String without = newSid();
        connect(withToken);
        connectWithoutRefreshToken(without);
        reset(null).andExpect(status().isNoContent());
        assertThat(stub.revocations).hasSize(1);
        assertThat(stub.revocations.get(0).form().get("token")).isEqualTo(heldRefreshTokens().get(0));
        assertThat(stateOf(without)).isEqualTo("NOT_CONNECTED");
    }

    // @trace FR-37
    @Test
    void resetDropsThePlanNotEligibleAndSessionExpiredFlags() throws Exception {
        resetRegistration();
        String notEligible = newSid();
        stub.responder = r -> stub.ok(3600, "openid profile", true);
        Started s = start(notEligible);
        assertThat(callbackLocation(returnQuery(s, "c"))).isEqualTo(NOT_ELIGIBLE);
        assertThat(stateOf(notEligible)).isEqualTo("PLAN_NOT_ELIGIBLE");

        String expired = newSid();
        stub.responder = r -> stub.ok(30, StubOpenAi.ALL_SCOPES, false);
        connect(expired);
        startRun(expired);
        assertThat(stateOf(expired)).isEqualTo("SESSION_EXPIRED");

        defaultTokens();
        reset(null).andExpect(status().isNoContent());
        assertThat(stateOf(notEligible)).isEqualTo("NOT_CONNECTED");
        assertThat(stateOf(expired)).isEqualTo("NOT_CONNECTED");
    }

    // ---- run-status classes ----

    static Stream<String> everyRunStatus() {
        return Stream.of(RunStatus.values()).map(Enum::name);
    }

    // @trace FR-37
    @ParameterizedTest(name = "a {0} run of another session")
    @MethodSource("everyRunStatus")
    void onlyQueuedAndRunningRunsRefuseTheReset(String runStatus) throws Exception {
        resetRegistration();
        String a = newSid();
        String other = newSid();
        connect(a);
        Started pending = start(a);
        assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
        boolean active = runStatus.equals("QUEUED") || runStatus.equals("RUNNING");
        UUID run = seedRun(other, runStatus);
        try {
            ResultActions r = reset(a);
            if (active) {
                r.andExpect(status().isConflict())
                    .andExpect(jsonPath("$.*", hasSize(2)))
                    .andExpect(jsonPath("$.code").value("RUN_IN_PROGRESS"))
                    .andExpect(jsonPath("$.message").value("Wait until the current run finishes"));
                // a 409 changes nothing
                assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
                assertThat(stateOf(a)).isEqualTo("CONNECTED");
                assertThat(stub.revocations).isEmpty();
                assertThat(callbackLocation(returnQuery(pending, "cp"))).isEqualTo(CONNECTED);
            } else {
                r.andExpect(status().isNoContent());
                assertThat(registeredClientId()).isNull();
                assertThat(stateOf(a)).isEqualTo("NOT_CONNECTED");
                assertThat(stub.revocations).hasSize(1);
            }
        } finally {
            jdbc.update("delete from generation_run where id = ?", run);
        }
    }

    // @trace FR-37
    @Test
    void noRunAtAllAllowsTheReset() throws Exception {
        resetRegistration();
        connect(newSid());
        reset(null).andExpect(status().isNoContent());
        assertThat(registeredClientId()).isNull();
    }

    // ---- disconnect revocation ----

    // @trace FR-37
    @Test
    void disconnectRevokesTheSessionsRefreshTokenBeforeClearingIt() throws Exception {
        resetRegistration();
        String sid = newSid();
        connect(sid);
        String rt = heldRefreshTokens().get(0);
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stub.revocations).hasSize(1);
        var rev = stub.revocations.get(0);
        assertThat(rev.contentType()).startsWith("application/x-www-form-urlencoded");
        assertThat(rev.form()).isEqualTo(Map.of("token", rt, "token_type_hint", "refresh_token", "client_id", ISSUED_ID));
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        // idempotent: nothing left to revoke
        stub.revocations.clear();
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stub.revocations).isEmpty();
        // the registration is not touched by a disconnect
        assertThat(registeredClientId()).isEqualTo(ISSUED_ID);
    }

    // @trace FR-37
    @Test
    void disconnectOfASessionWithoutRefreshTokenCallsNothing() throws Exception {
        String sid = newSid();
        connectWithoutRefreshToken(sid);
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stub.revocations).isEmpty();
    }

    // ---- revocation outcome classes (200, 400, 500; timeout and refused have their own classes) ----

    // @trace FR-37
    @ParameterizedTest(name = "revocation answers {0}")
    @ValueSource(ints = {200, 400, 500})
    void aRevocationOutcomeNeverChangesTheResult(int revokeStatus, CapturedOutput output) throws Exception {
        stub.revokeResponder = r -> new StubOpenAi.Reply(revokeStatus, revokeStatus == 200 ? "" : "{\"error\":\"x\"}", 0);
        resetRegistration();
        String sid = newSid();
        connect(sid);
        List<String> tokens = heldRefreshTokens();
        disconnect(sid).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(stub.revocations).hasSize(1);

        connect(sid);
        reset(sid).andExpect(status().isNoContent());
        assertThat(stateOf(sid)).isEqualTo("NOT_CONNECTED");
        assertThat(registeredClientId()).isNull();
        assertThat(stub.revocations).hasSize(2);

        assertThat(output.getAll()).contains("chatgpt revocation: status=" + revokeStatus);
        for (String t : heldRefreshTokens()) assertThat(output.getAll()).doesNotContain(t);
        for (String t : tokens) assertThat(output.getAll()).doesNotContain(t);
    }

    // ---- errors ----

    // @trace FR-37
    @Test
    void anUnexpectedFailureAnswers500InternalError() throws Exception {
        resetRegistration();
        String sid = newSid();
        connect(sid);
        jdbc.execute("alter table chatgpt_client_registration rename to chatgpt_client_registration_off");
        try {
            reset(sid).andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong — try again"));
        } finally {
            jdbc.execute("alter table chatgpt_client_registration_off rename to chatgpt_client_registration");
        }
    }
}
