package com.oracul.app.chatgpt;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.oracul.app.TestcontainersConfiguration;
import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

/** Shared driver for the ChatGPT sign-in flow through MockMvc against the contract paths. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
abstract class AbstractChatGptIT {

    static final String FRONTEND = "http://localhost:4200";
    static final String REDIRECT_URI = "http://127.0.0.1:4200/auth/callback";
    static final String CONNECTED = FRONTEND + "/?chatgpt=connected";
    static final String NOT_COMPLETED = FRONTEND + "/?chatgpt=not_completed";
    static final String NOT_ELIGIBLE = FRONTEND + "/?chatgpt=not_eligible";
    static final String VALID_RUN = """
        {"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],"customWildcards":[],
         "output":{"story":true,"illustration":false}}""";

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;
    @Autowired
    ApplicationContext ctx;

    final StubOpenAi stub = StubOpenAi.INSTANCE;

    @BeforeEach
    void resetStub() {
        stub.reset();
    }

    /** Authorize-URL parameters (decoded) plus the raw Location. */
    record Started(String location, Map<String, String> params) {
        String state() { return params.get("state"); }
        String challenge() { return params.get("code_challenge"); }
    }

    static Map<String, String> queryOf(String url) {
        Map<String, String> out = new LinkedHashMap<>();
        int q = url.indexOf('?');
        if (q < 0) return out;
        for (String pair : url.substring(q + 1).split("&")) {
            int i = pair.indexOf('=');
            out.put(
                URLDecoder.decode(pair.substring(0, i), StandardCharsets.UTF_8),
                URLDecoder.decode(pair.substring(i + 1), StandardCharsets.UTF_8));
        }
        return out;
    }

    static String sidOf(MvcResult r) {
        String h = r.getResponse().getHeader("Set-Cookie");
        if (h == null) return null;
        Matcher m = Pattern.compile("ORACUL_SID=([^;]+)").matcher(h);
        return m.find() ? m.group(1) : null;
    }

    /** Fresh browser session: first API call without a cookie. */
    String newSid() throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/connection")).andReturn();
        String sid = sidOf(r);
        if (sid == null) throw new AssertionError("no ORACUL_SID cookie issued on first request");
        return sid;
    }

    Started start(String sid) throws Exception {
        MvcResult r = mvc.perform(get("/api/auth/chatgpt/authorize").cookie(new Cookie("ORACUL_SID", sid))).andReturn();
        assertEquals(302, r.getResponse().getStatus(), "authorize must redirect");
        String loc = r.getResponse().getHeader("Location");
        return new Started(loc, queryOf(loc));
    }

    ResultActions callback(String query) throws Exception {
        return mvc.perform(get("/api/auth/chatgpt/callback" + (query.isEmpty() ? "" : "?" + query)));
    }

    String callbackLocation(String query) throws Exception {
        MvcResult r = callback(query).andReturn();
        assertEquals(302, r.getResponse().getStatus(), "callback must always redirect");
        return r.getResponse().getHeader("Location");
    }

    static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Full successful sign-in for a session; returns the started flow. */
    Started connect(String sid) throws Exception {
        Started s = start(sid);
        String loc = callbackLocation("code=" + enc("code-" + System.nanoTime()) + "&state=" + enc(s.state()));
        assertEquals(CONNECTED, loc);
        return s;
    }

    Map<String, Object> connection(String sid) throws Exception {
        String body = mvc.perform(get("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        try {
            return JsonPath.read(body, "$");
        } catch (RuntimeException e) {
            throw new AssertionError("connection body is not a JSON object: " + body);
        }
    }

    String stateOf(String sid) throws Exception {
        return String.valueOf(connection(sid).get("state"));
    }

    ResultActions disconnect(String sid) throws Exception {
        return mvc.perform(delete("/api/auth/chatgpt/connection").cookie(new Cookie("ORACUL_SID", sid)));
    }

    ResultActions startRun(String sid) throws Exception {
        var b = post("/api/runs").contentType(MediaType.APPLICATION_JSON).content(VALID_RUN);
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b);
    }

    static String s256(String verifier) throws Exception {
        byte[] d = MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII));
        return Base64.getUrlEncoder().withoutPadding().encodeToString(d);
    }

    void resetRegistration() {
        jdbc.update("delete from chatgpt_client_registration");
    }

    /** Simulates a backend restart: empties ChatGptCredentialStore. */
    void clearStore() throws Exception {
        Class<?> type;
        try {
            type = Class.forName("com.oracul.app.chatgpt.ChatGptCredentialStore");
        } catch (ClassNotFoundException e) {
            throw new AssertionError("ChatGptCredentialStore is missing");
        }
        Object bean = ctx.getBean(type);
        type.getMethod("clearAll").invoke(bean);
    }
}
