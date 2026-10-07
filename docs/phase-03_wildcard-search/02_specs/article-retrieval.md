# Spec — Article retrieval: selection per wildcard, publisher article text, relevant fragments, safe fetching

Covers: FR-53, FR-54, FR-55, FR-56

Delta against phase-01 `research-pipeline.md` FR-13 (filtering, metadata fetch, source fields), FR-17 (event selection
with counter-signal quota), phase-02 `news-search.md` FR-46 (30-source cap with topic round robin) and FR-48 step 8
(Google link "resolved" by following HTTP redirects — release finding R2), and the current code
`SourceRetrieval.readSources`, `SourceCap`, `ArticleMetadataFetcher`, `UrlNormalizer`, `SourceQualityTable`,
`SourceRepository`. Runs in stage READING_SOURCES (index 4), after the FR-52 join (`wildcard-search.md`), inside the
NFR-10 budget.

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-17: up to 25 events in CORE / SUPPORTING / COUNTER_SIGNAL with diversity caps and ≥ 1 counter-signal, Evidence IDs on events | **Superseded** by FR-53 (selection of up to 4 sources per wildcard, before article retrieval) and FR-57 (Evidence IDs on sources, `wildcard-evidence.md`). `EvidenceSelector` is no longer called for new runs; events get no `selection`; `counts.counterSignals` = 0. The `oracul.evidence.core / supporting / counter-signals / max-*` properties are no longer read. |
| FR-46: cap 30 by topic round robin ranked by source quality, then provider order | **Superseded** by the FR-53 cap: round robin over wildcard pipelines in pipeline order, each pipeline's own relevance order; "at most 30" stays. |
| FR-13 / FR-48 metadata fetch: GET the link with ≤ 3 redirects, 3 s, 512 KB; a redirect ending at another URL counted as "resolved" (R2: real Google ends on a Google page) | **Superseded** by FR-54 (decode through Google's batchexecute call, publisher fetch) and FR-56 (≤ 5 redirects, 2 MB, private-address refusal). A link that ends on a Google host is never the article. |
| FR-13: `summary` = page description else title; `metadataFetched` | kept with the FR-54 field rules below |

## Purpose
From each wildcard's search results ORACUL keeps the few most relevant articles, reaches the real publisher page behind
every Google News link, and extracts the paragraphs that matter for that wildcard — safely, without letting a feed
link reach internal addresses or flood memory.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| `source` (Flyway, migrations of slices 04 and 08) | `content_status` | VARCHAR(32) NULL | `ArticleContentStatus` (below); NULL for stored older runs |
| `source` | `excerpts` | JSONB NULL | `[{"pipelineId":"W01","fragments":["…"]}]` — per pipeline that lists the source and got ≥ 1 fragment, in pipeline order |
| `source` | `publisher_host` | TEXT NULL | host of `url` when it is the publisher URL (lower-case, leading `www.` removed); NULL when `url` is still the Google link |
| `source` | `pipeline_ids` | JSONB NULL | pipelines that found the source (FR-50 attribution), ascending |
| `source` | `url` | TEXT | publisher URL (normalised) after a successful decode, else the Google link (normalised); unique per run (existing constraint) |
| `source` | `id` | `S001`… | assigned in **Evidence order** (FR-57): pipelines in order, inside a pipeline its group order, first appearance wins — so `S00k` ↔ `E00k` |
| API `Source` | `contentStatus`, `excerpts`, `publisherHost`, `pipelineIds` | optional | always sent for new runs (`excerpts` may be `[]`) |
| API `WildcardPipeline` | `candidatesConsidered`, `sourceIds` | optional | set by the READING_SOURCES commit |
| `ResearchCounts` | `articlesConsidered` | int | distinct usable candidates over all pipelines (distinct normalised links after filtering) — the meaning of phase-01 FR-13 again |
| `ResearchCounts` | `sourcesKept` (new, optional, always sent for new runs) | int 0–30 | stored sources |
| `ResearchCounts` | `sourcesWithContent` (new, optional, always sent for new runs) | int ≥ 0 | stored sources with `contentStatus` RETRIEVED |

`ArticleContentStatus`: `RETRIEVED` (publisher page read and ≥ 1 fragment for ≥ 1 pipeline) · `DECODE_FAILED` (Google
page, attributes or batchexecute failed, timed out, or returned a Google host) · `PAGE_FAILED` (publisher page non-2xx,
timeout, connection error, not HTML/text, more than 5 redirects) · `NO_TEXT` (page read but no fragment: no matching
paragraph and no paragraph of ≥ 80 characters) · `REFUSED` (FR-56: blocked address, non-http(s) URL) ·
`NOT_ATTEMPTED` (budget ended or the run stopped before the retrieval finished). Everything but RETRIEVED is shown as
"content not retrieved".

### Configuration (new / changed)
| Property | Env var | Default | Rule |
|---|---|---|---|
| `oracul.news.google.decode-url` | `ORACUL_NEWS_GOOGLE_DECODE_URL` | `<google.base-url>/_/DotsSplashUi/data/batchexecute` | undocumented Google endpoint, behind `ArticleUrlDecoder`; tests point it to a stub |
| `oracul.news.article-fetch-timeout` | `ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT` | `PT8S` (was `PT3S`) | applies separately to (a) the decode step (Google page + batchexecute together) and (b) the publisher fetch incl. redirects; each also cut to the NFR-10 budget |
| `oracul.news.article-fetch-concurrency` | — | `8` | max retrieval HTTP requests open at once (Google page, decode POST and publisher GET share one semaphore); 1…8 |
| `oracul.news.article-max-bytes` | — | `2097152` (2 MB, was 524288) | bytes read per response body; reading stops there |
| `oracul.news.article-max-redirects` | — | `5` | per fetch (Google-page fetch, publisher fetch) |
| `oracul.news.fetch.allowed-private-hosts` | `ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS` | empty | comma-separated host names (case-insensitive, exact) exempt from the FR-56 address check; real mode sets none; E2E `stub`, backend tests `127.0.0.1` |
Constants (not configurable): 4 selected sources per wildcard, 30 sources per run (FR-46 limit), 3 fragments and 1,200
characters per source and pipeline.

## Behaviour

### FR-53 — Source selection per wildcard
- Happy path (READING_SOURCES, pure `WildcardSelector`, after the FR-52 join):
  1. **Candidates per pipeline**: the items of all its OK queries, in query order then feed order. Basic filter
     (phase-01 / FR-48 rules): `link` absolute `http`/`https`; cleaned `title` non-blank; `pubDate` parsed and older
     than the horizon's days (7 / 14 / 90) → dropped (unparsable → kept, `publishedAt` absent). Duplicates inside the
     pipeline (same normalised link, `UrlNormalizer` rules) are merged: the candidate remembers every query that
     returned it (H = number of those queries) and its best feed position (lowest 1-based index over those queries).
     `candidatesConsidered` = number of the pipeline's merged candidates.
  2. **Relevance score** of a candidate for its pipeline (deterministic, no ChatGPT):
     - tokens(text) = lower-case words of `[a-z0-9]+`, length ≥ 3, without the stop words {the, and, for, with, from,
       that, this, are, was, were, has, have, had, its, into, over, about, after, than, then, will, what, when, which,
       who, why, how, new, latest, news, today, says, said}.
     - label terms = tokens(label) (GENERAL: tokens of `major current world events`); query terms = tokens of the
       pipeline's query texts minus the label terms.
     - text = cleaned title + " " + snippet (RSS `description` with HTML tags removed, entities decoded, whitespace
       collapsed).
     - score = 3 × |label terms ∩ tokens(text)| + |query terms ∩ tokens(text)| + 2 × (H − 1).
     - Order: score desc, then best feed position asc, then the id of the query with that position asc, then the
       normalised link asc.
  3. **Selection**: the pipeline's first 4 candidates in that order (fewer when it has fewer).
  4. **Same article in several pipelines**: candidates of different pipelines with the same normalised link are the
     same article (one future source). Its `pipelineIds` = every pipeline that has it as a candidate; its `queryIds`
     = every query (any pipeline) that returned it, ascending.
  5. **Cap 30** (round robin): rounds r = 1…4; in each round visit the pipelines in pipeline order and take the
     pipeline's r-th selected candidate: already kept → nothing (it counts once); else kept if fewer than 30 are kept;
     stop at 30. Hence every pipeline with ≥ 1 candidate keeps ≥ 1 source whenever at most 30 pipelines have
     candidates (round 1 adds at most one new source per pipeline).
  6. **Groups**: the group of pipeline P = every kept source among P's candidates, in P's relevance order (P's own
     selections and kept sources P found that another pipeline selected). A pipeline without kept sources has an empty
     group ("no current sources found", FR-57 / FR-59 / FR-60).
  7. Retrieval (FR-54) runs for the kept sources only; then sources are numbered in Evidence order (Data) and stored
     in one guarded commit together with `counts.articlesConsidered`, `sourcesKept`, `sourcesWithContent` and every
     pipeline's `candidatesConsidered` / `sourceIds` in `search_plan`.
- Rules:
  - There is no counter-signal quota and no diversity cap (FR-53 acceptance 4); `topic` of a source = `topicKey` of
    its first pipeline (GENERAL → `major`), so phase-01 event ranking (FR-16) keeps working on the stored sources.
  - Two kept sources whose publisher URLs turn out equal after decoding (two Google links of one article) are merged
    after retrieval into the one earlier in Evidence order (union of `pipelineIds` / `queryIds`; the later one leaves
    no row). Numbering happens after this merge.
  - `articlesConsidered` = distinct normalised links over all pipelines' candidates; `sourcesKept` ≤ 30; the article
    fetchers are called for kept sources only.
- Errors: none user-facing; a pipeline with 0 candidates simply has no sources (FR-59 note).
- Ranges & invariants (unit, parameterized, `WildcardSelector`): candidates per pipeline 0, 1, 3, 4, 5, 40 → selected
  0, 1, 3, 4, 4, 4; pipelines × candidates: 9 pipelines × 10 → 30 kept (rounds 1–3 give 27, round 4 the first 3
  pipelines' 4th), every pipeline ≥ 3; 30 pipelines with candidates → 30 kept, each pipeline ≥ 1; 31 pipelines → 30
  kept, the 31st has none (named in the note); one shared candidate selected first by 3 pipelines → kept once, listed
  in 3 groups, uses one slot. Score classes: label term in title beats query term only (3 vs 1); found by 2 queries
  (+2); equal score → lower feed position first; equal position → lower query id; equal → link order; same input
  twice / shuffled pipeline input order of items → identical result. Invariants for every input: kept ⊆ ∪ selected;
  |kept| ≤ 30; |selected(P)| ≤ 4; no normalised link twice; every kept source's `pipelineIds` = exactly the pipelines
  having it as candidate; `articlesConsidered` = |∪ candidates|; `sourcesKept` = stored rows = `listRunSources`
  length; groups list only kept sources.

#### Slice 05_wildcard-selection — delta (step 4a)
Scope of this slice: FR-53 steps 1–7 with the **existing** article metadata fetch (`ArticleMetadataFetcher` through
`SafeFetcher`, FR-13 / FR-48 fields, unchanged) instead of FR-54 retrieval. Still unchanged until later slices:
`contentStatus` / `excerpts` / `publisherHost` / `sourcesWithContent` stay absent (08); the redirect-resolved URL and
the rule "a resolved URL that an earlier source already has keeps its Google link" stay (08 replaces them with decode +
merge); the phase-01 event stages and the event-level `EvidenceSelector` still build the pack from the kept sources
(06). `api/openapi.yaml` 0.7.0 is unchanged: every field this slice writes already exists (`ResearchCounts.sourcesKept`,
`WildcardPipeline.candidatesConsidered` / `sourceIds`, `Source.pipelineIds`); no path, status or `ApiError.code`
changes (`listRunSources` unknown / malformed / foreign run → 404 `RUN_NOT_FOUND` "Future not found", unchanged). No
Flyway migration (`search_plan` and `counts` are JSONB, `source.pipeline_ids` exists since 04). No UI, no
`data-testid`. `.oracul/stack.json` and the compose files are unchanged. No contract renames.

**Feed data.** `NewsProvider.Article` gains a 6th component `String snippet` (record `Article(url, title, publishedAt,
sourceName, sourceUrl, snippet)`); the 5-argument constructor stays (snippet null), so existing test code compiles.
`GoogleNewsProvider.parse` fills it from the item's `<description>` text: tags `<…>` removed, the entities `&amp;`
`&lt;` `&gt;` `&quot;` `&#39;` `&apos;` `&nbsp;` and numeric `&#NN;` / `&#xHH;` decoded, whitespace collapsed, trimmed;
missing or blank → null. Not stored, not on the API; used only for the relevance score (and in 08 for the summary).

**Component `com.oracul.app.research.WildcardSelector`** (pure, static, no Spring, no clock, no I/O; replaces
`SourceCap`, which is deleted together with the `SourceCap.select` call in `SourceRetrieval`):
- constants `PER_PIPELINE = 4`, `MAX_SOURCES = 30` (`SourceRetrieval.MAX_SOURCES` now refers to it),
  `GENERAL_SUBJECT = "major current world events"`, `STOP_WORDS` = the 35 words of FR-53 step 2.
- `public record Query(String id, String text, List<NewsProvider.Article> items)` — `items` = the query's own answer in
  feed order (`QueryResult.articles()`); the caller passes `List.of()` for a query that is not OK.
- `public record Pipeline(String id, String labelText, String topic, List<Query> queries)` — `labelText` = the
  pipeline label, `GENERAL_SUBJECT` for GENERAL; `topic` = `topicKey`, `"major"` for GENERAL.
- `public record Candidate(String url, NewsProvider.Article article, List<String> queryIds, int bestPosition,
  String bestQueryId, int score)` — `url` normalised (`UrlNormalizer`); `article` = its first occurrence in the
  pipeline (query order, then feed order); `queryIds` ascending, H = `queryIds.size()`.
- `public record Kept(String url, NewsProvider.Article article, List<String> pipelineIds, List<String> queryIds,
  String topic)` — `article` = first occurrence over the whole plan (pipeline order, query order, feed order: the same
  item the current code keeps), `pipelineIds` / `queryIds` ascending over all pipelines, `topic` = topic of
  `pipelineIds[0]`.
- `public record Result(List<Kept> kept, Map<String, Integer> candidatesConsidered, Map<String, List<Integer>> groups,
  int articlesConsidered)` — `kept` in Evidence order (index i ↔ `S00(i+1)`); both maps keyed by pipeline id in plan
  order, every pipeline present (0 / empty list when it has no candidate); `groups` values are indexes into `kept`.
- `public static Result select(List<Pipeline> pipelines, Instant cutoff)` — steps 1–6 of FR-53; `cutoff` = now − the
  horizon's days (`GoogleNewsSearch.timespanDays`), an item with `publishedAt` before it is dropped.
- package-private `static Set<String> tokens(String text)` (insertion-ordered) and `static List<Candidate>
  rank(Pipeline p, Instant cutoff)` (all candidates of P in relevance order, before the top-4 cut) for unit tests.
- Exact meaning of the step-2 inputs: **feed position** = 1-based index of the item in its query's `items` (every
  `<item>` of the answer in document order, counted before the basic filter); best position = the lowest over the
  queries of P that returned the link; **bestQueryId** = the lowest query id among those having that position;
  **text** = cleaned title + " " + snippet (null → "") of the candidate's first occurrence in P; tokens are taken with
  `Locale.ROOT` lower-casing; label terms of P = `tokens(labelText)`; query terms = tokens of all of P's query texts
  minus the label terms; `queryIds` / H count only queries whose answer held a **usable** item with that link.

**`SourceRetrieval`.** New `public Read read(SearchOutcome outcome, HorizonCode horizon, BooleanSupplier mayFetch)` with
`public record Read(List<SourceRepository.Stored> sources, SearchPlan plan, int articlesConsidered)`: builds the
selector input from `outcome.plan().getPipelines()` (queries in pipeline order, OK queries with their items), calls
`WildcardSelector.select`, fetches metadata (`fetchDetailed(url, 5)`, at most `article-fetch-concurrency` at once) for
the kept candidates only, then numbers the sources in Evidence order (`S001`… = `kept` order; the resolved-URL-taken
rule walks this order) and returns the plan with every pipeline's `candidatesConsidered` and `sourceIds` (the S-ids of
its group, in group order; `[]` when empty). `readSources(...)` keeps its signatures and returns `read(...).sources()`.
A plan **without** `pipelines` (phase-01 shape — never produced for a new run; only the test seam
`PlanSupport.legacyPlan` / `AbstractNewsSearchIT.planOf`) is read as one implicit pipeline per planned query, in plan
order (id = the query id, label terms none, query terms = tokens of that query's text, topic = the phase-01 intent
mapping as today); its sources get no `pipelineIds` and the returned plan is the input plan (no pipeline fields).
With at most 4 usable items per query and at most 30 distinct links this reproduces today's result, so
`AbstractOrderIT` (via `ParallelSearchIT` / `ParallelSearchSerialIT`), `GoogleNewsSourceIT` and
`SourceRetrievalUnitTest` keep their assertions unchanged.

