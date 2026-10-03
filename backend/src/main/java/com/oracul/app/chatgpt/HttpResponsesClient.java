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

    private static final String TOOLS_MESSAGE = "Responses requests must not carry tools";
    private static final Logger log = LoggerFactory.getLogger(HttpResponsesClient.class);

    private final ChatGptAuthService auth;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final Duration retryDelay;

    HttpResponsesClient(ChatGptAuthService auth, String baseUrl, String model, Duration timeout) {
        this(auth, baseUrl, model, timeout, Duration.ofSeconds(1));
    }

    @org.springframework.beans.factory.annotation.Autowired
    HttpResponsesClient(ChatGptAuthService auth,
                        @Value("${oracul.openai.responses-base-url:https://api.openai.com/v1}") String baseUrl,
                        @Value("${oracul.openai.model:gpt-5}") String model,
                        @Value("${oracul.openai.timeout:PT30S}") Duration timeout,
                        @Value("${oracul.openai.retry-delay:PT1S}") Duration retryDelay) {
        this.auth = auth;
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.model = model;
        this.timeout = timeout;
        this.retryDelay = retryDelay;
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
        String payload = serializeToolless(body);
        SessionCredentials creds = auth.requireUsableCredentials(sessionId);
        Result result = send(creds, payload);
        if (result.status() == 401 || result.status() == 403) {
            creds = auth.refreshAfterRejection(sessionId);
            result = send(creds, payload);
        }
        return result.text();
    }

    /**
     * Like {@link #createText} but with the transport-failure handling of the event calls: 429 fails at once, other
     * failures are retried once after the retry delay, a rejected token is refreshed once. Empty means the call
     * succeeded without usable output text (a content problem). Throws {@link ChatGptCallException} otherwise.
     */
    public Optional<String> createTextOrThrow(UUID sessionId, Map<String, Object> body) {
        return createTextOrThrow(sessionId, body, () -> { });
    }

    /**
     * As {@link #createTextOrThrow(UUID, Map)}; {@code beforeSend} runs before every HTTP request (first attempt, token
     * refresh retry, transport retry) and may throw to stop the call, e.g. {@link CallAbandonedException}.
     */
    public Optional<String> createTextOrThrow(UUID sessionId, Map<String, Object> body, Runnable beforeSend) {
        return createTextOrThrow(sessionId, body, beforeSend, status -> { });
    }

    /**
     * As {@link #createTextOrThrow(UUID, Map, Runnable)}; {@code finalStatus} receives the HTTP status of the last
     * request made (null when no HTTP response arrived or no request was sent) when the call ends, normally or not.
     */
    public Optional<String> createTextOrThrow(UUID sessionId, Map<String, Object> body, Runnable beforeSend,
                                              java.util.function.Consumer<Integer> finalStatus) {
        String payload = serializeToolless(body);
        Integer[] last = new Integer[1];
        Throwable failure = null;
        try {
            beforeSend.run();
            SessionCredentials creds = auth.requireUsableCredentials(sessionId);
            Result result = send(creds, payload);
            last[0] = status(result);
            if (rejected(result)) {
                beforeSend.run();
                creds = auth.refreshAfterRejection(sessionId);
                result = send(creds, payload);
                last[0] = status(result);
                if (rejected(result)) {
                    throw ChatGptCallException.sessionExpired();
                }
            }
            if (result.status() == 429) {
                throw ChatGptCallException.rateLimited();
            }
            if (failed(result)) {
                try {
                    Thread.sleep(retryDelay.toMillis());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw ChatGptCallException.unavailable();
                }
                beforeSend.run();
                result = send(creds, payload);
                last[0] = status(result);
                if (rejected(result)) {
                    throw ChatGptCallException.sessionExpired();
                }
                if (result.status() == 429) {
                    throw ChatGptCallException.rateLimited();
                }
                if (failed(result)) {
                    throw ChatGptCallException.unavailable();
                }
            }
            return result.text();
        } catch (com.oracul.app.common.ApiException e) {
            ChatGptCallException mapped = ChatGptCallException.sessionExpired();
            failure = mapped;
            throw mapped;
        } catch (RuntimeException | Error t) {
            failure = t;
            throw t;
        } finally {
            try {
                finalStatus.accept(last[0]);
            } catch (RuntimeException | Error statusError) {
                if (failure == null) {
                    throw statusError;
                }
                failure.addSuppressed(statusError);
            }
        }
    }

    private static Integer status(Result r) {
        return r.status() > 0 ? r.status() : null;
    }

    /** NFR-3: serializes once; the checked payload is exactly the sent payload. */
    private String serializeToolless(Map<String, Object> body) {
        String payload = json.writeValueAsString(body);
        JsonNode root = json.readTree(payload);
        rejectWebSearch(root);
        return payload;
    }

    private static void rejectWebSearch(JsonNode node) {
        if (node.isObject()) {
            for (Map.Entry<String, JsonNode> e : node.properties()) {
                if (e.getKey().startsWith("web_search") || e.getKey().equals("tools") || e.getKey().equals("tool_choice")) {
                    throw new IllegalStateException(TOOLS_MESSAGE);
                }
                rejectWebSearch(e.getValue());
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                rejectWebSearch(child);
            }
        }
    }

    private static boolean rejected(Result r) {
        return r.status() == 401 || r.status() == 403;
    }

    private static boolean failed(Result r) {
        return r.status() < 200 || r.status() >= 300;
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
