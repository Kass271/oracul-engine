package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubGdelt;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** generation-runs.md "Slice 11_run-failures" RunStartupSweepIT rows 1-3: active runs become RUN_INTERRUPTED. */
// @trace FR-32
class RunStartupSweepIT extends AbstractDeadlineIT {

    private static final String INTERRUPTED = "{\"code\":\"RUN_INTERRUPTED\",\"message\":\"" + I + "\"}";

    private record Setup(String sx, String x, String sy, String y, String sz, String z, Map<String, Object> yRow,
                         Map<String, Object> zRow) {}

    private Setup threeSessions() throws Exception {
        freshStubs();
        String sy = connectedSid();
        String y = (String) runWith(sy, A).run().get("id");
        gdelt.reset();
        gdelt.responder = req -> StubGdelt.status(503);
        String sz = connectedSid();
        String z = (String) runWith(sz, A).run().get("id");
        freshStubs();
        responses.gate("QUERY_EXPANSION");
        String sx = connectedSid();
        String x = (String) startOk(sx, A).get("id");
        assertThat(responses.awaitArrived("QUERY_EXPANSION", 1, Duration.ofSeconds(10))).isTrue();
        return new Setup(sx, x, sy, y, sz, z, row(y), row(z));
    }

    // #1
    @Test
    void anActiveRunIsFailedAsInterruptedAndTerminalRunsStay() throws Exception {
        Setup s = threeSessions();
        assertThat(sweep(STARTUP)).isEqualTo(1);
        Map<String, Object> x = runOf(s.sx(), s.x());
        assertThat(x.get("status")).isEqualTo("FAILED");
        assertThat(x.get("failure")).isEqualTo(json(INTERRUPTED));
        assertThat(x.get("completedAt")).isNotNull();
        assertThat(x.get("stage")).isEqualTo("RESEARCH_STRATEGY");
        assertThat(row(s.y())).isEqualTo(s.yRow());
        assertThat(row(s.z())).isEqualTo(s.zRow());
    }

    // #2
    @Test
    void theInterruptedRunDoesNothingAfterTheGateIsReleased() throws Exception {
        Setup s = threeSessions();
        assertThat(sweep(STARTUP)).isEqualTo(1);
        responses.release("QUERY_EXPANSION");
        int gdeltBefore = gdelt.requests.size();
        Map<String, Object> before = row(s.x());
        watchUnchanged(s.x(), before, 2500, () -> assertThat(gdelt.requests).hasSize(gdeltBefore));
        assertSlotReleased(s.sx());
    }

    // #3
    @Test
    void aQueuedRowIsInterruptedToo() throws Exception {
        String sid = newSid();
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, "
                + "deadline_at, created_at, updated_at) values (?, ?, cast(? as uuid), 'STANDARD', 'QUEUED', cast(? as jsonb), "
                + "cast(? as jsonb), ?, ?, ?)",
            id, "ORC-SWEEP-" + id.toString().substring(0, 6), sid, B, ZERO_COUNTS, now.plusSeconds(180), now, now);
        assertThat(sweep(STARTUP)).isEqualTo(1);
        Map<String, Object> run = runOf(sid, id.toString());
        assertThat(run.get("status")).isEqualTo("FAILED");
        assertThat(run.get("failure")).isEqualTo(json(INTERRUPTED));
        assertThat(run.get("stageIndex")).isEqualTo(0);
    }
}
