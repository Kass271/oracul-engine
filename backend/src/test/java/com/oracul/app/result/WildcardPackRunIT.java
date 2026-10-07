package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.reasoning.ScenarioFixtures;
import com.oracul.app.research.StubNews;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.test.web.servlet.MvcResult;

/**
 * wildcard-evidence.md "Slice 06_wildcard-pack", run level ("Ranges & invariants" of FR-57): the invariants of the grouped Evidence
 * Pack walked over four data sets (fixture (a) V4 under body A, fixture (c) V4 under the GENERAL body B, seven sources in two
 * pipelines with one shared article, an empty pack), plus the Evidence Guard, the ALTERNATIVE run and the getFutureResult sources.
 * Talks HTTP only.
 */
// @trace FR-57
class WildcardPackRunIT extends AbstractStoryIT {

    private static final String CRITIC = "SCENARIO_CRITIC";

    static Stream<String> datasets() {
        return Stream.of("V4 under body A", "V4 under body B", "seven sources, one shared", "empty feed under body A");
    }

    private List<Art> articles(int n) {
        List<Art> arts = new ArrayList<>();
        for (int i = 1; i <= n; i++) {
            arts.add(new Art("p-" + i, "reuters.com", "Pack article " + i));
            news.site("p-" + i, "Publisher " + i);
        }
        return arts;
    }

