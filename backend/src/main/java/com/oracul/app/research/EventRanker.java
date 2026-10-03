package com.oracul.app.research;

import com.oracul.app.api.model.EventClassification;
import com.oracul.app.api.model.EventRanking;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.RankingFactors;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.Trend;
import com.oracul.app.api.model.WildcardMatch;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Scenario-aware, deterministic ranking of classified events (FR-16). Pure. */
public class EventRanker {

    static final String CLASSIFICATION_FAILED = "CLASSIFICATION_FAILED";
    static final String LOW_SOURCE_QUALITY = "LOW_SOURCE_QUALITY";

    private final RankingWeights w;
    private final double minSourceQuality;

    public EventRanker(RankingWeights weights, double minSourceQuality) {
        this.w = weights;
        this.minSourceQuality = minSourceQuality;
    }

    public List<NormalizedEvent> rank(List<NormalizedEvent> events, Map<String, Source> sourcesById,
                                      ResearchProfile profile, Instant cutoff) {
        List<NormalizedEvent> ranked = new ArrayList<>();
        List<NormalizedEvent> unranked = new ArrayList<>();
        for (NormalizedEvent in : events) {
            NormalizedEvent e = EventCopies.copy(in);
            e.setSelection(null);
            if (e.getClassification() == null) {
                e.setRanking(null);
                if (e.getExcludedReason() == null) {
                    e.setExcludedReason(CLASSIFICATION_FAILED);
                }
                unranked.add(e);
                continue;
            }
            e.setRanking(score(e, sourcesById, profile, cutoff));
            BigDecimal quality = BigDecimal.valueOf(e.getClassification().getSourceQuality());
            if (quality.compareTo(BigDecimal.valueOf(minSourceQuality)) < 0) {
                e.setExcludedReason(LOW_SOURCE_QUALITY);
            }
            ranked.add(e);
        }
        ranked.sort(ORDER);
        unranked.sort(Comparator.comparing(NormalizedEvent::getId, EventCopies.ID_ORDER));
        List<NormalizedEvent> out = new ArrayList<>(ranked);
        out.addAll(unranked);
        return out;
    }

    /** API order of ranked events: score desc, source quality desc, date desc (absent last), id asc. */
    static final Comparator<NormalizedEvent> ORDER = Comparator
        .<NormalizedEvent>comparingDouble(e -> -e.getRanking().getScore())
        .thenComparingDouble(e -> -e.getClassification().getSourceQuality())
        .thenComparing(NormalizedEvent::getDate, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
        .thenComparing(NormalizedEvent::getId, EventCopies.ID_ORDER);

    private EventRanking score(NormalizedEvent e, Map<String, Source> sources, ResearchProfile p, Instant cutoff) {
        EventClassification c = e.getClassification();
        List<ResearchTopic> topics = p.getTopics() == null ? List.of() : p.getTopics();

        double topicMatch = topicMatch(e, c, sources, topics);
        double wildcard = 0;
        int strong = 0;
        for (ResearchTopic t : topics) {
            double s = matchScore(c, t.getKey());
            wildcard = Math.max(wildcard, s * t.getWeight());
            if (s >= 0.5) {
                strong++;
            }
        }
        double darkness = p.getDarkness() * c.getRisk();
        double optimism = p.getOptimism() * c.getOpportunity();
        double recency = recency(e.getDate(), p.getHorizon(), cutoff);
        double quality = c.getSourceQuality();
        double impact = c.getImpact();
        double trend = switch (c.getTrend()) {
            case ESTABLISHED -> 1.0;
            case EMERGING -> 0.7;
            case DECLINING -> 0.3;
        };
        double cross = Math.max(0, Math.min(1, (strong - 1) / 2.0));
        double t = c.getTrend() == Trend.ESTABLISHED ? 1.0 : 0.5;
        double corroboration = 0.5 * Math.min(1.0, e.getSourceIds().size() / 3.0) + 0.5 * t;
        double realism = p.getRealism() * corroboration + (1 - p.getRealism()) * c.getNovelty();

        double sum = w.topicMatch() * topicMatch + w.wildcardMatch() * wildcard + w.darknessMatch() * darkness
            + w.optimismMatch() * optimism + w.recency() * recency + w.sourceQuality() * quality
            + w.impact() * impact + w.trendStrength() * trend + w.crossTopic() * cross
            + w.realismCompatibility() * realism;
        double relevance = sum / w.sum();
        double score = relevance * (0.25 + 0.75 * quality);

        RankingFactors f = new RankingFactors(r4(topicMatch), r4(wildcard), r4(darkness), r4(optimism), r4(recency),
            r4(quality), r4(impact), r4(trend), r4(cross), r4(realism));
        return new EventRanking(r4(relevance), r4(quality), r4(score), f);
    }

    private static double topicMatch(NormalizedEvent e, EventClassification c, Map<String, Source> sources,
                                     List<ResearchTopic> topics) {
        boolean unexpected = false;
        for (String id : e.getSourceIds()) {
            Source s = sources.get(id);
            if (s == null || s.getTopic() == null) {
                continue;
            }
            String topic = s.getTopic().trim();
            for (ResearchTopic t : topics) {
                if (topic.equals(t.getKey())) {
                    return 1.0;
                }
            }
            if (topic.equalsIgnoreCase("unexpected")) {
                unexpected = true;
            }
        }
        for (ResearchTopic t : topics) {
            String cat = t.getCategory();
            if (cat == null || cat.equals("custom")) {
                continue;
            }
            if (same(e.getCategory(), cat) || same(c.getTopic(), cat)) {
                return 1.0;
            }
        }
        return unexpected ? 0.3 : 0.5;
    }

    private static boolean same(String a, String b) {
        return a != null && b != null && a.trim().toLowerCase(Locale.ROOT).equals(b.trim().toLowerCase(Locale.ROOT));
    }

    private static double matchScore(EventClassification c, String key) {
        if (c.getWildcardMatches() == null) {
            return 0;
        }
        for (WildcardMatch m : c.getWildcardMatches()) {
            if (m.getKey().equals(key)) {
                return m.getScore();
            }
        }
        return 0;
    }

    private static double recency(LocalDate date, HorizonCode horizon, Instant cutoff) {
        if (date == null) {
            return 0;
        }
        int span = switch (horizon) {
            case _1D, _1W -> 7;
            case _1M -> 14;
            default -> 90;
        };
        long age = Math.max(0, ChronoUnit.DAYS.between(date, cutoff.atZone(ZoneOffset.UTC).toLocalDate()));
        return 1 - Math.min((double) age / span, 1.0);
    }

    static double r4(double x) {
        return BigDecimal.valueOf(x).setScale(4, RoundingMode.HALF_UP).doubleValue();
    }
}
