package com.oracul.app.session;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    private static final Duration TOUCH_INTERVAL = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;
    private final Clock clock;

    SessionService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    /** Result of resolving a cookie: the session id and whether it was just created. */
    public record Resolved(UUID id, boolean created) {
    }

    public Resolved resolve(String cookieValue) {
        UUID id = parse(cookieValue);
        Instant now = clock.instant();
        if (id != null) {
            List<OffsetDateTime> seen = jdbc.query(
                "select last_seen_at from browser_session where id = ?",
                (rs, i) -> rs.getObject(1, OffsetDateTime.class), id);
            if (!seen.isEmpty()) {
                if (seen.get(0).toInstant().isBefore(now.minus(TOUCH_INTERVAL))) {
                    jdbc.update("update browser_session set last_seen_at = ? where id = ?", at(now), id);
                }
                return new Resolved(id, false);
            }
        }
        UUID fresh = UUID.randomUUID();
        jdbc.update("insert into browser_session (id, created_at, last_seen_at) values (?, ?, ?)",
            fresh, at(now), at(now));
        return new Resolved(fresh, true);
    }

    private static OffsetDateTime at(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }

    private static UUID parse(String value) {
        if (value == null) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
