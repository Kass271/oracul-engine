# Spec — Real news search: Google News RSS first, GDELT fallback, at most 30 sources

Covers: FR-44, FR-46, FR-48

Delta against phase-01 `research-pipeline.md` FR-13 ("Current-news search and source retrieval", "FR-13 — GDELT
provider", fixture F240) and the current code `SourceRetrieval.search`, `GdeltNewsProvider`, `NewsProvider`,
`ResearchPipeline` (stage SEARCHING). Filtering, URL normalisation, metadata fetch, source fields and
READING_SOURCES stay as in phase-01 unless FR-46 / FR-48 below say otherwise.

Sources: real check 1 (`04_build/01_signin-fix/real-check.md`, 2026-10-04: GDELT answered
`429 "Please limit requests to one every 5 seconds"`, a successful answer took 18 s) and the GDELT DOC 2.0 API
documentation (query syntax: "OR — enclose keywords or phrases in parentheses with the capitalised word OR between
them; OR blocks cannot be nested"; exact phrases in double quotes). Analyst calls to the real API from the build
machine on 2026-10-04 were all answered 429 (shared rate limit), so the OR syntax is **not** proven live — NFR-8
check 2 is the proof (see contract-notes decision 20). FR-48 (scope change, clarification-log round 6): a real
Google News RSS search (`https://news.google.com/rss/search?q=…&hl=en-US&gl=US&ceid=US:en`) answered 100 items in
0.6 s without a key.

**Slice 03 supersedes parts of FR-44 (built in slice 02):** GDELT is no longer the first provider (FR-48: Google
News RSS first, GDELT only for a group whose Google request failed), and "every group FAILED → run FAILED
NEWS_UNAVAILABLE" is gone (run-control.md FR-47: the run continues with zero sources and writes a speculative
future). Grouping, elements, attribution, the GDELT request shape, spacing, 429 retry and budget of FR-44 stay.

## Purpose
A real run gets current news quickly from Google News RSS, falls back to GDELT per group, and keeps at most the 30
best-ranked sources spread over the search topics. FR-44 (slice 02): a real run gets current news from GDELT although GDELT allows one request every 5 seconds and answers slowly: the
planned queries go out as at most 4 OR-group requests, one at a time, spaced, with a generous timeout and one retry
after a rate-limit answer, inside a fixed time budget so the ChatGPT stages still fit into the 3-minute run deadline.

## Data
FR-44: no schema or table change. FR-48: Flyway `V10` adds `source.publisher_url TEXT NULL` (API `Source.publisherUrl`,
optional). `search_plan` keeps one entry per planned query (`status`, `articlesReturned`);
`counts.searches` stays the number of planned queries (OK + EMPTY + FAILED); `counts.articlesRetrieved` stays
Σ `articlesReturned`.

### Configuration (Spring property · env var for Docker · default)
| Property | Env var | Default | Rule |
|---|---|---|---|
| `oracul.news.max-requests` | `ORACUL_NEWS_MAX_REQUESTS` | `4` | groups per run; values < 1 are treated as 1 |
| `oracul.news.request-spacing` | `ORACUL_NEWS_REQUEST_SPACING` | `PT5S` | minimum time between the **starts** of two GDELT requests (retries included); `PT0S` allowed |
| `oracul.news.query-timeout` | `ORACUL_NEWS_QUERY_TIMEOUT` | `PT30S` (was `PT10S`) | connect + whole answer of one request |
| `oracul.news.rate-limit-wait` | `ORACUL_NEWS_RATE_LIMIT_WAIT` | `PT5S` | wait after a 429 answer before the one retry |
| `oracul.news.search-budget` | `ORACUL_NEWS_SEARCH_BUDGET` | `PT75S` | wall time of the whole SEARCHING stage's GDELT traffic, from stage start |
| `oracul.news.max-records-per-query` | — | `25` | per-element factor of `maxrecords` (cap 250) |
| `oracul.news.query-concurrency` | — | removed | requests are serial; the property is ignored |

Time budget against the run deadline (`oracul.run.timeout`, PT3M = 180 s, `deadlineAt = createdAt + timeout`): the
search takes at most `search-budget` (75 s) because no request starts after the budget end and every request's
timeout is cut to the budget end. Real figures (4 requests × ~18 s ≈ 72 s) fit; at least 105 s remain for
READING_SOURCES and the ChatGPT stages. No request (first attempt or retry) starts at or after the run's
`deadlineAt` either (phase-01 run guard).

Test and E2E settings: the in-process `StubGdelt.registerAll` sets `oracul.news.request-spacing=PT0S` and
`oracul.news.rate-limit-wait=PT0S`; the Docker E2E stub stack sets `ORACUL_NEWS_REQUEST_SPACING: PT0.5S` and
`ORACUL_NEWS_RATE_LIMIT_WAIT: PT0.5S` on the backend (today in `docker-compose.override.yml`; slice 03 moves the
whole stub wiring to `docker-compose.e2e.yml`). `query-timeout` and `search-budget` keep their defaults in E2E (the
stub answers at once). Real mode (`docker-compose.yml`) sets none of them.

## Behaviour

### FR-44 — Real news search within GDELT's limits
- Happy path (stage SEARCHING, `SourceRetrieval.search`):
  1. **Grouping.** The plan's queries (plan order Q01…Qn) are split into `G = min(max-requests, n')` contiguous
     groups, where n' = number of queries with a non-empty element (step 2). Group sizes differ by at most 1, the
     larger groups first (n' = 20 → 5,5,5,5; 18 → 5,5,4,4; 7 → 2,2,2,1; 3 → 1,1,1). Every query with an element is in
     exactly one group; group order = plan order.
  2. **Element of a query**: its `text` with every `"`, `(` and `)` replaced by a space, whitespace collapsed,
     trimmed, and every standalone token `OR` / `AND` / `NOT` (upper case) removed. One token → the bare token
     (`vaccines`); two or more → a quoted phrase (`"mrna vaccine approval"`, original case kept). Empty → the query
     is not sent and gets `status` EMPTY, `articlesReturned` 0.
  3. **Request per group**: `GET <gdelt.base-url>/api/v2/doc/doc` with exactly the parameters `query`, `mode=ArtList`,
     `format=json`, `maxrecords`, `sort=HybridRel`, `timespan` (phase-01 horizon mapping unchanged), URL-encoded.
     `query` = `(<e1> OR <e2> OR …) sourcelang:english` for ≥ 2 elements, `<e1> sourcelang:english` for one element
     (GDELT rejects parentheses around a single term). `maxrecords` = min(250, `max-records-per-query` × group
     size) (5 elements → 125).
  4. **Serial and spaced**: one request at a time, groups in order. A request (or retry) starts at
     max(previous start + `request-spacing`, its earliest start), never before the previous request has ended. Each
     request waits up to min(`query-timeout`, time left in the search budget) for its full answer.
  5. **429**: an HTTP 429 answer (any body, e.g. GDELT's text/plain "Please limit requests to one every 5
     seconds…") → wait `rate-limit-wait` after the answer, then send the identical request once more (also subject to
     step 4 and the budget). A second 429 → the group is FAILED. No other answer is retried.
  6. **Answer classes per group** (phase-01 provider table, now per group): 200 JSON with ≥ 1 article → answered
     with articles; 200 JSON `{}` / `{"articles":[]}` / empty body → answered empty; non-2xx (after the step-5 retry
     for 429), timeout, connection error, 200 with a non-JSON body → FAILED; not started because the search budget
     or the run deadline ran out → FAILED.
  7. **Attribution** (per answered group, every `articles[]` entry, in response order): normalised text =
     lower-case, every run of characters other than `a-z0-9` → one space, trimmed; tokens = its words. The entry's
     title is matched against the group's elements (unquoted): (a) the first element in group order whose token
     sequence occurs contiguously in the title's token sequence; else (b) the element sharing the most distinct
     tokens with the title, ties → the earlier element; else (c) (no shared token, or no title) the group's first
     element. The entry is attributed to exactly that element's query.
  8. **Per query**: group FAILED → `status` FAILED, `articlesReturned` 0. Otherwise `articlesReturned` = entries
     attributed to it; `status` OK when > 0, EMPTY when 0.
  9. **Sources (READING_SOURCES filtering, phase-01 rules)** iterate the groups in order and, inside a group, the
     entries in response order (not per query): a new normalised URL becomes the next source `S00k` with
     `queryIds = [attributed query]` and `topic` = topic of that query's intent (phase-01 mapping); a URL seen before
     appends the attributed query id to the earlier source's `queryIds` (no duplicates, kept sorted by query id).
