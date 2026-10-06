package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Parser rules of research-pipeline.md "FR-15 - Classification validation" (stored classification, ignored entries). */
// @trace FR-15
class EventClassificationRulesIT extends AbstractEventIT {

    private Ran runScripted(String bodyJson, String... classificationAnswers) throws Exception {
        newsArticles(v4());
        script(NORMALIZATION, N_V4);
        script(CLASSIFICATION, classificationAnswers);
        return run(bodyJson);
    }

    private static String entry(String id, String topic, String subtopics, String geography, String wildcards) {
        return "{\"eventId\":\"" + id + "\",\"topic\":" + topic + ",\"subtopics\":" + subtopics + ",\"sentiment\":0.1,\"risk\":0.4,"
            + "\"opportunity\":0.6,\"impact\":0.5,\"novelty\":0.5,\"trend\":\"ESTABLISHED\",\"geography\":" + geography
            + ",\"wildcardMatches\":" + wildcards + "}";
    }

    @Test
    void firstEntryPerEventWinsAndUnknownEventIdsAreIgnored() throws Exception {
        Ran r = runScripted(A, classifications(
            entry("EV001", "\"first\"", "[]", "\"global\"", "[]"),
            entry("EV999", "\"ghost\"", "[]", "\"global\"", "[]"),
            entry("EV001", "\"second\"", "[]", "\"global\"", "[]"),
            entry("EV002", "\"labour\"", "[]", "\"global\"", "[]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(requests(CLASSIFICATION)).as("nothing bad, no follow-up").hasSize(1);
        List<Map<String, Object>> events = events(r);
        assertThat(events).hasSize(2);
        assertThat(classification(event(events, "EV001")).get("topic")).isEqualTo("first");
        assertThat(classification(event(events, "EV002")).get("topic")).isEqualTo("labour");
        assertThat(events).allSatisfy(e -> assertThat(omitted(e, "excludedReason")).isTrue());
    }

    @Test
    void textFieldsAreTrimmedAndBlankGeographyBecomesGlobal() throws Exception {
        Ran r = runScripted(A, classifications(
            entry("EV001", "\"  health \"", "[\" vaccines \",\"  \",\"policy\"]", "\"   \"", "[]"),
            entry("EV002", "\"labour\"", "[]", "\" Europe \"", "[]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        Map<String, Object> c1 = classification(event(events, "EV001"));
        assertThat(c1.get("topic")).isEqualTo("health");
        assertThat(c1.get("subtopics")).isEqualTo(List.of("vaccines", "policy"));
        assertThat(c1.get("geography")).isEqualTo("global");
        assertThat(classification(event(events, "EV002")).get("geography")).isEqualTo("Europe");
    }

    @Test
    void wildcardMatchesFollowTheProfileOrderFirstEntryWinsAndUnknownKeysAreDropped() throws Exception {
        Ran r = runScripted(A, classifications(
            entry("EV001", "\"health\"", "[]", "\"global\"",
                "[{\"key\":\"robotics-humanoid-boom\",\"score\":0.3},{\"key\":\"foo\",\"score\":0.9},"
                    + "{\"key\":\"robotics-humanoid-boom\",\"score\":0.7}]"),
            entry("EV002", "\"labour\"", "[]", "\"global\"", "[]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> events = events(r);
        EventClassificationIT.assertWildcards(event(events, "EV001"), 0.0, 0.3);
        EventClassificationIT.assertWildcards(event(events, "EV002"), 0.0, 0.0);
    }

    @Test
    void withoutWildcardTopicsTheKeyListIsNoneAndMatchesAreEmpty() throws Exception {
        Ran r = runScripted(B, classifications(
            entry("EV001", "\"health\"", "[]", "\"global\"", "[{\"key\":\"biology-new-pandemic\",\"score\":0.9}]"),
            entry("EV002", "\"labour\"", "[]", "\"global\"", "[]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(requests(CLASSIFICATION).get(0).inputText()).contains("Wildcard keys: none\n");
        for (Map<String, Object> e : events(r)) assertThat(wildcardMatches(e)).isEmpty();
    }

    @Test
    void boundaryValuesAreAcceptedAsGiven() throws Exception {
        String edge = "{\"eventId\":\"EV001\",\"topic\":\"health\",\"subtopics\":[],\"sentiment\":-1,\"risk\":1,\"opportunity\":0,"
            + "\"impact\":1,\"novelty\":0,\"trend\":\"DECLINING\",\"geography\":\"global\","
            + "\"wildcardMatches\":[{\"key\":\"biology-new-pandemic\",\"score\":1}]}";
        Ran r = runScripted(A, classifications(edge, entry("EV002", "\"labour\"", "[]", "\"global\"", "[]")));
        assertThat(r.run().get("status")).isEqualTo("COMPLETED");
        assertThat(requests(CLASSIFICATION)).hasSize(1);
        Map<String, Object> c = classification(event(events(r), "EV001"));
        assertThat(num(c.get("sentiment"))).isEqualTo(-1.0);
        assertThat(num(c.get("risk"))).isEqualTo(1.0);
        assertThat(num(c.get("opportunity"))).isEqualTo(0.0);
        assertThat(c.get("trend")).isEqualTo("DECLINING");
        assertThat(num(wildcardMatches(event(events(r), "EV001")).get(0).get("score"))).isEqualTo(1.0);
    }
}
