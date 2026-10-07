package com.oracul.app.research;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.WildcardPipeline;
import com.oracul.app.api.model.WildcardPipelineKind;
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
            return flat(plan).size();
        }

        public int articlesRetrieved() {
            return flat(plan).stream().mapToInt(QueryRef::articles).sum();
        }

        /** True when every planned query is FAILED: every group failed or could not be sent. */
        public boolean allFailed() {
            return flat(plan).stream().allMatch(q -> q.status() == SearchQueryStatus.FAILED);
        }
    }

    /** One planned query, whichever plan shape holds it. */
    record QueryRef(String id, String text, SearchQueryStatus status, int articles, WildcardPipeline pipeline) {
    }

    /** The planned queries: pipelines[].queries[] in pipeline order, else the phase-01 queries[]. */
    static List<QueryRef> flat(SearchPlan plan) {
        List<QueryRef> out = new ArrayList<>();
        if (plan.getPipelines() != null && !plan.getPipelines().isEmpty()) {
            for (WildcardPipeline p : plan.getPipelines()) {
                for (PipelineQuery q : p.getQueries()) {
                    out.add(new QueryRef(q.getId(), q.getText(), q.getStatus(), q.getArticlesReturned(), p));
                }
            }
        } else {
            for (SearchQuery q : plan.getQueries()) {
                out.add(new QueryRef(q.getId(), q.getText(), q.getStatus(), q.getArticlesReturned(), null));
            }
        }
        return out;
    }

    /** FR-46: the most sources a run keeps. */
    public static final int MAX_SOURCES = WildcardSelector.MAX_SOURCES;
    private static final int GOOGLE_REDIRECTS = 5;

    private final NewsSearchProvider search;
    private final ArticleMetadataFetcher fetcher;
    private final SourceQualityTable quality;
    private final Clock clock;
    private final Duration searchWindow;
    private final int fetchConcurrency;
    private final ExecutorService fetchPool;

    SourceRetrieval(NewsSearchProvider search, ArticleMetadataFetcher fetcher, SourceQualityTable quality, Clock clock,
                    @Value("${oracul.search.search-window:PT60S}") Duration searchWindow,
                    @Value("${oracul.news.article-fetch-concurrency:8}") int fetchConcurrency) {
        if (searchWindow.isZero() || searchWindow.isNegative()) {
            throw new IllegalStateException("oracul.search.search-window must be greater than zero");
        }
        this.search = search;
        this.fetcher = fetcher;
        this.quality = quality;
        this.clock = clock;
        this.searchWindow = searchWindow;
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

    public SearchOutcome search(SearchPlan plan, HorizonCode horizon, BooleanSupplier mayStart)
        throws InterruptedException {
        return search(plan, horizon, clock.instant(), null, mayStart);
    }

    /**
     * One request per planned query (FR-52). {@code mayStart} is the run guard: no request starts once it says no;
     * the window runs from {@code t0} and is cut by {@code deadlineAt} (null: none).
     */
    public SearchOutcome search(SearchPlan plan, HorizonCode horizon, Instant t0, Instant deadlineAt,
                                BooleanSupplier mayStart) throws InterruptedException {
        List<QueryRef> queries = flat(plan);
        List<String> texts = new ArrayList<>();
        queries.forEach(q -> texts.add(q.text()));
        List<NewsSearchProvider.QueryResult> results = search.search(texts, horizon,
            SearchBudget.search(clock, t0, searchWindow, deadlineAt), mayStart);
        Map<String, List<NewsProvider.Article>> articles = new LinkedHashMap<>();
        List<Attributed> ordered = new ArrayList<>();
        Map<String, NewsSearchProvider.QueryResult> byId = new HashMap<>();
        for (int i = 0; i < queries.size(); i++) {
            QueryRef q = queries.get(i);
            NewsSearchProvider.QueryResult r = results.get(i);
            List<NewsProvider.Article> own = new ArrayList<>(r.articles());
            articles.put(q.id(), own);
            own.forEach(a -> ordered.add(new Attributed(q.id(), a)));
            byId.put(q.id(), r);
        }
        SearchPlan out;
        if (plan.getPipelines() != null && !plan.getPipelines().isEmpty()) {
            List<WildcardPipeline> pipelines = new ArrayList<>();
            for (WildcardPipeline p : plan.getPipelines()) {
                List<PipelineQuery> updated = new ArrayList<>();
                for (PipelineQuery q : p.getQueries()) {
                    NewsSearchProvider.QueryResult r = byId.get(q.getId());
                    updated.add(new PipelineQuery(q.getId(), q.getText(), r.status(), articles.get(q.getId()).size()));
                }
                pipelines.add(new WildcardPipeline(p.getId(), p.getKind(), p.getLabel(), p.getHeading(),
                    p.getQueryMode(), updated).level(p.getLevel()).topicKey(p.getTopicKey())
                    .candidatesConsidered(p.getCandidatesConsidered()).sourceIds(p.getSourceIds()));
            }
            out = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(), plan.getIntents(),
                plan.getQueries()).pipelines(pipelines);
        } else {
            List<SearchQuery> updated = new ArrayList<>();
            for (SearchQuery q : plan.getQueries()) {
                NewsSearchProvider.QueryResult r = byId.get(q.getId());
                updated.add(new SearchQuery(q.getId(), q.getIntentId(), q.getBucket(), q.getText(), r.status(),
                    articles.get(q.getId()).size()));
            }
            out = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(),
                plan.getIntents(), updated);
        }
        return new SearchOutcome(out, articles, ordered);
    }

    private record Candidate(NewsProvider.Article article, String url, List<String> queryIds, String topic,
                             Instant seen, java.util.SortedSet<String> pipelines) {
    }

    /** Stored sources in Evidence order, the plan with candidatesConsidered / sourceIds, and the distinct usable links. */
    public record Read(List<SourceRepository.Stored> sources, SearchPlan plan, int articlesConsidered) {
    }

    public List<SourceRepository.Stored> readSources(SearchOutcome outcome, HorizonCode horizon)
        throws InterruptedException {
        return readSources(outcome, horizon, () -> true);
    }

    /** {@code mayFetch} is the run guard, checked before each article fetch (no fetch once the run is not RUNNING). */
    public List<SourceRepository.Stored> readSources(SearchOutcome outcome, HorizonCode horizon,
                                                      BooleanSupplier mayFetch) throws InterruptedException {
        return read(outcome, horizon, mayFetch).sources();
    }

    /** FR-53: select per wildcard, fetch metadata of the kept articles only, number in Evidence order. */
    public Read read(SearchOutcome outcome, HorizonCode horizon, BooleanSupplier mayFetch)
        throws InterruptedException {
        Instant cutoff = clock.instant().minus(GoogleNewsSearch.timespanDays(horizon), ChronoUnit.DAYS);
        SearchPlan plan = outcome.plan();
        boolean hasPipelines = plan.getPipelines() != null && !plan.getPipelines().isEmpty();
        Map<String, SearchIntent> intents = new HashMap<>();
        plan.getIntents().forEach(in -> intents.put(in.getId(), in));
        Map<String, SearchQuery> legacy = new HashMap<>();
        plan.getQueries().forEach(q -> legacy.put(q.getId(), q));
        List<WildcardSelector.Pipeline> input = new ArrayList<>();
        if (hasPipelines) {
            for (WildcardPipeline p : plan.getPipelines()) {
                List<WildcardSelector.Query> queries = new ArrayList<>();
                for (PipelineQuery q : p.getQueries()) {
                    queries.add(new WildcardSelector.Query(q.getId(), q.getText(), itemsOf(outcome, q.getId(), q.getStatus())));
                }
                boolean general = p.getKind() == WildcardPipelineKind.GENERAL;
                input.add(new WildcardSelector.Pipeline(p.getId(), general ? WildcardSelector.GENERAL_SUBJECT : p.getLabel(),
                    general || p.getTopicKey() == null ? "major" : p.getTopicKey(), queries));
            }
        } else {
            for (SearchQuery q : plan.getQueries()) {
                WildcardSelector.Query query = new WildcardSelector.Query(q.getId(), q.getText(),
                    itemsOf(outcome, q.getId(), q.getStatus()));
                input.add(new WildcardSelector.Pipeline(q.getId(), "", topicOf(intents.get(q.getIntentId())), List.of(query)));
            }
        }
        WildcardSelector.Result selected = WildcardSelector.select(input, cutoff);
        List<Candidate> candidates = new ArrayList<>();
        for (WildcardSelector.Kept k : selected.kept()) {
            candidates.add(new Candidate(k.article(), k.url(), new ArrayList<>(k.queryIds()), k.topic(),
                k.article().publishedAt(), hasPipelines ? new java.util.TreeSet<>(k.pipelineIds()) : null));
        }
        Prepared[] prepared = new Prepared[candidates.size()];
        runBounded(fetchPool, fetchConcurrency, candidates.size(), i -> {
            prepared[i] = prepare(candidates.get(i), mayFetch);
            return null;
        });
        List<SourceRepository.Stored> out = new ArrayList<>();
        String[] ids = new String[prepared.length];
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
            ids[i] = stored.source().getId();
            out.add(stored);
        }
        SearchPlan result = plan;
        if (hasPipelines) {
            List<WildcardPipeline> pipelines = new ArrayList<>();
            for (WildcardPipeline p : plan.getPipelines()) {
                List<String> sourceIds = new ArrayList<>();
                for (int idx : selected.groups().get(p.getId())) {
                    if (ids[idx] != null) {
                        sourceIds.add(ids[idx]);
                    }
                }
                pipelines.add(new WildcardPipeline(p.getId(), p.getKind(), p.getLabel(), p.getHeading(),
                    p.getQueryMode(), p.getQueries()).level(p.getLevel()).topicKey(p.getTopicKey())
                    .candidatesConsidered(selected.candidatesConsidered().get(p.getId())).sourceIds(sourceIds));
            }
            result = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(), plan.getIntents(),
                plan.getQueries()).pipelines(pipelines);
        }
        return new Read(out, result, selected.articlesConsidered());
    }

    private static List<NewsProvider.Article> itemsOf(SearchOutcome outcome, String queryId, SearchQueryStatus status) {
        List<NewsProvider.Article> items = outcome.articles().get(queryId);
        return items == null ? List.of() : List.copyOf(items);
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
        if (c.pipelines() != null) {
            s.setPipelineIds(new ArrayList<>(c.pipelines()));
        }
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
