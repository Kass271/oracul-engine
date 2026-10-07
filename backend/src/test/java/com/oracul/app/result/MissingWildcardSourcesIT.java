package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.research.StubNews;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * wildcard-evidence.md "Slice 09_wildcard-results" (FR-59 backend, test plan 1, 2, 4, 6, 7, 8 and the invariants; plan 3 is
 * MissingWildcardSourcesWindowIT, plan 5 InsufficientEvidenceIT): a wildcard without sources is named in the run's evidence note
 * (kind MISSING_WILDCARD_SOURCES, or the prefix of INSUFFICIENT_EVIDENCE) and never fails or changes the run. All E2E thresholds of
 * this context are 0. Body PE = realism 8, darkness 9, optimism 2, horizon 5y, New pandemic 8 then Energy crisis 3.
 */
// @trace FR-59
class MissingWildcardSourcesIT extends AbstractStoryIT {

    static final String PE = """
        {"realism":8,"darkness":9,"optimism":2,"horizon":"5y",
         "wildcards":[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"energy-energy-crisis","intensity":3}],
         "customWildcards":[],"output":{"story":true,"illustration":false}}""";
    /** PE plus the custom wildcard "AI takeover" 7 (W03 CUSTOM, after the catalogue pipelines). */
    static final String PEC = """
        {"realism":8,"darkness":9,"optimism":2,"horizon":"5y",
         "wildcards":[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"energy-energy-crisis","intensity":3}],
         "customWildcards":[{"label":"AI takeover","intensity":7}],"output":{"story":true,"illustration":false}}""";
    static final List<String> LABELS = List.of("New pandemic", "Energy crisis", "AI takeover");
    static final String NO_EVIDENCE_TEXT = "No current news could be used — this future is speculative, not grounded in evidence.";
    static final String SPECULATIVE_TASK_START = "The Evidence Pack is empty";
    static final String ENERGY_ONLY = "No current sources found for: Energy crisis. This part of the future is speculative.";

    // ---- helpers -------------------------------------------------------------------------------------------------

