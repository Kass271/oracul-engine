package com.oracul.app.research;

import com.jayway.jsonpath.JsonPath;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
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

    public record Reply(int status, String body, long delayMs) {}

    public static final StubResponses INSTANCE = new StubResponses();

    private static final Pattern TASK_LINE = Pattern.compile("^- (I\\d+) \\| (\\w+) \\| (\\d+) \\| ", Pattern.MULTILINE);

    private final HttpServer server;
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    public volatile Function<Request, Reply> responder = defaultResponder();

    private StubResponses() {
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        server.setExecutor(Executors.newCachedThreadPool());
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
        requests.clear();
        responder = defaultResponder();
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

    public static Reply status(int status, String body) {
        return new Reply(status, body, 0);
    }

    public static Reply delayed(Reply r, long ms) {
        return new Reply(r.status(), r.body(), ms);
    }

    public Function<Request, Reply> defaultResponder() {
        return req -> {
            String text = req.inputText();
            if (text.startsWith("ORACUL REQUEST QUERY_EXPANSION")) return completed(queriesJson(defaultQueries(text)));
            return status(400, "{\"error\":\"unexpected request\"}");
        };
    }

    private void handle(HttpExchange ex) throws IOException {
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> headers = new LinkedHashMap<>();
        ex.getRequestHeaders().forEach((k, v) -> headers.put(k.toLowerCase(), String.join(",", v)));
        Request req = new Request(headers, body);
        requests.add(req);
        Reply reply;
        try {
            reply = responder.apply(req);
        } catch (RuntimeException e) {
            reply = status(500, "{}");
        }
        try {
            if (reply.delayMs() > 0) Thread.sleep(reply.delayMs());
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
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
