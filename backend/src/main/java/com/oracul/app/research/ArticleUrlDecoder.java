package com.oracul.app.research;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** FR-54 step 3: Google's undocumented batchexecute call that turns a Google News article link into the publisher URL. */
@Component
public class ArticleUrlDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleUrlDecoder.class);
    private static final String USER_AGENT = "Mozilla/5.0 (compatible; ORACUL/1.0)";
    private static final int MAX_BYTES = 2_097_152;
    private static final Pattern URL = Pattern.compile("https?://[^\"\\\\\\s<>]+");
    private static final Pattern DIGITS = Pattern.compile("^\\d{1,18}$");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    /** The three values of the Google page element. */
    public record Attributes(String id, long ts, String sg) {
    }

    /** {@code url} is null on failure; {@code reason} is a fixed log word. */
    public record Result(String url, String reason) {
    }

    private final String decodeUrl;
    private final String googleBaseUrl;
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    ArticleUrlDecoder(@Value("${oracul.news.google.decode-url:}") String decodeUrl,
                      @Value("${oracul.news.google.base-url:https://news.google.com}") String googleBaseUrl) {
        String base = googleBaseUrl == null ? "" : googleBaseUrl.trim();
        this.decodeUrl = decodeUrl == null || decodeUrl.isBlank()
            ? (base.endsWith("/") ? base.substring(0, base.length() - 1) : base) + "/_/DotsSplashUi/data/batchexecute"
            : decodeUrl.trim();
        this.googleBaseUrl = base;
    }

    static Optional<Attributes> attributes(String googlePageHtml) {
        if (googlePageHtml == null || googlePageHtml.isEmpty()) {
            return Optional.empty();
        }
        for (Element e : Jsoup.parse(googlePageHtml).getAllElements()) {
            if (!e.hasAttr("data-n-a-id") || !e.hasAttr("data-n-a-ts") || !e.hasAttr("data-n-a-sg")) {
                continue;
            }
            String id = e.attr("data-n-a-id").trim();
            String ts = e.attr("data-n-a-ts").trim();
            String sg = e.attr("data-n-a-sg").trim();
            if (!id.isEmpty() && !sg.isEmpty() && DIGITS.matcher(ts).matches()) {
                return Optional.of(new Attributes(id, Long.parseLong(ts), sg));
            }
        }
        return Optional.empty();
    }

    static String fReq(Attributes a) {
        String inner = "[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],"
            + "\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0]," + quote(a.id()) + "," + a.ts() + "," + quote(a.sg()) + "]";
        return "[[[\"Fbv4je\"," + quote(inner) + ",null,\"generic\"]]]";
    }

    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }

    static Optional<String> publisherUrl(String answerBody, String googleBaseUrl) {
        if (answerBody == null) {
            return Optional.empty();
        }
        String candidate = structured(answerBody);
        if (candidate == null) {
            Matcher m = URL.matcher(answerBody);
            candidate = m.find() ? m.group() : null;
        }
        if (candidate == null) {
            return Optional.empty();
        }
        try {
            URI uri = URI.create(candidate);
            String scheme = uri.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https")) || uri.getHost() == null) {
                return Optional.empty();
            }
            if (isGoogleHost(uri, googleBaseUrl)) {
                return Optional.empty();
            }
            return Optional.of(candidate);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    private static String structured(String body) {
        try {
            int open = body.indexOf('[');
            if (open < 0) {
                return null;
            }
            JsonNode root = JSON.readTree(body.substring(open));
            if (!root.isArray()) {
                return null;
            }
            for (JsonNode entry : root) {
                if (entry.isArray() && entry.size() >= 3 && "wrb.fr".equals(entry.get(0).asString(null))
                    && "Fbv4je".equals(entry.get(1).asString(null)) && entry.get(2).isString()) {
                    JsonNode inner = JSON.readTree(entry.get(2).asString());
                    if (inner.isArray() && inner.size() >= 2 && "garturlres".equals(inner.get(0).asString(null))
                        && inner.get(1).isString()) {
                        return inner.get(1).asString();
                    }
                    return null;
                }
            }
        } catch (RuntimeException e) {
            return null;
        }
        return null;
    }

    /** google.com, gstatic.com, googleusercontent.com and their subdomains; or a Google article link of the base. */
    static boolean isGoogleHost(URI uri, String googleBaseUrl) {
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        for (String d : new String[] {"google.com", "gstatic.com", "googleusercontent.com"}) {
            if (host.equals(d) || host.endsWith("." + d)) {
                return true;
            }
        }
        String baseHost = hostOf(googleBaseUrl);
        String path = uri.getRawPath() == null ? "" : uri.getRawPath();
        return baseHost != null && baseHost.equals(host) && path.startsWith("/rss/articles/");
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static final java.util.concurrent.ScheduledExecutorService WATCHDOG =
        java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "decode-watchdog");
            t.setDaemon(true);
            return t;
        });

    public Result decode(Attributes a, Duration timeout) {
        long deadline = System.nanoTime() + (timeout.isNegative() || timeout.isZero() ? 1_000_000L : timeout.toNanos());
        String form = "f.req=" + URLEncoder.encode(fReq(a), StandardCharsets.UTF_8);
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(decodeUrl))
                .timeout(timeout.isNegative() || timeout.isZero() ? Duration.ofMillis(1) : timeout)
                .header("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                .header("User-Agent", USER_AGENT)
                .POST(HttpRequest.BodyPublishers.ofString(form, StandardCharsets.UTF_8)).build();
            HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            java.util.concurrent.atomic.AtomicBoolean timedOut = new java.util.concurrent.atomic.AtomicBoolean();
            InputStream stream = response.body();
            java.util.concurrent.ScheduledFuture<?> guard = WATCHDOG.schedule(() -> {
                timedOut.set(true);
                try {
                    stream.close();
                } catch (IOException ignored) {
                    // closing only unblocks the reader
                }
            }, Math.max(1L, deadline - System.nanoTime()), java.util.concurrent.TimeUnit.NANOSECONDS);
            try (InputStream in = stream) {
                int status = response.statusCode();
                if (status / 100 != 2) {
                    return new Result(null, "status=" + status);
                }
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int total = 0;
                int n;
                while (total < MAX_BYTES && (n = in.read(buf, 0, Math.min(buf.length, MAX_BYTES - total))) > 0) {
                    out.write(buf, 0, n);
                    total += n;
                }
                if (timedOut.get()) {
                    return new Result(null, "timeout");
                }
                String body = out.toString(StandardCharsets.UTF_8);
                Optional<String> url = publisherUrl(body, googleBaseUrl);
                if (url.isPresent()) {
                    return new Result(url.get(), null);
                }
                boolean anyUrl = structured(body) != null || URL.matcher(body).find();
                return new Result(null, anyUrl ? "google-host" : "no-url");
            } catch (IOException e) {
                if (timedOut.get()) {
                    return new Result(null, "timeout");
                }
                throw e;
            } finally {
                guard.cancel(false);
            }
        } catch (HttpTimeoutException e) {
            return new Result(null, "timeout");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(null, "error");
        } catch (IOException | RuntimeException e) {
            return new Result(null, e.getClass().getSimpleName().contains("Timeout") ? "timeout" : "error");
        }
    }
}
