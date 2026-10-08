package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.chatgpt.StubOpenAi;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * phase-04 story-rewrite.md FR-67, endpoint half: POST /api/runs/{runId}/rewrites over HTTP only. Check order 404, 400
 * (not JSON, style, language), 401 / 403 / 503, 409 (not completed, active), 500; nothing is stored on a rejection; the
 * 202 body of every valid style / language combination.
 */
// @trace FR-67
class StartRewriteRunIT extends AbstractRewriteIT {

    // ---- helpers --------------------------------------------------------------------------------------------------

    /** Row count, the parent row and the recorded Responses requests before a call that must change nothing. */
    private record Before(int rows, Map<String, Object> parentRow, int responseRequests) {}

    private Before before(UUID parent) {
        return new Before(runCount(), runRow(parent.toString()), responses.requests.size());
    }

    private void assertNothingStored(Before b, UUID parent) {
        assertThat(runCount()).as("generation_run rows").isEqualTo(b.rows());
        assertThat(runRow(parent.toString())).as("parent row, every column").isEqualTo(b.parentRow());
        assertThat(responses.requests).as("no pipeline request").hasSize(b.responseRequests());
    }

    /** A COMPLETED parent with a story (seeded) in a connected session. */
    private record Seeded(String sid, UUID parent) {}

    private Seeded connectedParent(String status, boolean withStory) throws Exception {
        String sid = connectedSid();
        return new Seeded(sid, seed(sid, status, withStory));
    }

    private Seeded unconnectedParent() throws Exception {
        String sid = newSid();
        return new Seeded(sid, seed(sid, "FAILED", false));
    }

    // ---- 404 ------------------------------------------------------------------------------------------------------

    static Stream<Arguments> unknownRuns() {
        return Stream.of(Arguments.of("unknown UUID", NO_RUN), Arguments.of("abc", "abc"), Arguments.of("123", "123"),
            Arguments.of("not a UUID with dashes", "0000-0000"));
    }

