package com.oracul.app.result;

import com.oracul.app.api.model.EvidencePack;
import com.oracul.app.api.model.FutureStory;
import com.oracul.app.api.model.ResearchCounts;
import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.ScenarioMetadata;
import com.oracul.app.api.model.StructuredScenario;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Pure (no Spring) driver of the slice 09 classes (future-result.md "Slice 09_future-story"): Datelines, StoryParser,
 * StoryWritingPrompt, HorizonLabels and ScenarioMetadataMapper. They are reached by reflection so RED compiles whatever
 * they look like; a missing class or method is an AssertionError naming it. Generated API models are used directly.
 */
final class StoryHarness {

    static final String PKG = "com.oracul.app.result.";

    private StoryHarness() {}

    static Class<?> type(String name) {
        try {
            return Class.forName(PKG + name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(PKG + name + " is missing");
        }
    }

    static Object constant(String className, String field) {
        try {
            var f = type(className).getDeclaredField(field);
            f.setAccessible(true);
            return f.get(null);
        } catch (NoSuchFieldException e) {
            throw new AssertionError(className + "." + field + " is missing");
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object target(Class<?> type, Method m) {
        if (Modifier.isStatic(m.getModifiers())) return null;
        for (Constructor<?> c : type.getDeclaredConstructors()) {
            if (c.getParameterCount() == 0) {
                try {
                    c.setAccessible(true);
                    return c.newInstance();
                } catch (ReflectiveOperationException e) {
                    throw new AssertionError(type.getSimpleName() + " cannot be instantiated: " + e, e);
                }
            }
        }
        throw new AssertionError(type.getSimpleName() + "." + m.getName() + " is not static and the class has no no-arg constructor");
    }

    static Object call(String className, String method, Object... args) {
        Class<?> type = type(className);
        Method m = null;
        for (Method c : type.getDeclaredMethods()) {
            if (c.getName().equals(method) && c.getParameterCount() == args.length) m = c;
        }
        if (m == null) throw new AssertionError(className + "." + method + "(" + args.length + " args) is missing");
        try {
            m.setAccessible(true);
            return m.invoke(target(type, m), args);
        } catch (InvocationTargetException e) {
            throw new AssertionError(className + "." + method + " failed: " + e.getTargetException(), e.getTargetException());
        } catch (IllegalAccessException e) {
            throw new AssertionError(e);
        }
    }

    private static Object accessor(Object record, String name) {
        try {
            Method m = record.getClass().getDeclaredMethod(name);
            m.setAccessible(true);
            return m.invoke(record);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(record.getClass().getSimpleName() + "." + name + "() is missing: " + e, e);
        }
    }

    // ---- Datelines / HorizonLabels -------------------------------------------------------------------------------

    static String dateline(LocalDate d) {
        return (String) call("Datelines", "format", d);
    }

    static String horizonLabel(Object horizonCode) {
        return (String) call("HorizonLabels", "label", horizonCode);
    }

    // ---- StoryParser ---------------------------------------------------------------------------------------------

    record Parsed(Optional<FutureStory> story, List<String> errors, boolean onlyDateErrors) {}

    @SuppressWarnings("unchecked")
    static Parsed parse(Optional<String> outputText, LocalDate cutoff, LocalDate windowEnd) {
        Object result = call("StoryParser", "parse", outputText, cutoff, windowEnd);
        return new Parsed((Optional<FutureStory>) accessor(result, "story"), (List<String>) accessor(result, "errors"),
            (Boolean) accessor(result, "onlyDateErrors"));
    }

    // ---- StoryWritingPrompt --------------------------------------------------------------------------------------

    /** StoryRequest(attempt, storyErrors) built reflectively. */
    static Object storyRequest(int attempt, List<String> errors) {
        Class<?> type = type("StoryRequest");
        if (!type.isRecord()) throw new AssertionError("StoryRequest must be a record");
        RecordComponent[] comps = type.getRecordComponents();
        if (comps.length != 2) throw new AssertionError("StoryRequest must have 2 components, has " + comps.length);
        try {
            Constructor<?> ctor = type.getDeclaredConstructor(comps[0].getType(), comps[1].getType());
            ctor.setAccessible(true);
            return ctor.newInstance(attempt, new ArrayList<>(errors));
        } catch (InvocationTargetException e) {
            throw new AssertionError("StoryRequest constructor failed: " + e.getTargetException(), e.getTargetException());
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("StoryRequest has an unexpected canonical constructor: " + e, e);
        }
    }

    static String instructions() {
        return (String) constant("StoryWritingPrompt", "INSTRUCTIONS");
    }

    static String inputText(EvidencePack pack, StructuredScenario scenario, Object request) {
        return (String) call("StoryWritingPrompt", "inputText", pack, scenario, request);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> body(String model, EvidencePack pack, StructuredScenario scenario, Object request) {
        return (Map<String, Object>) call("StoryWritingPrompt", "body", model, pack, scenario, request);
    }

    // ---- ScenarioMetadataMapper ----------------------------------------------------------------------------------

    static ScenarioMetadata map(ScenarioConfiguration cfg, ResearchCounts counts) {
        return (ScenarioMetadata) call("ScenarioMetadataMapper", "map", cfg, counts);
    }
}
