package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Rows #1, #2 and #6 of research-pipeline.md "Slice 07_evidence-pack": ranking and selection through the API. */
// @trace FR-16, FR-17
class RankingSelectionIT extends AbstractEvidenceIT {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> factors(Map<String, Object> event) {
        Map<String, Object> ranking = (Map<String, Object>) event.get("ranking");
        assertThat(ranking).as(event.get("id") + " has a ranking").isNotNull();
        return (Map<String, Object>) ranking.get("factors");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> rankingOf(Map<String, Object> event) {
        return (Map<String, Object>) event.get("ranking");
    }

    private static void assertFactors(Map<String, Object> event, double topic, double wildcard, double darkness, double optimism,
                                      double sq, double impact, double trend, double cross, double realism) {
        Map<String, Object> f = factors(event);
        assertThat(num(f.get("topicMatch"))).as("topicMatch").isEqualTo(topic);
        assertThat(num(f.get("wildcardMatch"))).as("wildcardMatch").isEqualTo(wildcard);
        assertThat(num(f.get("darknessMatch"))).as("darknessMatch").isEqualTo(darkness);
        assertThat(num(f.get("optimismMatch"))).as("optimismMatch").isEqualTo(optimism);
        assertThat(num(f.get("recency"))).as("recency").isBetween(0.0, 1.0);
        assertThat(num(f.get("sourceQuality"))).as("sourceQuality").isEqualTo(sq);
        assertThat(num(f.get("impact"))).as("impact").isEqualTo(impact);
        assertThat(num(f.get("trendStrength"))).as("trendStrength").isEqualTo(trend);
        assertThat(num(f.get("crossTopic"))).as("crossTopic").isEqualTo(cross);
        assertThat(num(f.get("realismCompatibility"))).as("realismCompatibility").isEqualTo(realism);
    }

    private static void assertSelection(Map<String, Object> event, String evidenceId, String section) {
        assertThat(event.get("selection")).as(event.get("id") + " selection").isEqualTo(Map.of("evidenceId", evidenceId, "section", section));
    }

    // #1
    @Test
    void darkAcceptanceRunRanksAndSelectsTheTwoEvents() throws Exception {
        Ran r = runV4(A);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events.stream().map(e -> e.get("id")).toList()).containsExactly("EV001", "EV002");

        Map<String, Object> ev1 = event(events, "EV001");
        assertFactors(ev1, 1.0, 0.72, 0.18, 0.16, 0.95, 0.7, 0.7, 0.0, 0.72);
        assertThat(num(rankingOf(ev1).get("reliability"))).isEqualTo(0.95);
        assertSelection(ev1, "E002", "COUNTER_SIGNAL");

        Map<String, Object> ev2 = event(events, "EV002");
        assertFactors(ev2, 1.0, 0.48, 0.54, 0.06, 0.85, 0.5, 0.7, 0.0, 0.4133);
        assertThat(num(rankingOf(ev2).get("reliability"))).isEqualTo(0.85);
        assertSelection(ev2, "E001", "CORE");

        for (Map<String, Object> e : events) {
            Map<String, Object> rk = rankingOf(e);
            for (String k : List.of("relevance", "reliability", "score")) assertThat(num(rk.get(k))).as(k).isBetween(0.0, 1.0);
            assertThat(num(rk.get("score"))).isEqualTo(Math.round(num(rk.get("score")) * 10000) / 10000.0);
            assertThat(omitted(e, "excludedReason")).isTrue();
        }
        assertThat(num(rankingOf(ev1).get("score"))).as("EV001 outranks EV002 by score").isGreaterThan(num(rankingOf(ev2).get("score")));

        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(2);
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(1);
        assertThat(counts(researchBody(r.sid(), r.id()))).as("getRunResearch counts").isEqualTo(counts(r.run()));

        Map<String, Object> db1 = jdbc.queryForMap(
            "select evidence_id, selection_section from event where run_id = cast(? as uuid) and id = 'EV001'", r.id());
        assertThat(db1).containsEntry("evidence_id", "E002").containsEntry("selection_section", "COUNTER_SIGNAL");
        Map<String, Object> db2 = jdbc.queryForMap(
            "select evidence_id, selection_section from event where run_id = cast(? as uuid) and id = 'EV002'", r.id());
        assertThat(db2).containsEntry("evidence_id", "E001").containsEntry("selection_section", "CORE");
    }

    // #2
    @Test
    void brightProfileSwapsCoreAndCounterSignalWithoutChangingEventContent() throws Exception {
        Ran dark = runV4(A);
        List<Map<String, Object>> darkEvents = events(dark);
        Ran bright = runV4(A_BRIGHT);
        assertThat(bright.run().get("status")).as("run: " + bright.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(bright);
        assertThat(events.stream().map(e -> e.get("id")).toList()).containsExactly("EV001", "EV002");
        assertSelection(event(events, "EV001"), "E001", "CORE");
        assertSelection(event(events, "EV002"), "E002", "COUNTER_SIGNAL");
        assertThat(counts(bright.run()).get("eventsSelected")).isEqualTo(2);
        assertThat(counts(bright.run()).get("counterSignals")).isEqualTo(1);
        for (String id : List.of("EV001", "EV002")) {
            Map<String, Object> d = event(darkEvents, id);
            Map<String, Object> b = event(events, id);
            assertThat(b.get("summary")).as(id + " summary").isEqualTo(d.get("summary"));
            assertThat(b.get("category")).as(id + " category").isEqualTo(d.get("category"));
            assertThat(b.get("entities")).as(id + " entities").isEqualTo(d.get("entities"));
            assertThat(b.get("classification")).as(id + " classification").isEqualTo(d.get("classification"));
            assertThat(rankingOf(b)).as(id + " ranking differs by profile").isNotEqualTo(rankingOf(d));
        }
    }

    // #6
    @Test
    void anEventWithoutClassificationHasNoRankingIsListedLastAndIsNotSelected() throws Exception {
        String bad = C_EV2.replace("\"risk\":0.6", "\"risk\":1.2");
        Ran r = runV4(A, N_V4, classifications(C_EV1, bad), classifications(bad));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        assertThat(events.stream().map(e -> e.get("id")).toList()).containsExactly("EV001", "EV002");
        Map<String, Object> ev2 = event(events, "EV002");
        assertThat(ev2).doesNotContainKey("ranking").doesNotContainKey("selection");
        assertThat(ev2.get("excludedReason")).isEqualTo("CLASSIFICATION_FAILED");
        assertSelection(event(events, "EV001"), "E001", "COUNTER_SIGNAL");

        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).hasSize(1);
        assertThat(section(pack, "counterSignals").get(0)).containsEntry("eventId", "EV001").containsEntry("evidenceId", "E001")
            .containsEntry("section", "COUNTER_SIGNAL");
        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(1);
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(1);
    }

    @Test
    void rankingIsDeterministicAcrossRuns() throws Exception {
        Ran a = runV4(A);
        Ran b = runV4(A);
        List<Map<String, Object>> ea = events(a);
        List<Map<String, Object>> eb = events(b);
        for (String id : List.of("EV001", "EV002")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> fa = (Map<String, Object>) rankingOf(event(ea, id)).get("factors");
            @SuppressWarnings("unchecked")
            Map<String, Object> fb = (Map<String, Object>) rankingOf(event(eb, id)).get("factors");
            assertThat(fb.get("topicMatch")).isEqualTo(fa.get("topicMatch"));
            assertThat(event(eb, id).get("selection")).isEqualTo(event(ea, id).get("selection"));
        }
    }
}