- Rules:
  - `counts.searches` = number of planned queries (unchanged meaning for the UI); `counts.articlesRetrieved` =
    Σ `articlesReturned` = number of article entries in all answered groups.
  - Logs: `news request failed: status=<code>` / `news request failed: <ExceptionSimpleName>` /
    `news request rate-limited, retrying once` / `news request skipped: search budget exhausted`; never the query
    text or the answer body.
  - The stage still ends with one `storeSearchResults` commit; the run guard check before SEARCHING stays.
- Errors:
  - some groups FAILED, at least one group answered (with or without articles) → the FAILED groups' queries are
    FAILED, the run continues with the sources it got (no user message)
  - every group FAILED (all requests failed or were not started) → run FAILED `NEWS_UNAVAILABLE` → "ORACUL could
    not reach its news sources — try again later" at stage SEARCHING (stageIndex 3); every query FAILED; no further
    ChatGPT call. **Analyst interpretation** of "only when no request returns any source": a request that GDELT
    answered with zero articles counts as reaching the news source, so an all-EMPTY search continues as in phase-01
    (READING_SOURCES with 0 sources → FR-31 INSUFFICIENT_EVIDENCE or COMPLETED without story) — contract-notes
    decision 21.
  - the run deadline passes during the search → phase-01 guard: no further request, run ends `RUN_TIMEOUT`.
