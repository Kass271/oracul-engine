package com.oracul.app.research;

import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.api.model.HorizonCode;
import com.oracul.app.api.model.QueryBucket;
import com.oracul.app.api.model.SearchIntent;
import com.oracul.app.api.model.SearchPlan;
import com.oracul.app.api.model.SearchQuery;
import com.oracul.app.api.model.SearchQueryStatus;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * phase-02 news-search.md FR-46 "At most 30 sources per run", exhaustive over the count and round-robin classes and the
 * invariants of the spec. The stage seams are the existing ones: {@code SourceRetrieval.search} (one Google request per
 * query, FR-52: the stub answers the query of topic t with the candidates of topic t, so a candidate belongs to the
 * query that returned it and the arrival order is plan order, then feed order) followed by {@code readSources}.
 * Candidates are told apart by topic (intent topicKey of their query) and quality (publisher domain through the quality
 * table).
 */
// @trace FR-46, FR-52
class SourceCapIT extends AbstractNewsSearchIT {

    private static final AtomicInteger RUN = new AtomicInteger();

    /** The usable candidate j of the test: topic index (-1 = no topic), publisher domain (quality). */
    record C(String name, int topic, String domain) {
        double quality() {
            return switch (domain) {
                case "who.int" -> 0.95;
                case "reuters.com" -> 0.85;
                case "example-news.com" -> 0.6;
                default -> 0.35; // medium.com
            };
        }
    }

    /** The domains in the order of a repeating quality pattern. */
    private static final String[] PATTERN = {"reuters.com", "medium.com", "who.int", "example-news.com"};

    /** No wait for runs of other classes: every article of this class carries a unique name prefix. */
    @Override
    @org.junit.jupiter.api.BeforeEach
    void resetNews() {
        news.reset();
    }

    private static String element(int topic) {
        return "tcap" + topic + " news";
    }

    private static String topicKey(int topic) {
        return "topic-" + topic;
    }

    /** Result of one run of the stage pair; {@code arrival} = the candidates in the order the search delivered them. */
    record Kept(List<String> names, List<Map<String, Object>> sources, int fetches, int retrieved, List<C> arrival) {}

    /**
     * Runs search + readSources over the candidates. The query of topic t (Q0(t+1)) answers the candidates of topic t in list
     * order, then the candidates listed in {@code extra} with second topic t; the no-topic query comes after the topic
     * queries. {@code extra} lists, per candidate name, a second topic whose query lists the same URL.
     */
    private Kept stage(List<C> cands, int topics, Map<String, Integer> extra) throws Exception {
        String prefix = "cap" + RUN.incrementAndGet() + "-";
        List<SearchIntent> intents = new ArrayList<>();
        List<SearchQuery> queries = new ArrayList<>();
        SearchPlan template = PlanSupport.plan(PlanSupport.cfgA(), 20);
        boolean noTopic = cands.stream().anyMatch(c -> c.topic() < 0);
        for (int t = 0; t < topics; t++) {
            SearchIntent in = new SearchIntent(String.format("I%02d", t + 1), QueryBucket.WILDCARD, "topic " + t, List.of());
            in.setTopicKey(topicKey(t));
            intents.add(in);
            queries.add(new SearchQuery(String.format("Q%02d", t + 1), in.getId(), QueryBucket.WILDCARD, element(t),
                SearchQueryStatus.EMPTY, 0));
        }
        if (noTopic) { // a query whose intent is not part of the plan: its sources have no topic
            queries.add(new SearchQuery(String.format("Q%02d", queries.size() + 1), "I99", QueryBucket.WILDCARD, "tcapnone news",
                SearchQueryStatus.EMPTY, 0));
        }
        SearchPlan plan = new SearchPlan(template.getQueryBudget(), template.getExpansionMode(), template.getBuckets(), intents, queries);

        Instant day = Instant.now().minus(1, ChronoUnit.DAYS);
        // per query text: its answer (own candidates, then the extras) and the arrival order of first appearances
        Map<String, List<String>> answers = new LinkedHashMap<>();
        List<C> arrival = new ArrayList<>();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (SearchQuery q : queries) {
            int t = q.getText().equals("tcapnone news") ? -1 : Integer.parseInt(q.getText().substring("tcap".length(), q.getText().indexOf(' ')));
            List<String> entries = new ArrayList<>();
            for (C c : cands) {
                if (c.topic() == t) {
                    entries.add(StubNews.rssItem(q.getText() + " story " + c.name(), news.baseUrl() + "/rss/articles/" + prefix + c.name(),
                        StubNews.pubDate(day), c.domain(), "https://" + c.domain()));
                    if (seen.add(c.name())) arrival.add(c);
                }
            }
            for (C c : cands) {
                if (extra.containsKey(c.name()) && extra.get(c.name()) == t) {
                    entries.add(StubNews.rssItem(q.getText() + " again " + c.name(), news.baseUrl() + "/rss/articles/" + prefix + c.name(),
                        StubNews.pubDate(day), c.domain(), "https://" + c.domain()));
                    if (seen.add(c.name())) arrival.add(c);
                }
            }
            answers.put(q.getText(), entries);
        }
        news.responder = req -> StubNews.rss(answers.getOrDefault(req.elements().get(0), List.of()).toArray(String[]::new));

        var outcome = search(plan);
        assertThat(news.requests).as("one request per planned query").hasSize(queries.size());
        int retrieved = outcome.articlesRetrieved();
        var stored = retrieval.readSources(outcome, HorizonCode._1Y);
        List<String> names = new ArrayList<>();
        List<Map<String, Object>> sources = new ArrayList<>();
        for (var st : stored) {
            var s = st.source();
            String url = s.getUrl().toString();
            names.add(url.substring(url.lastIndexOf('/') + 1 + prefix.length()));
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("topic", s.getTopic());
            m.put("queryIds", s.getQueryIds());
            m.put("url", url);
            sources.add(m);
        }
        int fetches = (int) news.articleRequests.stream().filter(n -> n.startsWith(prefix)).count();
        return new Kept(names, sources, fetches, retrieved, arrival);
    }

