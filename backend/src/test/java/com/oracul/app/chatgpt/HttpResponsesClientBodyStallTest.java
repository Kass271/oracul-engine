package com.oracul.app.chatgpt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.oracul.app.chatgpt.ChatGptCredentialStore.SessionCredentials;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** R4: oracul.openai.timeout must bound the whole exchange, including a stalled response body. */
// @trace FR-12
class HttpResponsesClientBodyStallTest {

    @Test
    void serverThatSendsHeadersThenStallsTheBodyIsGivenUpAfterTheTimeout() throws Exception {
        UUID sid = UUID.randomUUID();
        ChatGptAuthService auth = mock(ChatGptAuthService.class);
        when(auth.requireUsableCredentials(any())).thenReturn(new SessionCredentials(sid, "client", new Secret("tok"),
            new Secret("ref"), new Secret("id"), Instant.now().plusSeconds(3600), Set.of()));
        try (ServerSocket server = new ServerSocket(0, 5, InetAddress.getLoopbackAddress())) {
            Thread t = new Thread(() -> {
                try (Socket s = server.accept()) {
                    s.getInputStream().readNBytes(1); // request arrives
                    OutputStream out = s.getOutputStream();
                    out.write("HTTP/1.1 200 OK\r\nContent-Type: application/json\r\nContent-Length: 100000\r\n\r\n{\"status\":"
                        .getBytes());
                    out.flush();
                    Thread.sleep(8000);
                } catch (Exception ignored) {
                    // client gave up
                }
            });
            t.setDaemon(true);
            t.start();
            HttpResponsesClient client = new HttpResponsesClient(auth,
                "http://127.0.0.1:" + server.getLocalPort() + "/v1", "m", Duration.ofMillis(500));
            long start = System.nanoTime();
            Optional<String> result = java.util.concurrent.CompletableFuture
                .supplyAsync(() -> client.createText(sid, Map.of("model", "m")))
                .get(6, java.util.concurrent.TimeUnit.SECONDS);
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertThat(result).isEmpty();
            assertThat(ms).as("returns within roughly the timeout, not after the stall").isLessThan(3000);
        }
    }
}