**READING_SOURCES commit** (`ResearchPipeline`): one guarded transaction (`guard.lockAndCheck`) inserts the sources,
writes `counts` with `articlesConsidered` = `Read.articlesConsidered` and `sourcesKept` = number of stored rows (also
`0` — a run whose search found nothing still commits `sourcesKept: 0`, every `candidatesConsidered: 0` and every
`sourceIds: []`), and stores the returned `search_plan`. A STOP or deadline before the commit leaves none of the three
written (no source row, no `sourcesKept`, no `candidatesConsidered` / `sourceIds`). Every later counts write
(CONNECTING_SIGNALS, RANKING, accepted attempt) keeps `sourcesKept`; `sourcesWithContent` is not written in this slice.
ALTERNATIVE runs keep reusing the parent's sources, plan and counts.

**Worked fixtures** (stub query texts are `W<nn> stub query <i>` — `StubResponses.defaultGeneration` / E2E stub — so
the query terms of pipeline W<nn> are {`w<nn>`, `stub`, `query`}; no catalogue label used below shares a token with the
fixture titles):
- *E2E acceptance run* (body A, 6 queries × 5 items, `e2e/stubs/server.mjs` unchanged): W01 = Q01–Q03, W02 = Q04–Q06;
  every query answers `shared` (position 1, title "Shared stub article", score 1 + 2 × 2 = 5 in each pipeline) and its
  own `<key>-2 … <key>-5` (score 3). Selected W01: shared, `-2` of Q01, Q02, Q03; W02: shared, `-2` of Q04, Q05, Q06.
  Result: `searches` 6, `articlesRetrieved` 30, `articlesConsidered` 25, `sourcesKept` 7; S001 =
  `http://stub:4010/articles/shared` (`queryIds` Q01–Q06, `pipelineIds` [W01, W02], topic `biology-new-pandemic`),
  S002–S004 = the `-2` article of Q01 / Q02 / Q03 ([W01], one query id each), S005–S007 = the `-2` article of Q04 /
  Q05 / Q06 ([W02], topic `robotics-humanoid-boom`); topics 4 + 3; pipelines W01 `candidatesConsidered` 13 /
  `sourceIds` [S001, S002, S003, S004], W02 13 / [S001, S005, S006, S007]; 7 article fetches. Events (stub pairs the
  ids): EV001 [S001, S002], EV002 [S003, S004], EV003 [S005, S006], EV004 [S007] → `uniqueEvents` 4 (malformed
  classification: 4 excluded). Unchanged FR-17 on these 4 events — mode evidence (EV001 / EV004 dark, EV002 mid,
  EV003 bright, darkness 9 > optimism 2): core 3 (two of them risk 1.0), supporting 0, counter-signals 1 (EV003),
  `eventsSelected` 4, `counterSignals` 1, Evidence IDs E001–E004, `[E004] ` and `COUNTER-SIGNALS` in `promptText`;
  default classification (all bright): core 0, counter-signals 4, `eventsSelected` 4, `counterSignals` 4; sparse mode
  (insufficient-evidence) unchanged: core 2.
