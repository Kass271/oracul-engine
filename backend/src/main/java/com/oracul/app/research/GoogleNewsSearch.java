package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** FR-52: one Google News RSS request per planned query, on virtual threads under a fair semaphore. */
@Component
public class GoogleNewsSearch implements NewsSearchProvider {

    static final int MAX_ITEMS = 100;
    private static final Logger log = LoggerFactory.getLogger(GoogleNewsSearch.class);
    private static final Set<String> OPERATORS = Set.of("OR", "AND", "NOT");
    private static final NewsSearchProvider.QueryResult FAILED =
        new NewsSearchProvider.QueryResult(SearchQueryStatus.FAILED, List.of());
    private static final NewsSearchProvider.QueryResult EMPTY =
        new NewsSearchProvider.QueryResult(SearchQueryStatus.EMPTY, List.of());

    private final NewsProvider provider;
    private final int concurrency;
    private final Duration timeout;
    private final Duration rateLimitWait;

    GoogleNewsSearch(NewsProvider provider, @Value("${oracul.news.google.concurrency:8}") int concurrency,
                     @Value("${oracul.news.google.timeout:PT10S}") Duration timeout,
                     @Value("${oracul.news.google.rate-limit-wait:PT2S}") Duration rateLimitWait) {
        if (concurrency < 1 || concurrency > 8) {
            throw new IllegalStateException("oracul.news.google.concurrency must be between 1 and 8");
        }
        if (rateLimitWait.isNegative()) {
            throw new IllegalStateException("oracul.news.google.rate-limit-wait must not be negative");
        }
        this.provider = provider;
        this.concurrency = concurrency;
        this.timeout = timeout;
        this.rateLimitWait = rateLimitWait;
    }

    /** Quotes and parentheses become spaces, whitespace is collapsed, upper-case OR / AND / NOT go; null when nothing is left. */
    public static String text(String queryText) {
        if (queryText == null) {
            return null;
        }
        String cleaned = queryText.replace('"', ' ').replace('“', ' ').replace('”', ' ')
            .replace('(', ' ').replace(')', ' ');
        List<String> tokens = new ArrayList<>();
        for (String t : cleaned.trim().split("\\s+")) {
            if (!t.isEmpty() && !OPERATORS.contains(t)) {
                tokens.add(t);
            }
        }
        return tokens.isEmpty() ? null : String.join(" ", tokens);
    }

    public static String q(String text, HorizonCode horizon) {
        return text + " when:" + timespanDays(horizon) + "d";
    }

    /** Days of the {@code when:<N>d} window of a horizon (also the age filter). */
    public static int timespanDays(HorizonCode horizon) {
        return switch (horizon) {
            case _1D, _1W -> 7;
            case _1M -> 14;
            default -> 90;
        };
    }

    @Override
    public List<NewsSearchProvider.QueryResult> search(List<String> texts, HorizonCode horizon, SearchBudget budget,
                                                       BooleanSupplier mayStart) throws InterruptedException {
        int n = texts.size();
        NewsSearchProvider.QueryResult[] results = new NewsSearchProvider.QueryResult[n];
        Arrays.fill(results, FAILED);
        Semaphore permits = new Semaphore(concurrency, true);
        List<Future<?>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
        try {
            for (int i = 0; i < n; i++) {
                String text = text(texts.get(i));
                if (text == null) {
                    results[i] = EMPTY;
                    continue;
                }
                if (!acquire(permits, budget)) {
                    continue;
                }
                if (!mayStart.getAsBoolean()) {
                    permits.release();
                    continue;
                }
                if (budget.expired(SearchBudget.Phase.SEARCH)) {
                    permits.release();
                    log.warn("google news request skipped: search window ended");
                    continue;
                }
                String q = q(text, horizon);
                int index = i;
                Duration allowed = min(timeout, budget.remaining(SearchBudget.Phase.SEARCH));
                futures.add(pool.submit(() -> {
                    results[index] = run(q, allowed, permits, budget, mayStart);
                    return null;
                }));
            }
            for (Future<?> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    // the slot stays FAILED
                }
            }
        } catch (InterruptedException e) {
            pool.shutdownNow();
            throw e;
        } finally {
            pool.shutdown();
        }
        return Arrays.asList(results);
    }

    /** Takes a permit within the window; false (and the window line) when the window ends first. */
    private boolean acquire(Semaphore permits, SearchBudget budget) throws InterruptedException {
        Duration left = budget.remaining(SearchBudget.Phase.SEARCH);
        if (left.isZero() || !permits.tryAcquire(left.toNanos(), TimeUnit.NANOSECONDS)) {
            log.warn("google news request skipped: search window ended");
            return false;
        }
        return true;
    }

    /** Sends the request holding a permit (released on return); retries a 429 once. */
    private NewsSearchProvider.QueryResult run(String q, Duration allowed, Semaphore permits, SearchBudget budget,
                                               BooleanSupplier mayStart) throws InterruptedException {
        NewsProvider.Result r;
        try {
            r = provider.search(q, MAX_ITEMS, allowed);
        } finally {
            permits.release();
        }
        if (!r.rateLimited()) {
            return of(r);
        }
        Duration left = budget.remaining(SearchBudget.Phase.SEARCH);
        if (rateLimitWait.compareTo(left) >= 0) {
            log.warn("google news request skipped: search window ended");
            return FAILED;
        }
        log.warn("google news request rate-limited, retrying once");
        if (!rateLimitWait.isZero()) {
            Thread.sleep(rateLimitWait);
        }
        if (!acquire(permits, budget)) {
            return FAILED;
        }
        try {
            if (!mayStart.getAsBoolean()) {
                return FAILED;
            }
            if (budget.expired(SearchBudget.Phase.SEARCH)) {
                log.warn("google news request skipped: search window ended");
                return FAILED;
            }
            r = provider.search(q, MAX_ITEMS, min(timeout, budget.remaining(SearchBudget.Phase.SEARCH)));
        } finally {
            permits.release();
        }
        return r.rateLimited() ? FAILED : of(r);
    }

    private static NewsSearchProvider.QueryResult of(NewsProvider.Result r) {
        return new NewsSearchProvider.QueryResult(r.status(), r.articles());
    }

    private static Duration min(Duration a, Duration b) {
        return a.compareTo(b) <= 0 ? a : b;
    }
}
