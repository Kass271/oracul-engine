package com.oracul.app.common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.util.List;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

// @trace FR-38
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

    // ---- TLS: chain and hostname are verified before any application byte (the Authorization header) is sent ----

    private static final char[] PW = "changeit".toCharArray();

    private static KeyStore generate(Path dir, String name, String san) throws Exception {
        Path file = dir.resolve(name + ".p12");
        Process p = new ProcessBuilder(Path.of(System.getProperty("java.home"), "bin", "keytool").toString(),
            "-genkeypair", "-alias", name, "-keyalg", "RSA", "-keysize", "2048", "-validity", "2",
            "-dname", "CN=" + name, "-ext", "san=" + san, "-storetype", "PKCS12",
            "-keystore", file.toString(), "-storepass", "changeit", "-keypass", "changeit")
            .redirectErrorStream(true).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(p.waitFor()).as(out).isZero();
        KeyStore ks = KeyStore.getInstance("PKCS12");
        try (InputStream in = Files.newInputStream(file)) {
            ks.load(in, PW);
        }
        return ks;
    }

    private static SSLContext serverContext(KeyStore ks) throws Exception {
        KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        kmf.init(ks, PW);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(kmf.getKeyManagers(), null, null);
        return ctx;
    }

    /** Trusts exactly the certificate of {@code ks} (self-signed, so it is its own CA). */
    private static SSLSocketFactory trusting(KeyStore ks) throws Exception {
        KeyStore trust = KeyStore.getInstance("PKCS12");
        trust.load(null, null);
        trust.setCertificateEntry("ca", ks.getCertificateChain(ks.aliases().nextElement())[0]);
        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(trust);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, tmf.getTrustManagers(), null);
        return ctx.getSocketFactory();
    }

    private static final class TlsServer implements AutoCloseable {
        final SSLServerSocket socket;
        final AtomicInteger appBytes = new AtomicInteger();
        final AtomicReference<String> request = new AtomicReference<>();

        TlsServer(KeyStore ks) throws Exception {
            socket = (SSLServerSocket) serverContext(ks).getServerSocketFactory().createServerSocket(0, 5,
                java.net.InetAddress.getByName("127.0.0.1"));
            Thread t = new Thread(() -> {
                while (!socket.isClosed()) {
                    try (Socket c = socket.accept()) {
                        byte[] buf = new byte[4096];
                        int n = c.getInputStream().read(buf);
                        if (n > 0) {
                            appBytes.addAndGet(n);
                            request.set(new String(buf, 0, n, StandardCharsets.ISO_8859_1));
                            c.getOutputStream().write(bytes("HTTP/1.1 200 OK\r\nContent-Length: 2\r\n\r\nok"));
                            c.getOutputStream().flush();
                        }
                    } catch (Exception e) {
                        // failed handshake or closed server
                    }
                }
            });
            t.setDaemon(true);
            t.start();
        }

        URI uri(String host) {
            return URI.create("https://" + host + ":" + socket.getLocalPort() + "/v1/x");
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }

    @Test
    void tlsWithTrustedCertificateMatchingTheHostSucceedsByDnsAndByIp(@org.junit.jupiter.api.io.TempDir Path dir)
        throws Exception {
        KeyStore ks = generate(dir, "good", "dns:localhost,ip:127.0.0.1");
        try (TlsServer s = new TlsServer(ks)) {
            for (String host : List.of("localhost", "127.0.0.1")) {
                RawHttpGet.Response r = RawHttpGet.get(s.uri(host), Map.of("Authorization", "Bearer secret"),
                    Duration.ofSeconds(10), trusting(ks));
                assertThat(r.status()).isEqualTo(200);
                assertThat(r.body()).isEqualTo("ok");
                assertThat(s.request.get()).contains("Authorization: Bearer secret");
            }
        }
    }

    @Test
    void tlsWithTrustedCertificateForAnotherHostFailsAndSendsNoApplicationBytes(@org.junit.jupiter.api.io.TempDir Path dir)
        throws Exception {
        KeyStore ks = generate(dir, "wrong", "dns:other.example.test");
        try (TlsServer s = new TlsServer(ks)) {
            for (String host : List.of("localhost", "127.0.0.1")) {
                assertThatThrownBy(() -> RawHttpGet.get(s.uri(host), Map.of("Authorization", "Bearer secret"),
                    Duration.ofSeconds(10), trusting(ks))).isInstanceOf(IOException.class);
            }
            Thread.sleep(300); // let the server thread finish reading the aborted connections
            assertThat(s.appBytes.get()).isZero();
            assertThat(s.request.get()).isNull();
        }
    }

    @Test
    void tlsWithUntrustedCertificateFailsAndSendsNoApplicationBytes(@org.junit.jupiter.api.io.TempDir Path dir)
        throws Exception {
        KeyStore ks = generate(dir, "untrusted", "dns:localhost,ip:127.0.0.1");
        try (TlsServer s = new TlsServer(ks)) {
            assertThatThrownBy(() -> RawHttpGet.get(s.uri("localhost"), Map.of("Authorization", "Bearer secret"),
                Duration.ofSeconds(10), (SSLSocketFactory) SSLSocketFactory.getDefault()))
                .isInstanceOf(IOException.class);
            assertThatThrownBy(() -> RawHttpGet.get(s.uri("localhost"), Map.of("Authorization", "Bearer secret"),
                Duration.ofSeconds(10), trusting(generate(dir, "other-ca", "dns:localhost"))))
                .isInstanceOf(IOException.class);
            Thread.sleep(300);
            assertThat(s.appBytes.get()).isZero();
        }
    }
}