    private Ran runDataset(String name) throws Exception {
        switch (name) {
            case "V4 under body A" -> {
                return runV4(A);
            }
            case "V4 under body B" -> {
                return runV4(B);
            }
            case "seven sources, one shared" -> {
                news.reset();
                List<Art> arts = articles(7);
                // W01 = articles 1-4, W02 = the shared article 1 and the articles 5-7 (default normalisation and classification)
                news.responder = StubNews.firstQueryItems(List.of(
                    List.of(itemOf(arts.get(0)), itemOf(arts.get(1)), itemOf(arts.get(2)), itemOf(arts.get(3))),
                    List.of(itemOf(arts.get(0)), itemOf(arts.get(4)), itemOf(arts.get(5)), itemOf(arts.get(6)))));
                return run(A);
            }
            case "empty feed under body A" -> {
                news.reset();
                return run(A);
            }
            default -> throw new IllegalArgumentException(name);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<String> pipelineSourceIds(Map<String, Object> research, int i) {
        Map<String, Object> plan = (Map<String, Object>) research.get("searchPlan");
        List<Map<String, Object>> pipelines = (List<Map<String, Object>>) plan.get("pipelines");
        return (List<String>) pipelines.get(i).get("sourceIds");
    }

    @SuppressWarnings("unchecked")
    private static int pipelineCount(Map<String, Object> research) {
        return ((List<?>) ((Map<String, Object>) research.get("searchPlan")).get("pipelines")).size();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("datasets")
    void theInvariantsOfTheGroupedPackHoldForEveryDataset(String dataset) throws Exception {
        Ran r = runDataset(dataset);
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        assertThat(r.run().get("headline")).isNotNull();
        String raw = packRaw(r.sid(), r.id());
        Map<String, Object> pack = json(raw);
        String promptText = (String) pack.get("promptText");

        // legacy lists empty, counterSignals 0
        assertThat(section(pack, "core")).isEmpty();
        assertThat(section(pack, "supporting")).isEmpty();
        assertThat(section(pack, "counterSignals")).isEmpty();
        assertThat(counts(r.run()).get("counterSignals")).isEqualTo(0);
        assertThat(promptText).doesNotContain("CORE EVIDENCE").doesNotContain("COUNTER-SIGNALS").doesNotContain("<<<").doesNotContain(">>>");
        assertThat(promptText).doesNotEndWith("\n");

        // promptText = DB prompt_text = the evidence-pack block of every generation and critic request
        assertThat(jdbc.queryForObject("select prompt_text from evidence_pack where run_id = cast(? as uuid)", String.class, r.id()))
            .isEqualTo(promptText);
        assertThat(requests(GEN)).isNotEmpty();
        assertThat(requests(CRITIC)).isNotEmpty();
        for (StubResponses.Request req : requests(GEN)) assertThat(StubResponses.dataBlock(req.inputText(), "evidence-pack")).isEqualTo(promptText);
        for (StubResponses.Request req : requests(CRITIC)) assertThat(StubResponses.dataBlock(req.inputText(), "evidence-pack")).isEqualTo(promptText);

        // the body is identical on repeated calls
        assertThat(packRaw(r.sid(), r.id())).isEqualTo(raw);

        // eventsSelected = distinct section ids = sourcesKept = sources length = listRunSources length
        List<String> ids = sectionIds(pack);
        List<Map<String, Object>> runSources = sourceItems(r.sid(), r.id());
        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(ids.size());
        assertThat(counts(r.run()).get("sourcesKept")).isEqualTo(ids.size());
        assertThat((List<?>) pack.get("sources")).hasSize(ids.size());
        assertThat(runSources).hasSize(ids.size());
        assertThat(pack.get("sources")).isEqualTo(runSources);

        // Evidence IDs E001...E0k without gaps, in order of first appearance; E00k = S00k
        for (int k = 0; k < ids.size(); k++) assertThat(ids.get(k)).isEqualTo(String.format("E%03d", k + 1));
        List<Map<String, Object>> sections = wildcardSections(pack);
        Map<String, Object> research = researchBody(r.sid(), r.id());
        assertThat(sections).hasSize(pipelineCount(research));
        List<String> listed = new ArrayList<>();
        for (int i = 0; i < sections.size(); i++) {
            // section i lists exactly the ids of pipeline i's sourceIds in that order
            List<String> expected = pipelineSourceIds(research, i).stream().map(s -> "E" + s.substring(1)).toList();
            assertThat(items(sections.get(i)).stream().map(it -> (String) it.get("evidenceId")).toList()).as("section " + i).isEqualTo(expected);
            for (Map<String, Object> item : items(sections.get(i))) {
                assertThat(item.get("sourceId")).isEqualTo("S" + ((String) item.get("evidenceId")).substring(1));
                Map<String, Object> source = runSources.stream().filter(s -> s.get("id").equals(item.get("sourceId"))).findFirst().orElseThrow();
                assertThat(item.get("title")).isEqualTo(source.get("title"));
                assertThat(item.get("publisher")).isEqualTo(source.get("publisher"));
                assertThat(item.get("url")).isEqualTo(source.get("url"));
                assertThat((List<?>) item.get("fragments")).isEmpty();
                assertThat(item.get("contentRetrieved")).isEqualTo(false);
                assertThat(item.get("snippet")).isEqualTo(source.get("summary"));
                listed.add((String) item.get("sourceId"));
            }
        }
        // every kept source is listed in at least one section
        assertThat(listed.stream().distinct().sorted().toList()).isEqualTo(runSources.stream().map(s -> (String) s.get("id")).toList());

        // every Evidence ID of the text belongs to a stored source of the run
        for (String line : promptText.split("\n")) {
            if (line.matches("^\\[E\\d+] .*")) {
                String id = line.substring(1, line.indexOf(']'));
                assertThat(runSources.stream().map(s -> "E" + ((String) s.get("id")).substring(1))).contains(id);
            }
        }

        // no event has a selection (key absent, columns NULL)
        for (Map<String, Object> e : events(r)) assertThat(e).doesNotContainKey("selection");
        assertThat(jdbc.queryForObject("select count(*) from event where run_id = cast(? as uuid) and (evidence_id is not null or selection_section is not null)",
            Integer.class, r.id())).isZero();

        // note: total 0 <=> NO_EVIDENCE (the E2E thresholds of this suite are 0, so no INSUFFICIENT_EVIDENCE); speculative TASK line <=> no item
        if (ids.isEmpty()) {
            assertThat(noteKind(r.run())).isEqualTo("NO_EVIDENCE");
            assertThat(requests(GEN).get(0).inputText()).contains("The Evidence Pack is empty: no current news could be used.");
        } else {
            assertThat(r.run().get("evidenceNote")).isNull();
            assertThat(requests(GEN).get(0).inputText()).doesNotContain("The Evidence Pack is empty");
        }

        // getFutureResult.sources: one entry per distinct Evidence ID in ID order, section CORE, counterSignal false
        List<Map<String, Object>> resultSources = list(result(r).get("sources"));
        assertThat(resultSources.stream().map(s -> s.get("evidenceId")).toList()).isEqualTo(ids);
        for (Map<String, Object> s : resultSources) {
            assertThat(s.get("section")).isEqualTo("CORE");
            assertThat(s.get("counterSignal")).isEqualTo(false);
            Map<String, Object> source = runSources.stream().filter(x -> ("E" + ((String) x.get("id")).substring(1)).equals(s.get("evidenceId")))
                .findFirst().orElseThrow();
            assertThat(s.get("title")).isEqualTo(source.get("title"));
            assertThat(s.get("publisher")).isEqualTo(source.get("publisher"));
            assertThat(s.get("url")).isEqualTo(source.get("url"));
        }
        assertThat(resultSources.stream().filter(s -> Boolean.TRUE.equals(s.get("usedInScenario"))).count())
            .isEqualTo(((Number) counts(r.run()).get("sourcesUsed")).longValue());
    }

    @Test
    void aSharedSourceIsTheSameItemInBothSectionsAndNumberedByItsFirstAppearance() throws Exception {
        Ran r = runDataset("seven sources, one shared");
        assertStoryCompleted(r.run());
        Map<String, Object> pack = pack(r);
        List<Map<String, Object>> sections = wildcardSections(pack);
        assertThat(sections).hasSize(2);
        List<String> first = items(sections.get(0)).stream().map(i -> (String) i.get("evidenceId")).toList();
        List<String> second = items(sections.get(1)).stream().map(i -> (String) i.get("evidenceId")).toList();
        assertThat(first).hasSize(4);
        assertThat(second).hasSize(4);
        assertThat(first).as("W01 holds the first four numbers").containsExactly("E001", "E002", "E003", "E004");
        List<String> shared = first.stream().filter(second::contains).toList();
        assertThat(shared).as("the shared article is listed in both sections").hasSize(1);
        Map<String, Object> inFirst = items(sections.get(0)).stream().filter(i -> shared.get(0).equals(i.get("evidenceId"))).findFirst().orElseThrow();
        Map<String, Object> inSecond = items(sections.get(1)).stream().filter(i -> shared.get(0).equals(i.get("evidenceId"))).findFirst().orElseThrow();
        assertThat(inSecond).as("the identical item").isEqualTo(inFirst);
        assertThat(sectionIds(pack)).containsExactly("E001", "E002", "E003", "E004", "E005", "E006", "E007");
        assertThat(counts(r.run()).get("eventsSelected")).isEqualTo(7);
        assertThat(counts(r.run()).get("sourcesKept")).isEqualTo(7);
        // the identical item line appears under both headings
        List<String> lines = List.of(((String) pack.get("promptText")).split("\n"));
        String itemLine = lines.stream().filter(l -> l.startsWith("[" + shared.get(0) + "] ")).findFirst().orElseThrow();
        assertThat(lines.stream().filter(itemLine::equals).count()).isEqualTo(2);
        int snippetLine = lines.indexOf(itemLine) + 1;
        assertThat(lines.get(snippetLine)).startsWith("Content not retrieved. Snippet: ");
        assertThat(lines.get(lines.lastIndexOf(itemLine) + 1)).isEqualTo(lines.get(snippetLine));
    }

    @Test
    void anAlternativeRunReusesTheParentsPackUnchanged() throws Exception {
        Ran p = runV4(A);
        assertStoryCompleted(p.run());
        String parentPack = packRaw(p.sid(), p.id());
        MvcResult res = mvc.perform(post("/api/runs/" + p.id() + "/alternatives").cookie(new Cookie("ORACUL_SID", p.sid()))).andReturn();
        assertThat(res.getResponse().getStatus()).as(res.getResponse().getContentAsString()).isEqualTo(202);
        String altId = (String) json(res.getResponse().getContentAsString()).get("id");
        Map<String, Object> done = awaitRun(p.sid(), altId, 15_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
        assertThat(done.get("status")).as("alternative: " + done).isEqualTo("COMPLETED");
        assertThat(done.get("evidencePackId")).isEqualTo(p.run().get("evidencePackId"));
        assertThat(packRaw(p.sid(), altId)).as("the pack body of the ALTERNATIVE run").isEqualTo(parentPack);
        assertThat(counts(done).get("eventsSelected")).isEqualTo(counts(p.run()).get("eventsSelected"));
        assertThat(counts(done).get("counterSignals")).isEqualTo(0);
    }

    // ---- Evidence Guard on a new pack (run level) -----------------------------------------------------------------

    private Answer cite(String f1, String f2) {
        return computed(input -> ScenarioFixtures.sc(ScenarioFixtures.futureDate(input), f1, f1, f2, f2));
    }

    @Test
    void aFactCitingAnIdThatIsOnlyInASectionIsKept() throws Exception {
        scriptScenario(cite("[\"E004\"]", "[\"E001\"]"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertThat(requests(GEN)).hasSize(1);
        Map<String, Object> rec = structured(r);
        assertThat(rec.get("accepted")).isEqualTo(true);
        assertThat(list(rec.get("guardReports")).get(0).get("outcome")).isEqualTo("PASS");
        assertThat(list(rec.get("guardReports")).get(0).get("violations")).isEqualTo(List.of());
    }

    @Test
    void aFactCitingAnIdOutsideTheSectionsIsRemovedAsUnknown() throws Exception {
        scriptScenario(cite("[\"E001\"]", "[\"E005\"]"));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        Map<String, Object> rec = structured(r);
        Map<String, Object> report = list(rec.get("guardReports")).get(0);
        assertThat(report.get("outcome")).isEqualTo("PASS_WITH_REMOVALS");
        List<Map<String, Object>> violations = list(report.get("violations"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).containsEntry("type", "UNKNOWN_EVIDENCE_ID").containsEntry("claimId", "F2").containsEntry("evidenceId", "E005")
            .containsEntry("action", "REMOVED");
    }

    @Test
    void aCounterSignalEntryOnANewPackIsRemovedBecauseThePackHasNoCounterSignal() throws Exception {
        scriptScenario(computed(input -> ScenarioFixtures.scV4(ScenarioFixtures.futureDate(input))
            .replace("\"counterSignalsConsidered\":[]", "\"counterSignalsConsidered\":[{\"evidenceId\":\"E001\",\"howAddressed\":\"x\"}]")));
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        Map<String, Object> rec = structured(r);
        Map<String, Object> report = list(rec.get("guardReports")).get(0);
        assertThat(report.get("outcome")).isEqualTo("PASS_WITH_REMOVALS");
        List<Map<String, Object>> violations = list(report.get("violations"));
        assertThat(violations).hasSize(1);
        assertThat(violations.get(0)).containsEntry("type", "UNKNOWN_EVIDENCE_ID").containsEntry("evidenceId", "E001")
            .containsEntry("detail", "counter-signal E001 is not a counter-signal of the Evidence Pack").containsEntry("action", "REMOVED");
        assertThat(list(((Map<?, ?>) rec.get("structuredScenario")).get("counterSignalsConsidered"))).isEmpty();
    }
}
