package com.oracul.app.research;

import static com.oracul.app.research.RankingHarness.CUTOFF;
import static com.oracul.app.research.RankingHarness.HUMANOID;
import static com.oracul.app.research.RankingHarness.PANDEMIC;
import static com.oracul.app.research.RankingHarness.baseProfile;
import static com.oracul.app.research.RankingHarness.byId;
import static com.oracul.app.research.RankingHarness.classification;
import static com.oracul.app.research.RankingHarness.event;
import static com.oracul.app.research.RankingHarness.ids;
import static com.oracul.app.research.RankingHarness.match;
import static com.oracul.app.research.RankingHarness.profile;
import static com.oracul.app.research.RankingHarness.profileA;
import static com.oracul.app.research.RankingHarness.rank;
import static com.oracul.app.research.RankingHarness.rankingOf;
import static com.oracul.app.research.RankingHarness.source;
import static com.oracul.app.research.RankingHarness.weights;
import static com.oracul.app.research.RankingHarness.weightsOnly;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.EventRanking;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.RankingFactors;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.Trend;
import com.oracul.app.research.RankingHarness.Fx;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * research-pipeline.md "Slice 07_evidence-pack" FR-16: pure tests of {@code EventRanker} (rows R1-R13). Values are
 * the rounded (HALF_UP, 4 decimals) numbers of the spec table.
 */
// @trace FR-16
class EventRankerTest {

    private static void assertRanking(NormalizedEvent e, double relevance, double score) {
        EventRanking r = rankingOf(e);
        assertThat(r.getRelevance()).as(e.getId() + " relevance").isEqualTo(relevance);
        assertThat(r.getScore()).as(e.getId() + " score").isEqualTo(score);
    }

    private static void assertFactors(NormalizedEvent e, double topic, double wildcard, double darkness, double optimism,
                                      double recency, double sq, double impact, double trend, double cross, double realism) {
        RankingFactors f = rankingOf(e).getFactors();
        assertThat(f).as(e.getId() + " factors").isNotNull();
        assertThat(f.getTopicMatch()).as("topicMatch").isEqualTo(topic);
        assertThat(f.getWildcardMatch()).as("wildcardMatch").isEqualTo(wildcard);
        assertThat(f.getDarknessMatch()).as("darknessMatch").isEqualTo(darkness);
        assertThat(f.getOptimismMatch()).as("optimismMatch").isEqualTo(optimism);
        assertThat(f.getRecency()).as("recency").isEqualTo(recency);
        assertThat(f.getSourceQuality()).as("sourceQuality").isEqualTo(sq);
        assertThat(f.getImpact()).as("impact").isEqualTo(impact);
        assertThat(f.getTrendStrength()).as("trendStrength").isEqualTo(trend);
        assertThat(f.getCrossTopic()).as("crossTopic").isEqualTo(cross);
        assertThat(f.getRealismCompatibility()).as("realismCompatibility").isEqualTo(realism);
    }

    // R1
    @Test
    void darkProfileRanksTheRiskEventAboveTheOpportunityEvent() {
        Fx fx = new Fx();
        fx.base("EV001", 0.1, 0.9); // O: opportunity heavy
        fx.base("EV002", 0.9, 0.1); // H: risk heavy
        List<NormalizedEvent> out = rank(fx, baseProfile());
        assertThat(ids(out)).containsExactly("EV002", "EV001");
        assertRanking(byId(out, "EV002"), 0.4849, 0.4303);
        assertRanking(byId(out, "EV001"), 0.4029, 0.3576);
    }

    // R2
    @Test
    void lowSourceQualityLowersTheScoreAndExcludesBelowTheMinimum() {
        Fx fx = new Fx();
        NormalizedEvent l = fx.base("EV001", 0.9, 0.1);
        fx.setQuality(l, 0.2);
        NormalizedEvent q = fx.base("EV002", 0.9, 0.1);
        fx.setQuality(q, 0.9);
        List<NormalizedEvent> out = rank(fx, baseProfile());
        assertThat(ids(out)).containsExactly("EV002", "EV001");
        assertRanking(byId(out, "EV001"), 0.4215, 0.1686);
        assertThat(byId(out, "EV001").getExcludedReason()).isEqualTo("LOW_SOURCE_QUALITY");
        assertRanking(byId(out, "EV002"), 0.4898, 0.4530);
        assertThat(byId(out, "EV002").getExcludedReason()).isNull();
    }