    private Kept stage(List<C> cands, int topics) throws Exception {
        return stage(cands, topics, Map.of());
    }

    private static List<CapOracle.Cand> oracleInput(List<C> cands) {
        List<CapOracle.Cand> out = new ArrayList<>();
        for (C c : cands) out.add(new CapOracle.Cand(c.name(), c.topic() < 0 ? null : topicKey(c.topic()), c.quality()));
        return out;
    }

    /** The invariants of FR-46 for any input: kept ⊆ usable, size, order, ids, topic spread, top-ranked inside a topic, fetches. */
    private void assertInvariants(List<C> cands, Kept kept) {
        int n = cands.size();
        int k = Math.min(n, 30);
        List<String> usable = cands.stream().map(C::name).toList();
        assertThat(usable).containsAll(kept.names());
        assertThat(kept.names()).as("|kept| = min(n, 30)").hasSize(k);
        assertThat(kept.names()).as("no URL twice").doesNotHaveDuplicates();
        // kept candidates keep the arrival order
        List<Integer> arrivalIdx = kept.names().stream().map(usable::indexOf).toList();
        assertThat(arrivalIdx).as("arrival order").isSorted();
        // S-ids without gaps
        for (int i = 0; i < k; i++) assertThat(kept.sources().get(i).get("id")).isEqualTo(String.format("S%03d", i + 1));
        assertThat(kept.fetches()).as("the article fetcher is called exactly k times").isEqualTo(k);
        assertThat(kept.retrieved()).as("articlesRetrieved counts every raw entry").isGreaterThanOrEqualTo(n);

        Map<Integer, List<C>> byTopic = new LinkedHashMap<>();
        for (C c : cands) byTopic.computeIfAbsent(c.topic(), x -> new ArrayList<>()).add(c);
        Map<Integer, Long> keptPer = new LinkedHashMap<>();
        for (int t : byTopic.keySet()) {
            keptPer.put(t, byTopic.get(t).stream().filter(c -> kept.names().contains(c.name())).count());
        }
        for (int t : byTopic.keySet()) {
            boolean unkept = keptPer.get(t) < byTopic.get(t).size();
            if (!unkept) continue;
            for (int u : byTopic.keySet()) {
                if (keptPer.get(u) < byTopic.get(u).size()) {
                    assertThat(Math.abs(keptPer.get(t) - keptPer.get(u))).as("topics " + t + " and " + u + " both have unkept candidates")
                        .isLessThanOrEqualTo(1);
                }
            }
            // the kept ones of a topic are its top-ranked: quality descending, ties by arrival
            List<C> ranked = new ArrayList<>(byTopic.get(t));
            ranked.sort((a, b) -> {
                int c = Double.compare(b.quality(), a.quality());
                return c != 0 ? c : Integer.compare(usable.indexOf(a.name()), usable.indexOf(b.name()));
            });
            List<String> expectedTop = ranked.subList(0, keptPer.get(t).intValue()).stream().map(C::name).toList();
            List<String> actualKept = byTopic.get(t).stream().map(C::name).filter(kept.names()::contains).toList();
            assertThat(actualKept).as("kept of topic " + t + " are its top-ranked").containsExactlyInAnyOrderElementsOf(expectedTop);
        }
        // every kept source carries its topic and its query
        for (int i = 0; i < k; i++) {
            C c = cands.get(usable.indexOf(kept.names().get(i)));
            Map<String, Object> s = kept.sources().get(i);
            assertThat(s.get("topic")).isEqualTo(c.topic() < 0 ? null : topicKey(c.topic()));
        }
    }

