# Spec — Real news search within GDELT's limits

Covers: FR-44

Delta against phase-01 `research-pipeline.md` FR-13 ("Current-news search and source retrieval", "FR-13 — GDELT
provider", fixture F240) and the current code `SourceRetrieval.search`, `GdeltNewsProvider`, `NewsProvider`,
`ResearchPipeline` (stage SEARCHING). Filtering, URL normalisation, metadata fetch, source fields and
READING_SOURCES stay as in phase-01.

Sources: real check 1 (`04_build/01_signin-fix/real-check.md`, 2026-10-04: GDELT answered
`429 "Please limit requests to one every 5 seconds"`, a successful answer took 18 s) and the GDELT DOC 2.0 API
documentation (query syntax: "OR — enclose keywords or phrases in parentheses with the capitalised word OR between
them; OR blocks cannot be nested"; exact phrases in double quotes). Analyst calls to the real API from the build
machine on 2026-10-04 were all answered 429 (shared rate limit), so the OR syntax is **not** proven live — NFR-8
check 2 is the proof (see contract-notes decision 20).

## Purpose
A real run gets current news from GDELT although GDELT allows one request every 5 seconds and answers slowly: the
planned queries go out as at most 4 OR-group requests, one at a time, spaced, with a generous timeout and one retry
after a rate-limit answer, inside a fixed time budget so the ChatGPT stages still fit into the 3-minute run deadline.

## Data
No schema or table change. `search_plan` keeps one entry per planned query (`status`, `articlesReturned`);
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

## API (must match api/openapi.yaml)
No operation changes. `getRunResearch.searchPlan.queries[].status` / `articlesReturned` and `listRunSources`
`queryIds` / `topic` follow the rules above; `SearchQueryStatus` description documents the group semantics.
Outbound (GDELT, not part of our contract): `GET /api/v2/doc/doc` (at most `max-requests` + retries per run).

## UI
No UI change (FR-44 is UI: no). The research view shows the same per-query statuses and counts.

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