    // R3 / R4
    private static List<NormalizedEvent> realismCase(double realism) {
        Fx fx = new Fx();
        NormalizedEvent e = fx.base("EV001");
        e.getClassification().setNovelty(0.2);
        e.setSourceIds(new ArrayList<>(List.of("S001", "S101", "S102")));
        for (String id : List.of("S101", "S102")) fx.sources.put(id, source(id, "Stub Site", "major", 0.85));
        NormalizedEvent s = fx.base("EV002");
        s.getClassification().setNovelty(0.9);
        s.getClassification().setTrend(Trend.EMERGING);
        return rank(fx, profile(0.5, 0.5, realism, HorizonCode._5Y));
    }

    @Test
    void realismTenPrefersTheCorroboratedEstablishedEvent() {
        List<NormalizedEvent> out = realismCase(1.0);
        assertRanking(byId(out, "EV001"), 0.4724, 0.4192);
        assertRanking(byId(out, "EV002"), 0.4008, 0.3557);
        assertThat(rankingOf(byId(out, "EV001")).getFactors().getRealismCompatibility()).isEqualTo(1.0);
        assertThat(rankingOf(byId(out, "EV002")).getFactors().getRealismCompatibility()).isEqualTo(0.4167);
        assertThat(ids(out)).containsExactly("EV001", "EV002");
    }

    @Test
    void realismTwoPrefersTheNovelEmergingEvent() {
        List<NormalizedEvent> out = realismCase(0.2);
        assertRanking(byId(out, "EV002"), 0.4385, 0.3892);
        assertRanking(byId(out, "EV001"), 0.4099, 0.3638);
        assertThat(rankingOf(byId(out, "EV001")).getFactors().getRealismCompatibility()).isEqualTo(0.36);
        assertThat(rankingOf(byId(out, "EV002")).getFactors().getRealismCompatibility()).isEqualTo(0.8033);
        assertThat(ids(out)).containsExactly("EV002", "EV001");
    }

    // R5 / R6
    private static Fx v4Fixture() {
        Fx fx = new Fx();
        Source s1 = source("S001", "Stub Site", "biology-new-pandemic", 0.95);
        Source s2 = source("S002", "Stub Site", "biology-new-pandemic", 0.85);
        Source s3 = source("S003", "Stub Site", "biology-new-pandemic", 0.6);
        Source s4 = source("S004", "Stub Site", "robotics-humanoid-boom", 0.85);
        for (Source s : List.of(s1, s2, s3, s4)) fx.sources.put(s.getId(), s);
        fx.events.add(event("EV001", "2026-10-01", "health", List.of("WHO", "Pandemic vaccine"), List.of("S001", "S002", "S003"), 0.9,
            classification("health", 0.2, 0.8, 0.7, 0.6, Trend.EMERGING, "global", 0.95,
                match("biology-new-pandemic", 0.9), match("robotics-humanoid-boom", 0.0))));
        fx.events.add(event("EV002", "2026-10-01", "labour", List.of("Dock workers"), List.of("S004"), 0.7,
            classification("labour", 0.6, 0.3, 0.5, 0.4, Trend.EMERGING, "Europe", 0.85,
                match("biology-new-pandemic", 0.0), match("robotics-humanoid-boom", 0.8))));
        return fx;
    }

    @Test
    void workedExampleOfTheVaccineEvent() {
        List<NormalizedEvent> out = rank(v4Fixture(), profileA());
        NormalizedEvent e = byId(out, "EV001");
        assertFactors(e, 1.0, 0.72, 0.18, 0.16, 0.9889, 0.95, 0.7, 0.7, 0.0, 0.72);
        assertRanking(e, 0.5904, 0.5683);
        assertThat(rankingOf(e).getReliability()).isEqualTo(0.95);
    }

    @Test
    void workedExampleOfTheDockWorkersEvent() {
        List<NormalizedEvent> out = rank(v4Fixture(), profileA());
        NormalizedEvent e = byId(out, "EV002");
        assertFactors(e, 1.0, 0.48, 0.54, 0.06, 0.9889, 0.85, 0.5, 0.7, 0.0, 0.4133);
        assertRanking(e, 0.5341, 0.4741);
        assertThat(rankingOf(e).getReliability()).isEqualTo(0.85);
    }

