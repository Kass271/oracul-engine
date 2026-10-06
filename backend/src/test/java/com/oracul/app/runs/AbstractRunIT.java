package com.oracul.app.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.TestcontainersConfiguration;
import com.oracul.app.chatgpt.StubOpenAi;
import com.oracul.app.research.StubNews;
import com.oracul.app.research.StubResponses;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Shared driver for run tests: connected sessions through the stub token server, startRun/getRun over MockMvc. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
// slice 12: threshold 0 disables the sufficiency check so the fixtures of slices 04-11 keep their asserted behaviour
@org.springframework.test.context.TestPropertySource(properties = {
    "oracul.evidence.min-core.high=0",
    "oracul.evidence.min-core.medium=0",
    "oracul.evidence.min-core.low=0",
})
public abstract class AbstractRunIT {

    /** Base valid body B. */
    public static final String B = """
        {"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],"customWildcards":[],
         "output":{"story":true,"illustration":false}}""";
    /** Acceptance body A. */
    public static final String A = """
        {"realism":8,"darkness":9,"optimism":2,"horizon":"5y",
         "wildcards":[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"robotics-humanoid-boom","intensity":6}],
         "customWildcards":[],"output":{"story":true,"illustration":false}}""";
    public static final String ZERO_COUNTS =
        "{\"searches\":0,\"articlesRetrieved\":0,\"articlesConsidered\":0,\"uniqueEvents\":0,\"eventsSelected\":0,\"counterSignals\":0,\"sourcesUsed\":0}";
    public static final String NO_RUN = "00000000-0000-0000-0000-000000000000";

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected JdbcTemplate jdbc;

    protected final StubOpenAi stub = StubOpenAi.INSTANCE;
    protected final StubResponses responses = StubResponses.INSTANCE;
    protected final StubNews news = StubNews.INSTANCE;

