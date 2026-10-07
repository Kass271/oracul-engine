package com.oracul.app.style;

import static com.oracul.app.style.StyleFixtures.DICKENS;
import static com.oracul.app.style.StyleFixtures.SHIPPED;
import static com.oracul.app.style.StyleFixtures.fileOf;
import static com.oracul.app.style.StyleFixtures.location;
import static com.oracul.app.style.StyleFixtures.narrative;
import static com.oracul.app.style.StyleFixtures.shipped;
import static com.oracul.app.style.StyleFixtures.write;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * FR-62: what the loaded style file turns into — the WritingStyles domain API (profiles(), find(id)) and the body of
 * GET /api/writing-styles — for every successfully loaded file (shipped, with the dickens entry, reordered, smallest,
 * largest). The controller is exercised through a standalone MockMvc on a context that holds only WritingStyles and
 * WritingStylesController, so a file other than the shipped one can be loaded without a database.
 */
class WritingStylesCatalogueTest {

    @TempDir
    Path dir;

    /** A style file (in file order) and where it comes from. */
    record Scenario(String name, List<StyleFixtures.Entry> entries, boolean defaultLocation) {
        @Override
        public String toString() {
            return name;
        }

        List<String> ids() {
            return entries.stream().map(StyleFixtures.Entry::id).toList();
        }

        /** Catalogue order: the OFFICIAL profile first, the NARRATIVE profiles in file order. */
        List<StyleFixtures.Entry> catalogueOrder() {
            List<StyleFixtures.Entry> out = new ArrayList<>(entries.stream().filter(e -> e.mode().equals("OFFICIAL")).toList());
            out.addAll(entries.stream().filter(e -> e.mode().equals("NARRATIVE")).toList());
            return out;
        }
    }

    static List<Scenario> scenarios() {
        List<StyleFixtures.Entry> withDickens = new ArrayList<>(SHIPPED);
        withDickens.add(DICKENS);
        List<StyleFixtures.Entry> reordered = List.of(shipped("neutral-narrative"), shipped("kafka"), shipped("official-briefing"), shipped("poe"));
        List<StyleFixtures.Entry> officialInTheMiddle = new ArrayList<>(SHIPPED);
        officialInTheMiddle.remove(0);
        officialInTheMiddle.add(4, shipped("official-briefing"));
        List<StyleFixtures.Entry> largest = new ArrayList<>(SHIPPED);
        IntStream.rangeClosed(10, 50).forEach(i -> largest.add(narrative("p" + i)));
        return List.of(
            new Scenario("shipped file (default location)", SHIPPED, true),
            new Scenario("shipped file as a copy", SHIPPED, false),
            new Scenario("shipped + dickens", withDickens, false),
            new Scenario("reordered [neutral-narrative, kafka, official-briefing, poe]", reordered, false),
            new Scenario("official-briefing in the middle", officialInTheMiddle, false),
            new Scenario("smallest valid file (2 entries)", List.of(shipped("official-briefing"), shipped("neutral-narrative")), false),
            new Scenario("largest valid file (50 entries)", largest, false));
    }

    static Stream<Arguments> scenarioArgs() {
        return scenarios().stream().map(Arguments::of);
    }

    private ApplicationContextRunner runnerFor(Scenario s) {
        return s.defaultLocation() ? StyleSeam.runner(null) : StyleSeam.runner(location(write(dir, "styles.json", fileOf(s.entries()))));
    }

