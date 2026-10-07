package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/**
 * FR-53 "Source selection per wildcard" through whole runs (article-retrieval.md, "WildcardSelectionIT", acceptance 1-4): the top 4
 * by the deterministic relevance score per pipeline, a shared article as one source, the cap of 30 by pipeline round robin, the
 * selection independent of the event classification, and the invariants every run satisfies. One run per scenario; the stub query
 * texts are "W&lt;nn&gt; stub query &lt;i&gt;" and each fixture answers the queries by that text.
 */
// @trace FR-53
@TestPropertySource(properties = {
    "oracul.run.placeholder-stage-delay=PT0S",
    "oracul.run.executor-threads=8",
    "oracul.events.max-sources=1000",
})
class WildcardSelectionIT extends AbstractEventIT {

    private static final String CUSTOM_BODY = "{\"realism\":8,\"darkness\":9,\"optimism\":2,\"horizon\":\"5y\",\"wildcards\":[],"
        + "\"customWildcards\":[{\"label\":\"Quantum internet\",\"intensity\":5}],\"output\":{\"story\":true,\"illustration\":false}}";

    private static final String TWO_BODY = "{\"realism\":8,\"darkness\":9,\"optimism\":2,\"horizon\":\"5y\",\"wildcards\":["
        + "{\"wildcardId\":\"biology-new-pandemic\",\"intensity\":5},{\"wildcardId\":\"energy-energy-crisis\",\"intensity\":5}],"
        + "\"customWildcards\":[],\"output\":{\"story\":true,\"illustration\":false}}";

    private static Art art(String name, String title) {
        return new Art(name, "reuters.com", title);
    }

    private static String idOf(int n) {
        return String.format("S%03d", n);
    }

    // ---- invariants of every run ------------------------------------------------------------------------------

    /** The run invariants of the spec: counts, numbering, Evidence order, per-pipeline fields, article fetches. */
    @SuppressWarnings("unchecked")
    private void assertRunInvariants(Ran r) throws Exception {
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("run: " + run).isEqualTo("COMPLETED");
        Map<String, Object> counts = counts(run);
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(r.sid(), r.id()));
        int rows = jdbc.queryForObject("select count(*) from source where run_id = cast(? as uuid)", Integer.class, r.id());
        assertThat(counts.get("sourcesKept")).as("the READING_SOURCES commit writes counts.sourcesKept").isNotNull();
        for (Map<String, Object> p : pipelines) {
            assertThat(p.get("candidatesConsidered")).as(p.get("id") + ".candidatesConsidered is written by the commit").isNotNull();
        }
        int kept = ((Number) counts.get("sourcesKept")).intValue();
        assertThat(kept).as("sourcesKept = listRunSources length = source rows <= 30").isEqualTo(sources.size()).isEqualTo(rows)
            .isLessThanOrEqualTo(30);
        List<String> ids = sources.stream().map(s -> (String) s.get("id")).toList();
        for (int i = 0; i < ids.size(); i++) assertThat(ids.get(i)).as("S-ids contiguous").isEqualTo(idOf(i + 1));