    @DynamicPropertySource
    static void stubProps(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    @BeforeEach
    void resetStub() {
        stub.reset();
    }

    /** Isolation: no in-flight run of this test may reach the shared stub after the next test resets it. */
    @org.junit.jupiter.api.AfterEach
    void awaitNoActiveRuns() throws Exception {
        if (!awaitRunsAfterEach()) {
            // a deliberately pinned run must not outlive its test: terminal state makes the run guard stop its work
            jdbc.update("update generation_run set status = 'FAILED' where status in ('QUEUED','RUNNING')");
            return;
        }
        long end = System.currentTimeMillis() + 5_000;
        while (true) {
            java.util.List<String> active = jdbc.queryForList(
                "select cast(id as varchar) from generation_run where status in ('QUEUED','RUNNING')", String.class);
            if (active.isEmpty()) return;
            if (System.currentTimeMillis() >= end) {
                // a failed test must not leak its run into the next test: a terminal state makes the run guard stop its work
                jdbc.update("update generation_run set status = 'FAILED' where status in ('QUEUED','RUNNING')");
                throw new AssertionError("runs still active 5 s after the test (leak into next test): " + active);
            }
            Thread.sleep(25);
        }
    }

    /** Override with false in ITs that deliberately keep runs pending/running (long stage delays). */
    protected boolean awaitRunsAfterEach() {
        return true;
    }

    protected static Map<String, Object> json(String raw) {
        return JsonPath.read(raw, "$");
    }

    protected static Map<String, Object> json(ResultActions r) throws Exception {
        return json(r.andReturn().getResponse().getContentAsString());
    }

    protected static String sidOf(MvcResult r) {
        String h = r.getResponse().getHeader("Set-Cookie");
        if (h == null) return null;
        Matcher m = Pattern.compile("ORACUL_SID=([^;]+)").matcher(h);
        return m.find() ? m.group(1) : null;
    }

    /** Fresh browser session (first API call without a cookie). */
    protected String newSid() throws Exception {
        String sid = sidOf(mvc.perform(get("/api/auth/chatgpt/connection")).andReturn());
        if (sid == null) throw new AssertionError("no ORACUL_SID cookie issued on first request");
        return sid;
    }

    protected String connectedSid() throws Exception {
        String sid = newSid();
        connect(sid);
        return sid;
    }

    protected void connect(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertEquals(302, r.getResponse().getStatus(), "authorize must redirect");
        String loc = r.getResponse().getHeader("Location");
        String state = null;
        String nonce = null;
        String clientId = null;
        for (String pair : loc.substring(loc.indexOf('?') + 1).split("&")) {
            if (pair.startsWith("state=")) state = URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8);
            if (pair.startsWith("nonce=")) nonce = URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8);
            if (pair.startsWith("client_id=")) clientId = URLDecoder.decode(pair.substring(10), StandardCharsets.UTF_8);
        }
        String code = "code-" + System.nanoTime();
        stub.expectNonce(code, nonce);
        // a first registration must bring the issued client id back (phase-02 FR-36); a reauthorization may omit it
        String issued = "dynamic_agent_client".equals(clientId) ? "&client_id=oaiapp_stub_issued" : "";
        MvcResult cb = mvc.perform(get("/api/auth/chatgpt/callback?code=" + URLEncoder.encode(code, StandardCharsets.UTF_8)
            + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8) + issued)).andReturn();
        assertEquals(302, cb.getResponse().getStatus());
    }

    protected ResultActions startRun(String sid, String body) throws Exception {
        var b = post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(body);
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    protected ResultActions getRun(String sid, String runId) throws Exception {
        var b = get("/api/runs/" + runId);
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    protected ResultActions getResearch(String sid, String runId) throws Exception {
        var b = get("/api/runs/" + runId + "/research");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    protected ResultActions getSources(String sid, String runId) {
        try {
            var b = get("/api/runs/" + runId + "/sources");
            if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
            return mvc.perform(b);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    /** Body of GET /research (200 expected). */
    protected Map<String, Object> researchBody(String sid, String runId) throws Exception {
        MvcResult r = getResearch(sid, runId).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "getRunResearch: " + r.getResponse().getContentAsString());
        return json(r.getResponse().getContentAsString());
    }

    /** Items of GET /sources (200 expected). */
    @SuppressWarnings("unchecked")
    protected java.util.List<Map<String, Object>> sourceItems(String sid, String runId) throws Exception {
        MvcResult r = getSources(sid, runId).andReturn();
        assertEquals(200, r.getResponse().getStatus(), "listRunSources: " + r.getResponse().getContentAsString());
        return (java.util.List<Map<String, Object>>) json(r.getResponse().getContentAsString()).get("items");
    }

    /** Terminal state within the 10 s of the research-pipeline.md slice 05 test contract. */
    protected Map<String, Object> awaitDone(String sid, String runId) throws Exception {
        return awaitRun(sid, runId, 10_000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
    }

    /** Polls getResearch until searchPlan is present. */
    protected Map<String, Object> awaitPlan(String sid, String runId, long timeoutMs) throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        Map<String, Object> last = Map.of();
        while (System.currentTimeMillis() < end) {
            MvcResult r = getResearch(sid, runId).andReturn();
            if (r.getResponse().getStatus() == 200) {
                last = json(r.getResponse().getContentAsString());
                if (last.get("searchPlan") != null) return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("searchPlan did not appear within " + timeoutMs + " ms; last=" + last);
    }

    protected static String withHorizon(String body, String horizon) {
        return body.replaceFirst("\"horizon\":\"[^\"]+\"", "\"horizon\":\"" + horizon + "\"");
    }

    protected ResultActions disconnect(String sid) throws Exception {
        return mvc.perform(delete("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)));
    }

    /** Starts a run and returns its 202 body; fails with the real status when it is not 202. */
    protected Map<String, Object> startOk(String sid, String body) throws Exception {
        MvcResult r = startRun(sid, body).andReturn();
        assertEquals(202, r.getResponse().getStatus(), "startRun: " + r.getResponse().getContentAsString());
        return json(r.getResponse().getContentAsString());
    }

    /** Polls getRun every 50 ms until the predicate holds; fails after timeoutMs with the last observed body. */
    protected Map<String, Object> awaitRun(String sid, String runId, long timeoutMs, Predicate<Map<String, Object>> done)
        throws Exception {
        long end = System.currentTimeMillis() + timeoutMs;
        Map<String, Object> last = Map.of();
        while (System.currentTimeMillis() < end) {
            MvcResult r = getRun(sid, runId).andReturn();
            if (r.getResponse().getStatus() == 200) {
                last = json(r.getResponse().getContentAsString());
                if (done.test(last)) return last;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("run " + runId + " did not reach the expected state within " + timeoutMs + " ms; last=" + last);
    }

    protected Map<String, Object> awaitTerminal(String sid, String runId) throws Exception {
        return awaitRun(sid, runId, 5000, m -> !"QUEUED".equals(m.get("status")) && !"RUNNING".equals(m.get("status")));
    }

    protected int runCount() {
        return jdbc.queryForObject("select count(*) from generation_run", Integer.class);
    }

    /** kind of the run's evidenceNote (run-control.md FR-47); fails with the whole run body when there is no note. */
    @SuppressWarnings("unchecked")
    public static Object noteKind(Map<String, Object> run) {
        Object note = run.get("evidenceNote");
        org.assertj.core.api.Assertions.assertThat(note).as("evidenceNote of " + run).isNotNull();
        return ((Map<String, Object>) note).get("kind");
    }

    protected static boolean absent(Map<String, Object> m, String key) {
        return m.get(key) == null;
    }

    protected static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
