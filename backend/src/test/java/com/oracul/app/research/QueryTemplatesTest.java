package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.WildcardCategory;
import com.oracul.app.api.model.WildcardDefinition;
import com.oracul.app.scenario.ScenarioCatalogueData;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.Test;

/** QueryTemplates list properties for every intent kind (research-pipeline.md slice 05). */
// @trace FR-12
class QueryTemplatesTest {

    private static void assertWellFormed(String what, List<String> list) {
        assertThat(list).as(what).isNotNull();
        assertThat(list.size()).as(what + " size").isGreaterThanOrEqualTo(20);
        assertThat(list).as(what + " non-blank").allMatch(s -> s != null && !s.isBlank());
        assertThat(list).as(what + " <= 120 chars").allMatch(s -> s.length() <= 120);
        assertThat(list.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count())
            .as(what + " distinct case-insensitive").isEqualTo(list.size());
    }

    private static SearchIntent intent(String id, QueryBucket bucket, String topicKey, String category, String description) {
        return new SearchIntent(id, bucket, description, List.of("Horizon 1 year")).topicKey(topicKey).category(category);
    }

    @Test
    void everyCatalogueWildcardHasTemplatesStartingWithItsLowerCaseLabel() {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            for (WildcardDefinition w : c.getWildcards()) {
                List<String> list = PlanSupport.templates(intent("I01", QueryBucket.WILDCARD, w.getId(), null,
                    "Current developments related to " + w.getLabel()));
                assertWellFormed(w.getId(), list);
                assertThat(list.get(0)).as(w.getId() + " first").isEqualTo(w.getLabel().toLowerCase(Locale.ROOT));
            }
        }
    }

    @Test
    void everyCatalogueCategoryHasAdjacentTemplates() {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            assertWellFormed("category " + c.getId(), PlanSupport.templates(intent("I04", QueryBucket.ADJACENT, null, c.getId(),
                "Adjacent developments in " + c.getLabel())));
        }
    }

    @Test
    void genericAdjacentMajorAndUnexpectedHaveTemplates() {
        assertWellFormed("generic adjacent", PlanSupport.templates(intent("I02", QueryBucket.ADJACENT, null, null,
            "Adjacent developments in science, technology and economy")));
        assertWellFormed("major", PlanSupport.templates(intent("I03", QueryBucket.MAJOR, null, null, "Major current world events")));
        assertWellFormed("unexpected", PlanSupport.templates(intent("I06", QueryBucket.UNEXPECTED, null, null,
            "Unusual early signals and research")));
    }

    @Test
    void customTopicTemplatesStartWithTheTrimmedLabel() {
        List<String> list = PlanSupport.templates(intent("I01", QueryBucket.WILDCARD, "custom-1", null,
            "Current developments related to Solar sails"));
        assertWellFormed("custom", list);
        assertThat(list.get(0)).isEqualToIgnoringCase("Solar sails");
    }

    private static final String SUFFIX = " — breakthroughs, recoveries and opportunities";

    private static List<String> custom(String label, boolean withSuffix) {
        return PlanSupport.templates(intent("I01", QueryBucket.WILDCARD, "custom-1", null,
            "Current developments related to " + label + (withSuffix ? SUFFIX : "")));
    }

    private static int keywords(String q) {
        return q.trim().split("\\s+").length;
    }

    private static void assertSafeKeywordQueries(String what, List<String> list) {
        assertThat(list).as(what).isNotEmpty();
        for (String q : list) {
            String[] tokens = q.trim().split("\\s+");
            assertThat(q).as(what + " no slash: " + q).doesNotContain("/");
            assertThat(q).as(what + " no quotes: " + q).doesNotContain("\"").doesNotContain("\u201c").doesNotContain("\u201d");
            assertThat(q).as(what + " no parentheses: " + q).doesNotContain("(").doesNotContain(")");
            assertThat(q.length()).as(what + " <=120: " + q).isLessThanOrEqualTo(120);
            assertThat(tokens.length).as(what + " 2..8 keywords: " + q).isBetween(2, 8);
            for (String t : tokens) {
                assertThat(t).as(what + " no leading dash: " + q).doesNotStartWith("-");
                assertThat(t).as(what + " no operator word: " + q).isNotIn("OR", "AND", "NOT", "or", "and", "not");
            }
            assertThat(tokens[0].length() < 3 && tokens.length == 1).as(what + " bare short subject: " + q).isFalse();
        }
    }

    // R9
    @Test
    void longCustomLabelAppearsInFullInEveryQuery() {
        for (boolean suffix : new boolean[] {false, true}) {
            List<String> list = custom("Collapse of the global internet", suffix);
            assertWellFormed("long custom", list);
            assertThat(list).as("full label in every query (suffix=" + suffix + ")")
                .allMatch(q -> q.toLowerCase(Locale.ROOT).contains("collapse of the global internet"));
        }
    }

    // R9
    @Test
    void customLabelContainingEmDashIsNotCutAtTheDash() {
        for (boolean suffix : new boolean[] {false, true}) {
            List<String> list = custom("Ocean — desalination boom", suffix);
            assertWellFormed("dash custom", list);
            assertThat(list).as("dash label kept (suffix=" + suffix + ")")
                .allMatch(q -> q.toLowerCase(Locale.ROOT).contains("desalination boom"));
        }
    }

    // R9 + R12: labels of <=6 words appear in full in every query; longer labels follow the shortened-subject rule.
    @Test
    void fortyCharacterCustomLabelStillYieldsQueriesWithinLimit() {
        String label = "Rise of quantum battery startups in Asia";
        assertThat(label).hasSize(40);
        for (boolean suffix : new boolean[] {false, true}) {
            List<String> list = custom(label, suffix);
            assertWellFormed("40-char custom (suffix=" + suffix + ")", list);
            assertThat(list).allMatch(q -> q.length() <= 120);
            assertSafeKeywordQueries("40-char custom (suffix=" + suffix + ")", list);
            // 7 words (<= 8): the first query is the full label
            assertThat(list.get(0).toLowerCase(Locale.ROOT)).as("first query is the full label")
                .contains(label.toLowerCase(Locale.ROOT));
            // every other query keeps the first two non-stopword words
            for (String q : list.subList(1, list.size())) {
                String lower = q.toLowerCase(Locale.ROOT);
                assertThat(lower).as("first two content words in: " + q).contains("quantum").contains("battery");
            }
        }
    }

    // R12: labels of up to six words keep the full label in every query.
    @Test
    void labelsOfUpToSixWordsAppearInFullInEveryQuery() {
        for (String label : List.of("Fall of the big tech firms", "Collapse of the global shipping networks",
            "Next generation battery storage", "Deep sea mining")) {
            for (boolean suffix : new boolean[] {false, true}) {
                List<String> list = custom(label, suffix);
                assertWellFormed(label, list);
                assertSafeKeywordQueries(label, list);
                assertThat(list).as("full label in every query: " + label + " suffix=" + suffix)
                    .allMatch(q -> q.toLowerCase(Locale.ROOT).contains(label.toLowerCase(Locale.ROOT)));
            }
        }
    }

    // R10
    @Test
    void everyCatalogueIntentYieldsSafePlainKeywordQueries() {
        for (WildcardCategory c : ScenarioCatalogueData.catalogue().getCategories()) {
            assertSafeKeywordQueries("category " + c.getId(), PlanSupport.templates(intent("I04", QueryBucket.ADJACENT, null,
                c.getId(), "Adjacent developments in " + c.getLabel())));
            for (WildcardDefinition w : c.getWildcards()) {
                assertSafeKeywordQueries(w.getId(), PlanSupport.templates(intent("I01", QueryBucket.WILDCARD, w.getId(), null,
                    "Current developments related to " + w.getLabel())));
            }
        }
        assertSafeKeywordQueries("generic", PlanSupport.templates(intent("I02", QueryBucket.ADJACENT, null, null,
            "Adjacent developments in science, technology and economy")));
        assertSafeKeywordQueries("major", PlanSupport.templates(intent("I03", QueryBucket.MAJOR, null, null, "Major current world events")));
        assertSafeKeywordQueries("unexpected", PlanSupport.templates(intent("I06", QueryBucket.UNEXPECTED, null, null,
            "Unusual early signals and research")));
    }

    // R10
    @Test
    void aiCategoryDoesNotProduceTheBareQueryAi() {
        List<String> list = PlanSupport.templates(intent("I04", QueryBucket.ADJACENT, null, "ai", "Adjacent developments in AI"));
        assertThat(list).as("no bare 'ai'").noneMatch(q -> q.trim().equalsIgnoreCase("ai"));
        assertThat(list.get(0).toLowerCase(Locale.ROOT)).as("first query expands ai").contains("artificial intelligence");
    }

    // R10
    @Test
    void categoryLabelWithSlashProducesNoSlash() {
        List<String> list = PlanSupport.templates(intent("I05", QueryBucket.ADJACENT, null, "political",
            "Adjacent developments in Political / institutional"));
        assertThat(list).noneMatch(q -> q.contains("/"));
    }

    // R10
    @Test
    void hostileCustomLabelsNeverProduceOperatorSyntax() {
        List<String> labels = List.of("\"Quantum\" computing race", "-leaked memo scandal", "Chip (export) controls",
            "Rock OR roll revival", "Cats AND dogs merger", "NOT my problem crisis", "AI/ML regulation wave",
            "Say \"no\" to (nuclear) power");
        for (String label : labels) {
            for (boolean suffix : new boolean[] {false, true}) {
                List<String> list = custom(label, suffix);
                assertWellFormed("hostile " + label, list);
                assertSafeKeywordQueries("hostile " + label, list);
            }
        }
    }

    private static final java.util.Set<String> STOP = java.util.Set.of("a", "an", "the", "of", "in", "on", "to", "for", "at", "by", "with");

    private static List<String> contentWords(String label) {
        return java.util.Arrays.stream(label.toLowerCase(Locale.ROOT).split("\\s+")).filter(w -> !STOP.contains(w)).toList();
    }

    // R11
    // @trace FR-12
    @Test
    void customLabelsOfEightToTenWordsYieldEnoughSafeQueriesKeepingTheTopicRecognisable() {
        List<String> labels = List.of("Fall of the big US tech firms in the EU", "a new kind of fast AI chip for phones",
            "Rise of the new solar fuel cell boom");
        for (String label : labels) {
            assertThat(label.length()).isLessThanOrEqualTo(40);
            assertThat(label.split(" ").length).isBetween(8, 10);
            List<String> words = contentWords(label);
            for (boolean suffix : new boolean[] {false, true}) {
                String what = label + " suffix=" + suffix;
                List<String> list = custom(label, suffix);
                assertThat(list.size()).as(what + " size").isGreaterThanOrEqualTo(20);
                assertThat(list.stream().map(q -> q.toLowerCase(Locale.ROOT)).distinct().count()).as(what + " distinct").isEqualTo(list.size());
                assertSafeKeywordQueries(what, list);
                List<String> first = java.util.Arrays.asList(list.get(0).toLowerCase(Locale.ROOT).split("\\s+"));
                int from = 0;
                for (String w : words) {
                    int idx = first.subList(from, first.size()).indexOf(w);
                    assertThat(idx).as(what + " first query has '" + w + "' in order: " + list.get(0)).isGreaterThanOrEqualTo(0);
                    from += idx + 1;
                }
                for (String q : list) {
                    List<String> toks = java.util.Arrays.asList(q.toLowerCase(Locale.ROOT).split("\\s+"));
                    assertThat(toks).as(what + " keeps first two topic words: " + q).contains(words.get(0), words.get(1));
                }
            }
        }
    }
}
