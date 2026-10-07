package com.oracul.app.research;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Test-side view of the pure selector of FR-53 ({@code com.oracul.app.research.WildcardSelector}, article-retrieval.md "Slice
 * 05_wildcard-selection - delta"). The class and the 6th component {@code snippet} of {@code NewsProvider.Article} do not exist
 * before the slice is built and RED must compile, so everything is reached by reflection: the tests build plain {@link P} / {@link Q}
 * values, {@link #select} converts them to the production records, calls {@code select(List<Pipeline>, Instant)} and converts the
 * {@code Result} back to {@link Sel}. A missing class, constructor or method is an AssertionError that names it.
 */
final class SelectorApi {

    /** "Now" of the unit tests; item dates are NOW minus one day, the usual cutoff is NOW minus 90 days. */
    static final Instant NOW = Instant.parse("2026-10-07T12:00:00Z");
    static final Instant CUTOFF = NOW.minusSeconds(90L * 86_400);

    private SelectorApi() {
    }

    /** One feed item as the tests state it (null fields allowed). */
    record Item(String url, String title, Instant publishedAt, String snippet) {
        static Item of(String url, String title) {
            return new Item(url, title, NOW.minusSeconds(86_400), null);
        }
    }

    /** {@code WildcardSelector.Query(id, text, items)}: items = the query's own answer in feed order. */
    record Q(String id, String text, List<Item> items) {
        static Q of(String id, String text, Item... items) {
            return new Q(id, text, new ArrayList<>(List.of(items)));
        }
    }

    /** {@code WildcardSelector.Pipeline(id, labelText, topic, queries)}. */
    record P(String id, String label, String topic, List<Q> queries) {
        static P of(String id, String label, String topic, Q... queries) {
            return new P(id, label, topic, new ArrayList<>(List.of(queries)));
        }
    }

    /** {@code WildcardSelector.Candidate} (rank output). */
    record C(String url, String title, List<String> queryIds, int bestPosition, String bestQueryId, int score) {
    }

    /** {@code WildcardSelector.Kept}. */
    record K(String url, String title, List<String> pipelineIds, List<String> queryIds, String topic) {
    }

    /** {@code WildcardSelector.Result}; {@code raw} is the production object (for equality of two runs). */
    record Sel(List<K> kept, Map<String, Integer> candidatesConsidered, Map<String, List<Integer>> groups, int articlesConsidered,
               Object raw) {
        List<String> urls() {
            return kept.stream().map(K::url).toList();
        }
    }

    // ---- reflection plumbing ------------------------------------------------------------------------------------

    static Class<?> cls(String simpleOrNested) {
        String name = "com.oracul.app.research." + simpleOrNested;
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(name + " is missing (FR-53, article-retrieval.md slice 05 delta)");
        }
    }

    private static Constructor<?> ctor(Class<?> type, Class<?>... params) {
        try {
            Constructor<?> c = type.getDeclaredConstructor(params);
            c.setAccessible(true);
            return c;
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type.getName() + " has no constructor " + java.util.Arrays.toString(params));
        }
    }

    private static Object create(Constructor<?> c, Object... args) {
        try {
            return c.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("cannot construct " + c.getDeclaringClass().getName() + ": " + e);
        }
    }

    static Object get(Object target, String accessor) {
        try {
            Method m = target.getClass().getDeclaredMethod(accessor);
            m.setAccessible(true);
            return m.invoke(target);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(target.getClass().getName() + " has no accessor " + accessor + "()");
        } catch (InvocationTargetException e) {
            throw new AssertionError(accessor + "() failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object invokeStatic(String type, String method, Class<?>[] params, Object... args) {
        Class<?> c = cls(type);
        try {
            Method m = c.getDeclaredMethod(method, params);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type + "." + method + java.util.Arrays.toString(params) + " is missing");
        } catch (InvocationTargetException e) {
            throw new AssertionError(type + "." + method + " failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    /** NewsProvider.Article with the 6th component snippet. */
    static Object article(Item item) {
        Constructor<?> c = ctor(cls("NewsProvider$Article"), String.class, String.class, Instant.class, String.class, String.class,
            String.class);
        return create(c, item.url(), item.title(), item.publishedAt(), null, null, item.snippet());
    }

    /** The {@code snippet()} accessor of a NewsProvider.Article (the 6th component). */
    static String snippetOf(Object article) {
        return (String) get(article, "snippet");
    }

    private static Object query(Q q) {
        List<Object> items = new ArrayList<>();
        for (Item i : q.items()) items.add(article(i));
        // immutable lists: a selector that sorts or edits its input fails loudly (FR-53: input lists are not mutated)
        return create(ctor(cls("WildcardSelector$Query"), String.class, String.class, List.class), q.id(), q.text(), List.copyOf(items));
    }

    private static Object pipeline(P p) {
        List<Object> queries = new ArrayList<>();
        for (Q q : p.queries()) queries.add(query(q));
        return create(ctor(cls("WildcardSelector$Pipeline"), String.class, String.class, String.class, List.class), p.id(), p.label(),
            p.topic(), List.copyOf(queries));
    }

    private static String titleOf(Object article) {
        return (String) get(article, "title");
    }

    @SuppressWarnings("unchecked")
    private static List<String> strings(Object o) {
        return (List<String>) o;
    }

    // ---- the API under test -------------------------------------------------------------------------------------

    /** {@code WildcardSelector.select(List<Pipeline>, Instant cutoff)}. */
    @SuppressWarnings("unchecked")
    static Sel select(List<P> pipelines, Instant cutoff) {
        List<Object> ps = new ArrayList<>();
        for (P p : pipelines) ps.add(pipeline(p));
        Object result = invokeStatic("WildcardSelector", "select", new Class<?>[] {List.class, Instant.class}, List.copyOf(ps), cutoff);
        List<K> kept = new ArrayList<>();
        for (Object k : (List<Object>) get(result, "kept")) {
            kept.add(new K((String) get(k, "url"), titleOf(get(k, "article")), strings(get(k, "pipelineIds")),
                strings(get(k, "queryIds")), (String) get(k, "topic")));
        }
        return new Sel(kept, (Map<String, Integer>) get(result, "candidatesConsidered"),
            (Map<String, List<Integer>>) get(result, "groups"), ((Number) get(result, "articlesConsidered")).intValue(), result);
    }

    static Sel select(List<P> pipelines) {
        return select(pipelines, CUTOFF);
    }

    /** {@code WildcardSelector.rank(Pipeline, Instant)}: all candidates of the pipeline in relevance order. */
    @SuppressWarnings("unchecked")
    static List<C> rank(P p, Instant cutoff) {
        Object result = invokeStatic("WildcardSelector", "rank", new Class<?>[] {cls("WildcardSelector$Pipeline"), Instant.class},
            pipeline(p), cutoff);
        List<C> out = new ArrayList<>();
        for (Object c : (List<Object>) result) {
            out.add(new C((String) get(c, "url"), titleOf(get(c, "article")), strings(get(c, "queryIds")),
                ((Number) get(c, "bestPosition")).intValue(), (String) get(c, "bestQueryId"), ((Number) get(c, "score")).intValue()));
        }
        return out;
    }

    static List<C> rank(P p) {
        return rank(p, CUTOFF);
    }

    /** {@code WildcardSelector.tokens(String)} (insertion-ordered). */
    @SuppressWarnings("unchecked")
    static Set<String> tokens(String text) {
        return (Set<String>) invokeStatic("WildcardSelector", "tokens", new Class<?>[] {String.class}, text);
    }

    /** A static constant of WildcardSelector. */
    static Object constant(String name) {
        try {
            java.lang.reflect.Field f = cls("WildcardSelector").getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException e) {
            throw new AssertionError("WildcardSelector." + name + " is missing");
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }
}