        Set<String> evidenceOrder = new LinkedHashSet<>();
        int sumConsidered = 0;
        for (Map<String, Object> p : pipelines) {
            List<String> sourceIds = (List<String>) p.get("sourceIds");
            assertThat(sourceIds).as(p.get("id") + " sourceIds present").isNotNull();
            assertThat(ids).as(p.get("id") + " sourceIds subset of the sources").containsAll(sourceIds);
            evidenceOrder.addAll(sourceIds);
            sumConsidered += ((Number) p.get("candidatesConsidered")).intValue();
            for (Map<String, Object> s : sources) {
                boolean listed = sourceIds.contains(s.get("id"));
                boolean owns = ((List<String>) s.get("pipelineIds")).contains(p.get("id"));
                assertThat(listed).as(p.get("id") + " lists " + s.get("id") + " iff its pipelineIds contain it").isEqualTo(owns);
            }
        }
        assertThat(new ArrayList<>(evidenceOrder)).as("listRunSources order = Evidence order = pipelines' sourceIds, first appearance kept")
            .isEqualTo(ids);
        int considered = ((Number) counts.get("articlesConsidered")).intValue();
        assertThat(sumConsidered).as("sum of candidatesConsidered >= articlesConsidered >= sourcesKept").isGreaterThanOrEqualTo(considered);
        assertThat(considered).isGreaterThanOrEqualTo(kept);
        assertThat(news.articleRequests).as("article fetches = kept sources, each link at most once").hasSize(kept)
            .doesNotHaveDuplicates();
        Set<String> urls = new HashSet<>();
        for (Map<String, Object> s : sources) assertThat(urls.add((String) s.get("url"))).as("no link twice").isTrue();
        for (Map<String, Object> s : sources) {
            assertThat(s.get("topic")).as(s.get("id") + " topic = topicKey of its first pipeline").isEqualTo(
                pipelines.stream().filter(p -> p.get("id").equals(((List<String>) s.get("pipelineIds")).get(0))).findFirst().orElseThrow()
                    .getOrDefault("topicKey", "major"));
        }
    }

    // ---- acceptance 1: one pipeline, 40 distinct items, the top 4 by score ------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void onePipelineWithFortyDistinctItemsKeepsTheFourHighestScoringOnes() throws Exception {
        List<Art> q1 = new ArrayList<>();
        List<Art> q2 = new ArrayList<>();
        List<Art> q3 = new ArrayList<>();
        for (int i = 1; i <= 14; i++) q1.add(art("a" + i, "Filler a" + i));
        for (int i = 1; i <= 13; i++) q2.add(art("b" + i, "Filler b" + i));
        for (int i = 1; i <= 14; i++) q3.add(art("c" + i, "Filler c" + i));
        q1.set(2, art("dup", "Internet watch"));              // Q01 position 3 and Q03 position 3: found by 2 queries
        q3.set(2, art("dup", "Internet watch"));
        q1.set(13, art("a14", "Internet outage report"));      // label term "internet": 3
        q2.set(9, art("b10", "Quantum internet breakthrough")); // two label terms: 6
        q2.set(12, art("b13", "Stub query digest"));           // two query terms: 2
        q3.set(11, art("c12", "Quantum network update"));      // label term: 3
        // c5 has a plain title; its description (the snippet) holds a label term: 3
        List<String> a = q1.stream().map(this::itemOf).toList();
        List<String> b = q2.stream().map(this::itemOf).toList();
        List<String> c = new ArrayList<>(q3.stream().map(this::itemOf).toList());
        c.set(4, StubNews.rssItem("Filler c5", news.baseUrl() + "/rss/articles/c5", StubNews.pubDate(testNow.minusSeconds(86_400)),
            "reuters.com", "https://reuters.com", "<p>Researchers connect <b>quantum</b> computers</p>"));
        news.responder = req -> {
            int[] q = StubNews.stubQuery(req);
            if (q == null || q[0] != 1) return StubNews.rss();
            return switch (q[1]) {
                case 1 -> StubNews.rss(a.toArray(String[]::new));
                case 2 -> StubNews.rss(b.toArray(String[]::new));
                default -> StubNews.rss(c.toArray(String[]::new));
            };
        };
        Ran r = run(CUSTOM_BODY);
        assertRunInvariants(r);

        Map<String, Object> counts = counts(r.run());
        assertThat(counts.get("searches")).isEqualTo(3);
        assertThat(counts.get("articlesRetrieved")).isEqualTo(41);
        assertThat(counts.get("articlesConsidered")).as("40 distinct candidates").isEqualTo(40);
        assertThat(counts.get("sourcesKept")).isEqualTo(4);
        Map<String, Object> pipeline = PlanJson.pipelines(researchBody(r.sid(), r.id())).get(0);
        assertThat(pipeline.get("label")).isEqualTo("Quantum internet");
        assertThat(pipeline.get("candidatesConsidered")).isEqualTo(40);
        assertThat(pipeline.get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003", "S004"));

        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        // scores: b10 6, dup 3 + 2 = 5, then the three of score 3 by feed position: c5 (5, via its snippet), c12 (12), a14 (14)
        assertThat(sources.stream().map(s -> s.get("title")).toList()).as("top 4 by score, not the first 4 of the feed")
            .containsExactly("Quantum internet breakthrough", "Internet watch", "Filler c5", "Quantum network update");
        assertThat(sources.get(1).get("queryIds")).isEqualTo(List.of("Q01", "Q03"));
        assertThat(news.articleRequests).as("the article fetch runs for the kept sources only")
            .containsExactlyInAnyOrder("b10", "dup", "c5", "c12");
    }

    // ---- GENERAL pipeline: labelText = GENERAL_SUBJECT, not the label "General" -----------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void theGeneralPipelineScoresTheTokensOfTheSubjectNotItsLabel() throws Exception {
        // body B (no wildcards) = one GENERAL pipeline W01 with the label "General"; its labelText must be "major current world events"
        List<Art> feed = new ArrayList<>();
        for (int i = 1; i <= 12; i++) feed.add(art("g" + i, "Plain headline g" + i));
        feed.set(1, art("g2", "General overview of g2"));           // would score 3 if the label "General" were scored; 0 for the subject
        feed.set(6, art("g7", "World summit opens"));              // subject token "world": 3
        feed.set(7, art("g8", "Major events ahead"));              // "major", "events": 6
        feed.set(8, art("g9", "Current world events"));            // "current", "world", "events": 9
        feed.set(9, art("g10", "Events calendar"));                // "events": 3, but loses to g7 on the feed position
        feed.set(11, art("g12", "Major world events current"));    // all four subject tokens: 12
        List<String> items = feed.stream().map(this::itemOf).toList();
        news.responder = StubNews.firstQueryItems(List.of(items));
        Ran r = run(B);
        assertRunInvariants(r);

        Map<String, Object> counts = counts(r.run());
        assertThat(counts.get("articlesConsidered")).isEqualTo(12);
        assertThat(counts.get("sourcesKept")).isEqualTo(4);
        Map<String, Object> pipeline = PlanJson.pipelines(researchBody(r.sid(), r.id())).get(0);
        assertThat(pipeline.get("id")).isEqualTo("W01");
        assertThat(pipeline.get("candidatesConsidered")).isEqualTo(12);
        assertThat(pipeline.get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003", "S004"));
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources.stream().map(s -> s.get("title")).toList()).as("top 4 by the subject tokens x 3, ties by feed position")
            .containsExactly("Major world events current", "Current world events", "Major events ahead", "World summit opens");
        assertThat(sources).allSatisfy(s -> {
            assertThat(s.get("pipelineIds")).isEqualTo(List.of("W01"));
            assertThat(s.get("topic")).isEqualTo("major");
        });
        assertThat(news.articleRequests).containsExactlyInAnyOrder("g12", "g9", "g8", "g7");
    }

    // ---- acceptance 2: the same link in two pipelines ----------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void aLinkFoundByTwoPipelinesIsOneSourceListedInBoth() throws Exception {
        List<Art> w1 = List.of(art("shared-story", "Plain headline of the shared story"), art("w1-a2", "Plain headline w1 a2"),
            art("w1-a3", "Plain headline w1 a3"));
        List<Art> w2 = List.of(art("w2-x1", "Plain headline w2 x1"), art("shared-story", "Other words for the same link"),
            art("w2-x3", "Plain headline w2 x3"));
        news.responder = StubNews.firstQueryItems(List.of(w1.stream().map(this::itemOf).toList(), w2.stream().map(this::itemOf).toList()));
        Ran r = run(TWO_BODY);
        assertRunInvariants(r);

        Map<String, Object> counts = counts(r.run());
        assertThat(counts.get("articlesConsidered")).as("3 + 3 - 1 distinct links").isEqualTo(5);
        assertThat(counts.get("sourcesKept")).isEqualTo(5);
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources).hasSize(5);
        List<Map<String, Object>> shared = sources.stream().filter(s -> ((String) s.get("url")).endsWith("/articles/shared-story")).toList();
        assertThat(shared).as("one row for the shared link").hasSize(1);
        assertThat(shared.get(0).get("id")).isEqualTo("S001");
        assertThat(shared.get(0).get("pipelineIds")).isEqualTo(List.of("W01", "W02"));
        assertThat(shared.get(0).get("queryIds")).isEqualTo(List.of("Q01", "Q04"));
        assertThat(shared.get(0).get("topic")).as("topicKey of its first pipeline").isEqualTo("biology-new-pandemic");
        assertThat(shared.get(0).get("title")).as("first occurrence over the plan").isEqualTo("Plain headline of the shared story");
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(r.sid(), r.id()));
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003"));
        assertThat(pipelines.get(1).get("sourceIds")).as("W02's group keeps its own rank: x1, shared, x3").isEqualTo(List.of("S004", "S001", "S005"));
        assertThat(pipelines.get(0).get("candidatesConsidered")).isEqualTo(3);
        assertThat(pipelines.get(1).get("candidatesConsidered")).isEqualTo(3);
        assertThat(news.articleRequests.stream().filter("shared-story"::equals).count()).as("fetched once").isEqualTo(1);
    }

    // ---- acceptance 3: nine pipelines, 4 distinct items each, the cap of 30 ----------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void nineWildcardsWithFourItemsEachKeepThirtyAndEveryPipelineHasSources() throws Exception {
        List<List<String>> perPipeline = new ArrayList<>();
        for (int j = 1; j <= 9; j++) {
            List<String> items = new ArrayList<>();
            for (int i = 1; i <= 4; i++) items.add(itemOf(art("p" + j + "-" + i, "Plain headline p" + j + " number " + i)));
            perPipeline.add(items);
        }
        news.responder = StubNews.firstQueryItems(perPipeline);
        Ran r = run(wildcardsBody(9));
        assertRunInvariants(r);

        Map<String, Object> counts = counts(r.run());
        assertThat(counts.get("searches")).isEqualTo(18);
        assertThat(counts.get("articlesConsidered")).isEqualTo(36);
        assertThat(counts.get("sourcesKept")).as("rounds 1-3 give 27, round 4 the 4th of W01-W03").isEqualTo(30);
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(r.sid(), r.id()));
        for (int j = 0; j < 9; j++) {
            List<String> sourceIds = (List<String>) pipelines.get(j).get("sourceIds");
            assertThat(sourceIds).as("W0" + (j + 1)).hasSize(j < 3 ? 4 : 3);
            assertThat(pipelines.get(j).get("candidatesConsidered")).isEqualTo(4);
        }
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002", "S003", "S004"));
        assertThat(pipelines.get(1).get("sourceIds")).isEqualTo(List.of("S005", "S006", "S007", "S008"));
    }

    // ---- candidates come from OK queries only; found by several queries ranks first ---------------------------

    @Test
    @SuppressWarnings("unchecked")
    void aFailedQueryContributesNoCandidatesAndAnArticleOfThreeQueriesRanksFirst() throws Exception {
        String base = news.baseUrl();
        List<String> w1q1 = List.of(itemOf(art("a1", "Plain headline a1")), itemOf(art("a2", "Plain headline a2")));
        List<String> w2all = List.of(itemOf(art("b1", "Plain headline b1")));
        List<String> w2q1 = List.of(itemOf(art("b2", "Plain headline b2")), itemOf(art("b1", "Plain headline b1")),
            itemOf(art("b3", "Plain headline b3")));
        news.responder = req -> {
            int[] q = StubNews.stubQuery(req);
            if (q == null) return StubNews.rss();
            if (q[0] == 1) return q[1] == 1 ? StubNews.rss(w1q1.toArray(String[]::new)) : q[1] == 2 ? StubNews.status(503) : StubNews.rss();
            return q[1] == 1 ? StubNews.rss(w2q1.toArray(String[]::new)) : StubNews.rss(w2all.toArray(String[]::new));
        };
        Ran r = run(wildcardsBody(2));
        assertRunInvariants(r);
        List<Map<String, Object>> queries = PlanJson.queries(researchBody(r.sid(), r.id()));
        assertThat(queries.stream().map(q -> q.get("status")).toList()).containsExactly("OK", "FAILED", "EMPTY", "OK", "OK", "OK");
        List<Map<String, Object>> pipelines = PlanJson.pipelines(researchBody(r.sid(), r.id()));
        assertThat(pipelines.get(0).get("candidatesConsidered")).isEqualTo(2);
        assertThat(pipelines.get(0).get("sourceIds")).isEqualTo(List.of("S001", "S002"));
        assertThat(pipelines.get(1).get("candidatesConsidered")).isEqualTo(3);
        // b1 was returned by Q04 (position 2), Q05 and Q06 (position 1): score 0 + 4 beats b2 (0) at position 1
        assertThat(pipelines.get(1).get("sourceIds")).isEqualTo(List.of("S003", "S004", "S005"));
        List<Map<String, Object>> sources = sourceItems(r.sid(), r.id());
        assertThat(sources.get(2).get("url")).isEqualTo(base + "/articles/b1");
        assertThat(sources.get(2).get("queryIds")).isEqualTo(List.of("Q04", "Q05", "Q06"));
        assertThat(sources.get(3).get("url")).isEqualTo(base + "/articles/b2");
        assertThat(sources.get(4).get("url")).isEqualTo(base + "/articles/b3");
        assertThat(counts(r.run()).get("sourcesKept")).isEqualTo(5);
    }

    // ---- acceptance 4: no dependency on the classification ---------------------------------------------------

    private static StubResponses.Reply classified(StubResponses.Request req, String risk, String opportunity) {
        List<String> entries = new ArrayList<>();
        for (String id : StubResponses.eventIds(req.inputText())) {
            entries.add(StubResponses.defaultClassificationEntry(id).replace("\"risk\":0.4", "\"risk\":" + risk)
                .replace("\"opportunity\":0.6", "\"opportunity\":" + opportunity));
        }
        return StubResponses.completed("{\"classifications\":[" + String.join(",", entries) + "]}");
    }

    @Test
    @SuppressWarnings("unchecked")
    void theKeptSetDoesNotDependOnWhatTheClassificationAnswers() throws Exception {
        List<List<String>> perPipeline = new ArrayList<>();
        for (int j = 1; j <= 3; j++) {
            List<String> items = new ArrayList<>();
            for (int i = 1; i <= 5; i++) items.add(itemOf(art("k" + j + "-" + i, "Plain headline k" + j + " number " + i)));
            perPipeline.add(items);
        }
        List<Function<StubResponses.Request, StubResponses.Reply>> answers = List.of(
            req -> classified(req, "0.1", "0.9"),                      // bright
            req -> classified(req, "0.9", "0.1"),                      // dark
            req -> StubResponses.completed("not json at all"));        // malformed
        List<List<Map<String, Object>>> sourceRuns = new ArrayList<>();
        List<Object> pipelineFields = new ArrayList<>();
        for (Function<StubResponses.Request, StubResponses.Reply> answer : answers) {
            news.responder = StubNews.firstQueryItems(perPipeline);
            always(CLASSIFICATION, answer);
            Ran r = run(wildcardsBody(3));
            assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
            assertThat(counts(r.run()).get("sourcesKept")).isEqualTo(12);
            assertThat(counts(r.run()).get("articlesConsidered")).isEqualTo(15);
            List<Map<String, Object>> sources = new ArrayList<>();
            for (Map<String, Object> s : sourceItems(r.sid(), r.id())) {
                Map<String, Object> copy = new java.util.LinkedHashMap<>(s);
                copy.remove("retrievedAt");   // the only field that is a timestamp of the run itself
                sources.add(copy);
            }
            sourceRuns.add(sources);
            pipelineFields.add(PlanJson.pipelines(researchBody(r.sid(), r.id())).stream()
                .map(p -> List.of(p.get("id"), p.get("candidatesConsidered"), p.get("sourceIds"))).toList());
        }
        assertThat(sourceRuns.get(1)).as("dark = bright").isEqualTo(sourceRuns.get(0));
        assertThat(sourceRuns.get(2)).as("malformed = bright").isEqualTo(sourceRuns.get(0));
        assertThat(pipelineFields.get(1)).isEqualTo(pipelineFields.get(0));
        assertThat(pipelineFields.get(2)).isEqualTo(pipelineFields.get(0));
        assertThat(sourceRuns.get(0)).hasSize(12);
        assertThat(sourceRuns.get(0).stream().map(s -> s.get("id")).toList()).containsExactly(
            "S001", "S002", "S003", "S004", "S005", "S006", "S007", "S008", "S009", "S010", "S011", "S012");
    }
}
