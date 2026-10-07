package com.oracul.app.research;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** FR-53: pure source selection per wildcard (candidates, relevance score, top 4, cap 30 by round robin, groups). */
public final class WildcardSelector {

    public static final int PER_PIPELINE = 4;
    public static final int MAX_SOURCES = 30;
    public static final String GENERAL_SUBJECT = "major current world events";
    static final Set<String> STOP_WORDS = Set.of("the", "and", "for", "with", "from", "that", "this", "are", "was", "were",
        "has", "have", "had", "its", "into", "over", "about", "after", "than", "then", "will", "what", "when", "which", "who",
        "why", "how", "new", "latest", "news", "today", "says", "said");
    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");

    private WildcardSelector() {
    }

    public record Query(String id, String text, List<NewsProvider.Article> items) {
    }

    public record Pipeline(String id, String labelText, String topic, List<Query> queries) {
    }

    public record Candidate(String url, NewsProvider.Article article, List<String> queryIds, int bestPosition,
                            String bestQueryId, int score) {
    }

    public record Kept(String url, NewsProvider.Article article, List<String> pipelineIds, List<String> queryIds,
                       String topic) {
    }

    public record Result(List<Kept> kept, Map<String, Integer> candidatesConsidered, Map<String, List<Integer>> groups,
                         int articlesConsidered) {
    }

    static Set<String> tokens(String text) {
        Set<String> out = new LinkedHashSet<>();
        if (text == null) {
            return out;
        }
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        while (m.find()) {
            String t = m.group();
            if (t.length() >= 3 && !STOP_WORDS.contains(t)) {
                out.add(t);
            }
        }
        return out;
    }

    private static final class Acc {
        NewsProvider.Article article;
        final TreeSet<String> queryIds = new TreeSet<>();
        int bestPosition = Integer.MAX_VALUE;
        String bestQueryId;
    }

    static List<Candidate> rank(Pipeline p, Instant cutoff) {
        Map<String, Acc> byUrl = new LinkedHashMap<>();
        Set<String> queryTerms = new LinkedHashSet<>();
        for (Query q : p.queries()) {
            queryTerms.addAll(tokens(q.text()));
            List<NewsProvider.Article> items = q.items() == null ? List.of() : q.items();
            for (int i = 0; i < items.size(); i++) {
                NewsProvider.Article a = items.get(i);
                if (a == null) {
                    continue;
                }
                String url = UrlNormalizer.normalize(a.url());
                if (url == null || a.title() == null || a.title().isBlank()) {
                    continue;
                }
                if (a.publishedAt() != null && cutoff != null && a.publishedAt().isBefore(cutoff)) {
                    continue;
                }
                int position = i + 1;
                Acc acc = byUrl.computeIfAbsent(url, k -> new Acc());
                if (acc.article == null) {
                    acc.article = a;
                }
                acc.queryIds.add(q.id());
                if (position < acc.bestPosition
                    || position == acc.bestPosition && q.id().compareTo(acc.bestQueryId) < 0) {
                    acc.bestPosition = position;
                    acc.bestQueryId = q.id();
                }
            }
        }
        Set<String> labelTerms = tokens(p.labelText());
        queryTerms.removeAll(labelTerms);
        List<Candidate> out = new ArrayList<>();
        for (Map.Entry<String, Acc> e : byUrl.entrySet()) {
            Acc acc = e.getValue();
            NewsProvider.Article a = acc.article;
            Set<String> text = tokens(a.title() + " " + (a.snippet() == null ? "" : a.snippet()));
            int label = 0;
            for (String t : labelTerms) {
                if (text.contains(t)) {
                    label++;
                }
            }
            int query = 0;
            for (String t : queryTerms) {
                if (text.contains(t)) {
                    query++;
                }
            }
            int score = 3 * label + query + 2 * (acc.queryIds.size() - 1);
            out.add(new Candidate(e.getKey(), a, List.copyOf(acc.queryIds), acc.bestPosition, acc.bestQueryId, score));
        }
        out.sort(Comparator.comparingInt(Candidate::score).reversed().thenComparingInt(Candidate::bestPosition)
            .thenComparing(Candidate::bestQueryId).thenComparing(Candidate::url));
        return out;
    }

    public static Result select(List<Pipeline> pipelines, Instant cutoff) {
        int n = pipelines.size();
        List<List<Candidate>> ranked = new ArrayList<>();
        Map<String, Integer> considered = new LinkedHashMap<>();
        Map<String, NewsProvider.Article> firstArticle = new LinkedHashMap<>();
        Map<String, TreeSet<String>> pipelinesOf = new java.util.HashMap<>();
        Map<String, TreeSet<String>> queriesOf = new java.util.HashMap<>();
        Map<String, String> topicOfPipeline = new LinkedHashMap<>();
        for (Pipeline p : pipelines) {
            List<Candidate> r = rank(p, cutoff);
            ranked.add(r);
            considered.put(p.id(), r.size());
            topicOfPipeline.put(p.id(), p.topic());
            for (Candidate c : r) {
                firstArticle.putIfAbsent(c.url(), c.article());
                pipelinesOf.computeIfAbsent(c.url(), k -> new TreeSet<>()).add(p.id());
                queriesOf.computeIfAbsent(c.url(), k -> new TreeSet<>()).addAll(c.queryIds());
            }
        }
        Set<String> kept = new HashSet<>();
        outer:
        for (int round = 0; round < PER_PIPELINE; round++) {
            for (int i = 0; i < n; i++) {
                List<Candidate> r = ranked.get(i);
                if (round >= r.size() || round >= PER_PIPELINE) {
                    continue;
                }
                String url = r.get(round).url();
                if (kept.contains(url)) {
                    continue;
                }
                if (kept.size() >= MAX_SOURCES) {
                    break outer;
                }
                kept.add(url);
            }
        }
        Map<String, Integer> index = new LinkedHashMap<>();
        List<Kept> out = new ArrayList<>();
        List<List<String>> groupUrls = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            List<String> g = new ArrayList<>();
            for (Candidate c : ranked.get(i)) {
                if (kept.contains(c.url())) {
                    g.add(c.url());
                    if (!index.containsKey(c.url())) {
                        index.put(c.url(), out.size());
                        List<String> pids = new ArrayList<>(pipelinesOf.get(c.url()));
                        out.add(new Kept(c.url(), firstArticle.get(c.url()), pids, new ArrayList<>(queriesOf.get(c.url())),
                            topicOfPipeline.get(pids.get(0))));
                    }
                }
            }
            groupUrls.add(g);
        }
        Map<String, List<Integer>> groups = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            List<Integer> g = new ArrayList<>();
            groupUrls.get(i).forEach(u -> g.add(index.get(u)));
            groups.put(pipelines.get(i).id(), g);
        }
        return new Result(out, considered, groups, firstArticle.size());
    }
}
