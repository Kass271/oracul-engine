package com.oracul.app.research;

import com.jayway.jsonpath.JsonPath;
import com.oracul.app.reasoning.ScenarioFixtures;
import com.oracul.app.result.StoryFixtures;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.test.context.DynamicPropertyRegistry;

/** In-process stand-in for the OpenAI Responses API (NFR-7). One instance per JVM, reset before every test. */
public final class StubResponses {

    /** Recorded request: lower-cased header names and the raw body. */
    public record Request(Map<String, String> headers, String body) {
        /** Text of the first input_text part. */
        public String inputText() {
            return JsonPath.read(body, "$.input[0].content[0].text");
        }
    }

    /** status 0 = drop the connection without answering. */
    public record Reply(int status, String body, long delayMs) {}

    /** Arrival and completion time (System.nanoTime) of one request; completedNanos is 0 until it was answered. */
    public static final class Exchange {
        public final Request request;
        public final String purpose;
        public final long arrivedNanos;
        public volatile long completedNanos;

        Exchange(Request request, String purpose, long arrivedNanos) {
            this.request = request;
            this.purpose = purpose;
            this.arrivedNanos = arrivedNanos;
        }
    }

    public static final StubResponses INSTANCE = new StubResponses();

    private static final Pattern TASK_LINE = Pattern.compile("^- (I\\d+) \\| (\\w+) \\| (\\d+) \\| ", Pattern.MULTILINE);

    private final HttpServer server;
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    /** One entry per request, in arrival order, with arrival and completion times. */
    public final List<Exchange> exchanges = new CopyOnWriteArrayList<>();
    public volatile Function<Request, Reply> responder = defaultResponder();

    // concurrency bookkeeping (research-pipeline.md "Backend test stubs"): in flight = arrived and not yet answered
    private final Map<String, AtomicInteger> inFlight = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> maxInFlight = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> arrived = new ConcurrentHashMap<>();
    private final Map<String, Duration> purposeDelays = new ConcurrentHashMap<>();
    private final Map<Integer, Duration> batchDelays = new ConcurrentHashMap<>();
    private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();

