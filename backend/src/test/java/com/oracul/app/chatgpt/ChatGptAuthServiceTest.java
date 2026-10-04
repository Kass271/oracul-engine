package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracul.app.chatgpt.ChatGptCredentialStore.Flag;
import com.oracul.app.chatgpt.ChatGptCredentialStore.PendingAuthorization;
import com.oracul.app.common.ApiException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

// @trace FR-35, FR-36, FR-37, FR-40
class ChatGptAuthServiceTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-06-01T00:00:00Z"), ZoneOffset.UTC);
    private final ChatGptProperties props = ChatGptTestProps.of("http://x/t", "http://x/j", "http://x/r");
    private final ChatGptCredentialStore store = new ChatGptCredentialStore();
    private final ChatGptTokenClient tokens = mock(ChatGptTokenClient.class);
    private final ChatGptRegistrationRepository regs = mock(ChatGptRegistrationRepository.class);
    private final IdTokenValidator ids = mock(IdTokenValidator.class);

    private ChatGptAuthService service(ChatGptCredentialStore s, String base) {
        return new ChatGptAuthService(props, s, regs, tokens, ids, CLOCK, base);
    }

    @Test
    void trailingSlashesOfFrontendBaseUrlAreStripped() {
        assertThat(service(store, "http://front///").redirectTo("connected")).isEqualTo("http://front/?chatgpt=connected");
        assertThat(service(store, "http://front").redirectTo("x")).isEqualTo("http://front/?chatgpt=x");
    }

    @Test
    void startFailureYieldsNotCompletedRedirect() {
        when(regs.ensure()).thenThrow(new IllegalStateException("db"));
        assertThat(service(store, "http://front/").start(UUID.randomUUID())).isEqualTo("http://front/?chatgpt=not_completed");
    }

    @Test
    void exceptionInCompleteIsNotCompleted() {
        ChatGptCredentialStore broken = mock(ChatGptCredentialStore.class);
        when(broken.pending(any())).thenThrow(new RuntimeException("boom"));
        assertThat(service(broken, "http://f").complete("c", "s", null, null, null))
            .isEqualTo(ChatGptAuthService.NOT_COMPLETED);
    }

    @Test
    void stateAlreadyConsumedIsNotCompleted() {
        ChatGptCredentialStore racing = mock(ChatGptCredentialStore.class);
        PendingAuthorization p = new PendingAuthorization(new Secret("s"), new Secret("v"), new Secret("n"),
            UUID.randomUUID(), "cid", false, CLOCK.instant());
        when(racing.pending("s")).thenReturn(p);
        when(racing.removePending("s")).thenReturn(null);
        assertThat(service(racing, "http://f").complete("c", "s", null, null, null))
            .isEqualTo(ChatGptAuthService.NOT_COMPLETED);
    }

    @Test
    void sameStateCannotBeUsedTwice() {
        UUID sid = UUID.randomUUID();
        store.putPending(new PendingAuthorization(new Secret("s"), new Secret("v"), new Secret("n"), sid, "cid", false,
            CLOCK.instant()));
        ChatGptAuthService svc = service(store, "http://f");
        svc.complete(null, "s", "access_denied", null, null);
        assertThat(svc.complete("c", "s", null, null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
    }

    @Test
    void oversizedParametersAreNotCompleted() {
        ChatGptAuthService svc = service(store, "http://f");
        assertThat(svc.complete("c".repeat(4097), "s", null, null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", "s".repeat(513), null, null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", "s", "e".repeat(257), null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", "s", null, "d".repeat(2049), null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", "s", null, null, "i".repeat(257))).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", "", null, null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
        assertThat(svc.complete("c", null, null, null, null)).isEqualTo(ChatGptAuthService.NOT_COMPLETED);
    }

    @Test
    void everyFlagMapsToItsConnectionState() {
        ChatGptAuthService svc = service(store, "http://f");
        UUID sid = UUID.randomUUID();
        assertThat(svc.connectionState(sid)).isEqualTo(ChatGptAuthService.State.NOT_CONNECTED);
        store.setFlag(sid, Flag.REGISTRATION_INVALID);
        assertThat(svc.connectionState(sid)).isEqualTo(ChatGptAuthService.State.REGISTRATION_INVALID);
        store.setFlag(sid, Flag.SESSION_EXPIRED);
        assertThat(svc.connectionState(sid)).isEqualTo(ChatGptAuthService.State.SESSION_EXPIRED);
        store.setFlag(sid, Flag.PLAN_NOT_ELIGIBLE);
        assertThat(svc.connectionState(sid)).isEqualTo(ChatGptAuthService.State.PLAN_NOT_ELIGIBLE);
    }

    @Test
    void requireUsableCredentialsPerFlagAndWhenNotConnected() {
        ChatGptAuthService svc = service(store, "http://f");
        UUID sid = UUID.randomUUID();
        assertThatThrownBy(() -> svc.requireUsableCredentials(sid))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CHATGPT_NOT_CONNECTED"));
        store.setFlag(sid, Flag.PLAN_NOT_ELIGIBLE);
        assertThatThrownBy(() -> svc.requireUsableCredentials(sid))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CHATGPT_PLAN_NOT_ELIGIBLE"));
        store.setFlag(sid, Flag.SESSION_EXPIRED);
        assertThatThrownBy(() -> svc.requireUsableCredentials(sid))
            .isInstanceOfSatisfying(ApiException.class, e -> assertThat(e.code()).isEqualTo("CHATGPT_SESSION_EXPIRED"));
        // FR-40: flag REGISTRATION_INVALID -> 401 CHATGPT_REGISTRATION_INVALID
        store.setFlag(sid, Flag.REGISTRATION_INVALID);
        assertThatThrownBy(() -> svc.requireUsableCredentials(sid))
            .isInstanceOfSatisfying(ApiException.class, e -> {
                assertThat(e.code()).isEqualTo("CHATGPT_REGISTRATION_INVALID");
                assertThat(e.status().value()).isEqualTo(401);
                assertThat(e.getMessage()).isEqualTo(
                    "ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect");
            });
    }
}
