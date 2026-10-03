package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * generation-runs.md "Slice 11_run-failures" RunFailuresTest: the central failure-message table. {@code RunFailures} and
 * {@code message(RunFailureCode)} are reached by reflection (the code enum is the generated API model) so RED compiles.
 */
// @trace FR-32
class RunFailuresTest {

    private static final Map<String, String> TABLE = Map.ofEntries(
        Map.entry("NEWS_UNAVAILABLE", "ORACUL could not reach its news sources — try again later"),
        Map.entry("CHATGPT_RATE_LIMITED", "ChatGPT plan limit reached — try again later"),
        Map.entry("CHATGPT_UNAVAILABLE", "ChatGPT is unavailable right now — try again later"),
        Map.entry("CHATGPT_SESSION_EXPIRED", "ChatGPT session expired — please reconnect"),
        Map.entry("RUN_TIMEOUT", "Generation took too long — try again"),
        Map.entry("INVALID_SCENARIO", "ORACUL could not construct a valid scenario"),
        Map.entry("SCENARIO_REJECTED", "ORACUL could not construct a scenario supported by current evidence"),
        Map.entry("ALTERNATIVE_NOT_DISTINCT", "ORACUL could not find a different future — try changing a setting"),
        Map.entry("RUN_INTERRUPTED", "Generation was interrupted — try again"),
        Map.entry("INTERNAL_ERROR", "Something went wrong — try again"));

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static String message(String code) throws Throwable {
        Class<?> failures;
        try {
            failures = Class.forName("com.oracul.app.runs.RunFailures");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunFailures is missing");
        }
        Class<?> codeType = Class.forName("com.oracul.app.api.model.RunFailureCode");
        Object value = Enum.valueOf((Class<Enum>) codeType, code);
        Method m;
        try {
            m = failures.getMethod("message", codeType);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RunFailures.message(RunFailureCode) is missing");
        }
        try {
            return (String) m.invoke(null, value);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"NEWS_UNAVAILABLE", "CHATGPT_RATE_LIMITED", "CHATGPT_UNAVAILABLE", "CHATGPT_SESSION_EXPIRED",
        "RUN_TIMEOUT", "INVALID_SCENARIO", "SCENARIO_REJECTED", "ALTERNATIVE_NOT_DISTINCT", "RUN_INTERRUPTED", "INTERNAL_ERROR"})
    void everyCodeHasItsExactMessageWithoutInternals(String code) throws Throwable {
        String message = message(code);
        assertThat(message).isEqualTo(TABLE.get(code));
        assertThat(message).doesNotContainPattern(Pattern.compile("https?:|[{}<>]|Exception|Error:|Bearer"));
    }

    @Test
    void insufficientEvidenceHasNoFixedMessage() {
        assertThatThrownBy(() -> message("INSUFFICIENT_EVIDENCE")).isInstanceOf(IllegalArgumentException.class);
    }
}