    private static List<C> generate(int n, int topics, boolean blocks) {
        List<C> out = new ArrayList<>();
        for (int j = 0; j < n; j++) {
            int topic = blocks ? Math.min(topics - 1, j * topics / Math.max(n, 1)) : j % topics;
            out.add(new C("c" + j, topic, PATTERN[(j * 7 + j / 3) % PATTERN.length]));
        }
        return out;
    }

    static Stream<Arguments> countClasses() {
        List<Arguments> out = new ArrayList<>();
        for (int n : new int[] {0, 1, 29, 30, 31, 60, 100}) {
            for (int topics : new int[] {4, 5}) {
                for (boolean blocks : new boolean[] {false, true}) {
                    out.add(Arguments.of(n, topics, blocks));
                }
            }
        }
        return out.stream();
    }

    // n = 0, 1, 29, 30 (all kept), 31, 60, 100 (30 kept): selection equals the specification, every invariant holds
    @ParameterizedTest(name = "n={0}, {1} topics, blocks={2}")
    @MethodSource("countClasses")
    void theSelectionFollowsTheSpecForEveryUsableCount(int n, int topics, boolean blocks) throws Exception {
        List<C> cands = generate(n, topics, blocks);
        Kept kept = stage(cands, topics);
        List<C> arrival = kept.arrival(); // plan order (topic by topic), then feed order
        assertThat(arrival).as("every candidate arrives exactly once").hasSize(n).containsExactlyInAnyOrderElementsOf(cands);
        List<String> expected = CapOracle.select(oracleInput(arrival)).stream().map(i -> arrival.get(i).name()).toList();
        assertThat(kept.names()).as("kept candidates").containsExactlyElementsOf(expected);
        if (n <= 30) {
            assertThat(kept.names()).as("n <= 30: all kept, in arrival order").containsExactlyElementsOf(arrival.stream().map(C::name).toList());
        }
        assertInvariants(kept.arrival(), kept);
    }

    private static List<C> repeat(List<C> out, int topic, int count, String domain) {
        for (int i = 0; i < count; i++) out.add(new C("t" + topic + "-" + i + "-" + out.size(), topic, domain));
        return out;
    }

    private static long keptOfTopic(Kept kept, int topic) {
        return kept.sources().stream().filter(s -> topicKey(topic).equals(s.get("topic"))).count();
    }

