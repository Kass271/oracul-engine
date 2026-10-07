package com.oracul.app.research;

import static com.oracul.app.research.SelectorApi.CUTOFF;
import static com.oracul.app.research.SelectorApi.NOW;
import static org.assertj.core.api.Assertions.assertThat;

import com.oracul.app.research.SelectorApi.C;
import com.oracul.app.research.SelectorApi.Item;
import com.oracul.app.research.SelectorApi.K;
import com.oracul.app.research.SelectorApi.P;
import com.oracul.app.research.SelectorApi.Q;
import com.oracul.app.research.SelectorApi.Sel;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * FR-53 "Source selection per wildcard": the pure {@code WildcardSelector} (article-retrieval.md "Slice 05_wildcard-selection -
 * delta"). Inputs are built in code; the production class is reached through {@link SelectorApi} (it does not exist before the
 * slice is built). Covers the Ranges and invariants line: candidates per pipeline, pipelines x candidates, shared candidates, score
 * classes, filter classes, determinism, and a property-style loop over 0...33 pipelines x 0...12 candidates.
 */
// @trace FR-53
class WildcardSelectorTest {

    /** The stop words of FR-53 step 2. */
    private static final List<String> STOP_WORDS = List.of("the", "and", "for", "with", "from", "that", "this", "are", "was", "were",
        "has", "have", "had", "its", "into", "over", "about", "after", "than", "then", "will", "what", "when", "which", "who", "why",
        "how", "new", "latest", "news", "today", "says", "said");

    // ---- builders ---------------------------------------------------------------------------------------------

    private static String wid(int j) {
        return String.format("W%02d", j);
    }

    private static String qid(int n) {
        return String.format("Q%02d", n);
    }

    private static String text(int j, int i) {
        return wid(j) + " stub query " + i;
    }

    /** An item with a title that matches no label or query term of the pipelines below (score 0). */
    private static Item plain(String link, String id) {
        return Item.of(link, "Plain headline " + id);
    }

    /** Pipeline j with one query Q<j> holding {@code n} distinct plain candidates (own links), all of score 0: relevance = feed order. */
    private static P distinct(int j, int n) {
        List<Item> items = new ArrayList<>();
        for (int i = 1; i <= n; i++) items.add(plain("http://own.example/" + wid(j) + "/" + i, wid(j).toLowerCase() + "-" + i));
        return P.of(wid(j), "Zebra crossing", "topic-" + j, new Q(qid(j), text(j, 1), items));
    }

    private static List<P> distinctPipelines(int pipelines, int candidates) {
        List<P> out = new ArrayList<>();
        for (int j = 1; j <= pipelines; j++) out.add(distinct(j, candidates));
        return out;
    }

    private static List<Integer> sizes(Sel s, int pipelines) {
        List<Integer> out = new ArrayList<>();
        for (int j = 1; j <= pipelines; j++) out.add(s.groups().get(wid(j)).size());
        return out;
    }

    private static List<Integer> repeat(int value, int times) {
        return java.util.Collections.nCopies(times, value);
    }

    private static List<Integer> concat(List<Integer> a, List<Integer> b) {
        List<Integer> out = new ArrayList<>(a);
        out.addAll(b);
        return out;
    }

    // ---- constants and tokens ---------------------------------------------------------------------------------

    @Test
    void theConstantsAreTheSpecifiedOnes() {
        assertThat(SelectorApi.constant("PER_PIPELINE")).isEqualTo(4);
        assertThat(SelectorApi.constant("MAX_SOURCES")).isEqualTo(30);
        assertThat(SelectorApi.constant("GENERAL_SUBJECT")).isEqualTo("major current world events");
    }

    static Stream<Arguments> tokenCases() {
        return Stream.of(
            Arguments.of("New pandemic", List.of("pandemic")),
            Arguments.of("Energy crisis", List.of("energy", "crisis")),
            Arguments.of("ENERGY Energy energy", List.of("energy")),
            Arguments.of("AI is on", List.of()),
            Arguments.of("COVID-19 vaccine, 2026!", List.of("covid", "vaccine", "2026")),
            Arguments.of("x1 abc12 a_b", List.of("abc12")),
            Arguments.of("", List.of()),
            Arguments.of("Major current world events", List.of("major", "current", "world", "events")),
            Arguments.of("power grid strain", List.of("power", "grid", "strain")));
    }

