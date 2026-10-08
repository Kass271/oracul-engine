package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.reasoning.ScenarioFixtures;
import com.oracul.app.research.StubResponses;
import com.oracul.app.result.StoryFixtures;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * phase-04 story-rewrite.md FR-68, backend half 2: ALTERNATIVE FUTURE on a REWRITE parent. A COMPLETED rewrite with a story is
 * eligible; the alternative keeps the rewrite's style, language and pack, and avoids the scenario of the rewrite's scenarioRunId
 * as Future 1. REWRITE runs are never listed as avoided futures. ALTERNATIVE of a Poe / Ukrainian run keeps Poe / Ukrainian.
 */
// @trace FR-68
class AlternativeOfRewriteIT extends AbstractLabelsIT {

    private static List<String> futureLines(String text) {
        return StubResponses.dataBlock(text, "futures-to-avoid").lines().filter(l -> l.startsWith("Future ")).toList();
    }

    private Ran awaitAlternative(String sid, String id) throws Exception {
        return new Ran(sid, id, awaitRun(sid, id, 20_000, AbstractRewriteIT::ended));
    }

    /** Starts an alternative on the run (202 expected) and waits for its end. */
    private Ran alternativeRun(Ran parent) throws Exception {
        Http res = alternativeOn(parent.sid(), parent.id());
        assertThat(res.status()).as("startAlternativeRun: " + res.body()).isEqualTo(202);
        return awaitAlternative(parent.sid(), (String) json(res.body()).get("id"));
    }

    private String titleOf(Ran r) throws Exception {
        return (String) map(map(structured(r).get("structuredScenario")).get("futureEvent")).get("title");
    }

