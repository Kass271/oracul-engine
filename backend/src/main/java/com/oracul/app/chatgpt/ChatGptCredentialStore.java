package com.oracul.app.chatgpt;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Backend-memory-only home of every OAuth secret and per-session connection flag (FR-9). */
@Component
public class ChatGptCredentialStore {

    public enum Flag { PLAN_NOT_ELIGIBLE, SESSION_EXPIRED, REGISTRATION_INVALID }

    public record PendingAuthorization(Secret state, Secret codeVerifier, Secret nonce, UUID sessionId, String clientId,
                                       boolean dynamicRegistration, Instant createdAt) {
        @Override
        public String toString() {
            return "PendingAuthorization[REDACTED]";
        }
    }

    public record SessionCredentials(UUID sessionId, String clientId, Secret accessToken, Secret refreshToken,
                                     Secret idToken, Instant accessTokenExpiresAt, Set<String> grantedScopes) {
        @Override
        public String toString() {
            return "[REDACTED]";
        }
    }

    private final Map<String, PendingAuthorization> pending = new ConcurrentHashMap<>();
    private final Map<UUID, SessionCredentials> credentials = new ConcurrentHashMap<>();
    private final Map<UUID, Flag> flags = new ConcurrentHashMap<>();

    void putPending(PendingAuthorization p) {
        pending.put(p.state().value(), p);
    }

    void purgePendingCreatedAtOrBefore(Instant cutoff) {
        pending.values().removeIf(p -> !p.createdAt().isAfter(cutoff));
    }

    PendingAuthorization pending(String state) {
        return pending.get(state);
    }

    PendingAuthorization removePending(String state) {
        return pending.remove(state);
    }

    SessionCredentials credentials(UUID sessionId) {
        return credentials.get(sessionId);
    }

    void putCredentials(SessionCredentials c) {
        credentials.put(c.sessionId(), c);
    }

    void dropCredentials(UUID sessionId) {
        credentials.remove(sessionId);
    }

    Flag flag(UUID sessionId) {
        return flags.get(sessionId);
    }

    void setFlag(UUID sessionId, Flag flag) {
        flags.put(sessionId, flag);
    }

    void clearFlag(UUID sessionId) {
        flags.remove(sessionId);
    }

    /** Drops everything of one session: credentials, flag and its pending authorizations. */
    void clearSession(UUID sessionId) {
        credentials.remove(sessionId);
        flags.remove(sessionId);
        pending.values().removeIf(p -> p.sessionId().equals(sessionId));
    }

    /** Every held credential set (reset revokes their refresh tokens). */
    java.util.List<SessionCredentials> allCredentials() {
        return java.util.List.copyOf(credentials.values());
    }

    /** Drops every credential, pending authorization and flag of the installation. */
    void clearEverything() {
        clearAll();
    }

    /** Simulates a backend restart (tests). */
    public void clearAll() {
        pending.clear();
        credentials.clear();
        flags.clear();
    }
}
