package com.oracul.app.runs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import jakarta.servlet.http.Cookie;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** generation-runs.md "Slice 16_quick-regeneration" backend test: a quick run leaves the previous run untouched. */
// FR-29 regression guard (no product change in this slice, so it is green by design and carries no trace tag,
// otherwise red-check would reject the slice as NOT-RED for the backend layer).
class QuickRegenerationIT extends AbstractRunIT {

    @Test
    void quickRunStartsANewStandardRunAndLeavesTheCompletedOneUntouched() throws Exception {
        String sid = connectedSid();
        UUID run1 = UUID.randomUUID();
        OffsetDateTime created = OffsetDateTime.now(ZoneOffset.UTC).minusHours(1);
        jdbc.update("insert into generation_run (id, generation_id, session_id, kind, status, configuration, counts, "
                + "headline, deadline_at, created_at, updated_at, completed_at) values (?, ?, cast(? as uuid), 'STANDARD', "
                + "'COMPLETED', cast(? as jsonb), cast(? as jsonb), ?, ?, ?, ?, ?)",
            run1, "ORC-QR-" + run1.toString().replace("-", "").substring(20), sid, B, ZERO_COUNTS,
            "Quick base headline", created.plusDays(1), created, created, created.plusMinutes(1));
        Map<String, Object> before = jdbc.queryForMap(
            "select status, headline, cast(configuration as text) as configuration, completed_at, updated_at "
                + "from generation_run where id = ?", run1);

        String quick = B.replace("\"darkness\":5", "\"darkness\":7");
        Map<String, Object> started = startOk(sid, quick);
        assertThat(started.get("kind")).isEqualTo("STANDARD");
        assertThat(started.get("id")).isNotEqualTo(run1.toString());
        assertThat(started.containsKey("parentRunId") && started.get("parentRunId") != null).isFalse();
        @SuppressWarnings("unchecked")
        Map<String, Object> cfg = (Map<String, Object>) started.get("configuration");
        assertThat(cfg.get("darkness")).isEqualTo(7);
        assertThat(cfg).isEqualTo(json(B.replace("\"darkness\":5", "\"darkness\":7")));
        awaitTerminal(sid, (String) started.get("id"));

        Map<String, Object> after = jdbc.queryForMap(
            "select status, headline, cast(configuration as text) as configuration, completed_at, updated_at "
                + "from generation_run where id = ?", run1);
        assertThat(after.get("status")).isEqualTo(before.get("status")).isEqualTo("COMPLETED");
        assertThat(after.get("headline")).isEqualTo("Quick base headline");
        assertThat(json((String) after.get("configuration"))).isEqualTo(json((String) before.get("configuration")));
        assertThat(after.get("completed_at")).isEqualTo(before.get("completed_at"));
        assertThat(after.get("updated_at")).isEqualTo(before.get("updated_at"));

        String body = mvc.perform(get("/api/runs").cookie(new Cookie("ORACUL_SID", sid)))
            .andReturn().getResponse().getContentAsString();
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) json(body).get("items");
        Map<String, Object> listed = items.stream().filter(i -> run1.toString().equals(i.get("id"))).findFirst().orElse(null);
        assertThat(listed).as("run 1 listed").isNotNull();
        assertThat(listed.get("headline")).isEqualTo("Quick base headline");
        assertThat(((Map<?, ?>) listed.get("configuration")).get("darkness")).isEqualTo(5);
    }
}
