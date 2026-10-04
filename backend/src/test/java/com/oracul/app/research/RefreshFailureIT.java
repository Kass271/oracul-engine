package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.chatgpt.StubOpenAi;
import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-02 chatgpt-inference.md FR-40: how the answer of a token refresh decides the session. Exhaustive over the refresh
 * answer classes for startRun; representatives of every class for a refresh during a run and for startAlternativeRun.
 * Sign-in tokens live 60 s (inside the 5-minute skew), so the first token use after sign-in refreshes.
 */
// @trace FR-40
@TestPropertySource(properties = "oracul.chatgpt.http-timeout=PT1S")
class RefreshFailureIT extends AbstractPlanUsageIT {

    enum Kind { SESSION, REGISTRATION, OTHER, TRANSIENT }

    private volatile Function<StubOpenAi.TokenRequest, StubOpenAi.Reply> refreshAnswer;

    /** Sign-in tokens last 60 s; refresh requests are answered by {@link #refreshAnswer}. */
    private String signedIn() throws Exception {
        refreshAnswer = req -> stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
        stub.responder = req -> "refresh_token".equals(req.form().get("grant_type"))
            ? refreshAnswer.apply(req) : stub.ok(60, StubOpenAi.ALL_SCOPES, true);
        return connectedSid();
    }

    private static StubOpenAi.Reply flat(int status, String code) {
        return new StubOpenAi.Reply(status, "{\"error\":\"" + code + "\"}", 0);
    }

    private static StubOpenAi.Reply nested(int status, String code) {
        return new StubOpenAi.Reply(status, "{\"error\":{\"code\":\"" + code + "\"}}", 0);
    }

    record Case(String name, StubOpenAi.Reply reply, Kind kind) {
        @Override
        public String toString() {
            return name;
        }
    }

    static List<Case> cases() {
        List<Case> out = new ArrayList<>();
        for (String code : List.of("invalid_grant", "invalid_refresh_token", "token_expired", "refresh_token_expired",
            "refresh_token_invalidated", "refresh_token_reused")) {
            for (int status : List.of(400, 401)) {
                out.add(new Case(status + " {\"error\":\"" + code + "\"}", flat(status, code), Kind.SESSION));
                out.add(new Case(status + " {\"error\":{\"code\":\"" + code + "\"}}", nested(status, code), Kind.SESSION));
            }
        }
        for (int status : List.of(400, 401)) {
            out.add(new Case(status + " invalid_client", flat(status, "invalid_client"), Kind.REGISTRATION));
            out.add(new Case(status + " nested invalid_client", nested(status, "invalid_client"), Kind.REGISTRATION));
        }
        out.add(new Case("400 invalid_request", flat(400, "invalid_request"), Kind.OTHER));
        out.add(new Case("400 unknown_x", flat(400, "unknown_x"), Kind.OTHER));
        out.add(new Case("400 {}", new StubOpenAi.Reply(400, "{}", 0), Kind.OTHER));
        out.add(new Case("403 unknown_x", flat(403, "unknown_x"), Kind.OTHER));
        out.add(new Case("200 without access_token", new StubOpenAi.Reply(200, "{\"token_type\":\"Bearer\"}", 0), Kind.OTHER));
        out.add(new Case("200 non-JSON", new StubOpenAi.Reply(200, "not json", 0), Kind.OTHER));
        for (int status : List.of(500, 502, 503, 504)) {
            out.add(new Case(status + " temporarily_unavailable", flat(status, "temporarily_unavailable"), Kind.TRANSIENT));
        }
        out.add(new Case("timeout (> http-timeout)", new StubOpenAi.Reply(200, "{\"access_token\":\"late\"}", 2500), Kind.TRANSIENT));
        return out;
    }

    static Stream<Arguments> caseArgs() {
        return cases().stream().map(c -> Arguments.of(c.name(), c));
    }

    /** One representative per class and per body shape for the runs. */
    static Stream<Arguments> representativeArgs() {
        return cases().stream().filter(c -> List.of("400 {\"error\":\"invalid_grant\"}", "401 nested invalid_client",
                "400 unknown_x", "200 without access_token", "503 temporarily_unavailable", "timeout (> http-timeout)").contains(c.name()))
            .map(c -> Arguments.of(c.name(), c));
    }

    private static String expectedState(Kind k) {
        return switch (k) {
            case SESSION, OTHER -> "SESSION_EXPIRED";
            case REGISTRATION -> "REGISTRATION_INVALID";
            case TRANSIENT -> "CONNECTED";
        };
    }

