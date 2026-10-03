package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * scenario-reasoning.md "Slice 08_validated-scenario" tool guard: a body with tools, tool_choice or a web_search* key at any
 * depth throws IllegalStateException before any HTTP request; a normal body is sent once.
 */
// @trace FR-19
class HttpResponsesClientToolGuardTest {

    private static final String MESSAGE = "Responses requests must not carry tools";
    private static final String OK = "{\"status\":\"completed\",\"output\":[{\"type\":\"message\",\"role\":\"assistant\","
        + "\"content\":[{\"type\":\"output_text\",\"text\":\"ok\"}]}]}";

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();
    private final UUID sid = UUID.randomUUID();
    private HttpResponsesClient client;

    @BeforeEach
    void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/responses", ex -> {
            requests.incrementAndGet();
            ex.getRequestBody().readAllBytes();
            byte[] out = OK.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.sendResponseHeaders(200, out.length);
            ex.getResponseBody().write(out);
            ex.close();
        });
        server.start();
        ChatGptAuthService auth = mock(ChatGptAuthService.class);
        when(auth.requireUsableCredentials(any())).thenReturn(new SessionCredentials(sid, "client", new Secret("tok"),
            new Secret("ref"), new Secret("id"), Instant.now().plusSeconds(3600), Set.of()));
        client = new HttpResponsesClient(auth, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1", "m", Duration.ofSeconds(5));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    static List<Object[]> forbiddenBodies() {
        return List.of(
            new Object[] {"tools: []", Map.of("model", "m", "tools", List.of())},
            new Object[] {"tool_choice: auto", Map.of("model", "m", "tool_choice", "auto")},
            new Object[] {"text.format.web_search_options: {}",
                Map.of("model", "m", "text", Map.of("format", Map.of("web_search_options", Map.of())))},
            new Object[] {"web_search_preview at top level", Map.of("model", "m", "web_search_preview", true)},
            new Object[] {"web_search inside a list element",
                Map.of("model", "m", "input", List.of(Map.of("role", "user", "web_search", Map.of())))},
            new Object[] {"tools inside a JsonNode value (ObjectNode)", Map.of("model", "m", "input", node("{\"tools\":[]}"))},
            new Object[] {"tool_choice inside a JsonNode value (ObjectNode)",
                Map.of("model", "m", "input", node("{\"a\":{\"tool_choice\":\"auto\"}}"))},
            new Object[] {"web_search_preview inside a JsonNode value (ObjectNode)",
                Map.of("model", "m", "text", node("{\"format\":{\"web_search_preview\":{}}}"))},
            new Object[] {"web_search inside an ArrayNode element",
                Map.of("model", "m", "input", node("[{\"role\":\"user\",\"web_search\":{}}]"))},
            new Object[] {"web_search deep inside an array of arrays of maps",
                Map.of("model", "m", "input", List.of(List.of(List.of(Map.of("x", List.of(Map.of("web_search_call", 1)))))))},
            new Object[] {"web_search deep inside a JsonNode array at depth",
                Map.of("model", "m", "input", List.of(node("[[{\"k\":[{\"web_search\":1}]}]]")))},
            new Object[] {"record field serializing to tools", Map.of("model", "m", "input", new Holder(List.of(), null, null))},
            new Object[] {"record field serializing to tool_choice", Map.of("model", "m", "input", new Holder(null, "auto", null))},
            new Object[] {"record nested in a list with a web_search key",
                Map.of("model", "m", "input", List.of(new Holder(null, null, Map.of()))) },
            new Object[] {"POJO getter serializing to tools", Map.of("model", "m", "input", new Pojo())});
    }

    public record Holder(List<Object> tools, String tool_choice, Map<String, Object> web_search) {
    }

    public static class Pojo {
        public String getTools() {
            return "x";
        }
    }

    private static tools.jackson.databind.JsonNode node(String raw) {
        return tools.jackson.databind.json.JsonMapper.builder().build().readTree(raw);
    }

    @ParameterizedTest(name = "createTextOrThrow rejects {0}")
    @MethodSource("forbiddenBodies")
    void createTextOrThrowRejectsToolsBeforeAnyRequest(String name, Map<String, Object> body) {
        assertThatThrownBy(() -> client.createTextOrThrow(sid, body))
            .isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(requests.get()).as("no request is sent").isZero();
    }

    @ParameterizedTest(name = "createTextOrThrow with beforeSend rejects {0}")
    @MethodSource("forbiddenBodies")
    void theGuardAlsoHoldsForTheCallWithBeforeSend(String name, Map<String, Object> body) {
        assertThatThrownBy(() -> client.createTextOrThrow(sid, body, () -> { }))
            .isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(requests.get()).isZero();
    }

    @ParameterizedTest(name = "createText rejects {0}")
    @MethodSource("forbiddenBodies")
    void createTextRejectsToolsBeforeAnyRequest(String name, Map<String, Object> body) {
        assertThatThrownBy(() -> client.createText(sid, body))
            .isInstanceOf(IllegalStateException.class).hasMessage(MESSAGE);
        assertThat(requests.get()).isZero();
    }

    @Test
    void aNormalBodyIsSentExactlyOnce() {
        Map<String, Object> body = Map.of("model", "m", "instructions", "i", "store", false,
            "input", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", "hello")))),
            "text", Map.of("format", Map.of("type", "json_schema", "name", "x", "strict", true, "schema", Map.of("type", "object"))));
        Optional<String> out = client.createTextOrThrow(sid, body);
        assertThat(out).contains("ok");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void theGuardLooksAtKeysOnlyNotAtTextValues() {
        Map<String, Object> body = Map.of("model", "m", "instructions", "Do not use tools or web_search; tool_choice is none.",
            "input", List.of(Map.of("role", "user", "content", List.of(Map.of("type", "input_text", "text", "tools web_search tool_choice")))));
        assertThat(client.createTextOrThrow(sid, body)).contains("ok");
        assertThat(requests.get()).isEqualTo(1);
    }

    @Test
    void theOriginalExceptionSurvivesAFailingFinalStatusUpdate() {
        RuntimeException dbError = new IllegalArgumentException("db down");
        Map<String, Object> body = Map.of("model", "m", "input", "x");
        assertThatThrownBy(() -> client.createTextOrThrow(sid, body, () -> { throw new CallAbandonedException(); },
            status -> { throw dbError; }))
            .isInstanceOf(CallAbandonedException.class)
            .satisfies(e -> assertThat(e.getSuppressed()).contains(dbError));
        assertThat(requests.get()).isZero();
    }

    @Test
    void aChatGptFailureSurvivesAFailingFinalStatusUpdate() {
        RuntimeException dbError = new IllegalArgumentException("db down");
        ChatGptCallException original = new ChatGptCallException("CHATGPT_UNAVAILABLE", "boom");
        Map<String, Object> body = Map.of("model", "m", "input", "x");
        assertThatThrownBy(() -> client.createTextOrThrow(sid, body, () -> { throw original; },
            status -> { throw dbError; }))
            .isSameAs(original)
            .satisfies(e -> assertThat(e.getSuppressed()).contains(dbError));
    }

    @Test
    void aFailingFinalStatusUpdateAloneStillSurfacesOnASuccessfulCall() {
        RuntimeException dbError = new IllegalArgumentException("db down");
        Map<String, Object> body = Map.of("model", "m", "input", "x");
        assertThatThrownBy(() -> client.createTextOrThrow(sid, body, () -> { }, status -> { throw dbError; }))
            .isSameAs(dbError);
    }
}
