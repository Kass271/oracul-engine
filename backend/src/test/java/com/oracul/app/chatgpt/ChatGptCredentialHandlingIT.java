package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.env.Environment;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MvcResult;

/** FR-9: credentials live in backend memory only and never reach storage, logs or responses. */
@ExtendWith(OutputCaptureExtension.class)
class ChatGptCredentialHandlingIT extends AbstractChatGptIT {

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    @Autowired
    Environment env;

    private static final Pattern FORBIDDEN_COLUMN = Pattern.compile(
        "(?i)(access|refresh|id)_?token|auth(orization)?_?code|verifier|password|api_?key|secret|bearer");

    // ---- persistence ----

    // @trace FR-9
    @Test
    void persistedSchemaHasNoSecretColumnsAndKeepsTheTwoNonSecretTables() {
        List<String> columns = jdbc.queryForList(
            "select table_name || '.' || column_name from information_schema.columns where table_schema = 'public'",
            String.class);
        assertThat(columns).contains(
            "browser_session.id", "browser_session.created_at", "browser_session.last_seen_at",
            "chatgpt_client_registration.host_id", "chatgpt_client_registration.client_id");
        assertThat(columns.stream().filter(c -> FORBIDDEN_COLUMN.matcher(c.substring(c.indexOf('.') + 1)).find()))
            .isEmpty();
    }

    private List<String> everyTextValueInPublicTables() {
        List<String> values = new ArrayList<>();
        List<String> cols = jdbc.queryForList(
            "select table_name || '.' || column_name from information_schema.columns where table_schema = 'public'",
            String.class);
        for (String c : cols) {
            String t = c.substring(0, c.indexOf('.'));
            String col = c.substring(c.indexOf('.') + 1);
            values.addAll(jdbc.queryForList(
                "select cast(\"" + col + "\" as text) from \"" + t + "\" where \"" + col + "\" is not null", String.class));
        }
        return values;
    }

    // @trace FR-9
    @Test
    void noSecretReachesTablesLogsOrResponses(CapturedOutput output) throws Exception {
        List<String> responses = new ArrayList<>();
        String sid = newSid();
        MvcResult startRes = mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        String startLoc = startRes.getResponse().getHeader("Location");
        Started s = new Started(startLoc, queryOf(startLoc));
        String code = "code-SECRETCODE-" + System.nanoTime();
        MvcResult cb = callback(returnQuery(s, code)).andReturn();
        responses.add(cb.getResponse().getHeader("Location"));
        responses.add(cb.getResponse().getContentAsString());
        MvcResult conn = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        responses.add(conn.getResponse().getContentAsString());
        responses.add(conn.getResponse().getHeader("Set-Cookie"));
        responses.add(String.valueOf(startRes.getResponse().getContentAsString()));
        responses.add(disconnect(sid).andReturn().getResponse().getContentAsString());
        responses.add(startRun(sid).andReturn().getResponse().getContentAsString());

        String verifier = stub.requests.get(0).form().get("code_verifier");
        List<String> secrets = new ArrayList<>(stub.issued);
        assertThat(s.nonce()).as("the authorize request carries a nonce").isNotNull();
        secrets.addAll(List.of(code, verifier, s.state(), s.nonce()));
        assertThat(secrets).hasSizeGreaterThanOrEqualTo(5);

        for (String secret : secrets) {
            assertThat(output.getAll()).as("log output contains " + secret).doesNotContain(secret);
            for (String text : everyTextValueInPublicTables()) {
                assertThat(text).as("table value leaks " + secret).doesNotContain(secret);
            }
            for (String body : responses) {
                if (body != null) assertThat(body).as("response leaks " + secret).doesNotContain(secret);
            }
        }
        assertThat(output.getAll()).doesNotContain("STUBSECRET");
    }

    // @trace FR-9
    @Test
    void providerFailureLogsOnlyStatusAndNeverTheBody(CapturedOutput output) throws Exception {
        stub.responder = r -> StubOpenAi.status(500, "{\"error\":\"PROVIDERBODY at-STUBSECRET-leak\"}");
        Started s = start(newSid());
        String loc = callbackLocation(returnQuery(s, "c"));
        assertThat(loc).isEqualTo(NOT_COMPLETED);
        assertThat(output.getAll()).contains("chatgpt token request failed: status=500")
            .doesNotContain("PROVIDERBODY").doesNotContain("STUBSECRET");
    }

