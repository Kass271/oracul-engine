package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.result.StoryFixtures;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.time.Duration;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-04 story-rewrite.md FR-67, pipeline half: a REWRITE run sends only STORY_WRITING requests built from the scenario
 * run's accepted scenario and the rewrite's own style and language, writes only its own story and leaves the parent and the
 * scenario run untouched; the reads of a rewrite; the failures of stage 10.
 */
// @trace FR-67, FR-65
class RewriteRunIT extends AbstractRewriteIT {

    private static final String KAFKA = "Narrative — inspired by Franz Kafka";
    private static final String STORY_INVALID_MSG = "ORACUL could not construct a valid scenario";

    private List<StubResponses.Request> newStoryRequests(int storyBefore) {
        List<StubResponses.Request> all = requests(STORY);
        return all.subList(storyBefore, all.size());
    }

    /** The configuration without the two keys a rewrite replaces. */
    private static Map<String, Object> withoutStyle(Map<String, Object> configuration) {
        Map<String, Object> copy = new LinkedHashMap<>(configuration);
        Map<String, Object> out = new LinkedHashMap<>(map(copy.get("output")));
        out.remove("writingStyle");
        out.remove("language");
        copy.put("output", out);
        return copy;
    }

    private static String scenarioBlock(String input) {
        String block = StubResponses.dataBlock(input, "structured-scenario");
        assertThat(block).as("structured-scenario block").isNotEmpty();
        return block;
    }

    // ---- the pipeline ---------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void aRewriteSendsOnlyOneStoryRequestOnTheParentsScenarioAndSearchesNothing() throws Exception {
        Ran p = parent();
        String parentInput = s(1);
        String parentResult = resultRaw(p);
        Map<String, Object> parentSnapshot = snapshot(p.id());
        int total0 = responses.requests.size();
        int story0 = requests(STORY).size();
        int rss0 = news.requests.size();
        int pages0 = news.googlePageRequests.size();
        int decodes0 = news.decodeRequests.size();
        int articles0 = news.articleRequests.size();

