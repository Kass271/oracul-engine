package com.oracul.app.chatgpt;

import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Tool-less calls to the OpenAI Responses API with the session's access token. Never logs bodies or tokens. */
@Component
public class HttpResponsesClient {

    private static final Logger log = LoggerFactory.getLogger(HttpResponsesClient.class);

    private final ChatGptAuthService auth;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final Duration timeout;

    HttpResponsesClient(ChatGptAuthService auth,
                        @Value("${oracul.openai.responses-base-url:https://api.openai.com/v1}") String baseUrl,
                        @Value("${oracul.openai.model:gpt-5}") String model,
                        @Value("${oracul.openai.timeout:PT30S}") Duration timeout) {
        this.auth = auth;
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.model = model;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public String model() {
        return model;
    }

    /**
     * Sends {@code body} to {@code <base>/responses}. Returns the concatenated output text, or empty on any failure.
     * Throws {@link com.oracul.app.common.ApiException} (CHATGPT_SESSION_EXPIRED / NOT_CONNECTED) when the session is
     * unusable.
     */
    public Optional<String> createText(UUID sessionId, Map<String, Object> body) {
        String payload = json.writeValueAsString(body);
        SessionCredentials creds = auth.requireUsableCredentials(sessionId);
        Result result = send(creds, payload);
        if (result.status() == 401 || result.status() == 403) {
            creds = auth.refreshAfterRejection(sessionId);
            result = send(creds, payload);
        }
        return result.text();
    }

    private record Result(int status, Optional<String> text) {
    }

    private Result send(SessionCredentials creds, String payload) {
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + creds.accessToken().value())
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
            java.util.concurrent.CompletableFuture<HttpResponse<String>> future =
                http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> response;
            try {
                response = future.get(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (java.util.concurrent.TimeoutException e) {
                future.cancel(true);
                log.warn("responses call timed out");
                return new Result(-1, Optional.empty());
            } catch (InterruptedException e) {
                future.cancel(true);
                throw e;
            }
            int status = response.statusCode();
            if (status / 100 != 2) {
                log.warn("responses call failed: status={}", status);
                return new Result(status, Optional.empty());
            }
            return new Result(status, outputText(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(-1, Optional.empty());
        } catch (Exception e) {
            log.warn("responses call failed: {}", e.getClass().getSimpleName());
            return new Result(-1, Optional.empty());
        }
    }

    private Optional<String> outputText(String body) {
        try {
            JsonNode root = json.readTree(body);
            if (!"completed".equals(root.path("status").asString(null))) {
                log.warn("responses call not completed");
                return Optional.empty();
            }
            StringBuilder text = new StringBuilder();
            for (JsonNode item : root.path("output")) {
                if (!"message".equals(item.path("type").asString(null))) {
                    continue;
                }
                for (JsonNode part : item.path("content")) {
                    if ("output_text".equals(part.path("type").asString(null)) && part.path("text").isString()) {
                        text.append(part.path("text").asString());
                    }
                }
            }
            return text.isEmpty() ? Optional.empty() : Optional.of(text.toString());
        } catch (Exception e) {
            log.warn("responses answer unreadable: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }
}
