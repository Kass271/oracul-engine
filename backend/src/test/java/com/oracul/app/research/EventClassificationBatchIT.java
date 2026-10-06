package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** research-pipeline.md pipeline step 4: classification batches and the single follow-up pass in id order. */
// @trace FR-15
@TestPropertySource(properties = "oracul.events.classification-batch-size=2")
class EventClassificationBatchIT extends AbstractEventIT {

    @Test
    void badEventsOfAllBatchesAreRetriedTogetherInIdOrderInBatchesOfTheSameSize() throws Exception {
        List<Art> five = new ArrayList<>();
        for (int i = 1; i <= 5; i++) five.add(new Art("a" + i, "reuters.com", "Distinct headline number" + " " + "x".repeat(i)));
        newsArticles(five);
        // answers are keyed by the event ids of the request, not by arrival order (batches run in parallel):
        // an event asked for the first time is answered only when its number is even; asked again, always
        Set<String> asked = ConcurrentHashMap.newKeySet();
        always(CLASSIFICATION, req -> {
            List<String> entries = new ArrayList<>();
            for (String id : StubResponses.eventIds(req.inputText())) {
                boolean firstTime = asked.add(id);
                if (!firstTime || Integer.parseInt(id.substring(2)) % 2 == 0) entries.add(StubResponses.defaultClassificationEntry(id));
            }
            return StubResponses.completed(classifications(entries.toArray(new String[0])));
        });
        Ran r = run(B);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        List<StubResponses.Request> calls = requests(CLASSIFICATION);
        assertThat(calls).hasSize(5);
        // arrival order inside a pass is not defined; the follow-up pass starts only after the whole first pass finished
        assertThat(sortedByFirstEvent(calls.subList(0, 3))).containsExactly(
            List.of("EV001", "EV002"), List.of("EV003", "EV004"), List.of("EV005"));
        assertThat(sortedByFirstEvent(calls.subList(3, 5))).containsExactly(List.of("EV001", "EV003"), List.of("EV005"));
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(5);
        assertThat(events).allSatisfy(e -> {
            assertThat(e.get("classification")).isNotNull();
            assertThat(omitted(e, "excludedReason")).isTrue();
        });
    }

    private static List<List<String>> sortedByFirstEvent(List<StubResponses.Request> calls) {
        return calls.stream().map(q -> StubResponses.eventIds(q.inputText()))
            .sorted(java.util.Comparator.comparing(ids -> ids.get(0))).toList();
    }
}