    // R7
    @Test
    void relevanceFollowsTheWeights() {
        Fx fx = new Fx();
        fx.base("EV001", 0.1, 0.9);
        fx.base("EV002", 0.9, 0.1);
        List<NormalizedEvent> dark = rank(weights("topicMatch", 0, "wildcardMatch", 0, "darknessMatch", 1, "optimismMatch", 0,
            "recency", 0, "sourceQuality", 0, "impact", 0, "trendStrength", 0, "crossTopic", 0, "realismCompatibility", 0),
            0.30, fx.events, fx.sources, baseProfile(), CUTOFF);
        assertThat(rankingOf(byId(dark, "EV002")).getRelevance()).isEqualTo(rankingOf(byId(dark, "EV002")).getFactors().getDarknessMatch());
        assertThat(rankingOf(byId(dark, "EV002")).getRelevance()).isEqualTo(0.81);
        assertThat(ids(dark)).containsExactly("EV002", "EV001");

        Fx fx2 = new Fx();
        fx2.base("EV001", 0.1, 0.9);
        fx2.base("EV002", 0.9, 0.1);
        List<NormalizedEvent> bright = rank(weightsOnly("optimismMatch", 1), 0.30, fx2.events, fx2.sources, baseProfile(), CUTOFF);
        assertThat(rankingOf(byId(bright, "EV001")).getRelevance()).isEqualTo(0.18);
        assertThat(ids(bright)).containsExactly("EV001", "EV002");
    }

    @Test
    void weightsOnlyDarknessZeroesEveryOtherInfluence() {
        Fx fx = new Fx();
        fx.base("EV001", 0.1, 0.9);
        fx.base("EV002", 0.9, 0.1);
        List<NormalizedEvent> out = rank(weightsOnly("darknessMatch", 1), 0.30, fx.events, fx.sources, baseProfile(), CUTOFF);
        assertThat(ids(out)).containsExactly("EV002", "EV001");
        assertThat(rankingOf(byId(out, "EV002")).getRelevance()).isEqualTo(0.81);
        assertThat(rankingOf(byId(out, "EV001")).getRelevance()).isEqualTo(0.09);
    }

    // R8
    @Test
    void tieOnScoreGoesToTheHigherSourceQuality() {
        Fx fx = new Fx();
        NormalizedEvent y = fx.base("EV001");
        fx.setQuality(y, 0.5);
        y.getClassification().setImpact(0.8);
        NormalizedEvent x = fx.base("EV002");
        fx.setQuality(x, 1.0);
        x.getClassification().setImpact(0.5);
        List<NormalizedEvent> out = rank(weightsOnly("impact", 1), 0.30, fx.events, fx.sources, baseProfile(), CUTOFF);
        assertThat(rankingOf(byId(out, "EV001")).getScore()).isEqualTo(0.5);
        assertThat(rankingOf(byId(out, "EV002")).getScore()).isEqualTo(0.5);
        assertThat(ids(out)).containsExactly("EV002", "EV001");
    }

    @Test
    void tieOnScoreAndQualityGoesToTheLaterDateAndNoDateIsLast() {
        Fx fx = new Fx();
        NormalizedEvent none = fx.base("EV001");
        none.setDate(null);
        NormalizedEvent early = fx.base("EV002");
        early.setDate(java.time.LocalDate.parse("2026-09-01"));
        NormalizedEvent late = fx.base("EV003");
        late.setDate(java.time.LocalDate.parse("2026-09-20"));
        List<NormalizedEvent> out = rank(weightsOnly("sourceQuality", 1), 0.30, fx.events, fx.sources, baseProfile(), CUTOFF);
        assertThat(ids(out)).containsExactly("EV003", "EV002", "EV001");
    }

    @Test
    void fullTieGoesToTheLowerId() {
        Fx fx = new Fx();
        fx.base("EV005");
        fx.base("EV003");
        fx.base("EV004");
        List<NormalizedEvent> out = rank(weightsOnly("sourceQuality", 1), 0.30, fx.events, fx.sources, baseProfile(), CUTOFF);
        assertThat(ids(out)).containsExactly("EV003", "EV004", "EV005");
    }

    // R9
    @Test
    void anEventWithoutClassificationHasNoRankingAndComesAfterEveryRankedEvent() {
        Fx fx = new Fx();
        NormalizedEvent bad = fx.base("EV001");
        bad.setClassification(null);
        bad.setExcludedReason("CLASSIFICATION_FAILED");
        fx.base("EV002", 0.9, 0.1);
        fx.base("EV003", 0.1, 0.9);
        List<NormalizedEvent> out = rank(fx, baseProfile());
        assertThat(ids(out)).containsExactly("EV002", "EV003", "EV001");
        NormalizedEvent last = byId(out, "EV001");
        assertThat(last.getRanking()).isNull();
        assertThat(last.getExcludedReason()).isEqualTo("CLASSIFICATION_FAILED");
        assertThat(byId(out, "EV002").getExcludedReason()).isNull();
    }