- Changes earlier behaviour: one GDELT request per planned query (20 for budget 20, 18 for 18), up to 4 in parallel, `maxrecords=25`, decoded `query` = `<plan query text> sourcelang:english`, each request answered/failed per query (request number r = query r) → at most 4 serial OR-group requests (`(<e1> OR …) sourcelang:english`, `maxrecords` = min(250, 25 × group size), e.g. 125), per-query status/articlesReturned derived from the group answer by attribution; every test that counts GDELT requests as 20/18, checks `maxrecords=25` or the per-query `query` text, or answers by `req.number()` expecting request r ↔ query r must be updated (`sourcesCarryTheTopicOfTheirFirstQueryInQueryOrder`: 4 requests with one article each give 4 sources; `failedQueriesDoNotStopTheRun`: "first 5 requests 503" now fails every group) (tests: backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java, backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java, backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java, e2e/tests/search-sources.spec.ts)
- Changes earlier behaviour: test GDELT stubs answer per request number (`f240(req)` uses `req.number()` 1…18 as the fixture row r; requests were spaced only by concurrency) → the in-process `StubGdelt` records each request's parsed OR elements (`Request.elements()`, unquoted, in order) and the global index of its first element (`Request.firstElement()`, 1-based, = number of elements in all earlier requests + 1), and `registerAll` sets `oracul.news.request-spacing=PT0S` and `oracul.news.rate-limit-wait=PT0S` so ITs do not wait 5 s per request; fixture helpers emit per element the block the old request r = global element index produced, with titles prefixed by the element text (`"<element> Article r<r>-a<a>"`, the blank-title rows stay blank) so attribution (step 7a) gives every block to its own query and F240 keeps 240 entries / 205 sources / the same S-order (tests: backend/src/test/java/com/oracul/app/research/StubGdelt.java, backend/src/test/java/com/oracul/app/research/AbstractEventIT.java)
- Changes earlier behaviour: `oracul.news.query-timeout` default PT10S, no retry of any GDELT answer → PT30S, one retry after a 429 with `rate-limit-wait` (tests: none)
- Changes earlier behaviour: E2E stub `GET /api/v2/doc/doc` answered 5 articles per request keyed by the whole query (`sha1(query)`), recorded `{query, params}` → for an OR-group query it answers, per element in order, the 5 articles the old stub gave for that element alone (`shared?utm_source=<request n>` first with title "Shared stub article", then `<sha1(element)[0..8]>-2…5` with titles `"<element> stub article <key>-<a>"`), so the acceptance run still has `searches` 20, `articlesRetrieved` 100, `articlesConsidered` 81, 81 sources, 41 events — only the recorded GDELT request count changes 20 → 4; records gain `at` (ms epoch of arrival); new `POST /__control/news` modes (see "E2E stub") (tests: e2e/tests/search-sources.spec.ts)
- Ranges & invariants: query count n' (parameterized 1…20, plus 0 when every element is empty) → G = min(4, n') requests, sizes ⌈n'/G⌉ or ⌊n'/G⌋ with the larger first, contiguous, union = all queries with an element, no query in two groups; n' = 1 → `query` without parentheses; `max-requests` classes 0 / −1 → 1 request, 1 → 1, 4 → 4, 10 with n' = 20 → 10 requests of 2. Element classes: `vaccines` → `vaccines`; `mRNA vaccine approval` → `"mRNA vaccine approval"`; `"quoted" (paren) text` → `"quoted paren text"`; `fusion OR fission` → `"fusion fission"`; `   ` / `"()"` → not sent, EMPTY. `maxrecords` = min(250, 25 × size): size 1 → 25, 5 → 125, 10 → 250, 11 → 250. Timing (unit test with injected `Clock` and sleeper, spacing 5 s, rate-limit-wait 5 s, timeout 30 s, budget 75 s): for every pair of consecutive starts (retries included) start(k+1) − start(k) ≥ spacing and start(k+1) ≥ end(k); answers at 29.9 s are used, at 30 s or later → FAILED; 429 once → exactly 2 requests for that group, the retry ≥ 5 s after the 429 answer; 429 twice → FAILED after exactly 2 requests; 500 / 503 / drop / non-JSON → FAILED after exactly 1 request; no request starts at or after budget end (budget 12 s, spacing 5 s, every answer takes 5 s → request 1 at 0 s answered, request 2 at 5 s answered, request 3 at 10 s with timeout cut to 2 s → FAILED at 12 s, request 4 not sent; groups 3 and 4 FAILED) or after `deadlineAt`; every request timeout ≤ time left in the budget. Attribution invariants for every answered group: each entry is attributed to exactly one query of that group; Σ `articlesReturned` over all queries = Σ entries of answered groups = `counts.articlesRetrieved`; a query is FAILED iff its group FAILED or was not sent, OK iff attributed > 0, EMPTY otherwise; attribution classes: exact phrase in title → that element even when a later element shares more tokens; no phrase, unequal overlap → highest overlap; equal overlap → earlier element; no overlap / missing title → first element. Sources: S-numbers follow group order then response order; every `queryIds` is sorted, without duplicates, and contains only queries of groups whose answer listed that URL. NEWS_UNAVAILABLE iff every group FAILED (classes: all 503 → yes; 3 FAILED + 1 empty → no, run continues; 3 FAILED + 1 with articles → no; all not sent because the budget is 0 → yes).

