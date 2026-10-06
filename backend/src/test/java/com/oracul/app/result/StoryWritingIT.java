package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.chatgpt.StubOpenAi;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** future-result.md "Slice 09_future-story" ITs #1, #2, #4-#10, #15: the STORY_WRITING stage. */
// @trace FR-23, FR-38, FR-39
class StoryWritingIT extends AbstractStoryIT {

    private static final String STORY_INVALID = "ORACUL could not construct a valid scenario";

    // #1
    @Test
    void theRunCompletesWithTheStoryHeadlineAfterOneStoryRequest() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String c = cutoffDate(r);
        String d = dayAfter(c);
        assertThat(purposes()).containsExactly(EXPANSION, NORMALIZATION, CLASSIFICATION, GEN, "SCENARIO_CRITIC", STORY);
        assertThat(requests(STORY)).hasSize(1);
        StubResponses.Request req = sreq(1);
        Map<String, Object> body = JsonPath.read(req.body(), "$");
        assertThat(body.keySet()).containsExactlyInAnyOrder("model", "instructions", "input", "text", "store", "stream");
        // @trace FR-38
        assertThat(body.get("stream")).isEqualTo(true);
        assertThat(req.headers().get("accept")).isEqualTo("text/event-stream");
        assertThat(body.get("store")).isEqualTo(false);
        assertThat(instructionsOf(req)).isEqualTo(StoryFixtures.INSTRUCTIONS);
        assertThat(instructionsOf(req)).startsWith("You are the scenario reasoning component of ORACUL.\nYou are NOT a researcher.");
        assertThat(body.get("text")).isEqualTo(jsonOf(StoryFixtures.TEXT_FORMAT_JSON));
        assertThat(s(1)).startsWith("ORACUL REQUEST STORY_WRITING\nSETTINGS\nAttempt: 1 | Reason: INITIAL\n"
            + "Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years\n"
            + "Wildcards: New pandemic 8 | Humanoid robot boom 6\n"
            + "Cutoff date: " + c + "\n"
            + "Story date window: after " + c + " and no later than " + plusYears(c, 5) + "\n"
            + "Future event date: " + d);
    }

    // #2 (NFR-3)
    // @trace NFR-3
    @Test
    void noRequestUsesToolsAndTheScenarioIsPassedAsData() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        for (StubResponses.Request any : responses.requests) assertNoToolKeys(any);
        String closed = com.oracul.app.reasoning.ScenarioFixtures.CLOSED_EVIDENCE_MODE;
        assertThat(instructionsOf(requests(GEN).get(0))).startsWith(closed);
        assertThat(instructionsOf(sreq(1))).startsWith(closed);
        String block = StubResponses.dataBlock(s(1), "structured-scenario");
        assertThat(com.oracul.app.reasoning.ReasoningHarness.comparable(block)).isEqualTo(scenarioOf(structuredRaw(r)));
    }

    // #4
    @Test
    void theStoryIsStoredAndTheRequestRecordedWithoutAnyCredential() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String d = dayAfter(cutoffDate(r));
        List<Map<String, Object>> rows = jdbc.queryForList("select headline, dateline, cast(future_date as text) as future_date, body "
            + "from future_story where run_id = cast(? as uuid)", r.id());
        assertThat(rows).hasSize(1);
        Map<String, Object> row = rows.get(0);
        assertThat(row.get("headline")).isEqualTo(StoryFixtures.HEADLINE);
        assertThat(row.get("dateline")).isEqualTo(StoryHarness.dateline(java.time.LocalDate.parse(d)));
        assertThat(row.get("future_date")).isEqualTo(d);
        assertThat(row.get("body")).isEqualTo(StoryFixtures.body(3));
        List<Map<String, Object>> calls = storyModelCalls(r.id());
        assertThat(calls).hasSize(1);
        assertThat(calls.get(0).get("attempt")).isEqualTo(1);
        assertThat(calls.get(0).get("response_status")).isEqualTo(200);
        assertThat(JsonPath.<Object>read((String) calls.get(0).get("body"), "$")).isEqualTo(JsonPath.<Object>read(sreq(1).body(), "$"));
        String all = jdbc.queryForObject("select coalesce(string_agg(cast(f as text), ' '), '') from future_story f", String.class)
            + jdbc.queryForObject("select coalesce(string_agg(cast(m as text), ' '), '') from model_call m", String.class);
        assertThat(all).doesNotContain("Authorization").doesNotContain("Bearer").doesNotContain("STUBSECRET");
        assertThat(stub.issued).isNotEmpty();
        for (String token : stub.issued) assertThat(all).doesNotContain(token);
    }

    // #5
    @Test
    void aStoryOutsideTheWindowGetsOneCorrection() throws Exception {
        scriptStory(sfx("TODAY"), sfx("ST-DEFAULT"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String c = cutoffDate(r);
        assertThat(requests(STORY)).hasSize(2);
        assertThat(s(2)).contains("Attempt: 2 | Reason: STORY_CORRECTION");
        assertThat(s(2)).contains("Your previous story was invalid. Fix the errors listed in story-errors and return the complete story again.");
        assertThat(StubResponses.dataBlock(s(2), "story-errors"))
            .isEqualTo("futureDate " + c + " must be after " + c + " and no later than " + plusYears(c, 5));
        assertThat(instructionsOf(sreq(2))).isEqualTo(instructionsOf(sreq(1)));
        assertThat(result(r).get("story")).isInstanceOf(Map.class);
        assertThat(map(result(r).get("story")).get("futureDate")).isEqualTo(dayAfter(c));
        assertThat(storyModelCalls(r.id())).extracting(m -> m.get("attempt")).containsExactly(1, 2);
    }

    // #6
    @ParameterizedTest(name = "bad date twice: {0}")
    @ValueSource(strings = {"TODAY", "LATE", "BADDATE"})
    void aStoryWithOnlyDateErrorsTwiceFallsBackToTheScenarioFutureDate(String kind) throws Exception {
        scriptStory(sfx(kind), sfx(kind));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String d = dayAfter(cutoffDate(r));
        assertThat(requests(STORY)).hasSize(2);
        Map<String, Object> story = map(result(r).get("story"));
        assertThat(story.get("futureDate")).isEqualTo(d);
        assertThat(story.get("dateline")).isEqualTo(StoryHarness.dateline(java.time.LocalDate.parse(d)));
        assertThat(story.get("headline")).isEqualTo(StoryFixtures.HEADLINE);
        assertThat(story.get("body")).isEqualTo(StoryFixtures.body(3));
        assertThat(map(map(structured(r).get("structuredScenario")).get("futureEvent")).get("date")).isEqualTo(d);
    }

    // #7
    @ParameterizedTest(name = "invalid twice: {0}")
    @ValueSource(strings = {"NOT-JSON", "ST-149", "ST-901", "ST-H161", "ST-HTML", "TOOLS", "EMPTY"})
    void anInvalidStoryTwiceFailsTheRunWithInvalidScenario(String kind) throws Exception {
        scriptStory(sfx(kind), sfx(kind));
        Ran r = runV4(A);
        assertFailed(r.run(), "INVALID_SCENARIO", STORY_INVALID, "WRITING_STORY", 10);
        assertThat(absent(r.run(), "headline")).isTrue();
        assertThat(requests(STORY)).hasSize(2);
        assertThat(storyRows(r.id())).isZero();
        assertResultNotReady(getResult(r.sid(), r.id()));
        assertThat(structured(r).get("accepted")).isEqualTo(true);
        assertThat(startRun(r.sid(), B).andReturn().getResponse().getStatus()).as("slot released").isEqualTo(202);
    }

    // #8
    @Test
    void theCorrectionNamesTheBodyLengthError() throws Exception {
        scriptStory(sfx("ST-149"), sfx("ST-DEFAULT"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(StubResponses.dataBlock(s(2), "story-errors")).isEqualTo("body must have 150 to 900 words (found 149)");
    }

    // #9
    @Test
    void aSecondAnswerWithOnlyDateErrorsKeepsItsHeadlineAndBodyAndFallsBackOnTheDate() throws Exception {
        scriptStory(sfx("ST-149-TODAY"), sfx("TODAY"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String c = cutoffDate(r);
        Map<String, Object> story = map(result(r).get("story"));
        assertThat(story.get("futureDate")).isEqualTo(dayAfter(c));
        assertThat(story.get("body")).isEqualTo(StoryFixtures.body(3));
        assertThat(lines(StubResponses.dataBlock(s(2), "story-errors"))).containsExactly(
            "body must have 150 to 900 words (found 149)",
            "futureDate " + c + " must be after " + c + " and no later than " + plusYears(c, 5));
    }

    // #10
    @ParameterizedTest(name = "story call failure: {0}")
    @ValueSource(strings = {"429", "500", "401"})
    void transportFailuresFailTheRunInStageWritingStory(String kind) throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        String code;
        String message;
        int expectedRequests;
        switch (kind) {
            case "429" -> {
                always(STORY, StubResponses.status(429, PROVIDER_BODY));
                code = "CHATGPT_RATE_LIMITED";
                message = "ChatGPT usage limit reached — try again later";
                expectedRequests = 1;
            }
            case "500" -> {
                always(STORY, StubResponses.status(500, PROVIDER_BODY));
                code = "CHATGPT_UNAVAILABLE";
                message = "ChatGPT is temporarily unavailable — try again in a few minutes";
                expectedRequests = 3;
            }
            default -> {
                stub.responder = req -> "refresh_token".equals(req.form().get("grant_type"))
                    ? StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
                    : stub.ok(3600, StubOpenAi.ALL_SCOPES, true);
                always(STORY, StubResponses.status(401, PROVIDER_BODY));
                code = "CHATGPT_SESSION_EXPIRED";
                message = "ChatGPT session expired — please reconnect";
                expectedRequests = 1;
            }
        }
        Ran r = runWith(sid, A);
        assertFailed(r.run(), code, message, "WRITING_STORY", 10);
        assertThat(requests(STORY)).hasSize(expectedRequests);
        if ("500".equals(kind)) {
            assertThat(sreq(2).body()).isEqualTo(sreq(1).body());
            assertThat(sreq(3).body()).isEqualTo(sreq(1).body());
            assertThat(storyModelCalls(r.id())).hasSize(1);
        }
        assertThat(storyRows(r.id())).isZero();
        assertResultNotReady(getResult(r.sid(), r.id()));
        String seen = getRun(r.sid(), r.id()).andReturn().getResponse().getContentAsString();
        assertThat(seen).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("STUBSECRET");
        if ("401".equals(kind)) {
            String conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
                .andReturn().getResponse().getContentAsString();
            assertThat(json(conn).get("state")).isEqualTo("SESSION_EXPIRED");
        }
    }

    // #15 (NFR-2)
    // @trace NFR-2
    @Test
    void theRunCompletesWithTheHeadlineInUnderTenSeconds() throws Exception {
        news.reset();
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, C_V4);
        String sid = connectedSid();
        long start = System.nanoTime();
        String id = (String) startOk(sid, A).get("id");
        Map<String, Object> run = awaitRun(sid, id, 10_000, m -> "COMPLETED".equals(m.get("status")) || "FAILED".equals(m.get("status")));
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        assertStoryCompleted(run);
        assertThat(elapsedMs).as("wall clock from the 202").isLessThan(10_000);
    }
}
