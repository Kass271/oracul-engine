package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.chatgpt.ChatGptCredentialStore.PendingAuthorization;
import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// @trace FR-36, FR-37
class ChatGptCredentialStoreTest {

    @Test
    void recordsPrintRedacted() {
        UUID sid = UUID.randomUUID();
        PendingAuthorization p = new PendingAuthorization(new Secret("st"), new Secret("ver"), new Secret("no"), sid,
            "cid", false, Instant.now());
        SessionCredentials c = new SessionCredentials(sid, "cid", new Secret("acc"), new Secret("ref"),
            new Secret("idt"), Instant.now(), Set.of("a"));
        assertThat(p.toString()).isEqualTo("PendingAuthorization[REDACTED]");
        assertThat(c.toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void clearSessionDropsOnlyThatSessionsPendingAndKeepsOthers() {
        ChatGptCredentialStore store = new ChatGptCredentialStore();
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        store.putPending(new PendingAuthorization(new Secret("sa"), new Secret("v"), new Secret("n"), a, "c", false,
            Instant.now()));
        store.putPending(new PendingAuthorization(new Secret("sb"), new Secret("v"), new Secret("n"), b, "c", false,
            Instant.now()));
        store.clearSession(a);
        assertThat(store.pending("sa")).isNull();
        assertThat(store.pending("sb")).isNotNull();
    }

    @Test
    void purgeRemovesOldAndKeepsNewer() {
        ChatGptCredentialStore store = new ChatGptCredentialStore();
        Instant t = Instant.parse("2026-01-01T00:00:00Z");
        UUID s = UUID.randomUUID();
        store.putPending(new PendingAuthorization(new Secret("old"), new Secret("v"), new Secret("n"), s, "c", false, t));
        store.putPending(new PendingAuthorization(new Secret("new"), new Secret("v"), new Secret("n"), s, "c", false,
            t.plusSeconds(10)));
        store.purgePendingCreatedAtOrBefore(t);
        assertThat(store.pending("old")).isNull();
        assertThat(store.pending("new")).isNotNull();
    }
}
