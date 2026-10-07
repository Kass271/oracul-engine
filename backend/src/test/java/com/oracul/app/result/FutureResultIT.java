package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.EvidenceItem;
import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.EvidenceSection;
import com.oracul.app.api.model.Source;
import com.oracul.app.research.EvidencePackRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * future-result.md "Slice 09_future-story" ITs #3, #12 (empty pack, rejected), #13, #14: getFutureResult. Slice 09_wildcard-results
 * (wildcard-result-views.md "Changes earlier behaviour"): a new run's result has ten keys, the tenth being {@code wildcardGroups}; a
 * stored legacy pack still has none.
 */
// @trace FR-23, FR-25, FR-60
class FutureResultIT extends AbstractStoryIT {

    @Autowired
    EvidencePackRepository packs;

    private static EvidenceItem legacyItem(String id, EvidenceSection section, List<String> sourceIds) {
        return new EvidenceItem(id, section, "EV" + id.substring(1), "labour", "Legacy summary " + id,
            new ArrayList<>(List.of("Entity")), new ArrayList<>(sourceIds), 0.85, 0.8);
    }

    /**
     * Stored legacy pack (core / supporting / counterSignals, wildcardSections null: phase-01 runs and the packs ALTERNATIVE runs
     * reuse, wildcard-evidence.md): the result keeps the phase-01 layout, one ResultSource per item in Evidence ID order.
     */
    // @trace FR-57
    @Test
    void aStoredLegacyPackKeepsThePhaseOneLayoutOfTheResultSources() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        UUID packId = jdbc.queryForObject("select evidence_pack_id from generation_run where id = cast(? as uuid)", UUID.class, r.id());
        EvidencePack wildcard = packs.findById(packId).orElseThrow();
        assertThat(wildcard.getWildcardSections()).as("a new run builds a wildcard pack").isNotNull();
        List<Source> withDate = wildcard.getSources().stream().filter(x -> x.getPublishedAt() != null).toList();
        assertThat(withDate).as("the run kept at least two dated sources").hasSizeGreaterThanOrEqualTo(2);
        Source a = withDate.get(0);
        Source b = withDate.get(1);

        // inserted out of order on purpose; E003 has no source at all
        List<EvidenceItem> core = new ArrayList<>(List.of(legacyItem("E001", EvidenceSection.CORE, List.of(a.getId(), b.getId()))));
        List<EvidenceItem> supporting = new ArrayList<>(List.of(
            legacyItem("E003", EvidenceSection.SUPPORTING, List.of()),
            legacyItem("E002", EvidenceSection.SUPPORTING, List.of(b.getId()))));
        List<EvidenceItem> counters = new ArrayList<>(List.of(legacyItem("E004", EvidenceSection.COUNTER_SIGNAL, List.of(a.getId()))));
        EvidencePack legacy = new EvidencePack(UUID.randomUUID(), wildcard.getGenerationId(), wildcard.getCutoff(), wildcard.getConfiguration(),
            wildcard.getProfile(), core, supporting, counters, new ArrayList<>(wildcard.getSources()), "legacy prompt text");
        legacy.setWildcardSections(null); // as stored before phase 03: the sections column is NULL
        packs.insert(UUID.fromString(r.id()), legacy, OffsetDateTime.now());
        jdbc.update("update generation_run set evidence_pack_id = ? where id = cast(? as uuid)", legacy.getId(), r.id());

        Map<String, Object> res = result(r);
        // wildcard-result-views.md slice 09: no wildcardSections on the stored pack -> the key is absent (never [] and never null)
        assertThat(res).as("a legacy pack has no wildcardGroups").doesNotContainKey("wildcardGroups");
        assertThat(resultRaw(r)).doesNotContain("wildcardGroups");
        List<Map<String, Object>> sources = list(res.get("sources"));
        assertThat(sources.stream().map(x -> x.get("evidenceId")).toList()).as("one entry per item, in Evidence ID order")
            .containsExactly("E001", "E002", "E003", "E004");
        assertThat(sources.stream().map(x -> x.get("section")).toList())
            .containsExactly("CORE", "SUPPORTING", "SUPPORTING", "COUNTER_SIGNAL");
        assertThat(sources.stream().map(x -> x.get("counterSignal")).toList()).containsExactly(false, false, false, true);

