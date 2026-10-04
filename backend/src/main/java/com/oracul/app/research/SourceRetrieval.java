package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stages SEARCHING and READING_SOURCES: run the queries, filter, fetch metadata (FR-13). */
@Component
public class SourceRetrieval {

    private static final DateTimeFormatter SEENDATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int MAX_SUMMARY = 600;
    private static final Logger log = LoggerFactory.getLogger(SourceRetrieval.class);

    /** One article and the query it was attributed to. */
    public record Attributed(String queryId, NewsProvider.Article article) {
    }

    /**
     * Plan with query statuses filled in, the raw articles per query id, and every article in group order then response
     * order (the order READING_SOURCES walks).
     */
    public record SearchOutcome(SearchPlan plan, Map<String, List<NewsProvider.Article>> articles,
                                List<Attributed> ordered) {
        public int searches() {
            return plan.getQueries().size();
        }

        public int articlesRetrieved() {
            return plan.getQueries().stream().mapToInt(SearchQuery::getArticlesReturned).sum();
        }

        /** True when every planned query is FAILED: every group failed or could not be sent. */
        public boolean allFailed() {
            return plan.getQueries().stream().allMatch(q -> q.getStatus() == SearchQueryStatus.FAILED);
        }
    }

    private final NewsProvider news;
    private final ArticleMetadataFetcher fetcher;
    private final SourceQualityTable quality;
    private final Clock clock;
    private final int maxRequests;
    private final Duration requestSpacing;
    private final Duration queryTimeout;
    private final Duration rateLimitWait;
    private final Duration searchBudget;
    private final int maxRecordsPerQuery;
    private final int fetchConcurrency;
    private final ExecutorService fetchPool;