    private List<Art> articles(int n) {
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            arts.add(new Art("m-" + i, "reuters.com", "Missing test article " + i));
            news.site("m-" + i, "Publisher " + i);
        }
        return arts;
    }

    /** Fresh stub; the first query of pipeline j answers sizes[j-1] articles (sizes may hold zeros), every other query the empty feed. */
    private Ran runShape(String body, int... sizes) throws Exception {
        news.reset();
        int total = 0;
        for (int s : sizes) total += s;
        newsArticlesPerPipeline(articles(Math.max(total, 1)), sizes);
        return run(body);
    }

    private static Map<String, Object> note(String kind, String message, int core, int needed, String... wildcards) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("kind", kind);
        m.put("message", message);
        m.put("coreItems", core);
        m.put("coreNeeded", needed);
        if (wildcards.length > 0) m.put("wildcardsWithoutSources", List.of(wildcards));
        return m;
    }

    private String dbKind(String runId) {
        return jdbc.queryForObject("select evidence_note_kind from generation_run where id = cast(? as uuid)", String.class, runId);
    }

    /** The evidence_note_wildcards column as text (jsonb), or null. */
    private String dbWildcards(String runId) {
        return jdbc.queryForObject("select cast(evidence_note_wildcards as text) from generation_run where id = cast(? as uuid)", String.class, runId);
    }

    private static List<String> promptLines(Map<String, Object> pack) {
        return List.of(((String) pack.get("promptText")).split("\n"));
    }

    /** The invariants of FR-59 "Ranges & invariants", checked for every run of this class. */
    @SuppressWarnings("unchecked")
    private void assertNoteInvariants(Ran r, int realism) throws Exception {
        Map<String, Object> run = r.run();
        assertThat(run.get("status")).as("a lack of sources never fails a run: " + run).isEqualTo("COMPLETED");
        assertThat(run.get("headline")).isNotNull();
        Map<String, Object> pack = pack(r);
        List<String> ids = sectionIds(pack);
        List<String> missing = wildcardSections(pack).stream().filter(sec -> !"GENERAL".equals(sec.get("kind")) && items(sec).isEmpty())
            .map(sec -> (String) sec.get("label")).toList();
        Map<String, Object> note = (Map<String, Object>) run.get("evidenceNote");
        if (ids.isEmpty()) {
            assertThat(note).as("kind NO_EVIDENCE <=> pack empty").isNotNull();
            assertThat(note.get("kind")).isEqualTo("NO_EVIDENCE");
        } else if (missing.isEmpty()) {
            assertThat(note).as("evidence meets the realism, every wildcard has sources: no note").isNull();
        } else {
            assertThat(note).as("a wildcard without sources and a non-empty pack: " + run).isNotNull();
            assertThat(note.get("kind")).as("thresholds 0: MISSING_WILDCARD_SOURCES").isEqualTo("MISSING_WILDCARD_SOURCES");
        }
        boolean named = note != null && note.containsKey("wildcardsWithoutSources");
        assertThat(named).as("wildcardsWithoutSources present <=> pack non-empty and some CATALOGUE/CUSTOM section empty")
            .isEqualTo(!ids.isEmpty() && !missing.isEmpty());
        if (named) {
            assertThat(note.get("wildcardsWithoutSources")).as("exactly the labels of the empty sections in section order").isEqualTo(missing);
            assertThat((String) note.get("message")).startsWith("No current sources found for: ");
        } else if (note != null) {
            assertThat((String) note.get("message")).doesNotStartWith("No current sources found for: ");
        }
        if (note != null) assertThat((String) note.get("message")).doesNotContain("http");
        assertThat(run.get("suggestedRealism")).as("suggestedRealism present <=> INSUFFICIENT_EVIDENCE and realism > 1").isNull();
        // the column is NULL <=> wildcardsWithoutSources is absent, and holds exactly the labels otherwise
        String stored = dbWildcards(r.id());
        if (named) assertThat(json("{\"w\":" + stored + "}").get("w")).isEqualTo(missing);
        else assertThat(stored).isNull();
        // speculative mode <=> the pack is empty
        // the request log is shared by every run of a test: the run under test is the latest one
        List<?> genRequests = requests(GEN);
        String gen = requests(GEN).get(genRequests.size() - 1).inputText();
        if (ids.isEmpty()) assertThat(gen).contains(SPECULATIVE_TASK_START);
        else assertThat(gen).doesNotContain(SPECULATIVE_TASK_START);
    }

    /** FR-49 / FR-59 acceptance 4: only Google search, Google article page, decode and publisher requests; no former-provider path. */
    private void assertOnlyAllowedPaths(int plannedQueries) {
        long searches = news.paths.stream().filter("/rss/search"::equals).count();
        assertThat(searches).as("one /rss/search request per planned query").isEqualTo(plannedQueries);
        for (String p : news.paths) {
            assertThat(p).as("requested path").matches("^(/rss/search|/rss/articles/.*|/_/DotsSplashUi/data/batchexecute|/articles/.*)$");
            assertThat(p).doesNotStartWith("/api/v2/doc");
        }
    }

    // ---- test plan 1 -----------------------------------------------------------------------------------------------

    @Test
    void aWildcardWithoutSourcesIsNamedInAnOtherwiseNormalRun() throws Exception {
        Ran r = runShape(PE, 4);
        assertStoryCompleted(r.run());
        assertThat(requests(GEN)).hasSize(1);
        assertThat(requests(GEN).get(0).inputText()).as("normal mode: no speculative TASK line").doesNotContain(SPECULATIVE_TASK_START);
        assertThat(r.run().get("evidenceNote")).isEqualTo(note("MISSING_WILDCARD_SOURCES", ENERGY_ONLY, 4, 0, "Energy crisis"));
        assertThat(r.run().get("suggestedRealism")).isNull();
        assertThat(dbKind(r.id())).isEqualTo("MISSING_WILDCARD_SOURCES");
        assertThat(json("{\"w\":" + dbWildcards(r.id()) + "}").get("w")).isEqualTo(List.of("Energy crisis"));
        Map<String, Object> research = researchBody(r.sid(), r.id());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> pipelines = (List<Map<String, Object>>) ((Map<String, Object>) research.get("searchPlan")).get("pipelines");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> w2Queries = (List<Map<String, Object>>) pipelines.get(1).get("queries");
        assertThat(w2Queries.stream().map(q -> q.get("id")).toList()).containsExactly("Q04", "Q05", "Q06");
        assertThat(w2Queries.stream().map(q -> q.get("status")).toList()).containsOnly("EMPTY");
        List<String> lines = promptLines(pack(r));
        int heading = lines.indexOf("Wildcard: Energy crisis 3/10");
        assertThat(heading).as("W02 heading in the pack text").isGreaterThanOrEqualTo(0);
        assertThat(lines.get(heading + 1)).isEqualTo("no current sources found");
        assertNoteInvariants(r, 8);
        assertOnlyAllowedPaths(6);
    }

    // ---- test plan 2 -----------------------------------------------------------------------------------------------

    @Test
    void anEmptyFeedIsNoEvidenceWithoutTheNamedWildcardsAndEveryGroupIsEmpty() throws Exception {
        news.reset();
        Ran r = run(PE);
        assertStoryCompleted(r.run());
        assertThat(r.run().get("evidenceNote")).isEqualTo(note("NO_EVIDENCE", NO_EVIDENCE_TEXT, 0, 0));
        assertThat(dbKind(r.id())).isEqualTo("NO_EVIDENCE");
        assertThat(dbWildcards(r.id())).as("evidence_note_wildcards stays NULL").isNull();
        Map<String, Object> res = result(r);
        assertThat(list(res.get("sources"))).isEmpty();
        assertThat(res).containsKey("wildcardGroups");
        List<Map<String, Object>> groups = list(res.get("wildcardGroups"));
        assertThat(groups).hasSize(2);
        for (Map<String, Object> g : groups) assertThat(g.get("sources")).isEqualTo(List.of());
        assertNoteInvariants(r, 8);
        assertOnlyAllowedPaths(6);
    }

    // ---- test plan 4 (every /rss/search answering 503) ---------------------------------------------------------------

    @Test
    void whenEveryGoogleSearchAnswers503OnlySearchRequestsAreSentAndTheRunIsNoEvidence() throws Exception {
        news.reset();
        news.responder = req -> StubNews.status(503);
        Ran r = run(PE);
        assertStoryCompleted(r.run());
        assertThat(r.run().get("evidenceNote")).isEqualTo(note("NO_EVIDENCE", NO_EVIDENCE_TEXT, 0, 0));
        assertThat(news.paths).as("a 503 is not retried and nothing else is asked").containsOnly("/rss/search").hasSize(6);
        assertNoteInvariants(r, 8);
    }

    // ---- test plan 6 -----------------------------------------------------------------------------------------------

    @Test
    void twoWildcardsWithoutSourcesAreNamedInPipelineOrderCustomLast() throws Exception {
        Ran r = runShape(PEC, 4);
        assertStoryCompleted(r.run());
        assertThat(r.run().get("evidenceNote")).isEqualTo(note("MISSING_WILDCARD_SOURCES",
            "No current sources found for: Energy crisis, AI takeover. This part of the future is speculative.", 4, 0, "Energy crisis", "AI takeover"));
        assertThat(dbKind(r.id())).isEqualTo("MISSING_WILDCARD_SOURCES");
        assertThat(json("{\"w\":" + dbWildcards(r.id()) + "}").get("w")).isEqualTo(List.of("Energy crisis", "AI takeover"));
        assertNoteInvariants(r, 8);
    }

    // ---- test plan 7 -----------------------------------------------------------------------------------------------

    @Test
    void aGeneralRunIsNeverNamedAsAMissingWildcard() throws Exception {
        news.reset();
        Ran empty = run(B);
        assertStoryCompleted(empty.run());
        assertThat(empty.run().get("evidenceNote")).isEqualTo(note("NO_EVIDENCE", NO_EVIDENCE_TEXT, 0, 0));
        assertThat(dbWildcards(empty.id())).isNull();
        assertNoteInvariants(empty, 8);

        news.reset();
        newsArticlesPerPipeline(articles(1), 1);
        Ran one = run(B);
        assertStoryCompleted(one.run());
        assertThat(one.run().get("evidenceNote")).as("one article, thresholds 0: no note").isNull();
        assertThat(dbWildcards(one.id())).isNull();
        assertNoteInvariants(one, 8);
    }

    // ---- test plan 8 -----------------------------------------------------------------------------------------------

    @Test
    void anAlternativeRunCopiesTheNoteIncludingTheNamedWildcards() throws Exception {
        Ran p = runShape(PE, 4);
        assertStoryCompleted(p.run());
        assertThat(p.run().get("evidenceNote")).isEqualTo(note("MISSING_WILDCARD_SOURCES", ENERGY_ONLY, 4, 0, "Energy crisis"));
        MvcResult res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(202);
        Map<String, Object> created = json(res.getResponse().getContentAsString());
        assertThat(created.get("evidenceNote")).as("copied at creation").isEqualTo(p.run().get("evidenceNote"));
        String altId = (String) created.get("id");
        assertThat(dbWildcards(altId)).as("column copied at creation").isNotNull();
        Map<String, Object> done = awaitRun(p.sid(), altId, 15_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
        assertThat(done.get("status")).as("alternative: " + done).isEqualTo("COMPLETED");
        assertThat(done.get("evidenceNote")).isEqualTo(p.run().get("evidenceNote"));
        assertThat(json("{\"w\":" + dbWildcards(altId) + "}").get("w")).isEqualTo(json("{\"w\":" + dbWildcards(p.id()) + "}").get("w"));
        assertThat(dbKind(altId)).isEqualTo("MISSING_WILDCARD_SOURCES");
    }

    // ---- ranges & invariants: every shape of the group sizes ------------------------------------------------------

    /** Group sizes of W01 / W02 / W03 (New pandemic, Energy crisis, custom AI takeover): every empty / non-empty combination. */
    static Stream<Arguments> shapes() {
        return Stream.of(
            Arguments.of(List.of(4, 0, 0)), Arguments.of(List.of(0, 4, 0)), Arguments.of(List.of(0, 0, 4)), Arguments.of(List.of(4, 4, 0)),
            Arguments.of(List.of(4, 0, 4)), Arguments.of(List.of(0, 4, 4)), Arguments.of(List.of(0, 0, 0)), Arguments.of(List.of(4, 4, 4)),
            Arguments.of(List.of(1, 1, 1)));
    }

    @ParameterizedTest(name = "group sizes {0}")
    @MethodSource("shapes")
    void theNoteNamesExactlyTheEmptyGroupsForEveryShape(List<Integer> sizes) throws Exception {
        Ran r = runShape(PEC, sizes.stream().mapToInt(Integer::intValue).toArray());
        assertNoteInvariants(r, 8);
        boolean anySource = sizes.stream().anyMatch(s -> s > 0);
        List<String> expectedMissing = new ArrayList<>();
        for (int i = 0; i < 3; i++) if (sizes.get(i) == 0) expectedMissing.add(LABELS.get(i));
        @SuppressWarnings("unchecked")
        Map<String, Object> note = (Map<String, Object>) r.run().get("evidenceNote");
        if (!anySource) {
            assertThat(note.get("kind")).isEqualTo("NO_EVIDENCE");
        } else if (expectedMissing.isEmpty()) {
            assertThat(note).isNull();
        } else {
            assertThat(note.get("wildcardsWithoutSources")).isEqualTo(expectedMissing);
            assertThat(note.get("message")).isEqualTo("No current sources found for: " + String.join(", ", expectedMissing)
                + ". This part of the future is speculative.");
        }
    }
}
