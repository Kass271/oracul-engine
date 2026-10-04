package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.StubResponses;
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
 * run-control.md FR-47 rule "with all thresholds 0 (E2E medium/low) INSUFFICIENT_EVIDENCE cannot occur; NO_EVIDENCE still can":
 * the AbstractRunIT context sets every oracul.evidence.min-core.* to 0. Walks every realism 1-10.
 */
// @trace FR-47
class EvidenceNoteNoThresholdIT extends AbstractStoryIT {

    private static String bodyA(int realism) {
        return A.replace("\"realism\":8", "\"realism\":" + realism);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> note(Map<String, Object> run) {
        assertThat(run.get("evidenceNote")).as("evidenceNote of " + run).isNotNull();
        return (Map<String, Object>) run.get("evidenceNote");
    }

    static Stream<Integer> realisms() {
        return IntStream.rangeClosed(1, 10).boxed();
    }

    /** K(0, 3): no CORE item at all, three counter-signal candidates. */
    private Ran runCounterSignalsOnly(String body) throws Exception {
        gdelt.reset();
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= 3; i++) {
            arts.add(new Art("z-" + i, "reuters.com", "Z article " + i));
            gdelt.site("z-" + i, "Publisher " + i);
        }
        gdeltArticles(arts);
        always(CLASSIFICATION, req -> {
            List<String> entries = new ArrayList<>();
            for (String id : StubResponses.eventIds(req.inputText())) {
                entries.add(StubResponses.defaultClassificationEntry(id).replace("\"risk\":0.4", "\"risk\":0.1")
                    .replace("\"opportunity\":0.6", "\"opportunity\":0.8"));
            }
            return StubResponses.completed("{\"classifications\":[" + String.join(",", entries) + "]}");
        });
        Ran r = run(body);
        assertThat(section(pack(r), "core")).isEmpty();
        assertThat(section(pack(r), "counterSignals")).isNotEmpty();
        return r;
    }

    @ParameterizedTest(name = "thresholds 0, items in the pack, realism {0}")
    @MethodSource("realisms")
    void withThresholdsZeroAndAnItemInThePackThereIsNoNote(int realism) throws Exception {
        Ran r = runCounterSignalsOnly(bodyA(realism));
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("evidenceNote")).as("needed 0 -> core 0 meets it").isNull();
        assertThat(run.get("suggestedRealism")).isNull();
        assertThat(run.get("headline")).isNotNull();
    }

    @ParameterizedTest(name = "thresholds 0, empty pack, realism {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void noEvidenceStillHappensWithThresholdsZero(int realism) throws Exception {
        gdelt.reset();
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