    SourceRetrieval(NewsProvider news, ArticleMetadataFetcher fetcher, SourceQualityTable quality, Clock clock,
                    @Value("${oracul.news.max-requests:4}") int maxRequests,
                    @Value("${oracul.news.request-spacing:PT5S}") Duration requestSpacing,
                    @Value("${oracul.news.query-timeout:PT30S}") Duration queryTimeout,
                    @Value("${oracul.news.rate-limit-wait:PT5S}") Duration rateLimitWait,
                    @Value("${oracul.news.search-budget:PT75S}") Duration searchBudget,
                    @Value("${oracul.news.max-records-per-query:25}") int maxRecordsPerQuery,
                    @Value("${oracul.news.article-fetch-concurrency:8}") int fetchConcurrency) {
        this.news = news;
        this.fetcher = fetcher;
        this.quality = quality;
        this.clock = clock;
        this.maxRequests = Math.max(1, maxRequests);
        this.requestSpacing = requestSpacing;
        this.queryTimeout = queryTimeout;
        this.rateLimitWait = rateLimitWait;
        this.searchBudget = searchBudget;
        this.maxRecordsPerQuery = maxRecordsPerQuery;
        this.fetchConcurrency = Math.max(1, fetchConcurrency);
        this.fetchPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdown() {
        fetchPool.shutdownNow();
    }

    public SearchOutcome search(SearchPlan plan, HorizonCode horizon) throws InterruptedException {
        return search(plan, horizon, () -> true);
    }

    /**
     * Sends the planned queries as at most max-requests OR-group requests, one at a time and spaced (FR-44).
     * {@code mayStart} is the run guard: no request starts once it says no.
     */
    public SearchOutcome search(SearchPlan plan, HorizonCode horizon, BooleanSupplier mayStart)
        throws InterruptedException {
        List<SearchQuery> queries = plan.getQueries();
        String[] elements = new String[queries.size()];
        List<Integer> sendable = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            elements[i] = GdeltQueryGroups.element(queries.get(i).getText());
            if (elements[i] != null) {
                sendable.add(i);
            }
        }
        long budgetEnd = System.nanoTime() + searchBudget.toNanos();
        Scheduler scheduler = new Scheduler(budgetEnd, mayStart);
        Map<String, List<NewsProvider.Article>> articles = new LinkedHashMap<>();
        queries.forEach(q -> articles.put(q.getId(), new ArrayList<>()));
        SearchQueryStatus[] status = new SearchQueryStatus[queries.size()];
        java.util.Arrays.fill(status, SearchQueryStatus.EMPTY);
        List<Attributed> ordered = new ArrayList<>();

        int from = 0;
        for (int size : GdeltQueryGroups.groupSizes(sendable.size(), maxRequests)) {
            List<Integer> members = sendable.subList(from, from + size);
            from += size;
            List<String> groupElements = new ArrayList<>();
            members.forEach(i -> groupElements.add(elements[i]));
            NewsProvider.Result result = scheduler.request(GdeltQueryGroups.query(groupElements),
                GdeltQueryGroups.maxRecords(maxRecordsPerQuery, size), horizon);
            if (result.status() == SearchQueryStatus.FAILED) {
                members.forEach(i -> status[i] = SearchQueryStatus.FAILED);
                continue;
            }
            for (NewsProvider.Article a : result.articles()) {
                int member = GdeltQueryGroups.attribute(groupElements, a.title());
                int queryIndex = members.get(member);
                articles.get(queries.get(queryIndex).getId()).add(a);
                ordered.add(new Attributed(queries.get(queryIndex).getId(), a));
                status[queryIndex] = SearchQueryStatus.OK;
            }
        }
        List<SearchQuery> updated = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            SearchQuery q = queries.get(i);
            updated.add(new SearchQuery(q.getId(), q.getIntentId(), q.getBucket(), q.getText(), status[i],
                articles.get(q.getId()).size()));
        }
        SearchPlan out = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(),
            plan.getIntents(), updated);
        return new SearchOutcome(out, articles, ordered);
    }

    /** Serial, spaced GDELT requests inside the search budget, with one retry after a 429. */
    private final class Scheduler {
        private final long budgetEnd;
        private final BooleanSupplier mayStart;
        private long lastStart;
        private boolean started;

        Scheduler(long budgetEnd, BooleanSupplier mayStart) {
            this.budgetEnd = budgetEnd;
            this.mayStart = mayStart;
        }

        NewsProvider.Result request(String query, int maxRecords, HorizonCode horizon) throws InterruptedException {
            NewsProvider.Result result = attempt(query, maxRecords, horizon, System.nanoTime());
            if (result == null) {
                return NewsProvider.Result.failed();
            }
            if (!result.rateLimited()) {
                return result;
            }
            log.warn("news request rate-limited, retrying once");
            long earliest = System.nanoTime() + rateLimitWait.toNanos();
            result = attempt(query, maxRecords, horizon, earliest);
            return result == null || result.rateLimited() ? NewsProvider.Result.failed() : result;
        }

        /** Null when the request could not be started (budget or deadline). */
        private NewsProvider.Result attempt(String query, int maxRecords, HorizonCode horizon, long earliest)
            throws InterruptedException {
            long start = Math.max(earliest, started ? lastStart + requestSpacing.toNanos() : earliest);
            start = Math.max(start, System.nanoTime());
            if (start >= budgetEnd) {
                log.warn("news request skipped: search budget exhausted");
                return null;
            }
            sleepUntil(start);
            if (!mayStart.getAsBoolean()) {
                return null;
            }
            long now = System.nanoTime();
            long left = budgetEnd - now;
            if (left <= 0) {
                log.warn("news request skipped: search budget exhausted");
                return null;
            }
            lastStart = now;
            started = true;
            Duration timeout = Duration.ofNanos(Math.min(queryTimeout.toNanos(), left));
            return news.search(query, maxRecords, horizon, timeout);
        }

        private void sleepUntil(long nanoTime) throws InterruptedException {
            long wait;
            while ((wait = nanoTime - System.nanoTime()) > 0) {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            }
        }
    }

    private record Candidate(NewsProvider.Article article, String url, List<String> queryIds, String topic) {
    }

    public List<SourceRepository.Stored> readSources(SearchOutcome outcome, HorizonCode horizon)
        throws InterruptedException {
        List<Candidate> candidates = filter(outcome, horizon);
        SourceRepository.Stored[] stored = new SourceRepository.Stored[candidates.size()];
        runBounded(fetchPool, fetchConcurrency, candidates.size(), i -> {
            stored[i] = build(i, candidates.get(i));
            return null;
        });
        List<SourceRepository.Stored> out = new ArrayList<>();
        for (SourceRepository.Stored s : stored) {
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    private List<Candidate> filter(SearchOutcome outcome, HorizonCode horizon) {
        Map<String, SearchIntent> intents = new HashMap<>();
        outcome.plan().getIntents().forEach(in -> intents.put(in.getId(), in));
        Instant cutoff = clock.instant().minus(GdeltNewsProvider.timespanDays(horizon), ChronoUnit.DAYS);
        Map<String, SearchQuery> byId = new HashMap<>();
        outcome.plan().getQueries().forEach(q -> byId.put(q.getId(), q));
        Map<String, Candidate> byUrl = new LinkedHashMap<>();
        for (Attributed attributed : outcome.ordered()) {
            NewsProvider.Article a = attributed.article();
            SearchQuery q = byId.get(attributed.queryId());
            String url = UrlNormalizer.normalize(a.url());
            if (url == null || a.title() == null || a.title().isBlank()) {
                continue;
            }
            if (a.language() != null && !a.language().isBlank() && !a.language().trim().equalsIgnoreCase("English")) {
                continue;
            }
            Instant seen = parseSeen(a.seendate());
            if (seen != null && seen.isBefore(cutoff)) {
                continue;
            }
            Candidate existing = byUrl.get(url);
            if (existing != null) {
                if (!existing.queryIds().contains(q.getId())) {
                    existing.queryIds().add(q.getId());
                    java.util.Collections.sort(existing.queryIds());
                }
                continue;
            }
            List<String> ids = new ArrayList<>();
            ids.add(q.getId());
            byUrl.put(url, new Candidate(a, url, ids, topicOf(intents.get(q.getIntentId()))));
        }
        return new ArrayList<>(byUrl.values());
    }

    private static String topicOf(SearchIntent in) {
        if (in == null) {
            return null;
        }
        QueryBucket b = in.getBucket();
        return switch (b) {
            case WILDCARD -> in.getTopicKey();
            case ADJACENT -> in.getCategory() == null ? "general" : in.getCategory();
            case MAJOR -> "major";
            case UNEXPECTED -> "unexpected";
        };
    }

    private SourceRepository.Stored build(int index, Candidate c) {
        NewsProvider.Article a = c.article();
        Instant attempt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Optional<ArticleMetadataFetcher.Metadata> meta = fetcher.fetch(c.url());
        String title = collapse(a.title());
        String host = UrlNormalizer.host(c.url());
        String domain = a.domain() != null && !a.domain().isBlank() ? a.domain().trim() : host;
        String siteName = meta.map(ArticleMetadataFetcher.Metadata::siteName).map(String::trim).orElse("");
        String publisher = !siteName.isEmpty() ? siteName
            : a.domain() != null && !a.domain().isBlank() ? a.domain().trim() : host;
        String description = meta.map(ArticleMetadataFetcher.Metadata::description).map(SourceRetrieval::collapse)
            .orElse("");
        String summary = description.isEmpty() ? title
            : description.length() > MAX_SUMMARY ? description.substring(0, MAX_SUMMARY) : description;
        Instant seen = parseSeen(a.seendate());
        var cls = quality.classify(domain);

        Source s = new Source();
        s.setId(String.format("S%03d", index + 1));
        s.setUrl(URI.create(c.url()));
        s.setPublisher(publisher);
        s.setTitle(title);
        s.setPublishedAt(seen == null ? null : seen.atOffset(ZoneOffset.UTC));
        s.setRetrievedAt(attempt.atOffset(ZoneOffset.UTC));
        s.setSummary(summary);
        s.setTopic(c.topic());
        s.setEntities(new ArrayList<>());
        s.setSourceType(cls.type());
        s.setSourceQuality(cls.quality());
        s.setMetadataFetched(meta.isPresent());
        s.setQueryIds(c.queryIds());
        return new SourceRepository.Stored(s, a.language());
    }

    private static Instant parseSeen(String seendate) {
        if (seendate == null || seendate.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(seendate.trim(), SEENDATE).toInstant(ZoneOffset.UTC);
        } catch (Exception e) {
            return null;
        }
    }

    private static String collapse(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }

    @FunctionalInterface
    private interface Job {
        Object run(int index) throws Exception;
    }

    /** Runs jobs 0..n-1 with at most {@code limit} at a time and waits for all of them. */
    private static void runBounded(ExecutorService pool, int limit, int n, Job job) throws InterruptedException {
        java.util.concurrent.Semaphore permits = new java.util.concurrent.Semaphore(limit, true);
        List<Future<Object>> futures = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            int index = i;
            Callable<Object> task = () -> {
                permits.acquire();
                try {
                    return job.run(index);
                } finally {
                    permits.release();
                }
            };
            futures.add(pool.submit(task));
        }
        try {
            for (Future<Object> f : futures) {
                try {
                    f.get();
                } catch (ExecutionException e) {
                    // a failing job leaves its slot empty; callers treat that as FAILED / dropped
                }
            }
        } catch (InterruptedException e) {
            futures.forEach(f -> f.cancel(true));
            throw e;
        }
    }
}