    // @trace FR-68
    @Test
    void aCompletedRewriteIsEligibleAndTheAlternativeKeepsItsStyleLanguageAndPack() throws Exception {
        Ran p = runV4(bodyWithOutput(styleJson("OFFICIAL", "official-briefing"), "\"en\""));
        assertStoryCompleted(p.run());
        Ran rw = rewriteRun(p, body("poe", "uk"));
        assertThat(rw.run().get("status")).as("rewrite: " + rw.run()).isEqualTo("COMPLETED");
        Map<String, Object> rewriteSnapshot = snapshot(rw.id());
        Map<String, Object> scenarioSnapshot = snapshot(p.id());
        int rows = runCount();
        int news0 = news.requests.size();
        int total0 = responses.requests.size();
        int gen0 = requests(GEN).size();
        int story0 = requests(STORY).size();

        Http res = alternativeOn(rw.sid(), rw.id());
        assertThat(res.status()).as("startAlternativeRun on a rewrite: " + res.body()).isEqualTo(202);
        Map<String, Object> created = json(res.body());
        assertThat(runCount()).isEqualTo(rows + 1);
        assertThat(created.get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(created.get("parentRunId")).isEqualTo(rw.id());
        assertThat(created).as("an ALTERNATIVE has no scenarioRunId key").doesNotContainKey("scenarioRunId");
        assertThat(created.get("status")).isEqualTo("QUEUED");
        assertThat(created.get("stageIndex")).isEqualTo(0);
        assertThat(created.get("configuration")).as("the rewrite's configuration, with its style and language").isEqualTo(rw.run().get("configuration"));
        Map<String, Object> out = map(configurationOf(created).get("output"));
        assertThat(out.get("writingStyle")).isEqualTo(json(styleJson("NARRATIVE", "poe")));
        assertThat(out.get("language")).isEqualTo("uk");
        for (String key : List.of("evidencePackId", "counts", "evidenceNote", "suggestedRealism")) {
            assertThat(created.get(key)).as(key + " of the rewrite").isEqualTo(rw.run().get(key));
        }
        Ran alt = awaitAlternative(rw.sid(), (String) created.get("id"));

        assertThat(alt.run().get("status")).as("alternative: " + alt.run()).isEqualTo("COMPLETED");
        assertThat(alt.run().get("headline")).isEqualTo(StoryFixtures.styledHeadline(POE, "uk"));
        // no search; the pipeline of an alternative
        assertThat(news.requests).hasSize(news0);
        assertThat(purposes().subList(total0, purposes().size())).containsExactly(GEN, "SCENARIO_CRITIC", STORY);
        assertThat(requests(GEN)).hasSize(gen0 + 1);
        assertThat(requests(STORY)).hasSize(story0 + 1);
        assertThat(s(story0 + 1)).contains(sectionOf("poe", "uk") + "\nTASK\n");
        assertThat(pack(alt)).isEqualTo(pack(p));

        // its result: Poe / Ukrainian, no rewrittenFrom
        Map<String, Object> md = metadataOf(alt);
        assertThat(md.get("styleLabel")).isEqualTo(POE);
        assertThat(md.get("languageLabel")).isEqualTo(UKRAINIAN);
        assertThat(md).doesNotContainKey("rewrittenFrom");
        assertThat(map(result(alt).get("story")).get("headline")).isEqualTo(StoryFixtures.styledHeadline(POE, "uk"));

        // the rewrite and the scenario run are untouched
        assertThat(snapshot(rw.id())).isEqualTo(rewriteSnapshot);
        assertThat(snapshot(p.id())).isEqualTo(scenarioSnapshot);
    }

    // Ranges & invariants: Future 1 of the alternative is the scenario of the rewrite's scenarioRunId, for a rewrite of a STANDARD run,
    // of an ALTERNATIVE run and of a rewrite; no avoided future is a REWRITE run, the avoided list has no duplicate run and at most 10 entries.
    // @trace FR-68
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"rewrite of a STANDARD run", "rewrite of an ALTERNATIVE run", "rewrite of a rewrite"})
    void future1IsTheScenarioOfTheRewritesScenarioRun(String kind) throws Exception {
        Ran p = parent();
        Ran scenarioRun;
        Ran rewrite;
        List<String> expectedAvoided;
        switch (kind) {
            case "rewrite of a STANDARD run" -> {
                scenarioRun = p;
                rewrite = rewriteRun(p, body("kafka", "uk"));
                expectedAvoided = List.of("Future 1: Stub future A");
            }
            case "rewrite of an ALTERNATIVE run" -> {
                scenarioRun = alternativeOf(p);
                rewrite = rewriteRun(scenarioRun, body("kafka", "uk"));
                expectedAvoided = List.of("Future 1: Stub alternative future 1", "Future 2: Stub future A");
            }
            default -> {
                scenarioRun = p;
                Ran first = rewriteRun(p, body("kafka", "uk"));
                rewrite = rewriteRun(first, body("poe", "en"));
                expectedAvoided = List.of("Future 1: Stub future A");
            }
        }
        assertThat(rewrite.run().get("status")).as("rewrite: " + rewrite.run()).isEqualTo("COMPLETED");
        assertThat(rewrite.run().get("scenarioRunId")).isEqualTo(scenarioRun.id());
        int gen0 = requests(GEN).size();

        Ran alt = alternativeRun(rewrite);
        assertThat(alt.run().get("status")).as("alternative: " + alt.run()).isEqualTo("COMPLETED");
        assertThat(alt.run().get("kind")).isEqualTo("ALTERNATIVE");
        assertThat(alt.run().get("parentRunId")).isEqualTo(rewrite.id());

        List<StubResponses.Request> gens = requests(GEN);
        assertThat(gens).hasSize(gen0 + 1);
        String input = gens.get(gen0).inputText();
        List<String> avoided = futureLines(input);
        assertThat(avoided).as("futures-to-avoid").isEqualTo(expectedAvoided);
        assertThat(avoided.get(0)).as("Future 1 is the scenario of the scenario run").isEqualTo("Future 1: " + titleOf(scenarioRun));
        assertThat(avoided.size()).isLessThanOrEqualTo(10);
        assertThat(avoided.stream().map(l -> l.substring(l.indexOf(':') + 1).trim()).distinct()).hasSameSizeAs(avoided);
        assertThat(titleOf(alt)).as("a different future than Future 1").isNotEqualTo(titleOf(scenarioRun));
        assertThat(jdbc.queryForObject("select count(*) from generation_run where kind = 'REWRITE' and final_attempt is not null", Integer.class)).isZero();
    }

    static Stream<Arguments> notEligible() {
        return Stream.of("QUEUED", "RUNNING", "FAILED", "STOPPED", "COMPLETED without story").map(Arguments::of);
    }

    // Ranges & invariants: parent eligibility classes REWRITE COMPLETED with story -> 202 (tests above), REWRITE QUEUED / RUNNING /
    // FAILED / STOPPED / COMPLETED without a story -> 409 RUN_NOT_COMPLETED, and nothing is stored.
    // @trace FR-68
    @ParameterizedTest(name = "rewrite parent {0}")
    @MethodSource("notEligible")
    void aRewriteThatIsNotCompletedWithAStoryCannotHaveAnAlternative(String state) throws Exception {
        Ran p = parent();
        Ran rw = rewriteRun(p, body("kafka", "uk"));
        assertThat(rw.run().get("status")).as("rewrite: " + rw.run()).isEqualTo("COMPLETED");
        if ("COMPLETED without story".equals(state)) {
            jdbc.update("delete from future_story where run_id = cast(? as uuid)", rw.id());
        } else {
            jdbc.update("update generation_run set status = ? where id = cast(? as uuid)", state, rw.id());
        }
        int rows = runCount();
        int total0 = responses.requests.size();
        try {
            Http res = alternativeOn(rw.sid(), rw.id());
            assertThat(res.status()).as(state + ": " + res.body()).isEqualTo(409);
            assertThat(json(res.body())).isEqualTo(json("{\"code\":\"RUN_NOT_COMPLETED\",\"message\":\"" + ALT_NOT_COMPLETED_MSG + "\"}"));
            assertThat(runCount()).as("nothing is stored").isEqualTo(rows);
            assertThat(responses.requests).as("no pipeline is submitted").hasSize(total0);
        } finally {
            jdbc.update("update generation_run set status = 'FAILED' where id = cast(? as uuid) and status in ('QUEUED','RUNNING')", rw.id());
        }
    }

    // @trace FR-68
    @Test
    void anAlternativeOfAPoeUkrainianRunKeepsPoeAndUkrainian() throws Exception {
        Ran p = runV4(bodyWithOutput(styleJson("NARRATIVE", "poe"), "\"uk\""));
        assertThat(p.run().get("status")).as("run: " + p.run()).isEqualTo("COMPLETED");
        assertThat(metadataOf(p).get("styleLabel")).isEqualTo(POE);

        Ran alt = alternativeRun(p);
        assertThat(alt.run().get("status")).as("alternative: " + alt.run()).isEqualTo("COMPLETED");
        Map<String, Object> out = map(configurationOf(alt.run()).get("output"));
        assertThat(out.get("writingStyle")).isEqualTo(json(styleJson("NARRATIVE", "poe")));
        assertThat(out.get("language")).isEqualTo("uk");
        assertThat(alt.run().get("headline")).isEqualTo(StoryFixtures.styledHeadline(POE, "uk"));
        Map<String, Object> md = metadataOf(alt);
        assertThat(md.get("styleLabel")).isEqualTo(POE);
        assertThat(md.get("languageLabel")).isEqualTo(UKRAINIAN);
        assertThat(md).doesNotContainKey("rewrittenFrom");
        assertThat(joinedLabel(listItem(alt.sid(), alt.id()))).isEqualTo("Narrative — inspired by Edgar Allan Poe · Ukrainian");
    }

    // @trace FR-68
    @Test
    void theAlternativesListedForARewriteNeverHoldARewriteRun() throws Exception {
        Ran p = parent();
        Ran r1 = rewriteRun(p, body("wells", "en"));
        Ran r2 = rewriteRun(r1, body("verne", "uk"));
        int gen0 = requests(GEN).size();
        Ran alt = alternativeRun(r2);
        assertThat(alt.run().get("status")).as("alternative: " + alt.run()).isEqualTo("COMPLETED");
        String input = requests(GEN).get(gen0).inputText();
        assertThat(futureLines(input)).isEqualTo(List.of("Future 1: Stub future A"));
        assertThat(StubResponses.dataBlock(input, "futures-to-avoid")).doesNotContain(r1.id()).doesNotContain(r2.id());
    }

    /**
     * A COMPLETED STANDARD run of the session on the pack of {@code scenarioRun}, with a final scenario attempt whose title and
     * inference are {@code Seeded future k} / {@code Seeded inference k.}, created {@code k * 100} microseconds after it.
     */
    private UUID seedEligible(Ran scenarioRun, int k) {
        String cleaned = jdbc.queryForObject("select cast(a.cleaned_scenario as text) from generation_run r join scenario_attempt a "
            + "on a.run_id = r.id and a.attempt = r.final_attempt where r.id = cast(? as uuid)", String.class, scenarioRun.id());
        assertThat(cleaned).as("cleaned scenario of the scenario run").contains("\"Stub future A\"");
        String seeded = cleaned.replace("\"Stub future A\"", "\"Seeded future " + k + "\"")
            .replace("\"Stub inference.\"", "\"Seeded inference " + k + ".\"");
        OffsetDateTime created = jdbc.queryForObject("select created_at from generation_run where id = cast(? as uuid)",
            OffsetDateTime.class, scenarioRun.id()).withOffsetSameInstant(ZoneOffset.UTC).plusNanos(k * 100_000L);
        UUID id = UUID.randomUUID();
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, headline, "
                + "final_attempt, evidence_pack_id, deadline_at, created_at, updated_at, completed_at) select ?, ?, r.session_id, "
                + "'STANDARD', 'COMPLETED', r.configuration, r.counts, ?, 1, r.evidence_pack_id, ?, ?, ?, ? from generation_run r "
                + "where r.id = cast(? as uuid)",
            id, "ORC-AR-" + id.toString().replace("-", "").substring(20), "Seeded headline " + k,
            OffsetDateTime.now(ZoneOffset.UTC).plusDays(1), created, created, created, scenarioRun.id());
        jdbc.update("insert into scenario_attempt (run_id, attempt, reason, structured_scenario, schema_errors, cleaned_scenario, created_at) "
            + "values (?, 1, 'INITIAL', cast(? as jsonb), cast('[]' as jsonb), cast(? as jsonb), ?)", id, seeded, seeded, created);
        return id;
    }

    // Ranges & invariants: the futures to avoid of an ALTERNATIVE on a REWRITE parent, with the scenario run the OLDEST of 12 eligible
    // runs on the pack (11 seeded after it): exactly 10 entries, Future 1 = the scenario run (kept when `others` is trimmed to 9),
    // the 9 newest others follow oldest first, no duplicate, no REWRITE run (neither id nor headline).
    // @trace FR-68
    @Test
    @Timeout(120)
    void theWindowOfTenKeepsTheScenarioRunAsFuture1WhenItIsTheOldestOfManyEligibleRuns() throws Exception {
        Ran p = parent();
        for (int k = 1; k <= 11; k++) seedEligible(p, k);
        Ran rw = rewriteRun(p, body("kafka", "uk"));
        assertThat(rw.run().get("status")).as("rewrite: " + rw.run()).isEqualTo("COMPLETED");
        assertThat(rw.run().get("scenarioRunId")).isEqualTo(p.id());
        Map<String, Object> rewriteSnapshot = snapshot(rw.id());
        Map<String, Object> scenarioSnapshot = snapshot(p.id());
        int gen0 = requests(GEN).size();

        Ran alt = alternativeRun(rw);
        assertThat(alt.run().get("status")).as("alternative: " + alt.run()).isEqualTo("COMPLETED");
        assertThat(alt.run().get("parentRunId")).isEqualTo(rw.id());

        List<StubResponses.Request> gens = requests(GEN);
        assertThat(gens).hasSize(gen0 + 1);
        String input = gens.get(gen0).inputText();
        List<String> avoided = futureLines(input);
        List<String> expected = new ArrayList<>();
        expected.add("Future 1: " + titleOf(p));
        for (int n = 2; n <= 10; n++) expected.add("Future " + n + ": Seeded future " + (n + 1));
        assertThat(avoided).as("futures-to-avoid").hasSize(10).isEqualTo(expected);
        assertThat(avoided.get(0)).isEqualTo("Future 1: Stub future A");
        assertThat(avoided.stream().map(l -> l.substring(l.indexOf(':') + 1).trim()).distinct()).hasSameSizeAs(avoided);
        String block = StubResponses.dataBlock(input, "futures-to-avoid");
        assertThat(block).doesNotContain(rw.id()).doesNotContain((String) rw.run().get("headline"));
        assertThat(block).doesNotContain("Seeded future 1\n").doesNotContain("Seeded future 2\n");
        assertThat(titleOf(alt)).as("a different future than every avoided one").isEqualTo("Stub alternative future 10");

        assertThat(snapshot(rw.id())).isEqualTo(rewriteSnapshot);
        assertThat(snapshot(p.id())).isEqualTo(scenarioSnapshot);
    }

    // The distinctness check of an ALTERNATIVE on a REWRITE parent compares with the scenario of its scenarioRunId (a STANDARD run and
    // an ALTERNATIVE run): a candidate that repeats it twice fails the run with ALTERNATIVE_NOT_DISTINCT.
    // @trace FR-68
    @ParameterizedTest(name = "scenario run is a {0}")
    @ValueSource(strings = {"STANDARD run", "ALTERNATIVE run"})
    @Timeout(120)
    void aCandidateThatRepeatsTheScenarioRunTwiceFailsAsNotDistinct(String kind) throws Exception {
        boolean standard = kind.startsWith("STANDARD");
        Ran p = parent();
        Ran scenarioRun = standard ? p : alternativeOf(p);
        assertThat(scenarioRun.run().get("status")).as("scenario run: " + scenarioRun.run()).isEqualTo("COMPLETED");
        String repeatedTitle = titleOf(scenarioRun);
        Ran rw = rewriteRun(scenarioRun, body("kafka", "uk"));
        assertThat(rw.run().get("status")).as("rewrite: " + rw.run()).isEqualTo("COMPLETED");
        assertThat(rw.run().get("scenarioRunId")).isEqualTo(scenarioRun.id());
        Map<String, Object> rewriteSnapshot = snapshot(rw.id());
        Map<String, Object> scenarioSnapshot = snapshot(scenarioRun.id());
        int gen0 = requests(GEN).size();
        int story0 = requests(STORY).size();
        // the scenario run's own scenario, answered again for every attempt of the alternative
        always(GEN, computed(in -> standard ? ScenarioFixtures.scenarioDefault(in) : ScenarioFixtures.scenarioAlternative(in, 1)));

        Ran alt = alternativeRun(rw);

        assertFailed(alt.run(), "ALTERNATIVE_NOT_DISTINCT", AlternativeRunIT.NOT_DISTINCT_MSG, "CONSTRUCTING_SCENARIO", 9);
        List<StubResponses.Request> gens = requests(GEN);
        assertThat(gens).as("two attempts").hasSize(gen0 + 2);
        assertThat(StubResponses.attemptReason(gens.get(gen0).inputText())).isEqualTo("INITIAL");
        String second = gens.get(gen0 + 1).inputText();
        assertThat(StubResponses.attemptReason(second)).isEqualTo("ALTERNATIVE_DISTINCT");
        assertThat(futureLines(gens.get(gen0).inputText()).get(0)).isEqualTo("Future 1: " + repeatedTitle);
        assertThat(StubResponses.dataBlock(second, "duplicate-future"))
            .isEqualTo("Rejected future: " + repeatedTitle + "\nsame future event title as future 1\nsame causal steps as future 1");
        assertThat(requests(STORY)).as("no story for a failed alternative").hasSize(story0);
        assertThat(finalAttempt(alt.id())).isNull();
        assertThat(absent(alt.run(), "headline")).isTrue();

        assertThat(snapshot(rw.id())).isEqualTo(rewriteSnapshot);
        assertThat(snapshot(scenarioRun.id())).isEqualTo(scenarioSnapshot);
    }
}
