package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * run-control.md FR-47 (with wildcard-evidence.md slice 06) rule "with all thresholds 0 (E2E medium/low) INSUFFICIENT_EVIDENCE cannot occur; NO_EVIDENCE still can":
 * the AbstractRunIT context sets every oracul.evidence.min-core.* to 0. Walks every realism 1-10.
 */
// @trace FR-47, FR-57
class EvidenceNoteNoThresholdIT extends AbstractStoryIT {

    private static String bodyA(int realism) {
        return A.replace("\"realism\":8", "\"realism\":" + realism);
    }

    private static Map<String, Object> note(Map<String, Object> run) {
        assertThat(run.get("evidenceNote")).as("evidenceNote of " + run).isNotNull();
        return (Map<String, Object>) run.get("evidenceNote");
    }

    static Stream<Integer> realisms() {
        return IntStream.rangeClosed(1, 10).boxed();
    }

    /**
     * Three articles served as W01 2 + W02 1 under body A (wildcard-evidence.md slice 06: every kept source is an item of the pack,
     * there is no CORE / counter-signal split any more), default classification.
     */
    private Ran runThreeSources(String body) throws Exception {
        news.reset();
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            arts.add(new Art("z-" + i, "reuters.com", "Z article " + i));
            news.site("z-" + i, "Publisher " + i);
        }
        newsArticlesPerPipeline(arts, 2, 1);
        Ran r = run(body);
        Map<String, Object> pack = pack(r);
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        assertThat(sectionIds(pack)).as("3 distinct section items").hasSize(3);
        assertThat(wildcardSections(pack).stream().map(sec -> items(sec).size()).toList()).containsExactly(2, 1);
        return r;
    }

    @ParameterizedTest(name = "thresholds 0, items in the pack, realism {0}")
    @MethodSource("realisms")
    void withThresholdsZeroAndAnItemInThePackThereIsNoNote(int realism) throws Exception {
        Ran r = runThreeSources(bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("evidenceNote")).as("needed 0 -> the 3 items meet it").isNull();
        assertThat(counts(run).get("eventsSelected")).isEqualTo(3);
        assertThat(counts(run).get("counterSignals")).isEqualTo(0);
        assertThat(run.get("suggestedRealism")).isNull();
        assertThat(run.get("headline")).isNotNull();
    }

    @ParameterizedTest(name = "thresholds 0, empty pack, realism {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void noEvidenceStillHappensWithThresholdsZero(int realism) throws Exception {
        news.reset();
        Ran r = run(bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("kind", "NO_EVIDENCE");
        expected.put("message", "No current news could be used — this future is speculative, not grounded in evidence.");
        expected.put("coreItems", 0);
        expected.put("coreNeeded", 0);
        assertThat(note(run)).isEqualTo(expected);
        assertThat(run.get("suggestedRealism")).isNull();
        assertThat(run.get("headline")).isNotNull();
    }
}