    private static int expectedHttp(Kind k) {
        return k == Kind.TRANSIENT ? 503 : 401;
    }

    private static String expectedCode(Kind k) {
        return switch (k) {
            case SESSION, OTHER -> "CHATGPT_SESSION_EXPIRED";
            case REGISTRATION -> "CHATGPT_REGISTRATION_INVALID";
            case TRANSIENT -> "CHATGPT_UNAVAILABLE";
        };
    }

    private static String expectedMessage(Kind k) {
        return switch (k) {
            case SESSION, OTHER -> M_EXPIRED;
            case REGISTRATION -> M_REGISTRATION_INVALID;
            case TRANSIENT -> M_UNAVAILABLE;
        };
    }

    private Map<String, Object> registration() {
        return jdbc.queryForMap("select client_id, cast(host_id as text) as host_id from chatgpt_client_registration");
    }

    private void assertRefreshForms(String clientId) {
        List<StubOpenAi.TokenRequest> refreshes = stub.grant("refresh_token");
        for (StubOpenAi.TokenRequest t : refreshes) {
            assertThat(t.form().keySet()).containsExactlyInAnyOrder("grant_type", "refresh_token", "client_id", "resource");
            assertThat(t.form().get("client_id")).as("the issued client id, never the dynamic one").isEqualTo(clientId)
                .isNotEqualTo("dynamic_agent_client");
            assertThat(t.form().get("resource")).isEqualTo("https://api.openai.com/v1");
            assertThat(t.contentType()).startsWith("application/x-www-form-urlencoded");
        }
    }

    // ---- startRun ------------------------------------------------------------------------------------------------

    @ParameterizedTest(name = "startRun: refresh answer {0}")
    @MethodSource("caseArgs")
    void startRunClassifiesTheRefreshAnswer(String name, Case c) throws Exception {
        String sid = signedIn();
        Map<String, Object> registrationBefore = registration();
        int runsBefore = runCount();
        refreshAnswer = req -> c.reply();
        MvcResult res = startRun(sid, A).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(name + ": " + body).isEqualTo(expectedHttp(c.kind()));
        assertThat(json(body)).isEqualTo(ordered("code", expectedCode(c.kind()), "message", expectedMessage(c.kind())));
        assertThat(runCount()).as("no run row is created by a failing check").isEqualTo(runsBefore);
        assertThat(connectionState(sid)).as(name).isEqualTo(expectedState(c.kind()));
        assertThat(canGenerate(sid)).isEqualTo(c.kind() == Kind.TRANSIENT);
        assertThat(registration()).as("host id and issued client id are kept").isEqualTo(registrationBefore);
        assertRefreshForms((String) registrationBefore.get("client_id"));
        assertThat(stub.grant("refresh_token")).hasSize(1);
        if (c.kind() == Kind.TRANSIENT) {
            // credentials were kept: a later refresh works with the same refresh token
            String held = stub.grant("refresh_token").get(0).form().get("refresh_token");
            refreshAnswer = req -> stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
            assertThat(startRun(sid, A).andReturn().getResponse().getStatus()).isEqualTo(202);
            assertThat(stub.grant("refresh_token")).hasSize(2);
            assertThat(stub.grant("refresh_token").get(1).form().get("refresh_token")).isEqualTo(held);
        } else {
            // credentials were dropped: the next start is refused at once without a new refresh
            MvcResult again = startRun(sid, A).andReturn();
            assertThat(again.getResponse().getStatus()).isEqualTo(401);
            assertThat(json(again.getResponse().getContentAsString()).get("code")).isEqualTo(expectedCode(c.kind()) );
            assertThat(stub.grant("refresh_token")).hasSize(1);
        }
    }

    @Test
    void noRefreshTokenHeldIsASessionExpiry() throws Exception {
        stub.responder = req -> stub.ok(60, StubOpenAi.ALL_SCOPES, false);
        String sid = connectedSid();
        int runsBefore = runCount();
        MvcResult res = startRun(sid, A).andReturn();
        assertThat(res.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(res.getResponse().getContentAsString()))
            .isEqualTo(ordered("code", "CHATGPT_SESSION_EXPIRED", "message", M_EXPIRED));
        assertThat(stub.grant("refresh_token")).isEmpty();
        assertThat(connectionState(sid)).isEqualTo("SESSION_EXPIRED");
        assertThat(runCount()).isEqualTo(runsBefore);
    }

