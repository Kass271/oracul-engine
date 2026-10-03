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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Stages SEARCHING and READING_SOURCES: run the queries, filter, fetch metadata (FR-13). */
@Component
public class SourceRetrieval {

    private static final DateTimeFormatter SEENDATE = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'");
    private static final int MAX_SUMMARY = 600;

    /** Plan with query statuses filled in, plus the raw articles per query id. */
    public record SearchOutcome(SearchPlan plan, Map<String, List<NewsProvider.Article>> articles) {
        public int searches() {
            return plan.getQueries().size();
        }

        public int articlesRetrieved() {
            return plan.getQueries().stream().mapToInt(SearchQuery::getArticlesReturned).sum();
        }

        public boolean allFailed() {
            return plan.getQueries().stream().allMatch(q -> q.getStatus() == SearchQueryStatus.FAILED);
        }
    }

    private final NewsProvider news;
    private final ArticleMetadataFetcher fetcher;
    private final SourceQualityTable quality;
    private final Clock clock;
    private final int queryConcurrency;
    private final int fetchConcurrency;
    private final ExecutorService queryPool;
    private final ExecutorService fetchPool;

    SourceRetrieval(NewsProvider news, ArticleMetadataFetcher fetcher, SourceQualityTable quality, Clock clock,
                    @Value("${oracul.news.query-concurrency:4}") int queryConcurrency,
                    @Value("${oracul.news.article-fetch-concurrency:8}") int fetchConcurrency) {
        this.news = news;
        this.fetcher = fetcher;
        this.quality = quality;
        this.clock = clock;
        this.queryConcurrency = Math.max(1, queryConcurrency);
        this.fetchConcurrency = Math.max(1, fetchConcurrency);
        this.queryPool = Executors.newVirtualThreadPerTaskExecutor();
        this.fetchPool = Executors.newVirtualThreadPerTaskExecutor();
    }

    @PreDestroy
    void shutdown() {
        queryPool.shutdownNow();
        fetchPool.shutdownNow();
    }

    public SearchOutcome search(SearchPlan plan, HorizonCode horizon) throws InterruptedException {
        List<SearchQuery> queries = plan.getQueries();
        NewsProvider.Result[] results = new NewsProvider.Result[queries.size()];
        runBounded(queryPool, queryConcurrency, queries.size(), i -> {
            results[i] = news.search(queries.get(i).getText(), horizon);
            return null;
        });
        List<SearchQuery> updated = new ArrayList<>();
        Map<String, List<NewsProvider.Article>> articles = new LinkedHashMap<>();
        for (int i = 0; i < queries.size(); i++) {
            SearchQuery q = queries.get(i);
            NewsProvider.Result r = results[i] == null ? NewsProvider.Result.failed() : results[i];
            updated.add(new SearchQuery(q.getId(), q.getIntentId(), q.getBucket(), q.getText(), r.status(),
                r.articles().size()));
            articles.put(q.getId(), r.articles());
        }
        SearchPlan out = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(),
            plan.getIntents(), updated);
        return new SearchOutcome(out, articles);
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
        Map<String, Candidate> byUrl = new LinkedHashMap<>();
        for (SearchQuery q : outcome.plan().getQueries()) {
            for (NewsProvider.Article a : outcome.articles().getOrDefault(q.getId(), List.of())) {
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
                    }
                    continue;
                }
                List<String> ids = new ArrayList<>();
                ids.add(q.getId());
                byUrl.put(url, new Candidate(a, url, ids, topicOf(intents.get(q.getIntentId()))));
            }
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
