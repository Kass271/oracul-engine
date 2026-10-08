package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * phase-04 story-rewrite.md FR-68, backend half 1: {@code styleLabel} / {@code languageLabel} of getFutureResult.metadata and of
 * listRecentRuns items for every class of the label table, and {@code rewrittenFrom} for REWRITE runs only. Classes are produced
 * with a real stub run whose stored configuration is then overwritten (or, for the list, with a seeded row).
 */
// @trace FR-68
class StyleLabelsIT extends AbstractLabelsIT {

    /** One class of the label table: raw JSON of the two output keys (null = key absent) and the expected labels. */
    record LabelCase(String name, String style, String language, String styleLabel, String languageLabel) {
        @Override
        public String toString() {
            return name;
        }
    }

    private static final String EN = "\"en\"";
    private static final String UK = "\"uk\"";

    static String langLabel(String languageJson) {
        return UK.equals(languageJson) ? UKRAINIAN : ENGLISH;
    }

    /** The label classes: 9 profiles, legacy null, language null, and the 3 style kinds x 3 language kinds. */
    static List<LabelCase> labelCases() {
        List<LabelCase> out = new ArrayList<>();
        for (String id : PROFILE_IDS) {
            out.add(new LabelCase("profile " + id, styleJson(modeOf(id), id), EN, labelOf(id), ENGLISH));
        }
        out.add(new LabelCase("writingStyle JSON null", "null", null, LEGACY, ENGLISH));
        out.add(new LabelCase("language JSON null", styleJson("NARRATIVE", "kafka"), "null", KAFKA, ENGLISH));
        Map<String, String[]> styles = new java.util.LinkedHashMap<>();
        styles.put("shipped kafka", new String[] {styleJson("NARRATIVE", "kafka"), KAFKA});
        styles.put("legacy (no key)", new String[] {null, LEGACY});
        styles.put("missing tolstoy", new String[] {styleJson("NARRATIVE", "tolstoy"), "tolstoy"});
        Map<String, String> languages = new java.util.LinkedHashMap<>();
        languages.put("en", EN);
        languages.put("uk", UK);
        languages.put("absent", null);
        for (var s : styles.entrySet()) {
            for (var l : languages.entrySet()) {
                out.add(new LabelCase("cross " + s.getKey() + " x " + l.getKey(), s.getValue()[0], l.getValue(), s.getValue()[1], langLabel(l.getValue())));
            }
        }
        return out;
    }

    static Stream<Arguments> labelClasses() {
        return labelCases().stream().map(Arguments::of);
    }

    static Stream<Arguments> labelClassesByStatus() {
        return labelCases().stream().flatMap(c -> Stream.of("COMPLETED", "STOPPED").map(s -> Arguments.of(c, s)));
    }

    // Ranges & invariants: every class of the label table, one case each, over getFutureResult and listRecentRuns: the 9 shipped
    // profiles in their own mode, writingStyle null, a pattern-valid profileId missing from the file, language en / uk / absent / null,
    // and the cross product of the 3 style kinds with the 3 language kinds (the two labels are independent).
    // @trace FR-68
    @ParameterizedTest(name = "{0}")
    @MethodSource("labelClasses")
    void theLabelsOfEveryClassAreServedByTheResultAndTheList(LabelCase c) throws Exception {
        Ran p = parent();
        storeConfiguration(p.id(), bodyWithOutput(c.style(), c.language()));

        Map<String, Object> metadata = metadataOf(p);
        assertThat(metadata.get("styleLabel")).as("metadata.styleLabel").isEqualTo(c.styleLabel());
        assertThat(metadata.get("languageLabel")).as("metadata.languageLabel").isEqualTo(c.languageLabel());
        assertThat(metadata).as("a STANDARD run has no rewrittenFrom key").doesNotContainKey("rewrittenFrom");

        Map<String, Object> item = listItem(p.sid(), p.id());
        assertThat(item.get("styleLabel")).as("item.styleLabel").isEqualTo(c.styleLabel());
        assertThat(item.get("languageLabel")).as("item.languageLabel").isEqualTo(c.languageLabel());
        assertThat(item.get("styleLabel")).isEqualTo(metadata.get("styleLabel"));
        assertThat(item.get("languageLabel")).isEqualTo(metadata.get("languageLabel"));
    }

