package com.oracul.app.research;

import com.oracul.app.api.model.NormalizedEvent;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceType;
import com.oracul.app.chatgpt.HttpResponsesClient;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.URI;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.mockito.Mockito;

/**
 * Pure (no Spring, no HTTP) driver of {@code EventNormalizer} for {@link EventNormalizerTest}. The normalizer and its
 * collaborators are reached by reflection so RED compiles whatever the constructor looks like: constructor arguments
 * are resolved by type (HttpResponsesClient = scripted stub, RunGuard = always passes, Clock = system clock) and the int
 * settings by parameter name (batch / concurrency / max...), falling back to declaration order batch size, concurrency,
 * max sources. Seam used: {@code HttpResponsesClient.createTextOrThrow(UUID, Map)} answers the output text; the
 * normalizer's public {@code normalize(...)} takes the session / run UUIDs and the sources and returns the events.
 */
final class NormalizerHarness {

    /** One request of the normalizer: its input text, batch number k and the attempt number within this batch (1, 2). */
    record Call(String input, int batch, int attempt) {}

    /** Text of the model answer, or null for "200 without output text". */
    interface Answerer extends Function<Call, String> {}

    final List<Call> calls = new CopyOnWriteArrayList<>();
    private final Map<Integer, AtomicInteger> attempts = new ConcurrentHashMap<>();
    private final Object normalizer;
    private final Method normalize;

    NormalizerHarness(int batchSize, int concurrency, int maxSources, Answerer answerer) {
        HttpResponsesClient client = Mockito.mock(HttpResponsesClient.class, inv -> {
            String name = inv.getMethod().getName();
            if (name.equals("model")) return "stub-model";
            if (name.startsWith("createText")) {
                String input = inputOf(inv.getArguments());
                int batch = StubResponses.batch(input);
                Call call = new Call(input, batch, attempts.computeIfAbsent(batch, k -> new AtomicInteger()).incrementAndGet());
                calls.add(call);
                return java.util.Optional.ofNullable(answerer.apply(call));
            }
            return null;
        });
        this.normalizer = construct(client, batchSize, concurrency, maxSources);
        this.normalize = findNormalize(normalizer.getClass());
    }

    @SuppressWarnings("unchecked")
    private static String inputOf(Object[] args) {
        for (Object a : args) {
            if (a instanceof Map<?, ?> body) {
                List<Map<String, Object>> input = (List<Map<String, Object>>) body.get("input");
                List<Map<String, Object>> content = (List<Map<String, Object>>) input.get(0).get("content");
                return (String) content.get(0).get("text");
            }
        }
        throw new AssertionError("the normalizer called the Responses client without a request body Map");
    }

    private static Object construct(HttpResponsesClient client, int batchSize, int concurrency, int maxSources) {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.research.EventNormalizer");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.research.EventNormalizer is missing");
        }
        Constructor<?>[] ctors = type.getDeclaredConstructors();
        Constructor<?> ctor = ctors[0];
        for (Constructor<?> c : ctors) if (c.getParameterCount() > ctor.getParameterCount()) ctor = c;
        Parameter[] params = ctor.getParameters();
        Object[] args = new Object[params.length];
        int[] ordered = {batchSize, concurrency, maxSources};
        int nextInt = 0;
        for (int i = 0; i < params.length; i++) {
            Class<?> t = params[i].getType();
            String name = params[i].getName().toLowerCase();
            if (t == HttpResponsesClient.class) {
                args[i] = client;
            } else if (t == int.class || t == Integer.class) {
                if (name.contains("batch")) args[i] = batchSize;
                else if (name.contains("concurr") || name.contains("parallel")) args[i] = concurrency;
                else if (name.contains("max") || name.contains("cap")) args[i] = maxSources;
                else args[i] = ordered[Math.min(nextInt, 2)];
                nextInt++;
            } else if (t == Clock.class) {
                args[i] = Clock.systemUTC();
            } else if (t.getSimpleName().equals("RunGuard")) {
                // a guard that never stops the run: boolean checks pass, everything else answers null/void
                args[i] = Mockito.mock(t, inv -> inv.getMethod().getReturnType() == boolean.class ? Boolean.TRUE : null);
            } else {
                throw new AssertionError("EventNormalizer constructor parameter " + params[i] + " is not supported by NormalizerHarness");
            }
        }
        try {
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("EventNormalizer constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("EventNormalizer cannot be instantiated: " + e, e);
        }
    }

    private static Method findNormalize(Class<?> type) {
        for (Method m : type.getDeclaredMethods()) {
            if (m.getName().equals("normalize") && java.lang.reflect.Modifier.isPublic(m.getModifiers())) {
                m.setAccessible(true);
                return m;
            }
        }
        throw new AssertionError("EventNormalizer.normalize(...) is missing");
    }

    @SuppressWarnings("unchecked")
    List<NormalizedEvent> normalize(List<Source> sources) {
        Object[] args = new Object[normalize.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = normalize.getParameterTypes()[i];
            if (t == UUID.class) args[i] = UUID.randomUUID();
            else if (List.class.isAssignableFrom(t)) args[i] = new ArrayList<>(sources);
            else throw new AssertionError("EventNormalizer.normalize parameter type " + t + " is not supported by NormalizerHarness");
        }
        try {
            return (List<NormalizedEvent>) normalize.invoke(normalizer, args);
        } catch (InvocationTargetException e) {
            throw new AssertionError("EventNormalizer.normalize failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    // ---- builders ---------------------------------------------------------------------------------------------

    /** A stored source; publishedAt may be null. */
    static Source source(String id, String title, String topic, double quality, OffsetDateTime publishedAt) {
        Source s = new Source(id, URI.create("http://127.0.0.1/articles/" + id), "Stub Site", title,
            OffsetDateTime.parse("2026-10-03T00:00:00Z"), SourceType.NEWS, quality, true);
        s.setPublishedAt(publishedAt);
        s.setSummary("Summary of " + id);
        s.setTopic(topic);
        s.setEntities(new ArrayList<>());
        return s;
    }

    static OffsetDateTime day(int d) {
        return OffsetDateTime.parse(String.format("2026-09-%02dT12:00:00Z", d));
    }

    /** Event JSON; every argument except the ids is a raw JSON fragment. */
    static String ev(String ids, String date, String category, String entities, String summary, String disagreement, String confidence) {
        return "{\"sourceIds\":" + ids + ",\"date\":" + date + ",\"category\":" + category + ",\"entities\":" + entities
            + ",\"summary\":" + summary + ",\"disagreement\":" + disagreement + ",\"confidence\":" + confidence + "}";
    }

    static String answer(String... events) {
        return "{\"events\":[" + String.join(",", events) + "]}";
    }

    static String json(String s) {
        return StubResponses.jsonString(s);
    }

    /** One event per source id of the request, entity "Entity <id>" (the StubResponses default answer). */
    static String defaultAnswer(Call call) {
        return StubResponses.defaultNormalization(call.input());
    }
}
