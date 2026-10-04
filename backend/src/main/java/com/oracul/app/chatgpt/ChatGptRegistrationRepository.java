package com.oracul.app.chatgpt;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** The single non-secret client registration row of this installation. */
@Repository
class ChatGptRegistrationRepository {

    record Registration(UUID hostId, String clientId) {
    }

    private final JdbcTemplate jdbc;
    private final Clock clock;

    ChatGptRegistrationRepository(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    Registration ensure() {
        OffsetDateTime now = clock.instant().atOffset(ZoneOffset.UTC);
        jdbc.update("insert into chatgpt_client_registration (host_id, created_at, updated_at) values (?, ?, ?) "
            + "on conflict (singleton) do nothing", UUID.randomUUID(), now, now);
        List<Registration> rows = jdbc.query(
            "select host_id, client_id from chatgpt_client_registration",
            (rs, i) -> new Registration(rs.getObject(1, UUID.class), rs.getString(2)));
        return rows.get(0);
    }

    /** Writes the issued client id once; never overwrites an existing one. */
    void persistClientIdIfAbsent(String clientId) {
        jdbc.update("update chatgpt_client_registration set client_id = ?, updated_at = ? where client_id is null",
            clientId, clock.instant().atOffset(ZoneOffset.UTC));
    }

    boolean anyRunActive() {
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists (select 1 from generation_run where status in ('QUEUED','RUNNING'))", Boolean.class));
    }

    /** Forgets the issued client id (host id is kept); no row means nothing to do. */
    void clearClientId() {
        jdbc.update("update chatgpt_client_registration set client_id = null, updated_at = ?",
            clock.instant().atOffset(ZoneOffset.UTC));
    }
}
