package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** future-result.md "Slice 09_future-story": Datelines.format and HorizonLabels.label; phase 04: Datelines.format(d, language). */
// @trace FR-23, FR-66
class DatelinesTest {

    @ParameterizedTest
    @CsvSource({
        "2027-03-01, 'ORACUL FUTURE — March 1, 2027'",
        "2031-12-25, 'ORACUL FUTURE — December 25, 2031'",
        "2026-10-03, 'ORACUL FUTURE — October 3, 2026'",
    })
    void formatsTheDatelineWithAnEnglishMonthAndNoLeadingZero(String date, String expected) {
        assertThat(StoryHarness.dateline(LocalDate.parse(date))).isEqualTo(expected);
    }

    @Test
    void theDashIsAnEmDashWithOneSpaceOnEachSide() {
        String s = StoryHarness.dateline(LocalDate.parse("2027-03-01"));
        assertThat(s).startsWith("ORACUL FUTURE — ");
        assertThat(s.indexOf('—')).isEqualTo("ORACUL FUTURE ".length());
    }

    @ParameterizedTest
    @CsvSource({"_1D, Tomorrow", "_1W, 1 week", "_1M, 1 month", "_1Y, 1 year", "_5Y, 5 years", "_10Y, 10 years", "_20Y, 20 years"})
    void horizonLabelsFollowTheScenarioPanel(String constant, String label) throws Exception {
        Object code = HorizonCode.class.getField(constant).get(null);
        assertThat(StoryHarness.horizonLabel(code)).isEqualTo(label);
    }

    // ---- phase 04 styled-story.md FR-66: Datelines.format(d, language) -------------------------------------------

    /** The FutureStory.dateline pattern of api/openapi.yaml. */
    private static final Pattern CONTRACT_PATTERN = Pattern.compile("^ORACUL FUTURE — ((January|February|March|April|May|June|July|August|September|October|November|December) [1-9][0-9]?, [0-9]{4}|[1-9][0-9]? (січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня) [0-9]{4})$");

    /** The 1st and the last day of each of the 12 months (24 dates, year 2032 is a leap year) and days 1, 9, 10, 31. */
    static Stream<Arguments> boundaryDates() {
        List<Arguments> out = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate first = LocalDate.of(2032, month, 1);
            out.add(Arguments.of(first));
            out.add(Arguments.of(first.withDayOfMonth(first.lengthOfMonth())));
        }
        for (int day : new int[] {1, 9, 10, 31}) out.add(Arguments.of(LocalDate.of(2046, 12, day)));
        return out.stream();
    }

    // @trace FR-66
    @ParameterizedTest(name = "uk {0}")
    @MethodSource("boundaryDates")
    void ukrainianDatelineUsesTheGenitiveMonthOfTheTableAndNoLeadingZero(LocalDate d) {
        String expected = "ORACUL FUTURE — " + d.getDayOfMonth() + " " + StoryFixtures.UK_MONTHS.get(d.getMonthValue() - 1) + " " + d.getYear();
        String actual = StoryHarness.dateline(d, "uk");
        assertThat(actual).isEqualTo(expected);
        assertThat(actual).isEqualTo(StoryFixtures.ukDateline(d));
        assertThat(actual).matches("^ORACUL FUTURE — [1-9][0-9]? [а-яі]+ [0-9]{4}$");
        assertThat(actual.indexOf('—')).as("em dash with one space each side").isEqualTo("ORACUL FUTURE ".length());
        assertThat(CONTRACT_PATTERN.matcher(actual).matches()).as("contract pattern: " + actual).isTrue();
    }

    // @trace FR-66
    @ParameterizedTest(name = "en {0}")
    @MethodSource("boundaryDates")
    void englishDatelineIsIdenticalToThePhaseOneFormatterForTheSameDates(LocalDate d) {
        String phaseOne = StoryFixtures.enDateline(d);
        assertThat(StoryHarness.dateline(d, "en")).isEqualTo(phaseOne);
        assertThat(StoryHarness.dateline(d)).as("the 1-arg form stays English").isEqualTo(phaseOne);
        assertThat(CONTRACT_PATTERN.matcher(phaseOne).matches()).as("contract pattern: " + phaseOne).isTrue();
    }

    // @trace FR-66
    @Test
    void theUkrainianTableHasTheTwelveMonthsOfTheSpecInOrder() {
        for (int month = 1; month <= 12; month++) {
            LocalDate d = LocalDate.of(2030, month, 15);
            assertThat(StoryHarness.dateline(d, "uk")).isEqualTo("ORACUL FUTURE — 15 " + StoryFixtures.UK_MONTHS.get(month - 1) + " 2030");
        }
        assertThat(StoryFixtures.UK_MONTHS).containsExactly("січня", "лютого", "березня", "квітня", "травня", "червня", "липня",
            "серпня", "вересня", "жовтня", "листопада", "грудня");
        assertThat(StoryHarness.dateline(LocalDate.parse("2032-09-12"), "uk")).isEqualTo("ORACUL FUTURE — 12 вересня 2032");
        assertThat(StoryHarness.dateline(LocalDate.parse("2032-09-12"), "en")).isEqualTo("ORACUL FUTURE — September 12, 2032");
    }

    // @trace FR-66
    @Test
    void theLongestUkrainianDatelineFitsTheVarchar64Column() {
        String longest = StoryHarness.dateline(LocalDate.parse("2046-11-30"), "uk");
        assertThat(longest).isEqualTo("ORACUL FUTURE — 30 листопада 2046");
        assertThat(longest.length()).isLessThanOrEqualTo(64);
    }
}
