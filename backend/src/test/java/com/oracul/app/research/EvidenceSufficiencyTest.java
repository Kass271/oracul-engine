package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * generation-runs.md "Slice 12_insufficient-evidence" EvidenceSufficiencyTest. {@code MinCoreThresholds} and
 * {@code EvidenceSufficiency} are reached by reflection so RED compiles before they exist.
 */
// @trace FR-31
class EvidenceSufficiencyTest {

    private static final String THRESHOLDS = "com.oracul.app.research.MinCoreThresholds";
    private static final String SUFFICIENCY = "com.oracul.app.research.EvidenceSufficiency";

    private static Class<?> cls(String name) {
        try {
            return Class.forName(name);
        } catch (ClassNotFoundException e) {
            throw new AssertionError(name + " is missing");
        }
    }

    private static Object invoke(Method m, Object target, Object... args) throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    private static Object defaults() throws Throwable {
        Method m;
        try {
            m = cls(THRESHOLDS).getMethod("defaults");
        } catch (NoSuchMethodException e) {
            throw new AssertionError("MinCoreThresholds.defaults() is missing");
        }
        return invoke(m, null);
    }

    private static Object thresholds(int high, int medium, int low) throws Throwable {
        return cls(THRESHOLDS).getConstructor(int.class, int.class, int.class).newInstance(high, medium, low);
    }

    private static int forRealism(Object t, int realism) throws Throwable {
        return (int) invoke(cls(THRESHOLDS).getMethod("forRealism", int.class), t, realism);
    }

    private static boolean sufficient(int core, int realism, Object t) throws Throwable {
        return (boolean) invoke(cls(SUFFICIENCY).getMethod("sufficient", int.class, int.class, cls(THRESHOLDS)), null, core, realism, t);
    }

    private static OptionalInt suggested(int realism) throws Throwable {
        return (OptionalInt) invoke(cls(SUFFICIENCY).getMethod("suggestedRealism", int.class), null, realism);
    }

    @Test
    void defaultThresholdsPerRealism() throws Throwable {
        int[] expected = {1, 1, 1, 1, 1, 3, 3, 3, 5, 5};
        Object d = defaults();
        for (int r = 1; r <= 10; r++) assertThat(forRealism(d, r)).as("realism " + r).isEqualTo(expected[r - 1]);
    }

    @Test
    void defaultsAreFiveThreeOne() throws Throwable {
        assertThat(defaults()).isEqualTo(thresholds(5, 3, 1));
    }

    @ParameterizedTest(name = "realism {0} is rejected")
    @CsvSource({"0", "11", "-1"})
    void realismOutsideOneToTenIsRejected(int realism) throws Throwable {
        Object d = defaults();
        assertThatThrownBy(() -> forRealism(d, realism)).isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest(name = "realism {0}, core {1} -> sufficient {2}")
    @CsvSource({
        "10,4,false", "10,5,true", "9,4,false", "9,5,true", "8,2,false", "8,3,true", "7,2,false", "7,3,true",
        "6,2,false", "6,3,true", "5,0,false", "5,1,true", "2,0,false", "2,1,true", "1,0,false", "1,1,true",
        "10,10,true", "10,0,false"})
    void sufficientOnBothSidesOfTheBoundary(int realism, int core, boolean expected) throws Throwable {
        assertThat(sufficient(core, realism, defaults())).isEqualTo(expected);
    }

    @Test
    void thresholdZeroDisablesTheCheck() throws Throwable {
        Object zero = thresholds(0, 0, 0);
        for (int r = 1; r <= 10; r++) assertThat(sufficient(0, r, zero)).as("realism " + r).isTrue();
    }

    @ParameterizedTest(name = "realism {0} -> suggested {1}")
    @CsvSource({"2,1", "3,1", "4,2", "5,3", "8,6", "10,8"})
    void suggestedRealismIsTwoLower(int realism, int expected) throws Throwable {
        assertThat(suggested(realism)).isEqualTo(OptionalInt.of(expected));
    }

    @Test
    void noSuggestionAtRealismOne() throws Throwable {
        assertThat(suggested(1)).isEqualTo(OptionalInt.empty());
    }
}
