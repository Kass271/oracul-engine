package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** research-pipeline.md "FR-14 Tokens / Jaccard": TokenSimilarity.jaccard(a, b), pure. Reached by reflection so RED compiles. */
// @trace FR-14
class TokenSimilarityTest {

    private static double jaccard(String a, String b) {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.research.TokenSimilarity");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.research.TokenSimilarity is missing");
        }
        try {
            Method m = type.getDeclaredMethod("jaccard", String.class, String.class);
            m.setAccessible(true);
            Object target = Modifier.isStatic(m.getModifiers()) ? null : PlanSupport.newInstance(type.getName());
            return ((Number) m.invoke(target, a, b)).doubleValue();
        } catch (NoSuchMethodException e) {
            throw new AssertionError("TokenSimilarity.jaccard(String, String) is missing");
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("TokenSimilarity.jaccard failed: " + e, e);
        }
    }

    @Test
    void specExample() {
        assertThat(jaccard("Pandemic vaccine approved by regulators", "Regulators approved pandemic vaccine")).isEqualTo(0.8);
    }

    @Test
    void twoEmptyTokenSetsAreZero() {
        assertThat(jaccard("", "")).isEqualTo(0.0);
        assertThat(jaccard(" ,.! ", "---")).as("punctuation only = empty token set").isEqualTo(0.0);
    }

    @Test
    void caseAndPunctuationAreIgnored() {
        assertThat(jaccard("WHO: approves, vaccine!", "who approves VACCINE")).isEqualTo(1.0);
    }

    @Test
    void tokensAreASetSoRepeatsDoNotCount() {
        assertThat(jaccard("a a a b", "a b")).isEqualTo(1.0);
    }

    @ParameterizedTest(name = "jaccard(\"{0}\", \"{1}\") = {2}")
    @CsvSource({
        "a b c d e,a b c d f,0.6666666666666666",
        "a b c d e,a b c f g,0.42857142857142855",
        "a b,c d,0.0",
        "a b,'',0.0",
        "'',a b,0.0",
        "Ünïcode wörds 2026,ünïcode WÖRDS,0.6666666666666666",
    })
    void table(String a, String b, double expected) {
        assertThat(jaccard(a, b)).isEqualTo(expected, org.assertj.core.data.Offset.offset(1e-9));
    }
}
