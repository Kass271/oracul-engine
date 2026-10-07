package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.StubNews;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * wildcard-result-views.md "Slice 09_wildcard-results" (FR-60 backend, test plan 1-7): {@code getFutureResult.wildcardGroups} - one
 * group per pipeline in plan order with the pipeline's queries and the items of its pack section plus {@code usedInScenario}; absent
 * for a legacy pack (FutureResultIT); no {@code null} anywhere inside. Walks four data sets (V4 under body A, V4 under the GENERAL
 * body B, seven sources with one shared, an empty feed) for the invariants of the spec. Talks HTTP only.
 */
// @trace FR-60
class WildcardResultIT extends AbstractStoryIT {

    // ---- helpers -------------------------------------------------------------------------------------------------

    private List<Art> articles(int n) {
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            arts.add(new Art("g-" + i, "reuters.com", "Group article " + i));
            news.site("g-" + i, "Publisher " + i);
        }
        return arts;
    }

    private Ran runSevenWithOneShared() throws Exception {
        news.reset();
        List<Art> arts = articles(7);
        // W01 = articles 1-4, W02 = the shared article 1 and the articles 5-7
        news.responder = StubNews.firstQueryItems(List.of(
            List.of(itemOf(arts.get(0)), itemOf(arts.get(1)), itemOf(arts.get(2)), itemOf(arts.get(3))),
            List.of(itemOf(arts.get(0)), itemOf(arts.get(4)), itemOf(arts.get(5)), itemOf(arts.get(6)))));
        return run(A);
    }

    private Ran runDataset(String name) throws Exception {
        return switch (name) {
            case "V4 under body A" -> runV4(A);
            case "V4 under body B" -> runV4(B);
            case "seven sources, one shared" -> runSevenWithOneShared();
            case "empty feed under body A" -> {
                news.reset();
                yield run(A);
            }
            default -> throw new IllegalArgumentException(name);
        };
    }

    static Stream<String> datasets() {
        return Stream.of("V4 under body A", "V4 under body B", "seven sources, one shared", "empty feed under body A");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> groups(Map<String, Object> result) {
        assertThat(result).as("result keys").containsKey("wildcardGroups");
        return (List<Map<String, Object>>) result.get("wildcardGroups");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sourcesOf(Map<String, Object> group) {
        return (List<Map<String, Object>>) group.get("sources");
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> pipelines(Map<String, Object> research) {
        return (List<Map<String, Object>>) ((Map<String, Object>) research.get("searchPlan")).get("pipelines");
    }

    /** Evidence IDs cited by a fact of the accepted scenario of the run. */
    private Set<String> cited(Ran r) throws Exception {
        Set<String> cited = new LinkedHashSet<>();
        for (Map<String, Object> f : list(map(structured(r).get("structuredScenario")).get("factsUsed"))) {
            for (Object e : (List<?>) f.get("evidenceIds")) cited.add((String) e);
        }
        return cited;
    }

    private static void collectNulls(Object node, String path, List<String> hits) {
        if (node == null) {
            hits.add(path);
        } else if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) collectNulls(e.getValue(), path + "." + e.getKey(), hits);
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) collectNulls(l.get(i), path + "[" + i + "]", hits);
        }
    }

    /** The expected {@code ResultGroupSource} of a pack section item: the item without {@code snippet} plus {@code usedInScenario}. */
    private static Map<String, Object> expectedSource(Map<String, Object> packItem, Set<String> cited) {
        Map<String, Object> m = new LinkedHashMap<>(packItem);
        m.remove("snippet");
        m.put("usedInScenario", cited.contains((String) packItem.get("evidenceId")));
        return m;
    }

    /** Backend invariants of the spec ("Ranges & invariants"): checked for every completed run of this class. */
    private void assertGroupInvariants(Ran r) throws Exception {
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        String raw = resultRaw(r);
        Map<String, Object> result = json(raw);
        Map<String, Object> research = researchBody(r.sid(), r.id());
        Map<String, Object> pack = pack(r);
        Set<String> cited = cited(r);
        List<Map<String, Object>> groups = groups(result);
        List<Map<String, Object>> pipelines = pipelines(research);
        List<Map<String, Object>> sections = wildcardSections(pack);

        // groups = pipelines: ids, order, headings, kinds, levels, labels; queries copied in order
        assertThat(groups.stream().map(g -> g.get("pipelineId")).toList()).isEqualTo(pipelines.stream().map(p -> p.get("id")).toList());
        assertThat(groups).hasSameSizeAs(sections);
        for (int i = 0; i < groups.size(); i++) {
            Map<String, Object> g = groups.get(i);
            Map<String, Object> p = pipelines.get(i);
            assertThat(g.get("heading")).as("heading " + i).isEqualTo(p.get("heading"));
            assertThat(g.get("kind")).isEqualTo(p.get("kind"));
            assertThat(g.get("label")).isEqualTo(p.get("label"));
            if ("GENERAL".equals(p.get("kind"))) assertThat(g).as("GENERAL has no level").doesNotContainKey("level");
            else assertThat(g.get("level")).isEqualTo(p.get("level"));
            assertThat(g.get("queries")).as("queries of " + g.get("pipelineId")).isEqualTo(p.get("queries"));
            // sources = that pipeline's pack section items in order (without snippet, plus usedInScenario)
            Map<String, Object> section = sections.get(i);
            assertThat(section.get("pipelineId")).isEqualTo(g.get("pipelineId"));
            assertThat(sourcesOf(g)).as("sources of " + g.get("pipelineId"))
                .isEqualTo(items(section).stream().map(it -> expectedSource(it, cited)).toList());
            for (Map<String, Object> s : sourcesOf(g)) {
                List<?> fragments = (List<?>) s.get("fragments");
                assertThat(fragments).as("fragments present").isNotNull();
                assertThat(Boolean.TRUE.equals(s.get("contentRetrieved"))).as("contentRetrieved <=> fragments non-empty (" + s.get("evidenceId") + ")")
                    .isEqualTo(!fragments.isEmpty());
                assertThat(fragments.size()).isLessThanOrEqualTo(3);
                assertThat(s).doesNotContainKey("snippet");
            }
        }

        // union of the group evidence ids = the flat sources ids = the pack Evidence IDs
        List<String> groupIds = groups.stream().flatMap(g -> sourcesOf(g).stream()).map(s -> (String) s.get("evidenceId")).distinct().sorted().toList();
        List<String> flat = list(result.get("sources")).stream().map(s -> (String) s.get("evidenceId")).toList();
        assertThat(groupIds).isEqualTo(flat.stream().sorted().toList());
        assertThat(groupIds).isEqualTo(sectionIds(pack).stream().sorted().toList());
        // counts: distinct group evidence ids = sourcesKept = eventsSelected; distinct used ids = sourcesUsed
        Map<String, Object> counts = counts(r.run());
        assertThat(groupIds).hasSize(((Number) counts.get("sourcesKept")).intValue());
        assertThat(groupIds).hasSize(((Number) counts.get("eventsSelected")).intValue());
        long used = groups.stream().flatMap(g -> sourcesOf(g).stream()).filter(s -> Boolean.TRUE.equals(s.get("usedInScenario")))
            .map(s -> (String) s.get("evidenceId")).distinct().count();
        assertThat(used).isEqualTo(((Number) counts.get("sourcesUsed")).longValue());
        // research.counts and the flat sources keep their meaning
        assertThat(map(result.get("research")).get("counts")).isEqualTo(counts);
        for (Map<String, Object> s : list(result.get("sources"))) {
            assertThat(s.get("section")).isEqualTo("CORE");
            assertThat(s.get("counterSignal")).isEqualTo(false);
        }
        // no null anywhere inside wildcardGroups
        List<String> nulls = new ArrayList<>();
        collectNulls(JsonPath.read(raw, "$.wildcardGroups"), "$.wildcardGroups", nulls);
        assertThat(nulls).as("null values inside wildcardGroups").isEmpty();
    }

    // ---- test plan 1 -----------------------------------------------------------------------------------------------

    @Test
    void v4UnderBodyAHasTwoGroupsTheSecondOneWithoutSources() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        Map<String, Object> result = result(r);
        List<Map<String, Object>> groups = groups(result);
        assertThat(groups).hasSize(2);

        Map<String, Object> w1 = groups.get(0);
        assertThat(w1.get("pipelineId")).isEqualTo("W01");
        assertThat(w1.get("kind")).isEqualTo("CATALOGUE");
        assertThat(w1.get("label")).isEqualTo("New pandemic");
        assertThat(w1.get("level")).isEqualTo(8);
        assertThat(w1.get("heading")).isEqualTo("New pandemic 8/10");
        assertThat(w1.get("queries")).as("queries equal GET /research pipelines[0].queries")
            .isEqualTo(pipelines(researchBody(r.sid(), r.id())).get(0).get("queries"));
        assertThat(((List<?>) w1.get("queries"))).hasSize(3);

        Set<String> cited = cited(r);
        List<Map<String, Object>> packItems = items(wildcardSections(pack(r)).get(0));
        assertThat(packItems).hasSize(4);
        assertThat(sourcesOf(w1)).isEqualTo(packItems.stream().map(it -> expectedSource(it, cited)).toList());
        assertThat(sourcesOf(w1).stream().map(s -> s.get("usedInScenario")).toList()).as("the default scenario cites E001")
            .contains(true);

        Map<String, Object> w2 = groups.get(1);
        assertThat(w2.get("pipelineId")).isEqualTo("W02");
        assertThat(w2.get("heading")).isEqualTo("Humanoid robot boom 6/10");
        assertThat(w2.get("level")).isEqualTo(6);
        assertThat(sourcesOf(w2)).isEmpty();
        assertThat((List<?>) w2.get("queries")).hasSize(3);
    }

    // ---- test plan 2 -----------------------------------------------------------------------------------------------

    @Test
    void aGeneralRunHasOneGroupWithoutALevelAndAnUndatedItemHasNoPublishedAt() throws Exception {
        news.reset();
        String undated = StubNews.rssItem("Undated report on harbour automation", news.baseUrl() + "/rss/articles/undated", null,
            "reuters.com", "https://reuters.com");
        news.responder = StubNews.firstQueryItems(List.of(List.of(undated)));
        Ran r = run(B);
        assertStoryCompleted(r.run());
        List<Map<String, Object>> groups = groups(result(r));
        assertThat(groups).hasSize(1);
        Map<String, Object> g = groups.get(0);
        assertThat(g).doesNotContainKey("level");
        assertThat(g.get("kind")).isEqualTo("GENERAL");
        assertThat(g.get("pipelineId")).isEqualTo("W01");
        assertThat(g.get("heading")).isEqualTo("General");
        assertThat(sourcesOf(g)).hasSize(1);
        Map<String, Object> s = sourcesOf(g).get(0);
        assertThat(s).doesNotContainKey("publishedAt").doesNotContainKey("snippet");
        assertThat(s.get("evidenceId")).isEqualTo("E001");
        assertThat(s.get("title")).isEqualTo("Undated report on harbour automation");
        assertThat(s.get("sourceId")).isEqualTo("S001");
        assertThat(s.get("usedInScenario")).isEqualTo(cited(r).contains("E001"));
        assertGroupInvariants(r);
    }

    // ---- test plan 3 -----------------------------------------------------------------------------------------------

    @Test
    void aSharedSourceIsListedInBothGroupsWithTheSameSourceId() throws Exception {
        Ran r = runSevenWithOneShared();
        assertStoryCompleted(r.run());
        List<Map<String, Object>> groups = groups(result(r));
        assertThat(groups).hasSize(2);
        List<String> first = sourcesOf(groups.get(0)).stream().map(s -> (String) s.get("evidenceId")).toList();
        List<String> second = sourcesOf(groups.get(1)).stream().map(s -> (String) s.get("evidenceId")).toList();
        assertThat(first).containsExactly("E001", "E002", "E003", "E004");
        assertThat(second).contains("E001").hasSize(4);
        Map<String, Object> inFirst = sourcesOf(groups.get(0)).get(0);
        Map<String, Object> inSecond = sourcesOf(groups.get(1)).stream().filter(s -> "E001".equals(s.get("evidenceId"))).findFirst().orElseThrow();
        assertThat(inSecond.get("sourceId")).isEqualTo(inFirst.get("sourceId")).isEqualTo("S001");
        assertThat(inSecond.get("usedInScenario")).as("the same evidence id is cited or not in both groups").isEqualTo(inFirst.get("usedInScenario"));
        // each group carries its own section's fragments for the shared item
        List<Map<String, Object>> sections = wildcardSections(pack(r));
        for (int i = 0; i < 2; i++) {
            Map<String, Object> packItem = items(sections.get(i)).stream().filter(it -> "E001".equals(it.get("evidenceId"))).findFirst().orElseThrow();
            Map<String, Object> groupItem = sourcesOf(groups.get(i)).stream().filter(s -> "E001".equals(s.get("evidenceId"))).findFirst().orElseThrow();
            assertThat(groupItem.get("fragments")).as("group " + i).isEqualTo(packItem.get("fragments"));
        }
        assertGroupInvariants(r);
    }

    // ---- test plan 4 -----------------------------------------------------------------------------------------------

    @Test
    void anEmptyFeedKeepsEveryGroupWithNoSources() throws Exception {
        news.reset();
        Ran r = run(A);
        assertStoryCompleted(r.run());
        List<Map<String, Object>> groups = groups(result(r));
        assertThat(groups).hasSize(2);
        for (Map<String, Object> g : groups) {
            assertThat(g.get("sources")).as("sources of " + g.get("pipelineId")).isEqualTo(List.of());
            assertThat((List<?>) g.get("queries")).hasSize(3);
        }
        assertThat(list(result(r).get("sources"))).isEmpty();
        assertGroupInvariants(r);
    }

    // ---- test plan 6 -----------------------------------------------------------------------------------------------

    @Test
    void anAlternativeRunShowsTheParentsGroupsWithItsOwnUsedInScenario() throws Exception {
        Ran p = runV4(A);
        assertStoryCompleted(p.run());
        MvcResult res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(202);
        String altId = (String) json(res.getResponse().getContentAsString()).get("id");
        Map<String, Object> done = awaitRun(p.sid(), altId, 15_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
        assertThat(done.get("status")).as("alternative: " + done).isEqualTo("COMPLETED");
        Ran alt = new Ran(p.sid(), altId, done);

        List<Map<String, Object>> parentGroups = groups(result(p));
        List<Map<String, Object>> altGroups = groups(result(alt));
        assertThat(altGroups).hasSameSizeAs(parentGroups);
        Set<String> altCited = cited(alt);
        for (int i = 0; i < parentGroups.size(); i++) {
            Map<String, Object> pg = parentGroups.get(i);
            Map<String, Object> ag = altGroups.get(i);
            for (String key : List.of("pipelineId", "kind", "label", "heading", "queries")) assertThat(ag.get(key)).as(key).isEqualTo(pg.get(key));
            assertThat(ag.get("level")).isEqualTo(pg.get("level"));
            List<Map<String, Object>> expected = new ArrayList<>();
            for (Map<String, Object> s : sourcesOf(pg)) {
                Map<String, Object> e = new LinkedHashMap<>(s);
                e.put("usedInScenario", altCited.contains((String) s.get("evidenceId")));
                expected.add(e);
            }
            assertThat(sourcesOf(ag)).as("sources of " + ag.get("pipelineId")).isEqualTo(expected);
        }
        assertGroupInvariants(alt);
    }

    // ---- test plan 7 + invariants ----------------------------------------------------------------------------------

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasets")
    void theInvariantsOfTheGroupsHoldForEveryDataset(String dataset) throws Exception {
        Ran r = runDataset(dataset);
        assertGroupInvariants(r);
    }
}