        Set<Object> stageIndexes = new HashSet<>();
        Map<String, Object> created = rewriteOk(p.sid(), p.id(), body("kafka", "uk"));
        String id = (String) created.get("id");
        Map<String, Object> run = awaitEnded(p.sid(), id, stageIndexes);

        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("kind")).isEqualTo("REWRITE");
        assertThat(run.get("stage")).isEqualTo("WRITING_STORY");
        assertThat(run.get("stageIndex")).isEqualTo(10);
        assertThat(run.get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA, "uk"));
        assertThat(run.get("completedAt")).isNotNull();
        assertThat(run.get("failure")).isNull();
        assertThat(stageIndexes).as("a rewrite is observed only at stageIndex 0 and 10").isSubsetOf(0, 10);

        // exactly one Responses request, a STORY_WRITING one; no news / article / Google request
        assertThat(purposes().subList(total0, purposes().size())).containsExactly(STORY);
        assertThat(news.requests).hasSize(rss0);
        assertThat(news.googlePageRequests).hasSize(pages0);
        assertThat(news.decodeRequests).hasSize(decodes0);
        assertThat(news.articleRequests).hasSize(articles0);

        // the input differs from the parent's attempt-1 input only inside the WRITING STYLE section
        String input = newStoryRequests(story0).get(0).inputText();
        assertThat(input).contains(sectionOf("kafka", "uk") + "\nTASK\n");
        assertThat(parentInput).contains(sectionOf("official-briefing", "en") + "\nTASK\n");
        assertThat(input).isNotEqualTo(parentInput);
        assertThat(withoutStyleSection(input)).isEqualTo(withoutStyleSection(parentInput));
        assertThat(scenarioBlock(input)).isEqualTo(scenarioBlock(parentInput));
        // @trace FR-65: post-release correction 2026-10-08 - the instructions end with the rewrite's own section, so they
        // differ from the parent's and are equal once that trailing section is removed
        String rewriteInstructions = instructionsOf(newStoryRequests(story0).get(0));
        String parentInstructions = instructionsOf(sreq(1));
        assertThat(rewriteInstructions).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + sectionOf("kafka", "uk"));
        assertThat(parentInstructions).isEqualTo(StoryFixtures.INSTRUCTIONS + "\n" + sectionOf("official-briefing", "en"));
        assertThat(rewriteInstructions).isNotEqualTo(parentInstructions);
        assertThat(StoryFixtures.withoutTrailingSection(rewriteInstructions, sectionOf("kafka", "uk")))
            .isEqualTo(StoryFixtures.withoutTrailingSection(parentInstructions, sectionOf("official-briefing", "en")));
        assertThat(input.lines().filter("WRITING STYLE"::equals).count()).isEqualTo(1);

        // the rows of the rewrite: its own story and STORY_WRITING model call only
        assertThat(rowsOf("future_story", id)).isEqualTo(1);
        Map<String, Object> story = jdbc.queryForMap("select headline, dateline, cast(future_date as text) as future_date from future_story "
            + "where run_id = cast(? as uuid)", id);
        assertThat(story.get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA, "uk"));
        assertThat(story.get("dateline")).isEqualTo(StoryFixtures.ukDateline(LocalDate.parse((String) story.get("future_date"))));
        List<Map<String, Object>> calls = jdbc.queryForList("select purpose, attempt from model_call where run_id = cast(? as uuid) order by id", id);
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).get("purpose")).isEqualTo("STORY_WRITING");
        assertThat(calls.get(0).get("attempt")).isEqualTo(1);
        for (String table : List.of("scenario_attempt", "source", "event", "evidence_pack")) {
            assertThat(rowsOf(table, id)).as(table + " rows of the rewrite").isZero();
        }
        assertThat(finalAttempt(id)).isNull();

        // the parent is untouched, byte for byte, and so is its served result
        assertThat(snapshot(p.id())).isEqualTo(parentSnapshot);
        assertThat(resultRaw(p)).isEqualTo(parentResult);
        assertThat(json(getRun(p.sid(), p.id()))).isEqualTo(p.run());
    }

    // ---- chains -----------------------------------------------------------------------------------------------------

    private static final List<String[]> CHAIN = List.of(new String[] {"kafka", "uk"}, new String[] {"poe", "en"},
        new String[] {"neutral-narrative", "uk"});

    static Stream<Arguments> chainLengths() {
        return Stream.of(1, 2, 3).map(Arguments::of);
    }

    // Ranges & invariants: chains of length 1, 2 and 3 (rewrite of a rewrite of a rewrite): every STORY_WRITING request carries the
    // original's structured-scenario block and differs from the original's attempt-1 input only inside the WRITING STYLE section;
    // per rewrite 1 STORY_WRITING request, 0 other Responses requests and 0 news requests; scenarioRunId is never a rewrite.
    // @trace FR-67
    @ParameterizedTest(name = "chain of {0} rewrite(s)")
    @MethodSource("chainLengths")
    void theChainsKeepTheOriginalsScenarioAndPack(int length) throws Exception {
        Ran original = parent();
        String originalInput = s(1);
        Map<String, Object> originalScenario = structured(original);
        Ran current = original;
        for (int i = 0; i < length; i++) {
            String profileId = CHAIN.get(i)[0];
            String language = CHAIN.get(i)[1];
            String where = "step " + (i + 1) + " " + profileId + "/" + language;
            int total0 = responses.requests.size();
            int story0 = requests(STORY).size();
            int rss0 = news.requests.size();
            int pages0 = news.googlePageRequests.size();
            int articles0 = news.articleRequests.size();
            Map<String, Object> parentRun = json(getRun(current.sid(), current.id()));

            Ran r = rewriteRun(current, body(profileId, language));

            assertThat(r.run().get("status")).as(where + ": " + r.run()).isEqualTo("COMPLETED");
            assertThat(r.run().get("kind")).as(where).isEqualTo("REWRITE");
            assertThat(r.run().get("parentRunId")).as(where).isEqualTo(current.id());
            assertThat(r.run().get("scenarioRunId")).as(where + ": the original STANDARD run").isEqualTo(original.id());
            assertThat(jdbc.queryForObject("select kind from generation_run where id = cast(? as uuid)", String.class,
                r.run().get("scenarioRunId"))).isIn("STANDARD", "ALTERNATIVE");
            assertThat(r.run().get("evidencePackId")).as(where).isEqualTo(parentRun.get("evidencePackId")).isEqualTo(original.run().get("evidencePackId"));
            assertThat(r.run().get("counts")).as(where).isEqualTo(parentRun.get("counts"));
            assertThat(withoutStyle(configurationOf(r.run()))).as(where + ": configuration equals the parent's except style and language")
                .isEqualTo(withoutStyle(configurationOf(parentRun)));
            Map<String, Object> out = map(configurationOf(r.run()).get("output"));
            assertThat(out.get("writingStyle")).as(where).isEqualTo(json(styleJson(modeOf(profileId), profileId)));
            assertThat(out.get("language")).as(where).isEqualTo(language);

            assertThat(purposes().subList(total0, purposes().size())).as(where).containsExactly(STORY);
            assertThat(news.requests).as(where).hasSize(rss0);
            assertThat(news.googlePageRequests).as(where).hasSize(pages0);
            assertThat(news.articleRequests).as(where).hasSize(articles0);
            String input = newStoryRequests(story0).get(0).inputText();
            assertThat(scenarioBlock(input)).as(where).isEqualTo(scenarioBlock(originalInput));
            assertThat(withoutStyleSection(input)).as(where).isEqualTo(withoutStyleSection(originalInput));
            assertThat(input).as(where).contains(sectionOf(profileId, language) + "\nTASK\n");
            assertThat(structured(r)).as(where).isEqualTo(originalScenario);
            assertThat(map(map(result(r).get("story"))).get("headline")).as(where)
                .isEqualTo(StoryFixtures.styledHeadline(labelOf(profileId), language));
            current = r;
        }
        assertThat(jdbc.queryForObject("select count(*) from generation_run where (scenario_run_id is not null) <> (kind = 'REWRITE')",
            Integer.class)).isZero();
    }

    // @trace FR-67
    @Test
    void aRewriteOfAnAlternativeTellsTheAlternativesOwnScenario() throws Exception {
        Ran p = parent();
        Ran alt = alternativeOf(p);
        assertStoryCompleted(alt.run());
        assertThat(requests(STORY)).hasSize(2);
        String altInput = s(2);
        assertThat(scenarioBlock(altInput)).isNotEqualTo(scenarioBlock(s(1)));

        Ran r = rewriteRun(alt, body("verne", "en"));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(r.run().get("parentRunId")).isEqualTo(alt.id());
        assertThat(r.run().get("scenarioRunId")).isEqualTo(alt.id());
        assertThat(r.run().get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
        assertThat(requests(STORY)).hasSize(3);
        assertThat(scenarioBlock(s(3))).isEqualTo(scenarioBlock(altInput));
        assertThat(scenarioBlock(s(3))).isNotEqualTo(scenarioBlock(s(1)));
        assertThat(withoutStyleSection(s(3))).isEqualTo(withoutStyleSection(altInput));
        assertThat(structured(r)).isEqualTo(structured(alt));

        // a rewrite of that rewrite still tells the alternative's scenario
        Ran r2 = rewriteRun(r, body("wells", "uk"));
        assertThat(r2.run().get("parentRunId")).isEqualTo(r.id());
        assertThat(r2.run().get("scenarioRunId")).isEqualTo(alt.id());
        assertThat(scenarioBlock(s(4))).isEqualTo(scenarioBlock(altInput));
    }

    // ---- reads ------------------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void theReadsOfARewriteServeItsOwnStoryAndConfigurationOnTheScenarioRunsData() throws Exception {
        Ran p = parent();
        Ran r = rewriteRun(p, body("kafka", "uk"));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");

        assertThat(r.run().get("hasOpenCriticIssues")).isEqualTo(p.run().get("hasOpenCriticIssues"));
        assertThat(structured(r)).as("getStructuredScenario: the scenario run's record, its runId").isEqualTo(structured(p));
        assertThat(structured(r).get("runId")).isEqualTo(p.id());

        Map<String, Object> research = researchBody(r.sid(), r.id());
        Map<String, Object> pResearch = researchBody(p.sid(), p.id());
        assertThat(research.get("researchProfile")).isEqualTo(pResearch.get("researchProfile"));
        assertThat(research.get("searchPlan")).isEqualTo(pResearch.get("searchPlan"));
        assertThat(research.get("runId")).isEqualTo(r.id());
        assertThat(counts(r.run())).isEqualTo(counts(p.run()));
        assertThat(sourceItems(r.sid(), r.id())).isEqualTo(sourceItems(p.sid(), p.id()));
        assertThat(events(r)).isEqualTo(events(p));
        assertThat(pack(r)).isEqualTo(pack(p));

        Map<String, Object> result = result(r);
        Map<String, Object> pResult = result(p);
        assertThat(map(result.get("story")).get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA, "uk"));
        assertThat(map(pResult.get("story")).get("headline")).isEqualTo(StoryFixtures.HEADLINE);
        assertThat(map(result.get("story")).get("body")).isEqualTo(StoryFixtures.ubody(3));
        assertThat(result.get("runId")).isEqualTo(r.id());
        for (String key : List.of("causalChain", "sources", "wildcardGroups", "openCriticIssues", "labels")) {
            assertThat(result.get(key)).as("getFutureResult " + key).isEqualTo(pResult.get(key));
        }
        assertThat(map(result.get("metadata")).get("configuration")).as("the rewrite's own configuration").isEqualTo(r.run().get("configuration"));
        assertThat(map(pResult.get("metadata")).get("configuration")).isEqualTo(p.run().get("configuration"));
    }

    // @trace FR-67
    @Test
    void whileTheRewriteWritesItsStoryIsNotReadyButItsScenarioAlreadyIs() throws Exception {
        Ran p = parent();
        int story0 = requests(STORY).size();
        responses.gate(STORY);
        try {
            Map<String, Object> created = rewriteOk(p.sid(), p.id(), body("poe", "en"));
            String id = (String) created.get("id");
            assertThat(responses.awaitArrived(STORY, story0 + 1, Duration.ofSeconds(10))).as("the STORY_WRITING request arrived").isTrue();
            Map<String, Object> running = json(getRun(p.sid(), id));
            assertThat(running.get("status")).isEqualTo("RUNNING");
            assertThat(running.get("stage")).isEqualTo("WRITING_STORY");
            assertThat(running.get("stageIndex")).isEqualTo(10);
            assertThat(running.get("stageLabel")).isEqualTo("Writing from the future…");
            assertThat(running.get("scenarioRunId")).isEqualTo(p.id());
            assertThat(getStructured(p.sid(), id).andReturn().getResponse().getStatus()).isEqualTo(200);
            assertThat(json(getStructured(p.sid(), id))).isEqualTo(structured(p));
            assertResultNotReady(getResult(p.sid(), id));
            assertThat(rowsOf("future_story", id)).isZero();
        } finally {
            responses.release(STORY);
        }
    }

    // @trace FR-67
    @Test
    void theRecentFuturesListAndThePanelConfigurationFollowTheNewestRewrite() throws Exception {
        Ran p = parent();
        Ran r = rewriteRun(p, body("kafka", "uk"));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        MvcResult list = mvc.perform(get("/api/runs").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(list.getResponse().getStatus()).isEqualTo(200);
        List<Map<String, Object>> items = list(json(list.getResponse().getContentAsString()).get("items"));
        assertThat(items.stream().map(i -> (String) i.get("id")).toList()).containsExactly(r.id(), p.id());
        assertThat(items.get(0).get("kind")).isEqualTo("REWRITE");
        assertThat(items.get(0).get("headline")).isEqualTo(StoryFixtures.styledHeadline(KAFKA, "uk"));

        MvcResult cfg = mvc.perform(get("/api/scenario/configuration").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(cfg.getResponse().getStatus()).isEqualTo(200);
        Map<String, Object> output = map(json(cfg.getResponse().getContentAsString()).get("output"));
        assertThat(output.get("writingStyle")).isEqualTo(json(styleJson("NARRATIVE", "kafka")));
        assertThat(output.get("language")).isEqualTo("uk");
    }

    // ---- the outcomes of stage 10 -----------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void aStoryThatIsInvalidTwiceFailsTheRewriteAndLeavesTheParentAlone() throws Exception {
        Ran p = parent();
        Map<String, Object> parentSnapshot = snapshot(p.id());
        int story0 = requests(STORY).size();
        int total0 = responses.requests.size();
        scriptStory(sfx("ST-149"), sfx("ST-149"));
        Ran r = rewriteRun(p, body("kafka", "uk"));
        assertFailed(r.run(), "INVALID_SCENARIO", STORY_INVALID_MSG, "WRITING_STORY", 10);
        assertThat(absent(r.run(), "headline")).isTrue();
        assertThat(newStoryRequests(story0)).hasSize(2);
        assertThat(purposes().subList(total0, purposes().size())).containsExactly(STORY, STORY);
        assertThat(StubResponses.dataBlock(newStoryRequests(story0).get(1).inputText(), "story-errors"))
            .isEqualTo("body must have 150 to 900 words (found 149)");
        assertThat(storyModelCalls(r.id())).hasSize(2);
        assertThat(rowsOf("future_story", r.id())).isZero();
        assertResultNotReady(getResult(r.sid(), r.id()));
        assertThat(snapshot(p.id())).isEqualTo(parentSnapshot);
        assertThat(json(getRun(p.sid(), p.id()))).isEqualTo(p.run());
    }

    // @trace FR-67
    @Test
    void aRateLimitFailsTheRewriteWithoutARetryAndLeavesTheParentAlone() throws Exception {
        Ran p = parent();
        Map<String, Object> parentSnapshot = snapshot(p.id());
        int story0 = requests(STORY).size();
        always(STORY, StubResponses.status(429, PROVIDER_BODY));
        Ran r = rewriteRun(p, body("kafka", "en"));
        assertFailed(r.run(), "CHATGPT_RATE_LIMITED", "ChatGPT usage limit reached — try again later", "WRITING_STORY", 10);
        assertThat(newStoryRequests(story0)).hasSize(1);
        assertThat(getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString()).doesNotContain("PROVIDER-SECRET");
        assertThat(snapshot(p.id())).isEqualTo(parentSnapshot);
        assertThat(json(getRun(p.sid(), p.id()))).isEqualTo(p.run());
    }

    // @trace FR-67
    @Test
    void aFirstInvalidStoryIsCorrectedOnceAndTheRewriteCompletesWithTwoCalls() throws Exception {
        Ran p = parent();
        Map<String, Object> parentSnapshot = snapshot(p.id());
        int story0 = requests(STORY).size();
        scriptStory(sfx("ST-149"));
        Ran r = rewriteRun(p, body("kafka", "uk"));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<StubResponses.Request> sent = newStoryRequests(story0);
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1).inputText()).contains("Attempt: 2 | Reason: STORY_CORRECTION");
        assertThat(sent.get(0).inputText()).contains(sectionOf("kafka", "uk") + "\nTASK\n");
        assertThat(sent.get(1).inputText()).contains(sectionOf("kafka", "uk") + "\nTASK\n");
        assertThat(scenarioBlock(sent.get(1).inputText())).isEqualTo(scenarioBlock(sent.get(0).inputText()));
        assertThat(storyModelCalls(r.id())).hasSize(2);
        assertThat(rowsOf("future_story", r.id())).isEqualTo(1);
        assertThat(snapshot(p.id())).isEqualTo(parentSnapshot);
    }

    // @trace FR-67
    @ParameterizedTest(name = "bad date answered twice, language {0}")
    @CsvSource({"uk", "en"})
    void anInvalidStoryDateIsRebuiltByTheDateFallbackInTheRewritesLanguage(String language) throws Exception {
        Ran p = parent();
        int story0 = requests(STORY).size();
        scriptStory(sfx("BADDATE"), sfx("BADDATE"));
        Ran r = rewriteRun(p, body("kafka", language));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(newStoryRequests(story0)).hasSize(2);
        Map<String, Object> story = map(result(r).get("story"));
        LocalDate date = LocalDate.parse((String) story.get("futureDate"));
        assertThat(story.get("dateline")).isEqualTo("uk".equals(language) ? StoryFixtures.ukDateline(date) : StoryFixtures.enDateline(date));
        assertThat(story.get("headline")).isEqualTo(StoryFixtures.HEADLINE);
    }
}
