package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.chatgpt.HttpResponsesClient;
import com.oracul.app.result.AbstractStoryIT;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Shared driver of the slice 02_plan-usage-calls tests (phase-02 chatgpt-inference.md): fixed message table of FR-38/39/40,
 * failure assertions with providerCode, connection state and authorize-URL helpers. HTTP only; the one in-process hook
 * is {@code HttpResponsesClient.resetStructuredOutput()} (reached by reflection so a missing method is not a compile error).
 */
public abstract class AbstractPlanUsageIT extends AbstractStoryIT {

    public static final String M_EXPIRED = "ChatGPT session expired — please reconnect";
    public static final String M_NOT_ELIGIBLE = "Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed";
    public static final String M_RATE_LIMITED = "ChatGPT usage limit reached — try again later";
    public static final String M_UNAVAILABLE = "ChatGPT is temporarily unavailable — try again in a few minutes";
    public static final String M_REJECTED = "ChatGPT rejected ORACUL's request — please report this";
    public static final String M_INCOMPLETE = "ChatGPT did not finish the answer — please try again";
    public static final String M_NO_MODEL = "ChatGPT offers no model for this account — check your plan, then try again";
    public static final String M_REGISTRATION_INVALID =
        "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect";

    public static final Pattern PROVIDER_CODE = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    public static final String ISSUED_CLIENT_ID = "oaiapp_stub_issued";

    @Autowired
    protected ApplicationContext appContext;

    /** The structured-output flag lives as long as the shared Spring context: every test leaves it as it found it. */
    @AfterEach
    void resetStructuredOutputFlag() throws Exception {
        Object client = appContext.getBean(HttpResponsesClient.class);
        try {
            Method m = HttpResponsesClient.class.getDeclaredMethod("resetStructuredOutput");
            m.setAccessible(true);
            m.invoke(client);
        } catch (NoSuchMethodException e) {
            // not implemented yet: the tests then fail on their own assertions
        }
    }

    protected String connectionState(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        return (String) json(r.getResponse().getContentAsString()).get("state");
    }

    protected boolean canGenerate(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        return Boolean.TRUE.equals(json(r.getResponse().getContentAsString()).get("canGenerate"));
    }

    /** Query parameters of the Location of GET /api/auth/chatgpt/authorize (the pending attempt is left unused). */
    protected Map<String, String> authorizeParams(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertThat(r.getResponse().getStatus()).as("authorize redirects").isEqualTo(302);
        String loc = r.getResponse().getHeader("Location");
        Map<String, String> out = new LinkedHashMap<>();
        for (String pair : loc.substring(loc.indexOf('?') + 1).split("&")) {
            int i = pair.indexOf('=');
            out.put(URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    /** failure of a FAILED run: exactly {code, message[, providerCode]}. */
    protected static void assertFailure(Map<String, Object> run, String code, String message, String providerCode) {
        assertThat(run.get("status")).as("run: " + run).isEqualTo("FAILED");
        Map<String, Object> expected = new LinkedHashMap<>();
        expected.put("code", code);
        expected.put("message", message);
        if (providerCode != null) expected.put("providerCode", providerCode);
        assertThat(run.get("failure")).as("failure of " + run).isEqualTo(expected);
        assertThat(run.get("completedAt")).isNotNull();
    }

    /** Invariants of every failure class: fixed text, no secrets, no URL / JSON / stack trace / provider body. */
    protected void assertFailureHygiene(Map<String, Object> run, String sid, String id) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> failure = (Map<String, Object>) run.get("failure");
        String message = (String) failure.get("message");
        assertThat(message).doesNotContain("Bearer").doesNotContain("http://").doesNotContain("https://")
            .doesNotContain("{").doesNotContain("}").doesNotContain("Exception").doesNotContain("\tat ")
            .doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("STUBSECRET").doesNotContain("stub\"");
        for (String token : stub.issued) assertThat(message).doesNotContain(token);
        String code = (String) failure.get("code");
        boolean carries = "CHATGPT_REQUEST_REJECTED".equals(code) || "CHATGPT_UNEXPECTED_ERROR".equals(code);
        if (carries) {
            assertThat(failure.get("providerCode")).as("providerCode of " + code).isInstanceOf(String.class);
            assertThat((String) failure.get("providerCode")).matches(PROVIDER_CODE);
        } else {
            assertThat(failure).as("only rows 5 and 6 carry a providerCode").doesNotContainKey("providerCode");
        }
        String raw = getRun(sid, id).andReturn().getResponse().getContentAsString();
        assertThat(raw).doesNotContain("PROVIDER-SECRET-BODY").doesNotContain("STUBSECRET").doesNotContain("Bearer");
    }

    /** Every POST /v1/responses body of the run parsed as JSON. */
    protected List<Map<String, Object>> postedBodies() {
        return responses.requests.stream().map(r -> json(r.body())).toList();
    }

    protected static Object at(String body, String path) {
        return JsonPath.read(body, path);
    }
}