    @Test
    void oneTopicOfFortyKeepsItsThirtyBestByQualityThenArrival() throws Exception {
        List<C> cands = new ArrayList<>();
        for (int j = 0; j < 40; j++) cands.add(new C("q" + j, 0, PATTERN[j % 4]));
        Kept kept = stage(cands, 4);
        // 10 candidates of each quality: the ten blog ones (0.35) are the worst
        assertThat(kept.names()).hasSize(30);
        for (String n : kept.names()) assertThat(cands.get(Integer.parseInt(n.substring(1))).domain()).isNotEqualTo("medium.com");
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void topicsOf40And2And1GiveTwentySevenTwoAndOne() throws Exception {
        List<C> cands = new ArrayList<>();
        // first appearance order A, B, C; the rest of A follows
        cands.add(new C("a0", 0, "reuters.com"));
        cands.add(new C("b0", 1, "reuters.com"));
        cands.add(new C("c0", 2, "reuters.com"));
        for (int i = 1; i < 40; i++) cands.add(new C("a" + i, 0, "reuters.com"));
        cands.add(new C("b1", 1, "reuters.com"));
        Kept kept = stage(cands, 4);
        assertThat(keptOfTopic(kept, 2)).as("C").isEqualTo(1);
        assertThat(keptOfTopic(kept, 1)).as("B").isEqualTo(2);
        assertThat(keptOfTopic(kept, 0)).as("A").isEqualTo(27);
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void thirtyOneTopicsWithOneCandidateKeepTheFirstThirtyTopics() throws Exception {
        List<C> cands = new ArrayList<>();
        for (int t = 0; t < 31; t++) cands.add(new C("only" + t, t, "reuters.com"));
        Kept kept = stage(cands, 31);
        assertThat(kept.names()).containsExactlyElementsOf(cands.subList(0, 30).stream().map(C::name).toList());
        assertInvariants(kept.arrival(), kept);
    }

    static Stream<Arguments> evenSplits() {
        return Stream.of(
            Arguments.of("A(20) B(20)", new int[] {20, 20}, new int[] {15, 15}),
            Arguments.of("A(20) B(20) C(20)", new int[] {20, 20, 20}, new int[] {10, 10, 10}),
            Arguments.of("A(16) B(16) with 30", new int[] {16, 16}, new int[] {15, 15}),
            Arguments.of("A(11) B(10) C(10) D(10)", new int[] {11, 10, 10, 10}, new int[] {8, 8, 7, 7}),
            Arguments.of("A(40) B(40) C(40) D(40)", new int[] {40, 40, 40, 40}, new int[] {8, 8, 7, 7}),
            Arguments.of("A(2) B(40) C(40)", new int[] {2, 40, 40}, new int[] {2, 14, 14}));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("evenSplits")
    void roundRobinSpreadsTheThirtyOverTheTopics(String name, int[] sizes, int[] expected) throws Exception {
        List<C> cands = new ArrayList<>();
        int most = 0;
        for (int s : sizes) most = Math.max(most, s);
        for (int i = 0; i < most; i++) { // arrival: the topics alternate, in the order of the sizes
            for (int t = 0; t < sizes.length; t++) {
                if (i < sizes[t]) cands.add(new C("r" + t + "-" + i, t, "reuters.com"));
            }
        }
        Kept kept = stage(cands, sizes.length);
        for (int t = 0; t < sizes.length; t++) assertThat(keptOfTopic(kept, t)).as(name + " topic " + t).isEqualTo(expected[t]);
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void equalQualityKeepsTheArrivalOrder() throws Exception {
        List<C> cands = repeat(new ArrayList<>(), 0, 31, "reuters.com");
        Kept kept = stage(cands, 4);
        assertThat(kept.names()).containsExactlyElementsOf(cands.subList(0, 30).stream().map(C::name).toList());
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void aLaterHighQualityCandidateBeatsAnEarlierOneOfTheSameTopic() throws Exception {
        List<C> cands = repeat(new ArrayList<>(), 0, 30, "example-news.com");
        cands.add(new C("late-who", 0, "who.int"));
        Kept kept = stage(cands, 4);
        assertThat(kept.names()).contains("late-who");
        assertThat(kept.names()).as("the last of the equal 0.6 candidates is the one dropped")
            .doesNotContain(cands.get(29).name());
        assertThat(kept.names()).containsAll(cands.subList(0, 29).stream().map(C::name).toList());
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void candidatesWithoutATopicFormABucketOfTheirOwn() throws Exception {
        List<C> cands = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            cands.add(new C("none" + i, -1, "reuters.com"));
            cands.add(new C("x" + i, 0, "reuters.com"));
        }
        Kept kept = stage(cands, 4);
        assertThat(kept.sources().stream().filter(s -> s.get("topic") == null).count()).isEqualTo(15);
        assertThat(keptOfTopic(kept, 0)).isEqualTo(15);
        assertInvariants(kept.arrival(), kept);
    }

    @Test
    void aKeptSourceKeepsEveryQueryThatListedItsUrl() throws Exception {
        List<C> cands = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            cands.add(new C("p" + i, 0, "reuters.com"));
            cands.add(new C("q" + i, 1, "reuters.com"));
        }
        // p0 is also listed by the (later) query of topic 1, q0 by the (later) query of topic 2: each URL is one source that
        // lists exactly the queries whose own answer held it, and its topic is the topic of the first of them
        Kept kept = stage(cands, 4, Map.of("p0", 1, "q0", 2));
        assertThat(kept.names()).hasSize(30).contains("p0", "q0");
        Map<String, Object> p0 = kept.sources().get(kept.names().indexOf("p0"));
        Map<String, Object> q0 = kept.sources().get(kept.names().indexOf("q0"));
        assertThat(p0.get("queryIds")).as("pre-cap queryIds, sorted").isEqualTo(List.of("Q01", "Q02"));
        assertThat(q0.get("queryIds")).isEqualTo(List.of("Q02", "Q03"));
        assertThat(p0.get("topic")).as("topic of the first query").isEqualTo(topicKey(0));
        assertThat(q0.get("topic")).isEqualTo(topicKey(1));
        for (int i = 0; i < kept.names().size(); i++) {
            if (!List.of("p0", "q0").contains(kept.names().get(i))) {
                assertThat(((List<?>) kept.sources().get(i).get("queryIds"))).hasSize(1);
            }
        }
    }
}
