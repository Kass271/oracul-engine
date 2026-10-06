package com.oracul.app.research;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Reads description and site name of an article page; the page is parsed, never executed (FR-13). */
@Component
public class ArticleMetadataFetcher {

    /** Extracted page metadata; either field may be null. */
    public record Metadata(String description, String siteName) {
    }

    /** Page metadata together with the final URL and the number of redirects followed to reach it. */
    public record Fetched(Metadata metadata, String finalUrl, int redirects) {
    }
    private static final long WAIT_LIMIT_SECONDS = 10;
    private static final int MAX_TAG_LENGTH = 4096;
    private static final Pattern ATTR = Pattern.compile(
        "(?<![a-zA-Z:_-])([a-zA-Z:_-]++)\\s*+=\\s*(?:\"([^\"]*)\"|'([^']*)'|([^\\s>]+))");
    private static final Pattern CHARSET = Pattern.compile("charset=([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private final SafeFetcher fetcher;
    private final ExecutorService inner = Executors.newVirtualThreadPerTaskExecutor();
    private final int maxRedirects;

    @Autowired
    ArticleMetadataFetcher(SafeFetcher fetcher,
                           @Value("${oracul.news.article-max-redirects:5}") int maxRedirects) {
        this.fetcher = fetcher;
        this.maxRedirects = maxRedirects;
    }

    /** Test constructor: redirect limit of the spec default. */
    ArticleMetadataFetcher(SafeFetcher fetcher) {
        this(fetcher, 5);
    }

    @PreDestroy
    void shutdown() {
        inner.shutdownNow();
    }

    /** Empty on any failure (timeout, unreachable, refused, non-2xx, not HTML, too many redirects). */
    public Optional<Metadata> fetch(String url) {
        return fetchDetailed(url, maxRedirects).map(Fetched::metadata);
    }

    /** Like {@link #fetch} with a redirect limit; also reports the final URL and how many redirects were followed. */
    public Optional<Fetched> fetchDetailed(String url, int redirectLimit) {
        Future<Optional<Fetched>> future = inner.submit(() -> fetchBlocking(url, Math.min(redirectLimit, maxRedirects)));
        try {
            return future.get(WAIT_LIMIT_SECONDS, java.util.concurrent.TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            future.cancel(true);
            return Optional.empty();
        }
    }

    private Optional<Fetched> fetchBlocking(String url, int limit) {
        try {
            SafeFetcher.Result r = fetcher.fetch(URI.create(url));
            if (r.outcome() != SafeFetcher.Outcome.OK || r.status() / 100 != 2 || r.redirects() > limit) {
                return Optional.empty();
            }
            String type = r.contentType() == null ? "" : r.contentType().toLowerCase(Locale.ROOT);
            if (!(type.startsWith("text/html") || type.startsWith("application/xhtml+xml"))) {
                return Optional.empty();
            }
            return Optional.of(new Fetched(parse(new String(r.body(), charset(type))), r.finalUri().toString(), r.redirects()));
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
        int pos = 0;
        while (true) {
            int lt = html.indexOf('<', pos);
            if (lt < 0) {
                break;
            }
            pos = lt + 1;
            if (!html.regionMatches(true, lt, "<meta", 0, 5) || lt + 5 >= html.length()
                || !Character.isWhitespace(html.charAt(lt + 5))) {
                continue;
            }
            int gt = html.indexOf('>', lt + 5);
            if (gt < 0) {
                break;
            }
            pos = gt + 1;
            if (gt - lt > MAX_TAG_LENGTH) {
                continue;
            }
            String body = html.substring(lt + 5, gt);
            if (body.endsWith("/")) {
                body = body.substring(0, body.length() - 1);
            }
            Map<String, String> attrs = new HashMap<>();
            Matcher a = ATTR.matcher(body);
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
