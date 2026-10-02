package com.oracul.app.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.TestcontainersConfiguration;
import jakarta.servlet.http.Cookie;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/** FR-8: anonymous browser session cookie ORACUL_SID (isolation of credentials per session). */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BrowserSessionIT {

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcTemplate jdbc;

    private MvcResult call(Cookie cookie) throws Exception {
        var b = get("/api/auth/chatgpt/connection");
        if (cookie != null) b.cookie(cookie);
        return mvc.perform(b).andReturn();
    }

    private static String sid(MvcResult r) {
        String h = r.getResponse().getHeader("Set-Cookie");
        if (h == null) return null;
        Matcher m = Pattern.compile("ORACUL_SID=([^;]+)").matcher(h);
        return m.find() ? m.group(1) : null;
    }

    private Timestamp lastSeen(String sid) {
        return jdbc.queryForObject(
            "select last_seen_at from browser_session where cast(id as text) = ?", Timestamp.class, sid);
    }

    // @trace FR-8
    @Test
    void missingCookieCreatesSessionRowAndSessionCookie() throws Exception {
        MvcResult r = call(null);
        String header = r.getResponse().getHeader("Set-Cookie");
        assertThat(header).isNotNull();
        String sid = sid(r);
        assertThat(UUID.fromString(sid)).isNotNull();
        assertThat(header).contains("Path=/").contains("HttpOnly").contains("SameSite=Lax")
            .doesNotContain("Max-Age").doesNotContain("Expires").doesNotContain("Secure");
        assertThat(jdbc.queryForObject(
            "select count(*) from browser_session where cast(id as text) = ?", Integer.class, sid)).isEqualTo(1);
    }

    // @trace FR-8
    @Test
    void knownCookieIsKeptWithoutSetCookie() throws Exception {
        String sid = sid(call(null));
        assertThat(sid).as("first request issues the cookie").isNotNull();
        MvcResult r = call(new Cookie("ORACUL_SID", sid));
        assertThat(r.getResponse().getHeader("Set-Cookie")).isNull();
    }

    // @trace FR-8
    @Test
    void unknownOrMalformedCookieGetsANewSession() throws Exception {
        String unknown = UUID.randomUUID().toString();
        MvcResult a = call(new Cookie("ORACUL_SID", unknown));
        assertThat(sid(a)).isNotNull().isNotEqualTo(unknown);
        MvcResult b = call(new Cookie("ORACUL_SID", "not-a-uuid"));
        assertThat(sid(b)).isNotNull().isNotEqualTo("not-a-uuid");
    }

    // @trace FR-8
    @Test
    void lastSeenIsRefreshedAtMostOncePerMinute() throws Exception {
        String sid = sid(call(null));
        Timestamp old = Timestamp.from(Instant.now().minusSeconds(180));
        jdbc.update("update browser_session set last_seen_at = ? where cast(id as text) = ?", old, sid);
        call(new Cookie("ORACUL_SID", sid));
        Timestamp refreshed = lastSeen(sid);
        assertThat(refreshed).isAfter(old);
        call(new Cookie("ORACUL_SID", sid));
        assertThat(lastSeen(sid)).isEqualTo(refreshed);
    }
}