    @ParameterizedTest(name = "tokens of \"{0}\"")
    @MethodSource("tokenCases")
    void tokensAreLowerCaseAlphanumericWordsOfAtLeastThreeCharactersWithoutStopWords(String text, List<String> expected) {
        assertThat(List.copyOf(SelectorApi.tokens(text))).containsExactlyElementsOf(expected);
    }

    @ParameterizedTest(name = "stop word {0}")
    @ValueSource(strings = {"the", "and", "for", "with", "from", "that", "this", "are", "was", "were", "has", "have", "had", "its",
        "into", "over", "about", "after", "than", "then", "will", "what", "when", "which", "who", "why", "how", "new", "latest",
        "news", "today", "says", "said"})
    void everyStopWordIsDroppedInAnyLetterCase(String word) {
        assertThat(SelectorApi.tokens(word)).isEmpty();
        assertThat(SelectorApi.tokens(word.toUpperCase())).isEmpty();
        assertThat(List.copyOf(SelectorApi.tokens(word + " wordx"))).containsExactly("wordx");
    }

    // ---- (a) selected per pipeline ----------------------------------------------------------------------------

    static Stream<Arguments> perPipeline() {
        return Stream.of(Arguments.of(0, 0), Arguments.of(1, 1), Arguments.of(3, 3), Arguments.of(4, 4), Arguments.of(5, 4),
            Arguments.of(40, 4));
    }

    @ParameterizedTest(name = "{0} usable candidates -> {1} selected")
    @MethodSource("perPipeline")
    void aPipelineSelectsAtMostFourCandidates(int candidates, int selected) {
        Sel s = SelectorApi.select(List.of(distinct(1, candidates)));
        assertThat(s.kept()).hasSize(selected);
        assertThat(s.groups().get("W01")).as("the group holds the selected ones in feed order").containsExactlyElementsOf(
            IntStream.range(0, selected).boxed().toList());
        assertThat(s.candidatesConsidered().get("W01")).isEqualTo(candidates);
        assertThat(s.articlesConsidered()).isEqualTo(candidates);
        // the first four in relevance order = the first four of the feed here (every score ties at 0)
        List<String> expected = IntStream.rangeClosed(1, selected).mapToObj(i -> "http://own.example/W01/" + i).toList();
        assertThat(s.urls()).containsExactlyElementsOf(expected);
    }

    // ---- (b) pipelines x candidates ---------------------------------------------------------------------------

    static Stream<Arguments> matrix() {
        return Stream.of(
            Arguments.of(1, 40, 4, List.of(4)),
            Arguments.of(9, 4, 30, concat(repeat(4, 3), repeat(3, 6))),
            Arguments.of(9, 10, 30, concat(repeat(4, 3), repeat(3, 6))),
            Arguments.of(7, 4, 28, repeat(4, 7)),
            Arguments.of(8, 4, 30, concat(repeat(4, 6), repeat(3, 2))),
            Arguments.of(30, 1, 30, repeat(1, 30)),
            Arguments.of(31, 1, 30, concat(repeat(1, 30), repeat(0, 1))),
            Arguments.of(33, 4, 30, concat(repeat(1, 30), repeat(0, 3))));
    }

    @ParameterizedTest(name = "{0} pipelines x {1} distinct candidates -> {2} kept")
    @MethodSource("matrix")
    void theCapOfThirtyIsARoundRobinOverThePipelines(int pipelines, int candidates, int kept, List<Integer> groupSizes) {
        Sel s = SelectorApi.select(distinctPipelines(pipelines, candidates));
        assertThat(s.kept()).hasSize(kept);
        assertThat(sizes(s, pipelines)).as("group sizes in pipeline order").isEqualTo(groupSizes);
        assertThat(s.groups().keySet()).as("every pipeline is present, in plan order").containsExactlyElementsOf(
            IntStream.rangeClosed(1, pipelines).mapToObj(WildcardSelectorTest::wid).toList());
        for (int j = 1; j <= pipelines; j++) assertThat(s.candidatesConsidered().get(wid(j))).isEqualTo(candidates);
        assertThat(s.articlesConsidered()).isEqualTo(pipelines * candidates);
        assertThat(s.urls()).doesNotHaveDuplicates();
        if (pipelines == 31) assertThat(s.groups().get("W31")).as("W31 kept none, but considered 1").isEmpty();
        if (pipelines <= 30) {
            for (int j = 1; j <= pipelines; j++) assertThat(s.groups().get(wid(j))).as(wid(j) + " keeps at least one").isNotEmpty();
        }
    }

