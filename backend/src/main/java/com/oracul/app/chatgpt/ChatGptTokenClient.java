package com.oracul.app.chatgpt;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Form POSTs to the OpenAI token endpoint. Never logs bodies, headers or form values. */
@Component
class ChatGptTokenClient {

    private static final Logger log = LoggerFactory.getLogger(ChatGptTokenClient.class);
    private static final long DEFAULT_EXPIRES_IN = 3600;

    record TokenResponse(String accessToken, String refreshToken, String idToken, long expiresInSeconds,
                         String scope) {
        @Override
        public String toString() {
            return "[REDACTED]";
        }
    }

    private final ChatGptProperties props;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();

    ChatGptTokenClient(ChatGptProperties props) {
        this.props = props;
        this.http = HttpClient.newBuilder()
            .connectTimeout(props.httpTimeout())
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();
    }

    /** Outcome of a token request: a response, or a failure that may be an invalid_grant. */
    record Result(TokenResponse token, boolean invalidGrant) {
    }

    /** Returns the parsed token response, or null on any failure (already logged without secrets). */
    TokenResponse post(Map<String, String> form) {
        return exchange(form).token();
    }

    /** Best-effort token revocation: one attempt, every outcome is only logged (never the token). */
    void revoke(String token, String clientId) {
        try {
            Map<String, String> form = new java.util.LinkedHashMap<>();
            form.put("token", token);
            form.put("token_type_hint", "refresh_token");
            form.put("client_id", clientId);
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.revocationUrl()))
                .timeout(props.httpTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(encodeForm(form)))
                .build();
            HttpResponse<Void> response = http.send(request, HttpResponse.BodyHandlers.discarding());
            log.info("chatgpt revocation: status={}", response.statusCode());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("chatgpt revocation failed: {}", e.getClass().getSimpleName());
        } catch (Exception e) {
            log.warn("chatgpt revocation failed: {}", e.getClass().getSimpleName());
        }
    }

    private static String encodeForm(Map<String, String> form) {
        return form.entrySet().stream()
            .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
            .collect(Collectors.joining("&"));
    }

    Result exchange(Map<String, String> form) {
        Result failed = new Result(null, false);
        try {
            String body = encodeForm(form);
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.tokenUrl()))
                .timeout(props.httpTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("chatgpt token request failed: status={}", response.statusCode());
                int status = response.statusCode();
                return new Result(null, status >= 400 && status < 500 && isInvalidGrant(response.body()));
            }
            TokenResponse parsed = parse(response.body());
            if (parsed == null) {
                log.warn("chatgpt token request failed: status={}", response.statusCode());
            }
            return new Result(parsed, false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("chatgpt token request failed: {}", e.getClass().getSimpleName());
            return failed;
        } catch (Exception e) {
            log.warn("chatgpt token request failed: {}", e.getClass().getSimpleName());
            return failed;
        }
    }

    private boolean isInvalidGrant(String body) {
        try {
            if (json.readValue(body, Object.class) instanceof Map<?, ?> m) {
                Object err = m.get("error");
                if (err instanceof Map<?, ?> em) {
                    err = em.get("code");
                }
                return "invalid_grant".equals(err);
            }
        } catch (Exception e) {
            // not JSON: not an invalid_grant
        }
        return false;
    }

    private TokenResponse parse(String body) {
        Map<?, ?> map;
        try {
            Object value = json.readValue(body, Object.class);
            if (!(value instanceof Map<?, ?> m)) {
                return null;
            }
            map = m;
        } catch (Exception e) {
            return null;
        }
        String access = text(map.get("access_token"));
        if (access == null || access.isEmpty()) {
            return null;
        }
        long expiresIn = map.get("expires_in") instanceof Number n ? n.longValue() : DEFAULT_EXPIRES_IN;
        return new TokenResponse(access, text(map.get("refresh_token")), text(map.get("id_token")), expiresIn,
            text(map.get("scope")));
    }

    private static String text(Object value) {
        return value instanceof String s && !s.isEmpty() ? s : null;
    }
}