- *F240 with `F240_BODY`* (9 pipelines, Wk = Q(2k−1), Q(2k), 18 queries, 240 raw items): every item of Wk scores 3,
  `shared` 3 + 2 = 5. Selected Wk = shared + the next three by position, then query id: W01 r1-a3, r2-a3, r1-a4; the
  same pattern for W02–W07; W08 r16-a3 (position 2: the r16-a2 entry links to r16-a3), r15-a3, r15-a4; W09 r17-a3,
  r18-a3, r17-a4. Cap: round 1 keeps only shared (it is every pipeline's first selection), rounds 2–4 add 9 each →
  `sourcesKept` **28** (not 30), `articlesConsidered` 205, `candidatesConsidered` W01–W03 25, W04–W09 23; numbering
  S001 = shared, the own sources of Wk are S(3k−1), S(3k), S(3k+1) in its selection order; `sourceIds` W01 = [S001,
  S002, S003, S004], Wk = [S001, S(3k−1), S(3k), S(3k+1)]; 28 article fetches (shared once); shared has 18 `queryIds`
  and 9 `pipelineIds`; every other source has exactly its own query id. With Q02 answering 503: W01 selects shared,
  r1-a3, r1-a4, r1-a5 (W01 `candidatesConsidered` 13), `articlesRetrieved` 226, `articlesConsidered` 193, still 28 kept.
- *F240 with the new `F240_BODY_TEN`* (all ten `PlanSupport.TEN_WILDCARDS`, intensity 5, realism 8 / darkness 9 /
  optimism 2 / horizon 5y; 20 queries, 266 raw items; W10 = Q19, Q20 selects shared, r19-a3, r20-a3, r19-a4): rounds
  give 1 + 10 + 10 + 9 → `sourcesKept` **30** (W10's 4th is cut), `articlesConsidered` 227; S001–S004 from W01,
  S(3k−1)…S(3k+1) for W02–W09, S029 = r19-a3, S030 = r20-a3; `sourceIds` W10 = [S001, S029, S030]. The event-stage
  F240 tests use this body (default normalisation gives EVn = [S00n] as before).
- *V4 fixtures* (`newsArticles(v4())`, body A, all four in Q01): scores 3, 3, 3, 0 (label term `pandemic`) → order and
  ids unchanged (S004 = robot-strike); `withTitles` variants of `EventNormalizationIT` / `EventNormalizerRulesIT` keep
  their asserted ids (#19: S001 / S002 = the two `pandemic` titles, S003 = "Alpha | Beta", S004 = the injection title).

**Test harness** (tester): `AbstractEventIT` gains `public static String wildcardsBody(int n)` (n = 1…10: the first n
ids of `PlanSupport.TEN_WILDCARDS`, intensity 5 each, realism 8 / darkness 9 / optimism 2 / horizon 5y, story on,
illustration off, in exactly the JSON form of today's `F240_BODY`), `F240_BODY` = `wildcardsBody(9)` (same text) and
`F240_BODY_TEN` = `wildcardsBody(10)`. At `oracul.news.google.concurrency=1` request number k is query Qk, so pipeline
j's first query is request 3(j−1)+1 for n ≤ 8 and 2(j−1)+1 for n ≥ 9; tests that need more than 4 stored sources put
at most 4 usable items into each pipeline's answers. `CapOracle` and `SourceCapIT` are deleted (FR-46 superseded);
`F240Support.usableCandidates` returns its own record instead of `CapOracle.Cand`. New tests: `WildcardSelectorTest`
(unit, the Ranges below) and `WildcardSelectionIT` (runs, FR-53 acceptance 1–4).

- Changes earlier behaviour: FR-46 cap (all usable candidates kept up to 30 by topic round robin ranked by source quality, `CapOracle`, `articlesConsidered` = stored sources) → FR-53 selection: ≤ 4 per pipeline by relevance, shared article counted once, cap 30 by pipeline round robin, Evidence-order numbering; F240 with `F240_BODY` keeps **28** sources (S001 = shared, the own sources of Wk are S(3k−1)…S(3k+1)), `articlesConsidered` 30 → 205 (193 with Q02 failed), `sourcesKept` 28, article fetches 30 → 28, per-pipeline `candidatesConsidered` / `sourceIds` as in the worked fixture; the FR-46 topic-spread / quality-order assertions are replaced by the FR-53 expectations; `SourceCapIT.java` and `CapOracle.java` (under `backend/src/test/java/com/oracul/app/research/`) are deleted, not rewritten (tests: backend/src/test/java/com/oracul/app/research/GoogleNewsRunIT.java, backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java, backend/src/test/java/com/oracul/app/research/F240Support.java)
- Changes earlier behaviour: event-stage F240 tests ran body A (6 queries, 73 usable candidates, 30 kept) → body A now keeps 7 sources, so they start `F240_BODY_TEN` (30 kept); their 30-source / 30-event / batch-count / `assertF240Events` assertions stay, `articlesConsidered` 30 → `sourcesKept` 30 (`articlesConsidered` 227); `AbstractEventIT` gains `wildcardsBody(int)` and `F240_BODY_TEN` (tests: backend/src/test/java/com/oracul/app/research/AbstractEventIT.java, backend/src/test/java/com/oracul/app/research/EventBatchingIT.java, backend/src/test/java/com/oracul/app/research/EventConcurrencyIT.java, backend/src/test/java/com/oracul/app/research/EventConcurrencyOneIT.java, backend/src/test/java/com/oracul/app/research/EventFailureIT.java, backend/src/test/java/com/oracul/app/research/EventSourceCapIT.java)
- Changes earlier behaviour: every usable item of one query became a source (up to 30) → at most 4 per pipeline: the 10 filter-surviving candidates of `SourceFilteringIT` (body B), the 23 quality rows of `SourceQualityIT` (body B), the 11 / 6 articles of `SourceMetadataIT` (body A, all in Q01) would keep 4 — each fixture is spread so that no pipeline has more than 4 usable candidates (e.g. `wildcardsBody(3)` / `(6)` with ≤ 4 items in the first query of each pipeline) and keeps its per-source assertions; `SourceFilteringIT` additionally sees `articlesConsidered` 10 = Σ `candidatesConsidered` (tests: backend/src/test/java/com/oracul/app/research/SourceFilteringIT.java, backend/src/test/java/com/oracul/app/research/SourceQualityIT.java, backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java)
- Changes earlier behaviour: run fixtures with more than 4 articles in one pipeline → 4 kept: `EventClassificationBatchIT` (5 articles in body B → 4 sources / 4 events; spread over two pipelines, e.g. body A with a1–a3 in Q01 and a4–a5 in Q04, keeps EV001–EV005 and its batch layout), `InsufficientEvidenceIT` K(c, s) with c + s up to 9 (needs ≥ 3 pipelines, ≤ 4 per pipeline in K order so that S00i = k-i), `StopRunIT` ARTICLE row (12 articles in Q01 → 4 fetches, so "8 fetches open, the rest queued" never happens; needs ≥ 9 kept sources, e.g. a 3-wildcard body with 4 articles per pipeline), `ParallelSearchRunIT` (5 pipelines × 3 queries × 3 unique items: `articlesConsidered` 30 → 45, sources 30 → 20, 4 per pipeline, each still listing only its own query) (tests: backend/src/test/java/com/oracul/app/research/EventClassificationBatchIT.java, backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java, backend/src/test/java/com/oracul/app/runs/StopRunIT.java, backend/src/test/java/com/oracul/app/research/ParallelSearchRunIT.java)
- Changes earlier behaviour: `counts` of a run past READING_SOURCES had exactly the seven phase-01 keys and no pipeline carried `candidatesConsidered` / `sourceIds` → `sourcesKept` is always present from the READING_SOURCES commit on (also `0`) and every pipeline has `candidatesConsidered` (≥ 0) and `sourceIds` (possibly `[]`); exact-JSON count comparisons of completed runs gain `"sourcesKept":0` (`SourceRetrievalIT` #13 body A all empty, `GetRunTerminalIT` body B), and the optional-wire-field walk moves `sourcesKept`, `candidatesConsidered`, `sourceIds` from "absent" to "present" (`sourcesWithContent`, `contentStatus`, `excerpts`, `publisherHost`, `wildcardSections`, `wildcardGroups`, `wildcardsWithoutSources` stay absent) (tests: backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java, backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java, backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java)
- Changes earlier behaviour: E2E acceptance run kept all 25 candidates in arrival order (S002–S013 W01, S014–S025 W02, topics 13 + 12), 13 events, mode evidence 9 core + 4 counter-signals (E001–E013), default 5 counter-signals → 7 kept sources in Evidence order (S001 shared, S002–S004 W01, S005–S007 W02, topics 4 + 3, `sourcesKept` 7, `articlesConsidered` still 25), 4 events (EV004 = [S007]; malformed classification 4 excluded), mode evidence 3 core + 1 counter-signal (E001–E004, `eventsSelected` 4, `counterSignals` 1), default classification 4 counter-signals (`eventsSelected` 4, `counterSignals` 4) (tests: e2e/tests/search-sources.spec.ts, e2e/tests/events.spec.ts, e2e/tests/evidence-pack.spec.ts)
- Ranges & invariants: FR-53 Ranges above, made exact for `WildcardSelectorTest` (parameterized, inputs built in code): (a) selected per pipeline for 0, 1, 3, 4, 5, 40 usable candidates → 0, 1, 3, 4, 4, 4; (b) pipelines × distinct candidates: 1 × 40 → 4 kept; 9 × 4 → 30 kept (rounds 1–3 give 27, round 4 the 4th of W01–W03), every pipeline ≥ 3; 9 × 10 → 30 (same); 7 × 4 → 28 (no cut); 8 × 4 → 30 (W07, W08 lose their 4th); 30 × 1 → 30, each 1; 31 × 1 → 30, W31 kept 0 with `candidatesConsidered` 1 and group `[]`; 33 × 4 → 30 (round 1 only, W31–W33 none); a pipeline with 0 candidates between others → group `[]`, the others unchanged; (c) shared candidate: first selection of 3 pipelines → kept once, uses one slot, listed in 3 groups, `pipelineIds` = those 3, `queryIds` = every query of any pipeline that returned it; F240 shape (one link ranked first in all 9 pipelines, each with ≥ 3 further distinct candidates) → 28; a candidate of P that another pipeline selected and kept appears in P's group at its P-rank even though P did not select it; (d) score classes on one pipeline (label "Energy crisis", queries "power grid strain", "fuel price spike"): title with a label term (3) beats one with two query terms (2); a label term in the snippet only counts like one in the title; a term repeated in the text counts once; stop words and tokens < 3 characters never count (label "New pandemic" → label terms {pandemic}); case-insensitive ("ENERGY" = "energy"); found by 2 of P's queries +2, by 3 +4; equal score → lower best feed position first; equal position → lower best query id; equal → normalised link ascending; (e) filter classes: blank / whitespace title, non-http(s) or relative link, `publishedAt` older than 7 / 14 / 90 days for 1m / 1y / 5y (exactly the cutoff instant kept), unparsable date kept — a dropped item never counts toward position-free fields (`candidatesConsidered`, H, `queryIds`) but keeps its slot in feed positions; two links equal after `UrlNormalizer` (utm, fragment, default port, host case) are one candidate; (f) determinism: the same input twice → equal `Result`; input lists are not mutated; GENERAL (`labelText` = `GENERAL_SUBJECT`) scores the subject's tokens {major, current, world, events} × 3. Invariants for every generated input (property-style loop over pipelines 0…33 × candidates 0…12 with shared links): `kept.size()` ≤ 30 and = min(30, |∪ selected|); every pipeline with ≥ 1 candidate keeps ≥ 1 source when ≤ 30 pipelines have candidates; |selected(P)| ≤ 4; kept urls distinct; kept ⊆ ∪ selected; each `pipelineIds` = exactly the pipelines having it as candidate, ascending, and `topic` = the topic of `pipelineIds[0]`; `articlesConsidered` = |∪ candidates| ≥ `kept.size()`; Σ group sizes ≥ `kept.size()`; every kept index is in the group of each of its `pipelineIds` and in no other; groups preserve P's relevance order; Evidence order = concatenation of the groups with first appearance kept, so `kept[i]` first appears in the group of `pipelineIds[0]`. Run level (`WildcardSelectionIT`, one run each): FR-53 acceptance 1 — one pipeline whose 3 queries return 40 distinct items → `sourcesKept` 4, `candidatesConsidered` 40, the 4 are the top 4 by the score (fixture titles chosen so the expected 4 are not the first 4 of the feed); acceptance 2 — `New pandemic` + `Energy crisis` both returning the same link → one row, `pipelineIds` [W01, W02], listed in both pipelines' `sourceIds` with one S-id, `sourcesKept` = distinct links; acceptance 3 — 9 pipelines with ≥ 4 distinct items each → `sourcesKept` 30, every pipeline's `sourceIds` non-empty; acceptance 4 — the kept set is identical whatever the classification answers (bright / dark / malformed) and no source field depends on them. Run invariants for every run: `sourcesKept` = `listRunSources` length = source rows ≤ 30; S-ids contiguous S001…; `listRunSources` order = Evidence order = concatenation of the pipelines' `sourceIds` with first appearance kept; Σ `candidatesConsidered` ≥ `articlesConsidered` ≥ `sourcesKept`; article fetches = kept sources (each link fetched at most once); `sourceIds` ⊆ ids of `listRunSources`; a pipeline lists S-id s ⇔ s's `pipelineIds` contains it.

### FR-54 — Publisher article retrieval
- Happy path (per kept source, `ArticleRetriever` on virtual threads, at most `article-fetch-concurrency` (8) HTTP
  requests open at once; sources started in Evidence order):
  1. **Google link?** A link whose host equals the host of `google.base-url` or is `news.google.com` is a Google link;
     any other link is already a publisher URL (go to step 4).
  2. **Google page**: `GET <link>` through the safe fetcher (FR-56: redirects followed only while they stay on the same
     host, ≤ 5, 2 MB) — real Google answers 302 to the same path with `&hl=en-US&gl=US&ceid=US:en`, then 200 HTML. Parse
     (jsoup, never executed) the first element that has all three attributes `data-n-a-id` (non-blank), `data-n-a-ts`
     (digits) and `data-n-a-sg` (non-blank).
  3. **Decode** (`ArticleUrlDecoder`, the only place that knows the undocumented format): `POST <decode-url>`,
     `Content-Type: application/x-www-form-urlencoded;charset=UTF-8`, same `User-Agent`, body `f.req=<URL-encoded
     value>` with value exactly
     `[[["Fbv4je","[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"<id>\",<ts>,\"<sg>\"]",null,"generic"]]]`
     (`<ts>` unquoted digits). Answer 200 → the publisher URL = the first match of `https?://[^"\\\s<>]+` in the body.
     Decode failure = steps 2–3 fail in any way: non-2xx, timeout (8 s for steps 2+3 together), connection error, no
     element with the three attributes, no URL in the answer, or the URL's host is a Google host (`google.com` or a
     subdomain, `gstatic.com` or a subdomain, `googleusercontent.com` or a subdomain, or the `google.base-url` host).
  4. **Publisher page**: `GET <publisher URL>` through the safe fetcher (FR-56), 8 s for the whole fetch; success =
     final answer 2xx with `Content-Type` `text/html`, `application/xhtml+xml` or `text/plain`; the body (≤ 2 MB)
     goes to extraction (FR-55).
  5. **Source fields** (`retrievedAt` = instant the retrieval ended, injected clock):
     | Outcome | `url` | `publisherHost` | `contentStatus` | `metadataFetched` | `summary` | `excerpts` |
     |---|---|---|---|---|---|---|
     | decoded, page read, ≥ 1 fragment | publisher URL | its host | RETRIEVED | true | page `og:description` / `meta[name=description]` (≤ 600) else snippet else title | per pipeline (FR-55) |
     | decoded, page read, 0 fragments | publisher URL | its host | NO_TEXT | true | as above | `[]` |
     | decoded, page failed / timed out / not HTML or text | publisher URL | its host | PAGE_FAILED | false | snippet else title | `[]` |
     | decoded URL or a redirect hop refused (FR-56) | publisher URL | its host | REFUSED | false | snippet else title | `[]` |
     | decode failed / timed out / Google host | Google link | absent | DECODE_FAILED | false | snippet else title | `[]` |
     | Google link refused (FR-56) | Google link | absent | REFUSED | false | snippet else title | `[]` |
     | budget ended / run stopped first | the URL known so far | as known | NOT_ATTEMPTED | false | snippet else title | `[]` |
     In every row: `title` = cleaned RSS title; `publisher` = RSS `source` text, else the page's `og:site_name` (only
     when read), else `publisherHost`, else the link host; `publisherUrl` = RSS `source@url` (FR-48, unchanged);
     `publishedAt` from `pubDate`; `sourceType` / `sourceQuality` = `SourceQualityTable.classify` of `publisherHost`,
     else the host of `source@url`, else the link host; `language` NULL; `topic`, `queryIds`, `pipelineIds` per FR-53.
     Snippet = RSS `description` as in FR-53 step 2, cut to 600 characters; blank → title.
- Rules:
  - The Google page and the batchexecute answer are never used as article content or metadata (`og:site_name`
    "Google News" is never a publisher).
  - The run guard is checked before each HTTP request; after a STOP no further request starts and answers are
    discarded (phase-02 FR-45).
  - Logs: `article decode failed: <reason>` / `article fetch failed: <reason>` / `article fetch refused: <reason>` with
    a fixed reason word (`status=<code>`, `timeout`, `no-attributes`, `no-url`, `google-host`, `blocked-address`,
    `scheme`, `redirects`, `content-type`); never a URL, a page body or the decode answer.
- Errors (no user-facing error; every failure ends as "content not retrieved" for that source and the run continues):
  decode failure → DECODE_FAILED; page failure → PAGE_FAILED; refusal → REFUSED; budget / STOP → NOT_ATTEMPTED.
- Ranges & invariants: outcome classes (IT with `StubNews` modes, one row each): ok → RETRIEVED, `url`
  `<stub>/articles/<id>`, `publisherHost` = stub host; Google page without the attributes / attributes blank / ts not
  digits → DECODE_FAILED, Google link kept; batchexecute 500 / 400 / no URL / `https://news.google.com/...` /
  `https://www.google.com/url?...` / answer after 9 s → DECODE_FAILED; publisher 404 / 503 / `application/pdf` / no
  content type / answer after 9 s → PAGE_FAILED with the publisher URL; non-Google link → no decode request, fetched
  directly. Timing classes with timeout PT1S in the IT: answer at 0.9 s used, at 1.1 s → failure. Concurrency:
  20 kept sources with every stub answer held → never more than 8 retrieval requests open (parameterized concurrency
  1, 8); all 20 sources end with a status. Invariants for every run: `contentStatus` RETRIEVED ⇔ `excerpts` non-empty;
  `metadataFetched` true ⇔ status ∈ {RETRIEVED, NO_TEXT}; `publisherHost` present ⇔ `url` is not a Google link; no
  stored `url` is on a Google host unless status is DECODE_FAILED, REFUSED (of the Google link) or NOT_ATTEMPTED before
  decoding; `sourcesWithContent` = number of RETRIEVED sources; the decode endpoint is called at most once per kept
  Google-link source.

#### Slice 08_article-text — delta (step 4a)
Scope of this slice: FR-54 (retrieval), FR-55 (extraction, below), FR-61 (stubs, `wildcard-search.md`) and NFR-10
part 3 (stage budget for retrieval). It replaces the slice-02/05 "redirect-resolved" article metadata fetch in
`SourceRetrieval` (release finding R2) and makes the 06 pack show `Excerpt:` lines. No UI, no `data-testid`.
`api/openapi.yaml` stays 0.7.0: `Source.contentStatus` / `excerpts` / `publisherHost`, `ArticleContentStatus`,
`SourceExcerpt` and `ResearchCounts.sourcesWithContent` already exist; no path, status or `ApiError.code` changes
(`listRunSources` unknown / malformed / foreign run → 404 `RUN_NOT_FOUND` "Future not found", unchanged). No rename.
Flyway: `V13__source_content.sql` = `ALTER TABLE source ADD COLUMN content_status VARCHAR(32) NULL, ADD COLUMN excerpts
JSONB NULL, ADD COLUMN publisher_host TEXT NULL;` (V11 / V12 untouched). Dependency: `org.jsoup:jsoup` (current 1.x
release) in `backend/build.gradle.kts`. `.oracul/stack.json` is unchanged (both modes start the same compose files);
`docker-compose.e2e.yml` gains `ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT: PT3S` (owner backend-builder), `docker-compose.yml`
gets no such variable.

**Definitions (exact).**
- **Google link** (step 1): the host of the source's normalised link equals, case-insensitively, `news.google.com` or
  the host of `oracul.news.google.base-url` (port ignored). In-process that host is `127.0.0.1` (every fixture link on
  the stub is a Google link, also `<base>/articles/<x>`, whose page has no attributes → DECODE_FAILED, which is the old
  "no redirect keeps the Google link" outcome); in Docker it is `stub`. Any other link is a publisher URL (step 4).
- **Google host** (decode answer rejected): host equal to or a subdomain of `google.com`, `gstatic.com` or
  `googleusercontent.com` (so `news.google.com`, `www.google.com`, `consent.google.com`), **or** the URL is again a
  Google article link of the configured base (host of `google.base-url` and path starting `/rss/articles/`). A decoded
  `http://stub:4010/articles/<id>` / `http://127.0.0.1:<p>/articles/<id>` is therefore accepted.
- **Page read** = publisher fetch outcome `OK`, status 2xx, `Content-Type` starting (case-insensitive) with `text/html`,
  `application/xhtml+xml` or `text/plain`. Body text = the bytes read (≤ 2 MB) decoded with the `charset=` of the
  Content-Type, else UTF-8.

**`SafeFetcher` (additive).** New `public Result fetch(URI uri, Duration timeout, boolean sameHostOnly)`; the two
existing methods mean `sameHostOnly = false`. With `true`, a redirect whose resolved `Location` has another host
(case-insensitive) or another effective port is not followed: the 3xx answer is returned as `OK` with that status,
`finalUri` = the answering hop. Every hop now also sends `User-Agent: Mozilla/5.0 (compatible; ORACUL/1.0)` (the value of
`GoogleNewsProvider`). Default of `oracul.news.article-fetch-timeout` moves `PT3S` → `PT8S`; constructors unchanged.

**Component `com.oracul.app.research.ArticleUrlDecoder`** (`@Component`; the only class that knows the decode format):
- Constructor `ArticleUrlDecoder(String decodeUrl, String googleBaseUrl)` (Spring `@Value`s `oracul.news.google.decode-url`,
  default blank → `<google.base-url without trailing />` + `/_/DotsSplashUi/data/batchexecute`, and
  `oracul.news.google.base-url`); answers are read up to 2,097,152 bytes.
- `public record Attributes(String id, long ts, String sg)`;
  `static Optional<Attributes> attributes(String googlePageHtml)` — jsoup parse (no script execution, no network), the
  first element in document order carrying all three attributes with `data-n-a-id` non-blank (trimmed), `data-n-a-ts`
  matching `^\d{1,18}$` (trimmed), `data-n-a-sg` non-blank (trimmed); attributes split over elements, text that only
  looks like an attribute (comment, script text) → empty.
- `static String fReq(Attributes a)` — exactly
  `[[["Fbv4je","[\"garturlreq\",[[\"X\",\"X\",[\"X\",\"X\"],null,null,1,1,\"US:en\",null,1,null,null,null,null,null,0,1],\"X\",\"X\",1,[1,1,1],1,1,null,0,0,null,0],\"<id>\",<ts>,\"<sg>\"]",null,"generic"]]]`
  with `<id>` / `<sg>` JSON-string-escaped and `<ts>` the decimal digits. The request body is `f.req=` +
  `URLEncoder.encode(value, UTF_8)`.
- `static Optional<String> publisherUrl(String answerBody, String googleBaseUrl)` — (1) structured: drop everything
  before the first `[`, parse as JSON; the first array element `[ "wrb.fr", "Fbv4je", <string>, … ]` whose third entry is
  a string is parsed as JSON again; when that is an array whose first entry is `garturlres` and second a string, that
  string is the candidate; (2) otherwise the first match of `https?://[^"\\\s<>]+` in the raw body. The candidate must
  parse as an absolute `http`/`https` URL and must not be on a Google host, else empty.
- `public Result decode(Attributes a, Duration timeout)` with `record Result(String url, String reason)` (`url` null on
  failure, `reason` one of the log words below): one `POST <decode-url>`, headers `Content-Type:
  application/x-www-form-urlencoded;charset=UTF-8` and the User-Agent above, no redirect followed; non-2xx → `status=<n>`; timeout / connection error → `timeout` / `error`; no candidate → `no-url`;
  Google host → `google-host`. The decode endpoint is trusted configuration (not checked by SafeFetcher).

**Component `com.oracul.app.research.ArticleRetriever`** (`@Component`):
- Spring reads `oracul.news.google.base-url`, `oracul.news.article-fetch-timeout` (PT8S) and
  `oracul.news.article-fetch-concurrency` (8; a value outside 1…8 fails startup with an `IllegalStateException` naming
  the property). Package-private test constructor `ArticleRetriever(SafeFetcher fetcher, ArticleUrlDecoder decoder,
  String googleBaseUrl, Duration timeout, int concurrency, Clock clock)`.
- `public enum Status { PAGE_READ, DECODE_FAILED, PAGE_FAILED, REFUSED, NOT_ATTEMPTED }` (domain enum; mapped to
  `ArticleContentStatus` only in `SourceRetrieval`: PAGE_READ → RETRIEVED / NO_TEXT after extraction).
- `public record Outcome(Status status, String publisherUrl, String contentType, String body, String description,
  String siteName, Instant endedAt)` — `publisherUrl` = the decoded URL (Google link) or the link itself (publisher
  link), null while unknown; `body` / `contentType` / `description` / `siteName` only for PAGE_READ (`description` =
  page `og:description` if non-blank else `meta name=description`, `siteName` = `og:site_name`, both via
  `ArticleMetadataFetcher.parse`, which stays as it is; the Google page is never parsed for them).
- `public boolean isGoogleLink(String url)`.
- `public List<Outcome> retrieveAll(List<String> links, SearchBudget budget, BooleanSupplier mayStart) throws
  InterruptedException` — one virtual thread per link, links started in list order; one fair semaphore of
  `concurrency` permits is taken for **every** HTTP request (Google page GET incl. its redirects as one fetch, decode
  POST, publisher GET) and released when that request ended; `mayStart` (run guard) and `budget.expired(RETRIEVAL)` are
  checked before each request: false / expired → the link ends NOT_ATTEMPTED and no further request is made for it.
  Google link: `fetcher.fetch(link, t, true)` (same host only, ≤ 5 redirects), page must be `OK` 2xx, then
  `attributes` → `decoder.decode`; t for the Google page and the decode POST together = min(article-fetch-timeout,
  `budget.remaining(RETRIEVAL)`), the POST gets what is left of it. Publisher: `fetcher.fetch(publisherUrl, t, false)`
  with t = min(article-fetch-timeout, remaining). While links are open the caller thread checks the budget at least
  every 100 ms; once it is expired every unfinished link is cancelled (its request interrupted) and ends NOT_ATTEMPTED
  with the `publisherUrl` known so far; `retrieveAll` then returns. An interrupt of the caller cancels all links and
  is rethrown.
- Outcome per fetch result: Google page `REFUSED_SCHEME` / `REFUSED_ADDRESS` → REFUSED (publisherUrl null); Google page
  `TOO_MANY_REDIRECTS` → DECODE_FAILED `redirects`; `FAILED` → DECODE_FAILED `timeout` (the fetch used its whole time)
  or `error`; non-2xx (also a not-followed off-host 3xx) → DECODE_FAILED `status=<n>`; no attributes → DECODE_FAILED
  `no-attributes`; decode failure → DECODE_FAILED with the decoder's reason. Publisher `REFUSED_*` → REFUSED;
  `TOO_MANY_REDIRECTS` → PAGE_FAILED `redirects`; `FAILED` → PAGE_FAILED `timeout` / `error`; non-2xx → PAGE_FAILED
  `status=<n>`; other content type or none → PAGE_FAILED `content-type`; else PAGE_READ. A request cut because the
  budget expired is NOT_ATTEMPTED, never DECODE_FAILED / PAGE_FAILED.
- Logs (WARN, logger `com.oracul.app.research.ArticleRetriever`): `article decode failed: <reason>` /
  `article fetch failed: <reason>`; refusals are logged by SafeFetcher (`article fetch refused: blocked-address
  host=<host>` / `scheme scheme=<scheme>`). No log line ever holds a URL, a query string, a page body, the `f.req` value
  or the decode answer.

**`SourceRetrieval`.** Package-private constructor becomes `SourceRetrieval(NewsSearchProvider search, ArticleRetriever
retriever, SourceQualityTable quality, Clock clock, Duration searchWindow, Duration stageBudget)` (Spring:
`oracul.search.search-window` PT60S, `oracul.search.stage-budget` PT90S; ≤ 0 → `IllegalStateException` naming it); the
`ArticleMetadataFetcher` dependency and `oracul.news.article-fetch-concurrency` move to `ArticleRetriever`.
`SearchBudget.Phase` gains `RETRIEVAL`; `static SearchBudget retrieval(Clock clock, Instant t0, Duration stageBudget,
Instant deadlineAt)`. New `public Read read(SearchOutcome outcome, HorizonCode horizon, Instant t0, Instant deadlineAt,
BooleanSupplier mayFetch)` (the pipeline's call: t0 = RESEARCH_STRATEGY start, deadlineAt = the run's); the existing
`read(outcome, horizon, mayFetch)` uses t0 = `clock.instant()` and no deadline; `readSources(...)` keep their
signatures. `Read` gains a 4th component `int sourcesWithContent` (accessor name `sourcesWithContent()`). Steps:
1. `WildcardSelector.select` (unchanged) → kept candidates in Evidence order.
2. `retriever.retrieveAll(kept links)`; the decode endpoint is called at most once per kept Google-link source.
3. **Merge**: kept sources whose stored `url` (below) is equal after `UrlNormalizer` are one source: the earliest in kept
   order stays (its fields), `pipelineIds` / `queryIds` = sorted unions, the later ones leave no row; every pipeline
   group that listed a removed source lists the remaining one at the first of its positions (no duplicate in a group).
   This replaces the slice-05 rule "a resolved URL that an earlier source already has keeps its Google link".
4. **Extraction** (FR-55) of every PAGE_READ source for each pipeline in its (merged) `pipelineIds`, terms of that
   pipeline = the FR-53 label terms / query terms (`WildcardSelector.tokens`, GENERAL → `GENERAL_SUBJECT`).
5. **Numbering**: S001… in kept order without the merged-away entries (= Evidence order); `sourceIds` per pipeline from
   its group; `sourcesWithContent` = number of RETRIEVED sources.
- **Source fields** (refines the FR-54 table; `publisher` and `sourceType` / `sourceQuality` keep the current FR-48 code
  rules, so stored fixture expectations stay valid):
  | Field | Rule |
  |---|---|
  | `url` | `publisherUrl` (normalised) when the outcome has one, else the normalised link |
  | `publisherHost` | only when `url` came from `publisherUrl`: its host lower-cased, a leading `www.` removed; absent otherwise (DECODE_FAILED, REFUSED of the Google link, NOT_ATTEMPTED before the decode) |
  | `contentStatus` | PAGE_READ with ≥ 1 fragment for ≥ 1 pipeline → RETRIEVED; PAGE_READ without → NO_TEXT; else the outcome status |
  | `metadataFetched` | true ⇔ PAGE_READ |
  | `summary` | PAGE_READ: `description` whitespace-collapsed (≤ 600) if non-blank, else snippet, else title; all others: snippet, else title (snippet = `Article.snippet`, cut to 600) |
  | `publisher` | PAGE_READ and `siteName` non-blank (trimmed) → it; else RSS source text (trimmed, non-blank); else host of an absolute http(s) `source@url`; else host of `url` |
  | `sourceType` / `sourceQuality` | `SourceQualityTable.classify` of the host of an absolute http(s) `source@url`, else of the host of `url` |
  | `excerpts` | `[{pipelineId, fragments}]` for each pipeline (ascending) that got ≥ 1 fragment; `[]` otherwise |
  | `retrievedAt` | `Outcome.endedAt` truncated to micros |
  | `title`, `publisherUrl`, `publishedAt`, `topic`, `queryIds`, `pipelineIds`, `language` | unchanged (FR-48 / FR-53) |
- A plan **without** `pipelines` (test seam only) is retrieved the same way; extraction uses label terms ∅ and the
  tokens of the texts of the source's queries; `excerpts` = `[]` (no pipeline id to key them); `contentStatus` RETRIEVED
  / NO_TEXT by whether a fragment was found.

**Storage and wire.** `SourceRepository.insertAll` writes `content_status`, `excerpts` (JSON, `[]` allowed) and
`publisher_host` (NULL when absent); `list` maps NULL columns to absent fields (`setExcerpts(null)` for NULL, so the
`SourceMixin` rule for `excerpts` becomes `NON_NULL`: new runs always send `excerpts`, possibly `[]`; older runs send
neither `contentStatus` nor `excerpts`). `ResearchPipeline` READING_SOURCES: calls the 5-argument `read`, writes
`counts.sourcesWithContent` (also `0`) together with `sourcesKept` in the one guarded commit; every later counts write
(CONNECTING_SIGNALS, RANKING, accepted attempt) keeps both. A STOP or budget/deadline end before the commit writes
neither. ALTERNATIVE runs keep reusing the parent's sources and counts.

**Worked fixtures.**
- *E2E acceptance run* (body A, stub mode `ok`): the 7 sources of slice 05 keep ids and urls
  (`http://stub:4010/articles/shared`, `…/<key>-2`), all `contentStatus` RETRIEVED, `publisherHost` `stub`,
  `metadataFetched` true, summary `Summary of <name>`; `excerpts`: S001 `[{W01,[P1]},{W02,[P1]}]`; S002–S004
  `[{W01,[P2(q),P4(q)]}]` with q = `W01 stub query 1` / `2` / `3`; S005–S007 `[{W02,[P2(q),P4(q)]}]` with q =
  `W02 stub query 1` / `2` / `3` (paragraph texts P1…P6 in FR-61); `sourcesWithContent` 7; stub records: `decode` 7
  (ts 1759737600, sg `sig-<id>`, contentType `application/x-www-form-urlencoded;charset=UTF-8`), `google-page` 14 (7
  `redirect` + 7 `page`), `article` 7; the pack has 14 `Excerpt: ` lines and no `Content not retrieved` line.
- *Same run, stub mode `decode-fail`*: every source DECODE_FAILED, `url` = the normalised Google link
  (`http://stub:4010/rss/articles/shared`, `…/<key>-2`), no `publisherHost`, `metadataFetched` false, `excerpts` `[]`,
  summary = snippet (`<raw RSS title> Reuters`, e.g. `Shared stub article - Reuters Reuters`), publisher `Reuters`,
  `sourcesWithContent` 0, 0 `article` records, run COMPLETED, pack all in the snippet form.
- *In-process default page* (`StubNews` FR-61 article page without a registered match text): every pipeline gets the
  single fallback fragment P1, so a V4 run stores RETRIEVED sources with `excerpts` `[{W01,[P1]}]` and the pack item
  reads `Excerpt: Opening paragraph of this publisher page. It introduces the report in plain words for every reader.`
- Changes earlier behaviour: R2 — a Google link counted as resolved by following HTTP redirects (`/rss/articles/<x>` 302 → `/articles/<x>`), a failed page kept the feed link, a resolved URL already taken kept its Google link → decode flow: the Google page `/rss/articles/<x>` (same-host 302 then the page with `data-n-a-*`), the batchexecute POST, then the publisher page; a failed, slow or non-HTML publisher page now keeps the **publisher** URL (`<base>/articles/gone-404`, `…/pdf`) with PAGE_FAILED, `/articles/slow` (3 s) is RETRIEVED under the new 8 s timeout, `text/plain` pages are read (`metadataFetched` true, NO_TEXT for `just text`), `/redirect/3` ends on a page without attributes → DECODE_FAILED, the FR-56 refusal cases move to decoded publisher URLs (`news.decoded`) and to redirect hops of the publisher fetch, two links decoding to one publisher URL are merged into one source (tests: backend/src/test/java/com/oracul/app/research/GoogleNewsSourceIT.java, backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java)
- Changes earlier behaviour: new runs carried no `contentStatus` / `excerpts` / `publisherHost` / `sourcesWithContent` → every source of a new run carries `contentStatus` and `excerpts` (possibly `[]`) and, with a publisher URL, `publisherHost`, and counts carry `sourcesWithContent` from the READING_SOURCES commit on (also `0`): exact-JSON count comparisons gain `"sourcesWithContent":0` after `"sourcesKept":0`, the optional-wire walk keeps only `wildcardsWithoutSources` and `wildcardGroups` absent and asserts the four fields present, and its GENERAL undated item is now RETRIEVED (`contentRetrieved` true, fragment P1, no snippet) unless the test serves `StubNews.html(name)` as a NO_TEXT page (tests: backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java, backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java)
- Changes earlier behaviour: `SourceRetrieval(NewsSearchProvider, ArticleMetadataFetcher, SourceQualityTable, Clock, Duration, int)` called `ArticleMetadataFetcher.fetchDetailed` → `SourceRetrieval(NewsSearchProvider, ArticleRetriever, SourceQualityTable, Clock, Duration searchWindow, Duration stageBudget)` calling `ArticleRetriever.retrieveAll`; the reflective constructor helper and the blocking fake fetcher (interrupted-wait case) switch to a mocked `ArticleRetriever`, other assertions unchanged (tests: backend/src/test/java/com/oracul/app/research/ParallelSearchSupport.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java)
- Changes earlier behaviour: the ARTICLE stop case held the only article request kind (8 metadata fetches open) → retrieval has three request kinds sharing the 8 permits; the parameterized stop gains rows `GOOGLE_PAGE` (`news.googlePageGate`, wait for ≥ 8 `googlePageRequests`) and `DECODE` (`news.decodeGate`, wait for ≥ 8 `decodeRequests`), and the traffic snapshot adds both counts, so no request of any kind starts after a stop (tests: backend/src/test/java/com/oracul/app/runs/StopRunIT.java)
- Ranges & invariants: (a) Google-link classes (unit, `isGoogleLink`, base `http://127.0.0.1:<p>` and `https://news.google.com`): `https://news.google.com/rss/articles/X` yes, `https://NEWS.GOOGLE.COM/x` yes, `http://127.0.0.1:<p>/rss/articles/x` yes, `http://127.0.0.1:1/x` yes (host only), `http://localhost:<p>/x` no, `https://www.reuters.com/x` no, `https://news.google.com.evil.org/x` no; (b) decode-answer classes (unit, `publisherUrl`): structured answer with `http://127.0.0.1:<p>/articles/x` → it; structured with `=` / `&` escapes → unescaped URL; structured with `null` URL → empty; raw body `x http://a.example/1 y https://b.example/2` → the first; `https://news.google.com/rss/articles/x`, `https://www.google.com/url?q=x`, `https://google.com/`, `https://consent.google.com/m`, `https://fonts.gstatic.com/x`, `https://lh3.googleusercontent.com/x`, `<base>/rss/articles/x` → empty (Google host); `https://notgoogle.com/x`, `https://google.com.evil.org/x`, `<base>/articles/x` → accepted; `ftp://x/y`, `file:///etc/passwd`, empty body, `)]}'` only → empty; (c) attribute classes (unit, `attributes`): all three valid → found; id missing / blank, ts missing / blank / `17597a` / `-1` / 19 digits, sg missing / blank → empty; split over two elements → empty; inside an HTML comment or `<script>` text → empty; two valid elements → the first; (d) `fReq` of (`CBMiX`, 1759737600, `AU_yqLx`) equals the literal above with those values, `id` `a"b` is escaped `a\"b`; (e) outcome rows (IT, one kept source each, stub modes / maps of FR-61): ok → RETRIEVED with url `<base>/articles/<id>`, `publisherHost` `127.0.0.1`, one `decode` request whose `f.req` carries id / ts / `sig-<id>` and Content-Type `application/x-www-form-urlencoded;charset=UTF-8`; Google page 404 (`news.googlePages`), without attributes (`googlePage(id, null, null)` or a direct `<base>/articles/<x>` link), with blank / non-digit attributes, feed link `<base>/redirect-to?location=http%3A%2F%2Flocalhost%3A<p>%2Fx` (off-host 302 not followed: no request reaches `localhost`), feed link `<base>/redirect/6` (6 same-host redirects) → DECODE_FAILED, Google link kept, no `publisherHost`, 0 `article` requests; decode 500, 400 (wrong sg), `decode-no-url`, `decode-google-host` (`https://news.google.com/rss/articles/<id>`), `news.decoded` = `https://www.google.com/url?q=x` or `<base>/rss/articles/again` (no `/rss/articles/again` request follows), answer after the timeout → DECODE_FAILED; publisher 404, 503, `application/pdf`, no Content-Type, answer after the timeout, 6 redirects → PAGE_FAILED with url `<base>/articles/<id>` and `publisherHost`; `text/plain` page with an 80-character block → RETRIEVED, `just text` → NO_TEXT; decoded `http://localhost:<p>/articles/x`, `http://10.0.0.5/x`, `http://[::1]:<p>/x`, `<base>/redirect-to?location=http%3A%2F%2F169.254.169.254%2F` → REFUSED with url = the decoded URL, no request reaches the refused address; a non-Google feed link `http://localhost:<p>/articles/x` → REFUSED, 0 decode requests; (f) timing with `article-fetch-timeout` PT1S: publisher answer after 0.7 s → RETRIEVED, after 1.3 s → PAGE_FAILED; decode answer after 0.7 s → RETRIEVED, after 1.3 s → DECODE_FAILED; (g) concurrency (unit `ArticleRetrieverTest`, real StubNews, parameterized c ∈ {1, 8}): 20 Google links, every Google page / decode / publisher answer delayed 200 ms → `retrievalMaxOpen()` = c, all 20 PAGE_READ, the first c Google-page requests are for links 1…c; 3 links with c = 8 → maxOpen ≤ 3; (h) guard: `mayStart` false from the start → 0 retrieval requests, all NOT_ATTEMPTED; false after the k-th request → exactly k requests; (i) merge: links `/rss/articles/m1` (W01) and `/rss/articles/m2` (W02) with `news.decoded` m2 → `<base>/articles/m1` → one source S00k with `pipelineIds` [W01, W02], both `queryIds`, listed in both pipelines' `sourceIds`, ids contiguous, `sourcesKept` = rows; (j) budget (IT, `query-generation-window` PT1S, `search-window` PT2S, `stage-budget` PT3S): `articleGate` never released → READING_SOURCES commits at t0 + 3 s ± 1 s, every source NOT_ATTEMPTED with url `<base>/articles/<id>` and `publisherHost`, `metadataFetched` false, `excerpts` `[]`, summary = snippet else title, run COMPLETED with a story; `googlePageGate` held instead → NOT_ATTEMPTED with the Google link and no `publisherHost`; injected clock (`AbstractDeadlineIT`, `MutableClock`): `articleGate` held, clock advanced 91 s (deadline 3 min) → commit within 1 s of real time, every source NOT_ATTEMPTED, no `article` request starts after the advance. Invariants for every run of every IT and E2E: `contentStatus` RETRIEVED ⇔ `excerpts` non-empty (plans with pipelines); `metadataFetched` ⇔ status ∈ {RETRIEVED, NO_TEXT}; `publisherHost` present ⇔ `url` is not the Google link of a DECODE_FAILED / Google-REFUSED / pre-decode NOT_ATTEMPTED source; no stored `url` on a Google host (google.com, gstatic.com, googleusercontent.com families) unless that status; `sourcesWithContent` = number of RETRIEVED sources ≤ `sourcesKept` = rows; stored urls distinct; decode requests ≤ kept Google-link sources and `article` requests ≤ kept sources (each at most once); every excerpt's `pipelineId` ∈ the source's `pipelineIds`; no log line contains a URL, `f.req` or `garturlres`.


### FR-55 — Relevant text extraction
- Happy path (pure `FragmentExtractor.extract(body, contentType, terms)`, per kept source and per pipeline in its
  `pipelineIds`; no ChatGPT call):
  1. HTML (`text/html`, `application/xhtml+xml`): parse with jsoup (no script execution, no network); remove `script`,
     `style`, `noscript`, `template`, `svg`, `iframe`, `nav`, `header`, `footer`, `aside`, `form` and every element with
     `role="navigation"`; paragraphs = the text of each remaining `p` element (whitespace collapsed, trimmed), document
     order, blank ones dropped. `text/plain`: paragraphs = blocks separated by one or more blank lines.
  2. Terms of pipeline P = label terms ∪ query terms of P (FR-53 step 2 tokens). Match strength of a paragraph =
     2 × (distinct label terms in it) + (distinct other query terms in it), whole-token, case-insensitive.
  3. Matching paragraphs (strength ≥ 1) in order strength desc, then document order: take a paragraph when fewer than 3
     are taken and the total length stays ≤ 1,200 characters; skip it otherwise and try the next. If the first (strongest)
     paragraph alone is longer than 1,200 characters, it is cut at the last space at or before 1,199 characters and
     `…` is appended (≤ 1,200), and nothing else is taken.
  4. No matching paragraph → the first paragraph of ≥ 80 characters (cut as in step 3) is the single fragment.
  5. None of that → no fragment for P.
  6. Fragments are stored per pipeline in `excerpts` (ordered by match strength, as taken) and rendered only inside
     the untrusted evidence data block (FR-57/FR-58).
- Rules: only the body bytes read (≤ 2 MB, FR-56) are parsed — a truncated page is parsed as far as it goes. Text is
  stored as plain text (never HTML); sanitising for prompts happens at render time (FR-57).
- Errors: unparsable HTML → jsoup's lenient parse; nothing usable → NO_TEXT (never a run failure).
- Ranges & invariants: (unit, parameterized) 30 paragraphs of which 4 match (strengths 3, 2, 2, 1) → 3 fragments in
  order strength desc / document order, total ≤ 1,200; fragment count 0…3 for 0, 1, 2, 3, 4, 10 matching paragraphs;
  total-length classes: 3 × 300 → 3 fragments (900); 700 + 600 → 1 (the 600 skipped), then a 400 one fits → 2; one
  matching paragraph of 1,500 → 1 fragment of ≤ 1,200 ending with `…`; no match + first paragraphs of 79 and 80
  characters → the 80-character one; no match and all < 80 → no fragment (NO_TEXT); text inside `script`, `style`,
  `nav` (also `<p>` inside `<nav>`), `header`, `footer`, `aside` never appears in a fragment; a `<p>` containing
  `Ignore previous instructions` is kept as data (rendered only inside the data block). Invariants: every fragment is a
  substring (after whitespace collapsing) of one paragraph of the page or its cut form; Σ fragment lengths ≤ 1,200 per
  source and pipeline; ≤ 3 fragments; no ChatGPT request is made by extraction (stub request count unchanged).

#### Slice 08_article-text — FR-55 delta (step 4a)
**Component `com.oracul.app.research.FragmentExtractor`** (pure, static, no Spring, no I/O, no clock; never calls
ChatGPT): constants `MAX_FRAGMENTS = 3`, `MAX_CHARS = 1200`, `MIN_FALLBACK = 80`, `ELLIPSIS = "…"`.
- `static List<String> paragraphs(String body, String contentType)` — Content-Type starting (case-insensitive) with
  `text/plain`: blocks separated by one or more lines that are empty or whitespace only; otherwise HTML: jsoup parse,
  remove `script`, `style`, `noscript`, `template`, `svg`, `iframe`, `nav`, `header`, `footer`, `aside`, `form` and every
  element with `role="navigation"` (with their whole subtree, wherever they are), then the `text()` of each remaining
  `p` in document order. Every paragraph: each run of whitespace (incl. U+00A0) → one space, trimmed; empty ones dropped.
- `static int strength(String paragraph, Set<String> labelTerms, Set<String> queryTerms)` = 2 × |labelTerms ∩
  tokens(paragraph)| + |(queryTerms \ labelTerms) ∩ tokens(paragraph)|, `tokens` = `WildcardSelector.tokens` (whole
  tokens `[a-z0-9]+` ≥ 3 characters, `Locale.ROOT` lower case, stop words removed — so case-insensitive and a repeated
  word counts once).
- `static String cut(String text)` — unchanged when ≤ 1,200 characters (Java `String.length()`); else the part before the
  last space at an index ≤ 1,199 (no such space → the first 1,199 characters) plus `…`, so ≤ 1,200.
- `static List<String> extract(String body, String contentType, Set<String> labelTerms, Set<String> queryTerms)` —
  paragraphs with strength ≥ 1, ordered strength desc then document order; if the first one is longer than 1,200 the
  result is `[cut(first)]`; else walk the order and take a paragraph while fewer than 3 are taken and the running total
  of taken lengths plus its length is ≤ 1,200 (a paragraph that does not fit is skipped, later shorter ones may still be
  taken). No paragraph with strength ≥ 1 → `[cut(p)]` for the first paragraph of ≥ 80 characters in document order,
  else `[]`. A body that jsoup cannot make sense of is parsed leniently; a truncated (2 MB) body is parsed as far as it
  goes.
- `SourceRetrieval` calls it once per PAGE_READ source and pipeline (terms: article-retrieval.md slice-08 step 4) and
  stores the result as plain text in `excerpts` (never HTML); the pack renderer of 06 sanitises at render time.
- Stub page fixtures (FR-61): generic page (no match text) for any wildcard → `[P1]`; page with match text
  `W01 stub query 1` for pipeline W01 (label `New pandemic`, query terms {w01, stub, query}) → `[P2, P4]` (strength 3
  each, document order); the nav / header / script / style / footer texts, which also carry the match text, never appear.
- Changes earlier behaviour: every pack item was in the snippet form (`contentRetrieved` false, `fragments` [], `snippet` = summary, `Content not retrieved. Snippet: …` lines) because no source had fragments → sources whose publisher page was read carry fragments, so their items are `contentRetrieved` true with `fragments` and no `snippet`, and the pack text has `Excerpt: <fragment>` lines: V4 runs give `Excerpt: ` + P1 for E001–E004 (expected text helper), the seven-source run's shared item repeats its `Excerpt:` line under both headings, the E2E acceptance pack has 14 `Excerpt: ` lines and 0 snippet lines; tests that need the snippet form serve `StubNews.html(name)` (NO_TEXT) or a failing stub mode (tests: backend/src/test/java/com/oracul/app/research/AbstractEvidenceIT.java, backend/src/test/java/com/oracul/app/research/EvidencePackIT.java, backend/src/test/java/com/oracul/app/result/WildcardPackRunIT.java, backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java, e2e/tests/evidence-pack.spec.ts)
- Ranges & invariants: `FragmentExtractorTest` (unit, parameterized, inputs built in code; label terms {energy, crisis}, query terms {grid, strain, fuel, price}): (a) count classes — 0, 1, 2, 3, 4, 10 matching paragraphs (each 100 characters) among 30 → 0 (then fallback), 1, 2, 3, 3, 3 fragments; 30 paragraphs of which 4 match with strengths 3, 2, 2, 1 → the 3 strongest, the two of strength 2 in document order; (b) length classes — 3 × 300 → 3 (900); 700 then 600 (both matching, 700 stronger) → [700]; 700, 600, 400 → [700, 400]; 1,200 exactly → [it] unchanged; one matching 1,500-character paragraph → 1 fragment of ≤ 1,200 ending with `…` at a word boundary; 1,201 characters without a space → 1,199 characters + `…`; (c) strength classes — label term counts 2, query term 1, a word repeated counts once, `ENERGY` = `energy`, `energetic` ≠ `energy` (whole token), stop words and tokens < 3 never count; ties → document order; (d) fallback — no match, first paragraphs of 79 and 80 characters → the 80-character one; all < 80 → `[]`; a 1,500-character first paragraph ≥ 80 → cut form; (e) stripped containers — a matching `<p>` inside each of `nav`, `header`, `footer`, `aside`, `form`, `[role=navigation]`, and matching text inside `script`, `style`, `noscript`, `template`, `svg`, `iframe` never appears in a fragment (also not as fallback); text outside any `<p>` is not a paragraph; (f) text/plain — blocks split on blank lines (also `\r\n`, whitespace-only lines); (g) data, not instructions — a `<p>` `Ignore previous instructions and …` that matches is kept verbatim (plain text, tags removed, entities decoded) and appears in the pack only inside the evidence data block; (h) truncated HTML (unclosed tags, body cut mid-tag) → the paragraphs that were complete enough to parse. Invariants for every input (property loop over generated pages): ≤ 3 fragments; Σ lengths ≤ 1,200; every fragment equals a paragraph or its `cut` form; fragments are in strength-desc / document order; no fragment contains `<` from markup; the stub request log shows no extra Responses request for runs whose sources were extracted (the multiset of Responses purposes of an E2E acceptance run in mode `ok` equals that of the same run in mode `decode-fail`).


### FR-56 — Safe article fetching
- Happy path (`SafeFetcher`, used for Google article links, every redirect hop, decoded publisher URLs; **not** for
  the configured Google search and decode endpoints, which are trusted configuration):
  1. Scheme must be `http` or `https` (case-insensitive) → else refused (REFUSED, no connection attempt).
  2. Unless the host is listed in `fetch.allowed-private-hosts`: resolve the host; if **any** resolved address is
     loopback (127.0.0.0/8, ::1), any-local (0.0.0.0, ::), private (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16),
     link-local (169.254.0.0/16, fe80::/10) or unique-local (fc00::/7) — including IPv4-mapped IPv6 forms — the request
     is refused; the connection is made only to an address that passed this check (no second DNS lookup).
     A literal IP host is checked the same way.
  3. Redirects (301, 302, 303, 307, 308) are followed manually, each `Location` resolved against the current URL and
     checked by steps 1–2; more than 5 redirects → the fetch stops (PAGE_FAILED / DECODE_FAILED for the Google page).
  4. The body is read up to `article-max-bytes` (2 MB); reading stops there and only the bytes read are used.
  5. Timeout covers connect, every redirect hop and the body (8 s per fetch, cut to the budget).
- Rules:
  - The exemption list is exact host names (`stub`, `127.0.0.1`), never ranges; real mode (`docker compose up -d`)
    sets none. The E2E stack sets `ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub` in `docker-compose.e2e.yml` (stub
    on a private Docker address); the backend test base class sets `127.0.0.1`.
  - Fixes the phase-01 low finding "SSRF via article redirects".
- Errors: refused URL or hop → REFUSED ("content not retrieved"), log `article fetch refused: blocked-address` /
  `scheme`; never a run failure.

#### Slice 02_safe-fetching — delta (step 4a)
Scope of this slice: `SafeFetcher` itself and its use by the existing article metadata fetch
(`ArticleMetadataFetcher`, called by `SourceRetrieval` for every kept candidate). `contentStatus`, the Google decode
and the publisher fetch come in 08; until then "content not retrieved" means, for a source whose fetch was refused or
stopped: `url` = the feed link (normalised), `metadataFetched` = false, `publisher` from the feed `<source>`,
`summary` = title — exactly the existing "fetch failed" outcome of FR-13 / FR-48. No API, contract, database or UI
change (no `data-testid`). `api/openapi.yaml` is unchanged.

**Component `com.oracul.app.research.SafeFetcher`** (`@Component`)
- `public interface HostResolver { List<InetAddress> resolve(String host) throws UnknownHostException; }` (nested);
  production = `InetAddress.getAllByName`. A literal IP host (`127.0.0.1`, `[::1]`) is parsed, never looked up.
- Spring constructor reads `oracul.news.fetch.allowed-private-hosts` (default empty), `oracul.news.article-max-bytes`
  (default `2097152`, was `524288`), `oracul.news.article-max-redirects` (default `5`) and
  `oracul.news.article-fetch-timeout` (default stays `PT3S` in this slice; 08 moves it to `PT8S`).
- Package-private test constructor `SafeFetcher(HostResolver resolver, Set<String> allowedPrivateHosts, Duration timeout,
  int maxBytes, int maxRedirects)`.
- `public Result fetch(URI uri)` (configured timeout) and `public Result fetch(URI uri, Duration timeout)` (08 passes the
  budget-cut timeout). One `GET` per hop, header `Accept: text/html,application/xhtml+xml`, `Host` = host[:port] of
  that hop. HTTPS verifies the certificate for the host name (SNI = host name) while the socket is connected to the
  checked address (e.g. a plain-socket client in the style of `common/RawHttpGet`).
- `record Result(Outcome outcome, int status, String contentType, byte[] body, boolean truncated, URI finalUri,
  int redirects)`; `enum Outcome { OK, REFUSED_SCHEME, REFUSED_ADDRESS, TOO_MANY_REDIRECTS, FAILED }`.
  - `OK`: a non-redirect answer of any status was received; `status`/`contentType` of it, `body` = at most
    `maxBytes` bytes, `truncated` = the server had more bytes than `maxBytes` (reading stopped, connection closed),
    `finalUri` = URL of the answering hop, `redirects` = redirect answers followed.
  - `REFUSED_SCHEME`: scheme of the start URL or of a `Location` is not http/https (incl. missing scheme after
    resolution, `file:`, `ftp:`, `gopher:`, `javascript:`, `data:`); no connection is opened for it.
  - `REFUSED_ADDRESS`: the host of the start URL or of a hop is not exempt and at least one resolved address is blocked
    (step 2); no connection is opened to any address of that host.
  - `TOO_MANY_REDIRECTS`: the (maxRedirects + 1)-th redirect answer arrived; its `Location` is not requested.
  - `FAILED`: unparsable URL, unknown host, connect error, timeout (whole fetch), malformed answer, redirect status
    without `Location`, interrupted (interrupt flag restored).
  - Redirect = status 301, 302, 303, 307, 308 with `Location`; every other 3xx is an `OK` answer with that status.
- Exemption list: comma-separated, entries trimmed, blanks ignored, compared case-insensitively and exactly with the
  URL host (IPv6 literal without brackets); no wildcards, no ranges, no suffix match (`stub` ≠ `stub2`, `stub.`,
  `x.stub`; `127.0.0.1` ≠ `localhost`). An exempt host is still resolved through the resolver and connected to the
  resolved address.
- Log (WARN, logger `com.oracul.app.research.SafeFetcher`): `article fetch refused: blocked-address host=<host>` or
  `article fetch refused: scheme scheme=<scheme>`; the full URL and query string are not logged.

**`ArticleMetadataFetcher`** keeps `fetch(String)`, `fetchDetailed(String, int)`, `parse`, `Metadata`, `Fetched`, the
virtual-thread executor and its interrupt behaviour; its own `HttpClient` and the `(Duration, int)` constructor go —
package-private constructor `ArticleMetadataFetcher(SafeFetcher fetcher)`. Every fetch goes through `SafeFetcher`; it
returns metadata only for outcome `OK` with status 2xx and type `text/html` / `application/xhtml+xml`, parsing only the
bytes read. Redirect limit = min(argument, `article-max-redirects`); `fetch(String)` uses `article-max-redirects` (was
3). `SourceRetrieval` keeps calling `fetchDetailed(link, 5)`.

**Test harness** (tester): `StubNews.registerBaseUrls` also registers `oracul.news.fetch.allowed-private-hosts=127.0.0.1`
so both test bases (`AbstractRunIT` via `StubOpenAi.registerAll`, `AbstractNewsSearchIT`) exempt the in-process stub
without a new Spring context. `StubNews` gains `GET /redirect-to?location=<url-encoded>` → 302 with that `Location`
verbatim, and `GET /big/<bytes>?meta-at=<offset>` → 200 `text/html` of exactly `<bytes>` bytes with
`<meta property="og:description" content="Big page text">` starting at byte `<offset>` (default 0) — used through
`/rss/articles/…`-style redirects (`/redirect-to?location=<base>/big/…`) so the source counts as resolved.

**Integration cases** (add to `SourceMetadataIT`, which already has its own configuration — no new context): run
COMPLETED in every case; a feed link `<base>/redirect-to?location=…` with
- `http://localhost:<stub port>/articles/internal-1` (host not exempt, resolves to loopback) → refused at the hop:
  url = feed link, `metadataFetched` false, summary = title, `news.articleRequests` lacks `internal-1`;
- `http://169.254.169.254/latest/meta-data`, `http://10.0.0.5/x`, `http://[::1]:<stub port>/articles/internal-2`
  → same refused outcome;
- `file:///etc/passwd` → refused (scheme), same outcome;
- `<base>/big/2200000` (meta at 0) → `metadataFetched` true, summary `Big page text` (2 MB read, rest ignored);
- `<base>/big/2200000?meta-at=2100000` → `metadataFetched` true, summary = title (tag lies beyond the bytes read);
- `<base>/big/2000000?meta-at=1900000` → `metadataFetched` true, summary `Big page text` (was cut at 512 KB before).
And a feed link `http://127.0.0.1:<stub port>/rss/articles/ok` keeps resolving (exemption by exact name).

**Docker** (backend-builder): `docker-compose.e2e.yml` backend environment gets
`ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub`; `docker-compose.yml` gets no such variable. Compose files and
profiles of both stack modes are unchanged, so `.oracul/stack.json` stays as it is. A plain backend test (no Spring
context, reads `../docker-compose.e2e.yml` and `../docker-compose.yml`) asserts: the e2e file has exactly one line
`ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub` (quoted or not), no other value; the real file contains no
`ALLOWED_PRIVATE_HOSTS`. The existing E2E `search-sources.spec.ts` (sources resolve to `http://stub:4010/articles/…`)
keeps passing unchanged and is the live proof that the stub host is exempt.

- Changes earlier behaviour: `ArticleMetadataFetcher(Duration, int)` with its own `HttpClient`, fetching any address → `ArticleMetadataFetcher(SafeFetcher)`; the unit test against a `127.0.0.1` HttpServer gets a SafeFetcher whose exemption is `127.0.0.1` with otherwise unchanged assertions, plus a case that without the exemption every fetch is empty; the fake fetcher passes a SafeFetcher to `super` (tests: backend/src/test/java/com/oracul/app/research/ArticleMetadataFetcherTest.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalUnitTest.java)
- Changes earlier behaviour: article pages on the in-process stub `127.0.0.1` were fetched without any address check → loopback is refused unless exempt; the test bases register the exemption `127.0.0.1` in `StubNews.registerBaseUrls` and the stub gains `/redirect-to` and `/big/<bytes>` (tests: backend/src/test/java/com/oracul/app/research/StubNews.java)
- Changes earlier behaviour: response body cut at 512 KB (`article-max-bytes` 524288) → cut at 2 MB (2097152); the `big` case of `extractionRulesAndFallbacks` (600,000 characters, asserted as "only the first article-max-bytes are read") is no longer cut and moves to the `/big` cases above; `fetch(String)` follows up to 5 redirects instead of 3 (tests: backend/src/test/java/com/oracul/app/research/SourceMetadataIT.java)
- Ranges & invariants: unit `SafeFetcherTest` with an injected resolver and a local `HttpServer`, parameterized —
  (a) refused (`REFUSED_ADDRESS`, 0 requests reach any server): `http://127.0.0.1/`, `http://127.1.2.3/`,
  `http://[::1]/`, `http://0.0.0.0/`, `http://[::]/`, `http://10.0.0.5/`, `http://172.16.0.1/`,
  `http://172.31.255.255/`, `http://192.168.1.1/`, `http://169.254.169.254/latest/meta-data`, `http://[fe80::1]/`,
  `http://[fd00::1]/`, `http://[fc00::1]/`, `http://[::ffff:10.0.0.1]/`, `http://[::ffff:127.0.0.1]/`, a name resolving
  to 10.0.0.7, a name resolving to 93.184.216.34 and 10.0.0.7 (any blocked address refuses), `HTTP://LOCALHOST/` with
  the resolver answering 127.0.0.1 — every address of (a) also gives `true` from the package-private pure check
  `static boolean SafeFetcher.isBlocked(InetAddress)`; (b) allowed: `isBlocked` is `false` for 172.15.255.255,
  172.32.0.1, 192.169.0.1, 11.0.0.1, 93.184.216.34, 2606:4700::1, ::ffff:93.184.216.34 — tested on the pure check only, never by connecting to a public address (no real internet,
  NFR-7); (c) scheme (`REFUSED_SCHEME`, resolver never called): `file:///etc/passwd`, `ftp://x/`, `gopher://x`,
  `javascript:alert(1)`, `data:text/html,x`, `mailto:a@b.c`; `HTTP://` and `HTTPS://` upper-case are accepted;
  (d) redirect hops: exempt `start.test` (resolver → 127.0.0.1, server A) redirecting to
  `http://127.0.0.1:<port B>/actuator/health` → `REFUSED_ADDRESS`, server B receives 0 requests; redirecting to
  `file:///etc/passwd` → `REFUSED_SCHEME`; a relative `Location` resolves against the current URL; each of 301, 302,
  303, 307, 308 is followed; 300 and 304 are returned as `OK` with that status; 302 without `Location` → `FAILED`;
  (e) redirect count: chains of 0, 1, 4, 5 redirects → `OK` with `redirects` = 0, 1, 4, 5; 6 and an endless loop →
  `TOO_MANY_REDIRECTS` after exactly 6 requests (the 6th `Location` is never requested); maxRedirects 0 → the first
  redirect gives `TOO_MANY_REDIRECTS`; (f) body: 0 B, 1 B, 2 MB − 1, 2 MB, 2 MB + 1, 10 MB → `body.length` 0, 1,
  2,097,151, 2,097,152, 2,097,152, 2,097,152 and `truncated` false, false, false, false, true, true; a 10 MB body ends
  within the timeout (reading stops, not drained); (g) timeout per fetch (timeout 500 ms): a server that never answers,
  a body trickling 1 byte per 100 ms, and 5 hops of 200 ms each → `FAILED` in < 2 s; (h) exemption: list
  `" Stub , 127.0.0.1 ,"` → entries `stub`, `127.0.0.1`; `http://stub:4010/x` (resolver → 172.18.0.3) passes the check;
  `http://STUB:4010/x` passes; `http://stub2:4010/`, `http://x.stub:4010/`, `http://stub.:4010/` (resolving to
  172.18.0.4) → `REFUSED_ADDRESS`; `http://localhost/` with list `127.0.0.1` → `REFUSED_ADDRESS`; empty list → every
  blocked address refused; (i) pinning: exempt `pinned.test`, resolver `pinned.test` → 127.0.0.1 → the local server
  receives exactly one request with `Host: pinned.test:<port>` and the resolver is called exactly once per hop (no
  second lookup); (j) log: a refusal logs one WARN `article fetch refused: blocked-address host=<host>` /
  `article fetch refused: scheme scheme=<scheme>` without the query string. Invariants: no connection is ever opened to a
  blocked address unless its host name is on the exemption list; at most `maxBytes` bytes of a body are ever read;
  at most `maxRedirects + 1` requests per fetch; a refused, stopped or failed fetch never fails the run (IT cases above).

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/sources | listRunSources | — | 200 SourceList (≤ 30 items, ordered by id = Evidence order, new optional fields `contentStatus`, `excerpts`, `publisherHost`, `pipelineIds`) · 404 RUN_NOT_FOUND · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId}/research | getRunResearch | — | `searchPlan.pipelines[].candidatesConsidered` / `sourceIds`; `counts.sourcesKept` / `sourcesWithContent` |
No new operation. Outbound (not our contract): Google article page GET, `POST <decode-url>` (batchexecute, form
`f.req`), publisher page GET.

## UI
None here (FR-53–FR-56 are UI: no). The progress view shows READING_SOURCES "Reading relevant sources…" during
selection, retrieval and extraction; the results appear in the grouped SOURCES view (`wildcard-result-views.md`).
