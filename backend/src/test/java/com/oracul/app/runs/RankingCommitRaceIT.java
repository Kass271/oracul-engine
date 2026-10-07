package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.research.EvidencePackService;
import jakarta.servlet.http.Cookie;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The RANKING commit of the pipeline (evidence pack, ranking of the events and storePack in ONE guarded transaction, FR-57)
 * racing a stop (FR-45) or the deadline (FR-32): the pack build is held, the run is ended, the build is released. The guarded
 * commit must roll back as a whole (no evidence_pack row, no ranking, no evidence_pack_id); an abandoned run ends RUN_TIMEOUT
 * only when it is still RUNNING past its deadline and stays STOPPED otherwise.
 */
// @trace FR-57, FR-45, FR-32
class RankingCommitRaceIT extends AbstractDeadlineIT {

    @MockitoSpyBean
    EvidencePackService packService;

    private final CountDownLatch arrived = new CountDownLatch(1);
    private final CountDownLatch open = new CountDownLatch(1);

    /** Holds the pack build of the run: it is called at stage RANKING, before the guarded commit. */
    private void holdPackBuild() {
        doAnswer(inv -> {
            arrived.countDown();
            open.await(60, TimeUnit.SECONDS);
            return inv.callRealMethod();
        }).when(packService).build(any(), any(), any(), any(), any(), any(), any());
    }

    @AfterEach
    void openBuild() {
        open.countDown();
    }

    private String startAtRanking(String[] idOut) throws Exception {
        freshStubs();
        holdPackBuild();
        String sid = connectedSid();
        idOut[0] = (String) startOk(sid, A).get("id");
        assertThat(arrived.await(20, TimeUnit.SECONDS)).as("the pack build of the RANKING stage was reached").isTrue();
        assertThat(row(idOut[0]).get("stage")).isEqualTo("RANKING");
        return sid;
    }

    private void assertNothingCommitted(String id) {
        assertThat(jdbc.queryForObject("select count(*) from evidence_pack where run_id = cast(? as uuid)", Integer.class, id))
            .as("the rolled back RANKING commit leaves no evidence_pack row").isZero();
        assertThat(jdbc.queryForObject("select count(*) from generation_run where id = cast(? as uuid) and evidence_pack_id is null",
            Integer.class, id)).as("evidence_pack_id stays null").isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from event where run_id = cast(? as uuid) and "
            + "(ranking is not null or selection_section is not null or evidence_id is not null or excluded_reason is not null)",
            Integer.class, id)).as("no event got a ranking, selection or exclusion").isZero();
        assertThat(jdbc.queryForObject("select count(*) from generation_run where id = cast(? as uuid) and evidence_note_kind is not null",
            Integer.class, id)).as("no evidence note was stored").isZero();
    }

    // stopped at the RANKING commit; past the deadline or not, STOPPED is never overwritten by RUN_TIMEOUT
    @ParameterizedTest(name = "stop at the RANKING commit, deadline passed: {0}")
    @ValueSource(booleans = {false, true})
    void aStopAtTheRankingCommitRollsItBackAndStaysStopped(boolean pastDeadline) throws Exception {
        String[] id = new String[1];
        String sid = startAtRanking(id);
        MvcResult stop = mvc.perform(post("/api/runs/" + id[0] + "/stop").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(stop.getResponse().getStatus()).as(stop.getResponse().getContentAsString()).isEqualTo(200);
        Map<String, Object> stopped = runOf(sid, id[0]);
        assertThat(stopped.get("status")).isEqualTo("STOPPED");
        assertThat(stopped.get("stage")).isEqualTo("RANKING");
        if (pastDeadline) {
            clock.advance(d(600));
        }
        Map<String, Object> before = row(id[0]);

        open.countDown();
        watchUnchanged(id[0], before, 2500, () -> assertThat(requests("SCENARIO_GENERATION"))
            .as("no later stage runs for a stopped run").isEmpty());
        assertNothingCommitted(id[0]);
        Map<String, Object> run = runOf(sid, id[0]);
        assertThat(run.get("status")).isEqualTo("STOPPED");
        assertThat(run.get("failure")).isNull();
        assertThat(run.get("stage")).isEqualTo("RANKING");
        assertResultNotReady(getResult(sid, id[0]));
        assertNotReady(getPack(sid, id[0]));
        assertSlotReleased(sid);
    }

    // the deadline passes while the pack is built: the guarded commit fails, the abandoned run ends RUN_TIMEOUT
    @Test
    void aDeadlineAtTheRankingCommitRollsItBackAndTheAbandonedRunTimesOut() throws Exception {
        String[] id = new String[1];
        String sid = startAtRanking(id);
        clock.advance(d(180));

        open.countDown();
        Map<String, Object> run = awaitRun(sid, id[0], 10_000, m -> "FAILED".equals(m.get("status")));
        assertTimedOut(run, "RANKING", 6);
        assertThat(run.get("headline")).isNull();
        assertNothingCommitted(id[0]);
        Map<String, Object> before = row(id[0]);
        watchUnchanged(id[0], before, 2500, () -> assertThat(requests("SCENARIO_GENERATION"))
            .as("no later stage runs for a timed-out run").isEmpty());
        assertResultNotReady(getResult(sid, id[0]));
        assertNotReady(getPack(sid, id[0]));
        assertSlotReleased(sid);
    }
}
