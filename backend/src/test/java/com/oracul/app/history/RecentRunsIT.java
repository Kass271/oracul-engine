package com.oracul.app.history;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.oracul.app.runs.AbstractRunIT;
import com.oracul.app.runs.GenerationRunRepository;
import jakarta.servlet.http.Cookie;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

/** recent-futures.md "Slice 15_recent-futures" RecentRunsIT rows 1-8. */
// @trace FR-33
class RecentRunsIT extends AbstractRunIT {

    private static final OffsetDateTime BASE = OffsetDateTime.of(2026, 1, 1, 8, 0, 0, 0, ZoneOffset.UTC);

    @MockitoSpyBean
    GenerationRunRepository repo;

    @Override
    protected boolean awaitRunsAfterEach() {
        return false;
    }

    private static String cfg(int darkness) {
        return B.replace("\"darkness\":5", "\"darkness\":" + darkness);
    }

    private UUID seed(String sid, UUID id, String status, String headline, OffsetDateTime createdAt, int darkness) {
        OffsetDateTime completed = "COMPLETED".equals(status) ? createdAt.plusMinutes(1) : null;
        // active rows must have a far-future real deadline so no sweeper touches them during the test
        OffsetDateTime deadline = OffsetDateTime.now(ZoneOffset.UTC).plusDays(1);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, "
                + "headline, deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), 'STANDARD', ?, "
                + "cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?)",
            id, "ORC-RR-" + id.toString().replace("-", "").substring(20), sid, status, cfg(darkness), ZERO_COUNTS,
            headline, deadline, createdAt, createdAt, completed);
        return id;
    }

    private UUID seed(String sid, String status, String headline, OffsetDateTime createdAt, int darkness) {
        return seed(sid, UUID.randomUUID(), status, headline, createdAt, darkness);
    }

    private MvcResult list(String sid) throws Exception {
        var b = get("/api/runs");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        return mvc.perform(b).andReturn();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(String sid) throws Exception {
        MvcResult r = list(sid);
        assertThat(r.getResponse().getStatus()).as(r.getResponse().getContentAsString()).isEqualTo(200);
        return (List<Map<String, Object>>) json(r.getResponse().getContentAsString()).get("items");
    }

    private static List<String> ids(List<Map<String, Object>> items) {
        return items.stream().map(i -> (String) i.get("id")).toList();
    }

    // #1
    @Test
    void noCookieGivesAnEmptyListAndANewSession() throws Exception {
        MvcResult r = list(null);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        assertThat(json(r.getResponse().getContentAsString())).isEqualTo(json("{\"items\":[]}"));
        assertThat(r.getResponse().getHeader("Set-Cookie")).contains("ORACUL_SID=");
    }

    // #2
    @Test
    void twoCompletedRunsNewestFirstWithEveryField() throws Exception {
        String sid = newSid();
        UUID older = seed(sid, "COMPLETED", "Older headline", BASE.plusHours(2), 3);
        UUID newer = seed(sid, "COMPLETED", "Newer <b>headline</b>", BASE.plusHours(3), 9);
        List<Map<String, Object>> items = items(sid);
        assertThat(ids(items)).containsExactly(newer.toString(), older.toString());
        Map<String, Object> first = items.get(0);
        assertThat(first.get("headline")).isEqualTo("Newer <b>headline</b>");
        assertThat(first.get("kind")).isEqualTo("STANDARD");
        assertThat(first.get("generationId")).isEqualTo(
            jdbc.queryForObject("select generation_id from generation_run where id = ?", String.class, newer));
        assertThat(OffsetDateTime.parse((String) first.get("createdAt")).toInstant()).isEqualTo(BASE.plusHours(3).toInstant());
        assertThat(OffsetDateTime.parse((String) first.get("completedAt")).toInstant())
            .isEqualTo(BASE.plusHours(3).plusMinutes(1).toInstant());
        for (Map<String, Object> item : items) {
            MvcResult run = getRun(sid, (String) item.get("id")).andReturn();
            assertThat(run.getResponse().getStatus()).isEqualTo(200);
            assertThat(item.get("configuration")).isEqualTo(json(run.getResponse().getContentAsString()).get("configuration"));
        }
        assertThat(((Map<?, ?>) items.get(0).get("configuration")).get("darkness")).isEqualTo(9);
        assertThat(((Map<?, ?>) items.get(1).get("configuration")).get("darkness")).isEqualTo(3);
    }

    // #3
    @Test
    void onlyCompletedRunsWithAHeadlineAreListed() throws Exception {
        String sid = newSid();
        UUID ok = seed(sid, "COMPLETED", "Listed", BASE.plusMinutes(1), 5);
        seed(sid, "FAILED", "failed headline", BASE.plusMinutes(2), 5);
        seed(sid, "INSUFFICIENT_EVIDENCE", "insufficient headline", BASE.plusMinutes(3), 5);
        seed(sid, "COMPLETED", null, BASE.plusMinutes(4), 5);
        UUID running = seed(sid, "RUNNING", "running headline", BASE.plusMinutes(5), 5);
        try {
            assertThat(ids(items(sid))).containsExactly(ok.toString());
        } finally {
            jdbc.update("update generation_run set status = 'FAILED' where id = ?", running);
        }
    }

    // #4
    @ParameterizedTest(name = "n={0}")
    @ValueSource(ints = {20, 21, 25})
    void atMostTheTwentyNewestAreListed(int n) throws Exception {
        String sid = newSid();
        List<UUID> created = new ArrayList<>();
        for (int i = 0; i < n; i++) created.add(seed(sid, "COMPLETED", "Headline " + i, BASE.plusMinutes(i), 5));
        List<String> expected = new ArrayList<>();
        for (int i = n - 1; i >= n - 20; i--) expected.add(created.get(i).toString());
        List<Map<String, Object>> items = items(sid);
        assertThat(items).hasSize(20);
        assertThat(ids(items)).containsExactlyElementsOf(expected);
        assertThat(ids(items)).doesNotHaveDuplicates();
        for (int i = 0; i < n - 20; i++) assertThat(ids(items)).doesNotContain(created.get(i).toString());
        assertThat(jdbc.queryForObject("select count(*) from generation_run where cast(session_id as text) = ?",
            Integer.class, sid)).isEqualTo(n);
    }

    // #5
    @Test
    void equalCreatedAtIsOrderedByIdDescending() throws Exception {
        jdbc.update("delete from generation_run where id in (cast('00000000-0000-0000-0000-000000000001' as uuid), cast('00000000-0000-0000-0000-000000000002' as uuid))");
        String sid = newSid();
        UUID one = seed(sid, UUID.fromString("00000000-0000-0000-0000-000000000001"), "COMPLETED", "one", BASE, 5);
        UUID two = seed(sid, UUID.fromString("00000000-0000-0000-0000-000000000002"), "COMPLETED", "two", BASE, 5);
        try {
            assertThat(ids(items(sid))).containsExactly(two.toString(), one.toString());
        } finally {
            jdbc.update("delete from generation_run where id in (?, ?)", one, two);
        }
    }

    // #6
    @Test
    void otherSessionsSeeNothingOfMine() throws Exception {
        String a = newSid();
        String b = newSid();
        UUID run = seed(a, "COMPLETED", "A1", BASE.plusMinutes(1), 5);
        seed(a, "COMPLETED", "A2", BASE.plusMinutes(2), 5);
        seed(a, "COMPLETED", "A3", BASE.plusMinutes(3), 5);
        assertThat(items(a)).hasSize(3);
        assertThat(items(b)).isEmpty();
        MvcResult r = getRun(b, run.toString()).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(404);
        assertThat(json(r.getResponse().getContentAsString()))
            .isEqualTo(json("{\"code\":\"RUN_NOT_FOUND\",\"message\":\"Future not found\"}"));
    }

    // #7
    @Test
    void scenarioConfigurationIsTheNewestRunOfAnyStatus() throws Exception {
        String sid = newSid();
        seed(sid, "COMPLETED", "old", BASE.plusHours(1), 3);
        seed(sid, "FAILED", null, BASE.plusHours(2), 9);
        assertThat(configurationOf(sid).get("darkness")).isEqualTo(9);
        String fresh = newSid();
        Map<String, Object> defaults = configurationOf(null);
        assertThat(configurationOf(fresh)).isEqualTo(defaults);
        assertThat(defaults.get("darkness")).isNotEqualTo(9);
    }

    private Map<String, Object> configurationOf(String sid) throws Exception {
        var b = get("/api/scenario/configuration");
        if (sid != null) b.cookie(new Cookie("ORACUL_SID", sid));
        MvcResult r = mvc.perform(b).andReturn();
        assertThat(r.getResponse().getStatus()).isEqualTo(200);
        return json(r.getResponse().getContentAsString());
    }

    // #8
    @Test
    void anUnexpectedRepositoryFailureIsAnInternalError() throws Exception {
        Method m = Stream.of(GenerationRunRepository.class.getDeclaredMethods())
            .filter(x -> x.getName().equals("findRecentCompleted")).findFirst()
            .orElseThrow(() -> new AssertionError("GenerationRunRepository.findRecentCompleted is missing"));
        m.setAccessible(true);
        String sid = newSid();
        Object stub = doThrow(new RuntimeException("boom http://internal.example at com.oracul.app.X")).when(repo);
        m.invoke(stub, matcherArgs(m));
        MvcResult r = list(sid);
        assertThat(r.getResponse().getStatus()).isEqualTo(500);
        String body = r.getResponse().getContentAsString();
        assertThat(json(body)).isEqualTo(json("{\"code\":\"INTERNAL_ERROR\",\"message\":\"Something went wrong — try again\"}"));
        assertThat(body).doesNotContain("boom", "internal.example", "com.oracul");
    }

    private static Object[] matcherArgs(Method m) {
        Object[] args = new Object[m.getParameterCount()];
        for (int i = 0; i < args.length; i++) {
            Class<?> t = m.getParameterTypes()[i];
            if (t == int.class) args[i] = ArgumentMatchers.anyInt();
            else if (t == long.class) args[i] = ArgumentMatchers.anyLong();
            else args[i] = ArgumentMatchers.any();
        }
        return args;
    }
}
