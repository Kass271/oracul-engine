package com.oracul.app.common;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * One GET over a plain socket, exactly one request per call. The JDK HTTP clients silently repeat a GET whose
 * connection was dropped without an answer, which breaks "no retry except ..." rules (FR-39 row 4, FR-44). No redirects,
 * no compression, the whole exchange bounded by {@code timeout}.
 */
public final class RawHttpGet {

    /** Status and decoded body of the answer. */
    public record Response(int status, String body) {
    }

    private static final int MAX_BODY = 16 * 1024 * 1024;
    private static final ScheduledExecutorService WATCHDOG = newWatchdog();

    private static ScheduledExecutorService newWatchdog() {
        ScheduledThreadPoolExecutor e = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "raw-get-watchdog");
            t.setDaemon(true);
            return t;
        });
        e.setRemoveOnCancelPolicy(true);
        return e;
    }

    private RawHttpGet() {
    }

    public static Response get(URI uri, Map<String, String> headers, Duration timeout) throws IOException {
        return get(uri, headers, timeout, (SSLSocketFactory) SSLSocketFactory.getDefault());
    }

    /** Test seam: same as the public overload but with an explicit TLS trust source (package-private). */
    static Response get(URI uri, Map<String, String> headers, Duration timeout, SSLSocketFactory tlsFactory)
        throws IOException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Socket socket = new Socket();
        ScheduledFuture<?> watchdog = WATCHDOG.schedule(() -> {
            try {
                socket.close();
            } catch (IOException e) {
                // closing only unblocks the reader
            }
        }, Math.max(0, timeout.toNanos()), TimeUnit.NANOSECONDS);
        try {
            boolean tls = "https".equalsIgnoreCase(uri.getScheme());
            int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
            int millis = (int) Math.min(Integer.MAX_VALUE, Math.max(1, timeout.toMillis()));
            socket.connect(new InetSocketAddress(uri.getHost(), port), millis);
            socket.setSoTimeout(millis);
            Socket wire = socket;
            if (tls) {
                SSLSocket ssl = (SSLSocket) tlsFactory.createSocket(socket, uri.getHost(), port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                if (!isIpLiteral(uri.getHost())) {
                    params.setServerNames(java.util.List.of(new javax.net.ssl.SNIHostName(uri.getHost())));
                }
                ssl.setSSLParameters(params);
                // verifies chain and hostname; nothing is written before this succeeds
                ssl.startHandshake();
                wire = ssl;
            }
            String path = (uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath())
                + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            StringBuilder request = new StringBuilder("GET ").append(path).append(" HTTP/1.1\r\nHost: ")
                .append(uri.getHost()).append(uri.getPort() > 0 ? ":" + uri.getPort() : "").append("\r\n");
            headers.forEach((k, v) -> request.append(k).append(": ").append(v).append("\r\n"));
            request.append("Accept-Encoding: identity\r\nConnection: close\r\n\r\n");
            OutputStream out = wire.getOutputStream();
            out.write(request.toString().getBytes(StandardCharsets.UTF_8));
            out.flush();
            byte[] raw = wire.getInputStream().readNBytes(MAX_BODY);
            if (System.nanoTime() >= deadline) {
                throw new IOException("deadline");
            }
            return parse(raw);
        } finally {
            watchdog.cancel(false);
            try {
                socket.close();
            } catch (IOException e) {
                // nothing left to release
            }
        }
    }

    private static boolean isIpLiteral(String host) {
        return host.indexOf(':') >= 0 || host.matches("[0-9.]+");
    }

    private static Response parse(byte[] raw) throws IOException {
        int split = indexOf(raw, 0, "\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
        if (split < 0) {
            throw new IOException("no response head");
        }
        String[] lines = new String(raw, 0, split, StandardCharsets.ISO_8859_1).split("\r\n");
        String[] status = lines[0].split(" ");
        if (status.length < 2 || !status[0].startsWith("HTTP/")) {
            throw new IOException("bad status line");
        }
        int code;
        try {
            code = Integer.parseInt(status[1]);
        } catch (NumberFormatException e) {
            throw new IOException("bad status code");
        }
        boolean chunked = false;
        for (int i = 1; i < lines.length; i++) {
            String l = lines[i].toLowerCase(Locale.ROOT);
            if (l.startsWith("transfer-encoding:") && l.contains("chunked")) {
                chunked = true;
            }
        }
        byte[] body = Arrays.copyOfRange(raw, split + 4, raw.length);
        return new Response(code, new String(chunked ? dechunk(body) : body, StandardCharsets.UTF_8));
    }

    private static int indexOf(byte[] data, int from, byte[] pattern) {
        outer:
        for (int i = from; i + pattern.length <= data.length; i++) {
            for (int j = 0; j < pattern.length; j++) {
                if (data[i + j] != pattern[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }

    private static byte[] dechunk(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] crlf = "\r\n".getBytes(StandardCharsets.US_ASCII);
        int pos = 0;
        while (pos < data.length) {
            int eol = indexOf(data, pos, crlf);
            if (eol < 0) {
                throw new IOException("bad chunk");
            }
            int n;
            try {
                n = Integer.parseInt(new String(data, pos, eol - pos, StandardCharsets.US_ASCII).split(";")[0].trim(), 16);
            } catch (NumberFormatException e) {
                throw new IOException("bad chunk size");
            }
            pos = eol + 2;
            if (n == 0) {
                break;
            }
            if (pos + n > data.length) {
                throw new IOException("truncated chunk");
            }
            out.write(data, pos, n);
            pos += n + 2;
        }
        return out.toByteArray();
    }
}