    // @trace FR-67
    @ParameterizedTest(name = "unknown run [{0}] -> 404 RUN_NOT_FOUND")
    @MethodSource("unknownRuns")
    void anUnknownOrMalformedRunIs404(String name, String id) throws Exception {
        String sid = connectedSid();
        int rows = runCount();
        assertError(rewrite(sid, id, body("kafka", "uk")), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertThat(runCount()).isEqualTo(rows);
    }

    static Stream<Arguments> brokenBodies() {
        return Stream.of(Arguments.of("{", "{"), Arguments.of("{}", "{}"), Arguments.of("null", "null"), Arguments.of("empty", ""),
            Arguments.of("not json", "not json"));
    }

    // @trace FR-67
    @ParameterizedTest(name = "unknown UUID with body [{0}] -> 404 wins")
    @MethodSource("brokenBodies")
    void aMissingRunWinsOverEveryBodyProblem(String name, String raw) throws Exception {
        String sid = connectedSid();
        int rows = runCount();
        assertError(rewrite(sid, NO_RUN, raw), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertError(rewrite(newSid(), NO_RUN, raw), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertThat(runCount()).isEqualTo(rows);
    }

    // @trace FR-67
    @ParameterizedTest(name = "malformed id [{0}] with a body that is not JSON -> 404 wins")
    @ValueSource(strings = {"abc", "123"})
    void aMalformedIdWinsOverABrokenBody(String id) throws Exception {
        String sid = connectedSid();
        assertError(rewrite(sid, id, "{"), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertError(rewrite(sid, id, "{}"), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
    }

    // @trace FR-67
    @Test
    void aRunOfAnotherSessionIs404WithAndWithoutCookieEvenForAnUnconnectedSession() throws Exception {
        Seeded owner = connectedParent("COMPLETED", true);
        Before b = before(owner.parent());
        assertError(rewrite(connectedSid(), owner.parent().toString(), body("kafka", "uk")), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertError(rewrite(newSid(), owner.parent().toString(), body("kafka", "uk")), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertError(rewrite(null, owner.parent().toString(), body("kafka", "uk")), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertError(rewrite(connectedSid(), owner.parent().toString(), "{"), 404, "RUN_NOT_FOUND", NOT_FOUND_MSG);
        assertNothingStored(b, owner.parent());
    }

    // ---- 400: body is not JSON --------------------------------------------------------------------------------------

    /** The same 400 with and without a ChatGPT connection (validation precedes the connection and the status checks). */
    private void bad(String message, String raw, MediaType contentType) throws Exception {
        for (Seeded s : List.of(connectedParent("FAILED", false), unconnectedParent())) {
            Before b = before(s.parent());
            assertError(rewriteWith(s.sid(), s.parent().toString(), raw.getBytes(StandardCharsets.UTF_8), contentType), 400,
                "VALIDATION_FAILED", message);
            assertNothingStored(b, s.parent());
        }
    }

    static Stream<Arguments> notJson() {
        return Stream.of(Arguments.of("{", "{"), Arguments.of("not json", "not json"), Arguments.of("{\"writingStyle\":", "{\"writingStyle\":"),
            Arguments.of("empty body", ""));
    }

    // Ranges & invariants: every not-JSON class answers exactly 400 VALIDATION_FAILED "Request body is not valid JSON".
    // @trace FR-67
    @ParameterizedTest(name = "body [{0}] -> Request body is not valid JSON")
    @MethodSource("notJson")
    void aBodyThatIsNotJsonIs400(String name, String raw) throws Exception {
        bad(NOT_JSON_MSG, raw, MediaType.APPLICATION_JSON);
    }

    // @trace FR-67
    @Test
    void aValidJsonTextWithContentTypeTextPlainIs400NotJson() throws Exception {
        bad(NOT_JSON_MSG, body("kafka", "uk"), MediaType.TEXT_PLAIN);
    }

    // ---- 400: writing style ---------------------------------------------------------------------------------------

    private static String withStyle(String styleRaw, String languageRaw) {
        StringBuilder b = new StringBuilder("{");
        if (styleRaw != null) b.append("\"writingStyle\":").append(styleRaw);
        if (languageRaw != null) b.append(styleRaw != null ? "," : "").append("\"language\":").append(languageRaw);
        return b.append('}').toString();
    }

    /** The invalid writingStyle classes of writing-styles.md FR-63 "Ranges & invariants". */
    static List<Arguments> invalidStyleClasses() {
        return List.of(
            Arguments.of("string kafka", "\"kafka\""),
            Arguments.of("empty string", "\"\""),
            Arguments.of("number", "5"),
            Arguments.of("true", "true"),
            Arguments.of("false", "false"),
            Arguments.of("empty array", "[]"),
            Arguments.of("array holding a valid object", "[{\"mode\":\"OFFICIAL\",\"profileId\":\"official-briefing\"}]"),
            Arguments.of("empty object", "{}"),
            Arguments.of("mode missing", "{\"profileId\":\"kafka\"}"),
            Arguments.of("mode null", "{\"mode\":null,\"profileId\":\"kafka\"}"),
            Arguments.of("mode official", "{\"mode\":\"official\",\"profileId\":\"official-briefing\"}"),
            Arguments.of("mode Narrative", "{\"mode\":\"Narrative\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode POETIC", "{\"mode\":\"POETIC\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode empty", "{\"mode\":\"\",\"profileId\":\"kafka\"}"),
            Arguments.of("mode 1", "{\"mode\":1,\"profileId\":\"kafka\"}"),
            Arguments.of("mode array", "{\"mode\":[\"NARRATIVE\"],\"profileId\":\"kafka\"}"),
            Arguments.of("profileId missing", "{\"mode\":\"NARRATIVE\"}"),
            Arguments.of("profileId null", "{\"mode\":\"NARRATIVE\",\"profileId\":null}"),
            Arguments.of("profileId empty", "{\"mode\":\"NARRATIVE\",\"profileId\":\"\"}"),
            Arguments.of("profileId 5", "{\"mode\":\"NARRATIVE\",\"profileId\":5}"),
            Arguments.of("profileId Kafka", "{\"mode\":\"NARRATIVE\",\"profileId\":\"Kafka\"}"),
            Arguments.of("profileId ' kafka'", "{\"mode\":\"NARRATIVE\",\"profileId\":\" kafka\"}"),
            Arguments.of("profileId 'kafka '", "{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka \"}"),
            Arguments.of("profileId dickens (not in the shipped file)", "{\"mode\":\"NARRATIVE\",\"profileId\":\"dickens\"}"),
            Arguments.of("profileId 41 x a", "{\"mode\":\"NARRATIVE\",\"profileId\":\"" + "a".repeat(41) + "\"}"),
            Arguments.of("profileId 5000 x a", "{\"mode\":\"NARRATIVE\",\"profileId\":\"" + "a".repeat(5000) + "\"}"),
            Arguments.of("profileId object", "{\"mode\":\"NARRATIVE\",\"profileId\":{\"id\":\"kafka\"}}"),
            Arguments.of("profileId array", "{\"mode\":\"NARRATIVE\",\"profileId\":[\"kafka\"]}"),
            Arguments.of("OFFICIAL with kafka", styleJson("OFFICIAL", "kafka")),
            Arguments.of("OFFICIAL with neutral-narrative", styleJson("OFFICIAL", "neutral-narrative")),
            Arguments.of("NARRATIVE with official-briefing", styleJson("NARRATIVE", "official-briefing")));
    }

    /** Whole request bodies that fail on the style (the top-level classes of the spec). */
    static List<Arguments> invalidStyleBodies() {
        List<Arguments> out = new ArrayList<>(List.of(
            Arguments.of("JSON null", "null"),
            Arguments.of("JSON array", "[]"),
            Arguments.of("JSON string", "\"kafka\""),
            Arguments.of("JSON number", "1"),
            Arguments.of("JSON true", "true"),
            Arguments.of("empty object", "{}"),
            Arguments.of("only a language", "{\"language\":\"uk\"}"),
            Arguments.of("writingStyle missing", withStyle(null, "\"en\"")),
            Arguments.of("writingStyle null", withStyle("null", "\"uk\""))));
        for (Arguments a : invalidStyleClasses()) {
            out.add(Arguments.of("writingStyle " + a.get()[0], withStyle((String) a.get()[1], "\"en\"")));
        }
        return out;
    }

    // Ranges & invariants: every invalid style class answers exactly 400 VALIDATION_FAILED "Unknown writing style".
    // @trace FR-67
    @ParameterizedTest(name = "{0} -> Unknown writing style")
    @MethodSource("invalidStyleBodies")
    void anInvalidStyleIs400WithAndWithoutAConnection(String name, String raw) throws Exception {
        bad(STYLE_MSG, raw, MediaType.APPLICATION_JSON);
    }

    // @trace FR-67
    @ParameterizedTest(name = "writingStyle {0} wins over a bad language")
    @MethodSource("invalidStyleClasses")
    void aBadStyleIsReportedBeforeABadLanguage(String name, String styleRaw) throws Exception {
        bad(STYLE_MSG, withStyle(styleRaw, "\"de\""), MediaType.APPLICATION_JSON);
        bad(STYLE_MSG, withStyle(styleRaw, null), MediaType.APPLICATION_JSON);
    }

    // Ranges & invariants: the cross-mode grid, 2 modes x 9 profiles: the 9 mismatching cells are Unknown writing style
    // (the 9 matching cells are accepted by the 202 grid below).
    // @trace FR-67
    @Test
    void everyMismatchingModeAndProfileCellIsRejected() throws Exception {
        Seeded s = connectedParent("FAILED", false);
        int rejected = 0;
        for (String mode : List.of("OFFICIAL", "NARRATIVE")) {
            for (String profileId : PROFILE_IDS) {
                if (mode.equals(modeOf(profileId))) continue;
                Before b = before(s.parent());
                assertError(rewrite(s.sid(), s.parent().toString(), body(mode, profileId, "en")), 400, "VALIDATION_FAILED", STYLE_MSG);
                assertNothingStored(b, s.parent());
                rejected++;
            }
        }
        assertThat(rejected).as("mismatching cells of the 2 x 9 grid").isEqualTo(9);
    }

    // ---- 400: language ----------------------------------------------------------------------------------------------

    /** The invalid language classes of writing-styles.md FR-64, plus the two absent ones that are invalid here. */
    static List<Arguments> invalidLanguages() {
        return List.of(
            Arguments.of("absent", null),
            Arguments.of("null", "null"),
            Arguments.of("EN", "\"EN\""),
            Arguments.of("Uk", "\"Uk\""),
            Arguments.of("UK", "\"UK\""),
            Arguments.of("ua", "\"ua\""),
            Arguments.of("uk-UA", "\"uk-UA\""),
            Arguments.of("en-US", "\"en-US\""),
            Arguments.of("de", "\"de\""),
            Arguments.of("empty", "\"\""),
            Arguments.of("' en'", "\" en\""),
            Arguments.of("'uk '", "\"uk \""),
            Arguments.of("number 1", "1"),
            Arguments.of("number 0", "0"),
            Arguments.of("true", "true"),
            Arguments.of("false", "false"),
            Arguments.of("array", "[\"uk\"]"),
            Arguments.of("empty object", "{}"),
            Arguments.of("object with code", "{\"code\":\"uk\"}"));
    }

    // Ranges & invariants: every invalid language class answers exactly 400 VALIDATION_FAILED "Unknown story language".
    // @trace FR-67
    @ParameterizedTest(name = "language {0} -> Unknown story language")
    @MethodSource("invalidLanguages")
    void anInvalidOrMissingLanguageIs400WithAndWithoutAConnection(String name, String languageRaw) throws Exception {
        bad(LANGUAGE_MSG, withStyle(styleJson("NARRATIVE", "poe"), languageRaw), MediaType.APPLICATION_JSON);
        bad(LANGUAGE_MSG, withStyle(styleJson("OFFICIAL", "official-briefing"), languageRaw), MediaType.APPLICATION_JSON);
    }

    // ---- 401 / 403 / 503 ------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void notConnectedIs401EvenWhenTheParentIsNotCompleted() throws Exception {
        String sid = newSid();
        for (String status : List.of("COMPLETED", "FAILED")) {
            UUID parent = seed(sid, status, "COMPLETED".equals(status));
            Before b = before(parent);
            assertError(rewrite(sid, parent.toString(), body("kafka", "uk")), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
            assertNothingStored(b, parent);
        }
    }

    // @trace FR-67
    @Test
    void aDisconnectedSessionIs401AndStoresNothing() throws Exception {
        Ran p = parent();
        disconnect(p.sid());
        Map<String, Object> snap = snapshot(p.id());
        int rows = runCount();
        assertError(rewrite(p.sid(), p.id(), body("kafka", "uk")), 401, "CHATGPT_NOT_CONNECTED", NOT_CONNECTED_MSG);
        assertThat(runCount()).isEqualTo(rows);
        assertThat(snapshot(p.id())).isEqualTo(snap);
    }

    // @trace FR-67
    @Test
    void aFailedRefreshIs401SessionExpired() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? StubOpenAi.status(400, "{\"error\":\"invalid_grant\"}")
            : stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        UUID parent = seed(sid, "COMPLETED", true);
        Before b = before(parent);
        assertError(rewrite(sid, parent.toString(), body("kafka", "uk")), 401, "CHATGPT_SESSION_EXPIRED",
            "ChatGPT session expired — please reconnect");
        assertNothingStored(b, parent);
    }

    // @trace FR-67
    @Test
    void aRefreshThatReportsAnInvalidClientIs401RegistrationInvalid() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? StubOpenAi.status(401, "{\"error\":\"invalid_client\"}")
            : stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        UUID parent = seed(sid, "COMPLETED", true);
        Before b = before(parent);
        MvcResult r = rewrite(sid, parent.toString(), body("kafka", "uk")).andReturn();
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(401);
        assertThat(json(r.getResponse().getContentAsString()).get("code")).isEqualTo("CHATGPT_REGISTRATION_INVALID");
        assertThat(json(r.getResponse().getContentAsString())).hasSize(2);
        assertNothingStored(b, parent);
    }

    // @trace FR-67
    @Test
    void aPlanThatIsNotEligibleIs403() throws Exception {
        stub.responder = r -> stub.ok(3600, "openid", true);
        String sid = connectedSid();
        UUID parent = seed(sid, "COMPLETED", true);
        Before b = before(parent);
        assertError(rewrite(sid, parent.toString(), body("kafka", "uk")), 403, "CHATGPT_PLAN_NOT_ELIGIBLE",
            "Your ChatGPT plan is not eligible for ORACUL");
        assertNothingStored(b, parent);
    }

    // @trace FR-67
    @Test
    void aTransientRefreshFailureIs503() throws Exception {
        stub.responder = r -> "refresh_token".equals(r.form().get("grant_type"))
            ? StubOpenAi.status(503, "{\"error\":\"temporarily_unavailable\"}")
            : stub.ok(30, StubOpenAi.ALL_SCOPES, true);
        String sid = connectedSid();
        UUID parent = seed(sid, "COMPLETED", true);
        Before b = before(parent);
        assertError(rewrite(sid, parent.toString(), body("kafka", "uk")), 503, "CHATGPT_UNAVAILABLE",
            "ChatGPT is temporarily unavailable — try again in a few minutes");
        assertNothingStored(b, parent);
    }

    // ---- 409 ------------------------------------------------------------------------------------------------------

    static Stream<Arguments> notCompletedParents() {
        return Stream.of("QUEUED", "RUNNING", "FAILED", "INSUFFICIENT_EVIDENCE", "STOPPED", "COMPLETED-no-story")
            .map(s -> Arguments.of(s));
    }

    // Ranges & invariants: parent status classes QUEUED, RUNNING, FAILED, INSUFFICIENT_EVIDENCE, STOPPED, COMPLETED without a story.
    // @trace FR-67
    @ParameterizedTest(name = "parent {0} -> 409 RUN_NOT_COMPLETED")
    @MethodSource("notCompletedParents")
    void aParentThatIsNotCompletedWithAStoryIs409(String kind) throws Exception {
        String status = kind.startsWith("COMPLETED") ? "COMPLETED" : kind;
        String sid = connectedSid();
        UUID parent = seed(sid, status, false);
        try {
            Before b = before(parent);
            // a RUNNING / QUEUED parent is itself the active run of the session: still RUN_NOT_COMPLETED, not RUN_ALREADY_ACTIVE
            assertError(rewrite(sid, parent.toString(), body("kafka", "uk")), 409, "RUN_NOT_COMPLETED", NOT_REWRITABLE);
            assertNothingStored(b, parent);
        } finally {
            failActive(parent);
        }
    }

    // @trace FR-67
    @Test
    void anActiveRunOfTheSessionIs409RunAlreadyActive() throws Exception {
        Ran p = parent();
        UUID active = seed(p.sid(), "QUEUED", false);
        try {
            Map<String, Object> snap = snapshot(p.id());
            int rows = runCount();
            int requests = responses.requests.size();
            assertError(rewrite(p.sid(), p.id(), body("kafka", "uk")), 409, "RUN_ALREADY_ACTIVE", ACTIVE_MSG);
            assertThat(runCount()).isEqualTo(rows);
            assertThat(snapshot(p.id())).isEqualTo(snap);
            assertThat(responses.requests).hasSize(requests);
        } finally {
            failActive(active);
        }
    }

    // @trace FR-67
    @Test
    void twoConcurrentValidRequestsOnTheSameParentYieldOneRunAndOneConflict() throws Exception {
        Ran p = parent();
        int rows = runCount();
        CountDownLatch go = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<String> ids = new ArrayList<>();
        List<String> statuses = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        try {
            Callable<String[]> call = () -> {
                go.await();
                var r = rewrite(p.sid(), p.id(), body("poe", "en")).andReturn().getResponse();
                return new String[] {String.valueOf(r.getStatus()), r.getContentAsString()};
            };
            List<Future<String[]>> fs = List.of(pool.submit(call), pool.submit(call));
            go.countDown();
            for (Future<String[]> f : fs) {
                String[] r = f.get();
                statuses.add(r[0]);
                if ("202".equals(r[0])) ids.add((String) json(r[1]).get("id"));
                else conflicts.add(r[1]);
            }
        } finally {
            pool.shutdownNow();
        }
        assertThat(statuses).containsExactlyInAnyOrder("202", "409");
        assertThat(json(conflicts.get(0))).isEqualTo(ordered("code", "RUN_ALREADY_ACTIVE", "message", ACTIVE_MSG));
        assertThat(runCount()).isEqualTo(rows + 1);
        awaitRewrite(p.sid(), ids.get(0));
    }

    // ---- 500 ------------------------------------------------------------------------------------------------------

    // @trace FR-67
    @Test
    void anUnexpectedFailureInsideTheStartIs500WithoutInternalsAndStoresNothing() throws Exception {
        Ran p = parent();
        jdbc.execute("create or replace function oracul_test_reject_rewrite() returns trigger language plpgsql as $$ begin "
            + "if new.kind = 'REWRITE' then raise exception 'boom http://internal.example at com.oracul.app.X'; end if; return new; end $$");
        jdbc.execute("create trigger oracul_test_reject_rewrite before insert on generation_run for each row "
            + "execute function oracul_test_reject_rewrite()");
        try {
            Map<String, Object> snap = snapshot(p.id());
            int rows = runCount();
            int requests = responses.requests.size();
            MvcResult r = rewrite(p.sid(), p.id(), body("kafka", "uk")).andReturn();
            String raw = r.getResponse().getContentAsString();
            assertThat(r.getResponse().getStatus()).as(raw).isEqualTo(500);
            assertThat(json(raw)).isEqualTo(ordered("code", "INTERNAL_ERROR", "message", INTERNAL_MSG));
            assertThat(raw).doesNotContain("boom", "internal.example", "com.oracul", "Exception", "ERROR:", "oracul_test_reject_rewrite");
            assertThat(runCount()).isEqualTo(rows);
            assertThat(snapshot(p.id())).isEqualTo(snap);
            assertThat(responses.requests).as("no pipeline submitted").hasSize(requests);
        } finally {
            jdbc.execute("drop trigger if exists oracul_test_reject_rewrite on generation_run");
            jdbc.execute("drop function if exists oracul_test_reject_rewrite()");
        }
    }

    // ---- 202 ------------------------------------------------------------------------------------------------------

    /** Every (profile, language) combination of the shipped file: 9 x 2 = 18 cases. */
    static Stream<Arguments> combinations() {
        return PROFILE_IDS.stream().flatMap(id -> Stream.of("en", "uk").map(l -> Arguments.of(id, l)));
    }

    // Ranges & invariants: 9 profiles x 2 languages, each its own case -> 202 with the stored writingStyle / language equal to the request's.
    // @trace FR-67
    @ParameterizedTest(name = "{0} / {1} -> 202, stored and told in that style")
    @MethodSource("combinations")
    void everyStyleAndLanguageCombinationIsAcceptedAndStoredAsRequested(String profileId, String language) throws Exception {
        Ran p = parent();
        Map<String, Object> parentRun = p.run();
        int rows = runCount();
        Map<String, Object> created = rewriteOk(p.sid(), p.id(), body(profileId, language));
        assertThat(runCount()).isEqualTo(rows + 1);
        Map<String, Object> out = map(configurationOf(created).get("output"));
        assertThat(out.get("writingStyle")).isEqualTo(json(styleJson(modeOf(profileId), profileId)));
        assertThat(out.get("language")).isEqualTo(language);
        assertThat(created.get("kind")).isEqualTo("REWRITE");
        Map<String, Object> stored = jdbc.queryForMap("select cast(configuration as text) cfg from generation_run where id = cast(? as uuid)",
            created.get("id"));
        Map<String, Object> storedOut = map(json((String) stored.get("cfg")).get("output"));
        assertThat(storedOut.get("writingStyle")).isEqualTo(json(styleJson(modeOf(profileId), profileId)));
        assertThat(storedOut.get("language")).isEqualTo(language);
        Ran done = awaitRewrite(p.sid(), (String) created.get("id"));
        assertThat(done.run().get("status")).as("run: " + done.run()).isEqualTo("COMPLETED");
        assertThat(done.run().get("headline")).isEqualTo(com.oracul.app.result.StoryFixtures.styledHeadline(labelOf(profileId), language));
        assertThat(parentRun.get("status")).isEqualTo("COMPLETED");
    }

    // Ranges & invariants: the 9 matching cells of the 2 x 9 mode / profile grid are accepted (one parent, one rewrite after the other).
    // @trace FR-67
    @Test
    void everyMatchingModeAndProfileCellIsAccepted() throws Exception {
        Ran p = parent();
        int accepted = 0;
        for (String profileId : PROFILE_IDS) {
            Map<String, Object> created = rewriteOk(p.sid(), p.id(), body(modeOf(profileId), profileId, "en"));
            assertThat(created.get("parentRunId")).as(profileId).isEqualTo(p.id());
            awaitRewrite(p.sid(), (String) created.get("id"));
            accepted++;
        }
        assertThat(accepted).isEqualTo(9);
    }

    // @trace FR-67
    @Test
    void theParentsOwnCombinationIsAccepted() throws Exception {
        Ran p = runV4(withOutput(A, "NARRATIVE", "kafka", "uk"));
        assertStoryCompleted(p.run(), com.oracul.app.result.StoryFixtures.styledHeadline(labelOf("kafka"), "uk"));
        Map<String, Object> created = rewriteOk(p.sid(), p.id(), body("kafka", "uk"));
        assertThat(created.get("kind")).isEqualTo("REWRITE");
        awaitRewrite(p.sid(), (String) created.get("id"));
    }

    private static void assertStoryCompleted(Map<String, Object> run, String headline) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("headline")).isEqualTo(headline);
    }

    /** Body A with output.writingStyle and output.language. */
    static String withOutput(String bodyA, String mode, String profileId, String language) {
        return bodyA.replace("\"output\":{\"story\":true,\"illustration\":false}", "\"output\":{\"story\":true,\"illustration\":false,"
            + "\"writingStyle\":" + styleJson(mode, profileId) + ",\"language\":\"" + language + "\"}");
    }

    // @trace FR-67
    @Test
    void the202BodyIsTheQueuedRewriteWithTheParentsConfigurationPackAndCounts() throws Exception {
        Ran p = parent();
        Map<String, Object> pRun = p.run();
        int rows = runCount();
        Map<String, Object> created = rewriteOk(p.sid(), p.id(), body("kafka", "uk"));
        assertThat(runCount()).isEqualTo(rows + 1);
        assertThat(created.get("id")).isNotEqualTo(p.id());
        assertThat((String) created.get("generationId")).matches("^ORC-\\d{4}-\\d{2}-\\d{2}-\\d{4}(-\\d+)?$");
        assertThat(created.get("generationId")).isNotEqualTo(pRun.get("generationId"));
        assertThat(created.get("kind")).isEqualTo("REWRITE");
        assertThat(created.get("parentRunId")).isEqualTo(p.id());
        assertThat(created.get("scenarioRunId")).isEqualTo(p.id());
        assertThat(created.get("status")).isEqualTo("QUEUED");
        assertThat(created.get("stageIndex")).isEqualTo(0);
        assertThat(created.get("stageCount")).isEqualTo(10);
        for (String k : List.of("stage", "stageLabel", "failure", "headline", "completedAt")) {
            assertThat(absent(created, k)).as(k + " absent").isTrue();
        }
        Map<String, Object> expectedConfiguration = new java.util.LinkedHashMap<>(configurationOf(pRun));
        Map<String, Object> expectedOutput = new java.util.LinkedHashMap<>(map(expectedConfiguration.get("output")));
        expectedOutput.put("writingStyle", json(styleJson("NARRATIVE", "kafka")));
        expectedOutput.put("language", "uk");
        expectedConfiguration.put("output", expectedOutput);
        assertThat(created.get("configuration")).isEqualTo(expectedConfiguration);
        for (String k : List.of("evidencePackId", "counts", "evidenceNote", "suggestedRealism", "hasOpenCriticIssues")) {
            assertThat(created.get(k)).as(k).isEqualTo(pRun.get(k));
        }
        assertThat(OffsetDateTime.parse((String) created.get("updatedAt"))).isEqualTo(OffsetDateTime.parse((String) created.get("createdAt")));

        // getRun right after the 202 answers the same stable values
        Map<String, Object> read = json(getRun(p.sid(), (String) created.get("id")));
        for (String k : List.of("id", "generationId", "kind", "parentRunId", "scenarioRunId", "configuration", "evidencePackId", "counts",
            "createdAt", "hasOpenCriticIssues")) {
            assertThat(read.get(k)).as("getRun " + k).isEqualTo(created.get(k));
        }

        Map<String, Object> stored = jdbc.queryForMap("select kind, cast(parent_run_id as text) parent, cast(scenario_run_id as text) scenario, "
            + "cast(session_id as text) sid, cast(evidence_pack_id as text) pack, final_attempt, "
            + "extract(epoch from (deadline_at - created_at)) secs from generation_run where id = cast(? as uuid)", created.get("id"));
        assertThat(stored.get("kind")).isEqualTo("REWRITE");
        assertThat(stored.get("parent")).isEqualTo(p.id());
        assertThat(stored.get("scenario")).isEqualTo(p.id());
        assertThat(stored.get("sid")).isEqualTo(p.sid());
        assertThat(stored.get("pack")).isEqualTo(pRun.get("evidencePackId"));
        assertThat(stored.get("final_attempt")).as("a rewrite has no scenario attempts").isNull();
        assertThat(((Number) stored.get("secs")).doubleValue()).isEqualTo(180.0);

        String columns = "cast(research_profile as text) rp, cast(search_plan as text) sp, cast(counts as text) counts, "
            + "cast(evidence_pack_id as text) pack, suggested_realism, evidence_note_kind, evidence_core_items, evidence_core_needed, "
            + "cast(evidence_note_wildcards as text) wildcards";
        Map<String, Object> copied = jdbc.queryForMap("select " + columns + " from generation_run where id = cast(? as uuid)", created.get("id"));
        Map<String, Object> parentCols = jdbc.queryForMap("select " + columns + " from generation_run where id = cast(? as uuid)", p.id());
        for (String k : copied.keySet()) {
            Object c = copied.get(k);
            Object o = parentCols.get(k);
            if (c instanceof String s && o instanceof String t && (s.startsWith("{") || s.startsWith("["))) {
                assertThat(jsonOf(s)).as("copied column " + k).isEqualTo(jsonOf(t));
            } else {
                assertThat(c).as("copied column " + k).isEqualTo(o);
            }
        }
        awaitRewrite(p.sid(), (String) created.get("id"));
    }

    // @trace FR-67
    @Test
    void unknownKeysAreIgnoredAndNotStored() throws Exception {
        Ran p = parent();
        String raw = "{\"extra\":1,\"writingStyle\":{\"mode\":\"NARRATIVE\",\"profileId\":\"kafka\",\"foo\":{\"x\":1}},\"language\":\"uk\",\"other\":[1]}";
        Map<String, Object> created = rewriteOk(p.sid(), p.id(), raw);
        Map<String, Object> out = map(configurationOf(created).get("output"));
        assertThat(out.get("writingStyle")).isEqualTo(json(styleJson("NARRATIVE", "kafka")));
        String stored = jdbc.queryForObject("select cast(configuration as text) from generation_run where id = cast(? as uuid)", String.class,
            created.get("id"));
        assertThat(map(map(json(stored).get("output")).get("writingStyle"))).containsOnlyKeys("mode", "profileId");
        assertThat(json(stored)).doesNotContainKeys("extra", "other");
        awaitRewrite(p.sid(), (String) created.get("id"));
    }

    // @trace FR-67
    @Test
    void scenarioRunIdAppearsOnlyForRewriteRuns() throws Exception {
        Ran p = parent();
        assertThat(p.run()).doesNotContainKey("scenarioRunId");
        Ran a = alternativeOf(p);
        assertThat(a.run()).doesNotContainKey("scenarioRunId");
        Ran r = rewriteRun(p, body("poe", "en"));
        assertThat(r.run().get("scenarioRunId")).isEqualTo(p.id());
        assertThat(json(getRun(p.sid(), p.id()))).doesNotContainKey("scenarioRunId");
    }

    // @trace FR-67
    @Test
    void theDatabaseInvariantHoldsForEveryRowScenarioRunIdIsSetExactlyForRewrites() throws Exception {
        Ran p = parent();
        Ran r1 = rewriteRun(p, body("kafka", "uk"));
        Ran r2 = rewriteRun(r1, body("poe", "en"));
        assertThat(r2.run().get("status")).isEqualTo("COMPLETED");
        Set<String> kinds = new HashSet<>(jdbc.queryForList("select distinct kind from generation_run", String.class));
        assertThat(kinds).contains("STANDARD", "REWRITE");
        assertThat(jdbc.queryForObject("select count(*) from generation_run where (scenario_run_id is not null) <> (kind = 'REWRITE')",
            Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from generation_run r join generation_run s on s.id = r.scenario_run_id "
            + "where s.kind = 'REWRITE'", Integer.class)).as("a scenario run is never a rewrite").isZero();
    }
}
