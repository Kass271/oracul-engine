package com.oracul.app.research;

import static org.junit.jupiter.api.Assertions.fail;

import com.oracul.app.api.model.ScenarioConfiguration;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.chatgpt.HttpResponsesClient;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.mockito.Mockito;
import org.springframework.boot.convert.ApplicationConversionService;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Reflective access to the seams of slice 04_wildcard-queries (wildcard-search.md FR-50 / FR-51 "Slice 04 delta"):
 * {@code QueryRules}, {@code QueryGenerationPrompt}, {@code QueryGenerator} and {@code QueryTemplates.label}. None of
 * them exists while the RED tests are written; going through reflection keeps every test compiling, so a test fails on its
 * assertion ("... does not exist yet") and not on a compile error. Names and signatures are exactly the ones of the spec.
 */
final class QuerySupport {

    static final String RULES = "com.oracul.app.research.QueryRules";
    static final String PROMPT = "com.oracul.app.research.QueryGenerationPrompt";
    static final String GENERATOR = "com.oracul.app.research.QueryGenerator";
    static final String TEMPLATES = "com.oracul.app.research.QueryTemplates";

    private QuerySupport() {
    }

    static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return fail(name + " does not exist yet (wildcard-search.md FR-50 / FR-51, slice 04_wildcard-queries)");
        }
    }

    private static Object staticCall(String className, String name, Class<?>[] types, Object... args) {
        Class<?> t = cls(className);
        try {
            Method m = t.getDeclaredMethod(name, types);
            m.setAccessible(true);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof RuntimeException re) {
                throw re;
            }
            if (c instanceof Error err) {
                throw err;
            }
            throw new IllegalStateException(c);
        } catch (ReflectiveOperationException e) {
            return fail(t.getSimpleName() + "." + name + " is missing or has another signature: " + e);
        }
    }

    // ---- QueryTemplates.label ---------------------------------------------------------------------------------------

    /** {@code public static String QueryTemplates.label(String)} (FR-51 step 6, "L"). */
    static String label(String raw) {
        return (String) staticCall(TEMPLATES, "label", new Class<?>[] {String.class}, raw);
    }

    // ---- QueryRules (rule Q) ----------------------------------------------------------------------------------------

    /** {@code public static String QueryRules.clean(String)}: null stays null; trim, inner whitespace to one space. */
    static String clean(String raw) {
        return (String) staticCall(RULES, "clean", new Class<?>[] {String.class}, raw);
    }

    /** {@code public static boolean QueryRules.valid(String)}: rule Q on the cleaned text; null / blank is false. */
    static boolean valid(String text) {
        return (Boolean) staticCall(RULES, "valid", new Class<?>[] {String.class}, text);
    }

    // ---- QueryGenerationPrompt --------------------------------------------------------------------------------------

    /** {@code public static final String QueryGenerationPrompt.INSTRUCTIONS}. */
    static String instructions() {
        try {
            return (String) cls(PROMPT).getField("INSTRUCTIONS").get(null);
        } catch (ReflectiveOperationException e) {
            return fail("QueryGenerationPrompt.INSTRUCTIONS is missing: " + e);
        }
    }

    /** {@code public static String QueryGenerationPrompt.input(WildcardPipeline, ScenarioConfiguration, String horizonLabel)}. */
    static String input(WildcardPipeline p, ScenarioConfiguration cfg, String horizonLabel) {
        return (String) staticCall(PROMPT, "input", new Class<?>[] {WildcardPipeline.class, ScenarioConfiguration.class, String.class},
            p, cfg, horizonLabel);
    }

    /** {@code public static Map<String, Object> QueryGenerationPrompt.body(String model, String inputText)}. */
    @SuppressWarnings("unchecked")
    static Map<String, Object> body(String model, String inputText) {
        return (Map<String, Object>) staticCall(PROMPT, "body", new Class<?>[] {String.class, String.class}, model, inputText);
    }

    // ---- QueryGenerator pure helpers --------------------------------------------------------------------------------

    /** {@code static List<String> QueryGenerator.parse(String outputText)}; null = unusable. */
    @SuppressWarnings("unchecked")
    static List<String> parse(String outputText) {
        return (List<String>) staticCall(GENERATOR, "parse", new Class<?>[] {String.class}, outputText);
    }

    /** {@code static WildcardPipeline QueryGenerator.merge(WildcardPipeline template, List<String> modelTexts, List<String> templateTexts)}. */
    static WildcardPipeline merge(WildcardPipeline template, List<String> modelTexts, List<String> templateTexts) {
        return (WildcardPipeline) staticCall(GENERATOR, "merge", new Class<?>[] {WildcardPipeline.class, List.class, List.class},
            template, modelTexts, templateTexts);
    }

    // ---- startup from properties ------------------------------------------------------------------------------------

    /** Starts a context with the Spring-managed {@code QueryGenerator} (the {@code @Value} constructor) over test doubles. */
    static void generatorFromProperties(Map<String, String> properties, Consumer<Throwable> startupFailure) {
        Class<?> t = cls(GENERATOR);
        ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(ctx -> ctx.getBeanFactory().setConversionService(ApplicationConversionService.getSharedInstance()))
            .withBean(Clock.class, Clock::systemUTC)
            .withBean(HttpResponsesClient.class, () -> Mockito.mock(HttpResponsesClient.class))
            .withBean(QueryTemplates.class, QueryTemplates::new)
            .withUserConfiguration(t);
        runner.withPropertyValues(properties.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toArray(String[]::new))
            .run(ctx -> startupFailure.accept(ctx.getStartupFailure()));
    }
}
