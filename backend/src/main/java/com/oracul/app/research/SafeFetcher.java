package com.oracul.app.research;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Fetches article pages without letting a link reach internal addresses (FR-56): http/https only, every hop checked,
 * redirects followed by hand, connection only to a checked address, body and time limited.
 */
@Component
public class SafeFetcher {

    private static final Logger LOG = LoggerFactory.getLogger(SafeFetcher.class);
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");

    /** Resolves a host name to all of its addresses. */
    public interface HostResolver {
        List<InetAddress> resolve(String host) throws UnknownHostException;
    }

    /** How a fetch ended. */
    public enum Outcome { OK, REFUSED_SCHEME, REFUSED_ADDRESS, TOO_MANY_REDIRECTS, FAILED }

    /** Answer of one fetch; see the slice spec for the meaning of each field. */
    public record Result(Outcome outcome, int status, String contentType, byte[] body, boolean truncated, URI finalUri,
                         int redirects) {
    }

    private final HostResolver resolver;
    private final Set<String> allowedPrivateHosts;
    private final Duration timeout;
    private final int maxBytes;
    private final int maxRedirects;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();

    @Autowired
    SafeFetcher(@Value("${oracul.news.fetch.allowed-private-hosts:}") String allowedPrivateHosts,
                @Value("${oracul.news.article-max-bytes:2097152}") int maxBytes,
                @Value("${oracul.news.article-max-redirects:5}") int maxRedirects,
                @Value("${oracul.news.article-fetch-timeout:PT3S}") Duration timeout) {
        this(InetAddress_::all, parse(allowedPrivateHosts), timeout, maxBytes, maxRedirects);
    }

    SafeFetcher(HostResolver resolver, Set<String> allowedPrivateHosts, Duration timeout, int maxBytes, int maxRedirects) {
        this.resolver = resolver;
        Set<String> lower = new HashSet<>();
        for (String h : allowedPrivateHosts) {
            lower.add(h.trim().toLowerCase(Locale.ROOT));
        }
        this.allowedPrivateHosts = Set.copyOf(lower);
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.maxRedirects = maxRedirects;
    }

    /** Production resolver. */
    private static final class InetAddress_ {
        static List<InetAddress> all(String host) throws UnknownHostException {
            return List.of(InetAddress.getAllByName(host));
        }
    }

    private static Set<String> parse(String list) {
        Set<String> out = new HashSet<>();
        if (list != null) {
            for (String s : list.split(",")) {
                if (!s.isBlank()) {
                    out.add(s.trim().toLowerCase(Locale.ROOT));
                }
            }
        }
        return out;
    }

    @PreDestroy
    void shutdown() {
        workers.shutdownNow();
    }

    public Result fetch(URI uri) {
        return fetch(uri, timeout);
    }