    @Test
    void unrankedEventsAreOrderedByIdAfterTheRankedOnes() {
        Fx fx = new Fx();
        for (String id : List.of("EV004", "EV002")) {
            NormalizedEvent bad = fx.base(id);
            bad.setClassification(null);
            bad.setExcludedReason("CLASSIFICATION_FAILED");
        }
        fx.base("EV003");
        assertThat(ids(rank(fx, baseProfile()))).containsExactly("EV003", "EV002", "EV004");
    }

    // R10
    @Test
    void recencyIsOneAfterTheCutoffZeroBeyondTheTimespanAndZeroWithoutDate() {
        Fx fx = new Fx();
        fx.base("EV001").setDate(java.time.LocalDate.parse("2026-12-01"));
        fx.base("EV002").setDate(java.time.LocalDate.parse("2026-01-01"));
        fx.base("EV003").setDate(null);
        List<NormalizedEvent> out = rank(fx, baseProfile());
        assertThat(rankingOf(byId(out, "EV001")).getFactors().getRecency()).isEqualTo(1.0);
        assertThat(rankingOf(byId(out, "EV002")).getFactors().getRecency()).isEqualTo(0.0);
        assertThat(rankingOf(byId(out, "EV003")).getFactors().getRecency()).isEqualTo(0.0);
    }

    @Test
    void recencyTimespanDependsOnTheHorizon() {
        Fx fx = new Fx();
        fx.base("EV001").setDate(java.time.LocalDate.parse("2026-09-25")); // 7 days before the cutoff date
        assertThat(rankingOf(rank(fx, profile(0.9, 0.2, 0.8, HorizonCode._1W)).get(0)).getFactors().getRecency()).isEqualTo(0.0);
        assertThat(rankingOf(rank(fx, profile(0.9, 0.2, 0.8, HorizonCode._1M)).get(0)).getFactors().getRecency()).isEqualTo(0.5);
        assertThat(rankingOf(rank(fx, profile(0.9, 0.2, 0.8, HorizonCode._20Y)).get(0)).getFactors().getRecency()).isEqualTo(0.9222);
    }

    // R11
    @Test
    void wildcardMatchIsTheBestScoreTimesWeightAndCrossTopicCountsTopicsAtHalf() {
        Fx fx = new Fx();
        NormalizedEvent e = fx.base("EV001");
        e.getClassification().setWildcardMatches(new ArrayList<>(List.of(
            match("biology-new-pandemic", 0.9), match("robotics-humanoid-boom", 0.5))));
        List<NormalizedEvent> out = rank(fx, profile(0.9, 0.2, 0.8, HorizonCode._5Y, PANDEMIC, HUMANOID));
        RankingFactors f = rankingOf(out.get(0)).getFactors();
        assertThat(f.getWildcardMatch()).isEqualTo(0.72);
        assertThat(f.getCrossTopic()).isEqualTo(0.5);

        Fx fx3 = new Fx();
        ResearchTopic third = new ResearchTopic("energy-fusion", "Fusion", "energy", 0.5, false);
        fx3.base("EV001").getClassification().setWildcardMatches(new ArrayList<>(List.of(
            match("biology-new-pandemic", 0.9), match("robotics-humanoid-boom", 0.9), match("energy-fusion", 0.9))));
        RankingFactors f3 = rankingOf(rank(fx3, profile(0.9, 0.2, 0.8, HorizonCode._5Y, PANDEMIC, HUMANOID, third)).get(0)).getFactors();
        assertThat(f3.getCrossTopic()).isEqualTo(1.0);

        Fx fx0 = new Fx();
        fx0.base("EV001");
        RankingFactors f0 = rankingOf(rank(fx0, baseProfile()).get(0)).getFactors();
        assertThat(f0.getWildcardMatch()).as("0 without topics").isEqualTo(0.0);
        assertThat(f0.getCrossTopic()).isEqualTo(0.0);
    }

