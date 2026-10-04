package com.oracul.app.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

// @trace FR-39
// @trace FR-44
class RawHttpGetTest {

    /** Serves one canned answer per accepted connection; counts connections. */
    private static final class Server implements AutoCloseable {
        final ServerSocket socket;
        final AtomicInteger connections = new AtomicInteger();
        final AtomicReference<String> request = new AtomicReference<>();

        Server(byte[] answer, boolean hold) throws IOException {
            socket = new ServerSocket(0);
            Thread t = new Thread(() -> {
                try {
                    while (!socket.isClosed()) {
                        Socket c = socket.accept();
                        connections.incrementAndGet();
                        byte[] buf = new byte[4096];
                        int n = c.getInputStream().read(buf);
                        request.set(new String(buf, 0, Math.max(n, 0), StandardCharsets.ISO_8859_1));
                        if (hold) {
                            Thread.sleep(3000);
                        } else if (answer != null) {
                            c.getOutputStream().write(answer);
                            c.getOutputStream().flush();
                        }
                        c.close();
                    }
                } catch (Exception e) {
                    // server closed
                }
            });
            t.setDaemon(true);
            t.start();
        }

        URI uri(String pathAndQuery) {
            return URI.create("http://127.0.0.1:" + socket.getLocalPort() + pathAndQuery);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    private static byte[] bytes(String s) {
        return s.getBytes(StandardCharsets.ISO_8859_1);
    }

    @Test
    void getsPlainBodyAndSendsHeadersOnce() throws Exception {
        try (Server s = new Server(bytes("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nhi"), false)) {
            RawHttpGet.Response r = RawHttpGet.get(s.uri("/a/b?x=1&y=2"), Map.of("X-Test", "v"), Duration.ofSeconds(5));
            assertThat(r.status()).isEqualTo(200);
            assertThat(r.body()).isEqualTo("hi");
            assertThat(s.request.get()).startsWith("GET /a/b?x=1&y=2 HTTP/1.1\r\n")
                .contains("X-Test: v\r\n").contains("Accept-Encoding: identity").contains("Host: 127.0.0.1:");
            assertThat(s.connections.get()).isEqualTo(1);
        }
    }

    @Test
    void emptyPathBecomesSlashAndStatusIsPassedThrough() throws Exception {
        try (Server s = new Server(bytes("HTTP/1.1 429 Too Many\r\n\r\n"), false)) {
            RawHttpGet.Response r = RawHttpGet.get(s.uri(""), Map.of(), Duration.ofSeconds(5));
            assertThat(r.status()).isEqualTo(429);
            assertThat(r.body()).isEmpty();
            assertThat(s.request.get()).startsWith("GET / HTTP/1.1");
        }
    }

    @Test
    void decodesChunkedBody() throws Exception {
        String answer = "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n3;ext=1\r\nabc\r\n2\r\nde\r\n0\r\n\r\n";
        try (Server s = new Server(bytes(answer), false)) {
            assertThat(RawHttpGet.get(s.uri("/"), Map.of(), Duration.ofSeconds(5)).body()).isEqualTo("abcde");
        }
    }

    @Test
    void malformedAnswersFailWithoutRetry() throws Exception {
        String[] bad = {
            "garbage without head",
            "FTP/1.1 200 OK\r\n\r\n",
            "HTTP/1.1\r\n\r\n",
            "HTTP/1.1 abc OK\r\n\r\n",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nnocrlf",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\nzz\r\nabc\r\n",
            "HTTP/1.1 200 OK\r\nTransfer-Encoding: chunked\r\n\r\n10\r\nabc\r\n"
        };
        for (String a : bad) {
            try (Server s = new Server(bytes(a), false)) {
                assertThatThrownBy(() -> RawHttpGet.get(s.uri("/"), Map.of(), Duration.ofSeconds(5)))
                    .isInstanceOf(IOException.class);
                assertThat(s.connections.get()).isEqualTo(1);
            }
        }
    }

    @Test
    void emptyAnswerIsAnIoErrorAndTimeoutIsBounded() throws Exception {
        try (Server s = new Server(null, false)) {
            assertThatThrownBy(() -> RawHttpGet.get(s.uri("/"), Map.of(), Duration.ofSeconds(5)))
                .isInstanceOf(IOException.class);
        }
        try (Server s = new Server(null, true)) {
            long start = System.nanoTime();
            assertThatThrownBy(() -> RawHttpGet.get(s.uri("/"), Map.of(), Duration.ofMillis(300)))
                .isInstanceOf(IOException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(2));
            assertThat(s.connections.get()).isEqualTo(1);
        }
    }
}