### FR-46 — At most 30 sources per run
- Happy path (stage READING_SOURCES, `SourceRetrieval.readSources`, after the phase-01 filter — normalised-URL
  dedup with `queryIds` merging, blank title, non-English language, age — and **before** any article page is
  fetched):
  1. **Usable candidates** = the filtered candidates in arrival order (groups in plan order, inside a group the
     answer order of whichever provider answered it — Google News RSS or GDELT, FR-48). n = their number.
  2. n ≤ 30 → every candidate is kept (identical to today: same order, same S-ids, same `queryIds`).
  3. n > 30 → exactly 30 are kept:
     a. **Topic buckets**: key = the candidate's `topic` (topic of the intent of its first query, phase-01 mapping;
        candidates without a topic form one bucket of their own). Buckets are ordered by the arrival index of their
        first candidate.
     b. **Rank inside a bucket**: `sourceQuality` descending (phase-01 `SourceQualityTable` on the candidate's
        domain: GDELT `domain`, Google News host of `<source url>`, else host of the article URL), ties by arrival
        index ascending (= provider relevance order).
     c. **Round robin**: in rounds, visit the buckets in bucket order and take the next-ranked candidate of every
        bucket that still has one, until 30 are taken (a partial last round favours the earlier buckets).
  4. The kept candidates are put back into arrival order and numbered `S001`…`S0k` (k = min(n, 30)) in that order;
     each keeps its full `queryIds` (every query whose answer listed that normalised URL, sorted) and its `topic`.
  5. Only kept candidates are fetched (article page / Google News redirect, FR-48) and stored; dropped candidates
     leave no row and cause no outbound request.
- Rules:
  - The limit 30 is a constant (`SourceRetrieval.MAX_SOURCES`), not configurable.
  - `counts.articlesConsidered` = k = rows in `source` = `listRunSources.items` length ≤ 30, for every run created
    from this slice on (the WHY THESE NEWS `summary-articles` shows the same number); `counts.articlesRetrieved`
    keeps counting every raw entry of the answered groups (may exceed 30).
  - Events, ranking and the Evidence Pack are built from the kept sources only; `oracul.events.max-sources` (120)
    stays as a guard but a run never reaches it.
  - ALTERNATIVE runs search nothing (unchanged).
- Errors: none (no user-facing error; n = 0 → 0 sources, the run continues, run-control.md FR-47).
- Changes earlier behaviour: every usable source was kept and fetched (F240 fixture → 205 sources / 205 events, events normalised in 6 batches of ≤ 40; E2E acceptance run → 81 sources, 41 events, 3 normalisation batches, mode `evidence` → pack 25 items) → at most 30 kept by topic round robin (F240 → 30 sources; the E2E acceptance run → 30 sources, so event, batch and pack counts derived from them shrink); `EventSourceCapIT` can only exercise the 120-source normaliser cap with a lower `oracul.events.max-sources` (tests: backend/src/test/java/com/oracul/app/research/SourceFixtureIT.java, backend/src/test/java/com/oracul/app/research/EventSourceCapIT.java, backend/src/test/java/com/oracul/app/research/EventBatchingIT.java, backend/src/test/java/com/oracul/app/research/EventConcurrencyIT.java, backend/src/test/java/com/oracul/app/research/EventConcurrencyOneIT.java, backend/src/test/java/com/oracul/app/research/EventFailureIT.java, e2e/tests/search-sources.spec.ts, e2e/tests/events.spec.ts, e2e/tests/evidence-pack.spec.ts)
- Ranges & invariants: usable count classes (parameterized unit test of the pure selection, e.g. `SourceCap.select(candidates)`, plus one IT with F240): n = 0 → 0, 1 → 1, 29 → 29, 30 → 30 (all kept, identical order/ids), 31 → 30, 60 → 30, 205 → 30. Round-robin classes: one topic with 40 → its 30 best by quality then arrival; topics A(40), B(2), C(1) in that first-appearance order → C 1, B 2, A 27; 31 topics with 1 each → the first 30 topics; topics A(20), B(20) → 15 + 15; A(20), B(20), C(20) → 10 + 10 + 10; A(16), B(16) with 30 → 15 + 15; quality ties keep arrival order; a later high-quality candidate (0.95) beats an earlier 0.6 one of the same topic. Invariants for every input: kept ⊆ usable; |kept| = min(n, 30); no URL twice; for any two topics t, u that still have unkept candidates |kept_t − kept_u| ≤ 1; a topic with no unkept candidate keeps all of its candidates; inside a topic the kept ones are its top-ranked ones; S-ids are S001…S0k without gaps in arrival order; every kept source's `queryIds` equals its pre-cap `queryIds`; `articlesConsidered` = k = stored rows = `listRunSources` length; the article fetcher is called exactly k times.

