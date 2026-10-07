package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.research.StubNews;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Wire format of api/openapi.yaml 0.7.0: an optional field that is unset is absent from the JSON, never null and never [].
 * Walks the raw body of every endpoint that carries one of the phase-03 optional fields. Slice 04 (FR-50 change line):
 * {@code SearchPlan.pipelines} and {@code Source.pipelineIds} are present on the wire for a new run, so they left the walked
 * set and are asserted present instead; slice 05 (FR-53) does the same for {@code ResearchCounts.sourcesKept},
 * {@code WildcardPipeline.candidatesConsidered} and {@code WildcardPipeline.sourceIds}; slice 06 (FR-57) does the same for
 * {@code EvidencePack.wildcardSections} and checks that no {@code null} value appears inside it; slice 08 (FR-54 / FR-55,
 * article-retrieval.md "Changes earlier behaviour") does the same for {@code ResearchCounts.sourcesWithContent},
 * {@code Source.contentStatus}, {@code Source.excerpts} and {@code Source.publisherHost}; slice 09 (FR-59 / FR-60, wildcard-evidence.md
 * and wildcard-result-views.md "Changes earlier behaviour") does the same for {@code EvidenceNote.wildcardsWithoutSources} (V4 under body
 * A has the note naming {@code Humanoid robot boom}) and {@code FutureResult.wildcardGroups} (2 groups, no {@code null} inside), so the
 * walked OPTIONAL set is empty now and both fields are asserted present.
 */
// @trace FR-49, FR-50, FR-53, FR-54, FR-55, FR-57, FR-59, FR-60
class OptionalWireFieldsAbsentIT extends AbstractStoryIT {

    /** Optional 0.7.0 field name -> the schema property it belongs to. Empty since slice 09: every phase-03 optional field is present on a new run. */
    private static final Map<String, String> OPTIONAL = Map.of();

