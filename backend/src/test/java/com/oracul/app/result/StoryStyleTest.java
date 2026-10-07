package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.OutputSettings;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.StoryLanguage;
import com.oracul.app.api.model.WritingStyleSelection;
import com.oracul.app.reasoning.ReasoningHarness;
import com.oracul.app.style.WritingStyleProfile;
import com.oracul.app.style.WritingStyles;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * phase-04 styled-story.md FR-65 "Interfaces": StoryStyle.of(configuration, styles) — the effective style of a run
 * (profile with fallback to official-briefing, language with fallback to English) and the domain Language enum.
 */
// @trace FR-65, FR-66
class StoryStyleTest {

    private static final WritingStyles STYLES = new WritingStyles("classpath:writing-styles.json", new DefaultResourceLoader());
    private static final String OFFICIAL = "official-briefing";

    private static ScenarioConfiguration config(OutputSettings output) {
        ScenarioConfiguration c = ReasoningHarness.configA(HorizonCode._5Y);
        c.setOutput(output);
        return c;
    }

    private static OutputSettings output(WritingStyleSelection style, StoryLanguage language) {
        OutputSettings o = new OutputSettings(true, false);
        o.setWritingStyle(style);
        o.setLanguage(language);
        return o;
    }

    private static WritingStyleSelection sel(com.oracul.app.api.model.WritingMode mode, String profileId) {
        return new WritingStyleSelection(mode, profileId);
    }

    private static void assertStyle(ScenarioConfiguration cfg, String profileId, String language) {
        Object style = StoryHarness.styleOf(cfg, STYLES);
        assertThat(StoryHarness.profileOf(style).id()).as("profile").isEqualTo(profileId);
        assertThat(StoryHarness.profileOf(style)).as("the profile of the style file").isEqualTo(STYLES.find(profileId).orElseThrow());
        assertThat(StoryHarness.languageCodeOf(style)).as("language").isEqualTo(language);
    }

    // ---- the profile: every class of the "Interfaces" row --------------------------------------------------------

    static Stream<Arguments> fallbackToOfficialBriefing() {
        return Stream.of(
            Arguments.of("configuration null", null),
            Arguments.of("output null", config(null)),
            Arguments.of("writingStyle absent", config(new OutputSettings(true, false))),
            Arguments.of("writingStyle null", config(output(null, StoryLanguage.EN))),
            Arguments.of("profileId dickens (not in the file)", config(output(sel(com.oracul.app.api.model.WritingMode.NARRATIVE, "dickens"), StoryLanguage.EN))),
            Arguments.of("profileId null", config(output(sel(com.oracul.app.api.model.WritingMode.NARRATIVE, null), StoryLanguage.EN))),
            Arguments.of("profileId of another case (Kafka)", config(output(sel(com.oracul.app.api.model.WritingMode.NARRATIVE, "Kafka"), StoryLanguage.EN))));
    }

    @ParameterizedTest(name = "{0} -> official-briefing")
    @MethodSource("fallbackToOfficialBriefing")
    void aMissingOrUnknownStyleFallsBackToOfficialBriefing(String what, ScenarioConfiguration cfg) {
        assertStyle(cfg, OFFICIAL, "en");
    }

    static Stream<Arguments> shippedPairs() {
        List<Arguments> out = new ArrayList<>();
        for (WritingStyleProfile p : STYLES.profiles()) {
            out.add(Arguments.of(p.mode().name(), p.id()));
        }
        return out.stream();
    }

    @ParameterizedTest(name = "{0} / {1} -> that profile")
    @MethodSource("shippedPairs")
    void eachShippedModeAndProfileIdPairSelectsThatProfile(String mode, String profileId) {
        ScenarioConfiguration cfg = config(output(sel(com.oracul.app.api.model.WritingMode.valueOf(mode), profileId), StoryLanguage.EN));
        assertStyle(cfg, profileId, "en");
    }

    @Test
    void theNineShippedPairsAreWalked() {
        assertThat(shippedPairs().count()).isEqualTo(9);
    }

    @Test
    void theProfilesOwnModeIsTheEffectiveModeNotTheStoredOne() {
        ScenarioConfiguration cfg = config(output(sel(com.oracul.app.api.model.WritingMode.OFFICIAL, "kafka"), StoryLanguage.EN));
        Object style = StoryHarness.styleOf(cfg, STYLES);
        assertThat(StoryHarness.profileOf(style).id()).isEqualTo("kafka");
        assertThat(StoryHarness.profileOf(style).mode()).isEqualTo(com.oracul.app.style.WritingMode.NARRATIVE);
        // and the other way round
        Object other = StoryHarness.styleOf(config(output(sel(com.oracul.app.api.model.WritingMode.NARRATIVE, OFFICIAL), StoryLanguage.EN)), STYLES);
        assertThat(StoryHarness.profileOf(other).mode()).isEqualTo(com.oracul.app.style.WritingMode.OFFICIAL);
    }

    // ---- the language ------------------------------------------------------------------------------------------

    static Stream<Arguments> languages() {
        return Stream.of(
            Arguments.of("absent", null, "en"),
            Arguments.of("null", null, "en"),
            Arguments.of("en", StoryLanguage.EN, "en"),
            Arguments.of("uk", StoryLanguage.UK, "uk"));
    }

    @ParameterizedTest(name = "language {0} -> {2}")
    @MethodSource("languages")
    void theLanguageIsUkrainianOnlyForStoredUkAndEnglishOtherwise(String what, StoryLanguage stored, String expected) {
        assertStyle(config(output(sel(com.oracul.app.api.model.WritingMode.NARRATIVE, "poe"), stored)), "poe", expected);
        assertStyle(config(output(null, stored)), OFFICIAL, expected);
    }

    @Test
    void aMissingConfigurationOrOutputGivesEnglish() {
        assertStyle(null, OFFICIAL, "en");
        assertStyle(config(null), OFFICIAL, "en");
    }

    // ---- the domain Language enum --------------------------------------------------------------------------------

    @ParameterizedTest
    @CsvSource({"EN, en, English", "UK, uk, Ukrainian"})
    void theDomainLanguageHasACodeAndALabel(String constant, String code, String label) {
        Object language = StoryHarness.language(code);
        assertThat(((Enum<?>) language).name()).isEqualTo(constant);
        assertThat(StoryHarness.languageLabelOf(language)).isEqualTo(label);
        assertThat(StoryHarness.languageCodeOf(StoryHarness.style(STYLES.find(OFFICIAL).orElseThrow(), code))).isEqualTo(code);
    }
}