### FR-48 — Google News RSS as the main news source, GDELT as fallback
- Happy path (stage SEARCHING, `SourceRetrieval.search`; grouping and elements exactly as FR-44 steps 1–2):
  1. **Google request per group** (in group order): `GET <google.base-url>/rss/search` with exactly the parameters
     `q`, `hl=en-US`, `gl=US`, `ceid=US:en`, URL-encoded; headers `Accept: application/rss+xml, application/xml,
     text/xml` and `User-Agent: Mozilla/5.0 (compatible; ORACUL/1.0)`. `q` = `(<e1> OR <e2> OR …) when:<N>d` for ≥ 2
     elements, `<e1> when:<N>d` for one element; N by horizon: `1d`, `1w` → 7; `1m` → 14; `1y`, `5y`, `10y`, `20y` →
     90 (the same days as the phase-01 age filter).
  2. **Answer classes**: HTTP 200 whose body is well-formed XML with root `rss` and a `channel` → answered (with the
     items, possibly none). Non-2xx (incl. 429), timeout, connection error, a body that is not well-formed XML, a
     different root element, or any `<!DOCTYPE` (XXE guard: no DTD, no external entities) → **failed**. No Google
     request is ever retried.
  3. **Items**: the `channel/item` elements in feed order, at most min(250, `max-records-per-query` × group size)
     (5 elements → 125); each item is one raw entry (counted in `articlesReturned` / `articlesRetrieved`). Fields:
     `title`, `link`, `pubDate`, `source` (text) and `source@url`. Title cleaning: trim, and when it ends with
     `" - " + <source text>` (exact, after trimming the source text) that suffix is removed once.
  4. **Attribution** per answered group exactly as FR-44 step 7, on the cleaned title.
  5. **Fallback**: a group whose Google request **failed** is sent once to GDELT under the FR-44 rules (request
     shape, `request-spacing` between GDELT starts, one retry after 429, `query-timeout`) within the remaining search
     budget; its answer (with or without articles) makes the group answered, a GDELT failure or a request not started
     makes it FAILED. A group Google answered with zero items is EMPTY and is **not** sent to GDELT.
  6. **Serial schedule**: one request at a time across both providers, groups in order (Google g, its GDELT
     fallback if needed, then Google g+1 …). A Google request starts at max(previous request's end, previous Google
     start + `google.request-spacing`); a GDELT request keeps the FR-44 spacing relative to the previous GDELT start.
     Each Google request waits up to min(`google.timeout`, time left in `search-budget`) for its whole answer. No
     request (Google, GDELT, retry) starts at or after the end of `search-budget`, at or after the run's
     `deadlineAt`, or once the run is no longer RUNNING (STOP, run-control.md FR-45).
  7. **Per query** (FR-44 step 8, both providers): group FAILED or never sent → `status` FAILED, `articlesReturned` 0
     (never EMPTY); answered → OK when ≥ 1 entry attributed, else EMPTY. A query whose element is empty after
     cleaning is not sent and stays EMPTY (FR-44).
  8. **Source from a Google item** (READING_SOURCES, after FR-46): candidate URL = the item `link` (must be absolute
     `http`/`https`, else the item is dropped by the filter); `pubDate` (RFC 1123, e.g. `Sat, 03 Oct 2026 10:00:00
     GMT`) is the seen date for the age filter and `publishedAt` (unparsable → absent, age filter not applied);
     language is unknown (passes the language filter, stored NULL). The article fetch (phase-01 fetcher, ≤ 5
     redirects, `article-fetch-timeout`) starts at the link: when it follows ≥ 1 HTTP redirect and ends with a 2xx
     HTML answer at a URL different from the link → **resolved**: `url` = that final URL (normalised),
     `metadataFetched` true, `publisher` = its `og:site_name` else the item's `source` text, `summary` = its
     description (≤ 600) else the title. Otherwise (no redirect — real Google answers its own page —, non-2xx,
     timeout, not HTML) → `url` = the Google link, `metadataFetched` false, `publisher` = the `source` text (else the
     host of `source@url`, else the link host), `summary` = the title. In both cases `publisherUrl` = `source@url`
     when it is an absolute http(s) URL (else absent), `sourceType` / `sourceQuality` from the host of `source@url`
     (else the url host), `topic` / `queryIds` as FR-44 step 9. A resolved URL that equals the `url` of an earlier
     source of the same run is not used: the later source keeps its Google link (`url` stays unique per run).
  9. GDELT sources are unchanged (no `publisherUrl`).
