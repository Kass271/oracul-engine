package com.oracul.app.research;

import com.oracul.app.api.model.EventSelection;
import com.oracul.app.api.model.EvidenceSection;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.Source;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Diverse evidence selection with counter-signals (FR-17). Pure. */
public class EvidenceSelector {

    public record Result(List<NormalizedEvent> core, List<NormalizedEvent> supporting,
                         List<NormalizedEvent> counterSignals) {
    }

    private static final BigDecimal MARGIN = new BigDecimal("0.2");
    private static final BigDecimal BALANCE = new BigDecimal("0.1");

    private final EvidenceProperties props;

    public EvidenceSelector(EvidenceProperties properties) {
        this.props = properties;
    }

    public Result select(List<NormalizedEvent> ranked, Map<String, Source> sourcesById, ResearchProfile profile) {
        List<NormalizedEvent> eligible = new ArrayList<>();
        for (NormalizedEvent e : ranked) {
            if (e.getExcludedReason() == null && e.getClassification() != null) {
                eligible.add(e);
            }
        }
        Set<String> categories = new HashSet<>();
        for (NormalizedEvent e : eligible) {
            categories.add(e.getCategory() == null ? "" : e.getCategory().trim().toLowerCase(Locale.ROOT));
        }
        int categoryCap = categories.size() >= 3
            ? BigDecimal.valueOf(props.maxCategoryShare()).multiply(BigDecimal.valueOf(props.maxItems()))
                .setScale(0, RoundingMode.FLOOR).intValue()
            : Integer.MAX_VALUE;
        Picker picker = new Picker(sourcesById, categoryCap);

        BigDecimal d = BigDecimal.valueOf(profile.getDarkness()).subtract(BigDecimal.valueOf(profile.getOptimism()));
        List<NormalizedEvent> core;
        List<NormalizedEvent> counterCandidates;
        if (d.compareTo(BALANCE) > 0) {
            counterCandidates = filter(eligible, Dominance.BRIGHT);
            core = picker.greedy(minus(eligible, counterCandidates), props.core());
        } else if (d.compareTo(BALANCE.negate()) < 0) {
            counterCandidates = filter(eligible, Dominance.DARK);
            core = picker.greedy(minus(eligible, counterCandidates), props.core());
        } else {
            core = picker.greedy(eligible, props.core());
            int dark = 0;
            int bright = 0;
            for (NormalizedEvent e : core) {
                Dominance dom = dominance(e);
                if (dom == Dominance.DARK) {
                    dark++;
                } else if (dom == Dominance.BRIGHT) {
                    bright++;
                }
            }
            List<NormalizedEvent> rest = minus(eligible, core);
            if (dark > bright) {
                counterCandidates = filter(rest, Dominance.BRIGHT);
            } else if (bright > dark) {
                counterCandidates = filter(rest, Dominance.DARK);
            } else {
                counterCandidates = List.of();
            }
        }
        List<NormalizedEvent> remaining = minus(minus(eligible, core), counterCandidates);
        List<NormalizedEvent> supporting = picker.greedy(remaining, props.supporting());
        List<NormalizedEvent> counter = picker.greedy(counterCandidates, props.counterSignals());
        if (counter.isEmpty() && !counterCandidates.isEmpty() && props.counterSignals() >= 1) {
            counter = new ArrayList<>(List.of(counterCandidates.get(0)));
        }

        int[] next = {1};
        return new Result(mark(core, EvidenceSection.CORE, next), mark(supporting, EvidenceSection.SUPPORTING, next),
            mark(counter, EvidenceSection.COUNTER_SIGNAL, next));
    }

    private static List<NormalizedEvent> mark(List<NormalizedEvent> picked, EvidenceSection section, int[] next) {
        List<NormalizedEvent> out = new ArrayList<>();
        for (NormalizedEvent in : picked) {
            NormalizedEvent e = EventCopies.copy(in);
            e.setSelection(new EventSelection(String.format("E%03d", next[0]++), section));
            out.add(e);
        }
        return out;
    }

    private enum Dominance { DARK, BRIGHT, NONE }

