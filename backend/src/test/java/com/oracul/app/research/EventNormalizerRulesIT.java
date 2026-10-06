package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * research-pipeline.md "FR-14 - Normalisation validation, fallback, merge": validation messages, accepted-field rules,
 * fallback grouping and id assignment, observed through the retry request and listRunEvents.
 */
// @trace FR-14
class EventNormalizerRulesIT extends AbstractEventIT {

    /** One event JSON with all fields; strings are raw JSON fragments. */
    private static String ev(String ids, String date, String category, String entities, String summary, String disagreement, String confidence) {
        return "{\"sourceIds\":" + ids + ",\"date\":" + date + ",\"category\":" + category + ",\"entities\":" + entities
            + ",\"summary\":" + summary + ",\"disagreement\":" + disagreement + ",\"confidence\":" + confidence + "}";
    }

    private static String ok(String ids) {
        return ev(ids, "null", "\"c\"", "[]", "\"s\"", "null", "0.5");
    }

    private static String answer(String... evs) {
        return "{\"events\":[" + String.join(",", evs) + "]}";
    }

    private Ran runScripted(String... normalizationAnswers) throws Exception {
        newsArticles(v4());
        script(NORMALIZATION, normalizationAnswers);
        return run(A);
    }

    static Stream<Arguments> violations() {
        return Stream.of(
            Arguments.of("no source ids",
                answer(ev("[]", "null", "\"c\"", "[]", "\"s\"", "null", "0.5"), ok("[\"S001\",\"S002\",\"S003\",\"S004\"]")),
                List.of("event 1 has no source ids")),
            Arguments.of("blank summary",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"c\"", "[]", "\"  \"", "null", "0.5"), ok("[\"S004\"]")),
                List.of("event 1 has a blank summary")),
            Arguments.of("blank category",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "null", "\" \"", "[]", "\"s\"", "null", "0.5"), ok("[\"S004\"]")),
                List.of("event 1 has a blank category")),
            Arguments.of("invalid date",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "\"2026-13-45\"", "\"c\"", "[]", "\"s\"", "null", "0.5"), ok("[\"S004\"]")),
                List.of("event 1 has an invalid date")),
            Arguments.of("date in the wrong format",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "\"10/01/2026\"", "\"c\"", "[]", "\"s\"", "null", "0.5"), ok("[\"S004\"]")),
                List.of("event 1 has an invalid date")),
            Arguments.of("confidence above 1",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"c\"", "[]", "\"s\"", "null", "1.5"), ok("[\"S004\"]")),
                List.of("event 1 has confidence outside 0..1")),
            Arguments.of("confidence below 0",
                answer(ok("[\"S001\",\"S002\",\"S003\"]"), ev("[\"S004\"]", "null", "\"c\"", "[]", "\"s\"", "null", "-0.1")),
                List.of("event 2 has confidence outside 0..1")),
            Arguments.of("id in two events",
                answer(ok("[\"S001\",\"S002\",\"S003\",\"S004\"]"), ok("[\"S004\"]")),
                List.of("source id S004 appears more than once")),
            Arguments.of("id twice in one event",
                answer(ok("[\"S001\",\"S001\",\"S002\",\"S003\"]"), ok("[\"S004\"]")),
                List.of("source id S001 appears more than once")),
            Arguments.of("missing id",
                answer(ok("[\"S001\",\"S002\"]"), ok("[\"S004\"]")),
                List.of("source id S003 is missing")),
            Arguments.of("several missing ids in id order",
                answer(ok("[\"S004\"]")),
                List.of("source id S001 is missing", "source id S002 is missing", "source id S003 is missing")),
            Arguments.of("all violations in the specified order",
                answer(ev("[\"S001\",\"S002\",\"S003\"]", "\"nope\"", "\"c\"", "[]", "\"s\"", "null", "0.5"),
                    ev("[\"S999\"]", "null", "\" \"", "[]", "\" \"", "null", "2")),
                List.of("event 1 has an invalid date", "event 2 has a blank summary", "event 2 has a blank category",
                    "event 2 has confidence outside 0..1", "unknown source id S999", "source id S004 is missing")),
            Arguments.of("event without confidence field",
                answer("{\"sourceIds\":[\"S001\",\"S002\",\"S003\",\"S004\"],\"date\":null,\"category\":\"c\",\"entities\":[],"
                    + "\"summary\":\"s\",\"disagreement\":null}"),
                List.of("answer does not match the schema")),
            Arguments.of("confidence of the wrong type",
                answer(ev("[\"S001\",\"S002\",\"S003\",\"S004\"]", "null", "\"c\"", "[]", "\"s\"", "null", "\"0.9\"")),
                List.of("answer does not match the schema")),
            Arguments.of("sourceIds not an array",
                answer(ev("\"S001\"", "null", "\"c\"", "[]", "\"s\"", "null", "0.5")),
                List.of("answer does not match the schema")));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("violations")
    void everyViolationIsReportedWithTheSpecifiedMessageAndRetriedOnce(String name, String bad, List<String> expected) throws Exception {
        Ran r = runScripted(bad, N_V4);
        assertThat(r.run().get("status")).as(name + " run: " + r.run()).isEqualTo("COMPLETED");
        List<StubResponses.Request> calls = requests(NORMALIZATION);
        assertThat(calls).as(name).hasSize(2);
        assertThat(lines(StubResponses.dataBlock(calls.get(1).inputText(), "validation-errors"))).as(name).isEqualTo(expected);
        assertThat(events(r)).as("retry answer is used").hasSize(2);
        assertThat(event(events(r), "EV001").get("summary")).isEqualTo(
            "Health regulators approved a new pandemic vaccine. Reports differ on the number of doses approved.");
    }

    // accepted fields
    @Test
    void acceptedFieldsAreCleanedAndTheSummaryStatesTheDisagreement() throws Exception {
        Ran r = runScripted(answer(
            ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"  health \"", "[\" WHO \",\"\",\"  \",\"Who\",\"EMA\",\"ema \",\"Pandemic   vaccine\"]",
                "\"  Regulators approved it.  \"", "\"the dose count.\"", "0.8"),
            ev("[\"S004\"]", "null", "\"labour\"", "[]", "\"REPORTS DIFFER on wages.\"", "\"   \"", "0.8")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        Map<String, Object> e1 = events.get(0);
        assertThat(e1.get("category")).isEqualTo("health");
        assertThat(e1.get("entities")).isEqualTo(List.of("WHO", "EMA", "Pandemic vaccine"));
        assertThat(e1.get("summary")).isEqualTo("Regulators approved it. Reports differ on the dose count.");
        assertThat(e1.get("disagreement")).isEqualTo("the dose count.");
        Map<String, Object> e2 = events.get(1);
        assertThat(e2.get("summary")).as("already states the disagreement, nothing is appended").isEqualTo("REPORTS DIFFER on wages.");
        assertThat(omitted(e2, "disagreement")).as("blank disagreement is absent").isTrue();
    }

    @Test
    void disagreementAppendKeepsTheSummaryAtMost600CharactersAndTheSentenceComplete() throws Exception {
        String longSummary = "Word ".repeat(118).trim() + " tail"; // 594 characters
        Ran r = runScripted(answer(
            ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"c\"", "[]", "\"" + longSummary + "\"", "\"the number of doses\"", "0.8"),
            ok("[\"S004\"]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        String summary = (String) event(events(r), "EV001").get("summary");
        assertThat(summary.length()).isLessThanOrEqualTo(600);
        assertThat(summary).endsWith(" Reports differ on the number of doses.");
        assertThat(summary).startsWith("Word Word Word");
    }

    @Test
    void dateFallsBackToTheEarliestSourceDateAndKeepsAValidModelDate() throws Exception {
        newsArticles(List.of(
            new Art("who-vaccine", "who.int", "WHO approves new pandemic vaccine", 1),
            new Art("reuters-vaccine", "reuters.com", "Regulators approve pandemic vaccine", 4),
            new Art("local-vaccine", "example-news.com", "Pandemic vaccine gets approval", 2)));
        script(NORMALIZATION, answer(
            ev("[\"S001\",\"S002\"]", "null", "\"c\"", "[]", "\"s\"", "null", "0.5"),
            ev("[\"S003\"]", "\"2020-02-29\"", "\"c\"", "[]", "\"s\"", "null", "0.5")));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events.get(0).get("date")).isEqualTo(utcDate(source(r, "S002").get("publishedAt")));
        assertThat(events.get(1).get("date")).isEqualTo("2020-02-29");
    }

    @Test
    void eventIdsFollowTheLowestSourceIdNotTheAnswerOrder() throws Exception {
        Ran r = runScripted(answer(
            ev("[\"S004\"]", "null", "\"labour\"", "[]", "\"Dock workers strike.\"", "null", "0.7"),
            ev("[\"S003\",\"S002\",\"S001\"]", "null", "\"health\"", "[]", "\"Vaccine approved.\"", "null", "0.9")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events.get(0).get("id")).isEqualTo("EV001");
        assertThat(events.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(events.get(1).get("id")).isEqualTo("EV002");
        assertThat(events.get(1).get("sourceIds")).isEqualTo(List.of("S004"));
    }

    // fallback grouping
    @Test
    void fallbackJoinsTitlesWithJaccardAtLeastPointSixTransitivelyAndOnlyThen() throws Exception {
        newsArticles(withTitles(v4(), "a b c d e", "a b c d f", "a b c f g", "x y z"));
        scriptReplies(NORMALIZATION, StubResponses.completed("not json"), StubResponses.completed("not json"));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).get("sourceIds")).as("S001~S002 (0.67), S002~S003 (0.67), S001!~S003 (0.43)")
            .isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(events.get(0).get("summary")).isEqualTo("a b c d e");
        assertThat(events.get(1).get("sourceIds")).isEqualTo(List.of("S004"));
    }

    @Test
    void fallbackKeepsTitlesBelowTheThresholdApart() throws Exception {
        newsArticles(withTitles(v4(), "a b c d e", "a b c f g", "p q r", "x y z"));
        scriptReplies(NORMALIZATION, StubResponses.completed("not json"), StubResponses.completed("not json"));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(events(r)).hasSize(4);
        assertThat(counts(r.run()).get("uniqueEvents")).isEqualTo(4);
    }

    @Test
    void aValidAnswerGroupingEverythingNeedsNoRetry() throws Exception {
        newsArticles(withTitles(v4(), "Pandemic vaccine approved by regulators", "Regulators approved pandemic vaccine",
            "Dock workers strike over humanoid robots", "Fusion plant opens in France"));
        script(NORMALIZATION, answer(ok("[\"S001\",\"S002\",\"S003\",\"S004\"]")));
        Ran r = run(A);
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(requests(NORMALIZATION)).as("valid answer, no retry").hasSize(1);
        assertThat(events(r)).hasSize(1);
        assertThat(event(events(r), "EV001").get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003", "S004"));
    }

    // #28 (review R6): a long disagreement is capped, the summary stays within 600 characters
    @Test
    void aVeryLongDisagreementIsCappedTo200AndTheSentenceStaysComplete() throws Exception {
        Ran r = runScripted(answer(
            ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"health\"", "[]", "\"Health regulators approved a new pandemic vaccine.\"",
                "\"" + "x".repeat(700) + "\"", "0.9"),
            ok("[\"S004\"]")));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        Map<String, Object> e = event(events(r), "EV001");
        assertThat(e.get("disagreement")).isEqualTo("x".repeat(200));
        String expected = "Health regulators approved a new pandemic vaccine. Reports differ on " + "x".repeat(200) + ".";
        assertThat(expected).hasSize(270);
        assertThat(e.get("summary")).isEqualTo(expected);
    }

    // #28 second case
    @Test
    void aVeryLongSummaryIsCutSoThatTheDisagreementSentenceFits() throws Exception {
        String summary = "word ".repeat(140); // 700 characters, no "reports differ"
        Ran r = runScripted(answer(
            ev("[\"S001\",\"S002\",\"S003\"]", "null", "\"health\"", "[]", "\"" + summary + "\"", "\"the dose count\"", "0.9"),
            ok("[\"S004\"]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        String stored = (String) event(events(r), "EV001").get("summary");
        assertThat(stored.length()).isLessThanOrEqualTo(600);
        assertThat(stored).endsWith(" Reports differ on the dose count.");
        assertThat(stored).startsWith("word word word");
    }
}