- Rules — configuration (Spring property · env var · default):
  | Property | Env var | Default | Rule |
  |---|---|---|---|
  | `oracul.news.google.base-url` | `ORACUL_NEWS_GOOGLE_BASE_URL` | `https://news.google.com` | trailing `/` removed; tests and the E2E stack point it to a stub, never to the real Google (NFR-7) |
  | `oracul.news.google.request-spacing` | `ORACUL_NEWS_GOOGLE_REQUEST_SPACING` | `PT1S` | minimum time between the starts of two Google requests; `PT0S` allowed |
  | `oracul.news.google.timeout` | `ORACUL_NEWS_GOOGLE_TIMEOUT` | `PT10S` | connect + whole answer of one Google request |
  GDELT properties of FR-44 (`request-spacing`, `query-timeout`, `rate-limit-wait`, `max-records-per-query`) apply
  to GDELT requests only; `max-requests` (groups) and `search-budget` (whole stage, both providers) apply to both.
  Real mode sets none of them (no user configuration).
- Rules — logs: `google news request failed: status=<code>` · `google news request failed: <ExceptionSimpleName>` ·
  `google news answer is not RSS` · `news group falling back to GDELT`; never the query text, the `q` value or the
  answer body. The FR-44 GDELT log lines stay.
- Errors (no user-facing error from the search itself):
  - one group's Google request fails → GDELT for that group; the run continues with whatever the groups returned
  - every group FAILED on both providers, or nothing could be sent (budget 0, deadline) → every query FAILED,
    `articlesRetrieved` 0, 0 sources; the run continues (run-control.md FR-47: speculative future with the
    NO_EVIDENCE note) — no NEWS_UNAVAILABLE
  - run deadline / STOP during the search → no further request; the run ends RUN_TIMEOUT / stays STOPPED
- Changes earlier behaviour: GDELT was the only news provider (4 OR-group GDELT requests per run) → every group goes to Google News RSS first and only a failed Google group goes to GDELT; the in-process stub must serve `/rss/search` (default answer HTTP 503, so every group of the existing ITs falls back to GDELT and their GDELT assertions stay valid), record those requests apart from `requests`, serve `/rss/articles/<name>` → 302 `/articles/<name>`, and `registerAll` as well as `AbstractNewsSearchIT` must register `oracul.news.google.base-url` (stub base URL) and `oracul.news.google.request-spacing=PT0S` — otherwise ITs would call the real Google (tests: backend/src/test/java/com/oracul/app/research/StubGdelt.java, backend/src/test/java/com/oracul/app/research/AbstractNewsSearchIT.java)
- Changes earlier behaviour: E2E runs searched the GDELT stub (acceptance run → 4 recorded `gdelt` requests; `__control/news` modes `partial`, `rate-limited-once`, `rate-limited` decided the query statuses) → the stub's Google News RSS endpoint answers every group (4 recorded `rss` requests, 0 `gdelt` requests while `__control/rss` is `ok`); GDELT modes only matter with `__control/rss` `down` or `malformed` (tests: e2e/tests/search-sources.spec.ts)
- Changes earlier behaviour: `Source` had no publisher site URL → optional `publisherUrl` for Google News sources (GDELT sources unchanged, so existing source bodies stay equal) (tests: none)
- Ranges & invariants: `q` per horizon (parameterized over all 7 codes): `1d`/`1w` → `when:7d`, `1m` → `when:14d`, `1y`/`5y`/`10y`/`20y` → `when:90d`; one element → no parentheses; parameters exactly `q`, `hl`, `gl`, `ceid` (4 names, each once). Answer classes (each alone): 200 RSS with 3 items → answered, no GDELT request; 200 RSS with 0 items → EMPTY, no GDELT request; 404 / 429 / 500 / 503 / timeout at `google.timeout` / connection refused / 200 `not xml` / 200 truncated XML / 200 root `<html>` / 200 with `<!DOCTYPE` → exactly 1 Google + 1 GDELT request for that group. Item cap: a feed of 130 items for a 5-element group → 125 entries; 1 element → 25. Title classes: `"Vaccine approved - Reuters"` + source `Reuters` → `"Vaccine approved"`; `"A - B - Reuters"` → `"A - B"`; `"Vaccine approved"` → unchanged; source `BBC` with title ending `- Reuters` → unchanged; source text missing → unchanged. `pubDate` classes: RFC 1123 → `publishedAt` equal instant; empty / `yesterday` → absent and the item is not age-filtered; 200 days old (horizon `1y`, 90 days) → dropped. Link classes: `https://…` / `http://…` kept; missing, empty, `javascript:x`, relative → dropped. Resolution classes: 302 → 2xx HTML at another URL → resolved url + `og:site_name`; 200 at the link (no redirect) → Google link, `metadataFetched` false, publisher = source text; 302 → 404 / timeout / 6 redirects → Google link; resolved URL equal to an earlier source's url → Google link. Timing (unit test with injected clock and sleeper, google spacing 1 s, GDELT spacing 5 s, budget 75 s): Google starts ≥ 1 s apart and after the previous request ended; GDELT starts ≥ 5 s apart; every timeout ≤ time left in the budget; nothing starts after the budget end, `deadlineAt` or a STOP. Invariants for every search: requests are strictly serial; per group exactly 1 Google request and 0 or 1 (+ 1 retry after 429) GDELT requests, GDELT only after a failed Google request; Σ Google requests ≤ 4; a query is FAILED iff its group got no answer from either provider or was never sent, OK iff attributed > 0, EMPTY otherwise; Σ `articlesReturned` = Σ entries of answered groups = `articlesRetrieved`; no log line contains a query text.

