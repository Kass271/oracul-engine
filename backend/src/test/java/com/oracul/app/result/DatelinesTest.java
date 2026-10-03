package com.oracul.app.result;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** future-result.md "Slice 09_future-story": Datelines.format and HorizonLabels.label. */
// @trace FR-23
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
}
