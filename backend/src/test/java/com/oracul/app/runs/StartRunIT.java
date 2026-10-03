package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;

/** startRun rows of generation-runs.md "Slice 04_run-start": runs stay active (stage delay PT30S). */
// @trace FR-10
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT30S",
    "oracul.run.executor-threads=32",
})
class StartRunIT extends AbstractRunIT {

    @Override
    protected boolean awaitRunsAfterEach() {
        return false; // runs are kept pending on purpose
    }

    private static final String ACTIVE_MSG = "A generation is already running";
    private static final String NOT_CONNECTED_MSG = "Connect ChatGPT to generate";
    private static final String EXPIRED_MSG = "ChatGPT session expired — please reconnect";
    private static final String NOT_ELIGIBLE_MSG = "Your ChatGPT plan is not eligible for ORACUL";

    private void assertError(ResultActions r, int status, String code, String message) throws Exception {
        r.andExpect(status().is(status))
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.*", hasSize(2)))
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.message").value(message));
    }

    // #1
    @Test
    void acceptsAcceptanceBodyAndReturnsTheQueuedRun() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, A);
        assertThat(UUID.fromString((String) run.get("id"))).isNotNull();
        assertThat((String) run.get("generationId")).matches("^ORC-\\d{4}-\\d{2}-\\d{2}-\\d{4}(-\\d+)?$");
        assertThat(run.get("kind")).isEqualTo("STANDARD");
        assertThat(run.get("status")).isEqualTo("QUEUED");
        assertThat(run.get("stageIndex")).isEqualTo(0);
        assertThat(run.get("stageCount")).isEqualTo(10);
        for (String k : List.of("stage", "stageLabel", "parentRunId", "evidencePackId", "failure", "suggestedRealism", "headline", "completedAt")) {
            assertThat(absent(run, k)).as(k + " absent").isTrue();
        }
        assertThat(run.get("configuration")).isEqualTo(json(A));
        assertThat(run.get("counts")).isEqualTo(json(ZERO_COUNTS));
        OffsetDateTime created = OffsetDateTime.parse((String) run.get("createdAt"));
        OffsetDateTime updated = OffsetDateTime.parse((String) run.get("updatedAt"));
        assertThat(created.getOffset()).isEqualTo(ZoneOffset.UTC);
        assertThat(updated).isEqualTo(created);
    }

    // #1 stored row
    @Test
    void storesTheRunWithSessionConfigurationAndDeadline() throws Exception {
        String sid = connectedSid();
        Map<String, Object> run = startOk(sid, A);
        String id = (String) run.get("id");
        Map<String, Object> row = jdbc.queryForMap(
            "select cast(session_id as text) sid, kind, cast(configuration as text) cfg,"
                + " extract(epoch from (deadline_at - created_at)) secs from generation_run where id = cast(? as uuid)", id);
        assertThat(row.get("sid")).isEqualTo(sid);
        assertThat(row.get("kind")).isEqualTo("STANDARD");
        assertThat(json((String) row.get("cfg"))).isEqualTo(json(A));
        assertThat(((Number) row.get("secs")).doubleValue()).isEqualTo(180.0);
    }

    // #1 NFR-1 scan extended to the new table
    @Test
    void noRunColumnContainsATokenValue() throws Exception {
        String sid = connectedSid();
        startOk(sid, A);
        assertThat(stub.issued).isNotEmpty();
        List<String> rows = jdbc.queryForList("select cast(to_jsonb(g) as text) from generation_run g", String.class);
        assertThat(rows).isNotEmpty();
        for (String v : stub.issued) {
            assertThat(rows).noneMatch(r -> r.contains(v));
        }
    }

    // #2
    @Test
    void unknownPropertiesAreIgnoredAndNotEchoed() throws Exception {
        String sid = connectedSid();
        String withFoo = B.replaceFirst("\\{", "{\"foo\":1,");
        Map<String, Object> run = startOk(sid, withFoo);
        assertThat(run.get("status")).isEqualTo("QUEUED");
        assertThat(run.get("configuration")).isEqualTo(json(B));
        assertThat(((Map<?, ?>) run.get("configuration")).containsKey("foo")).isFalse();
    }

    // #4
    @Test
    void secondStartWhileActiveIs409AndStoresNothing() throws Exception {
        String sid = connectedSid();
        startOk(sid, B);
        int before = runCount();
        assertError(startRun(sid, B), 409, "RUN_ALREADY_ACTIVE", ACTIVE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // #5
    @Test
    void invalidBodyIsReportedBeforeTheActiveRunCheck() throws Exception {
        String sid = connectedSid();
        startOk(sid, B);
        int before = runCount();
        assertError(startRun(sid, B.replace("\"darkness\":5", "\"darkness\":11")), 400, "VALIDATION_FAILED",
            "darkness must be between 1 and 10");
        assertThat(runCount()).isEqualTo(before);
    }

    // #6
    @Test
    void theActiveRunRuleIsPerSession() throws Exception {
        String x = connectedSid();
        String y = connectedSid();
        Map<String, Object> rx = startOk(x, B);
        Map<String, Object> ry = startOk(y, B);
        assertThat(ry.get("id")).isNotEqualTo(rx.get("id"));
    }

    // #7
    @Test
    void concurrentStartsYieldExactlyOneRun() throws Exception {
        String sid = connectedSid();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<int[]> call = () -> {
                go.await();
                var r = startRun(sid, B).andReturn().getResponse();
                return new int[] {r.getStatus(), r.getContentAsString().contains("RUN_ALREADY_ACTIVE") ? 1 : 0};
            };
            List<Future<int[]>> fs = new ArrayList<>();
            fs.add(pool.submit(call));
            fs.add(pool.submit(call));
            go.countDown();
            List<Integer> statuses = new ArrayList<>();
            int conflictBodies = 0;
            for (Future<int[]> f : fs) {
                int[] r = f.get();
                statuses.add(r[0]);
                conflictBodies += r[1];
            }
            assertThat(statuses).containsExactlyInAnyOrder(202, 409);
            assertThat(conflictBodies).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        Integer active = jdbc.queryForObject(
            "select count(*) from generation_run where cast(session_id as text) = ? and status in ('QUEUED','RUNNING')",
            Integer.class, sid);
        assertThat(active).isEqualTo(1);
    }

    // #9
    @Test
    void withoutCookieIs401NotConnectedAndStoresNothing() throws Exception {
        int before = runCount();
        assertError(startRun(null, B), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
        assertError(startRun(newSid(), B), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // #10
    @Test
    void failedRefreshIs401SessionExpiredAndStoresNothing() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? com.oracul.app.chatgpt.StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
            : stub.ok(30, com.oracul.app.chatgpt.StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        int before = runCount();
        assertError(startRun(sid, B), 401, "CHATGPT_SESSION_EXPIRED", EXPIRED_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // #11
    @Test
    void planNotEligibleIs403AndStoresNothing() throws Exception {
        stub.responder = r -> stub.ok(3600, "openid", true);
        String sid = connectedSid();
        int before = runCount();
        assertError(startRun(sid, B), 403, "CHATGPT_PLAN_NOT_ELIGIBLE", NOT_ELIGIBLE_MSG);
        assertThat(runCount()).isEqualTo(before);
    }

    // #12
    @Test
    void validationComesBeforeTheConnectionCheck() throws Exception {
        int before = runCount();
        assertError(startRun(null, B.replace("\"1y\"", "\"3y\"")), 400, "VALIDATION_FAILED", "unknown horizon");
        assertThat(runCount()).isEqualTo(before);
    }

    // #13
    @Test
    void connectionIsCheckedBeforeTheActiveRunRule() throws Exception {
        String sid = connectedSid();
        startOk(sid, B);
        disconnect(sid).andExpect(status().is2xxSuccessful());
        assertError(startRun(sid, B), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
    }
}