## API (must match api/openapi.yaml)
No operation changes. `getRunResearch.searchPlan.queries[].status` / `articlesReturned` and `listRunSources`
`queryIds` / `topic` follow the rules above; `SearchQueryStatus` and `ResearchCounts` descriptions document the
group and cap semantics. Additive (0.6.0): optional `Source.publisherUrl` (format uri, Google News sources only).
`listRunSources` returns at most 30 items for runs created from slice 03 on.
Outbound (not part of our contract): Google News `GET /rss/search` (at most `max-requests` per run), GDELT
`GET /api/v2/doc/doc` (only for failed Google groups, + one 429 retry each), article pages / Google News links.

## UI
No UI change (FR-44, FR-46, FR-48 are UI: no). The research view and WHY THESE NEWS show the same per-query statuses
and counts (`summary-articles` ≤ 30).

## Slice 03_run-modes-readme — FR-46 / FR-48 test contract
- Unit (no Spring), `// @trace FR-46`: the pure selection (e.g. `com.oracul.app.research.SourceCap.select(List<Candidate>)`
  → kept candidates in arrival order) parameterized over the FR-46 classes and invariants.
- Unit, `// @trace FR-48`: a pure RSS parser / query builder (e.g. `GoogleNewsQuery.q(elements, horizon)`,
  `GoogleNewsRss.parse(body, cap)`, `GoogleNewsRss.cleanTitle(title, source)`) parameterized over the `q`, answer,
  item-cap, title, `pubDate` and link classes; the serial two-provider schedule with an injected clock and sleeper.