    @Test
    void aPipelineWithoutCandidatesBetweenOthersHasAnEmptyGroupAndTheOthersAreUnchanged() {
        Sel s = SelectorApi.select(List.of(distinct(1, 4), distinct(2, 0), distinct(3, 4)));
        assertThat(s.kept()).hasSize(8);
        assertThat(s.groups().get("W02")).isEmpty();
        assertThat(s.candidatesConsidered()).containsEntry("W02", 0).containsEntry("W01", 4).containsEntry("W03", 4);
        assertThat(s.groups().get("W01")).containsExactly(0, 1, 2, 3);
        assertThat(s.groups().get("W03")).containsExactly(4, 5, 6, 7);
    }

    @Test
    void noPipelinesGiveAnEmptyResult() {
        Sel s = SelectorApi.select(List.of());
        assertThat(s.kept()).isEmpty();
        assertThat(s.groups()).isEmpty();
        assertThat(s.candidatesConsidered()).isEmpty();
        assertThat(s.articlesConsidered()).isZero();
    }

    // ---- (c) shared candidates --------------------------------------------------------------------------------

    private static final String SHARED = "http://shared.example/story";

    @Test
    void aCandidateSelectedFirstByThreePipelinesIsKeptOnceUsesOneSlotAndIsListedInThreeGroups() {
        // pipelines Wj have the queries Q(2j-1), Q(2j); the shared link comes first in the first query of each pipeline and, for W02,
        // again in its second query (so it ranks first there by H = 2); every pipeline has 2 own candidates besides
        List<P> ps = new ArrayList<>();
        for (int j = 1; j <= 3; j++) {
            Q first = Q.of(qid(2 * j - 1), text(j, 1), plain(SHARED, "shared-" + j),
                plain("http://own.example/" + wid(j) + "/1", "o" + j + "x1"), plain("http://own.example/" + wid(j) + "/2", "o" + j + "x2"));
            Q second = j == 2 ? Q.of(qid(2 * j), text(j, 2), plain(SHARED, "shared-2b")) : Q.of(qid(2 * j), text(j, 2));
            ps.add(P.of(wid(j), "Zebra crossing", "topic-" + j, first, second));
        }
        Sel s = SelectorApi.select(ps);
        assertThat(s.kept()).as("one shared + 2 own sources per pipeline: 7 slots, not 9").hasSize(7);
        K shared = s.kept().get(0);
        assertThat(shared.url()).isEqualTo(SHARED);
        assertThat(shared.pipelineIds()).containsExactly("W01", "W02", "W03");
        assertThat(shared.queryIds()).as("every query of any pipeline that returned it, ascending").containsExactly("Q01", "Q03", "Q04", "Q05");
        assertThat(shared.topic()).as("topic of its first pipeline").isEqualTo("topic-1");
        assertThat(shared.title()).as("article = first occurrence over the whole plan").isEqualTo("Plain headline shared-1");
        for (int j = 1; j <= 3; j++) {
            assertThat(s.groups().get(wid(j)).get(0)).as(wid(j) + " lists the shared source first").isEqualTo(0);
            assertThat(s.groups().get(wid(j))).hasSize(3);
        }
        assertThat(s.kept().stream().filter(k -> k.pipelineIds().size() == 1)).hasSize(6);
        assertThat(s.articlesConsidered()).isEqualTo(7);
        assertThat(s.candidatesConsidered()).containsEntry("W01", 3).containsEntry("W02", 3).containsEntry("W03", 3);
    }

