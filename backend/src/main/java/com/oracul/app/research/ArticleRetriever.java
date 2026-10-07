package com.oracul.app.research;

import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** FR-54: reaches the publisher page behind every kept link (Google page, decode, publisher fetch) within the stage budget. */
@Component
public class ArticleRetriever {

    private static final Logger LOG = LoggerFactory.getLogger(ArticleRetriever.class);
    private static final Pattern CHARSET = Pattern.compile("charset=\"?([\\w.:-]+)", Pattern.CASE_INSENSITIVE);
    private static final long WATCH_MS = 50;

    /** How the retrieval of one link ended (mapped to {@code ArticleContentStatus} in {@code SourceRetrieval}). */
    public enum Status { PAGE_READ, DECODE_FAILED, PAGE_FAILED, REFUSED, NOT_ATTEMPTED }

    /** {@code publisherUrl}: the decoded URL (Google link) or the link itself (publisher link); null while unknown. */
    public record Outcome(Status status, String publisherUrl, String contentType, String body, String description,
                          String siteName, Instant endedAt) {
    }

    private final SafeFetcher fetcher;
    private final ArticleUrlDecoder decoder;
    private final String baseHost;
    private final Duration timeout;
    private final int concurrency;
    private final Clock clock;

    @Autowired
    ArticleRetriever(SafeFetcher fetcher, ArticleUrlDecoder decoder,
                     @Value("${oracul.news.google.base-url:https://news.google.com}") String googleBaseUrl,
                     @Value("${oracul.news.article-fetch-timeout:PT8S}") Duration timeout,
                     @Value("${oracul.news.article-fetch-concurrency:8}") int concurrency, Clock clock) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalStateException("oracul.news.article-fetch-concurrency must be between 1 and 8");
        }
        this.fetcher = fetcher;
        this.decoder = decoder;
        this.baseHost = hostOf(googleBaseUrl);
        this.timeout = timeout;
        this.concurrency = concurrency;
        this.clock = clock;
    }

    private static String hostOf(String url) {
        try {
            String h = URI.create(url.trim()).getHost();
            return h == null ? null : h.toLowerCase(Locale.ROOT);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public boolean isGoogleLink(String url) {
        String host = hostOf(url);
        return host != null && (host.equals("news.google.com") || host.equals(baseHost));
    }

    private Outcome outcome(Status status, String publisherUrl) {
        return new Outcome(status, publisherUrl, null, null, null, null, clock.instant());
    }

    /** One link: state shared with the watching caller. */
    private final class Link implements Runnable {
        final int index;
        final String link;
        final SearchBudget budget;
        final BooleanSupplier mayStart;
        final Semaphore permits;
        final AtomicReferenceArray<Outcome> out;
        final CountDownLatch done;
        volatile String known;
        boolean held = true;

        Link(int index, String link, SearchBudget budget, BooleanSupplier mayStart, Semaphore permits,
             AtomicReferenceArray<Outcome> out, CountDownLatch done) {
            this.index = index;
            this.link = link;
            this.budget = budget;
            this.mayStart = mayStart;
            this.permits = permits;
            this.out = out;
            this.done = done;
        }

        @Override
        public void run() {
            Outcome result;
            try {
                result = retrieve();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                result = outcome(Status.NOT_ATTEMPTED, known);
            } catch (RuntimeException e) {
                result = outcome(Status.NOT_ATTEMPTED, known);
            } finally {
                if (held) {
                    permits.release();
                    held = false;
                }
            }
            out.compareAndSet(index, null, result);
            done.countDown();
        }

        private boolean begin() throws InterruptedException {
            if (!held) {
                permits.acquire();
                held = true;
            }
            if (!mayStart.getAsBoolean() || budget.expired(SearchBudget.Phase.RETRIEVAL)) {
                permits.release();
                held = false;
                return false;
            }
            return true;
        }

        private void end() {
            if (held) {
                permits.release();
                held = false;
            }
        }

        private Duration allowed() {
            Duration left = budget.remaining(SearchBudget.Phase.RETRIEVAL);
            return left.compareTo(timeout) < 0 ? left : timeout;
        }

        private Outcome retrieve() throws InterruptedException {
            if (!isGoogleLink(link)) {
                known = link;
                return publisher(link);
            }
            Duration t = allowed();
            long start = System.nanoTime();
            if (!begin()) {
                return outcome(Status.NOT_ATTEMPTED, null);
            }
            SafeFetcher.Result page;
            try {
                page = fetcher.fetch(URI.create(link), t, true);
            } catch (RuntimeException e) {
                page = null;
            } finally {
                end();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            if (page == null || page.outcome() == SafeFetcher.Outcome.FAILED) {
                return cut(Status.DECODE_FAILED, null, used(start, t) ? "timeout" : "error");
            }
            switch (page.outcome()) {
                case REFUSED_SCHEME, REFUSED_ADDRESS -> {
                    return outcome(Status.REFUSED, null);
                }
                case TOO_MANY_REDIRECTS -> {
                    return cut(Status.DECODE_FAILED, null, "redirects");
                }
                default -> {
                }
            }
            if (page.status() / 100 != 2) {
                return cut(Status.DECODE_FAILED, null, "status=" + page.status());
            }
            Optional<ArticleUrlDecoder.Attributes> attrs = ArticleUrlDecoder.attributes(
                new String(page.body(), charsetOf(page.contentType())));
            if (attrs.isEmpty()) {
                return cut(Status.DECODE_FAILED, null, "no-attributes");
            }
            Duration left = t.minus(Duration.ofNanos(System.nanoTime() - start));
            if (left.isNegative() || left.isZero()) {
                return cut(Status.DECODE_FAILED, null, "timeout");
            }
            if (!begin()) {
                return outcome(Status.NOT_ATTEMPTED, null);
            }
            ArticleUrlDecoder.Result decoded;
            try {
                decoded = decoder.decode(attrs.get(), left);
            } finally {
                end();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            if (decoded.url() == null) {
                return cut(Status.DECODE_FAILED, null, decoded.reason());
            }
            known = decoded.url();
            return publisher(decoded.url());
        }

        private boolean used(long start, Duration t) {
            return System.nanoTime() - start >= t.toNanos() * 9 / 10;
        }

        /** A failure caused by the budget running out is NOT_ATTEMPTED; otherwise it is logged and kept. */
        private Outcome cut(Status status, String url, String reason) {
            if (budget.remaining(SearchBudget.Phase.RETRIEVAL).toMillis() < 100) {
                return outcome(Status.NOT_ATTEMPTED, known);
            }
            if (status == Status.DECODE_FAILED) {
                LOG.warn("article decode failed: {}", reason);
            } else {
                LOG.warn("article fetch failed: {}", reason);
            }
            return outcome(status, known);
        }

        private Outcome publisher(String url) throws InterruptedException {
            Duration t = allowed();
            long start = System.nanoTime();
            if (!begin()) {
                return outcome(Status.NOT_ATTEMPTED, known);
            }
            SafeFetcher.Result r;
            try {
                URI uri;
                try {
                    uri = URI.create(url);
                } catch (IllegalArgumentException e) {
                    return outcome(Status.REFUSED, known);
                }
                r = fetcher.fetch(uri, t, false);
            } catch (RuntimeException e) {
                r = null;
            } finally {
                end();
            }
            if (Thread.currentThread().isInterrupted()) {
                throw new InterruptedException();
            }
            if (r == null || r.outcome() == SafeFetcher.Outcome.FAILED) {
                return cut(Status.PAGE_FAILED, null, used(start, t) ? "timeout" : "error");
            }
            switch (r.outcome()) {
                case REFUSED_SCHEME, REFUSED_ADDRESS -> {
                    return outcome(Status.REFUSED, known);
                }
                case TOO_MANY_REDIRECTS -> {
                    return cut(Status.PAGE_FAILED, null, "redirects");
                }
                default -> {
                }
            }
            if (r.status() / 100 != 2) {
                return cut(Status.PAGE_FAILED, null, "status=" + r.status());
            }
            String type = r.contentType() == null ? "" : r.contentType().trim().toLowerCase(Locale.ROOT);
            if (!(type.startsWith("text/html") || type.startsWith("application/xhtml+xml") || type.startsWith("text/plain"))) {
                return cut(Status.PAGE_FAILED, null, "content-type");
            }
            String body = new String(r.body(), charsetOf(r.contentType()));
            String description = null;
            String siteName = null;
            if (!type.startsWith("text/plain")) {
                ArticleMetadataFetcher.Metadata meta = ArticleMetadataFetcher.parse(body);
                description = meta.description();
                siteName = meta.siteName();
            }
            return new Outcome(Status.PAGE_READ, known, r.contentType(), body, description, siteName, clock.instant());
        }
    }

    private static Charset charsetOf(String contentType) {
        if (contentType != null) {
            Matcher m = CHARSET.matcher(contentType);
            if (m.find()) {
                try {
                    return Charset.forName(m.group(1));
                } catch (RuntimeException e) {
                    // fall back to UTF-8
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    public List<Outcome> retrieveAll(List<String> links, SearchBudget budget, BooleanSupplier mayStart)
        throws InterruptedException {
        int n = links.size();
        AtomicReferenceArray<Outcome> out = new AtomicReferenceArray<>(n);
        CountDownLatch done = new CountDownLatch(n);
        Semaphore permits = new Semaphore(concurrency, true);
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        List<Link> tasks = new ArrayList<>();
        List<Future<?>> futures = new ArrayList<>();
        try {
            int next = 0;
            while (next < n) {
                if (budget.expired(SearchBudget.Phase.RETRIEVAL)) {
                    break;
                }
                if (!permits.tryAcquire(WATCH_MS, TimeUnit.MILLISECONDS)) {
                    continue;
                }
                Link task = new Link(next, links.get(next), budget, mayStart, permits, out, done);
                tasks.add(task);
                futures.add(pool.submit(task));
                next++;
            }
            for (int i = next; i < n; i++) {
                String known = isGoogleLink(links.get(i)) ? null : links.get(i);
                if (out.compareAndSet(i, null, outcome(Status.NOT_ATTEMPTED, known))) {
                    done.countDown();
                }
            }
            while (!done.await(WATCH_MS, TimeUnit.MILLISECONDS)) {
                if (budget.expired(SearchBudget.Phase.RETRIEVAL)) {
                    break;
                }
            }
            for (int i = 0; i < tasks.size(); i++) {
                Link task = tasks.get(i);
                if (out.get(task.index) == null) {
                    out.compareAndSet(task.index, null, outcome(Status.NOT_ATTEMPTED, task.known));
                    futures.get(i).cancel(true);
                }
            }
        } catch (InterruptedException e) {
            futures.forEach(f -> f.cancel(true));
            throw e;
        } finally {
            pool.shutdownNow();
        }
        List<Outcome> result = new ArrayList<>(n);
        for (int i = 0; i < n; i++) {
            result.add(out.get(i));
        }
        return result;
    }
}
