package com.oracul.app.research;

import static org.junit.jupiter.api.Assertions.fail;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Reflective access to {@code com.oracul.app.research.SafeFetcher} (article-retrieval.md FR-56, slice 02). The class does
 * not exist when the RED tests are written; going through reflection keeps every test compiling, so a test fails on its
 * assertion ("SafeFetcher does not exist yet") and not on a compile error. The names and signatures used here are
 * exactly the ones of the slice spec.
 */
final class SafeFetcherSupport {

    static final String CLASS = "com.oracul.app.research.SafeFetcher";

    private SafeFetcherSupport() {
    }

    /** Answer of one fetch: the fields of the spec's {@code SafeFetcher.Result}; {@code outcome} is the enum constant name. */
    record Res(String outcome, int status, String contentType, byte[] body, boolean truncated, URI finalUri, int redirects) {
        int bodyLength() {
            return body == null ? -1 : body.length;
        }
    }

    static Class<?> type() {
        try {
            return Class.forName(CLASS);
        } catch (ClassNotFoundException e) {
            return fail(CLASS + " does not exist yet (article-retrieval.md FR-56, slice 02_safe-fetching)");
        }
    }

    private static Class<?> nested(String simpleName) {
        for (Class<?> c : type().getDeclaredClasses()) {
            if (c.getSimpleName().equals(simpleName)) {
                return c;
            }
        }
        return fail(CLASS + "." + simpleName + " does not exist (article-retrieval.md FR-56)");
    }

    /** Scripted {@code SafeFetcher.HostResolver}: host (lower-case) to addresses; unknown hosts throw UnknownHostException. */
    static final class Resolver {
        final Map<String, List<InetAddress>> table = new ConcurrentHashMap<>();
        final List<String> calls = new CopyOnWriteArrayList<>();
        /** Lookups take this long and ignore interrupts (a slow DNS answer); 0 = immediate. */
        volatile long uninterruptibleDelayMs;
        /** When set, every lookup throws this Error (a failure no checked handling expects). */
        volatile Error failWith;

        Resolver map(String host, String... addresses) {
            List<InetAddress> list = new ArrayList<>();
            for (String a : addresses) {
                try {
                    list.add(InetAddress.getByName(a));
                } catch (UnknownHostException e) {
                    throw new IllegalArgumentException(a, e);
                }
            }
            table.put(host.toLowerCase(Locale.ROOT), list);
            return this;
        }

        Resolver mapAddresses(String host, InetAddress... addresses) {
            table.put(host.toLowerCase(Locale.ROOT), List.of(addresses));
            return this;
        }

