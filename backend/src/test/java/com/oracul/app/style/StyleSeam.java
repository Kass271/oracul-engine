package com.oracul.app.style;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

import com.oracul.app.api.WritingStylesApi;
import com.oracul.app.common.ApiExceptionHandler;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Reaches the production classes of the style catalogue by name, so the tests compile before the classes exist and then
 * fail on an assertion ("class missing") instead of on a compile error. The names and shapes are the ones the spec
 * "Backend shape" fixes: com.oracul.app.style.WritingStyles / WritingStylesController implements WritingStylesApi.
 */
final class StyleSeam {

    static final String STYLES = "com.oracul.app.style.WritingStyles";
    static final String CONTROLLER = "com.oracul.app.style.WritingStylesController";
    static final String DEFAULT_LOCATION = "classpath:writing-styles.json";

    private StyleSeam() {
    }

    static Class<?> stylesClass() {
        return load(STYLES);
    }

    static Class<?> controllerClass() {
        return load(CONTROLLER);
    }

    private static Class<?> load(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            return fail("production class " + name + " does not exist yet (spec writing-styles.md FR-62 'Backend shape')");
        }
    }

    /** A context runner with WritingStyles + WritingStylesController, reading the given location (null = default). */
    static ApplicationContextRunner runner(String location) {
        ApplicationContextRunner r = new ApplicationContextRunner().withUserConfiguration(stylesClass(), controllerClass());
        return location == null ? r : r.withPropertyValues("oracul.writing-styles.location=" + location);
    }

    /** Only the WritingStyles bean (the startup validation). */
    static ApplicationContextRunner stylesOnly(String location) {
        ApplicationContextRunner r = new ApplicationContextRunner().withUserConfiguration(stylesClass());
        return location == null ? r : r.withPropertyValues("oracul.writing-styles.location=" + location);
    }

    /** The controller of the context behind a MockMvc, with the production exception handler. */
    static MockMvc mvc(AssertableApplicationContext ctx) {
        assertThat(ctx).hasNotFailed();
        WritingStylesApi api = (WritingStylesApi) ctx.getBean(controllerClass());
        return MockMvcBuilders.standaloneSetup(api).setControllerAdvice(new ApiExceptionHandler()).build();
    }

    /** The deepest cause of the startup failure. */
    static Throwable rootCause(AssertableApplicationContext ctx) {
        assertThat(ctx).hasFailed();
        Throwable t = ctx.getStartupFailure();
        Throwable root = NestedExceptionUtils.getMostSpecificCause(t);
        return root == null ? t : root;
    }

    // ---- the domain API through reflection: profiles() and find(id) ----

    /** One profile of the domain API, read through its record accessors. */
    record Profile(String id, String mode, String label, String characteristics, String instructions) {
    }

    static List<Profile> profiles(Object styles) {
        try {
            Method m = stylesClass().getMethod("profiles");
            return ((List<?>) m.invoke(styles)).stream().map(StyleSeam::profile).toList();
        } catch (ReflectiveOperationException e) {
            return fail("WritingStyles.profiles() is not callable: " + e);
        }
    }

    static Optional<Profile> find(Object styles, String id) {
        try {
            Method m = stylesClass().getMethod("find", String.class);
            return ((Optional<?>) m.invoke(styles, id)).map(StyleSeam::profile);
        } catch (ReflectiveOperationException e) {
            return fail("WritingStyles.find(String) is not callable: " + e);
        }
    }

    private static Profile profile(Object p) {
        try {
            Class<?> c = p.getClass();
            Enum<?> mode = (Enum<?>) c.getMethod("mode").invoke(p);
            return new Profile((String) c.getMethod("id").invoke(p), mode.name(), (String) c.getMethod("label").invoke(p),
                (String) c.getMethod("characteristics").invoke(p), (String) c.getMethod("instructions").invoke(p));
        } catch (ReflectiveOperationException e) {
            return fail("WritingStyleProfile record accessors are not callable: " + e);
        }
    }
}