    private static Dominance dominance(NormalizedEvent e) {
        BigDecimal risk = BigDecimal.valueOf(e.getClassification().getRisk());
        BigDecimal opp = BigDecimal.valueOf(e.getClassification().getOpportunity());
        if (risk.subtract(opp).compareTo(MARGIN) >= 0) {
            return Dominance.DARK;
        }
        if (opp.subtract(risk).compareTo(MARGIN) >= 0) {
            return Dominance.BRIGHT;
        }
        return Dominance.NONE;
    }

    private static List<NormalizedEvent> filter(List<NormalizedEvent> events, Dominance dom) {
        List<NormalizedEvent> out = new ArrayList<>();
        for (NormalizedEvent e : events) {
            if (dominance(e) == dom) {
                out.add(e);
            }
        }
        return out;
    }

    private static List<NormalizedEvent> minus(List<NormalizedEvent> from, List<NormalizedEvent> remove) {
        Set<String> ids = new HashSet<>();
        for (NormalizedEvent e : remove) {
            ids.add(e.getId());
        }
        List<NormalizedEvent> out = new ArrayList<>();
        for (NormalizedEvent e : from) {
            if (!ids.contains(e.getId())) {
                out.add(e);
            }
        }
        return out;
    }

    /** Greedy picking with caps counted over everything picked so far in any section. */
    private final class Picker {
        private final Map<String, Source> sources;
        private final int categoryCap;
        private final Map<String, Integer> entities = new HashMap<>();
        private final Map<String, Integer> publishers = new HashMap<>();
        private final Map<String, Integer> geographies = new HashMap<>();
        private final Map<String, Integer> categories = new HashMap<>();

        Picker(Map<String, Source> sources, int categoryCap) {
            this.sources = sources;
            this.categoryCap = categoryCap;
        }

        List<NormalizedEvent> greedy(List<NormalizedEvent> candidates, int limit) {
            List<NormalizedEvent> out = new ArrayList<>();
            for (NormalizedEvent e : candidates) {
                if (out.size() >= limit) {
                    break;
                }
                String entity = entity(e);
                String publisher = publisher(e);
                String geography = geography(e);
                String category = e.getCategory() == null ? "" : e.getCategory().trim().toLowerCase(Locale.ROOT);
                if (entity != null && entities.getOrDefault(entity, 0) >= props.maxPerEntity()) {
                    continue;
                }
                if (publisher != null && publishers.getOrDefault(publisher, 0) >= props.maxPerPublisher()) {
                    continue;
                }
                if (geography != null && geographies.getOrDefault(geography, 0) >= props.maxPerGeography()) {
                    continue;
                }
                if (categories.getOrDefault(category, 0) >= categoryCap) {
                    continue;
                }
                if (entity != null) {
                    entities.merge(entity, 1, Integer::sum);
                }
                if (publisher != null) {
                    publishers.merge(publisher, 1, Integer::sum);
                }
                if (geography != null) {
                    geographies.merge(geography, 1, Integer::sum);
                }
                categories.merge(category, 1, Integer::sum);
                out.add(e);
            }
            return out;
        }

        private String entity(NormalizedEvent e) {
            if (e.getEntities() == null || e.getEntities().isEmpty() || e.getEntities().get(0) == null) {
                return null;
            }
            return e.getEntities().get(0).trim().toLowerCase(Locale.ROOT);
        }

        private String geography(NormalizedEvent e) {
            String g = e.getClassification().getGeography();
            if (g == null) {
                return null;
            }
            g = g.trim().toLowerCase(Locale.ROOT);
            return g.equals("global") ? null : g;
        }

        private String publisher(NormalizedEvent e) {
            Source best = null;
            for (String id : e.getSourceIds()) {
                Source s = sources.get(id);
                if (s == null) {
                    continue;
                }
                if (best == null || s.getSourceQuality() > best.getSourceQuality()
                    || (s.getSourceQuality() == best.getSourceQuality()
                        && EventCopies.ID_ORDER.compare(s.getId(), best.getId()) < 0)) {
                    best = s;
                }
            }
            return best == null || best.getPublisher() == null ? null
                : best.getPublisher().trim().toLowerCase(Locale.ROOT);
        }
    }
}