        Object proxy() {
            Class<?> iface = nested("HostResolver");
            InvocationHandler h = (proxy, method, args) -> {
                if (method.getDeclaringClass() == Object.class) {
                    return switch (method.getName()) {
                        case "hashCode" -> System.identityHashCode(proxy);
                        case "equals" -> proxy == args[0];
                        default -> "resolver";
                    };
                }
                String host = (String) args[0];
                calls.add(host);
                if (failWith != null) {
                    throw failWith;
                }
                long until = System.nanoTime() + uninterruptibleDelayMs * 1_000_000L;
                while (System.nanoTime() < until) {
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                        // a resolver that does not react to interrupts
                    }
                }
                List<InetAddress> found = table.get(host.toLowerCase(Locale.ROOT));
                if (found == null) {
                    throw new UnknownHostException(host);
                }
                return new ArrayList<>(found);
            };
            return Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[] {iface}, h);
        }
    }

    /** One {@code SafeFetcher} instance. */
    static final class Fx {
        private final Object target;

        Fx(Object target) {
            this.target = target;
        }

        Res fetch(String url) {
            return call("fetch", new Class<?>[] {URI.class}, URI.create(url));
        }

        Res fetch(String url, Duration timeout) {
            return call("fetch", new Class<?>[] {URI.class, Duration.class}, URI.create(url), timeout);
        }

        /** {@code fetch(URI, Duration, boolean sameHostOnly)} (article-retrieval.md slice 08 "SafeFetcher (additive)"). */
        Res fetch(String url, Duration timeout, boolean sameHostOnly) {
            return call("fetch", new Class<?>[] {URI.class, Duration.class, boolean.class}, URI.create(url), timeout, sameHostOnly);
        }

        Object raw() {
            return target;
        }

        private Res call(String name, Class<?>[] types, Object... args) {
            try {
                Method m = type().getMethod(name, types);
                m.setAccessible(true);
                return toRes(m.invoke(target, args));
            } catch (InvocationTargetException e) {
                if (e.getCause() instanceof RuntimeException re) {
                    throw re;
                }
                if (e.getCause() instanceof Error err) {
                    throw err;
                }
                throw new IllegalStateException(e.getCause());
            } catch (ReflectiveOperationException e) {
                return fail("SafeFetcher." + name + " is missing or has another signature: " + e);
            }
        }
    }

    private static Res toRes(Object result) throws ReflectiveOperationException {
        if (result == null) {
            return fail("SafeFetcher.fetch returned null");
        }
        Class<?> c = result.getClass();
        return new Res(
            String.valueOf(get(c, result, "outcome")),
            (Integer) get(c, result, "status"),
            (String) get(c, result, "contentType"),
            (byte[]) get(c, result, "body"),
            (Boolean) get(c, result, "truncated"),
            (URI) get(c, result, "finalUri"),
            (Integer) get(c, result, "redirects"));
    }

    private static Object get(Class<?> c, Object o, String accessor) throws ReflectiveOperationException {
        Method m = c.getDeclaredMethod(accessor);
        m.setAccessible(true);
        return m.invoke(o);
    }

    /** The package-private test constructor {@code SafeFetcher(HostResolver, Set<String>, Duration, int, int)}. */
    static Fx fetcher(Resolver resolver, Set<String> allowedPrivateHosts, Duration timeout, int maxBytes, int maxRedirects) {
        Class<?> t = type();
        try {
            Constructor<?> ctor = t.getDeclaredConstructor(nested("HostResolver"), Set.class, Duration.class, int.class, int.class);
            ctor.setAccessible(true);
            return new Fx(ctor.newInstance(resolver.proxy(), allowedPrivateHosts, timeout, maxBytes, maxRedirects));
        } catch (InvocationTargetException e) {
            return fail("SafeFetcher test constructor threw " + e.getCause());
        } catch (ReflectiveOperationException e) {
            return fail("SafeFetcher(HostResolver, Set<String>, Duration, int, int) is missing: " + e);
        }
    }

    /** {@code new ArticleMetadataFetcher(SafeFetcher)} (package-private constructor of the slice spec). */
    static ArticleMetadataFetcher metadataFetcher(Fx fx) {
        try {
            Constructor<ArticleMetadataFetcher> ctor = ArticleMetadataFetcher.class.getDeclaredConstructor(type());
            ctor.setAccessible(true);
            return ctor.newInstance(fx.raw());
        } catch (InvocationTargetException e) {
            return fail("ArticleMetadataFetcher(SafeFetcher) threw " + e.getCause());
        } catch (ReflectiveOperationException e) {
            return fail("ArticleMetadataFetcher(SafeFetcher) is missing: " + e);
        }
    }

    /** The package-private pure check {@code static boolean SafeFetcher.isBlocked(InetAddress)}. */
    static boolean isBlocked(InetAddress address) {
        try {
            Method m = type().getDeclaredMethod("isBlocked", InetAddress.class);
            m.setAccessible(true);
            return (Boolean) m.invoke(null, address);
        } catch (InvocationTargetException e) {
            return fail("SafeFetcher.isBlocked threw " + e.getCause());
        } catch (ReflectiveOperationException e) {
            return fail("static boolean SafeFetcher.isBlocked(InetAddress) is missing: " + e);
        }
    }

    /** Builds the Spring-managed bean from properties (the {@code @Value} constructor) and hands it to {@code body}. */
    static void withSpring(Map<String, String> properties, Consumer<Fx> body) {
        Class<?> t = type();
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withUserConfiguration(t);
        String[] pairs = properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new);
        runner.withPropertyValues(pairs).run(ctx -> {
            if (ctx.getStartupFailure() != null) {
                fail("SafeFetcher does not start from properties " + properties + ": " + rootCause(ctx.getStartupFailure()));
            }
            body.accept(new Fx(ctx.getBean(t)));
        });
    }

    private static String rootCause(Throwable e) {
        Throwable c = e;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        return c.toString();
    }
}
