package com.oracul.app.runs;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Run guard of stage 5 (NFR-2, FR-32): passes iff the run row is still RUNNING and now is before its deadline.
 * Every check re-reads the row.
 */
@Component
public class RunGuard {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    RunGuard(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** True when the work of the run may continue. */
    public boolean check(UUID runId) {
        return passes(jdbc.query("select status, deadline_at from generation_run where id = ?", this::row, runId));
    }

    /**
     * Same check inside the caller's transaction after locking the run row ({@code SELECT ... FOR UPDATE}), so no other
     * writer can end the run before the caller's inserts commit.
     */
    public boolean lockAndCheck(UUID runId) {
        return passes(jdbc.query("select status, deadline_at from generation_run where id = ? for update",
            this::row, runId));
    }

    private record Row(String status, OffsetDateTime deadline) {
    }

    private Row row(java.sql.ResultSet rs, int i) throws java.sql.SQLException {
        return new Row(rs.getString("status"), rs.getObject("deadline_at", OffsetDateTime.class));
    }

    private boolean passes(List<Row> rows) {
        if (rows.isEmpty()) {
            return false;
        }
        Row r = rows.get(0);
        return "RUNNING".equals(r.status()) && r.deadline() != null
            && clock.instant().isBefore(r.deadline().toInstant());
    }
}