    // R12
    @Test
    void topicMatchRules() {
        ResearchProfile p = profile(0.9, 0.2, 0.8, HorizonCode._5Y, PANDEMIC);
        Fx fx = new Fx();
        fx.base("EV001").setCategory("biology");
        Fx fxClassificationTopic = new Fx();
        fxClassificationTopic.base("EV001").getClassification().setTopic(" Biology ");
        Fx fxUnexpected = new Fx();
        fxUnexpected.base("EV001");
        fxUnexpected.sources.get("S001").setTopic("unexpected");
        Fx fxMixed = new Fx();
        NormalizedEvent mixed = fxMixed.base("EV001");
        mixed.setSourceIds(new ArrayList<>(List.of("S001", "S101")));
        fxMixed.sources.put("S101", source("S101", "Stub Site", "robotics", 0.85));
        Fx fxCustom = new Fx();
        fxCustom.base("EV001").setCategory("custom");
        ResearchProfile custom = profile(0.9, 0.2, 0.8, HorizonCode._5Y,
            new ResearchTopic("custom-1", "Mars colony", "custom", 0.7, true));

        assertThat(rankingOf(rank(fx, p).get(0)).getFactors().getTopicMatch()).as("event category = topic category").isEqualTo(1.0);
        assertThat(rankingOf(rank(fxClassificationTopic, p).get(0)).getFactors().getTopicMatch())
            .as("classification topic trimmed, case-insensitive").isEqualTo(1.0);
        assertThat(rankingOf(rank(fxUnexpected, p).get(0)).getFactors().getTopicMatch()).as("unexpected source").isEqualTo(0.3);
        assertThat(rankingOf(rank(fxMixed, p).get(0)).getFactors().getTopicMatch()).as("major + robotics").isEqualTo(0.5);
        assertThat(rankingOf(rank(fxCustom, custom).get(0)).getFactors().getTopicMatch()).as("custom category never matches").isEqualTo(0.5);
    }

    // R13
    @Test
    void sameInputTwiceAndShuffledInputGiveIdenticalOutput() {
        List<List<NormalizedEvent>> outs = new ArrayList<>();
        for (int run = 0; run < 3; run++) {
            Fx fx = v4Fixture();
            Fx more = new Fx();
            for (int n = 3; n <= 8; n++) more.base(String.format("EV%03d", n), 0.1 * (n % 9), 0.1 * ((n * 3) % 9));
            fx.events.addAll(more.events);
            fx.sources.putAll(more.sources);
            if (run == 1) Collections.reverse(fx.events);
            if (run == 2) Collections.shuffle(fx.events, new java.util.Random(7));
            outs.add(rank(fx, profileA()));
        }
        for (int i = 1; i < outs.size(); i++) {
            assertThat(ids(outs.get(i))).isEqualTo(ids(outs.get(0)));
            for (NormalizedEvent e : outs.get(0)) {
                assertThat(byId(outs.get(i), e.getId()).getRanking()).isEqualTo(e.getRanking());
            }
        }
    }

    @Test
    void darknessAndOptimismChangeOnlyRankingNotTheEventContent() {
        Fx a = v4Fixture();
        Fx b = v4Fixture();
        List<NormalizedEvent> dark = rank(a, profile(0.9, 0.2, 0.8, HorizonCode._5Y, PANDEMIC, HUMANOID));
        List<NormalizedEvent> bright = rank(b, profile(0.2, 0.9, 0.8, HorizonCode._5Y, PANDEMIC, HUMANOID));
        for (NormalizedEvent e : dark) {
            NormalizedEvent o = byId(bright, e.getId());
            assertThat(o.getSummary()).isEqualTo(e.getSummary());
            assertThat(o.getCategory()).isEqualTo(e.getCategory());
            assertThat(o.getEntities()).isEqualTo(e.getEntities());
            assertThat(o.getClassification()).isEqualTo(e.getClassification());
            assertThat(o.getRanking()).isNotEqualTo(e.getRanking());
        }
        assertThat(ids(bright)).hasSameSizeAs(ids(dark));
    }

    @Test
    void lowSourceQualityEventKeepsItsRankingAndIsListedByScore() {
        Fx fx = new Fx();
        NormalizedEvent low = fx.base("EV001", 0.9, 0.1);
        fx.setQuality(low, 0.29);
        NormalizedEvent edge = fx.base("EV002", 0.9, 0.1);
        fx.setQuality(edge, 0.30);
        List<NormalizedEvent> out = rank(fx, baseProfile());
        assertThat(byId(out, "EV001").getExcludedReason()).isEqualTo("LOW_SOURCE_QUALITY");
        assertThat(byId(out, "EV001").getRanking()).isNotNull();
        assertThat(byId(out, "EV002").getExcludedReason()).as("exactly the minimum is allowed").isNull();
    }
}
