package com.oracul.app.research;

import static com.oracul.app.research.RankingHarness.baseProfile;
import static com.oracul.app.research.RankingHarness.bright;
import static com.oracul.app.research.RankingHarness.dark;
import static com.oracul.app.research.RankingHarness.ev;
import static com.oracul.app.research.RankingHarness.g82;
import static com.oracul.app.research.RankingHarness.id;
import static com.oracul.app.research.RankingHarness.idRange;
import static com.oracul.app.research.RankingHarness.ids;
import static com.oracul.app.research.RankingHarness.profile;
import static com.oracul.app.research.RankingHarness.profileA;
import static com.oracul.app.research.RankingHarness.props;
import static com.oracul.app.research.RankingHarness.select;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.EvidenceSection;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.research.RankingHarness.Fx;
import com.oracul.app.research.RankingHarness.Selection;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * research-pipeline.md "Slice 07_evidence-pack" FR-17: pure tests of {@code EvidenceSelector} (rows S1-S15) on the
 * fixture G82 (82 events already in rank order).
 */
// @trace FR-17
class EvidenceSelectorTest {

    private static Selection selectA(Fx fx) {
        return select(props(), fx.events, fx.sources, profileA());
    }

    private static List<String> evidenceIds(List<NormalizedEvent> events) {
        List<String> out = new ArrayList<>();
        for (NormalizedEvent e : events) {
            assertThat(e.getSelection()).as(e.getId() + " selection").isNotNull();
            out.add(e.getSelection().getEvidenceId());
        }
        return out;
    }

    private static List<String> eIds(int from, int to) {
        List<String> out = new ArrayList<>();
        for (int n = from; n <= to; n++) out.add(String.format("E%03d", n));
        return out;
    }

    // S1
    @Test
    void g82SelectsTenCoreTenSupportingFiveCounterSignals() {
        Fx fx = g82();
        Selection s = selectA(fx);
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
        assertThat(ids(s.supporting())).isEqualTo(idRange(11, 20));
        assertThat(ids(s.counterSignals())).isEqualTo(idRange(71, 75));
        assertThat(evidenceIds(s.core())).isEqualTo(eIds(1, 10));
        assertThat(evidenceIds(s.supporting())).isEqualTo(eIds(11, 20));
        assertThat(evidenceIds(s.counterSignals())).isEqualTo(eIds(21, 25));
        assertThat(s.core()).allSatisfy(e -> assertThat(e.getSelection().getSection()).isEqualTo(EvidenceSection.CORE));
        assertThat(s.supporting()).allSatisfy(e -> assertThat(e.getSelection().getSection()).isEqualTo(EvidenceSection.SUPPORTING));
        assertThat(s.counterSignals()).allSatisfy(e -> assertThat(e.getSelection().getSection()).isEqualTo(EvidenceSection.COUNTER_SIGNAL));
        assertThat(s.all()).hasSize(25);
    }

    @Test
    void eventsThatAreNotSelectedAreLeftUntouched() {
        Fx fx = g82();
        Selection s = selectA(fx);
        List<String> picked = s.allIds();
        for (NormalizedEvent e : fx.events) {
            if (!picked.contains(e.getId())) assertThat(e.getSelection()).as(e.getId()).isNull();
        }
    }

    // S2
    @Test
    void aSingleBrightEventIsTheCounterSignalEvenWhenItRanksLast() {
        Fx fx = g82();
        for (int n = 71; n <= 81; n++) dark(ev(fx, n));
        Selection s = selectA(fx);
        assertThat(ids(s.counterSignals())).containsExactly("EV082");
        assertThat(evidenceIds(s.counterSignals())).containsExactly("E021");
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
        assertThat(ids(s.supporting())).isEqualTo(idRange(11, 20));
    }

    // S3
    @Test
    void entityCapAllowsTwoPerPrimaryEntityCaseAndSpaceInsensitive() {
        Fx fx = g82();
        String[] variants = {"Pandemic Corp", "pandemic corp ", " PANDEMIC CORP", "Pandemic  Corp".replace("  ", " "), "pandemic corp", "Pandemic Corp "};
        for (int n = 1; n <= 6; n++) ev(fx, n).setEntities(new ArrayList<>(List.of(variants[n - 1], "Other " + n)));
        Selection s = selectA(fx);
        assertThat(ids(s.core())).isEqualTo(List.of("EV001", "EV002", "EV007", "EV008", "EV009", "EV010", "EV011", "EV012", "EV013", "EV014"));
        assertThat(s.allIds()).doesNotContain("EV003", "EV004", "EV005", "EV006");
    }

    @Test
    void eventsWithoutEntitiesAreNotEntityCapped() {
        Fx fx = g82();
        for (int n = 1; n <= 12; n++) ev(fx, n).setEntities(new ArrayList<>());
        Selection s = selectA(fx);
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
    }

