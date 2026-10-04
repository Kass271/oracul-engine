package com.oracul.app.chatgpt;

import com.oracul.app.api.model.RunFailureCode;
import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import com.oracul.app.common.ApiException;
import com.oracul.app.common.RawHttpGet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Tool-less calls to the OpenAI Responses API with the session's access token, following the documented plan-usage
 * transport (FR-38): {@code store:false}, {@code stream:true}, a model from {@code GET /models}, success only on
 * {@code response.completed}. Never logs bodies or tokens.
 */
@Component
public class HttpResponsesClient {

    private static final String TOOLS_MESSAGE = "Responses requests must not carry tools";
    private static final Logger log = LoggerFactory.getLogger(HttpResponsesClient.class);

    private static final String UNSUPPORTED_CAPABILITY = "subscription_sharing_unsupported_capability";
    private static final Set<String> SESSION_CODES = Set.of("subscription_sharing_invalid_user",
        "chatpass_v2_scope_not_authorized", "chatpass_v2_invalid_authorization_context");
    private static final Set<String> UNAVAILABLE_CODES = Set.of("subscription_sharing_usage_unavailable",
        "subscription_sharing_user_unavailable");
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Za-z0-9_.:-]{1,64}$");
    private static final int MAX_SLUG = 128;
    private static final int MAX_ERROR_BODY = 256 * 1024;
    private static final String FALLBACK_SENTENCE =
        "Answer with exactly one JSON object and nothing else (no Markdown fence). It must validate against this JSON Schema: ";

    private static final ScheduledExecutorService WATCHDOG = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "responses-watchdog");
        t.setDaemon(true);
        return t;
    });

    private final ChatGptAuthService auth;
    private final HttpClient http;
    private final JsonMapper json = JsonMapper.builder().build();
    private final String baseUrl;
    private final String model;
    private final Duration timeout;
    private final Duration streamTimeout;
    private final Duration retryDelay;
    private final Map<UUID, String> sessionModels = new ConcurrentHashMap<>();
    private volatile boolean structuredOutputSupported = true;

    HttpResponsesClient(ChatGptAuthService auth, String baseUrl, String model, Duration timeout) {
        this(auth, baseUrl, model, timeout, timeout, Duration.ofSeconds(1));
    }

    @org.springframework.beans.factory.annotation.Autowired
    HttpResponsesClient(ChatGptAuthService auth,
                        @Value("${oracul.openai.responses-base-url:https://api.openai.com/v1}") String baseUrl,
                        @Value("${oracul.openai.model:gpt-5}") String model,
                        @Value("${oracul.openai.timeout:PT30S}") Duration timeout,
                        @Value("${oracul.openai.stream-timeout:PT120S}") Duration streamTimeout,
                        @Value("${oracul.openai.retry-delay:PT1S}") Duration retryDelay) {
        this.auth = auth;
        String base = baseUrl;
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        this.baseUrl = base;
        this.model = model;
        this.timeout = timeout;
        this.streamTimeout = streamTimeout;
        this.retryDelay = retryDelay;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    /** The preferred model slug (oracul.openai.model); prompt builders are called with it. */
    public String model() {
        return model;
    }

    /** Sets the structured-output flag back to supported (tests that trigger the fallback call it in @AfterEach). */
    void resetStructuredOutput() {
        structuredOutputSupported = true;
    }

    // ---- model resolution (FR-38 step 1) ---------------------------------------------------------------------------

    /** As {@link #resolveModel(UUID, Runnable)} without a run guard. */
    public String resolveModel(UUID sessionId) {
        return resolveModel(sessionId, () -> { });
    }

    /**
     * Reads the account's model catalogue once for the run, remembers the chosen slug for the session and returns it.
     * Throws {@link ChatGptCallException} with the FR-39 / FR-40 / CHATGPT_NO_MODEL failure.
     */
    public String resolveModel(UUID sessionId, Runnable beforeSend) {
        sessionModels.remove(sessionId);
        String slug = withRetries(sessionId, beforeSend, creds -> {
            Exchange ex = get(creds);
            if (ex.err() != null) {
                return new Outcome<>(null, ex.err());
            }
            JsonNode root = parse(ex.body());
            if (root == null || !root.isObject()) {
                return new Outcome<>(null, Err.transport());
            }
            String chosen = choose(root.path("models"));
            return new Outcome<>(chosen == null ? "" : chosen, null);
        });
        if (slug == null || slug.isEmpty()) {
            throw ChatGptCallException.of(RunFailureCode.CHATGPT_NO_MODEL);
        }
        sessionModels.put(sessionId, slug);
        return slug;
    }

    private String choose(JsonNode models) {
        if (!models.isArray()) {
            return null;
        }
        String firstListed = null;
        String first = null;
        for (JsonNode entry : models) {
            String slug = slugOf(entry);
            if (slug == null) {
                continue;
            }
            if (slug.equals(model)) {
                return slug;
            }
            if (first == null) {
                first = slug;
            }
            if (firstListed == null && "list".equals(entry.path("visibility").asString(null))) {
                firstListed = slug;
            }
        }
        return firstListed != null ? firstListed : first;
    }

    private static String slugOf(JsonNode entry) {
        JsonNode slug = entry.path("slug");
        if (!slug.isString()) {
            return null;
        }
        String s = slug.asString();
        return s.isEmpty() || s.length() > MAX_SLUG ? null : s;
    }

    // ---- calls --------------------------------------------------------------------------------------------------------

    /**
     * One attempt, never retried (query expansion). Returns the output text, or empty on any failure that the caller
     * may answer with a fallback. Throws {@link ChatGptCallException} for CHATGPT_SESSION_EXPIRED,
     * CHATGPT_REGISTRATION_INVALID and CHATGPT_PLAN_NOT_ELIGIBLE.
     */
    public Optional<String> createText(UUID sessionId, Map<String, Object> body) {
        Call call = prepareCall(sessionId, body);
        try {
            SessionCredentials creds = auth.requireUsableCredentials(sessionId);
            Outcome<String> result = post(sessionId, creds, call, () -> { });
            if (result.err() == null) {
                return result.value() == null || result.value().isEmpty() ? Optional.empty() : Optional.of(result.value());
            }
            ChatGptCallException failure = classify(sessionId, result.err());
            if (fatalForExpansion(failure)) {
                throw failure;
            }
            return Optional.empty();
        } catch (ApiException e) {
            ChatGptCallException mapped = fromApi(e);
            if (fatalForExpansion(mapped)) {
                throw mapped;
            }
            return Optional.empty();
        }
    }

    private static boolean fatalForExpansion(ChatGptCallException e) {
        return switch (e.code()) {
            case "CHATGPT_SESSION_EXPIRED", "CHATGPT_REGISTRATION_INVALID", "CHATGPT_PLAN_NOT_ELIGIBLE" -> true;
            default -> false;
        };
    }

    /**
     * Like {@link #createText} but with the transport handling of the stage calls: failures are classified by the FR-39
     * table (retries only for the "unavailable" class). Empty means the call succeeded without usable output text (a
     * content problem). Throws {@link ChatGptCallException} otherwise.
     */
    public Optional<String> createTextOrThrow(UUID sessionId, Map<String, Object> body) {
        return createTextOrThrow(sessionId, body, () -> { });
    }

    /**
     * As {@link #createTextOrThrow(UUID, Map)}; {@code beforeSend} runs before every HTTP request (first attempt,
     * retries, fallback repeat) and may throw to stop the call, e.g. {@link CallAbandonedException}.
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
        Call call = prepareCall(sessionId, body);
        Integer[] last = new Integer[1];
        Throwable failure = null;
        try {
            String text = withRetries(sessionId, beforeSend, creds -> post(sessionId, creds, call, beforeSend, last));
            return text == null || text.isEmpty() ? Optional.empty() : Optional.of(text);
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

    /**
     * The request body exactly as it will be sent: the run's model, {@code store:false}, {@code stream:true} and, once
     * the structured-output fallback is in force, without {@code text.format}. Recorded in {@code model_call}.
     */
    public Map<String, Object> prepare(UUID sessionId, Map<String, Object> body) {
        return prepareCall(sessionId, body).asMap();
    }

    // ---- retry loop ----------------------------------------------------------------------------------------------------

    private interface Attempt<T> {
        Outcome<T> run(SessionCredentials creds);
    }

    private record Outcome<T>(T value, Err err) {
    }

    /** Runs {@code attempt} with the row-4 retries (2 retries, waits retry-delay and 2 x retry-delay). */
    private <T> T withRetries(UUID sessionId, Runnable beforeSend, Attempt<T> attempt) {
        ChatGptCallException lastFailure = null;
        for (int n = 0; n < 3; n++) {
            if (n > 0) {
                sleep(retryDelay.multipliedBy(1L << (n - 1)));
            }
            beforeSend.run();
            Outcome<T> outcome;
            try {
                SessionCredentials creds = auth.requireUsableCredentials(sessionId);
                outcome = attempt.run(creds);
            } catch (ApiException e) {
                ChatGptCallException mapped = fromApi(e);
                if (!"CHATGPT_UNAVAILABLE".equals(mapped.code())) {
                    throw mapped;
                }
                lastFailure = mapped;
                continue;
            }
            if (outcome.err() == null) {
                return outcome.value();
            }
            ChatGptCallException failure = classify(sessionId, outcome.err());
            if (!"CHATGPT_UNAVAILABLE".equals(failure.code())) {
                throw failure;
            }
            lastFailure = failure;
        }
        throw lastFailure == null ? ChatGptCallException.unavailable() : lastFailure;
    }

    private void sleep(Duration d) {
        try {
            Thread.sleep(d.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ChatGptCallException.unavailable();
        }
    }

    private ChatGptCallException fromApi(ApiException e) {
        return switch (e.code()) {
            case "CHATGPT_PLAN_NOT_ELIGIBLE" -> ChatGptCallException.of(RunFailureCode.CHATGPT_PLAN_NOT_ELIGIBLE);
            case "CHATGPT_REGISTRATION_INVALID" -> ChatGptCallException.of(RunFailureCode.CHATGPT_REGISTRATION_INVALID);
            case "CHATGPT_UNAVAILABLE" -> ChatGptCallException.unavailable();
            default -> ChatGptCallException.sessionExpired();
        };
    }

    // ---- classification (FR-39) ------------------------------------------------------------------------------------------

    /** What went wrong with one exchange: an HTTP error, a failure event, a transport failure or an unfinished stream. */
    private record Err(Kind kind, int status, String code, String param) {
        enum Kind { HTTP, EVENT, TRANSPORT, INCOMPLETE }

        static Err transport() {
            return new Err(Kind.TRANSPORT, 0, null, null);
        }

        static Err incomplete() {
            return new Err(Kind.INCOMPLETE, 0, null, null);
        }
    }

    private ChatGptCallException classify(UUID sessionId, Err err) {
        if (err.kind() == Err.Kind.INCOMPLETE) {
            return ChatGptCallException.of(RunFailureCode.CHATGPT_INCOMPLETE);
        }
        String code = err.code();
        int status = err.kind() == Err.Kind.HTTP ? err.status() : 0;
        if (status == 401 || (code != null && SESSION_CODES.contains(code))) {
            auth.sessionRejected(sessionId);
            return ChatGptCallException.sessionExpired();
        }
        if ("subscription_sharing_user_not_eligible".equals(code)) {
            return ChatGptCallException.of(RunFailureCode.CHATGPT_PLAN_NOT_ELIGIBLE);
        }
        if ("subscription_sharing_usage_limit_exceeded".equals(code) || (status == 429 && code == null)) {
            return ChatGptCallException.rateLimited();
        }
        if ((code != null && UNAVAILABLE_CODES.contains(code))
            || (code == null && (err.kind() == Err.Kind.TRANSPORT || status >= 500))) {
            return ChatGptCallException.unavailable();
        }
        if (UNSUPPORTED_CAPABILITY.equals(code) || "subscription_sharing_route_not_supported".equals(code)) {
            return ChatGptCallException.rejected(code);
        }
        if (code != null) {
            return ChatGptCallException.unexpected(SAFE_CODE.matcher(code).matches() ? code : "unknown_error");
        }
        return status > 0 ? ChatGptCallException.unexpected("http_" + status)
            : ChatGptCallException.of(RunFailureCode.CHATGPT_INCOMPLETE);
    }

    // ---- request preparation -----------------------------------------------------------------------------------------------

    /** A prepared request: the JSON tree being sent and whether the structured-output fallback shaped it. */
    private final class Call {
        private ObjectNode root;
        private String payload;
        private boolean fallbackApplied;

        Call(ObjectNode root, boolean fallbackApplied) {
            this.root = root;
            this.fallbackApplied = fallbackApplied;
            this.payload = json.writeValueAsString(root);
        }

        boolean hasTextFormat() {
            return root.path("text").path("format").isObject();
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> asMap() {
            return json.convertValue(root, LinkedHashMap.class);
        }

        /** Replaces the request by its structured-output fallback form (no text.format, schema in the instructions). */
        void toFallback() {
            ObjectNode copy = root.deepCopy();
            applyFallback(copy);
            this.root = copy;
            this.payload = json.writeValueAsString(copy);
            this.fallbackApplied = true;
        }
    }

    private Call prepareCall(UUID sessionId, Map<String, Object> body) {
        JsonNode tree = json.valueToTree(body);
        rejectWebSearch(tree); // NFR-3: the checked tree is the sent tree
        ObjectNode root = (ObjectNode) tree;
        String slug = sessionModels.get(sessionId);
        if (slug != null) {
            root.put("model", slug);
        }
        root.put("store", false);
        root.put("stream", true);
        boolean fallback = false;
        if (!structuredOutputSupported && root.path("text").path("format").isObject()) {
            applyFallback(root);
            fallback = true;
        }
        return new Call(root, fallback);
    }

    private void applyFallback(ObjectNode root) {
        JsonNode format = root.path("text").path("format");
        if (!format.isObject()) {
            return;
        }
        JsonNode schema = format.has("schema") ? format.get("schema") : format;
        String instructions = root.path("instructions").asString("");
        root.put("instructions", instructions + "\n\n" + FALLBACK_SENTENCE + json.writeValueAsString(schema));
        root.remove("text");
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

    // ---- exchange ------------------------------------------------------------------------------------------------------------

    private Outcome<String> post(UUID sessionId, SessionCredentials creds, Call call, Runnable beforeSend) {
        return post(sessionId, creds, call, beforeSend, new Integer[1]);
    }

    /** One logical attempt: the request and, when text.format is rejected, its single fallback repeat. */
    private Outcome<String> post(UUID sessionId, SessionCredentials creds, Call call, Runnable beforeSend, Integer[] last) {
        Result result = send(creds, call.payload, last);
        if (result.err() != null && call.hasTextFormat() && UNSUPPORTED_CAPABILITY.equals(result.err().code())
            && (result.err().param() == null || result.err().param().startsWith("text"))) {
            call.toFallback();
            beforeSend.run();
            result = send(creds, call.payload, last);
            boolean rejectedAgain = result.err() != null && UNSUPPORTED_CAPABILITY.equals(result.err().code());
            if (!rejectedAgain) {
                structuredOutputSupported = false;
            }
        }
        if (result.err() != null) {
            return new Outcome<>(null, result.err());
        }
        String text = result.text();
        if ((call.fallbackApplied || !structuredOutputSupported) && text != null) {
            text = stripFence(text);
        }
        return new Outcome<>(text, null);
    }

    private static String stripFence(String text) {
        String t = text.trim();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl > 0) {
                t = t.substring(nl + 1);
                int end = t.lastIndexOf("```");
                if (end >= 0) {
                    t = t.substring(0, end);
                }
            }
        }
        return t.trim();
    }

    private record Result(String text, Err err) {
    }

    private record Exchange(String body, Err err) {
    }

    /** POST /responses; the answer is a stream (or a final JSON object). */
    private Result send(SessionCredentials creds, String payload, Integer[] last) {
        last[0] = null;
        long deadlineNanos = System.nanoTime() + streamTimeout.toNanos();
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(baseUrl + "/responses"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + creds.accessToken().value())
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(payload))
                .build();
        } catch (RuntimeException e) {
            log.warn("responses request could not be built: {}", e.getClass().getSimpleName());
            return new Result(null, Err.transport());
        }
        HttpResponse<InputStream> response = headers(request);
        if (response == null) {
            return new Result(null, Err.transport());
        }
        int status = response.statusCode();
        last[0] = status > 0 ? status : null;
        Watch watch = new Watch(response.body(), deadlineNanos);
        try (InputStream in = response.body()) {
            if (status / 100 != 2) {
                log.warn("responses call failed: status={}", status);
                String body = readBounded(in, watch);
                return new Result(null, httpError(status, body));
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (contentType.toLowerCase(java.util.Locale.ROOT).startsWith("application/json")) {
                return fromJson(readBounded(in, watch), watch);
            }
            return fromStream(in, watch);
        } catch (IOException e) {
            log.warn("responses stream failed: {}", e.getClass().getSimpleName());
            return new Result(null, status / 100 == 2 ? Err.incomplete() : Err.transport());
        } finally {
            watch.cancel();
        }
    }

    /** Waits for the response headers (connect + time to headers); null on any failure. */
    private HttpResponse<InputStream> headers(HttpRequest request) {
        CompletableFuture<HttpResponse<InputStream>> future = null;
        try {
            future = http.sendAsync(request, HttpResponse.BodyHandlers.ofInputStream());
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("responses call timed out");
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("responses call failed: {}", rootName(e));
        }
        return null;
    }

    private static String rootName(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c instanceof java.util.concurrent.ExecutionException) {
            c = c.getCause();
        }
        return c.getClass().getSimpleName();
    }

    /** Closes the body when the whole-call deadline passes, which ends a blocked read. */
    private static final class Watch {
        private final ScheduledFuture<?> task;
        private volatile boolean expired;

        Watch(InputStream body, long deadlineNanos) {
            long left = Math.max(0, deadlineNanos - System.nanoTime());
            this.task = WATCHDOG.schedule(() -> {
                expired = true;
                try {
                    body.close();
                } catch (IOException e) {
                    // closing only unblocks the reader
                }
            }, left, TimeUnit.NANOSECONDS);
        }

        boolean expired() {
            return expired;
        }

        void cancel() {
            task.cancel(false);
        }
    }

    private String readBounded(InputStream in, Watch watch) throws IOException {
        try {
            byte[] bytes = in.readNBytes(MAX_ERROR_BODY);
            if (watch.expired()) {
                throw new IOException("deadline");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            if (watch.expired()) {
                throw new IOException("deadline");
            }
            throw e;
        }
    }

    private Err httpError(int status, String body) {
        JsonNode root = parse(body);
        String code = null;
        String param = null;
        if (root != null && root.path("error").isObject()) {
            JsonNode err = root.path("error");
            code = err.path("code").isString() ? err.path("code").asString() : null;
            param = err.path("param").isString() ? err.path("param").asString() : null;
        }
        return new Err(Err.Kind.HTTP, status, code, param);
    }

    private JsonNode parse(String text) {
        try {
            return text == null || text.isBlank() ? null : json.readTree(text);
        } catch (Exception e) {
            return null;
        }
    }

    /** A 200 application/json answer is the final response object. */
    private Result fromJson(String body, Watch watch) {
        JsonNode root = parse(body);
        if (root == null || !root.isObject()) {
            return new Result(null, Err.incomplete());
        }
        String status = root.path("status").asString(null);
        if ("completed".equals(status)) {
            return new Result(outputText(root, ""), null);
        }
        if ("failed".equals(status)) {
            return failure(root.path("error").path("code"), root.path("error").path("param"));
        }
        return new Result(null, Err.incomplete());
    }

    private static Result failure(JsonNode code, JsonNode param) {
        if (!code.isString()) {
            return new Result(null, Err.incomplete());
        }
        return new Result(null, new Err(Err.Kind.EVENT, 200, code.asString(),
            param.isString() ? param.asString() : null));
    }

    /** Reads Server-Sent Events; success only on response.completed. */
    private Result fromStream(InputStream in, Watch watch) throws IOException {
        StringBuilder deltas = new StringBuilder();
        StringBuilder data = new StringBuilder();
        String eventName = null;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while (true) {
                line = reader.readLine();
                if (watch.expired()) {
                    return new Result(null, Err.incomplete());
                }
                if (line == null || line.isEmpty()) {
                    if (data.length() > 0) {
                        Result done = handle(data.toString(), eventName, deltas);
                        data.setLength(0);
                        eventName = null;
                        if (done != null) {
                            return done;
                        }
                    }
                    if (line == null) {
                        return new Result(null, Err.incomplete());
                    }
                    continue;
                }
                if (line.startsWith(":")) {
                    continue;
                }
                int colon = line.indexOf(':');
                String field = colon < 0 ? line : line.substring(0, colon);
                String value = colon < 0 ? "" : line.substring(colon + 1);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (field.equals("data")) {
                    if (data.length() > 0) {
                        data.append('\n');
                    }
                    data.append(value);
                } else if (field.equals("event")) {
                    eventName = value;
                }
            }
        } catch (IOException e) {
            log.warn("responses stream failed: {}", e.getClass().getSimpleName());
            return new Result(null, Err.incomplete());
        }
    }

    /** The terminal result of one event, or null to keep reading. */
    private Result handle(String data, String eventName, StringBuilder deltas) {
        JsonNode event = parse(data);
        if (event == null || !event.isObject()) {
            return null;
        }
        String type = event.path("type").isString() ? event.path("type").asString() : eventName;
        if (type == null) {
            return null;
        }
        switch (type) {
            case "response.output_text.delta":
                if (event.path("delta").isString()) {
                    deltas.append(event.path("delta").asString());
                }
                return null;
            case "response.completed":
                return new Result(outputText(event.path("response"), deltas.toString()), null);
            case "response.incomplete":
                return new Result(null, Err.incomplete());
            case "response.failed":
                return failure(event.path("response").path("error").path("code"),
                    event.path("response").path("error").path("param"));
            case "error":
                if (event.path("code").isString()) {
                    return failure(event.path("code"), event.path("param"));
                }
                return failure(event.path("error").path("code"), event.path("error").path("param"));
            default:
                return null;
        }
    }

    /** Concatenated output_text parts of the message items; the deltas when there are none. */
    private static String outputText(JsonNode response, String deltas) {
        StringBuilder text = new StringBuilder();
        for (JsonNode item : response.path("output")) {
            if (!"message".equals(item.path("type").asString(null))) {
                continue;
            }
            for (JsonNode part : item.path("content")) {
                if ("output_text".equals(part.path("type").asString(null)) && part.path("text").isString()) {
                    text.append(part.path("text").asString());
                }
            }
        }
        return text.isEmpty() ? deltas : text.toString();
    }

    /** GET /models: exactly one request per attempt (see {@link RawHttpGet}). */
    private Exchange get(SessionCredentials creds) {
        try {
            RawHttpGet.Response r = RawHttpGet.get(URI.create(baseUrl + "/models"),
                Map.of("Authorization", "Bearer " + creds.accessToken().value(), "Accept", "application/json"),
                streamTimeout.compareTo(timeout) < 0 ? streamTimeout : timeout);
            if (r.status() / 100 != 2) {
                log.warn("models call failed: status={}", r.status());
                return new Exchange(null, httpError(r.status(), r.body()));
            }
            return new Exchange(r.body(), null);
        } catch (IOException | RuntimeException e) {
            log.warn("models call failed: {}", e.getClass().getSimpleName());
            return new Exchange(null, Err.transport());
        }
    }
}