    @Test
    void aSessionExpiryKeepsTheReauthorizationAndARegistrationInvalidOnlyResetsToAFirstRegistration() throws Exception {
        String expired = signedIn();
        Map<String, Object> registration = registration();
        String hostUrn = "urn:uuid:" + registration.get("host_id");
        refreshAnswer = req -> flat(400, "invalid_grant");
        assertThat(startRun(expired, A).andReturn().getResponse().getStatus()).isEqualTo(401);
        Map<String, String> reauth = authorizeParams(expired);
        assertThat(reauth.get("client_id")).as("reauthorization uses the stored issued client id").isEqualTo(registration.get("client_id"));
        assertThat(reauth).doesNotContainKey("agent_name_hint");
        assertThat(reauth.get("ext_agent_host_id")).isEqualTo(hostUrn);

        String invalid = signedIn();
        refreshAnswer = req -> flat(401, "invalid_client");
        MvcResult res = startRun(invalid, A).andReturn();
        assertThat(json(res.getResponse().getContentAsString()).get("code")).isEqualTo("CHATGPT_REGISTRATION_INVALID");
        Map<String, String> still = authorizeParams(invalid);
        assertThat(still.get("client_id")).as("the client id is kept until Reset").isEqualTo(registration.get("client_id"));
        assertThat(mvc.perform(delete("/api/auth/chatgpt/registration").cookie(new Cookie("ORACUL_SID", invalid)))
            .andReturn().getResponse().getStatus()).isEqualTo(204);
        Map<String, String> first = authorizeParams(invalid);
        assertThat(first.get("client_id")).isEqualTo("dynamic_agent_client");
        assertThat(first.get("agent_name_hint")).isEqualTo("ORACUL");
        assertThat(first.get("ext_agent_host_id")).as("the host id survives the reset").isEqualTo(hostUrn);
    }

    @Test
    void aRegistrationInvalidSessionCannotGenerateAndSaysSoInTheConnection() throws Exception {
        String sid = signedIn();
        refreshAnswer = req -> flat(400, "invalid_client");
        startRun(sid, A);
        MvcResult conn = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
            .get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(json(conn.getResponse().getContentAsString()))
            .isEqualTo(ordered("state", "REGISTRATION_INVALID", "canGenerate", false));
        MvcResult again = startRun(sid, A).andReturn();
        assertThat(again.getResponse().getStatus()).isEqualTo(401);
        assertThat(json(again.getResponse().getContentAsString()))
            .isEqualTo(ordered("code", "CHATGPT_REGISTRATION_INVALID", "message", M_REGISTRATION_INVALID));
    }

    // ---- a refresh that is needed during a run ---------------------------------------------------------------------

    @ParameterizedTest(name = "refresh during the run: {0}")
    @MethodSource("representativeArgs")
    void aRefreshFailingDuringTheRunFailsTheRunWithTheMatchingCode(String name, Case c) throws Exception {
        String sid = signedIn();
        AtomicInteger refreshes = new AtomicInteger();
        // the startRun check refreshes fine (and the new token is again inside the skew); the run's own refresh fails
        refreshAnswer = req -> refreshes.incrementAndGet() == 1 ? stub.ok(60, StubOpenAi.ALL_SCOPES, true) : c.reply();
        Map<String, Object> registrationBefore = registration();
        Ran r = runWithin(sid, A, 20_000);
        assertFailure(r.run(), expectedCode(c.kind()), expectedMessage(c.kind()), null);
        assertThat(connectionState(sid)).isEqualTo(expectedState(c.kind()));
        assertThat(responses.requests).as("no Responses call without a usable token").isEmpty();
        assertThat(registration()).isEqualTo(registrationBefore);
        if (c.kind() == Kind.TRANSIENT) {
            assertThat(stub.grant("refresh_token")).as("startRun + at most 3 attempts of the call").hasSize(4);
        } else {
            assertThat(stub.grant("refresh_token")).as("startRun + the failing refresh").hasSize(2);
        }
        assertFailureHygiene(r.run(), sid, r.id());
    }