    // Ranges & invariants: the same classes on the Recent futures list for COMPLETED and for STOPPED entries (seeded rows).
    // @trace FR-68
    @ParameterizedTest(name = "{0} / {1}")
    @MethodSource("labelClassesByStatus")
    void theListLabelsCompletedAndStoppedEntriesAlike(LabelCase c, String status) throws Exception {
        String sid = newSid();
        UUID id = seedListed(sid, status, bodyWithOutput(c.style(), c.language()), OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC));
        Map<String, Object> item = listItem(sid, id.toString());
        assertThat(item.get("status")).isEqualTo(status);
        assertThat(item.get("styleLabel")).isEqualTo(c.styleLabel());
        assertThat(item.get("languageLabel")).isEqualTo(c.languageLabel());
    }

    // @trace FR-68
    @Test
    void aRunStartedWithoutAStyleIsLabelledLegacyAndAnExplicitOfficialStyleIsNot() throws Exception {
        Ran legacy = parent();
        assertThat(configurationOf(legacy.run()).get("output")).as("absent stays absent").isEqualTo(json("{\"story\":true,\"illustration\":false}"));
        Map<String, Object> md = metadataOf(legacy);
        assertThat(md.get("styleLabel")).isEqualTo(LEGACY);
        assertThat(md.get("languageLabel")).isEqualTo(ENGLISH);
        for (String key : List.of("configuration", "horizonLabel", "wildcards", "counts")) {
            assertThat(md).as("metadata keeps " + key).containsKey(key);
        }
        assertThat(md).doesNotContainKey("rewrittenFrom");

        Ran official = runV4(bodyWithOutput(styleJson("OFFICIAL", "official-briefing"), EN));
        assertStoryCompleted(official.run());
        Map<String, Object> md2 = metadataOf(official);
        assertThat(md2.get("styleLabel")).as("an explicit Official / Briefing is not the legacy label").isEqualTo(OFFICIAL);
        assertThat(md2.get("languageLabel")).isEqualTo(ENGLISH);
    }

    // @trace FR-68
    @Test
    void aUkrainianNarrativeRunIsLabelledByItsStoredChoice() throws Exception {
        Ran r = runV4(bodyWithOutput(styleJson("NARRATIVE", "kafka"), UK));
        assertThat(r.run().get("status")).as("run: " + r.run()).isEqualTo("COMPLETED");
        Map<String, Object> md = metadataOf(r);
        assertThat(md.get("styleLabel")).isEqualTo(KAFKA);
        assertThat(md.get("languageLabel")).isEqualTo(UKRAINIAN);
        assertThat(joinedLabel(listItem(r.sid(), r.id()))).isEqualTo("Narrative — inspired by Franz Kafka · Ukrainian");
    }

    // @trace FR-68
    @Test
    void anAlternativeAndAStandardRunHaveNoRewrittenFromButBothHaveLabels() throws Exception {
        Ran p = parent();
        Ran alt = alternativeOf(p);
        assertStoryCompleted(alt.run());
        for (Ran r : List.of(p, alt)) {
            Map<String, Object> md = metadataOf(r);
            assertThat(md).as(r.run().get("kind") + " has no rewrittenFrom key").doesNotContainKey("rewrittenFrom");
            assertThat(md.get("styleLabel")).isEqualTo(LEGACY);
            assertThat(md.get("languageLabel")).isEqualTo(ENGLISH);
        }
    }

    private static final List<String[]> CHAIN = List.of(new String[] {"kafka", "uk"}, new String[] {"poe", "en"});

    // Ranges & invariants: rewrittenFrom present iff kind REWRITE, runId = parentRunId and headline = getRun(parentRunId).headline,
    // for chains of length 1 and 2 (a rewrite of a rewrite names the parent rewrite); a REWRITE's labels come from its own
    // configuration and the parent's stay; every listed COMPLETED run has the labels of its metadata.
    // @trace FR-68
    @ParameterizedTest(name = "chain of {0} rewrite(s)")
    @ValueSource(ints = {1, 2})
    void aRewriteNamesItsDirectParentAndCarriesItsOwnLabels(int length) throws Exception {
        Ran original = runV4(bodyWithOutput(styleJson("OFFICIAL", "official-briefing"), EN));
        assertStoryCompleted(original.run());
        assertThat(metadataOf(original)).doesNotContainKey("rewrittenFrom");
        List<Ran> all = new ArrayList<>();
        all.add(original);
        List<String[]> expected = new ArrayList<>();
        expected.add(new String[] {OFFICIAL, ENGLISH});
        Ran current = original;
        for (int i = 0; i < length; i++) {
            String profileId = CHAIN.get(i)[0];
            String language = CHAIN.get(i)[1];
            String where = "step " + (i + 1) + " " + profileId + "/" + language;
            Map<String, Object> parentRun = json(getRun(current.sid(), current.id()));

            Ran r = rewriteRun(current, body(profileId, language));
            assertThat(r.run().get("status")).as(where + ": " + r.run()).isEqualTo("COMPLETED");
            Map<String, Object> md = metadataOf(r);
            assertThat(md.get("styleLabel")).as(where).isEqualTo(labelOf(profileId));
            assertThat(md.get("languageLabel")).as(where).isEqualTo("uk".equals(language) ? UKRAINIAN : ENGLISH);
            Map<String, Object> from = map(md.get("rewrittenFrom"));
            assertThat(from).as(where + ": rewrittenFrom").containsOnlyKeys("runId", "headline");
            assertThat(from.get("runId")).as(where).isEqualTo(current.id()).isEqualTo(r.run().get("parentRunId"));
            assertThat(from.get("headline")).as(where).isEqualTo(parentRun.get("headline"));
            if (i == 1) {
                assertThat(from.get("runId")).as("the direct parent, not the scenario run").isNotEqualTo(r.run().get("scenarioRunId"));
                assertThat(from.get("headline")).isNotEqualTo(original.run().get("headline"));
            }

            // the labels of every earlier run did not change
            for (int k = 0; k < all.size(); k++) {
                Map<String, Object> earlier = metadataOf(all.get(k));
                assertThat(earlier.get("styleLabel")).as(where + ": labels of run " + k).isEqualTo(expected.get(k)[0]);
                assertThat(earlier.get("languageLabel")).isEqualTo(expected.get(k)[1]);
            }
            all.add(r);
            expected.add(new String[] {labelOf(profileId), "uk".equals(language) ? UKRAINIAN : ENGLISH});
            current = r;
        }
        assertThat(metadataOf(original)).doesNotContainKey("rewrittenFrom");

        // the list: REWRITE runs are listed with their own labels, equal to their metadata
        List<Map<String, Object>> items = listItems(original.sid());
        assertThat(items).hasSize(all.size());
        for (Map<String, Object> item : items) {
            Map<String, Object> md = metadataOf(original.sid(), (String) item.get("id"));
            assertThat(item.get("styleLabel")).as("item " + item.get("kind")).isEqualTo(md.get("styleLabel"));
            assertThat(item.get("languageLabel")).isEqualTo(md.get("languageLabel"));
            assertThat(md.containsKey("rewrittenFrom")).as("rewrittenFrom iff REWRITE").isEqualTo("REWRITE".equals(item.get("kind")));
        }
        assertThat(items.get(0).get("kind")).isEqualTo("REWRITE");
        assertThat(joinedLabel(items.get(0))).isEqualTo(expected.get(length)[0] + " · " + expected.get(length)[1]);
    }
}
