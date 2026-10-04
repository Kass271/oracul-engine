package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

// @trace FR-36, FR-37
class ChatGptTokenClientTest {

    private HttpServer server;
    private volatile int status = 200;
    private volatile String body = "{}";

    private ChatGptTokenClient client() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", ex -> {
            byte[] b = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(status, b.length);
            ex.getResponseBody().write(b);
            ex.close();
        });
        server.start();
        String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/t";
        return new ChatGptTokenClient(ChatGptTestProps.of(url, url, url));
    }

    @AfterEach
    void stop() {
        Thread.interrupted();
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void tokenResponseToStringIsRedacted() {
        var r = new ChatGptTokenClient.TokenResponse("acc", "ref", "idt", 5, "s");
        assertThat(r.toString()).isEqualTo("[REDACTED]");
    }

    @Test
    void interruptedRevokeRestoresFlagAndReturnsNormally() throws Exception {
        ChatGptTokenClient c = client();
        Thread.currentThread().interrupt();
        c.revoke("tok", "cid");
        assertThat(Thread.interrupted()).isTrue();
    }

    @Test
    void interruptedTokenRequestReturnsFailedResult() throws Exception {
        ChatGptTokenClient c = client();
        Thread.currentThread().interrupt();
        var r = c.exchange(Map.of("grant_type", "x"));
        assertThat(Thread.interrupted()).isTrue();
        assertThat(r.token()).isNull();
        assertThat(r.invalidGrant()).isFalse();
    }

    @Test
    void invalidGrantDetectionAcrossBodies() throws Exception {
        ChatGptTokenClient c = client();
        status = 400;
        body = "{\"error\":\"invalid_grant\"}";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isTrue();
        body = "{\"error\":{\"code\":\"invalid_grant\"}}";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isTrue();
        body = "{\"error\":{\"code\":\"other\"}}";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isFalse();
        body = "{\"error\":\"other\"}";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isFalse();
        body = "not json";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isFalse();
        body = "[1,2]";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isFalse();
        status = 500;
        body = "{\"error\":\"invalid_grant\"}";
        assertThat(c.exchange(Map.of("a", "b")).invalidGrant()).isFalse();
    }

    @Test
    void successBodiesAreParsedOrRejected() throws Exception {
        ChatGptTokenClient c = client();
        body = "{\"access_token\":\"a\",\"refresh_token\":\"r\",\"expires_in\":7,\"scope\":\"s\"}";
        var ok = c.post(Map.of("a", "b"));
        assertThat(ok.accessToken()).isEqualTo("a");
        assertThat(ok.expiresInSeconds()).isEqualTo(7);
        body = "{\"access_token\":\"a\"}";
        assertThat(c.post(Map.of("a", "b")).expiresInSeconds()).isEqualTo(3600);
        body = "{\"foo\":1}";
        assertThat(c.post(Map.of("a", "b"))).isNull();
        body = "[]";
        assertThat(c.post(Map.of("a", "b"))).isNull();
        body = "garbage";
        assertThat(c.post(Map.of("a", "b"))).isNull();
    }
}
