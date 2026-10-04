package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.runs.AbstractRunIT;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.context.TestPropertySource;

/**
 * phase-02 news-search.md FR-44 through a whole run (acceptance body A: 20 queries, 4 groups of 5): sources in group then
 * response order with attributed queryIds, which classes of group failure end the run, and what is logged.
 */
// @trace FR-44, FR-47
@ExtendWith(OutputCaptureExtension.class)
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=10",
})
class NewsSearchRunIT extends AbstractRunIT {

    static final String NEWS_DOWN = "ORACUL could not reach its news sources — try again later";

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Object o) {
        return (List<Map<String, Object>>) o;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> queries(String sid, String id) throws Exception {
        return list(((Map<String, Object>) researchBody(sid, id).get("searchPlan")).get("queries"));
    }

    private static String article(String url, String title) {
        return StubGdelt.article(url, title, "reuters.com", "English", StubGdelt.seendate(Instant.now().minus(1, ChronoUnit.DAYS)));
    }

    private Map<String, Object> run(String sid) throws Exception {
        String id = (String) startOk(sid, A).get("id");
        return awaitDone(sid, id);
    }

    @Test
    void sourcesFollowGroupOrderThenResponseOrderAndKeepTheAttributedQueryIds() throws Exception {
        String base = gdelt.baseUrl() + "/articles/";
        gdelt.responder = req -> switch (req.number()) {
            case 1 -> StubGdelt.json(StubGdelt.articles(List.of(article(base + "u1", "u1"), article(base + "u2", "u2"))));
            case 2 -> StubGdelt.json(StubGdelt.articles(List.of(article(base + "u2", "u2 again"), article(base + "u3", "u3"))));
            default -> StubGdelt.json("{}");
        };
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> sources = sourceItems(sid, id);
        assertThat(sources).extracting(s -> s.get("url")).containsExactly(base + "u1", base + "u2", base + "u3");
        assertThat(sources).extracting(s -> s.get("id")).containsExactly("S001", "S002", "S003");
        // titles share no token with any element: every entry goes to the first element of its group (Q01 and Q06)
        assertThat(sources.get(0).get("queryIds")).isEqualTo(List.of("Q01"));
        assertThat(sources.get(1).get("queryIds")).as("seen in both groups: sorted, no duplicates").isEqualTo(List.of("Q01", "Q06"));
        assertThat(sources.get(2).get("queryIds")).isEqualTo(List.of("Q06"));
        List<Map<String, Object>> q = queries(sid, id);
        assertThat(q.get(0).get("articlesReturned")).isEqualTo(2);
        assertThat(q.get(5).get("articlesReturned")).isEqualTo(2);
        assertThat(q.get(1).get("status")).isEqualTo("EMPTY");
        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) json(getRun(sid, id)).get("counts");
        assertThat(counts.get("searches")).isEqualTo(20);
        assertThat(counts.get("articlesRetrieved")).as("Σ articlesReturned = entries of the answered groups").isEqualTo(4);
        assertThat(counts.get("articlesConsidered")).isEqualTo(3);
    }

    @Test
    void anArticleWhoseTitleContainsAnElementIsAttributedToThatQuery() throws Exception {
        String base = gdelt.baseUrl() + "/articles/";
        gdelt.responder = req -> req.number() == 1
            ? StubGdelt.json(StubGdelt.articles(List.of(article(base + "third", req.elements().get(2) + " makes headlines"))))
            : StubGdelt.json("{}");
        String sid = connectedSid();
        String id = (String) startOk(sid, A).get("id");
        assertThat(awaitDone(sid, id).get("status")).isEqualTo("COMPLETED");
        List<Map<String, Object>> q = queries(sid, id);
        assertThat(q.get(0).get("status")).isEqualTo("EMPTY");
        assertThat(q.get(2).get("status")).as("the third element of group 1").isEqualTo("OK");
        assertThat(q.get(2).get("articlesReturned")).isEqualTo(1);
        assertThat(sourceItems(sid, id).get(0).get("queryIds")).isEqualTo(List.of("Q03"));
    }

    static Stream<Arguments> groupFailureClasses() {
        String base = "/articles/";
        return Stream.of(
            Arguments.of("3 groups FAILED + 1 answered empty", 3, "{}", "COMPLETED", 0),
            Arguments.of("3 groups FAILED + 1 with articles", 3, "ARTICLES", "COMPLETED", 1),
            Arguments.of("2 groups FAILED + 2 answered", 2, "ARTICLES", "COMPLETED", 2),
            // run-control.md FR-47: no news at all is no failure any more; the run goes on speculatively
            Arguments.of("all 4 groups FAILED", 4, "{}", "COMPLETED", 0));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("groupFailureClasses")
    void noClassOfGroupFailureEndsTheRun(String name, int failedGroups, String restAnswer, String status,
                                                               int sources) throws Exception {
        String base = gdelt.baseUrl() + "/articles/";
        gdelt.responder = req -> req.number() <= failedGroups ? StubGdelt.status(503)
            : "ARTICLES".equals(restAnswer)
                ? StubGdelt.json(StubGdelt.articles(List.of(article(base + "r" + req.number(), "r" + req.number()))))
                : StubGdelt.json(restAnswer);
        String sid = connectedSid();
        Map<String, Object> run = run(sid);
        assertThat(run.get("status")).as(name + ": " + run).isEqualTo(status);
        assertThat(gdelt.requests).as("each group is sent once").hasSize(4);
        List<Map<String, Object>> q = queries(sid, (String) run.get("id"));
        for (int i = 0; i < 20; i++) {
            boolean failed = i < failedGroups * 5;
            if (failed) assertThat(q.get(i).get("status")).as("Q" + (i + 1)).isEqualTo("FAILED");
            else assertThat(q.get(i).get("status")).as("Q" + (i + 1)).isIn("OK", "EMPTY");
        }
        assertThat(absent(run, "failure")).isTrue();
        assertThat(sourceItems(sid, (String) run.get("id"))).hasSize(sources);
        if (sources == 0) {
            @SuppressWarnings("unchecked")
            Map<String, Object> note = (Map<String, Object>) run.get("evidenceNote");
            assertThat(note).as("no source at all: speculative run with the NO_EVIDENCE note").isNotNull();
            assertThat(note.get("kind")).isEqualTo("NO_EVIDENCE");
            assertThat(run.get("headline")).isNotNull();
        }
    }

    @Test
    void failuresAreLoggedWithoutTheQueryTextOrTheAnswerBody(CapturedOutput out) throws Exception {
        gdelt.responder = req -> req.number() == 1 ? StubGdelt.status(503)
            : req.number() == 2 ? new StubGdelt.Reply(200, "application/json", "{not json PROVIDER-SECRET-BODY", 0)
            : StubGdelt.json("{}");
        String sid = connectedSid();
        assertThat(run(sid).get("status")).isEqualTo("COMPLETED");
        assertThat(out.getOut() + out.getErr()).contains("news request failed: status=503");
        assertThat(out.getOut() + out.getErr()).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("stub query");
    }

    @Test
    void aRateLimitRetryIsLoggedWithoutTheQueryText(CapturedOutput out) throws Exception {
        gdelt.responder = req -> req.number() == 1
            ? new StubGdelt.Reply(429, "text/plain", "Please limit requests to one every 5 seconds PROVIDER-SECRET-BODY", 0)
            : StubGdelt.json("{}");
        String sid = connectedSid();
        assertThat(run(sid).get("status")).isEqualTo("COMPLETED");
        assertThat(out.getOut() + out.getErr()).contains("news request rate-limited, retrying once");
        assertThat(out.getOut() + out.getErr()).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("stub query");
    }
}