    private String get(String sid, String path) throws Exception {
        var b = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get(path)
            .cookie(new jakarta.servlet.http.Cookie("ORACUL_SID", sid));
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).as("GET " + path + ": " + r.getResponse().getContentAsString()).isEqualTo(200);
        return r.getResponse().getContentAsString();
    }

    /** Every JSON path at which a key of {@link #OPTIONAL} occurs (null / [] / any value). */
    private static void collect(Object node, String path, List<String> hits) {
        if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) {
                String p = path + "." + e.getKey();
                if (OPTIONAL.containsKey(String.valueOf(e.getKey()))) hits.add(p + " = " + e.getValue());
                collect(e.getValue(), p, hits);
            }
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) collect(l.get(i), path + "[" + i + "]", hits);
        }
    }

    private static void assertNoOptionalKeys(String endpoint, String raw) {
        List<String> hits = new ArrayList<>();
        collect(JsonPath.read(raw, "$"), "$", hits);
        assertThat(hits).as(endpoint + " must not carry an unset optional 0.7.0 field (absent, not null / []): " + OPTIONAL.values())
            .isEmpty();
    }

    @Test
    @Timeout(60)
    void aCompletedRunWithoutPhase03DataCarriesNoOptionalFieldOnTheWire() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String id = r.id();
        String sid = r.sid();

        String run = get(sid, "/api/runs/" + id);
        String research = get(sid, "/api/runs/" + id + "/research");
        String sources = get(sid, "/api/runs/" + id + "/sources");
        String pack = packRaw(sid, id);
        String result = resultRaw(r);

        // the bodies really carry the objects the optional fields belong to
        assertThat(JsonPath.<Object>read(run, "$.counts")).isNotNull();
        assertThat(JsonPath.<Object>read(research, "$.counts")).isNotNull();
        assertThat(JsonPath.<Object>read(research, "$.searchPlan")).isNotNull();
        assertThat(JsonPath.<List<Object>>read(sources, "$.items")).as("sources").isNotEmpty();
        assertThat(JsonPath.<Object>read(result, "$.research.counts")).isNotNull();
        assertThat(JsonPath.<List<Object>>read(result, "$.sources")).as("result sources").isNotEmpty();
        assertThat(JsonPath.<List<Object>>read(pack, "$.sources")).as("pack sources").isNotEmpty();

        // FR-50: a new run carries its pipelines and every source its pipelineIds (never absent, never empty)
        assertThat(JsonPath.<List<Object>>read(research, "$.searchPlan.pipelines")).as("pipelines of a new run").hasSize(2);
        // FR-53: the READING_SOURCES commit writes sourcesKept and, for every pipeline, candidatesConsidered and sourceIds (possibly [])
        assertThat(JsonPath.<List<Object>>read(research, "$.searchPlan.pipelines[*].candidatesConsidered"))
            .as("present on every pipeline of a new run").hasSize(2);
        assertThat(JsonPath.<List<Object>>read(research, "$.searchPlan.pipelines[*].sourceIds")).as("present on every pipeline").hasSize(2);
        assertThat(JsonPath.<Integer>read(research, "$.counts.sourcesKept")).as("research counts").isEqualTo(4);
        assertThat(JsonPath.<Integer>read(run, "$.counts.sourcesKept")).as("run counts").isEqualTo(4);
        assertThat(JsonPath.<Integer>read(result, "$.research.counts.sourcesKept")).as("result counts").isEqualTo(4);
        List<Object> items = JsonPath.read(sources, "$.items");
        List<Object> pipelineIds = JsonPath.read(sources, "$.items[*].pipelineIds");
        assertThat(pipelineIds).as("every source of a new run carries pipelineIds").hasSize(items.size());
        assertThat(pipelineIds).allSatisfy(ids -> assertThat((List<?>) ids).isNotEmpty());
        // FR-54 / FR-55: every source of a new run carries contentStatus, excerpts and (publisher page known) publisherHost; the V4 pages
        // are served by the stub's default publisher page, so all four are RETRIEVED with the single fallback fragment P1
        assertThat(JsonPath.<List<Object>>read(sources, "$.items[*].contentStatus")).as("contentStatus on every source").hasSize(items.size())
            .containsOnly("RETRIEVED");
        assertThat(JsonPath.<List<Object>>read(sources, "$.items[*].excerpts")).as("excerpts on every source").hasSize(items.size());
        assertThat(JsonPath.<List<Object>>read(sources, "$.items[*].publisherHost")).as("publisherHost on every source")
            .hasSize(items.size()).containsOnly("127.0.0.1");
        assertThat(JsonPath.<List<String>>read(sources, "$.items[*].excerpts[0].fragments[0]")).hasSize(items.size())
            .containsOnly(P1);
        assertThat(JsonPath.<Integer>read(research, "$.counts.sourcesWithContent")).as("research counts").isEqualTo(4);
        assertThat(JsonPath.<Integer>read(run, "$.counts.sourcesWithContent")).as("run counts").isEqualTo(4);
        assertThat(JsonPath.<Integer>read(result, "$.research.counts.sourcesWithContent")).as("result counts").isEqualTo(4);

        assertNoOptionalKeys("GET /api/runs/{id}", run);
        assertNoOptionalKeys("GET /api/runs/{id}/research", research);
        assertNoOptionalKeys("GET /api/runs/{id}/sources", sources);
        assertNoOptionalKeys("GET /api/runs/{id}/evidence-pack", pack);
        assertNoOptionalKeys("GET /api/runs/{id}/result", result);

        // FR-59: V4 under body A (W01 S001-S004, W02 empty) has the MISSING_WILDCARD_SOURCES note naming exactly the empty wildcard
        assertThat(JsonPath.<Map<String, Object>>read(run, "$.evidenceNote")).as("evidenceNote of V4 under body A")
            .containsEntry("kind", "MISSING_WILDCARD_SOURCES")
            .containsEntry("wildcardsWithoutSources", List.of("Humanoid robot boom"));
        assertThat(JsonPath.<List<Object>>read(run, "$.evidenceNote.wildcardsWithoutSources")).containsExactly("Humanoid robot boom");

        // FR-60: the result carries the groups (one per pipeline, no null anywhere inside, no level only for GENERAL)
        assertThat(JsonPath.<List<Object>>read(result, "$.wildcardGroups")).as("wildcardGroups of a new run").hasSize(2);
        assertThat(JsonPath.<List<Object>>read(result, "$.wildcardGroups[*].sources[*]")).as("group sources").hasSize(4);
        assertThat(JsonPath.<List<Object>>read(result, "$.wildcardGroups[*].level")).as("level of both catalogue groups").containsExactly(8, 6);
        List<String> groupNulls = new ArrayList<>();
        collectNulls(JsonPath.read(result, "$.wildcardGroups"), "$.wildcardGroups", groupNulls);
        assertThat(groupNulls).as("null values inside wildcardGroups").isEmpty();

        // FR-57: a new pack carries wildcardSections (one per pipeline) and no null value anywhere inside them
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections")).as("wildcardSections of a new pack").hasSize(2);
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections[*].items[*]")).as("section items").hasSize(4);
        List<String> nulls = new ArrayList<>();
        collectNulls(JsonPath.read(pack, "$.wildcardSections"), "$.wildcardSections", nulls);
        assertThat(nulls).as("null values inside wildcardSections").isEmpty();
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections[*].items[*].fragments")).as("fragments always present").hasSize(4);
        // FR-55: the fragments of the retrieved pages are the pack content now, no snippet
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections[*].items[*].contentRetrieved")).containsOnly(true);
        assertThat(JsonPath.<List<String>>read(pack, "$.wildcardSections[*].items[*].fragments[0]")).hasSize(4).containsOnly(P1);
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections[*].items[*].snippet")).as("no snippet for retrieved items").isEmpty();
    }

    /** P1 of the stub's publisher page (FR-61): the fragment of a page without match text (first paragraph of at least 80 characters). */
    private static final String P1 = "Opening paragraph of this publisher page. It introduces the report in plain words for every reader.";

    private static void collectNulls(Object node, String path, List<String> hits) {
        if (node == null) {
            hits.add(path);
        } else if (node instanceof Map<?, ?> m) {
            for (Map.Entry<?, ?> e : m.entrySet()) collectNulls(e.getValue(), path + "." + e.getKey(), hits);
        } else if (node instanceof List<?> l) {
            for (int i = 0; i < l.size(); i++) collectNulls(l.get(i), path + "[" + i + "]", hits);
        }
    }

    /**
     * A GENERAL run (body B) whose only item has no pubDate and whose publisher page is served as metadata only (NO_TEXT, article-retrieval.md
     * slice 08 "Changes earlier behaviour"): the section has no level, the item no publishedAt, a snippet instead of fragments.
     */
    @Test
    @Timeout(60)
    void aGeneralSectionHasNoLevelAndAnItemWithoutADateHasNoPublishedAt() throws Exception {
        news.reset();
        news.pages.put("undated", new StubNews.Page(200, "text/html; charset=utf-8", StubNews.html("undated")));
        String undated = StubNews.rssItem("Undated report on harbour automation", news.baseUrl() + "/rss/articles/undated", null,
            "reuters.com", "https://reuters.com");
        news.responder = StubNews.firstQueryItems(List.of(List.of(undated)));
        Ran r = run(B);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        String pack = packRaw(r.sid(), r.id());
        assertThat(JsonPath.<List<Object>>read(pack, "$.wildcardSections")).hasSize(1);
        Map<String, Object> section = JsonPath.read(pack, "$.wildcardSections[0]");
        assertThat(section).as("GENERAL section").doesNotContainKey("level");
        assertThat(section).containsEntry("kind", "GENERAL").containsEntry("pipelineId", "W01");
        Map<String, Object> item = JsonPath.read(pack, "$.wildcardSections[0].items[0]");
        assertThat(item).as("undated item").doesNotContainKey("publishedAt");
        assertThat(item).containsEntry("evidenceId", "E001").containsEntry("contentRetrieved", false).containsKey("snippet");
        assertThat(item.get("snippet")).isInstanceOf(String.class);
        assertThat((List<?>) item.get("fragments")).isEmpty();
        List<String> nulls = new ArrayList<>();
        collectNulls(JsonPath.read(pack, "$.wildcardSections"), "$.wildcardSections", nulls);
        assertThat(nulls).as("null values inside wildcardSections").isEmpty();
        assertThat(lines(JsonPath.read(pack, "$.promptText"))).anyMatch(l -> l.startsWith("[E001] Undated report on harbour automation · ")
            && l.contains(" · unknown · "));
        // FR-60: the same shape in the result groups - no level for GENERAL, no publishedAt for the undated item, no null anywhere
        String result = resultRaw(r);
        Map<String, Object> group = JsonPath.read(result, "$.wildcardGroups[0]");
        assertThat(JsonPath.<List<Object>>read(result, "$.wildcardGroups")).hasSize(1);
        assertThat(group).doesNotContainKey("level").containsEntry("kind", "GENERAL").containsEntry("pipelineId", "W01")
            .containsEntry("heading", "General");
        Map<String, Object> groupItem = JsonPath.read(result, "$.wildcardGroups[0].sources[0]");
        assertThat(groupItem).doesNotContainKey("publishedAt").doesNotContainKey("snippet").containsEntry("evidenceId", "E001")
            .containsEntry("contentRetrieved", false);
        assertThat((List<?>) groupItem.get("fragments")).isEmpty();
        List<String> groupNulls = new ArrayList<>();
        collectNulls(JsonPath.read(result, "$.wildcardGroups"), "$.wildcardGroups", groupNulls);
        assertThat(groupNulls).as("null values inside wildcardGroups").isEmpty();
    }

    /** FR-55: the same undated GENERAL item on the stub's default publisher page is RETRIEVED: fragment P1 and no snippet. */
    @Test
    @Timeout(60)
    void aGeneralItemWithAReadablePageIsRetrievedWithTheFallbackFragment() throws Exception {
        news.reset();
        String undated = StubNews.rssItem("Undated report on harbour automation", news.baseUrl() + "/rss/articles/undated", null,
            "reuters.com", "https://reuters.com");
        news.responder = StubNews.firstQueryItems(List.of(List.of(undated)));
        Ran r = run(B);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        String pack = packRaw(r.sid(), r.id());
        Map<String, Object> item = JsonPath.read(pack, "$.wildcardSections[0].items[0]");
        assertThat(item).as("undated item").doesNotContainKey("publishedAt").doesNotContainKey("snippet");
        assertThat(item).containsEntry("contentRetrieved", true).containsEntry("evidenceId", "E001");
        assertThat(item.get("fragments")).isEqualTo(List.of(P1));
        assertThat(lines(JsonPath.read(pack, "$.promptText"))).contains("Excerpt: " + P1)
            .noneMatch(l -> l.startsWith("Content not retrieved"));
    }
}
