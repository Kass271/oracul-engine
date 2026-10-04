package com.oracul.app.research;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads description and site name of an article page; the page is parsed, never executed (FR-13). */
@Component
public class ArticleMetadataFetcher {

    /** Extracted page metadata; either field may be null. */
    public record Metadata(String description, String siteName) {
    }

    private static final int MAX_REDIRECTS = 3;

    /** Page metadata together with the final URL and the number of redirects followed to reach it. */
    public record Fetched(Metadata metadata, String finalUrl, int redirects) {
    }
    private static final Pattern META = Pattern.compile("<meta\\s+([^>]*?)/?>", Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
    private static final Pattern ATTR = Pattern.compile(
        "([a-zA-Z:_-]+)\\s*=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final HttpClient http;
    private final ExecutorService inner = Executors.newVirtualThreadPerTaskExecutor();
    private final Duration timeout;
    private final int maxBytes;

    ArticleMetadataFetcher(@Value("${oracul.news.article-fetch-timeout:PT3S}") Duration timeout,
                           @Value("${oracul.news.article-max-bytes:524288}") int maxBytes) {
        this.timeout = timeout;
        this.maxBytes = maxBytes;
        this.http = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).connectTimeout(timeout).followRedirects(HttpClient.Redirect.NEVER).build();
    }

    @PreDestroy
    void shutdown() {
        inner.shutdownNow();
    }

    /** Empty on any failure (timeout, unreachable, non-2xx, not HTML, too many redirects). */
    public Optional<Metadata> fetch(String url) {
        return fetchDetailed(url, MAX_REDIRECTS).map(Fetched::metadata);
    }

    /** Like {@link #fetch} with a redirect limit; also reports the final URL and how many redirects were followed. */
    public Optional<Fetched> fetchDetailed(String url, int maxRedirects) {
        Future<Optional<Fetched>> future = inner.submit(() -> fetchBlocking(url, maxRedirects));
        try {
            return future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            future.cancel(true);
            return Optional.empty();
        }
    }

    private Optional<Fetched> fetchBlocking(String url, int maxRedirects) {
        try {
            URI target = URI.create(url);
            for (int hop = 0; hop <= maxRedirects; hop++) {
                HttpRequest request = HttpRequest.newBuilder(target).timeout(timeout)
                    .header("Accept", "text/html,application/xhtml+xml").GET().build();
                HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
                int status = response.statusCode();
                if (status / 100 == 3) {
                    response.body().close();
                    Optional<String> location = response.headers().firstValue("Location");
                    if (location.isEmpty()) {
                        return Optional.empty();
                    }
                    target = target.resolve(location.get());
                    continue;
                }
                try (InputStream in = response.body()) {
                    String type = response.headers().firstValue("Content-Type").orElse("").toLowerCase(Locale.ROOT);
                    if (status / 100 != 2 || !(type.startsWith("text/html") || type.startsWith("application/xhtml+xml"))) {
                        return Optional.empty();
                    }
                    byte[] bytes = in.readNBytes(maxBytes);
                    return Optional.of(new Fetched(parse(new String(bytes, charset(type))), target.toString(), hop));
                }
            }
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static Charset charset(String contentType) {
        Matcher m = CHARSET.matcher(contentType);
        if (m.find()) {
            try {
                return Charset.forName(m.group(1));
            } catch (Exception e) {
                // fall through
            }
        }
        return StandardCharsets.UTF_8;
    }

    static Metadata parse(String html) {
        String ogDescription = null;
        String description = null;
        String siteName = null;
        Matcher m = META.matcher(html);
        while (m.find()) {
            Map<String, String> attrs = new HashMap<>();
            Matcher a = ATTR.matcher(m.group(1));
            while (a.find()) {
                String value = a.group(2) != null ? a.group(2) : a.group(3) != null ? a.group(3) : a.group(4);
                attrs.putIfAbsent(a.group(1).toLowerCase(Locale.ROOT), value);
            }
            String key = attrs.getOrDefault("property", attrs.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
            String content = attrs.get("content");
            if (content == null) {
                continue;
            }
            if (key.equals("og:description") && ogDescription == null) {
                ogDescription = unescape(content);
            } else if (key.equals("og:site_name") && siteName == null) {
                siteName = unescape(content);
            } else if (attrs.getOrDefault("name", "").equalsIgnoreCase("description") && description == null) {
                description = unescape(content);
            }
        }
        String best = ogDescription != null && !ogDescription.isBlank() ? ogDescription : description;
        return new Metadata(best, siteName);
    }

    private static String unescape(String s) {
        return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<")
            .replace("&gt;", ">").replace("&amp;", "&");
    }
}