    private Ran runWithin(String sid, String body, long timeoutMs) throws Exception {
        String id = (String) startOk(sid, body).get("id");
        return new Ran(sid, id, awaitRun(sid, id, timeoutMs, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status"))));
    }

    // ---- startAlternativeRun -----------------------------------------------------------------------------------------

    private UUID seedCompletedParent(String sid, String packId) {
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, headline, "
                + "final_attempt, evidence_pack_id, deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), "
                + "'STANDARD', 'COMPLETED', cast(? as jsonb), cast(? as jsonb), 'Seeded headline', 1, cast(? as uuid), ?, ?, ?, ?)",
            id, "ORC-RF-" + id.toString().replace("-", "").substring(20), sid, A, ZERO_COUNTS, packId,
            now.plusDays(1), now.minusMinutes(5), now.minusMinutes(4), now.minusMinutes(4));
        return id;
    }

    @ParameterizedTest(name = "startAlternativeRun: refresh answer {0}")
    @MethodSource("representativeArgs")
    void startAlternativeRunClassifiesTheRefreshAnswerToo(String name, Case c) throws Exception {
        Ran source = runV4(A);
        assertStoryCompleted(source.run());
        String packId = jdbc.queryForObject("select cast(evidence_pack_id as text) from generation_run where id = cast(? as uuid)",
            String.class, source.id());
        String sid = signedIn();
        UUID parent = seedCompletedParent(sid, packId);
        refreshAnswer = req -> c.reply();
        int runsBefore = runCount();
        MvcResult res = mvc.perform(post("/api/runs/" + parent + "/alternatives").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        String body = res.getResponse().getContentAsString();
        assertThat(res.getResponse().getStatus()).as(name + ": " + body).isEqualTo(expectedHttp(c.kind()));
        assertThat(json(body)).isEqualTo(ordered("code", expectedCode(c.kind()), "message", expectedMessage(c.kind())));
        assertThat(runCount()).as("no run row is created by a failing check").isEqualTo(runsBefore);
        assertThat(connectionState(sid)).isEqualTo(expectedState(c.kind()));
    }

    // ---- invariants ------------------------------------------------------------------------------------------------------

    @Test
    void concurrentTokenUsesOfOneSessionRefreshOnce() throws Exception {
        String sid = signedIn();
        // slow refresh so that the second caller really waits for the first
        Function<StubOpenAi.TokenRequest, StubOpenAi.Reply> slow = req -> {
            StubOpenAi.Reply ok = stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
            return new StubOpenAi.Reply(ok.status(), ok.body(), 400);
        };
        refreshAnswer = slow;
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) futures.add(pool.submit(() -> startRun(sid, A).andReturn().getResponse().getStatus()));
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> f : futures) statuses.add(f.get());
            assertThat(statuses).as("one run is started, the other call finds it active").contains(202);
        } finally {
            pool.shutdown();
        }
        assertThat(stub.grant("refresh_token")).as("concurrent token uses make at most one refresh request").hasSize(1);
    }

    @Test
    void aRefreshReplacesTheWholeTokenSetAndAnAbsentRefreshTokenKeepsTheOldOne() throws Exception {
        String sid = signedIn();
        refreshAnswer = req -> stub.ok(60, StubOpenAi.ALL_SCOPES, false); // no refresh_token in any refresh answer
        Ran r = runWithin(sid, A, 20_000);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        String firstRefreshToken = stub.issued.stream().filter(v -> v.startsWith("rt-")).findFirst().orElseThrow();
        String signInAccess = stub.issued.stream().filter(v -> v.startsWith("at-")).findFirst().orElseThrow();
        List<StubOpenAi.TokenRequest> refreshes = stub.grant("refresh_token");
        assertThat(refreshes.size()).as("every token use is inside the skew").isGreaterThanOrEqualTo(2);
        for (StubOpenAi.TokenRequest t : refreshes) {
            assertThat(t.form().get("refresh_token")).as("the old refresh token is kept").isEqualTo(firstRefreshToken);
        }
        for (StubResponses.Request req : responses.requests) {
            assertThat(req.headers().get("authorization")).as("calls use the refreshed access token")
                .isNotEqualTo("Bearer " + signInAccess).startsWith("Bearer at-STUBSECRET-");
        }
    }

    @Test
    void everyRefreshOfARunUsesTheRefreshTokenOfThePreviousAnswer() throws Exception {
        String sid = signedIn();
        refreshAnswer = req -> stub.ok(60, StubOpenAi.ALL_SCOPES, true);
        Ran r = runWithin(sid, A, 20_000);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<String> issuedRefreshTokens = stub.issued.stream().filter(v -> v.startsWith("rt-")).toList();
        List<StubOpenAi.TokenRequest> refreshes = stub.grant("refresh_token");
        assertThat(refreshes.size()).isGreaterThanOrEqualTo(2);
        for (int i = 0; i < refreshes.size(); i++) {
            assertThat(refreshes.get(i).form().get("refresh_token")).as("refresh " + i).isEqualTo(issuedRefreshTokens.get(i));
        }
        assertRefreshForms((String) registration().get("client_id"));
    }
}