    // S4
    @Test
    void publisherCapAllowsThreePerPublisherIgnoringCase() {
        Fx fx = g82();
        String[] names = {"Stub Site", "stub site", " STUB SITE ", "Stub site", "sTUB sITE"};
        for (int n = 1; n <= 5; n++) fx.sources.get("S" + id(n).substring(2)).setPublisher(names[n - 1]);
        Selection s = selectA(fx);
        assertThat(s.allIds()).contains("EV001", "EV002", "EV003").doesNotContain("EV004", "EV005");
    }

    @Test
    void publisherOfAnEventIsItsHighestQualitySource() {
        Fx fx = g82();
        // EV004 reports through S004 (Pub 4, 0.85) and S101 ("Pub 1", 0.99): its primary publisher is Pub 1
        NormalizedEvent e4 = ev(fx, 4);
        e4.setSourceIds(new ArrayList<>(List.of("S004", "S101")));
        fx.sources.put("S101", RankingHarness.source("S101", "Pub 1", "major", 0.99));
        NormalizedEvent e2 = ev(fx, 2);
        e2.setSourceIds(new ArrayList<>(List.of("S002", "S102")));
        fx.sources.put("S102", RankingHarness.source("S102", "Pub 1", "major", 0.99));
        NormalizedEvent e3 = ev(fx, 3);
        e3.setSourceIds(new ArrayList<>(List.of("S003", "S103")));
        fx.sources.put("S103", RankingHarness.source("S103", "Pub 1", "major", 0.99));
        // EV001 (Pub 1), EV002, EV003, EV004 all count towards Pub 1: the fourth is skipped
        Selection s = selectA(fx);
        assertThat(s.allIds()).contains("EV001", "EV002", "EV003").doesNotContain("EV004");
    }

    // S5
    @Test
    void geographyCapAllowsSixPerRegionButNotGlobal() {
        Fx france = g82();
        for (int n = 1; n <= 8; n++) ev(france, n).getClassification().setGeography(n % 2 == 0 ? "France" : " france ");
        Selection s = selectA(france);
        assertThat(s.allIds()).contains("EV001", "EV002", "EV003", "EV004", "EV005", "EV006").doesNotContain("EV007", "EV008");

        Fx global = g82();
        for (int n = 1; n <= 8; n++) ev(global, n).getClassification().setGeography("global");
        assertThat(selectA(global).allIds()).contains(idRange(1, 8).toArray(String[]::new));
    }

    // S6
    @Test
    void categoryCapLimitsOneCategoryToTenWhenThereAreAtLeastThreeCategories() {
        Fx fx = g82();
        for (int n = 1; n <= 12; n++) ev(fx, n).setCategory(n % 2 == 0 ? "health" : "Health");
        Selection s = selectA(fx);
        long health = s.all().stream().filter(e -> "health".equalsIgnoreCase(e.getCategory())).count();
        assertThat(health).isEqualTo(10);
        assertThat(s.all()).hasSize(25);
    }

    @Test
    void noCategoryCapWhenFewerThanThreeCategoriesExist() {
        Fx fx = g82();
        for (NormalizedEvent e : fx.events) e.setCategory("health");
        Selection s = selectA(fx);
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
        assertThat(ids(s.supporting())).isEqualTo(idRange(11, 20));
        assertThat(ids(s.counterSignals())).isEqualTo(idRange(71, 75));
    }

    // S7
    @Test
    void theCounterSignalIsTheOnlyCapException() {
        Fx fx = g82();
        for (int n = 71; n <= 82; n++) fx.sources.get("S" + id(n).substring(2)).setPublisher("Pub 1");
        Selection s = select(props("maxPerPublisher", 1), fx.events, fx.sources, profileA());
        assertThat(ids(s.counterSignals())).containsExactly("EV071");
        assertThat(ids(s.core())).as("Pub 1 is used by EV001: no other Pub 1 event in core").doesNotContain("EV071");
    }

    // S8
    @Test
    void anOptimisticProfileTakesDarkEventsAsCounterSignals() {
        Fx fx = g82();
        Selection s = select(props(), fx.events, fx.sources, profile(0.2, 0.9, 0.8, HorizonCode._5Y));
        assertThat(ids(s.core())).isEqualTo(idRange(71, 80));
        assertThat(ids(s.supporting())).isEqualTo(idRange(81, 82));
        assertThat(ids(s.counterSignals())).isEqualTo(idRange(1, 5));
        assertThat(evidenceIds(s.counterSignals())).isEqualTo(eIds(13, 17));
    }

    // S9
    @Test
    void aBalancedProfileTakesTheMinorityToneAsCounterSignals() {
        Fx fx = g82();
        Selection s = select(props(), fx.events, fx.sources, profile(0.5, 0.5, 0.8, HorizonCode._5Y));
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
        assertThat(ids(s.supporting())).isEqualTo(idRange(11, 20));
        assertThat(ids(s.counterSignals())).isEqualTo(idRange(71, 75));
    }