    private static String body(MockMvc mvc) throws Exception {
        return mvc.perform(get("/api/writing-styles"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> doc(String body) {
        return (Map<String, Object>) JsonPath.parse(body).json();
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> list(Map<String, Object> doc, String key) {
        return (List<Map<String, Object>>) doc.get(key);
    }

    // @trace FR-62
    @ParameterizedTest(name = "catalogue invariants: {0}")
    @MethodSource("scenarioArgs")
    void catalogueInvariantsHoldForEveryLoadedFile(Scenario s) {
        runnerFor(s).run(ctx -> {
            String body = body(StyleSeam.mvc(ctx));
            Map<String, Object> doc = doc(body);

            // key sets are exact, and the prompt text never leaves the backend
            assertThat(doc.keySet()).containsExactlyInAnyOrder("modes", "profiles", "languages");
            assertThat(body).doesNotContain("instructions");
            s.entries().forEach(e -> assertThat(body).doesNotContain(e.instructions()));

            // modes
            List<Map<String, Object>> modes = list(doc, "modes");
            assertThat(modes).hasSize(2);
            modes.forEach(m -> assertThat(m.keySet()).containsExactlyInAnyOrder("id", "label", "isDefault", "defaultProfileId"));
            assertThat(modes.get(0)).containsEntry("id", "OFFICIAL").containsEntry("label", "Official / Briefing")
                .containsEntry("isDefault", true).containsEntry("defaultProfileId", "official-briefing");
            assertThat(modes.get(1)).containsEntry("id", "NARRATIVE").containsEntry("label", "Narrative")
                .containsEntry("isDefault", false).containsEntry("defaultProfileId", "neutral-narrative");

            // profiles: count, order, uniqueness, key sets, values byte-identical to the file
            List<Map<String, Object>> profiles = list(doc, "profiles");
            List<StyleFixtures.Entry> expected = s.catalogueOrder();
            assertThat(profiles).hasSize(s.entries().size());
            assertThat(profiles.stream().map(p -> p.get("id")).toList()).containsExactlyElementsOf(expected.stream().map(StyleFixtures.Entry::id).toList());
            assertThat(profiles.stream().map(p -> p.get("id")).distinct().count()).isEqualTo(profiles.size());
            assertThat(profiles.get(0)).containsEntry("id", "official-briefing");
            for (int i = 0; i < expected.size(); i++) {
                Map<String, Object> p = profiles.get(i);
                StyleFixtures.Entry e = expected.get(i);
                assertThat(p.keySet()).containsExactlyInAnyOrder("id", "mode", "label", "characteristics", "isDefault", "isModeDefault");
                assertThat(p).containsEntry("mode", e.mode()).containsEntry("label", e.label()).containsEntry("characteristics", e.characteristics());
                assertThat(p.get("isDefault")).isEqualTo(e.id().equals("official-briefing"));
                assertThat(p.get("isModeDefault")).isEqualTo(e.id().equals("official-briefing") || e.id().equals("neutral-narrative"));
            }
            assertThat(profiles.stream().filter(p -> Boolean.TRUE.equals(p.get("isDefault"))).count()).isEqualTo(1);
            for (String mode : List.of("OFFICIAL", "NARRATIVE")) {
                assertThat(profiles.stream().filter(p -> p.get("mode").equals(mode) && Boolean.TRUE.equals(p.get("isModeDefault"))).count())
                    .as("isModeDefault profiles of %s", mode).isEqualTo(1);
            }
            assertThat(profiles.stream().map(p -> (String) p.get("mode")).collect(java.util.stream.Collectors.toSet()))
                .isSubsetOf(Set.of("OFFICIAL", "NARRATIVE"));
            assertThat(profiles.stream().filter(p -> p.get("mode").equals("OFFICIAL")).count()).isEqualTo(1);

            // each mode's default profile is listed and belongs to that mode
            for (Map<String, Object> m : modes) {
                assertThat(profiles).anySatisfy(p -> assertThat(p).containsEntry("id", m.get("defaultProfileId")).containsEntry("mode", m.get("id")));
            }

            // NARRATIVE order = file order
            assertThat(profiles.stream().filter(p -> p.get("mode").equals("NARRATIVE")).map(p -> p.get("id")).toList())
                .containsExactlyElementsOf(s.entries().stream().filter(e -> e.mode().equals("NARRATIVE")).map(StyleFixtures.Entry::id).toList());

            // languages
            List<Map<String, Object>> languages = list(doc, "languages");
            assertThat(languages).hasSize(2);
            languages.forEach(l -> assertThat(l.keySet()).containsExactlyInAnyOrder("code", "label", "isDefault"));
            assertThat(languages.get(0)).containsEntry("code", "en").containsEntry("label", "English").containsEntry("isDefault", true);
            assertThat(languages.get(1)).containsEntry("code", "uk").containsEntry("label", "Ukrainian").containsEntry("isDefault", false);
        });
    }

    // @trace FR-62
    @ParameterizedTest(name = "domain API: {0}")
    @MethodSource("scenarioArgs")
    void domainApiListsProfilesInCatalogueOrderAndFindsEachById(Scenario s) {
        runnerFor(s).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            Object styles = ctx.getBean(StyleSeam.stylesClass());
            List<StyleSeam.Profile> profiles = StyleSeam.profiles(styles);
            List<StyleFixtures.Entry> expected = s.catalogueOrder();
            assertThat(profiles).hasSize(expected.size());
            for (int i = 0; i < expected.size(); i++) {
                StyleFixtures.Entry e = expected.get(i);
                assertThat(profiles.get(i)).isEqualTo(new StyleSeam.Profile(e.id(), e.mode(), e.label(), e.characteristics(), e.instructions()));
                assertThat(StyleSeam.find(styles, e.id())).contains(profiles.get(i));
            }
        });
    }

    static Stream<StyleFixtures.Entry> shippedEntries() {
        return SHIPPED.stream();
    }

    // @trace FR-62
    @ParameterizedTest(name = "shipped profile {0}")
    @MethodSource("shippedEntries")
    void shippedFileCarriesEveryFieldOfEveryProfileVerbatim(StyleFixtures.Entry e) {
        StyleSeam.runner(null).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(StyleSeam.find(ctx.getBean(StyleSeam.stylesClass()), e.id()))
                .contains(new StyleSeam.Profile(e.id(), e.mode(), e.label(), e.characteristics(), e.instructions()));
        });
    }

