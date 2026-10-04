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
// @trace FR-32, FR-39
class RunFailuresTest {

    private static final Map<String, String> TABLE = Map.ofEntries(
        Map.entry("NEWS_UNAVAILABLE", "ORACUL could not reach its news sources — try again later"),
        Map.entry("CHATGPT_RATE_LIMITED", "ChatGPT usage limit reached — try again later"),
        Map.entry("CHATGPT_UNAVAILABLE", "ChatGPT is temporarily unavailable — try again in a few minutes"),
        Map.entry("CHATGPT_SESSION_EXPIRED", "ChatGPT session expired — please reconnect"),
        Map.entry("CHATGPT_PLAN_NOT_ELIGIBLE",
            "Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed"),
        Map.entry("CHATGPT_REGISTRATION_INVALID",
            "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect"),
        Map.entry("CHATGPT_INCOMPLETE", "ChatGPT did not finish the answer — please try again"),
        Map.entry("CHATGPT_REQUEST_REJECTED", "ChatGPT rejected ORACUL's request — please report this"),
        Map.entry("CHATGPT_NO_MODEL", "ChatGPT offers no model for this account — check your plan, then try again"),
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
        "CHATGPT_PLAN_NOT_ELIGIBLE", "CHATGPT_REGISTRATION_INVALID", "CHATGPT_INCOMPLETE", "CHATGPT_REQUEST_REJECTED",
        "CHATGPT_NO_MODEL", "RUN_TIMEOUT", "INVALID_SCENARIO", "SCENARIO_REJECTED", "ALTERNATIVE_NOT_DISTINCT", "RUN_INTERRUPTED", "INTERNAL_ERROR"})
    void everyCodeHasItsExactMessageWithoutInternals(String code) throws Throwable {
        String message = message(code);
        assertThat(message).isEqualTo(TABLE.get(code));
        assertThat(message).doesNotContainPattern(Pattern.compile("https?:|[{}<>]|Exception|Error:|Bearer"));
    }

    private static String unexpected(String providerCode) throws Throwable {
        Class<?> failures;
        try {
            failures = Class.forName("com.oracul.app.runs.RunFailures");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunFailures is missing");
        }
        Method m;
        try {
            m = failures.getMethod("unexpected", String.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RunFailures.unexpected(String) is missing");
        }
        try {
            return (String) m.invoke(null, providerCode);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // FR-39 row 6: the CHATGPT_UNEXPECTED_ERROR text is built with the sanitized provider code
    @ParameterizedTest(name = "provider code {0}")
    @ValueSource(strings = {"weird_new_code", "http_403", "unknown_error", "a", "A.b:c-d_9"})
    void theUnexpectedErrorTextCarriesTheProviderCode(String providerCode) throws Throwable {
        assertThat(unexpected(providerCode))
            .isEqualTo("ChatGPT returned an unexpected error (" + providerCode + ") — please try again");
    }

    private static String insufficient(int realism) throws Throwable {
        Class<?> failures;
        try {
            failures = Class.forName("com.oracul.app.runs.RunFailures");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("com.oracul.app.runs.RunFailures is missing");
        }
        Method m;
        try {
            m = failures.getMethod("insufficientEvidence", int.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError("RunFailures.insufficientEvidence(int) is missing");
        }
        try {
            return (String) m.invoke(null, realism);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    // @trace FR-31
    @Test
    void insufficientEvidenceMessageAtRealismTen() throws Throwable {
        assertThat(insufficient(10)).isEqualTo("ORACUL found insufficient current evidence to construct this scenario at Realism 10.");
    }

    // @trace FR-31
    @ParameterizedTest(name = "realism {0}")
    @ValueSource(ints = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10})
    void insufficientEvidenceMessageEndsWithTheRealism(int realism) throws Throwable {
        assertThat(insufficient(realism))
            .isEqualTo("ORACUL found insufficient current evidence to construct this scenario at Realism " + realism + ".");
    }

    // @trace FR-31
    @ParameterizedTest(name = "realism {0} is rejected")
    @ValueSource(ints = {0, 11})
    void insufficientEvidenceRealismOutsideOneToTenIsRejected(int realism) {
        assertThatThrownBy(() -> insufficient(realism)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void insufficientEvidenceHasNoFixedMessage() {
        assertThatThrownBy(() -> message("INSUFFICIENT_EVIDENCE")).isInstanceOf(IllegalArgumentException.class);
    }
}
