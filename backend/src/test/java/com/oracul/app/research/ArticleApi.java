package com.oracul.app.research;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.BooleanSupplier;

/**
 * Test-side view of the three new classes of article-retrieval.md slice 08: {@code ArticleUrlDecoder}, {@code FragmentExtractor} and
 * {@code ArticleRetriever}. None of them exists before the slice is built and RED must compile, so everything is reached by reflection;
 * a missing class, constructor or method is an AssertionError that names it (the test then fails on its assertion, not on the compiler).
 * Names, signatures and visibility are exactly those of the slice spec.
 */
final class ArticleApi {

    private ArticleApi() {
    }

    // ---- plumbing -------------------------------------------------------------------------------------------------

    static Class<?> cls(String name) {
        String full = "com.oracul.app.research." + name;
        try {
            return Class.forName(full);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(full + " is missing (article-retrieval.md slice 08 delta)");
        }
    }

    private static Object invoke(Method m, Object target, Object... args) {
        try {
            m.setAccessible(true);
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            if (e.getTargetException() instanceof Error err) throw err;
            throw new AssertionError(m.getName() + " threw " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    static Object call(String type, String method, Class<?>[] params, Object... args) {
        Class<?> c = cls(type);
        try {
            return invoke(c.getDeclaredMethod(method, params), null, args);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type + "." + method + java.util.Arrays.toString(params) + " is missing");
        }
    }

    static Object get(Object target, String accessor) {
        try {
            return invoke(target.getClass().getDeclaredMethod(accessor), target);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(target.getClass().getName() + " has no accessor " + accessor + "()");
        }
    }

    static Object instanceCall(Object target, String method, Class<?>[] params, Object... args) {
        try {
            return invoke(target.getClass().getDeclaredMethod(method, params), target, args);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(target.getClass().getName() + "." + method + java.util.Arrays.toString(params) + " is missing");
        }
    }

    static Object constant(String type, String name) {
        try {
            java.lang.reflect.Field f = cls(type).getDeclaredField(name);
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(type + "." + name + " is missing");
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object construct(Class<?> type, Class<?>[] params, Object... args) {
        try {
            Constructor<?> c = type.getDeclaredConstructor(params);
            c.setAccessible(true);
            return c.newInstance(args);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(type.getName() + " has no constructor " + java.util.Arrays.toString(params));
        } catch (InvocationTargetException e) {
            if (e.getTargetException() instanceof RuntimeException re) throw re;
            throw new AssertionError("constructor threw " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    // ---- FragmentExtractor ----------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    static List<String> paragraphs(String body, String contentType) {
        return (List<String>) call("FragmentExtractor", "paragraphs", new Class<?>[] {String.class, String.class}, body, contentType);
    }

    static int strength(String paragraph, Set<String> labelTerms, Set<String> queryTerms) {
        return ((Number) call("FragmentExtractor", "strength", new Class<?>[] {String.class, Set.class, Set.class}, paragraph, labelTerms,
            queryTerms)).intValue();
    }

    static String cut(String text) {
        return (String) call("FragmentExtractor", "cut", new Class<?>[] {String.class}, text);
    }

    @SuppressWarnings("unchecked")
    static List<String> extract(String body, String contentType, Set<String> labelTerms, Set<String> queryTerms) {
        return (List<String>) call("FragmentExtractor", "extract", new Class<?>[] {String.class, String.class, Set.class, Set.class}, body,
            contentType, labelTerms, queryTerms);
    }

    // ---- ArticleUrlDecoder ----------------------------------------------------------------------------------------

    /** {@code ArticleUrlDecoder.Attributes(String id, long ts, String sg)} as plain values. */
    record Attrs(String id, long ts, String sg) {
    }

    private static Object attributesObject(Attrs a) {
        return construct(cls("ArticleUrlDecoder$Attributes"), new Class<?>[] {String.class, long.class, String.class}, a.id(), a.ts(), a.sg());
    }

    @SuppressWarnings("unchecked")
    static Optional<Attrs> attributes(String googlePageHtml) {
        Optional<Object> r = (Optional<Object>) call("ArticleUrlDecoder", "attributes", new Class<?>[] {String.class}, googlePageHtml);
        return r.map(o -> new Attrs((String) get(o, "id"), ((Number) get(o, "ts")).longValue(), (String) get(o, "sg")));
    }

    static String fReq(Attrs a) {
        return (String) call("ArticleUrlDecoder", "fReq", new Class<?>[] {cls("ArticleUrlDecoder$Attributes")}, attributesObject(a));
    }

    @SuppressWarnings("unchecked")
    static Optional<String> publisherUrl(String answerBody, String googleBaseUrl) {
        return (Optional<String>) call("ArticleUrlDecoder", "publisherUrl", new Class<?>[] {String.class, String.class}, answerBody, googleBaseUrl);
    }

    /** One {@code ArticleUrlDecoder} instance ({@code new ArticleUrlDecoder(decodeUrl, googleBaseUrl)}). */
    static Object decoder(String decodeUrl, String googleBaseUrl) {
        return construct(cls("ArticleUrlDecoder"), new Class<?>[] {String.class, String.class}, decodeUrl, googleBaseUrl);
    }

    /** {@code decoder.decode(Attributes, Duration)} as {url, reason}. */
    static String[] decode(Object decoder, Attrs a, Duration timeout) {
        Object r = instanceCall(decoder, "decode", new Class<?>[] {cls("ArticleUrlDecoder$Attributes"), Duration.class}, attributesObject(a), timeout);
        return new String[] {(String) get(r, "url"), (String) get(r, "reason")};
    }

    // ---- ArticleRetriever -----------------------------------------------------------------------------------------

    /** {@code ArticleRetriever.Outcome}. */
    record Outcome(String status, String publisherUrl, String contentType, String body, String description, String siteName, Instant endedAt) {
    }

    /** {@code new ArticleRetriever(SafeFetcher, ArticleUrlDecoder, String googleBaseUrl, Duration timeout, int concurrency, Clock)}. */
    static Object retriever(Object safeFetcher, Object decoder, String googleBaseUrl, Duration timeout, int concurrency, Clock clock) {
        return construct(cls("ArticleRetriever"),
            new Class<?>[] {SafeFetcherSupport.type(), cls("ArticleUrlDecoder"), String.class, Duration.class, int.class, Clock.class},
            safeFetcher, decoder, googleBaseUrl, timeout, concurrency, clock);
    }

    static boolean isGoogleLink(Object retriever, String url) {
        return (Boolean) instanceCall(retriever, "isGoogleLink", new Class<?>[] {String.class}, url);
    }

    @SuppressWarnings("unchecked")
    static List<Outcome> retrieveAll(Object retriever, List<String> links, Object budget, BooleanSupplier mayStart) {
        try {
            List<Object> raw = (List<Object>) instanceCall(retriever, "retrieveAll",
                new Class<?>[] {List.class, cls("SearchBudget"), BooleanSupplier.class}, links, budget, mayStart);
            List<Outcome> out = new ArrayList<>();
            for (Object o : raw) {
                out.add(new Outcome(String.valueOf(get(o, "status")), (String) get(o, "publisherUrl"), (String) get(o, "contentType"),
                    (String) get(o, "body"), (String) get(o, "description"), (String) get(o, "siteName"), (Instant) get(o, "endedAt")));
            }
            return out;
        } catch (RuntimeException e) {
            if (e.getCause() instanceof InterruptedException) throw new AssertionError(e);
            throw e;
        }
    }
}