    private StubResponses() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        // the server answers requests concurrently: >= 16 threads, so 16 gated / delayed requests never starve each other
        AtomicInteger threadNo = new AtomicInteger();
        server.setExecutor(Executors.newFixedThreadPool(32, r -> {
            Thread t = new Thread(r, "stub-responses-" + threadNo.incrementAndGet());
            t.setDaemon(true);
            return t;
        }));
        server.createContext("/v1/responses", this::handle);
        server.start();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + port() + "/v1";
    }

    public static void registerAll(DynamicPropertyRegistry r) {
        r.add("oracul.openai.responses-base-url", INSTANCE::baseUrl);
        r.add("oracul.openai.model", () -> "stub-model");
    }

    public void reset() {
        for (CountDownLatch gate : gates.values()) gate.countDown(); // never leave a handler thread parked
        gates.clear();
        purposeDelays.clear();
        batchDelays.clear();
        requests.clear();
        exchanges.clear();
        arrived.clear();
        // requests of the previous test that are still being answered keep their in-flight count
        maxInFlight.clear();
        inFlight.forEach((purpose, now) -> maxInFlight.put(purpose, new AtomicInteger(now.get())));
        responder = defaultResponder();
    }

    /** Highest number of requests of the purpose that were being answered at the same time since the last reset. */
    public int maxInFlight(String purpose) {
        AtomicInteger max = maxInFlight.get(purpose);
        return max == null ? 0 : max.get();
    }

    /** Answer every request of the purpose only after the delay. */
    public void delay(String purpose, Duration delay) {
        purposeDelays.put(purpose, delay);
    }

    /** Answer every EVENT_NORMALIZATION request whose "Batch: k of n" line has this k only after the delay. */
    public void delayBatch(int k, Duration delay) {
        batchDelays.put(k, delay);
    }

    /** Hold every request of the purpose until {@link #release(String)} (requests arriving later are held too). */
    public void gate(String purpose) {
        gates.put(purpose, new CountDownLatch(1));
    }

    /** Opens the gate of the purpose: held requests continue, later ones are not held. */
    public void release(String purpose) {
        CountDownLatch gate = gates.remove(purpose);
        if (gate != null) gate.countDown();
    }

    /** Blocks until at least {@code count} requests of the purpose have arrived (since the last reset). */
    public boolean awaitArrived(String purpose, int count, Duration timeout) {
        long end = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < end) {
            AtomicInteger n = arrived.get(purpose);
            if (n != null && n.get() >= count) return true;
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        AtomicInteger n = arrived.get(purpose);
        return n != null && n.get() >= count;
    }

    /** Number of requests of the purpose that arrived since the last reset. */
    public int arrivedCount(String purpose) {
        AtomicInteger n = arrived.get(purpose);
        return n == null ? 0 : n.get();
    }

    /** One TASK line of the QUERY_EXPANSION prompt. */
    public record TaskLine(String intentId, String bucket, int count) {}

    public static List<TaskLine> taskLines(String inputText) {
        List<TaskLine> out = new ArrayList<>();
        Matcher m = TASK_LINE.matcher(inputText);
        while (m.find()) out.add(new TaskLine(m.group(1), m.group(2), Integer.parseInt(m.group(3))));
        return out;
    }

    public static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                default -> sb.append(c);
            }
        }
        return sb.append('"').toString();
    }

    /** Stub reply shape: a completed response whose single output_text is {@code outputText}. */
    public static Reply completed(String outputText) {
        return new Reply(200, "{\"id\":\"resp_1\",\"status\":\"completed\",\"output\":[{\"type\":\"message\","
            + "\"role\":\"assistant\",\"content\":[{\"type\":\"output_text\",\"text\":" + jsonString(outputText) + "}]}]}", 0);
    }

    /** {"queries":[{intentId,text}...]} from (intentId, text) pairs. */
    public static String queriesJson(List<String[]> pairs) {
        StringBuilder sb = new StringBuilder("{\"queries\":[");
        for (int i = 0; i < pairs.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"intentId\":").append(jsonString(pairs.get(i)[0])).append(",\"text\":")
                .append(jsonString(pairs.get(i)[1])).append('}');
        }
        return sb.append("]}").toString();
    }

    /** Default answer: "<id> stub query <i>" for i = 1..n per intent. */
    public static List<String[]> defaultQueries(String inputText) {
        List<String[]> pairs = new ArrayList<>();
        for (TaskLine t : taskLines(inputText)) {
            for (int i = 1; i <= t.count(); i++) pairs.add(new String[] {t.intentId(), t.intentId() + " stub query " + i});
        }
        return pairs;
    }


    private static final String PURPOSE_PREFIX = "ORACUL REQUEST ";
    private static final Pattern SOURCE_LINE = Pattern.compile("^(S\\d+) \\| ", Pattern.MULTILINE);
    private static final Pattern EVENT_LINE = Pattern.compile("^(EV\\d+) \\| ", Pattern.MULTILINE);

    /** The word after "ORACUL REQUEST " of an input text, or "" when absent. */
    public static String purposeOf(String inputText) {
        if (!inputText.startsWith(PURPOSE_PREFIX)) return "";
        int end = inputText.indexOf('\n');
        return inputText.substring(PURPOSE_PREFIX.length(), end < 0 ? inputText.length() : end).trim();
    }

    public static String purpose(Request req) {
        return purposeOf(req.inputText());
    }

    private static final Pattern BATCH_LINE = Pattern.compile("^Batch: (\\d+) of (\\d+) \\|", Pattern.MULTILINE);

    /** k of the "Batch: <k> of <n> | ..." line of an EVENT_NORMALIZATION input text; 0 when there is no such line. */
    public static int batch(String inputText) {
        Matcher m = BATCH_LINE.matcher(inputText);
        return m.find() ? Integer.parseInt(m.group(1)) : 0;
    }

    public static int batch(Request req) {
        return batch(req.inputText());
    }

    /** n of the "Batch: <k> of <n> | ..." line; 0 when there is no such line. */
    public static int batchCount(String inputText) {
        Matcher m = BATCH_LINE.matcher(inputText);
        return m.find() ? Integer.parseInt(m.group(2)) : 0;
    }

    /** Content lines of the named ORACUL_UNTRUSTED_DATA block ("" when the block is missing). */
    public static String dataBlock(String inputText, String name) {
        String open = "<<<ORACUL_UNTRUSTED_DATA name=\"" + name + "\">>>";
        int start = inputText.indexOf(open);
        if (start < 0) return "";
        start += open.length();
        if (start < inputText.length() && inputText.charAt(start) == '\n') start++;
        int end = inputText.indexOf("<<<END_ORACUL_UNTRUSTED_DATA>>>", start);
        if (end < 0) return "";
        return inputText.substring(start, end).stripTrailing();
    }

    /** Source ids (S001...) of the "sources" block lines, in order. */
    public static List<String> sourceIds(String inputText) {
        List<String> out = new ArrayList<>();
        Matcher m = SOURCE_LINE.matcher(dataBlock(inputText, "sources"));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** Event ids (EV001...) of the "events" block lines, in order. */
    public static List<String> eventIds(String inputText) {
        List<String> out = new ArrayList<>();
        Matcher m = EVENT_LINE.matcher(dataBlock(inputText, "events"));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    public static String defaultNormalization(String inputText) {
        StringBuilder sb = new StringBuilder("{\"events\":[");
        List<String> ids = sourceIds(inputText);
        for (int i = 0; i < ids.size(); i++) {
            String id = ids.get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"sourceIds\":[\"").append(id).append("\"],\"date\":null,\"category\":\"general\",\"entities\":[\"Entity ")
                .append(id).append("\"],\"summary\":\"Stub event ").append(id).append("\",\"disagreement\":null,\"confidence\":0.8}");
        }
        return sb.append("]}").toString();
    }

    public static String defaultClassificationEntry(String eventId) {
        return "{\"eventId\":\"" + eventId + "\",\"topic\":\"general\",\"subtopics\":[],\"sentiment\":0.1,\"risk\":0.4,"
            + "\"opportunity\":0.6,\"impact\":0.5,\"novelty\":0.5,\"trend\":\"ESTABLISHED\",\"geography\":\"global\",\"wildcardMatches\":[]}";
    }

    public static String defaultClassification(String inputText) {
        List<String> entries = new ArrayList<>();
        for (String id : eventIds(inputText)) entries.add(defaultClassificationEntry(id));
        return "{\"classifications\":[" + String.join(",", entries) + "]}";
    }

    /** D of a SCENARIO_GENERATION request: the day after the start of its future event date window. */
    public static String futureDate(String inputText) {
        return ScenarioFixtures.futureDate(inputText);
    }

    /** SC-V4 / SC-E099 / SC-NOEV / SC-BAD with D taken from the request text. */
    public static String scenarioFixture(String name, String inputText) {
        return ScenarioFixtures.fixture(name, futureDate(inputText));
    }

    /** R of the "Attempt: n | Reason: R" line of a SCENARIO_GENERATION request. */
    public static String attemptReason(String inputText) {
        return ScenarioFixtures.attemptReason(inputText);
    }

    /** ST-* / TODAY / LATE / BADDATE answer text for a STORY_WRITING request text (see StoryFixtures.fixture). */
    public static String storyFixture(String name, String inputText) {
        return StoryFixtures.fixture(name, inputText);
    }

    public static Reply status(int status, String body) {
        return new Reply(status, body, 0);
    }

    public static Reply delayed(Reply r, long ms) {
        return new Reply(r.status(), r.body(), ms);
    }

    public Function<Request, Reply> defaultResponder() {
        return req -> {
            String text = req.inputText();
            String purpose = purposeOf(text);
            if ("QUERY_EXPANSION".equals(purpose)) return completed(queriesJson(defaultQueries(text)));
            if ("EVENT_NORMALIZATION".equals(purpose)) return completed(defaultNormalization(text));
            if ("EVENT_CLASSIFICATION".equals(purpose)) return completed(defaultClassification(text));
            if ("SCENARIO_GENERATION".equals(purpose)) return completed(ScenarioFixtures.scenarioDefault(text));
            if ("STORY_WRITING".equals(purpose)) return completed(StoryFixtures.stDefault(StoryFixtures.futureEventDate(text)));
            return status(400, "{\"error\":\"unexpected request\"}");
        };
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        Request req = new Request(headers, body);
        String purpose;
        try {
            purpose = purpose(req);
        } catch (RuntimeException e) {
            purpose = "";
        }
        Exchange exchange = new Exchange(req, purpose, System.nanoTime());
        int now = inFlight.computeIfAbsent(purpose, k -> new AtomicInteger()).incrementAndGet();
        maxInFlight.computeIfAbsent(purpose, k -> new AtomicInteger()).accumulateAndGet(now, Math::max);
        requests.add(req);
        exchanges.add(exchange);
        arrived.computeIfAbsent(purpose, k -> new AtomicInteger()).incrementAndGet();
        try {
            respond(ex, req, purpose);
        } finally {
            exchange.completedNanos = System.nanoTime();
            inFlight.get(purpose).decrementAndGet();
        }
    }

    private void respond(HttpExchange ex, Request req, String purpose) {
        CountDownLatch gate = gates.get(purpose);
        if (gate != null) {
            try {
                gate.await(60, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Reply reply;
        try {
            reply = responder.apply(req);
        } catch (RuntimeException e) {
            reply = status(500, "{}");
        }
        long delayMs = reply.delayMs();
        Duration purposeDelay = purposeDelays.get(purpose);
        if (purposeDelay != null) delayMs += purposeDelay.toMillis();
        if ("EVENT_NORMALIZATION".equals(purpose) && !batchDelays.isEmpty()) {
            Duration batchDelay = batchDelays.get(batch(req));
            if (batchDelay != null) delayMs += batchDelay.toMillis();
        }
        try {
            if (delayMs > 0) Thread.sleep(delayMs);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        if (reply.status() == 0) {
            ex.close(); // status 0 = drop the connection without answering
            return;
        }
        byte[] out = reply.body().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        try {
            ex.sendResponseHeaders(reply.status(), out.length == 0 ? -1 : out.length);
            if (out.length > 0) ex.getResponseBody().write(out);
        } catch (IOException ignored) {
            // client already timed out
        } finally {
            ex.close();
        }
    }
}