- Integration (`// @trace FR-46` / `// @trace FR-48`, `AbstractRunIT` family): F240 run → 30 sources (`articlesConsidered`
  30, S001…S030, round-robin per topic), article fetches = 30; Google stub answering per element (mirror of the GDELT
  fixture with `" - Reuters"` title suffixes and `<source url="https://www.reuters.com">Reuters</source>`) → 0
  GDELT requests, the same per-query statuses as the GDELT answer would give; Google 503 for group 2 only → exactly
  one GDELT request (group 2's elements); Google empty → EMPTY, no GDELT; `/rss/articles/<name>` 302 → sources
  resolved to `/articles/<name>` with `publisherUrl` `https://www.reuters.com`; Google 200 without redirect → Google
  link kept, `metadataFetched` false, publisher `Reuters`; budget 0 → every query FAILED, 0 requests, the run goes
  on (FR-47).
- In-process stub (`StubGdelt`): context `/rss/search` with `volatile Function<RssRequest, Reply> rssResponder`
  (default `status(503)`), `List<RssRequest> rssRequests` (`number`, `rawQuery`, decoded `params`, `q`, `elements` —
  `q` without the ` when:<N>d` suffix and outer parentheses, split on ` OR `, unquoted — `arrivedNanos`), helpers
  `rssItem(title, link, pubDate, source, sourceUrl)` / `rss(items…)`; context `/rss/articles/` → 302 `Location:
  <base>/articles/<rest>` (query dropped); `reset()` clears both; `registerAll` adds `oracul.news.google.base-url`
  and `oracul.news.google.request-spacing=PT0S`; `AbstractNewsSearchIT` registers the same two properties.
- E2E stub (`e2e/stubs/server.mjs`, not a test file — the tester updates it with the tests):
  - `GET /rss/search` records `{q, params, at}` under `GET /__control/requests?kind=rss`; mode `ok` (default):
    decode `q`, drop ` when:<N>d`, strip outer parentheses, split on ` OR `, unquote; per element answer the 5
    entries the GDELT stub gives, as RSS 2.0 (`content-type: application/rss+xml; charset=utf-8`): titles
    `"<title> - Reuters"`, links `http://stub:4010/rss/articles/shared?utm_source=<request n>` and
    `http://stub:4010/rss/articles/<sha1(element)[0..8]>-<a>`, `pubDate` = now − 1 day (RFC 1123), `<source
    url="https://www.reuters.com">Reuters</source>` — so the acceptance run keeps `searches` 20,
    `articlesRetrieved` 100 and 81 usable candidates (→ 30 kept, FR-46); mode `empty` → a valid feed without items;
    `down` → 503; `malformed` → 200 `application/rss+xml` body `<rss><channel><item><title>broken`.
  - `GET /rss/articles/<name>` → 302 `Location: http://stub:4010/articles/<name>`.
  - `POST /__control/rss {"mode": "ok" | "empty" | "down" | "malformed"}` → 204, unknown → 400
    `{"error":"unknown_mode"}`; `POST /__control/reset` resets it to `ok` and clears the `rss` records.
- E2E (`e2e/tests/search-sources.spec.ts`, `// @trace FR-46, FR-48`): acceptance run → 4 `rss` requests whose
  `params` are exactly `q`, `hl=en-US`, `gl=US`, `ceid=US:en`, `q` matches `^\(.+( OR .+)+\) when:\d+d$`, consecutive
  `at` ≥ 200 ms apart, 0 `gdelt` requests, counts 20 / 100 / 30, 30 sources S001…S030 with `publisherUrl`
  `https://www.reuters.com` and urls `http://stub:4010/articles/…`; rss `down` → 4 `rss` + 4 `gdelt` requests, run
  COMPLETED from the GDELT answers (30 sources); rss `malformed` + news `down` → every query FAILED, run COMPLETED
  with the NO_EVIDENCE note (run-control.md FR-47).

## Slice 02_plan-usage-calls — FR-44 test contract
- Unit (no Spring): a pure grouping/attribution class (e.g. `com.oracul.app.research.GdeltQueryGroups`: groups,
  element, query string, maxrecords, attribution) and the serial scheduler with an injected `Clock` and a sleeper
  (`SourceRetrieval` or a new `GdeltSearch`), so spacing, timeout, 429 retry and budget are tested without real
  waiting. Trace `// @trace FR-44`.
- Integration (`SourceRetrievalIT` and the other listed ITs, `StubGdelt` with spacing PT0S): request count, exact
  parameters, OR-query shape, per-query statuses, partial failure, all-FAILED → NEWS_UNAVAILABLE, 429-once → 2
  requests and COMPLETED, 429-twice on every group → NEWS_UNAVAILABLE.
- E2E stub (`e2e/stubs/server.mjs`, `GET /api/v2/doc/doc`): split the decoded `query` (without ` sourcelang:english`)
  into elements (strip the outer parentheses, split on ` OR `, unquote); answer per element as described above.
  `POST /__control/news` `{"mode": "ok" | "down" | "rate-limited-once" | "rate-limited" | "partial"}`:
  `down` → every request 503 (unchanged); `rate-limited-once` → the first request after the mode was set answers
  429 `text/plain` "Please limit requests to one every 5 seconds", later ones `ok`; `rate-limited` → every request
  429 (same text); `partial` → requests 1 and 2 (since the mode was set) answer 503, the rest `ok`. Unknown mode → 400
  `{"error":"unknown_mode"}`. `GET /__control/requests?kind=gdelt` returns `{query, params, at}` per request.
  `POST /__control/reset` resets the mode to `ok` and the request-number counter of the modes.
- E2E (`e2e/tests/search-sources.spec.ts`, `// @trace FR-44`): acceptance run → 4 GDELT requests, each `query`
  matches `^\(.+( OR .+)+\) sourcelang:english$`, consecutive `at` values ≥ 500 ms apart (E2E spacing PT0.5S),
  counts 20 / 100 / 81; `rate-limited-once` → 5 requests, run COMPLETED, the 2nd request ≥ 500 ms after the 1st;
  `partial` → run COMPLETED, the queries of groups 1–2 FAILED, the others OK; `rate-limited` → run FAILED
  NEWS_UNAVAILABLE with the failure message, 8 GDELT requests.
