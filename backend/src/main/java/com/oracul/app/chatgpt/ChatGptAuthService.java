package com.oracul.app.chatgpt;

import com.oracul.app.chatgpt.ChatGptCredentialStore.Flag;
import com.oracul.app.chatgpt.ChatGptCredentialStore.PendingAuthorization;
import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import com.oracul.app.common.ApiException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ChatGptAuthService {

    private static final Logger log = LoggerFactory.getLogger(ChatGptAuthService.class);
    private static final Pattern ISSUED_CLIENT_ID = Pattern.compile("^oaiapp_[A-Za-z0-9_-]{1,249}$");
    private static final SecureRandom RANDOM = new SecureRandom();

    public static final String CONNECTED = "connected";
    public static final String NOT_COMPLETED = "not_completed";
    public static final String NOT_ELIGIBLE = "not_eligible";

    public enum State { NOT_CONNECTED, CONNECTED, PLAN_NOT_ELIGIBLE, SESSION_EXPIRED }

    private final ChatGptProperties props;
    private final ChatGptCredentialStore store;
    private final ChatGptRegistrationRepository registrations;
    private final ChatGptTokenClient tokens;
    private final Clock clock;
    private final String frontendBaseUrl;
    private final Object[] locks = newLocks();

    ChatGptAuthService(ChatGptProperties props, ChatGptCredentialStore store,
                       ChatGptRegistrationRepository registrations, ChatGptTokenClient tokens, Clock clock,
                       @Value("${oracul.frontend-base-url:http://localhost:4200}") String frontendBaseUrl) {
        this.props = props;
        this.store = store;
        this.registrations = registrations;
        this.tokens = tokens;
        this.clock = clock;
        String base = frontendBaseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.frontendBaseUrl = base;
    }

    public String redirectTo(String outcome) {
        return frontendBaseUrl + "/?chatgpt=" + outcome;
    }

    // ---- start ----

    /** Returns the OpenAI authorize URL, or the frontend not_completed URL when it cannot be built. */
    public String start(UUID sessionId) {
        try {
            ChatGptRegistrationRepository.Registration reg = registrations.ensure();
            boolean dynamic = reg.clientId() == null;
            String clientId = dynamic ? props.dynamicClientId() : reg.clientId();
            String state = randomUrlSafe(32);
            String verifier = randomUrlSafe(64);
            String challenge = Base64.getUrlEncoder().withoutPadding().encodeToString(
                MessageDigest.getInstance("SHA-256").digest(verifier.getBytes(StandardCharsets.US_ASCII)));

            Map<String, String> q = new LinkedHashMap<>();
            q.put("response_type", "code");
            q.put("client_id", clientId);
            if (dynamic) {
                q.put("ext_agent_host_id", reg.hostId().toString());
                q.put("agent_name_hint", props.agentNameHint());
            }
            q.put("redirect_uri", props.redirectUri());
            q.put("scope", String.join(" ", props.scopeList()));
            q.put("state", state);
            q.put("code_challenge", challenge);
            q.put("code_challenge_method", "S256");
            q.put("resource", props.resource());
            String query = q.entrySet().stream()
                .map(e -> encode(e.getKey()) + "=" + encode(e.getValue()))
                .collect(Collectors.joining("&"));
            String base = props.authorizeUrl();
            String url = base + (base.contains("?") ? "&" : "?") + query;
            URI.create(url); // fails for an unbuildable authorize URL

            store.purgePendingCreatedAtOrBefore(clock.instant().minus(props.pendingTtl()));
            store.putPending(new PendingAuthorization(new Secret(state), new Secret(verifier), sessionId, clientId,
                dynamic, clock.instant()));
            return url;
        } catch (Exception e) {
            log.warn("chatgpt authorize url could not be built: {}", e.getClass().getSimpleName());
            return redirectTo(NOT_COMPLETED);
        }
    }

    // ---- callback ----

    public String complete(String code, String state, String error, String errorDescription, String clientId) {
        try {
            return doComplete(code, state, error, errorDescription, clientId);
        } catch (Exception e) {
            log.warn("chatgpt callback failed: {}", e.getClass().getSimpleName());
            return NOT_COMPLETED;
        }
    }

    private String doComplete(String code, String state, String error, String errorDescription, String clientId) {
        if (tooLong(code, 4096) || tooLong(state, 512) || tooLong(error, 256)
            || tooLong(errorDescription, 2048) || tooLong(clientId, 256)) {
            return NOT_COMPLETED;
        }
        if (state == null || state.isEmpty()) {
            return NOT_COMPLETED;
        }
        PendingAuthorization entry = store.pending(state);
        if (entry == null) {
            return NOT_COMPLETED;
        }
        if (!clock.instant().isBefore(entry.createdAt().plus(props.pendingTtl()))) {
            store.removePending(state);
            return NOT_COMPLETED;
        }
        PendingAuthorization pending = store.removePending(state);
        if (pending == null) {
            return NOT_COMPLETED;
        }
        if (error != null || code == null || code.isEmpty()) {
            return NOT_COMPLETED;
        }
        String exchangeClientId = pending.dynamicRegistration() && clientId != null
            && ISSUED_CLIENT_ID.matcher(clientId).matches() ? clientId : pending.clientId();

        Map<String, String> form = new LinkedHashMap<>();
        form.put("grant_type", "authorization_code");
        form.put("code", code);
        form.put("redirect_uri", props.redirectUri());
        form.put("client_id", exchangeClientId);
        form.put("code_verifier", pending.codeVerifier().value());
        ChatGptTokenClient.TokenResponse token = tokens.post(form);
        if (token == null) {
            return NOT_COMPLETED;
        }
        if (pending.dynamicRegistration() && ISSUED_CLIENT_ID.matcher(exchangeClientId).matches()) {
            registrations.persistClientIdIfAbsent(exchangeClientId);
        }
        UUID sessionId = pending.sessionId();
        Set<String> granted = new LinkedHashSet<>();
        if (token.scope() == null) {
            granted.addAll(props.scopeList());
        } else {
            for (String s : token.scope().trim().split("\\s+")) {
                if (!s.isEmpty()) {
                    granted.add(s);
                }
            }
        }
        if (!granted.contains(props.requiredScope())) {
            store.dropCredentials(sessionId);
            store.setFlag(sessionId, Flag.PLAN_NOT_ELIGIBLE);
            return NOT_ELIGIBLE;
        }
        store.putCredentials(new SessionCredentials(sessionId, exchangeClientId, new Secret(token.accessToken()),
            token.refreshToken() == null ? null : new Secret(token.refreshToken()),
            token.idToken() == null ? null : new Secret(token.idToken()),
            clock.instant().plusSeconds(token.expiresInSeconds()), granted));
        store.clearFlag(sessionId);
        return CONNECTED;
    }

    // ---- state / use / end ----

    public State connectionState(UUID sessionId) {
        Flag flag = store.flag(sessionId);
        if (flag == Flag.PLAN_NOT_ELIGIBLE) {
            return State.PLAN_NOT_ELIGIBLE;
        }
        if (flag == Flag.SESSION_EXPIRED) {
            return State.SESSION_EXPIRED;
        }
        return store.credentials(sessionId) != null ? State.CONNECTED : State.NOT_CONNECTED;
    }

    public void disconnect(UUID sessionId) {
        synchronized (lockFor(sessionId)) {
            store.clearSession(sessionId);
        }
    }

    private Object lockFor(UUID sessionId) {
        return locks[Math.floorMod(sessionId.hashCode(), locks.length)];
    }

    private static Object[] newLocks() {
        Object[] l = new Object[64];
        for (int i = 0; i < l.length; i++) {
            l[i] = new Object();
        }
        return l;
    }

    public SessionCredentials requireUsableCredentials(UUID sessionId) {
        synchronized (lockFor(sessionId)) {
            Flag flag = store.flag(sessionId);
            if (flag == Flag.PLAN_NOT_ELIGIBLE) {
                throw new ApiException(HttpStatus.FORBIDDEN, "CHATGPT_PLAN_NOT_ELIGIBLE",
                    "Your ChatGPT plan is not eligible for ORACUL");
            }
            if (flag == Flag.SESSION_EXPIRED) {
                throw expired();
            }
            SessionCredentials creds = store.credentials(sessionId);
            if (creds == null) {
                throw new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_NOT_CONNECTED",
                    "Connect ChatGPT to generate");
            }
            Instant threshold = clock.instant().plus(props.refreshSkew());
            if (creds.accessTokenExpiresAt().isAfter(threshold)) {
                return creds;
            }
            return refresh(creds);
        }
    }

    private SessionCredentials refresh(SessionCredentials creds) {
        ChatGptTokenClient.TokenResponse token = null;
        if (creds.refreshToken() != null) {
            Map<String, String> form = new LinkedHashMap<>();
            form.put("grant_type", "refresh_token");
            form.put("refresh_token", creds.refreshToken().value());
            form.put("client_id", creds.clientId());
            token = tokens.post(form);
        }
        if (token == null) {
            store.dropCredentials(creds.sessionId());
            store.setFlag(creds.sessionId(), Flag.SESSION_EXPIRED);
            throw expired();
        }
        SessionCredentials renewed = new SessionCredentials(creds.sessionId(), creds.clientId(),
            new Secret(token.accessToken()),
            token.refreshToken() != null ? new Secret(token.refreshToken()) : creds.refreshToken(),
            creds.idToken(), clock.instant().plusSeconds(token.expiresInSeconds()), creds.grantedScopes());
        store.putCredentials(renewed);
        return renewed;
    }

    private static ApiException expired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "CHATGPT_SESSION_EXPIRED",
            "ChatGPT session expired — please reconnect");
    }

    private static boolean tooLong(String value, int limit) {
        return value != null && value.length() > limit;
    }

    private static String randomUrlSafe(int bytes) {
        byte[] b = new byte[bytes];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    private static String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8).replace("+", "%20");
    }
}
