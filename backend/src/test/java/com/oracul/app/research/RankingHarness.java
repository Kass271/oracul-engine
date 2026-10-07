package com.oracul.app.research;

import com.oracul.app.api.model.EventClassification;
import com.oracul.app.api.model.EventRanking;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.RankingFactors;
import com.oracul.app.api.model.ResearchProfile;
import com.oracul.app.api.model.ResearchTopic;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceType;
import com.oracul.app.api.model.Trend;
import com.oracul.app.api.model.WildcardMatch;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure (no Spring) driver of {@code EventRanker} for the slice 07 unit tests (research-pipeline.md "Slice 07_evidence-pack").
 * The selector and the pack renderer of that slice are gone (wildcard-evidence.md slice 06: {@code select}, {@code Selection}
 * and {@code render} are removed with them). The production class and its configuration records are reached by reflection so
 * RED compiles whatever they look like; a missing class or method is an AssertionError naming it. Generated API models are used
 * directly (they exist before the implementation).
 */
final class RankingHarness {

    static final Instant CUTOFF = Instant.parse("2026-10-02T18:42:00Z");

    private RankingHarness() {}

    // ---- reflection ------------------------------------------------------------------------------------------

    static Class<?> type(String name) {
        try {
            return Class.forName("com.oracul.app.research." + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.research." + name + " is missing");
        }
    }

    private static Object call(Object target, Class<?> type, String name, Object... args) {
        Method m = null;
        for (Method c : type.getDeclaredMethods()) {
            if (c.getName().equals(name) && c.getParameterCount() == args.length) m = c;
        }
        if (m == null) throw new AssertionError(type.getSimpleName() + "." + name + "(" + args.length + " args) is missing");
        try {
            m.setAccessible(true);
            return m.invoke(Modifier.isStatic(m.getModifiers()) ? null : target, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(type.getSimpleName() + "." + name + " failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object instance(Class<?> type, Object... args) {
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterCount() != args.length) continue;
            try {
                c.setAccessible(true);
                return c.newInstance(args);
            } catch (InvocationTargetException e) {
                throw new AssertionError(type.getSimpleName() + " constructor failed: " + e.getTargetException(), e.getTargetException());
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(type.getSimpleName() + " cannot be instantiated: " + e, e);
            }
        }
        throw new AssertionError(type.getSimpleName() + " has no constructor with " + args.length + " parameters");
    }

    /** A configuration record built from its defaults() with the named components replaced. */
    private static Object record(String name, boolean fromDefaults, Map<String, Number> overrides) {
        Class<?> type = type(name);
        if (!type.isRecord()) throw new AssertionError(name + " must be a record");
        RecordComponent[] comps = type.getRecordComponents();
        Object base = fromDefaults ? call(null, type, "defaults") : null;
        Object[] values = new Object[comps.length];
        Class<?>[] types = new Class<?>[comps.length];
        for (int i = 0; i < comps.length; i++) {
            types[i] = comps[i].getType();
            Number v = overrides.get(comps[i].getName());
            if (v == null) {
                try {
                    v = fromDefaults ? (Number) comps[i].getAccessor().invoke(base) : 0;
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(e);
                }
            }
            values[i] = types[i] == int.class ? (Object) v.intValue() : (Object) v.doubleValue();
        }
        for (String k : overrides.keySet()) {
            boolean known = false;
            for (RecordComponent c : comps) known |= c.getName().equals(k);
            if (!known) throw new AssertionError(name + " has no component " + k);
        }
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(types);
            ctor.setAccessible(true);
            return ctor.newInstance(values);
        } catch (InvocationTargetException e) {
            throw new AssertionError(name + " constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(name + " has an unexpected canonical constructor: " + e, e);
        }
    }

    static Map<String, Number> kv(Object... kv) {
        Map<String, Number> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Number) kv[i + 1]);
        return m;
    }

    /** RankingWeights.defaults() with the named weights replaced. */
    static Object weights(Object... kv) {
        return record("RankingWeights", true, kv(kv));
    }

    /** Every weight 0 except the named ones. */
    static Object weightsOnly(Object... kv) {
        Map<String, Number> m = new LinkedHashMap<>();
        for (String c : List.of("topicMatch", "wildcardMatch", "darknessMatch", "optimismMatch", "recency", "sourceQuality",
            "impact", "trendStrength", "crossTopic", "realismCompatibility")) m.put(c, 0.0);
        m.putAll(kv(kv));
        return record("RankingWeights", false, m);
    }

    static Object defaultWeights() {
        return weights();
    }

    /** EvidenceProperties.defaults() with the named components replaced. */
    static Object props(Object... kv) {
        return record("EvidenceProperties", true, kv(kv));
    }