        List<String> cited = new ArrayList<>();
        for (Map<String, Object> f : list(map(structured(r).get("structuredScenario")).get("factsUsed"))) {
            for (Object e : (List<?>) f.get("evidenceIds")) cited.add((String) e);
        }
        for (Map<String, Object> src : sources) {
            assertThat(src.get("usedInScenario")).as("usedInScenario of " + src.get("evidenceId"))
                .isEqualTo(cited.contains((String) src.get("evidenceId")));
        }
        assertThat(sources.stream().anyMatch(x -> Boolean.TRUE.equals(x.get("usedInScenario")))).as("the scenario cites a pack item").isTrue();

        // the first source of the item gives title, publisher, url and publishedAt
        assertSourceOf(sources.get(0), a);
        assertSourceOf(sources.get(1), b);
        assertSourceOf(sources.get(3), a);
        Map<String, Object> noSource = sources.get(2);
        assertThat(noSource.get("title")).isEqualTo("");
        assertThat(noSource.get("publisher")).isEqualTo("");
        assertThat(noSource.get("url")).isEqualTo("");
        assertThat(noSource.get("publishedAt")).as("no source, no publishedAt").isNull();
    }

    private static void assertSourceOf(Map<String, Object> got, Source expected) {
        assertThat(got.get("title")).isEqualTo(expected.getTitle());
        assertThat(got.get("publisher")).isEqualTo(expected.getPublisher());
        assertThat(got.get("url")).isEqualTo(expected.getUrl().toString());
        assertThat(OffsetDateTime.parse(String.valueOf(got.get("publishedAt"))).toInstant())
            .isEqualTo(expected.getPublishedAt().toInstant());
    }

    // #3
    @Test
    void aCompletedRunServesTheFullFutureResult() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String d = dayAfter(cutoffDate(r));
        Map<String, Object> res = result(r);
        assertThat(res.keySet())
            .containsExactlyInAnyOrder("runId", "generationId", "labels", "story", "metadata", "causalChain",
            "sources", "research", "openCriticIssues", "wildcardGroups");
        assertThat(res.get("runId")).isEqualTo(r.id());
        assertThat(res.get("generationId")).isEqualTo(r.run().get("generationId"));
        assertThat(res.get("labels")).isEqualTo(List.of("AI-GENERATED FUTURE SCENARIO", "POSSIBLE FUTURE — NOT CURRENT NEWS"));
        Map<String, Object> story = map(res.get("story"));
        assertThat(story).isEqualTo(Map.of("headline", "Stub headline from the future",
            "dateline", StoryHarness.dateline(java.time.LocalDate.parse(d)), "futureDate", d, "body", StoryFixtures.body(3)));
        assertThat(story.get("dateline")).isNotEqualTo("STUB DATELINE");

        Map<String, Object> meta = map(res.get("metadata"));
        assertThat(meta.get("configuration")).isEqualTo(json(A));
        assertThat(meta.get("horizonLabel")).isEqualTo("5 years");
        assertThat(meta.get("wildcards")).isEqualTo(jsonOf("[{\"label\":\"New pandemic\",\"intensity\":8,\"custom\":false},"
            + "{\"label\":\"Humanoid robot boom\",\"intensity\":6,\"custom\":false}]"));
        Map<String, Object> counts = counts(r.run());
        assertThat(meta.get("counts")).isEqualTo(counts);
        assertThat(map(res.get("research")).get("counts")).isEqualTo(counts);
        assertThat(num(counts.get("articlesConsidered"))).isGreaterThan(0);
        assertThat(num(counts.get("uniqueEvents"))).isGreaterThan(0);
        assertThat(num(counts.get("eventsSelected"))).isGreaterThan(0);

        assertThat(res.get("causalChain")).isEqualTo(map(structured(r).get("structuredScenario")).get("causalChain"));

        List<Map<String, Object>> sources = list(res.get("sources"));
        assertThat(sources).hasSize(((Number) counts.get("eventsSelected")).intValue());
        List<String> ids = sources.stream().map(x -> (String) x.get("evidenceId")).toList();
        assertThat(ids).isSorted().doesNotHaveDuplicates();
        for (Map<String, Object> src : sources) {
            assertThat(src.keySet()).contains("evidenceId", "section", "title", "publisher", "url", "usedInScenario", "counterSignal");
            assertThat(src.get("counterSignal")).isEqualTo("COUNTER_SIGNAL".equals(src.get("section")));
        }
        assertThat(sources.stream().filter(x -> Boolean.TRUE.equals(x.get("usedInScenario"))).count())
            .isEqualTo(((Number) counts.get("sourcesUsed")).longValue());
        assertThat(map(res.get("research")).get("intents")).isEqualTo(map(researchBody(r.sid(), r.id()).get("searchPlan")).get("intents"));
        assertThat(res.get("openCriticIssues")).isEqualTo(List.of());
    }

    // #14
    @Test
    void aOneDayRunHasATomorrowWindowAndNoWildcards() throws Exception {
        Ran r = runV4(withHorizon(B, "1d"));
        assertStoryCompleted(r.run());
        String c = cutoffDate(r);
        assertThat(s(1)).contains("Story date window: after " + c + " and no later than " + dayAfter(c) + "\n");
        Map<String, Object> res = result(r);
        assertThat(map(res.get("story")).get("futureDate")).isEqualTo(dayAfter(c));
        Map<String, Object> meta = map(res.get("metadata"));
        assertThat(meta.get("horizonLabel")).isEqualTo("Tomorrow");
        assertThat(meta.get("wildcards")).isEqualTo(List.of());
    }

    // #12 (empty pack; run-control.md FR-47: speculative mode writes a story and a result with no sources)
    // @trace FR-47
    @Test
    void aRunWithAnEmptyPackHasASpeculativeResultWithoutSources() throws Exception {
        Ran r = run(A); // default empty news feed: COMPLETED with headline and story
        assertStoryCompleted(r.run());
        assertThat(requests(STORY)).hasSize(1);
        assertThat(storyRows(r.id())).isEqualTo(1);
        Map<String, Object> res = result(r);
        assertThat(list(res.get("sources"))).isEmpty();
        assertThat(map(res.get("story")).get("headline")).isEqualTo("Stub headline from the future");
        assertThat(num(counts(r.run()).get("articlesConsidered"))).isZero();
    }

    // #12 (rejected scenario)
    @Test
    void aRejectedScenarioHasNoResult() throws Exception {
        scriptScenario(fx("SC-BAD"), fx("SC-BAD"));
        Ran r = runV4(A);
        assertThat(r.run().get("status")).isEqualTo("FAILED");
        assertThat(requests(STORY)).isEmpty();
        assertResultNotReady(getResult(r.sid(), r.id()));
    }

    // #13
    @ParameterizedTest(name = "unknown run [{0}]")
    @ValueSource(strings = {NO_RUN, "abc"})
    void anUnknownOrMalformedRunIs404(String id) throws Exception {
        String sid = connectedSid();
        assertRunNotFound(getResult(sid, id));
    }

    // #13
    @Test
    void aRunOfAnotherSessionIs404() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        String other = connectedSid();
        assertRunNotFound(getResult(other, r.id()));
        assertThat(getResult(r.sid(), r.id()).andReturn().getResponse().getStatus()).isEqualTo(200);
    }

    // #13
    @Test
    void aRequestWithoutACookieIs404() throws Exception {
        Ran r = runV4(A);
        assertStoryCompleted(r.run());
        assertRunNotFound(getResult(null, r.id()));
    }
}
