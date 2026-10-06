package com.oracul.app.reasoning;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubResponses;
import com.oracul.app.result.AbstractStoryIT;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** scenario-reasoning.md "Slice 10_critic" IT #14: the critic call belongs to stage 8, the critic regeneration to stage 9. */
// @trace FR-22
@TestPropertySource(properties = "oracul.run.min-stage-duration=PT2S")
class CriticStagesIT extends AbstractStoryIT {

    private static final String CRITIC = "SCENARIO_CRITIC";

    @Test
    void theCriticRunsInStageEightAndTheRegenerationInStageNine() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        Function<StubResponses.Request, StubResponses.Reply> fallback = responses.defaultResponder();
        always(CRITIC, req -> calls.getAndIncrement() == 0 ? StubResponses.completed(StubResponses.criticFixture("CR-ICS")) : fallback.apply(req));
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        List<String> stages = new ArrayList<>();
        int criticAtFirstNine = -1;
        int genAtFirstTen = -1;
        List<Integer> genWhileEight = new ArrayList<>();
        Map<String, Object> run = Map.of();
        long end = System.currentTimeMillis() + 90_000;
        while (System.currentTimeMillis() < end) {
            run = json(getRun(sid, id).andReturn().getResponse().getContentAsString());
            String stage = (String) run.get("stage");
            if (stage != null && (stages.isEmpty() || !stages.get(stages.size() - 1).equals(stage))) {
                stages.add(stage);
                if ("CONSTRUCTING_SCENARIO".equals(stage)) criticAtFirstNine = requests(CRITIC).size();
                if ("WRITING_STORY".equals(stage)) genAtFirstTen = requests(GEN).size();
            }
            if ("CHALLENGING_ASSUMPTIONS".equals(stage)) genWhileEight.add(requests(GEN).size());
            if ("COMPLETED".equals(run.get("status")) || "FAILED".equals(run.get("status"))) break;
            Thread.sleep(200);
        }
        assertThat(run.get("status")).as("run: " + run + " stages: " + stages).isEqualTo("COMPLETED");
        List<String> seen = stages.stream()
            .filter(s -> List.of("CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO", "WRITING_STORY").contains(s)).toList();
        assertThat(seen).containsExactly("CHALLENGING_ASSUMPTIONS", "CONSTRUCTING_SCENARIO", "WRITING_STORY");
        assertThat(criticAtFirstNine).as("critic request(s) received before stage 9 was observed").isGreaterThanOrEqualTo(1);
        assertThat(genWhileEight).as("no regeneration while stage 8").allMatch(n -> n == 1);
        assertThat(genAtFirstTen).as("regeneration done before stage 10").isEqualTo(2);
    }
}
