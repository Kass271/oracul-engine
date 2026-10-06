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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
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

/** Stages SEARCHING and READING_SOURCES: run the queries, filter, fetch metadata (FR-13, FR-48). */
@Component
public class SourceRetrieval {

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

    /** FR-46: the most sources a run keeps. */
    public static final int MAX_SOURCES = SourceCap.MAX;
    private static final int GOOGLE_REDIRECTS = 5;
    private static final int MAX_PER_QUERY = 25;

    private final NewsProvider google;
    private final Duration googleSpacing;
    private final Duration googleTimeout;
    private final ArticleMetadataFetcher fetcher;
    private final SourceQualityTable quality;
    private final Clock clock;
    private final Duration searchBudget;
    private final int fetchConcurrency;
    private final ExecutorService fetchPool;

    SourceRetrieval(NewsProvider google, ArticleMetadataFetcher fetcher, SourceQualityTable quality, Clock clock,
                    @Value("${oracul.news.google.request-spacing:PT1S}") Duration googleSpacing,
                    @Value("${oracul.news.google.timeout:PT10S}") Duration googleTimeout,
                    @Value("${oracul.news.search-budget:PT75S}") Duration searchBudget,
                    @Value("${oracul.news.article-fetch-concurrency:8}") int fetchConcurrency) {
        this.google = google;
        this.googleSpacing = googleSpacing;
        this.googleTimeout = googleTimeout;
        this.fetcher = fetcher;
        this.quality = quality;
        this.clock = clock;
        this.searchBudget = searchBudget;
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
     * Sends the planned queries as 4 OR-group requests, one at a time and spaced (FR-48).
     * {@code mayStart} is the run guard: no request starts once it says no.
     */
    public SearchOutcome search(SearchPlan plan, HorizonCode horizon, BooleanSupplier mayStart)
        throws InterruptedException {
        List<SearchQuery> queries = plan.getQueries();
        String[] elements = new String[queries.size()];
        List<Integer> sendable = new ArrayList<>();
        for (int i = 0; i < queries.size(); i++) {
            elements[i] = GoogleQueryGroups.element(queries.get(i).getText());
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
        for (int size : GoogleQueryGroups.groupSizes(sendable.size(), GoogleQueryGroups.GROUPS)) {
            List<Integer> members = sendable.subList(from, from + size);
            from += size;
            List<String> groupElements = new ArrayList<>();
            members.forEach(i -> groupElements.add(elements[i]));
            int maxRecords = GoogleQueryGroups.maxRecords(MAX_PER_QUERY, size);
            NewsProvider.Result result = scheduler.google(GoogleNewsProvider.q(groupElements, horizon), maxRecords);
            if (result == null) {
                result = NewsProvider.Result.failed(); // not started
            }
            if (result.status() == SearchQueryStatus.FAILED) {
                log.warn("news group failed");
                members.forEach(i -> status[i] = SearchQueryStatus.FAILED);
                continue;
            }
            for (NewsProvider.Article a : result.articles()) {
                int member = GoogleQueryGroups.attribute(groupElements, a.title());
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

    /** Serial, spaced Google requests inside the search budget (never retried). */
    private final class Scheduler {
        private final long budgetEnd;
        private final BooleanSupplier mayStart;
        private long lastGoogleStart;
        private boolean googleStarted;

        Scheduler(long budgetEnd, BooleanSupplier mayStart) {
            this.budgetEnd = budgetEnd;
            this.mayStart = mayStart;
        }

        /** One Google request (never retried); null when it could not be started (budget, deadline, STOP). */
        NewsProvider.Result google(String q, int maxItems) throws InterruptedException {
            long now = System.nanoTime();
            long start = googleStarted ? Math.max(now, lastGoogleStart + googleSpacing.toNanos()) : now;
            if (start >= budgetEnd) {
                log.warn("news request skipped: search budget exhausted");
                return null;
            }
            sleepUntil(start);
            if (!mayStart.getAsBoolean()) {
                return null;
            }
            now = System.nanoTime();
            long left = budgetEnd - now;
            if (left <= 0) {
                log.warn("news request skipped: search budget exhausted");
                return null;
            }
            lastGoogleStart = now;
            googleStarted = true;
            return google.search(q, maxItems, Duration.ofNanos(Math.min(googleTimeout.toNanos(), left)));
        }

        private void sleepUntil(long nanoTime) throws InterruptedException {
            long wait;
            while ((wait = nanoTime - System.nanoTime()) > 0) {
                Thread.sleep(wait / 1_000_000, (int) (wait % 1_000_000));
            }
        }
    }

    private record Candidate(NewsProvider.Article article, String url, List<String> queryIds, String topic,
                             Instant seen) {
    }

    public List<SourceRepository.Stored> readSources(SearchOutcome outcome, HorizonCode horizon)
        throws InterruptedException {
        return readSources(outcome, horizon, () -> true);
    }

    /** {@code mayFetch} is the run guard, checked before each article fetch (no fetch once the run is not RUNNING). */
    public List<SourceRepository.Stored> readSources(SearchOutcome outcome, HorizonCode horizon,
                                                      BooleanSupplier mayFetch) throws InterruptedException {
        List<Candidate> usable = filter(outcome, horizon);
        List<SourceCap.Ranked> ranked = new ArrayList<>();
        for (Candidate c : usable) {
            ranked.add(new SourceCap.Ranked(c.topic(), quality.classify(qualityDomain(c, c.url())).quality()));
        }
        List<Candidate> candidates = new ArrayList<>();
        for (int i : SourceCap.select(ranked)) {
            candidates.add(usable.get(i));
        }
        Prepared[] prepared = new Prepared[candidates.size()];
        runBounded(fetchPool, fetchConcurrency, candidates.size(), i -> {
            prepared[i] = prepare(candidates.get(i), mayFetch);
            return null;
        });
        List<SourceRepository.Stored> out = new ArrayList<>();
        java.util.Set<String> used = new java.util.HashSet<>();
        for (Prepared p : prepared) {
            if (p != null) {
                used.add(p.candidate().url());
            }
        }
        java.util.Set<String> taken = new java.util.HashSet<>();
        for (int i = 0; i < prepared.length; i++) {
            if (prepared[i] == null) {
                continue;
            }
            Prepared p = prepared[i];
            String resolved = p.resolvedUrl();
            // a resolved URL that an earlier source already has (or another candidate's own link) is not used twice
            boolean useResolved = resolved != null && !taken.contains(resolved) && !used.contains(resolved);
            SourceRepository.Stored stored = build(out.size(), p, useResolved ? resolved : null);
            taken.add(stored.source().getUrl().toString());
            out.add(stored);
        }
        return out;
    }

    private List<Candidate> filter(SearchOutcome outcome, HorizonCode horizon) {
        Map<String, SearchIntent> intents = new HashMap<>();
        outcome.plan().getIntents().forEach(in -> intents.put(in.getId(), in));
        Instant cutoff = clock.instant().minus(GoogleQueryGroups.timespanDays(horizon), ChronoUnit.DAYS);
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
            Instant seen = a.publishedAt();
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
            byUrl.put(url, new Candidate(a, url, ids, topicOf(intents.get(q.getIntentId())), seen));
        }
        return new ArrayList<>(byUrl.values());
    }

    /** Domain whose quality table entry ranks the candidate: Google publisher host, else the URL host. */
    private static String qualityDomain(Candidate c, String url) {
        String host = absoluteHost(c.article().sourceUrl());
        return host != null ? host : UrlNormalizer.host(url);
    }

    /** Host (as written) of an absolute http(s) URL, else null. */
    private static String absoluteHost(String url) {
        String n = UrlNormalizer.normalize(url);
        if (n == null) {
            return null;
        }
        try {
            return URI.create(n).getHost();
        } catch (Exception e) {
            return null;
        }
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

    /** A candidate with the result of its article fetch. */
    private record Prepared(Candidate candidate, Optional<ArticleMetadataFetcher.Metadata> meta, String resolvedUrl) {
    }

    private Prepared prepare(Candidate c, BooleanSupplier mayFetch) {
        if (!mayFetch.getAsBoolean()) {
            return new Prepared(c, Optional.empty(), null);
        }
        Optional<ArticleMetadataFetcher.Fetched> fetched = fetcher.fetchDetailed(c.url(), GOOGLE_REDIRECTS);
        if (fetched.isPresent() && fetched.get().redirects() >= 1) {
            String finalUrl = UrlNormalizer.normalize(fetched.get().finalUrl());
            if (finalUrl != null && !finalUrl.equals(c.url())) {
                return new Prepared(c, Optional.of(fetched.get().metadata()), finalUrl);
            }
        }
        return new Prepared(c, Optional.empty(), null);
    }

    private SourceRepository.Stored build(int index, Prepared p, String resolvedUrl) {
        Candidate c = p.candidate();
        NewsProvider.Article a = c.article();
        Instant attempt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        String title = collapse(a.title());
        String url = resolvedUrl != null ? resolvedUrl : c.url();
        Optional<ArticleMetadataFetcher.Metadata> meta = resolvedUrl == null ? Optional.empty() : p.meta();
        String host = UrlNormalizer.host(url);
        String siteName = meta.map(ArticleMetadataFetcher.Metadata::siteName).map(String::trim).orElse("");
        String description = meta.map(ArticleMetadataFetcher.Metadata::description).map(SourceRetrieval::collapse)
            .orElse("");
        String summary = description.isEmpty() ? title
            : description.length() > MAX_SUMMARY ? description.substring(0, MAX_SUMMARY) : description;
        String publisher;
        String domain;
        String publisherUrl = null;
        String sourceText = a.sourceName() == null ? "" : a.sourceName().trim();
        String sourceHost = absoluteHost(a.sourceUrl());
        if (sourceHost != null) {
            publisherUrl = a.sourceUrl().trim();
        }
        domain = sourceHost != null ? sourceHost : host;
        if (resolvedUrl != null && !siteName.isEmpty()) {
            publisher = siteName;
        } else if (!sourceText.isEmpty()) {
            publisher = sourceText;
        } else {
            publisher = sourceHost != null ? sourceHost : host;
        }
        var cls = quality.classify(domain);

        Source s = new Source();
        s.setId(String.format("S%03d", index + 1));
        s.setUrl(URI.create(url));
        s.setPublisher(publisher);
        s.setTitle(title);
        s.setPublishedAt(c.seen() == null ? null : c.seen().atOffset(ZoneOffset.UTC));
        s.setRetrievedAt(attempt.atOffset(ZoneOffset.UTC));
        s.setSummary(summary);
        s.setTopic(c.topic());
        s.setEntities(new ArrayList<>());
        s.setSourceType(cls.type());
        s.setSourceQuality(cls.quality());
        s.setMetadataFetched(meta.isPresent());
        s.setQueryIds(c.queryIds());
        if (publisherUrl != null) {
            s.setPublisherUrl(URI.create(publisherUrl));
        }
        return new SourceRepository.Stored(s, null);
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