    // S10
    @Test
    void aBalancedProfileWithEvenToneInCoreHasNoCounterSignals() {
        Fx fx = g82();
        for (int n = 1; n <= 5; n++) dark(ev(fx, n));
        for (int n = 6; n <= 10; n++) bright(ev(fx, n));
        Selection s = select(props(), fx.events, fx.sources, profile(0.5, 0.5, 0.8, HorizonCode._5Y));
        assertThat(ids(s.core())).isEqualTo(idRange(1, 10));
        assertThat(s.counterSignals()).isEmpty();
        assertThat(ids(s.supporting())).isEqualTo(idRange(11, 20));
    }

    // S11
    @Test
    void excludedEventsAreNeverSelected() {
        Fx fx = g82();
        ev(fx, 1).setExcludedReason("LOW_SOURCE_QUALITY");
        NormalizedEvent failed = ev(fx, 2);
        failed.setClassification(null);
        failed.setRanking(null);
        failed.setExcludedReason("CLASSIFICATION_FAILED");
        Selection s = selectA(fx);
        assertThat(s.allIds()).doesNotContain("EV001", "EV002");
        assertThat(ids(s.core())).isEqualTo(idRange(3, 12));
    }

    // S12
    @Test
    void fewEligibleEventsFillOnlyCore() {
        Fx fx = new Fx();
        for (int n = 1; n <= 3; n++) {
            NormalizedEvent e = fx.base(id(n), 0.8, 0.1);
            e.setEntities(new ArrayList<>(List.of("Entity " + n)));
            e.setRanking(RankingHarness.ranking(0.9 - n * 0.001));
            fx.sources.get("S00" + n).setPublisher("Pub " + n);
        }
        Selection s = selectA(fx);
        assertThat(ids(s.core())).containsExactly("EV001", "EV002", "EV003");
        assertThat(s.supporting()).isEmpty();
        assertThat(s.counterSignals()).isEmpty();
        assertThat(evidenceIds(s.core())).containsExactly("E001", "E002", "E003");
    }

    // S13
    @Test
    void noEventsGiveEmptySections() {
        Selection s = select(props(), new ArrayList<>(), new java.util.LinkedHashMap<>(), baseProfile());
        assertThat(s.core()).isEmpty();
        assertThat(s.supporting()).isEmpty();
        assertThat(s.counterSignals()).isEmpty();
    }

    // S14
    @Test
    void anOpportunityMarginOfExactlyPointTwoIsBright() {
        Fx fx = new Fx();
        for (int n = 1; n <= 3; n++) {
            NormalizedEvent e = fx.base(id(n), 0.8, 0.1);
            e.setEntities(new ArrayList<>(List.of("Entity " + n)));
            e.setRanking(RankingHarness.ranking(0.9 - n * 0.001));
            fx.sources.get("S00" + n).setPublisher("Pub " + n);
        }
        NormalizedEvent edge = ev(fx, 3);
        edge.getClassification().setOpportunity(0.6);
        edge.getClassification().setRisk(0.4);
        Selection s = selectA(fx);
        assertThat(ids(s.counterSignals())).containsExactly("EV003");
        assertThat(ids(s.core())).containsExactly("EV001", "EV002");
        assertThat(evidenceIds(s.counterSignals())).containsExactly("E003");
    }

    @Test
    void aRiskMarginOfExactlyPointTwoIsDark() {
        Fx fx = new Fx();
        for (int n = 1; n <= 3; n++) {
            NormalizedEvent e = fx.base(id(n), 0.1, 0.8);
            e.setEntities(new ArrayList<>(List.of("Entity " + n)));
            e.setRanking(RankingHarness.ranking(0.9 - n * 0.001));
            fx.sources.get("S00" + n).setPublisher("Pub " + n);
        }
        NormalizedEvent edge = ev(fx, 3);
        edge.getClassification().setRisk(0.6);
        edge.getClassification().setOpportunity(0.4);
        Selection s = select(props(), fx.events, fx.sources, profile(0.2, 0.9, 0.8, HorizonCode._5Y));
        assertThat(ids(s.counterSignals())).containsExactly("EV003");
    }

    // S15
    @Test
    void sameInputTwiceGivesIdenticalEvidenceIds() {
        Selection a = selectA(g82());
        Selection b = selectA(g82());
        assertThat(ids(a.core())).isEqualTo(ids(b.core()));
        assertThat(ids(a.supporting())).isEqualTo(ids(b.supporting()));
        assertThat(ids(a.counterSignals())).isEqualTo(ids(b.counterSignals()));
        assertThat(evidenceIds(a.all())).isEqualTo(evidenceIds(b.all()));
    }

    @Test
    void totalNeverExceedsMaxItems() {
        Fx fx = g82();
        Selection s = select(props("maxItems", 12, "core", 5, "supporting", 4, "counterSignals", 3), fx.events, fx.sources, profileA());
        assertThat(s.core()).hasSize(5);
        assertThat(s.supporting()).hasSize(4);
        assertThat(s.counterSignals()).hasSize(3);
    }
}