    // @trace FR-62
    @ParameterizedTest(name = "find [{0}] is empty")
    @NullAndEmptySource
    @ValueSource(strings = {"Kafka", "KAFKA", " kafka", "kafka ", "dickens", "unknown", "official", "neutral"})
    void findIsExactCaseSensitiveAndEmptyForUnknownIds(String id) {
        StyleSeam.runner(null).run(ctx -> {
            assertThat(ctx).hasNotFailed();
            assertThat(StyleSeam.find(ctx.getBean(StyleSeam.stylesClass()), id)).isEmpty();
        });
    }

    // @trace FR-62
    @Test
    void catalogueOrderExampleFromTheSpec() {
        // file [neutral-narrative, kafka, official-briefing, poe] is listed official-briefing, neutral-narrative, kafka, poe
        Scenario s = scenarios().stream().filter(x -> x.name().startsWith("reordered")).findFirst().orElseThrow();
        runnerFor(s).run(ctx -> {
            String body = body(StyleSeam.mvc(ctx));
            assertThat(JsonPath.<List<String>>read(body, "$.profiles[*].id"))
                .containsExactly("official-briefing", "neutral-narrative", "kafka", "poe");
            assertThat(StyleSeam.profiles(ctx.getBean(StyleSeam.stylesClass())).stream().map(StyleSeam.Profile::id).toList())
                .containsExactly("official-briefing", "neutral-narrative", "kafka", "poe");
        });
    }

