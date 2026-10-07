package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * NFR-10 part 2 / wildcard-search.md FR-51 range (f): {@code oracul.search.query-generation-window} = PT1S (the other windows stay
 * above it): QUERY_GENERATION calls still open when the window ends are abandoned and their pipelines use the templates; the plan
 * is stored at the end of the window, the run completes, an answer that arrives later is ignored.
 */
// @trace FR-51, NFR-10
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.search.query-generation-window=PT1S",
})
class QueryGenerationWindowIT extends AbstractEventIT {

    private static final List<String> TEMPLATES = List.of(
        "New pandemic extreme scenario disaster", "New pandemic unprecedented scale catastrophe", "New pandemic radical upheaval collapse",
        "Humanoid robot boom serious disruption crisis", "Humanoid robot boom major escalation conflict", "Humanoid robot boom growing concerns threat");

    @Test
    void everyAnswerHeldGivesTheTemplatePlanWithinTheWindowAndALateAnswerIsIgnored(CapturedOutput out) throws Exception {
        responses.gate(QUERY_GENERATION);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(responses.awaitArrived(QUERY_GENERATION, 2, Duration.ofSeconds(10))).as("both calls are open").isTrue();
        long t0 = System.nanoTime();
        // the answers are still held: the plan appears when the 1 s window is over (spec: about 1 s, at most 3 s)
        Map<String, Object> research = awaitPlan(sid, id, 3000);
        long ms = (System.nanoTime() - t0) / 1_000_000;
        assertThat(ms).as("plan stored about 1 s after the calls started, not after the held answers").isLessThan(3000);
        assertThat(PlanJson.plan(research).get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(PlanJson.pipelines(research)).hasSize(2).allSatisfy(p -> assertThat(p.get("queryMode")).isEqualTo("TEMPLATE_FALLBACK"));
        assertThat(PlanJson.queryTexts(research)).containsExactlyElementsOf(TEMPLATES);

        responses.release(QUERY_GENERATION); // the answers arrive after the window: they are ignored
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> finalResearch = researchBody(sid, id);
        assertThat(PlanJson.plan(finalResearch).get("expansionMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(PlanJson.queryTexts(finalResearch)).as("the late model answers were not used").containsExactlyElementsOf(TEMPLATES);
        assertThat(news.requests.stream().map(StubNews.Request::q).toList()).as("Google was asked the template texts")
            .containsExactlyInAnyOrderElementsOf(TEMPLATES.stream().map(t -> t + " when:90d").toList());
        assertThat(requests(QUERY_GENERATION)).as("no retry").hasSize(2);
        String log = out.getOut() + out.getErr();
        assertThat(log).contains("query generation fell back to templates: pipeline=W01 reason=WINDOW")
            .contains("query generation fell back to templates: pipeline=W02 reason=WINDOW");
    }

    @Test
    void anAnswerInsideTheWindowIsUsedAndOnlyThePipelineThatMissedTheWindowFallsBack() throws Exception {
        responses.responder = req -> {
            if (!QUERY_GENERATION.equals(StubResponses.purpose(req))) return StubResponses.INSTANCE.defaultResponder().apply(req);
            StubResponses.Reply ok = StubResponses.completed(StubResponses.defaultGeneration(req.inputText()));
            return "W02".equals(StubResponses.pipelineOf(req.inputText())) ? StubResponses.delayed(ok, 2500) : ok;
        };
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitDone(sid, id);
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> research = researchBody(sid, id);
        assertThat(PlanJson.plan(research).get("expansionMode")).as("one pipeline kept model queries").isEqualTo("MODEL");
        Map<String, Object> w1 = PlanJson.pipeline(research, "W01");
        Map<String, Object> w2 = PlanJson.pipeline(research, "W02");
        assertThat(w1.get("queryMode")).isEqualTo("MODEL");
        assertThat(PlanJson.queriesOf(w1).stream().map(q -> q.get("text")).toList())
            .containsExactly("W01 stub query 1", "W01 stub query 2", "W01 stub query 3");
        assertThat(w2.get("queryMode")).isEqualTo("TEMPLATE_FALLBACK");
        assertThat(PlanJson.queriesOf(w2).stream().map(q -> q.get("text")).toList()).containsExactlyElementsOf(TEMPLATES.subList(3, 6));
    }
}
