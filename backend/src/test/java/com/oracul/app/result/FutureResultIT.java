package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** future-result.md "Slice 09_future-story" ITs #3, #12 (empty pack, rejected), #13, #14: getFutureResult. */
// @trace FR-23, FR-25
class FutureResultIT extends AbstractStoryIT {

    // #3
    @Test
    void aCompletedRunServesTheFullFutureResult() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String d = dayAfter(cutoffDate(r));
        Map<String, Object> res = result(r);
        assertThat(res.keySet())
            .containsExactlyInAnyOrder("runId", "generationId", "labels", "story", "metadata", "causalChain",
            "sources", "research", "openCriticIssues");
        assertThat(res.get("runId")).isEqualTo(r.id());
        assertThat(res.get("generationId")).isEqualTo(r.run().get("generationId"));
        assertThat(res.get("labels")).isEqualTo(List.of("AI-GENERATED FUTURE SCENARIO", "POSSIBLE FUTURE — NOT CURRENT NEWS"));
        Map<String, Object> story = map(res.get("story"));
        assertThat(story).isEqualTo(Map.of("headline", "Stub headline from the future",
            "dateline", StoryHarness.dateline(java.time.LocalDate.parse(d)), "futureDate", d, "body", StoryFixtures.body(3)));
        assertThat(story.get("dateline")).isNotEqualTo("STUB DATELINE");

        Map<String, Object> meta = map(res.get("metadata"));
        assertThat(meta.get("configuration")).isEqualTo(json(A));
        assertThat(meta.get("horizonLabel")).isEqualTo("5 years");
        assertThat(meta.get("wildcards")).isEqualTo(jsonOf("[{\"label\":\"New pandemic\",\"intensity\":8,\"custom\":false},"
            + "{\"label\":\"Humanoid robot boom\",\"intensity\":6,\"custom\":false}]"));
        Map<String, Object> counts = counts(r.run());
        assertThat(meta.get("counts")).isEqualTo(counts);
        assertThat(map(res.get("research")).get("counts")).isEqualTo(counts);
        assertThat(num(counts.get("articlesConsidered"))).isGreaterThan(0);
        assertThat(num(counts.get("uniqueEvents"))).isGreaterThan(0);
        assertThat(num(counts.get("eventsSelected"))).isGreaterThan(0);

        assertThat(res.get("causalChain")).isEqualTo(map(structured(r).get("structuredScenario")).get("causalChain"));

        List<Map<String, Object>> sources = list(res.get("sources"));
        assertThat(sources).hasSize(((Number) counts.get("eventsSelected")).intValue());
        List<String> ids = sources.stream().map(x -> (String) x.get("evidenceId")).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        for (Map<String, Object> src : sources) {
            assertThat(src.keySet()).contains("evidenceId", "section", "title", "publisher", "url", "usedInScenario", "counterSignal");
            assertThat(src.get("counterSignal")).isEqualTo("COUNTER_SIGNAL".equals(src.get("section")));
        }
        assertThat(sources.stream().filter(x -> Boolean.TRUE.equals(x.get("usedInScenario"))).count())
            .isEqualTo(((Number) counts.get("sourcesUsed")).longValue());
        assertThat(map(res.get("research")).get("intents")).isEqualTo(map(researchBody(r.sid(), r.id()).get("searchPlan")).get("intents"));
        assertThat(res.get("openCriticIssues")).isEqualTo(List.of());
    }

    // #14
    @Test
    void aOneDayRunHasATomorrowWindowAndNoWildcards() throws Exception {
        Ran r = runV4(withHorizon(B, "1d"));
        assertStoryCompleted(r.run());
        String c = cutoffDate(r);
        assertThat(s(1)).contains("Story date window: after " + c + " and no later than " + dayAfter(c) + "\n");
        Map<String, Object> res = result(r);
        assertThat(map(res.get("story")).get("futureDate")).isEqualTo(dayAfter(c));
        Map<String, Object> meta = map(res.get("metadata"));
        assertThat(meta.get("horizonLabel")).isEqualTo("Tomorrow");
        assertThat(meta.get("wildcards")).isEqualTo(List.of());
    }

    // #12 (empty pack; run-control.md FR-47: speculative mode writes a story and a result with no sources)
    // @trace FR-47
    @Test
    void aRunWithAnEmptyPackHasASpeculativeResultWithoutSources() throws Exception {
        Ran r = run(A); // default empty news feed: COMPLETED with headline and story
        assertStoryCompleted(r.run());
        assertThat(requests(STORY)).hasSize(1);
        assertThat(storyRows(r.id())).isEqualTo(1);
        Map<String, Object> res = result(r);
        assertThat(list(res.get("sources"))).isEmpty();
        assertThat(map(res.get("story")).get("headline")).isEqualTo("Stub headline from the future");
        assertThat(num(counts(r.run()).get("articlesConsidered"))).isZero();
    }

    // #12 (rejected scenario)
    @Test
    void aRejectedScenarioHasNoResult() throws Exception {
        scriptScenario(fx("SC-BAD"), fx("SC-BAD"));
        Ran r = runV4(A);
        assertThat(r.run().get("status")).isEqualTo("FAILED");
        assertThat(requests(STORY)).isEmpty();
        assertResultNotReady(getResult(r.sid(), r.id()));
    }

    // #13
    @ParameterizedTest(name = "unknown run [{0}]")
    @ValueSource(strings = {NO_RUN, "abc"})
    void anUnknownOrMalformedRunIs404(String id) throws Exception {
        String sid = connectedSid();
        assertRunNotFound(getResult(sid, id));
    }

    // #13
    @Test
    void aRunOfAnotherSessionIs404() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String other = connectedSid();
        assertRunNotFound(getResult(other, r.id()));
        assertThat(getResult(r.sid(), r.id()).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // #13
    @Test
    void aRequestWithoutACookieIs404() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertRunNotFound(getResult(null, r.id()));
    }
}