    // @trace FR-62
    @Test
    void aProfileAddedToTheFileIsListedAfterARestartAndFindableWithItsInstructions() throws Exception {
        List<StyleFixtures.Entry> withDickens = new ArrayList<>(SHIPPED);
        withDickens.add(DICKENS);
        Path f = write(dir, "styles.json", fileOf(SHIPPED));
        StyleSeam.runner(location(f)).run(ctx -> {
            assertThat(JsonPath.<List<String>>read(body(StyleSeam.mvc(ctx)), "$.profiles[*].id")).hasSize(9).doesNotContain("dickens");
            assertThat(StyleSeam.find(ctx.getBean(StyleSeam.stylesClass()), "dickens")).isEmpty();
        });
        write(dir, "styles.json", fileOf(withDickens));
        StyleSeam.runner(location(f)).run(ctx -> {
            String body = body(StyleSeam.mvc(ctx));
            assertThat(JsonPath.<List<String>>read(body, "$.profiles[*].id")).hasSize(10).last().isEqualTo("dickens");
            Map<String, Object> last = list(doc(body), "profiles").get(9);
            assertThat(last).containsEntry("label", "Narrative — inspired by Charles Dickens")
                .containsEntry("mode", "NARRATIVE").containsEntry("isDefault", false).containsEntry("isModeDefault", false);
            assertThat(StyleSeam.find(ctx.getBean(StyleSeam.stylesClass()), "dickens").orElseThrow().instructions())
                .isEqualTo("Evoke Victorian social observation with vivid characters and moral contrast.");
        });
    }

    // @trace FR-62
    @Test
    void theFileIsReadOnlyAtStartupSoChangingOrDeletingItDoesNotChangeTheAnswer() {
        Path f = write(dir, "styles.json", fileOf(SHIPPED));
        StyleSeam.runner(location(f)).run(ctx -> {
            MockMvc mvc = StyleSeam.mvc(ctx);
            String before = body(mvc);
            Object styles = ctx.getBean(StyleSeam.stylesClass());
            List<StyleSeam.Profile> profilesBefore = StyleSeam.profiles(styles);

            Files.writeString(f, "this is not json any more", StandardCharsets.UTF_8);
            assertThat(body(mvc)).isEqualTo(before);
            assertThat(StyleSeam.profiles(styles)).isEqualTo(profilesBefore);

            Files.writeString(f, fileOf(List.of(shipped("official-briefing"), shipped("neutral-narrative"))), StandardCharsets.UTF_8);
            assertThat(body(mvc)).isEqualTo(before);

            Files.delete(f);
            assertThat(body(mvc)).isEqualTo(before);
            assertThat(StyleSeam.find(styles, "kafka")).isPresent();
            assertThat(profilesBefore).hasSize(9);
        });
    }

    // @trace FR-62
    @Test
    void repeatedCallsAnswerTheSameBody() {
        StyleSeam.runner(null).run(ctx -> {
            MockMvc mvc = StyleSeam.mvc(ctx);
            String first = body(mvc);
            assertThat(body(mvc)).isEqualTo(first);
            assertThat(body(mvc)).isEqualTo(first);
        });
    }

    // @trace FR-62
    @Test
    void unexpectedFailureWhileAnsweringIsAnInternalErrorWithoutInternals() {
        @SuppressWarnings("unchecked")
        Class<Object> type = (Class<Object>) (Class<?>) StyleSeam.stylesClass();
        Object failing = Mockito.mock(type, inv -> {
            if (inv.getMethod().getDeclaringClass() == Object.class) return Mockito.RETURNS_DEFAULTS.answer(inv);
            throw new RuntimeException("boom-secret-detail");
        });
        new ApplicationContextRunner().withUserConfiguration(StyleSeam.controllerClass()).withBean(type, () -> failing).run(ctx -> {
            String body = StyleSeam.mvc(ctx).perform(get("/api/writing-styles"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.message").value("Something went wrong — try again"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
            assertThat(doc(body).keySet()).containsExactlyInAnyOrder("code", "message");
            assertThat(body).doesNotContain("boom-secret-detail").doesNotContain("RuntimeException").doesNotContain("com.oracul");
        });
    }
}