    // ---- ranker ------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<NormalizedEvent> rank(Object weights, double minSourceQuality, List<NormalizedEvent> events,
                                      Map<String, Source> sources, ResearchProfile profile, Instant cutoff) {
        Class<?> type = type("EventRanker");
        Object ranker = instance(type, weights, minSourceQuality);
        return (List<NormalizedEvent>) call(ranker, type, "rank", events, sources, profile, cutoff);
    }

    static List<NormalizedEvent> rank(Fx fx, ResearchProfile profile) {
        return rank(defaultWeights(), 0.30, fx.events, fx.sources, profile, CUTOFF);
    }

    // ---- fixtures ----------------------------------------------------------------------------------------------

    static final ResearchTopic PANDEMIC = new ResearchTopic("biology-new-pandemic", "New pandemic", "biology", 0.8, false);
    static final ResearchTopic HUMANOID = new ResearchTopic("robotics-humanoid-boom", "Humanoid robot boom", "robotics", 0.6, false);

    static ResearchProfile profile(double darkness, double optimism, double realism, HorizonCode horizon, ResearchTopic... topics) {
        return new ResearchProfile(darkness, optimism, realism, horizon, new ArrayList<>(List.of(topics)));
    }

    /** Profile of acceptance body A. */
    static ResearchProfile profileA() {
        return profile(0.9, 0.2, 0.8, HorizonCode._5Y, PANDEMIC, HUMANOID);
    }

    /** Unit baseline profile: darkness 0.9, optimism 0.2, realism 0.8, 5y, no topics. */
    static ResearchProfile baseProfile() {
        return profile(0.9, 0.2, 0.8, HorizonCode._5Y);
    }

    static String sid(String eventId) {
        return "S" + eventId.substring(2);
    }

    static Source source(String id, String publisher, String topic, double quality) {
        Source s = new Source(id, URI.create("http://127.0.0.1/articles/" + id), publisher, "Title " + id,
            OffsetDateTime.parse("2026-10-02T00:00:00Z"), SourceType.NEWS, quality, true);
        s.setTopic(topic);
        s.setSummary("Summary of " + id);
        s.setEntities(new ArrayList<>());
        s.setQueryIds(new ArrayList<>());
        return s;
    }

    static EventClassification classification(String topic, double risk, double opportunity, double impact, double novelty,
                                              Trend trend, String geography, double sourceQuality, WildcardMatch... matches) {
        return new EventClassification(topic, new ArrayList<>(), 0.0, risk, opportunity, impact, novelty, sourceQuality, trend,
            geography, new ArrayList<>(List.of(matches)));
    }

    static NormalizedEvent event(String id, String date, String category, List<String> entities, List<String> sourceIds,
                                 double confidence, EventClassification c) {
        NormalizedEvent e = new NormalizedEvent(id, category, new ArrayList<>(entities), "Summary of " + id,
            new ArrayList<>(sourceIds), confidence);
        if (date != null) e.setDate(LocalDate.parse(date));
        e.setClassification(c);
        return e;
    }

    static WildcardMatch match(String key, double score) {
        return new WildcardMatch(key, score);
    }

    static EventRanking ranking(double score) {
        return new EventRanking(score, 0.85, score, new RankingFactors(0.5, 0.5, 0.5, 0.5, 0.5, 0.85, 0.5, 0.5, 0.5, 0.5));
    }

    /** Events of one test plus the sources they reference. */
    static final class Fx {
        final List<NormalizedEvent> events = new ArrayList<>();
        final Map<String, Source> sources = new LinkedHashMap<>();

        /**
         * Unit baseline event: date 2026-10-01, category general, no entities, one source topic major quality 0.85,
         * classification general / risk .5 / opportunity .5 / impact .5 / novelty .5 / ESTABLISHED / global / 0.85.
         */
        NormalizedEvent base(String id) {
            Source s = source(sid(id), "Stub Site", "major", 0.85);
            sources.put(s.getId(), s);
            NormalizedEvent e = event(id, "2026-10-01", "general", List.of(), List.of(s.getId()), 0.8,
                classification("general", 0.5, 0.5, 0.5, 0.5, Trend.ESTABLISHED, "global", 0.85));
            events.add(e);
            return e;
        }

        NormalizedEvent base(String id, double risk, double opportunity) {
            NormalizedEvent e = base(id);
            e.getClassification().setRisk(risk);
            e.getClassification().setOpportunity(opportunity);
            return e;
        }

        Source sourceOf(NormalizedEvent e) {
            return sources.get(e.getSourceIds().get(0));
        }

        void setQuality(NormalizedEvent e, double q) {
            sourceOf(e).setSourceQuality(q);
            e.getClassification().setSourceQuality(q);
        }
    }

    static EventRanking rankingOf(NormalizedEvent e) {
        if (e.getRanking() == null) throw new AssertionError(e.getId() + " has no ranking");
        return e.getRanking();
    }

    static List<String> ids(List<NormalizedEvent> events) {
        List<String> out = new ArrayList<>();
        for (NormalizedEvent e : events) out.add(e.getId());
        return out;
    }

    static NormalizedEvent byId(List<NormalizedEvent> events, String id) {
        for (NormalizedEvent e : events) if (e.getId().equals(id)) return e;
        throw new AssertionError("no event " + id + " in " + ids(events));
    }
}