    /** F240 shape: one link ranked first in all pipelines (found by both queries of each), each with >= 3 further distinct candidates. */
    @Test
    void theF240ShapeKeepsTwentyEightSources() {
        List<P> ps = new ArrayList<>();
        for (int j = 1; j <= 9; j++) {
            List<Item> a = new ArrayList<>(List.of(plain(SHARED, "sh-" + j)));
            List<Item> b = new ArrayList<>(List.of(plain(SHARED, "sh-" + j)));
            for (int i = 1; i <= 5; i++) a.add(plain("http://own.example/" + wid(j) + "/" + i, "o" + j + "x" + i));
            ps.add(P.of(wid(j), "Zebra crossing", "topic-" + j, new Q(qid(2 * j - 1), text(j, 1), a), new Q(qid(2 * j), text(j, 2), b)));
        }
        Sel s = SelectorApi.select(ps);
        assertThat(s.kept()).as("round 1 keeps only the shared link, rounds 2-4 nine each").hasSize(28);
        assertThat(s.kept().get(0).url()).isEqualTo(SHARED);
        assertThat(s.kept().get(0).pipelineIds()).hasSize(9);
        assertThat(s.kept().get(0).queryIds()).hasSize(18);
        assertThat(s.articlesConsidered()).isEqualTo(1 + 9 * 5);
        for (int j = 1; j <= 9; j++) {
            assertThat(s.groups().get(wid(j))).as(wid(j)).hasSize(4);
            assertThat(s.groups().get(wid(j)).get(0)).isEqualTo(0);
        }
    }

    @Test
    void aCandidateOfAPipelineThatAnotherPipelineSelectedAndKeptAppearsInItsGroupAtItsOwnRankEvenThoughItWasNotSelected() {
        String z = "http://shared.example/z";
        Q w1 = Q.of("Q01", text(1, 1), plain("http://own.example/W01/1", "x1"), plain("http://own.example/W01/2", "x2"),
            plain("http://own.example/W01/3", "x3"), plain("http://own.example/W01/4", "x4"), plain(z, "z"));
        Q w2 = Q.of("Q02", text(2, 1), plain(z, "z"), plain("http://own.example/W02/2", "y2"), plain("http://own.example/W02/3", "y3"),
            plain("http://own.example/W02/4", "y4"));
        Sel s = SelectorApi.select(List.of(P.of("W01", "Zebra crossing", "topic-1", w1), P.of("W02", "Zebra crossing", "topic-2", w2)));
        List<String> urls = s.urls();
        assertThat(urls).containsExactly("http://own.example/W01/1", "http://own.example/W01/2", "http://own.example/W01/3",
            "http://own.example/W01/4", z, "http://own.example/W02/2", "http://own.example/W02/3", "http://own.example/W02/4");
        assertThat(s.groups().get("W01")).as("Z is the 5th of W01's rank and not selected by it, yet listed there").containsExactly(0, 1, 2, 3, 4);
        assertThat(s.groups().get("W02")).containsExactly(4, 5, 6, 7);
        assertThat(s.kept().get(4).pipelineIds()).isEqualTo(List.of("W01", "W02"));
        assertThat(s.kept().get(4).topic()).isEqualTo("topic-1");
    }

    // ---- (d) score classes ------------------------------------------------------------------------------------

    /** Pipeline "Energy crisis" with queries "power grid strain" / "fuel price spike"; the label terms are {energy, crisis}. */
    private static P energy(Q... queries) {
        return P.of("W01", "Energy crisis", "energy-energy-crisis", queries);
    }

    private static Q q1(Item... items) {
        return Q.of("Q01", "power grid strain", items);
    }

    private static Q q2(Item... items) {
        return Q.of("Q02", "fuel price spike", items);
    }

    static Stream<Arguments> scores() {
        return Stream.of(
            Arguments.of("Energy news", null, 3, "one label term, 'news' is a stop word"),
            Arguments.of("Energy crisis deepens", null, 6, "two label terms"),
            Arguments.of("Power grid outage", null, 2, "two query terms"),
            Arguments.of("Energy fuel price", null, 5, "one label term (3) and two query terms (2)"),
            Arguments.of("Plain headline", "Energy prices fall", 3, "a label term in the snippet counts like one in the title"),
            Arguments.of("Energy", "energy energy ENERGY", 3, "a term repeated in the text counts once"),
            Arguments.of("ENERGY", null, 3, "case-insensitive"),
            Arguments.of("The new news and for ai of a to", null, 0, "stop words and tokens shorter than 3 characters never count"),
            Arguments.of("Plain headline", null, 0, "nothing matches"),
            Arguments.of("Grid", "Strain on the grid", 2, "a query term in title and snippet counts once"));
    }