    public Result fetch(URI uri, Duration timeout) {
        long deadline = System.nanoTime() + Math.max(0, timeout.toNanos());
        AtomicReference<Socket> current = new AtomicReference<>();
        AtomicBoolean cancelled = new AtomicBoolean();
        Future<Result> future = workers.submit(() -> run(uri, deadline, current, cancelled));
        try {
            return future.get(Math.max(1, TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            abort(future, current, cancelled);
            Thread.currentThread().interrupt();
            return failed(uri);
        } catch (TimeoutException e) {
            abort(future, current, cancelled);
            return failed(uri);
        } catch (Exception e) {
            abort(future, current, cancelled);
            return failed(uri);
        }
    }

    private static void abort(Future<?> future, AtomicReference<Socket> current, AtomicBoolean cancelled) {
        cancelled.set(true);
        future.cancel(true);
        closeQuietly(current.get());
    }

    private static void closeQuietly(Socket s) {
        if (s != null) {
            try {
                s.close();
            } catch (IOException e) {
                // nothing left to release
            }
        }
    }

    private static Result failed(URI uri) {
        return new Result(Outcome.FAILED, 0, null, new byte[0], false, uri, 0);
    }

    private static Result refused(Outcome outcome, URI uri, int redirects) {
        return new Result(outcome, 0, null, new byte[0], false, uri, redirects);
    }

    private Result run(URI start, long deadline, AtomicReference<Socket> current, AtomicBoolean cancelled) {
        URI target = start;
        int redirects = 0;
        try {
            while (true) {
                String scheme = target.getScheme();
                if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
                    LOG.warn("article fetch refused: scheme scheme={}", scheme == null ? "" : scheme.toLowerCase(Locale.ROOT));
                    return refused(Outcome.REFUSED_SCHEME, target, redirects);
                }
                String host = hostOf(target);
                if (host == null || host.isEmpty()) {
                    return failed(target);
                }
                List<InetAddress> addresses = resolve(host);
                if (addresses == null) {
                    return failed(target);
                }
                if (!allowedPrivateHosts.contains(host.toLowerCase(Locale.ROOT))) {
                    for (InetAddress a : addresses) {
                        if (isBlocked(a)) {
                            LOG.warn("article fetch refused: blocked-address host={}", host);
                            return refused(Outcome.REFUSED_ADDRESS, target, redirects);
                        }
                    }
                }
                Hop hop = hop(target, host, addresses, deadline, current, cancelled);
                if (hop.redirect != null) {
                    if (redirects >= maxRedirects) {
                        return refused(Outcome.TOO_MANY_REDIRECTS, target, redirects);
                    }
                    redirects++;
                    target = target.resolve(hop.redirect);
                    continue;
                }
                return new Result(Outcome.OK, hop.status, hop.contentType, hop.body, hop.truncated, target, redirects);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return failed(target);
        } catch (Exception e) {
            return failed(target);
        }
    }

    private List<InetAddress> resolve(String host) throws UnknownHostException {
        if (host.indexOf(':') >= 0 || IPV4.matcher(host).matches()) {
            return List.of(InetAddress.getByName(host));
        }
        List<InetAddress> found = resolver.resolve(host);
        return found == null || found.isEmpty() ? null : found;
    }

    private static String hostOf(URI uri) {
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        return host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
    }

    /** True for loopback, any-local, private, link-local and unique-local addresses, also in IPv4-mapped form. */
    static boolean isBlocked(InetAddress address) {
        InetAddress a = address;
        byte[] raw = a.getAddress();
        if (a instanceof Inet6Address && raw.length == 16) {
            boolean mapped = true;
            for (int i = 0; i < 10; i++) {
                mapped &= raw[i] == 0;
            }
            mapped &= raw[10] == (byte) 0xff && raw[11] == (byte) 0xff;
            if (mapped) {
                byte[] v4 = {raw[12], raw[13], raw[14], raw[15]};
                try {
                    a = InetAddress.getByAddress(v4);
                } catch (UnknownHostException e) {
                    return true;
                }
                raw = v4;
            } else if ((raw[0] & 0xfe) == 0xfc) {
                return true;
            }
        }
        if (a.isLoopbackAddress() || a.isAnyLocalAddress() || a.isLinkLocalAddress() || a.isSiteLocalAddress()) {
            return true;
        }
        if (a instanceof Inet4Address) {
            int b0 = raw[0] & 0xff;
            int b1 = raw[1] & 0xff;
            return b0 == 10 || b0 == 127 || b0 == 0 || (b0 == 172 && b1 >= 16 && b1 <= 31) || (b0 == 192 && b1 == 168)
                || (b0 == 169 && b1 == 254);
        }
        return false;
    }

    private record Hop(int status, String contentType, byte[] body, boolean truncated, String redirect) {
    }

    private Hop hop(URI uri, String host, List<InetAddress> addresses, long deadline, AtomicReference<Socket> current,
                    AtomicBoolean cancelled) throws IOException, InterruptedException {
        boolean tls = "https".equalsIgnoreCase(uri.getScheme());
        int port = uri.getPort() > 0 ? uri.getPort() : tls ? 443 : 80;
        Socket socket = new Socket();
        current.set(socket);
        if (cancelled.get()) {
            socket.close();
            throw new InterruptedException();
        }
        try {
            IOException last = null;
            boolean connected = false;
            for (InetAddress a : addresses) {
                try {
                    socket.connect(new InetSocketAddress(a, port), remainingMillis(deadline));
                    connected = true;
                    break;
                } catch (IOException e) {
                    last = e;
                    if (socket.isClosed()) {
                        break;
                    }
                }
            }
            if (!connected) {
                throw last != null ? last : new IOException("no address");
            }
            socket.setSoTimeout(remainingMillis(deadline));
            Socket wire = socket;
            if (tls) {
                SSLSocket ssl = (SSLSocket) ((SSLSocketFactory) SSLSocketFactory.getDefault()).createSocket(socket, host, port, true);
                SSLParameters params = ssl.getSSLParameters();
                params.setEndpointIdentificationAlgorithm("HTTPS");
                if (!(host.indexOf(':') >= 0 || IPV4.matcher(host).matches())) {
                    params.setServerNames(List.of(new javax.net.ssl.SNIHostName(host)));
                }
                ssl.setSSLParameters(params);
                ssl.startHandshake();
                wire = ssl;
            }
            String rawPath = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
            String path = rawPath + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            String hostHeader = uri.getHost() + (uri.getPort() > 0 ? ":" + uri.getPort() : "");
            String request = "GET " + path + " HTTP/1.1\r\nHost: " + hostHeader
                + "\r\nAccept: text/html,application/xhtml+xml\r\nAccept-Encoding: identity\r\nConnection: close\r\n\r\n";
            OutputStream out = wire.getOutputStream();
            out.write(request.getBytes(StandardCharsets.UTF_8));
            out.flush();
            InputStream in = new java.io.BufferedInputStream(wire.getInputStream(), 16 * 1024);
            return readAnswer(in, deadline, socket);
        } finally {
            closeQuietly(socket);
        }
    }

    private static int remainingMillis(long deadline) throws IOException {
        long ms = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
        if (ms <= 0) {
            throw new IOException("deadline");
        }
        return (int) Math.min(Integer.MAX_VALUE, ms);
    }

    private Hop readAnswer(InputStream in, long deadline, Socket socket) throws IOException {
        String statusLine = readLine(in);
        String[] parts = statusLine.split(" ");
        if (parts.length < 2 || !parts[0].startsWith("HTTP/")) {
            throw new IOException("bad status line");
        }
        int status;
        try {
            status = Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException e) {
            throw new IOException("bad status code");
        }
        String contentType = null;
        String location = null;
        long contentLength = -1;
        boolean chunked = false;
        while (true) {
            String line = readLine(in);
            if (line.isEmpty()) {
                break;
            }
            int colon = line.indexOf(':');
            if (colon <= 0) {
                continue;
            }
            String name = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            String value = line.substring(colon + 1).trim();
            switch (name) {
                case "content-type" -> contentType = value;
                case "location" -> location = value;
                case "content-length" -> {
                    try {
                        contentLength = Long.parseLong(value);
                    } catch (NumberFormatException e) {
                        throw new IOException("bad content-length");
                    }
                }
                case "transfer-encoding" -> chunked = value.toLowerCase(Locale.ROOT).contains("chunked");
                default -> {
                }
            }
        }
        boolean redirect = status == 301 || status == 302 || status == 303 || status == 307 || status == 308;
        if (redirect) {
            if (location == null || location.isBlank()) {
                throw new IOException("redirect without location");
            }
            return new Hop(status, contentType, null, false, location);
        }
        if (status == 204 || status == 304 || status / 100 == 1) {
            return new Hop(status, contentType, new byte[0], false, null);
        }
        socket.setSoTimeout(remainingMillis(deadline));
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        boolean truncated;
        if (chunked) {
            ChunkedStream chunks = new ChunkedStream(in);
            truncated = copy(chunks, body, -1, deadline, socket);
        } else {
            truncated = copy(in, body, contentLength, deadline, socket);
        }
        return new Hop(status, contentType, body.toByteArray(), truncated, null);
    }

    /** Reads at most maxBytes; truncated when more data exists (known by length, else by one more byte). */
    private boolean copy(InputStream in, ByteArrayOutputStream out, long length, long deadline, Socket socket) throws IOException {
        long limit = length >= 0 ? Math.min(length, maxBytes) : maxBytes;
        byte[] buf = new byte[16 * 1024];
        long total = 0;
        while (total < limit) {
            socket.setSoTimeout(remainingMillis(deadline));
            int n = in.read(buf, 0, (int) Math.min(buf.length, limit - total));
            if (n < 0) {
                if (length >= 0) {
                    throw new EOFException("body shorter than content-length");
                }
                return false;
            }
            out.write(buf, 0, n);
            total += n;
        }
        if (length >= 0) {
            return length > maxBytes;
        }
        socket.setSoTimeout(remainingMillis(deadline));
        return in.read() >= 0;
    }

    private static String readLine(InputStream in) throws IOException {
        ByteArrayOutputStream line = new ByteArrayOutputStream();
        while (true) {
            int c = in.read();
            if (c < 0) {
                throw new EOFException("connection closed in head");
            }
            if (c == '\n') {
                break;
            }
            if (c != '\r') {
                line.write(c);
            }
            if (line.size() > 64 * 1024) {
                throw new IOException("head line too long");
            }
        }
        return line.toString(StandardCharsets.ISO_8859_1);
    }

    /** Decodes a chunked body as a stream. */
    private static final class ChunkedStream extends InputStream {
        private final InputStream in;
        private long left;
        private boolean done;

        ChunkedStream(InputStream in) {
            this.in = in;
        }

        @Override
        public int read() throws IOException {
            byte[] one = new byte[1];
            int n = read(one, 0, 1);
            return n < 0 ? -1 : one[0] & 0xff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (done) {
                return -1;
            }
            if (left == 0) {
                String size = readLine(in).split(";")[0].trim();
                try {
                    left = Long.parseLong(size, 16);
                } catch (NumberFormatException e) {
                    throw new IOException("bad chunk size");
                }
                if (left == 0) {
                    done = true;
                    return -1;
                }
            }
            int n = in.read(b, off, (int) Math.min(len, left));
            if (n < 0) {
                throw new EOFException("truncated chunk");
            }
            left -= n;
            if (left == 0) {
                readLine(in);
            }
            return n;
        }
    }
}
