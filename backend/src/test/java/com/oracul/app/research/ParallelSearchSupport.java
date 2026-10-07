package com.oracul.app.research;

import static org.junit.jupiter.api.Assertions.fail;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Reflective access to the seams of slice 03_parallel-search (wildcard-search.md FR-52 "Slice 03 delta"):
 * {@code GoogleNewsSearch}, {@code NewsSearchProvider}, {@code SearchBudget} and the new {@code SourceRetrieval}
 * constructor. None of them exists while the RED tests are written; going through reflection keeps every test compiling,
 * so a test fails on its assertion ("... does not exist yet") and not on a compile error. Names and signatures are exactly
 * the ones of the slice spec.
 */
final class ParallelSearchSupport {

    static final String SEARCH = "com.oracul.app.research.GoogleNewsSearch";
    static final String BUDGET = "com.oracul.app.research.SearchBudget";
    static final String PROVIDER = "com.oracul.app.research.NewsSearchProvider";

    private ParallelSearchSupport() {
    }

    /** One {@code NewsSearchProvider.QueryResult}. */
    record QR(SearchQueryStatus status, List<NewsProvider.Article> articles) {
    }

    static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return fail(name + " does not exist yet (wildcard-search.md FR-52, slice 03_parallel-search)");
        }
    }

    private static <T> T unwrap(InvocationTargetException e) {
        Throwable c = e.getCause();
        if (c instanceof RuntimeException re) {
            throw re;
        }
        if (c instanceof Error err) {
            throw err;
        }
        throw new IllegalStateException(c);
    }

    // ---- GoogleNewsSearch -------------------------------------------------------------------------------------------

    /** The package-private test constructor {@code GoogleNewsSearch(NewsProvider, int, Duration, Duration)}. */
    static Object googleSearch(NewsProvider provider, int concurrency, Duration timeout, Duration rateLimitWait) {
        Class<?> t = cls(SEARCH);
        try {
            Constructor<?> c = t.getDeclaredConstructor(NewsProvider.class, int.class, Duration.class, Duration.class);
            c.setAccessible(true);
            return c.newInstance(provider, concurrency, timeout, rateLimitWait);
        } catch (InvocationTargetException e) {
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail("GoogleNewsSearch(NewsProvider, int, Duration, Duration) is missing: " + e);
        }
    }

    /** {@code List<QueryResult> search(List<String> texts, HorizonCode horizon, SearchBudget budget, BooleanSupplier mayStart)}. */
    static List<QR> search(Object google, List<String> texts, HorizonCode horizon, Object budget, BooleanSupplier mayStart)
        throws InterruptedException {
        Class<?> t = cls(SEARCH);
        try {
            Method m = t.getMethod("search", List.class, HorizonCode.class, cls(BUDGET), BooleanSupplier.class);
            m.setAccessible(true);
            List<?> raw = (List<?>) m.invoke(google, texts, horizon, budget, mayStart);
            List<QR> out = new ArrayList<>();
            for (Object r : raw) {
                Method status = r.getClass().getDeclaredMethod("status");
                Method articles = r.getClass().getDeclaredMethod("articles");
                status.setAccessible(true);
                articles.setAccessible(true);
                @SuppressWarnings("unchecked")
                List<NewsProvider.Article> a = (List<NewsProvider.Article>) articles.invoke(r);
                out.add(new QR((SearchQueryStatus) status.invoke(r), a));
            }
            return out;
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof InterruptedException ie) {
                throw ie;
            }
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail("GoogleNewsSearch.search(List, HorizonCode, SearchBudget, BooleanSupplier) is missing: " + e);
        }
    }

    /** {@code public static String GoogleNewsSearch.text(String)}. */
    static String text(String queryText) {
        return (String) staticCall(cls(SEARCH), "text", new Class<?>[] {String.class}, queryText);
    }

    /** {@code public static String GoogleNewsSearch.q(String text, HorizonCode horizon)}. */
    static String q(String text, HorizonCode horizon) {
        return (String) staticCall(cls(SEARCH), "q", new Class<?>[] {String.class, HorizonCode.class}, text, horizon);
    }

    /** {@code public static int GoogleNewsSearch.timespanDays(HorizonCode)}. */
    static int timespanDays(HorizonCode horizon) {
        return (Integer) staticCall(cls(SEARCH), "timespanDays", new Class<?>[] {HorizonCode.class}, horizon);
    }

    private static Object staticCall(Class<?> t, String name, Class<?>[] types, Object... args) {
        try {
            Method m = t.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail(t.getSimpleName() + "." + name + " is missing or has another signature: " + e);
        }
    }

    // ---- SearchBudget -----------------------------------------------------------------------------------------------

    /** {@code SearchBudget.search(Clock, Instant t0, Duration searchWindow, Instant deadlineAt)}. */
    static Object budget(Clock clock, Instant t0, Duration searchWindow, Instant deadlineAt) {
        return staticCall(cls(BUDGET), "search", new Class<?>[] {Clock.class, Instant.class, Duration.class, Instant.class},
            clock, t0, searchWindow, deadlineAt);
    }

    /** The canonical record constructor {@code SearchBudget(Clock, Instant, Map<Phase, Duration>, Instant)}; phases by name. */
    static Object budgetOf(Clock clock, Instant t0, Map<String, Duration> windowsByPhase, Instant deadlineAt) {
        Class<?> t = cls(BUDGET);
        try {
            Map<Object, Duration> windows = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Duration> e : windowsByPhase.entrySet()) {
                windows.put(phase(e.getKey()), e.getValue());
            }
            Constructor<?> c = t.getDeclaredConstructor(Clock.class, Instant.class, Map.class, Instant.class);
            c.setAccessible(true);
            return c.newInstance(clock, t0, windows, deadlineAt);
        } catch (InvocationTargetException e) {
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail("SearchBudget(Clock, Instant, Map<Phase, Duration>, Instant) is missing: " + e);
        }
    }

    static Object phase(String name) {
        for (Class<?> c : cls(BUDGET).getDeclaredClasses()) {
            if (c.getSimpleName().equals("Phase") && c.isEnum()) {
                for (Object k : c.getEnumConstants()) {
                    if (((Enum<?>) k).name().equals(name)) {
                        return k;
                    }
                }
                return fail("SearchBudget.Phase has no constant " + name);
            }
        }
        return fail("SearchBudget.Phase (enum) does not exist");
    }

    static Duration remaining(Object budget, String phase) {
        return (Duration) instanceCall(budget, "remaining", phase);
    }

    static boolean expired(Object budget, String phase) {
        return (Boolean) instanceCall(budget, "expired", phase);
    }

    private static Object instanceCall(Object budget, String name, String phase) {
        try {
            Method m = cls(BUDGET).getMethod(name, phaseType());
            m.setAccessible(true);
            return m.invoke(budget, phase(phase));
        } catch (InvocationTargetException e) {
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail("SearchBudget." + name + "(Phase) is missing: " + e);
        }
    }

    private static Class<?> phaseType() {
        for (Class<?> c : cls(BUDGET).getDeclaredClasses()) {
            if (c.getSimpleName().equals("Phase")) {
                return c;
            }
        }
        return fail("SearchBudget.Phase does not exist");
    }

    // ---- SourceRetrieval with a NewsSearchProvider -------------------------------------------------------------------

    /** {@code new SourceRetrieval(NewsSearchProvider, ArticleMetadataFetcher, SourceQualityTable, Clock, Duration, int)}. */
    static SourceRetrieval retrieval(Object searchProvider, ArticleMetadataFetcher fetcher, SourceQualityTable table, Clock clock,
                                     Duration searchWindow, int fetchConcurrency) {
        try {
            Constructor<SourceRetrieval> c = SourceRetrieval.class.getDeclaredConstructor(cls(PROVIDER), ArticleMetadataFetcher.class,
                SourceQualityTable.class, Clock.class, Duration.class, int.class);
            c.setAccessible(true);
            return c.newInstance(searchProvider, fetcher, table, clock, searchWindow, fetchConcurrency);
        } catch (InvocationTargetException e) {
            return unwrap(e);
        } catch (ReflectiveOperationException e) {
            return fail("SourceRetrieval(NewsSearchProvider, ArticleMetadataFetcher, SourceQualityTable, Clock, Duration, int) is missing: " + e);
        }
    }

    /** A {@code NewsSearchProvider} whose every text is answered by {@code per} (status and articles of one query). */
    static Object providerOf(java.util.function.Function<String, QR> per) {
        Class<?> iface = cls(PROVIDER);
        InvocationHandler h = (proxy, method, args) -> {
            if (method.getDeclaringClass() == Object.class) {
                return switch (method.getName()) {
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "equals" -> proxy == args[0];
                    default -> "provider";
                };
            }
            @SuppressWarnings("unchecked")
            List<String> texts = (List<String>) args[0];
            Class<?> qr = null;
            for (Class<?> c : iface.getDeclaredClasses()) {
                if (c.getSimpleName().equals("QueryResult")) {
                    qr = c;
                }
            }
            if (qr == null) {
                throw new IllegalStateException("NewsSearchProvider.QueryResult is missing");
            }
            Constructor<?> ctor = qr.getDeclaredConstructor(SearchQueryStatus.class, List.class);
            ctor.setAccessible(true);
            List<Object> out = new ArrayList<>();
            for (String t : texts) {
                QR r = per.apply(t);
                out.add(ctor.newInstance(r.status(), r.articles()));
            }
            return out;
        };
        return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, h);
    }

    // ---- startup from properties ------------------------------------------------------------------------------------

    /** Starts a context with the Spring-managed {@code GoogleNewsSearch} (the {@code @Value} constructor) over a stub provider. */
    static void googleSearchFromProperties(Map<String, String> properties, Consumer<Throwable> startupFailure) {
        Class<?> t = cls(SEARCH);
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(NewsProvider.class, () -> (q, max, timeout) -> NewsProvider.Result.failed())
            .withUserConfiguration(t);
        runner.withPropertyValues(pairs(properties)).run(ctx -> startupFailure.accept(ctx.getStartupFailure()));
    }

    /** Starts a context with the Spring-managed {@code SourceRetrieval} (the {@code @Value} constructor) over test doubles. */
    static void retrievalFromProperties(Map<String, String> properties, Consumer<Throwable> startupFailure) {
        Class<?> iface = cls(PROVIDER);
        Object provider = providerOf(t -> new QR(SearchQueryStatus.EMPTY, List.of()));
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(ArticleMetadataFetcher.class, () -> Mockito.mock(ArticleMetadataFetcher.class))
            .withBean(SourceQualityTable.class, () -> new SourceQualityTable("who.int", "nature.com", "reuters.com", "medium.com"))
            .withBean((Class) iface, () -> provider)
            .withUserConfiguration(SourceRetrieval.class);
        runner.withPropertyValues(pairs(properties)).run(ctx -> startupFailure.accept(ctx.getStartupFailure()));
    }

    private static String[] pairs(Map<String, String> properties) {
        return properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
    }

    /** The messages of the whole cause chain, joined. */
    static String chain(Throwable e) {
        StringBuilder sb = new StringBuilder();
        for (Throwable c = e; c != null; c = c.getCause() == c ? null : c.getCause()) {
            sb.append(c.getClass().getSimpleName()).append(": ").append(c.getMessage()).append('\n');
        }
        return sb.toString();
    }
}