    @ParameterizedTest(name = "{3}: \"{0}\" / {1} -> {2}")
    @MethodSource("scores")
    void theRelevanceScoreCountsLabelTermsThreeTimesAndQueryTermsOnce(String title, String snippet, int score, String why) {
        List<C> ranked = SelectorApi.rank(energy(q1(new Item("http://a.example/x", title, NOW.minusSeconds(60), snippet)), q2()));
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).score()).as(why).isEqualTo(score);
    }

    @Test
    void aCandidateFoundByMoreQueriesGetsTwoPerExtraQuery() {
        P two = energy(q1(plain("http://a.example/x", "x")), q2(plain("http://a.example/x", "x")));
        assertThat(SelectorApi.rank(two).get(0).score()).as("found by 2 queries: +2").isEqualTo(2);
        assertThat(SelectorApi.rank(two).get(0).queryIds()).containsExactly("Q01", "Q02");
        P three = energy(q1(plain("http://a.example/x", "x")), q2(plain("http://a.example/x", "x")),
            Q.of("Q03", "fuel power", plain("http://a.example/x", "x")));
        assertThat(SelectorApi.rank(three).get(0).score()).as("found by 3 queries: +4").isEqualTo(4);
        P one = energy(q1(plain("http://a.example/x", "x")));
        assertThat(SelectorApi.rank(one).get(0).score()).isZero();
    }

    @Test
    void theScoreUsesTheTextOfTheFirstOccurrenceInThePipeline() {
        P p = energy(q1(plain("http://a.example/x", "first")), q2(Item.of("http://a.example/x", "Energy crisis")));
        List<C> ranked = SelectorApi.rank(p);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).title()).as("article = first occurrence (query order, then feed order)").isEqualTo("Plain headline first");
        assertThat(ranked.get(0).score()).as("0 for the first text + 2 for the second query").isEqualTo(2);
    }

    @Test
    void aTitleWithALabelTermBeatsOneWithTwoQueryTermsRegardlessOfFeedOrder() {
        P p = energy(q1(Item.of("http://a.example/grid", "Power grid outage"), Item.of("http://a.example/energy", "Energy news")));
        assertThat(SelectorApi.rank(p).stream().map(C::url).toList()).containsExactly("http://a.example/energy", "http://a.example/grid");
        assertThat(SelectorApi.rank(p).stream().map(C::score).toList()).containsExactly(3, 2);
    }

    @Test
    void equalScoresAreOrderedByBestFeedPositionThenByQueryId() {
        // positions: x1 = 1, x2 = 2, x3 = 3 in Q02; y1 = 1 in Q01 -> equal score 0
        P p = energy(q1(plain("http://a.example/y1", "y1")), q2(plain("http://a.example/x1", "x1"), plain("http://a.example/x2", "x2"),
            plain("http://a.example/x3", "x3")));
        List<C> ranked = SelectorApi.rank(p);
        // position 1: Q01 before Q02 (lower query id), then position 2, then position 3
        assertThat(ranked.stream().map(C::url).toList()).containsExactly("http://a.example/y1", "http://a.example/x1",
            "http://a.example/x2", "http://a.example/x3");
        assertThat(ranked.stream().map(C::bestPosition).toList()).containsExactly(1, 1, 2, 3);
        assertThat(ranked.stream().map(C::bestQueryId).toList()).containsExactly("Q01", "Q02", "Q02", "Q02");
        // the same link at positions 3 (Q01) and 1 (Q02): best position 1 from Q02
        P twice = energy(q1(plain("http://a.example/a", "a"), plain("http://a.example/b", "b"), plain("http://a.example/t", "t")),
            q2(plain("http://a.example/t", "t")));
        C t = SelectorApi.rank(twice).stream().filter(c -> c.url().endsWith("/t")).findFirst().orElseThrow();
        assertThat(t.bestPosition()).isEqualTo(1);
        assertThat(t.bestQueryId()).as("the id of the query that has the best position").isEqualTo("Q02");
        assertThat(t.queryIds()).containsExactly("Q01", "Q02");
    }

    @Test
    void generalPipelineScoresTheTokensOfTheSubject() {
        P general = P.of("W01", "major current world events", "major",
            Q.of("Q01", "W01 stub query 1", Item.of("http://a.example/a", "Plain headline"), Item.of("http://a.example/b", "Major world events"),
                Item.of("http://a.example/c", "Current affairs")));
        List<C> ranked = SelectorApi.rank(general);
        assertThat(ranked.stream().map(C::url).toList()).containsExactly("http://a.example/b", "http://a.example/c", "http://a.example/a");
        assertThat(ranked.stream().map(C::score).toList()).containsExactly(9, 3, 0);
    }

    // ---- (e) filter classes -----------------------------------------------------------------------------------

    static Stream<Arguments> unusable() {
        return Stream.of(
            Arguments.of("blank title", new Item("http://a.example/x", "", NOW.minusSeconds(60), null)),
            Arguments.of("whitespace title", new Item("http://a.example/x", "  \t ", NOW.minusSeconds(60), null)),
            Arguments.of("null title", new Item("http://a.example/x", null, NOW.minusSeconds(60), null)),
            Arguments.of("ftp link", new Item("ftp://a.example/x", "Title", NOW.minusSeconds(60), null)),
            Arguments.of("javascript link", new Item("javascript:void(0)", "Title", NOW.minusSeconds(60), null)),
            Arguments.of("relative link", new Item("/relative/path", "Title", NOW.minusSeconds(60), null)),
            Arguments.of("blank link", new Item("", "Title", NOW.minusSeconds(60), null)),
            Arguments.of("null link", new Item(null, "Title", NOW.minusSeconds(60), null)),
            Arguments.of("one second older than the cutoff", new Item("http://a.example/x", "Title", CUTOFF.minusSeconds(1), null)));
    }

    @ParameterizedTest(name = "dropped: {0}")
    @MethodSource("unusable")
    void anUnusableItemIsNotACandidate(String name, Item item) {
        P p = energy(q1(item));
        assertThat(SelectorApi.rank(p)).isEmpty();
        Sel s = SelectorApi.select(List.of(p));
        assertThat(s.kept()).isEmpty();
        assertThat(s.candidatesConsidered().get("W01")).isZero();
        assertThat(s.articlesConsidered()).isZero();
    }

    @ParameterizedTest(name = "horizon window of {0} days")
    @ValueSource(ints = {7, 14, 90})
    void anItemExactlyAtTheCutoffIsKeptAndOneSecondOlderIsDropped(int days) {
        Instant cutoff = NOW.minusSeconds(days * 86_400L);
        P p = energy(q1(new Item("http://a.example/at", "At cutoff", cutoff, null), new Item("http://a.example/old", "Older", cutoff.minusSeconds(1), null),
            new Item("http://a.example/new", "Newer", cutoff.plusSeconds(1), null)));
        assertThat(SelectorApi.rank(p, cutoff).stream().map(C::url).toList()).containsExactlyInAnyOrder("http://a.example/at", "http://a.example/new");
        assertThat(SelectorApi.select(List.of(p), cutoff).candidatesConsidered().get("W01")).isEqualTo(2);
    }

    @Test
    void anUnparsableDateIsKept() {
        P p = energy(q1(new Item("http://a.example/x", "No date", null, null)));
        assertThat(SelectorApi.rank(p)).hasSize(1);
    }

    @Test
    void aDroppedItemKeepsItsSlotInFeedPositionsButNeverCountsElsewhere() {
        // Q01: [blank-title entry for L, X]; Q02: [L]. L is a candidate of Q02 only: H = 1, queryIds [Q02]; X is at position 2
        P p = energy(q1(new Item("http://a.example/l", " ", NOW.minusSeconds(60), null), plain("http://a.example/x", "x")),
            q2(plain("http://a.example/l", "l")));
        List<C> ranked = SelectorApi.rank(p);
        assertThat(ranked).hasSize(2);
        C x = ranked.stream().filter(c -> c.url().endsWith("/x")).findFirst().orElseThrow();
        C l = ranked.stream().filter(c -> c.url().endsWith("/l")).findFirst().orElseThrow();
        assertThat(x.bestPosition()).as("the dropped item still took position 1").isEqualTo(2);
        assertThat(l.queryIds()).as("the dropped occurrence does not count toward H / queryIds").containsExactly("Q02");
        assertThat(l.score()).as("H = 1: no bonus").isZero();
        assertThat(SelectorApi.select(List.of(p)).candidatesConsidered().get("W01")).isEqualTo(2);
    }

    @Test
    void linksEqualAfterNormalisationAreOneCandidate() {
        P p = energy(q1(plain("HTTP://Example.COM:80/a?utm_source=x&b=1#frag", "one")),
            q2(plain("http://example.com/a?b=1", "two"), plain("http://example.com/a?b=1&utm_medium=y", "three")));
        List<C> ranked = SelectorApi.rank(p);
        assertThat(ranked).hasSize(1);
        assertThat(ranked.get(0).url()).isEqualTo("http://example.com/a?b=1");
        assertThat(ranked.get(0).queryIds()).containsExactly("Q01", "Q02");
        assertThat(ranked.get(0).title()).isEqualTo("Plain headline one");
        Sel s = SelectorApi.select(List.of(p));
        assertThat(s.kept()).hasSize(1);
        assertThat(s.kept().get(0).url()).isEqualTo("http://example.com/a?b=1");
        assertThat(s.articlesConsidered()).isEqualTo(1);
    }

    // ---- (f) determinism --------------------------------------------------------------------------------------

    @Test
    void theSameInputTwiceGivesEqualResultsAndTheInputIsNotMutated() {
        List<P> ps = new ArrayList<>(generate(7, 9));
        List<List<List<Item>>> snapshot = new ArrayList<>();
        for (P p : ps) snapshot.add(p.queries().stream().map(q -> (List<Item>) new ArrayList<>(q.items())).toList());
        Sel first = SelectorApi.select(ps);
        Sel second = SelectorApi.select(ps);
        assertThat(second.raw()).isEqualTo(first.raw());
        assertThat(second.urls()).isEqualTo(first.urls());
        assertThat(ps).hasSize(7);
        for (int i = 0; i < ps.size(); i++) {
            for (int k = 0; k < ps.get(i).queries().size(); k++) {
                assertThat(ps.get(i).queries().get(k).items()).as("items of " + ps.get(i).id() + " query " + k).isEqualTo(snapshot.get(i).get(k));
            }
        }
        assertThat(first.kept()).isNotEmpty();
    }

    // ---- invariants over generated inputs ---------------------------------------------------------------------

    /**
     * Pipeline j has two queries (Q(2j-1), Q(2j)) and {@code candidates} candidates in order; every third candidate is a link shared by
     * all pipelines, the others are the pipeline's own; every (i + j) mod 4 = 0 title carries a label term so that scores vary; the
     * second query repeats every second candidate (H = 2 for those).
     */
    private static List<P> generate(int pipelines, int candidates) {
        List<P> out = new ArrayList<>();
        for (int j = 1; j <= pipelines; j++) {
            List<Item> a = new ArrayList<>();
            List<Item> b = new ArrayList<>();
            for (int i = 1; i <= candidates; i++) {
                String link = i % 3 == 0 ? "http://shared.example/s" + (i / 3) : "http://own.example/" + wid(j) + "/" + i;
                Item item = Item.of(link, ((i + j) % 4 == 0 ? "Energy " : "") + "Plain headline " + wid(j).toLowerCase() + " " + i);
                a.add(item);
                if (i % 2 == 0) b.add(item);
            }
            out.add(P.of(wid(j), "Energy crisis", "topic-" + j, new Q(qid(2 * j - 1), text(j, 1), a), new Q(qid(2 * j), text(j, 2), b)));
        }
        return out;
    }

    static Stream<Arguments> grid() {
        return IntStream.rangeClosed(0, 33).boxed().flatMap(p -> IntStream.rangeClosed(0, 12).mapToObj(c -> Arguments.of(p, c)));
    }

    @ParameterizedTest(name = "{0} pipelines x {1} candidates")
    @MethodSource("grid")
    void theInvariantsHoldForEveryInput(int pipelines, int candidates) {
        List<P> ps = generate(pipelines, candidates);
        Sel s = SelectorApi.select(ps);

        // the independent restatement of steps 3-6 over rank() (the relevance order of each pipeline)
        Map<String, List<C>> ranked = new LinkedHashMap<>();
        for (P p : ps) ranked.put(p.id(), SelectorApi.rank(p));
        Set<String> kept = new LinkedHashSet<>();
        for (int round = 0; round < 4; round++) {
            for (P p : ps) {
                List<C> rk = ranked.get(p.id());
                if (round >= Math.min(4, rk.size())) continue;
                String url = rk.get(round).url();
                if (!kept.contains(url) && kept.size() < 30) kept.add(url);
            }
        }
        Set<String> allCandidates = new LinkedHashSet<>();
        ranked.values().forEach(l -> l.forEach(c -> allCandidates.add(c.url())));
        Set<String> selectedUnion = new LinkedHashSet<>();
        ranked.values().forEach(l -> l.stream().limit(4).forEach(c -> selectedUnion.add(c.url())));

        assertThat(s.kept().size()).as("<= 30 and = min(30, |union of selected|)").isLessThanOrEqualTo(30)
            .isEqualTo(Math.min(30, selectedUnion.size())).isEqualTo(kept.size());
        assertThat(s.urls()).as("kept urls distinct").doesNotHaveDuplicates();
        assertThat(selectedUnion).as("kept is a subset of the union of the selections").containsAll(s.urls());
        assertThat(s.articlesConsidered()).as("articlesConsidered = |union of candidates| >= kept").isEqualTo(allCandidates.size())
            .isGreaterThanOrEqualTo(s.kept().size());

        // groups: kept sources among P's candidates in P's relevance order; Evidence order = groups concatenated, first appearance kept
        List<String> evidence = new ArrayList<>();
        Map<String, List<String>> groupUrls = new LinkedHashMap<>();
        for (P p : ps) {
            List<String> g = ranked.get(p.id()).stream().map(C::url).filter(kept::contains).toList();
            groupUrls.put(p.id(), g);
            for (String url : g) if (!evidence.contains(url)) evidence.add(url);
        }
        assertThat(s.urls()).as("Evidence order").isEqualTo(evidence);
        assertThat(s.groups().keySet()).containsExactlyElementsOf(ps.stream().map(P::id).toList());
        assertThat(s.candidatesConsidered().keySet()).containsExactlyElementsOf(ps.stream().map(P::id).toList());
        int groupTotal = 0;
        for (P p : ps) {
            assertThat(s.candidatesConsidered().get(p.id())).as(p.id() + " candidatesConsidered").isEqualTo(ranked.get(p.id()).size());
            List<String> g = groupUrls.get(p.id());
            groupTotal += g.size();
            assertThat(s.groups().get(p.id()).stream().map(i -> s.kept().get(i).url()).toList()).as(p.id() + " group in relevance order")
                .isEqualTo(g);
            assertThat(Math.min(4, ranked.get(p.id()).size())).as("|selected(P)| <= 4").isLessThanOrEqualTo(4);
        }
        assertThat(groupTotal).as("sum of group sizes >= kept").isGreaterThanOrEqualTo(s.kept().size());
        if (pipelines <= 30) {
            for (P p : ps) {
                if (!ranked.get(p.id()).isEmpty()) assertThat(s.groups().get(p.id())).as(p.id() + " keeps >= 1").isNotEmpty();
            }
        }

        for (int i = 0; i < s.kept().size(); i++) {
            K k = s.kept().get(i);
            List<String> owners = new ArrayList<>();
            Set<String> queries = new java.util.TreeSet<>();
            for (P p : ps) {
                for (C c : ranked.get(p.id())) {
                    if (c.url().equals(k.url())) {
                        owners.add(p.id());
                        queries.addAll(c.queryIds());
                    }
                }
            }
            assertThat(k.pipelineIds()).as("pipelineIds of " + k.url() + " = exactly the pipelines having it as candidate, ascending").isEqualTo(owners);
            assertThat(k.queryIds()).as("queryIds of " + k.url()).isEqualTo(new ArrayList<>(queries));
            assertThat(k.topic()).as("topic = topic of the first pipeline").isEqualTo("topic-" + Integer.parseInt(owners.get(0).substring(1)));
            C first = ranked.get(owners.get(0)).stream().filter(c -> c.url().equals(k.url())).findFirst().orElseThrow();
            assertThat(k.title()).as("article = first occurrence over the whole plan").isEqualTo(first.title());
            final int index = i;
            for (P p : ps) {
                boolean listed = s.groups().get(p.id()).contains(index);
                assertThat(listed).as(k.url() + " in the group of " + p.id() + " iff it is its candidate").isEqualTo(owners.contains(p.id()));
            }
        }
    }
}
