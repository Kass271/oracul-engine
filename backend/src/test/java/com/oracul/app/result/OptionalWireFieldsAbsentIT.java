package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Wire format of api/openapi.yaml 0.7.0: an optional field that is unset is absent from the JSON, never null and never [].
 * Walks the raw body of every endpoint that carries one of the phase-03 optional fields.
 */
// @trace FR-49
class OptionalWireFieldsAbsentIT extends AbstractStoryIT {

    /** Optional 0.7.0 field name -> the schema property it belongs to. */
    private static final Map<String, String> OPTIONAL = Map.ofEntries(
        Map.entry("wildcardsWithoutSources", "EvidenceNote.wildcardsWithoutSources"),
        Map.entry("sourcesKept", "ResearchCounts.sourcesKept"),
        Map.entry("sourcesWithContent", "ResearchCounts.sourcesWithContent"),
        Map.entry("pipelines", "SearchPlan.pipelines"),
        Map.entry("publisherHost", "Source.publisherHost"),
        Map.entry("contentStatus", "Source.contentStatus"),
        Map.entry("excerpts", "Source.excerpts"),
        Map.entry("pipelineIds", "Source.pipelineIds"),
        Map.entry("wildcardSections", "EvidencePack.wildcardSections"),
        Map.entry("wildcardGroups", "FutureResult.wildcardGroups"));

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

        assertNoOptionalKeys("GET /api/runs/{id}", run);
        assertNoOptionalKeys("GET /api/runs/{id}/research", research);
        assertNoOptionalKeys("GET /api/runs/{id}/sources", sources);
        assertNoOptionalKeys("GET /api/runs/{id}/evidence-pack", pack);
        assertNoOptionalKeys("GET /api/runs/{id}/result", result);
    }
}
