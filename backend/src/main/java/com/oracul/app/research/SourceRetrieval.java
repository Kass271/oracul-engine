package com.oracul.app.research;

import com.oracul.app.api.model.ArticleContentStatus;
import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.PipelineQuery;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import com.oracul.app.api.model.Source;
import com.oracul.app.api.model.SourceExcerpt;
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

    private final NewsSearchProvider search;
    private final ArticleRetriever retriever;
    private final SourceQualityTable quality;
    private final Clock clock;
    private final Duration searchWindow;
    private final Duration stageBudget;

    SourceRetrieval(NewsSearchProvider search, ArticleRetriever retriever, SourceQualityTable quality, Clock clock,
                    @Value("${oracul.search.search-window:PT60S}") Duration searchWindow,
                    @Value("${oracul.search.stage-budget:PT90S}") Duration stageBudget) {
        if (searchWindow.isZero() || searchWindow.isNegative()) {
            throw new IllegalStateException("oracul.search.search-window must be greater than zero");
        }
        if (stageBudget.isZero() || stageBudget.isNegative()) {
            throw new IllegalStateException("oracul.search.stage-budget must be greater than zero");
        }
        this.search = search;
        this.retriever = retriever;
        this.quality = quality;
        this.clock = clock;
        this.searchWindow = searchWindow;
        this.stageBudget = stageBudget;
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

    /** Stored sources in Evidence order, the plan with candidatesConsidered / sourceIds, and the distinct usable links. */
    public record Read(List<SourceRepository.Stored> sources, SearchPlan plan, int articlesConsidered,
                       int sourcesWithContent) {
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

    /** FR-53 / FR-54: select per wildcard, retrieve the kept articles, extract fragments, number in Evidence order. */
    public Read read(SearchOutcome outcome, HorizonCode horizon, BooleanSupplier mayFetch)
        throws InterruptedException {
        return read(outcome, horizon, clock.instant(), null, mayFetch);
    }

    public Read read(SearchOutcome outcome, HorizonCode horizon, Instant t0, Instant deadlineAt,
                     BooleanSupplier mayFetch) throws InterruptedException {
        Instant cutoff = clock.instant().minus(GoogleNewsSearch.timespanDays(horizon), ChronoUnit.DAYS);
        SearchPlan plan = outcome.plan();
        boolean hasPipelines = plan.getPipelines() != null && !plan.getPipelines().isEmpty();
        Map<String, SearchIntent> intents = new HashMap<>();
        plan.getIntents().forEach(in -> intents.put(in.getId(), in));
        List<WildcardSelector.Pipeline> input = new ArrayList<>();
        Map<String, String> queryTexts = new HashMap<>();
        if (hasPipelines) {
            for (WildcardPipeline p : plan.getPipelines()) {
                List<WildcardSelector.Query> queries = new ArrayList<>();
                for (PipelineQuery q : p.getQueries()) {
                    queries.add(new WildcardSelector.Query(q.getId(), q.getText(), itemsOf(outcome, q.getId(), q.getStatus())));
                    queryTexts.put(q.getId(), q.getText());
                }
                boolean general = p.getKind() == WildcardPipelineKind.GENERAL;
                input.add(new WildcardSelector.Pipeline(p.getId(), general ? WildcardSelector.GENERAL_SUBJECT : p.getLabel(),
                    general || p.getTopicKey() == null ? "major" : p.getTopicKey(), queries));
            }
        } else {
            for (SearchQuery q : plan.getQueries()) {
                WildcardSelector.Query query = new WildcardSelector.Query(q.getId(), q.getText(),
                    itemsOf(outcome, q.getId(), q.getStatus()));
                queryTexts.put(q.getId(), q.getText());
                input.add(new WildcardSelector.Pipeline(q.getId(), "", topicOf(intents.get(q.getIntentId())), List.of(query)));
            }
        }
        WildcardSelector.Result selected = WildcardSelector.select(input, cutoff);
        List<WildcardSelector.Kept> kept = selected.kept();
        List<String> links = kept.stream().map(WildcardSelector.Kept::url).toList();
        List<ArticleRetriever.Outcome> outcomes = links.isEmpty() ? List.of()
            : retriever.retrieveAll(links, SearchBudget.retrieval(clock, t0, stageBudget, deadlineAt), mayFetch);

        // ---- merge: kept sources whose stored url is equal are one source (the earliest stays) ----
        int n = kept.size();
        String[] urls = new String[n];
        int[] survivor = new int[n];
        Map<String, Integer> byUrl = new HashMap<>();
        for (int i = 0; i < n; i++) {
            ArticleRetriever.Outcome o = outcomes.get(i);
            String publisher = o.publisherUrl() == null ? null : UrlNormalizer.normalize(o.publisherUrl());
            urls[i] = publisher != null ? publisher : links.get(i);
            Integer first = byUrl.putIfAbsent(urls[i], i);
            survivor[i] = first == null ? i : first;
        }
        List<java.util.SortedSet<String>> pipelineSets = new ArrayList<>();
        List<java.util.SortedSet<String>> querySets = new ArrayList<>();
        for (WildcardSelector.Kept k : kept) {
            pipelineSets.add(new java.util.TreeSet<>(k.pipelineIds()));
            querySets.add(new java.util.TreeSet<>(k.queryIds()));
        }
        for (int i = 0; i < n; i++) {
            if (survivor[i] != i) {
                pipelineSets.get(survivor[i]).addAll(pipelineSets.get(i));
                querySets.get(survivor[i]).addAll(querySets.get(i));
            }
        }

        // ---- extraction terms per pipeline ----
        Map<String, java.util.Set<String>> labelTerms = new HashMap<>();
        Map<String, java.util.Set<String>> queryTerms = new HashMap<>();
        for (WildcardSelector.Pipeline p : input) {
            java.util.Set<String> label = WildcardSelector.tokens(p.labelText());
            java.util.Set<String> terms = new java.util.LinkedHashSet<>();
            for (WildcardSelector.Query q : p.queries()) {
                terms.addAll(WildcardSelector.tokens(q.text()));
            }
            terms.removeAll(label);
            labelTerms.put(p.id(), label);
            queryTerms.put(p.id(), terms);
        }

        List<SourceRepository.Stored> out = new ArrayList<>();
        String[] ids = new String[n];
        int withContent = 0;
        for (int i = 0; i < n; i++) {
            if (survivor[i] != i) {
                continue;
            }
            ArticleRetriever.Outcome o = outcomes.get(i);
            List<SourceExcerpt> excerpts = new ArrayList<>();
            boolean fragment = false;
            if (o.status() == ArticleRetriever.Status.PAGE_READ) {
                if (hasPipelines) {
                    for (String pid : pipelineSets.get(i)) {
                        List<String> f = FragmentExtractor.extract(o.body(), o.contentType(), labelTerms.get(pid),
                            queryTerms.get(pid));
                        if (!f.isEmpty()) {
                            excerpts.add(new SourceExcerpt(pid, f));
                        }
                    }
                    fragment = !excerpts.isEmpty();
                } else {
                    java.util.Set<String> terms = new java.util.LinkedHashSet<>();
                    for (String qid : querySets.get(i)) {
                        terms.addAll(WildcardSelector.tokens(queryTexts.getOrDefault(qid, "")));
                    }
                    fragment = !FragmentExtractor.extract(o.body(), o.contentType(), java.util.Set.of(), terms).isEmpty();
                }
            }
            ArticleContentStatus status = switch (o.status()) {
                case PAGE_READ -> fragment ? ArticleContentStatus.RETRIEVED : ArticleContentStatus.NO_TEXT;
                case DECODE_FAILED -> ArticleContentStatus.DECODE_FAILED;
                case PAGE_FAILED -> ArticleContentStatus.PAGE_FAILED;
                case REFUSED -> ArticleContentStatus.REFUSED;
                case NOT_ATTEMPTED -> ArticleContentStatus.NOT_ATTEMPTED;
            };
            if (status == ArticleContentStatus.RETRIEVED) {
                withContent++;
            }
            SourceRepository.Stored stored = build(out.size(), kept.get(i), o, urls[i], status, excerpts,
                hasPipelines ? pipelineSets.get(i) : null, querySets.get(i));
            ids[i] = stored.source().getId();
            out.add(stored);
        }
        String[] resolved = new String[n];
        for (int i = 0; i < n; i++) {
            resolved[i] = ids[survivor[i]];
        }
        SearchPlan result = plan;
        if (hasPipelines) {
            List<WildcardPipeline> pipelines = new ArrayList<>();
            for (WildcardPipeline p : plan.getPipelines()) {
                List<String> sourceIds = new ArrayList<>();
                for (int idx : selected.groups().get(p.getId())) {
                    if (!sourceIds.contains(resolved[idx])) {
                        sourceIds.add(resolved[idx]);
                    }
                }
                pipelines.add(new WildcardPipeline(p.getId(), p.getKind(), p.getLabel(), p.getHeading(),
                    p.getQueryMode(), p.getQueries()).level(p.getLevel()).topicKey(p.getTopicKey())
                    .candidatesConsidered(selected.candidatesConsidered().get(p.getId())).sourceIds(sourceIds));
            }
            result = new SearchPlan(plan.getQueryBudget(), plan.getExpansionMode(), plan.getBuckets(), plan.getIntents(),
                plan.getQueries()).pipelines(pipelines);
        }
        return new Read(out, result, selected.articlesConsidered(), withContent);
    }

    private static List<NewsProvider.Article> itemsOf(SearchOutcome outcome, String queryId, SearchQueryStatus status) {
        List<NewsProvider.Article> items = outcome.articles().get(queryId);
        return items == null ? List.of() : List.copyOf(items);
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

    private SourceRepository.Stored build(int index, WildcardSelector.Kept k, ArticleRetriever.Outcome o, String url,
                                           ArticleContentStatus status, List<SourceExcerpt> excerpts,
                                           java.util.SortedSet<String> pipelines, java.util.SortedSet<String> queryIds) {
        NewsProvider.Article a = k.article();
        String title = collapse(a.title());
        boolean read = o.status() == ArticleRetriever.Status.PAGE_READ;
        boolean fromPublisher = o.publisherUrl() != null && UrlNormalizer.normalize(o.publisherUrl()) != null;
        String host = UrlNormalizer.host(url);
        String snippet = a.snippet() == null ? "" : collapse(a.snippet());
        if (snippet.length() > MAX_SUMMARY) {
            snippet = snippet.substring(0, MAX_SUMMARY);
        }
        String fallback = snippet.isEmpty() ? title : snippet;
        String description = read && o.description() != null ? collapse(o.description()) : "";
        String summary = description.isEmpty() ? fallback
            : description.length() > MAX_SUMMARY ? description.substring(0, MAX_SUMMARY) : description;
        String siteName = read && o.siteName() != null ? o.siteName().trim() : "";
        String sourceText = a.sourceName() == null ? "" : a.sourceName().trim();
        String sourceHost = absoluteHost(a.sourceUrl());
        String publisherUrl = sourceHost != null ? a.sourceUrl().trim() : null;
        String publisher = !siteName.isEmpty() ? siteName : !sourceText.isEmpty() ? sourceText
            : sourceHost != null ? sourceHost : host;
        var cls = quality.classify(sourceHost != null ? sourceHost : host);

        Source s = new Source();
        s.setId(String.format("S%03d", index + 1));
        s.setUrl(URI.create(url));
        s.setPublisher(publisher);
        s.setTitle(title);
        s.setPublishedAt(a.publishedAt() == null ? null : a.publishedAt().atOffset(ZoneOffset.UTC));
        s.setRetrievedAt(o.endedAt().truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC));
        s.setSummary(summary);
        s.setTopic(k.topic());
        s.setEntities(new ArrayList<>());
        s.setSourceType(cls.type());
        s.setSourceQuality(cls.quality());
        s.setMetadataFetched(read);
        s.setQueryIds(new ArrayList<>(queryIds));
        if (pipelines != null) {
            s.setPipelineIds(new ArrayList<>(pipelines));
        }
        if (publisherUrl != null) {
            s.setPublisherUrl(URI.create(publisherUrl));
        }
        s.setContentStatus(status);
        s.setExcerpts(excerpts);
        if (fromPublisher && !host.isEmpty()) {
            s.setPublisherHost(host);
        }
        return new SourceRepository.Stored(s, null);
    }

    private static String collapse(String s) {
        return s == null ? "" : s.trim().replaceAll("\\s+", " ");
    }
}
