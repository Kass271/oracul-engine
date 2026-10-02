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

    /** Returns the parsed token response, or null on any failure (already logged without secrets). */
    TokenResponse post(Map<String, String> form) {
        try {
            String body = form.entrySet().stream()
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.UTF_8) + "="
                    + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
            HttpRequest request = HttpRequest.newBuilder(URI.create(props.tokenUrl()))
                .timeout(props.httpTimeout())
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                log.warn("chatgpt token request failed: status={}", response.statusCode());
                return null;
            }
            TokenResponse parsed = parse(response.body());
            if (parsed == null) {
                log.warn("chatgpt token request failed: status={}", response.statusCode());
            }
            return parsed;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("chatgpt token request failed: {}", e.getClass().getSimpleName());
            return null;
        } catch (Exception e) {
            log.warn("chatgpt token request failed: {}", e.getClass().getSimpleName());
            return null;
        }
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
