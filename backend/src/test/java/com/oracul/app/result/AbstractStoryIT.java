package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.reasoning.AbstractReasoningIT;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/**
 * Shared driver of the slice 09 tests (future-result.md "Slice 09_future-story"): scripted STORY_WRITING answers keyed by
 * the request number of that purpose, getFutureResult, future_story / model_call inspection.
 */
@TestPropertySource(properties = {
    "oracul.run.min-stage-duration=PT0S",
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.openai.retry-delay=PT0S",
})
public abstract class AbstractStoryIT extends AbstractReasoningIT {

    protected static final String STORY = "STORY_WRITING";
    protected static final String READY_MSG = "This future is not ready yet";

    /** ST-* / TODAY / LATE / BADDATE answer computed from the request. */
    protected static Answer sfx(String name) {
        return req -> StubResponses.completed(StubResponses.storyFixture(name, req.inputText()));
    }

    /** The 1st, 2nd... STORY_WRITING request are answered by these; later ones get ST-DEFAULT. */
    protected void scriptStory(Answer... answers) {
        AtomicInteger n = new AtomicInteger();
        Function<StubResponses.Request, StubResponses.Reply> fallback = responses.defaultResponder();
        always(STORY, req -> {
            int k = n.getAndIncrement();
            return k < answers.length ? answers[k].apply(req) : fallback.apply(req);
        });
    }

    /** k-th (1-based) STORY_WRITING request. */
    protected StubResponses.Request sreq(int k) {
        List<StubResponses.Request> all = requests(STORY);
        assertThat(all.size()).as("STORY_WRITING requests").isGreaterThanOrEqualTo(k);
        return all.get(k - 1);
    }

    /** Input text of the k-th STORY_WRITING request. */
    protected String s(int k) {
        return sreq(k).inputText();
    }

    protected ResultActions getResult(String sid, String runId) throws Exception {
        var b = get("/api/runs/" + runId + "/result");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    protected String resultRaw(Ran r) throws Exception {
        MvcResult res = getResult(r.sid(), r.id()).andReturn();
        assertThat(res.getResponse().getStatus()).as("getFutureResult: " + res.getResponse().getContentAsString()).isEqualTo(200);
        return res.getResponse().getContentAsString();
    }

    protected Map<String, Object> result(Ran r) throws Exception {
        return json(resultRaw(r));
    }

    @SuppressWarnings("unchecked")
    protected static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    protected static void assertResultNotReady(ResultActions r) throws Exception {
        assertError(r, 409, "RESULT_NOT_READY", READY_MSG);
    }

    protected int storyRows(String runId) {
        return jdbc.queryForObject("select count(*) from future_story where run_id = cast(? as uuid)", Integer.class, runId);
    }

    protected List<Map<String, Object>> storyModelCalls(String runId) {
        return jdbc.queryForList("select attempt, response_status, cast(request_body as text) as body from model_call "
            + "where run_id = cast(? as uuid) and purpose = 'STORY_WRITING' order by attempt", runId);
    }

    /** Cutoff date of the run's pack plus the number of years (window end of a 5y run). */
    protected String plusYears(String date, int years) {
        return LocalDate.parse(date).plusYears(years).toString();
    }

    protected static String dayAfter(String date) {
        return LocalDate.parse(date).plusDays(1).toString();
    }

    protected static void assertStoryCompleted(Map<String, Object> run) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(run.get("stageIndex")).isEqualTo(10);
        assertThat(run.get("headline")).isEqualTo(StoryFixtures.HEADLINE);
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(run.get("failure")).isNull();
    }
}