    // ---- redaction ----

    // @trace FR-9
    @Test
    void logbackRedactsBearerTokens(CapturedOutput output) {
        Logger log = LoggerFactory.getLogger("any");
        log.info("Authorization: Bearer abc123secret");
        assertThat(output.getAll()).contains("Authorization: Bearer [REDACTED]").doesNotContain("abc123secret");
    }

    // @trace FR-9
    @Test
    void logbackRedactsFormAndJsonSecrets(CapturedOutput output) {
        Logger log = LoggerFactory.getLogger("any");
        log.info("form code=xyz&state=abc&client_id=oaiapp_1");
        log.info("other error_code=5 done");
        log.info("json {\"access_token\": \"t1\"} {\"refresh_token\":\"t2\"} {\"code_verifier\":\"t3\"}");
        log.info("pair access_token=AT9 refresh_token=RT9 id_token=IT9 code_verifier=CV9");
        String all = output.getAll();
        assertThat(all).contains("code=[REDACTED]&state=[REDACTED]&client_id=oaiapp_1");
        assertThat(all).contains("error_code=5 done");
        assertThat(all).contains("{\"access_token\":\"[REDACTED]\"}");
        assertThat(all).contains("{\"refresh_token\":\"[REDACTED]\"}").contains("{\"code_verifier\":\"[REDACTED]\"}");
        assertThat(all).contains("access_token=[REDACTED] refresh_token=[REDACTED] id_token=[REDACTED] code_verifier=[REDACTED]");
        assertThat(all).doesNotContain("xyz&state").doesNotContain("t1").doesNotContain("AT9").doesNotContain("CV9");
    }

    // @trace FR-9
    @Test
    void logbackRedactsFormattedArgumentsAndExceptionText(CapturedOutput output) {
        Logger log = LoggerFactory.getLogger("any");
        log.info("calling with {}", "Bearer argsecret999");
        log.error("failed", new IllegalStateException("upstream said Bearer stacksecret777 is bad"));
        String all = output.getAll();
        assertThat(all).contains("Bearer [REDACTED]").contains("IllegalStateException");
        assertThat(all).doesNotContain("argsecret999").doesNotContain("stacksecret777");
    }

    // ---- request logging ----

    // @trace FR-9
    @Test
    void incomingBearerHeaderIsIgnoredAndLoggedRedacted(CapturedOutput output) throws Exception {
        String sid = newSid();
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/connection")
                .cookie(new Cookie("ORACUL_SID", sid)).header("Authorization", "Bearer abc123secret"))
            .andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("abc123secret").contains("NOT_CONNECTED");
        assertThat(output.getAll())
            .contains("api request method=GET path=/api/auth/chatgpt/connection")
            .contains("authorization=Bearer [REDACTED]")
            .doesNotContain("abc123secret");
    }

    // @trace FR-9
    @Test
    void requestLogLineHasDashesForMissingQueryAndAuthorization(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/scenario/catalogue"));
        assertThat(output.getAll())
            .containsPattern("api request method=GET path=/api/scenario/catalogue query=- status=\\d{3} authorization=-");
    }

    // @trace FR-9
    @Test
    void callbackQueryIsLoggedRedacted(CapturedOutput output) throws Exception {
        mvc.perform(get("/api/auth/chatgpt/callback?code=LOGCODE123&state=LOGSTATE456"));
        assertThat(output.getAll())
            .contains("api request method=GET path=/api/auth/chatgpt/callback query=code=[REDACTED]&state=[REDACTED]")
            .doesNotContain("LOGCODE123").doesNotContain("LOGSTATE456");
    }

    // ---- configuration ----

    // @trace FR-9
    @Test
    void onlyHealthIsExposedAndErrorDetailsAreHidden() {
        assertThat(env.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(env.getProperty("server.error.include-message")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-stacktrace")).isEqualTo("never");
        assertThat(env.getProperty("server.error.include-exception")).isEqualTo("false");
        assertThat(env.getProperty("server.error.include-binding-errors")).isEqualTo("never");
    }
}
