package com.oracul.app.runs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.TestcontainersConfiguration;
import com.oracul.app.chatgpt.StubOpenAi;
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

    @DynamicPropertySource
    static void stubProps(DynamicPropertyRegistry r) {
        StubOpenAi.registerAll(r);
    }

    @BeforeEach
    void resetStub() {
        stub.reset();
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
        for (String pair : loc.substring(loc.indexOf('?') + 1).split("&")) {
            if (pair.startsWith("state=")) state = URLDecoder.decode(pair.substring(6), StandardCharsets.UTF_8);
        }
        MvcResult cb = mvc.perform(get("/api/auth/chatgpt/callback?code=" + URLEncoder.encode("code-" + System.nanoTime(), StandardCharsets.UTF_8)
            + "&state=" + URLEncoder.encode(state, StandardCharsets.UTF_8))).andReturn();
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

    protected static boolean absent(Map<String, Object> m, String key) {
        return m.get(key) == null;
    }

    protected static Map<String, Object> ordered(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
