# Spec — Result views grouped by wildcard: SOURCES and WHY THESE NEWS?

Covers: FR-60

Delta against phase-01 `future-result.md` FR-27 (SOURCES list with "used in scenario" and "counter-signal" badges,
slice 13) and FR-28 (WHY THESE NEWS? with intents, `drivenBy` chips and six summary numbers, slice 14), and the current
code `FutureResultService` (backend) and `src/app/result/` (`future-result.ts`, `why-sources.ts`, `why-news-panel.ts`).
FR-23, FR-25 and FR-26 stay as they are (the WHY chips now point at grouped items, see "Highlight").

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-27: one flat list `source-item-<evidenceId>` in Evidence-ID order, badge `source-counter-<id>` "counter-signal" | **Superseded for runs with `wildcardGroups`**: one group per wildcard, items `source-item-<pipelineId>-<evidenceId>`, excerpt or "content not retrieved", no counter-signal badge. Results without `wildcardGroups` (runs stored before this phase, reopened from Recent futures) keep the phase-01 rendering unchanged. |
| FR-28: intents with `drivenBy` chips; six numbers incl. "unique events identified", "events selected", "counter-signals retained" | **Superseded for runs with `wildcardGroups`**: per wildcard its queries with status and item count; five numbers: searches, articles considered, sources kept, sources with content, sources used in the scenario. Legacy results unchanged. |

## Purpose
The user sees, per wildcard, what ORACUL searched, what came back, and which real articles — with the passages that
mattered — fed the future, and sees plainly which wildcard found nothing.

## Data
`getFutureResult` (`FutureResult`) gains the optional `wildcardGroups` — present iff the run (for ALTERNATIVE runs:
the run that built the pack) has a search plan with `pipelines`:
| Field | Source |
|---|---|
| `wildcardGroups[]` | one per pipeline, plan order |
| `.pipelineId`, `.kind`, `.label`, `.level` (absent for GENERAL), `.heading` | the pipeline (`heading` = `New pandemic 8/10`, `Ocean desalination boom 7/10`, `General`) |
| `.queries[]` | the pipeline's `PipelineQuery` entries (`id`, `text`, `status`, `articlesReturned`) |
| `.sources[]` | the pack section's items (FR-57) in section order, each `{evidenceId, sourceId, title, publisher, publishedAt?, url, contentRetrieved, fragments, usedInScenario}`; `usedInScenario` = the evidenceId is cited by a fact of the accepted scenario's `factsUsed` |
| `research.counts` | run counts incl. the new `sourcesKept`, `sourcesWithContent` |
| `research.intents` | `[]` for new runs |
| `sources` (flat) | unchanged meaning: one entry per Evidence ID in ID order; new runs: `section` CORE, `counterSignal` false (feeds the FR-26 chip rule) |

## Behaviour

### FR-60 — SOURCES and WHY THESE NEWS? grouped by wildcard
- Happy path — SOURCES (`open-sources` toggles `sources-panel`, a `mat-card`, title `sources-title` `SOURCES`; layout
  chosen by `wildcardGroups` present):
  1. Every group has no sources (empty pack) → only `sources-empty` text exactly `No sources` (phase-02 FR-47 / FR-59
     acceptance 2: the SOURCES view is empty).
  2. Otherwise one `source-group-<pipelineId>` per group in API order, each starting with `source-group-title-<pipelineId>`
     (text exactly `heading`, e.g. `New pandemic 8/10`); a group without sources contains only
     `source-group-empty-<pipelineId>` text exactly `no current sources found`.
  3. Per source of a group, in API order, `source-item-<pipelineId>-<evidenceId>` containing (testid suffix
     `<p>-<e>` = `<pipelineId>-<evidenceId>`):
     | testid | content |
     |---|---|
     | `source-id-<p>-<e>` | evidenceId |
     | `source-title-<p>-<e>` | title; empty → `Untitled source` |
     | `source-publisher-<p>-<e>` | publisher; empty → `Unknown publisher` |
     | `source-date-<p>-<e>` | `publishedAt` as `d MMM yyyy` in UTC (`30 Sep 2026`); absent → `date unknown` |
     | `source-link-<p>-<e>` | `a`, `href` = url, `target="_blank"`, `rel="noopener noreferrer"`, text `Open source` — only for `http://` / `https://` urls (case-insensitive) |
     | `source-no-link-<p>-<e>` | `Link unavailable` — instead of the link for any other url |
     | `source-used-<p>-<e>` | `used in scenario` iff `usedInScenario` |
     | `source-excerpt-<p>-<e>` | iff `contentRetrieved`: container with one `source-fragment-<p>-<e>-<k>` per fragment (k 0-based, API order), text exactly the fragment |
     | `source-not-retrieved-<p>-<e>` | iff not `contentRetrieved`: text exactly `content not retrieved` (no excerpt) |
     No counter-signal badge exists in this layout. A source listed in two groups is rendered in both with its
     group's fragments.
- Happy path — WHY THESE NEWS? (`open-why-news` toggles `why-news-panel`, title `why-news-title` `WHY THESE NEWS?`):
  4. One `why-news-group-<pipelineId>` per group (also when every group is empty), title
     `why-news-group-title-<pipelineId>` = `heading`; then per query in API order `why-news-query-<queryId>` with
     `why-news-query-text-<queryId>` (text exactly the query text), `why-news-query-status-<queryId>` (exactly `OK`,
     `EMPTY`, `FAILED`, or `PENDING`) and `why-news-query-count-<queryId>` (`1 item` / `<n> items`, n =
     `articlesReturned`, `0 items`); then `why-news-group-sources-<pipelineId>` `1 source` / `<n> sources` (n = the
     group's sources) or, for an empty group, `why-news-group-empty-<pipelineId>` text exactly `no current sources found`.
  5. Then `research-summary` (title `research-summary-title` `Research summary`) with exactly five lines in this order,
     n from `research.counts` as a plain integer, singular iff n = 1:
     | testid | field | n = 1 | n ≠ 1 |
     |---|---|---|---|
     | `summary-searches` | `searches` | `1 search performed` | `<n> searches performed` |
     | `summary-articles` | `articlesConsidered` | `1 article considered` | `<n> articles considered` |
     | `summary-kept` | `sourcesKept` (absent → 0) | `1 source kept` | `<n> sources kept` |
     | `summary-content` | `sourcesWithContent` (absent → 0) | `1 source with content` | `<n> sources with content` |
     | `summary-used` | `sourcesUsed` | `1 source used in the scenario` | `<n> sources used in the scenario` |
     No `summary-events`, `summary-selected`, `summary-counter-signals`, intent or `drivenBy` element in this layout.
- Rules:
  - Layout switch: `wildcardGroups` present → grouped layout in both panels; absent → the phase-01 layout of
    future-result.md slices 13/14, unchanged (testids, texts, badges).
  - **Highlight (FR-26 in the grouped layout)**: a click on an enabled WHY chip (enabled ⇔ its id is in the flat
    `sources`) opens SOURCES, gives every `source-item-*-<evidenceId>` of that id the class `highlighted` and
    `data-highlighted="true"` for 3,000 ms, and scrolls the first of them into view; all other items have neither.
  - All texts are interpolated (never `innerHTML`): titles, fragments, query texts and custom labels show literally.
  - No extra HTTP call: both panels use `getFutureResult` (called once per runId); `getRunResearch` and
    `getEvidencePack` are never called by the result view. ALTERNATIVE runs show the parent's groups and counts.
  - Panels start closed and stay independent (phase-01).
- Errors: as FR-23 (result endpoint: 404 RUN_NOT_FOUND "Future not found" / 409 RESULT_NOT_READY → failure view).
- Ranges & invariants (frontend unit, parameterized; E2E for the real path): groups 0 (absent → legacy), 1, 2, 3 in
  API order; per group 0 sources (→ `source-group-empty-*`, and `why-news-group-empty-*`), 1 and 5 sources; all groups
  empty → `sources-empty` only and no `source-group-*` while WHY THESE NEWS? still lists every group with its queries;
  fragments 0 / 1 / 3 → `source-not-retrieved` / 1 / 3 `source-fragment-*`; `contentRetrieved` × `usedInScenario` = 4
  classes, badge / excerpt present ⇔ flag; url classes `https://…`, `http://…` → link; `javascript:…`, `ftp://…`,
  `""` → `source-no-link`; date classes as FR-27; query status classes OK / EMPTY / FAILED / PENDING shown verbatim;
  count classes n = 0, 1, 2, 1234 for every count line and for `why-news-query-count` (`0 items`, `1 item`, `2
  items`, `1234 items`) and `why-news-group-sources` (`1 source`, `2 sources`); a source in two groups → two items
  with the same `source-id` text and both highlighted by one chip click; XSS classes (`<img src=x onerror=alert(1)>`
  as title, fragment, query text, custom label) render literally with no element. E2E invariants for every grouped
  result: group ids and headings = `getRunResearch` pipelines; per group the source items = the pack section's items
  (`getEvidencePack.wildcardSections`); `summary-*` numbers = `GET /api/runs/{id}` counts; `summary-kept` n = number of
  distinct evidenceIds over all groups; `summary-content` n ≤ `summary-kept` n; `summary-used` n = number of distinct
  evidenceIds with `usedInScenario`.

#### Slice 09_wildcard-results — FR-60 delta (step 4a)
Scope of this slice: all of FR-60 (backend `wildcardGroups`, grouped SOURCES, grouped WHY THESE NEWS?, highlight).
Contract `api/openapi.yaml` 0.7.0 already has `FutureResult.wildcardGroups`, `ResultWildcardGroup`, `ResultGroupSource`
and `ResearchCounts.sourcesKept` / `sourcesWithContent`; this slice changes descriptions only (contract-notes.md
"Slice 09"). No path, status, `ApiError.code` or rename. `.oracul/stack.json`, compose files, nginx and the E2E stub are
unchanged.

**Backend `com.oracul.app.result.FutureResultService.result`** (`GET /api/runs/{runId}/result`, `getFutureResult`):
- `wildcardGroups` present ⇔ the run's `searchPlan.pipelines` is non-empty AND the run's Evidence Pack (for an
  ALTERNATIVE run the reused parent pack; its plan is the copied parent plan) has `wildcardSections` ≠ null. Otherwise
  absent — never `[]`, never `null` (legacy pack, phase-01/02 plan; existing `FutureResultMixin` NON_EMPTY).
- One `ResultWildcardGroup` per pipeline in plan order: `pipelineId` = pipeline `id`, `kind`, `label`, `level` (absent
  for GENERAL), `heading`, `queries` = the pipeline's `PipelineQuery` entries copied in order (`id`, `text`, `status`,
  `articlesReturned`), `sources` = the items of the pack section whose `pipelineId` matches, in section order (no such
  section → `[]`). Item → `ResultGroupSource`: `evidenceId`, `sourceId`, `title`, `publisher`, `publishedAt` (absent
  when the item has none), `url`, `contentRetrieved`, `fragments` (copy; `[]` when none), `usedInScenario` = the
  `evidenceId` is in ⋃ `factsUsed[*].evidenceIds` of THIS run's accepted (guard-cleaned) scenario — the same rule as the
  flat `sources`. The pack's `snippet` is not exposed.
- No `null` anywhere inside `wildcardGroups`: NON_NULL mixins for `ResultWildcardGroup.level` and
  `ResultGroupSource.publishedAt` (slice-01 review finding).
- Unchanged: flat `sources`, `research.intents` (`[]` for new runs), `research.counts`, `metadata`, the other keys.
- Errors unchanged: unknown / malformed / foreign runId → 404 `RUN_NOT_FOUND` "Future not found"; run not COMPLETED
  with a story → 409 `RESULT_NOT_READY` "This future is not ready yet". No 500 for any group shape.

**Frontend** (route `/futures/:runId`, folder `src/app/result/`):
- `FutureResultComponent` binds `[groups]="r.wildcardGroups ?? null"` on `app-why-sources`; `WhySourcesComponent` gains
  `groups = input<ResultWildcardGroup[] | null>(null)`; grouped layout ⇔ `groups()` is non-null.
- New `wildcard-sources.ts` (`app-wildcard-sources`, inputs `groups`, `highlighted: string | null`) renders the SOURCES
  body; new `wildcard-why-news.ts` (`app-wildcard-why-news`, inputs `groups`, `counts: ResearchCounts`) renders the
  WHY THESE NEWS? body. `sources-panel` / `sources-title` `SOURCES`, `why-news-panel` / `why-news-title` `WHY THESE
  NEWS?`, `research-summary` / `research-summary-title` `Research summary` exist in both layouts. The legacy
  `why-news-panel.ts` and the flat list stay byte-for-byte in behaviour for results without `wildcardGroups`.
- Elements and texts (exact; `<p>` = pipelineId, `<e>` = evidenceId, `<q>` = query id, `<k>` = 0-based fragment index):
  SOURCES — all groups' `sources` empty → only `sources-empty` `No sources` (no `source-group-*`); else per group in API
  order `source-group-<p>` containing `source-group-title-<p>` (= `heading`) and either `source-group-empty-<p>` `no
  current sources found` or per source in API order `source-item-<p>-<e>` with `source-id-<p>-<e>` (evidenceId),
  `source-title-<p>-<e>` (title, empty → `Untitled source`), `source-publisher-<p>-<e>` (empty → `Unknown publisher`),
  `source-date-<p>-<e>` (`d MMM yyyy` UTC, e.g. `5 Oct 2026`; absent / unparsable → `date unknown`), `source-link-<p>-<e>`
  (`<a>`, `href` = url, `target="_blank"`, `rel="noopener noreferrer"`, text `Open source`; only for `^https?://` case-
  insensitive) or `source-no-link-<p>-<e>` `Link unavailable`, `source-used-<p>-<e>` `used in scenario` iff
  `usedInScenario`, and `source-excerpt-<p>-<e>` with one `source-fragment-<p>-<e>-<k>` per fragment (text exactly the
  fragment) iff `contentRetrieved`, else `source-not-retrieved-<p>-<e>` `content not retrieved`. No `source-counter-*`,
  no `source-item-<e>` element in this layout.
  WHY THESE NEWS? — per group in API order `why-news-group-<p>` with `why-news-group-title-<p>` (= `heading`), per query
  in API order `why-news-query-<q>` with `why-news-query-text-<q>` (text), `why-news-query-status-<q>` (`OK` / `EMPTY` /
  `FAILED` / `PENDING`, a `mat-chip`), `why-news-query-count-<q>` (`1 item` / `<n> items`), then
  `why-news-group-sources-<p>` (`1 source` / `<n> sources`) or, for an empty group, `why-news-group-empty-<p>` `no
  current sources found`; then `research-summary` with exactly `summary-searches`, `summary-articles`, `summary-kept`,
  `summary-content`, `summary-used` in this order (texts of FR-60 step 5). No `why-news-empty`, `why-news-intent-*`,
  `summary-events`, `summary-selected`, `summary-counter-signals`, `summary-sources-used` in this layout.
- Highlight: a click on an enabled `why-evidence-<order>-<e>` chip (enabled ⇔ `e` is in the flat `sources`) opens
  SOURCES, gives every `source-item-<p>-<e>` with that `e` the class `highlighted` and `data-highlighted="true"` for
  3,000 ms (a new click restarts the timer and moves the highlight), and scrolls the first of them (DOM order) into view.
- HTTP: exactly one `GET /api/runs/{id}/result` per runId; the result view never calls `/research`, `/sources`,
  `/events` or `/evidence-pack`.

**Test plan**:
- Backend IT (AbstractStoryIT; e.g. FutureResultIT or a new `WildcardResultIT`): (1) V4 under body A → 2 groups:
  W01 `{kind CATALOGUE, label "New pandemic", level 8, heading "New pandemic 8/10"}` with `queries` equal to
  `GET /research` `pipelines[0].queries` and `sources` = `wildcardSections[0].items` without `snippet` plus
  `usedInScenario`; W02 `Humanoid robot boom 6/10` with `sources` `[]`; (2) GENERAL body B → 1 group without `level`,
  heading `General`; an undated item has no `publishedAt`; (3) seven sources with one shared (WildcardPackRunIT
  fixture) → `E001` in both groups with each group's own fragments, same `sourceId`; (4) empty feed → every group
  present with `sources` `[]`; (5) stored legacy pack (FutureResultIT legacy case) → no `wildcardGroups` key; (6)
  ALTERNATIVE of (1) → groups equal the parent's except `usedInScenario`, which follows the alternative's scenario;
  (7) raw body: no `null` inside `wildcardGroups`; (8) 404 / 409 unchanged.
- Frontend unit (Vitest, route harness as `sources-panel.spec.ts`: `RouterTestingHarness` on `/futures/<id>`, flush
  `GET /api/runs/<id>` COMPLETED and `GET /api/runs/<id>/result` with a fixture carrying `wildcardGroups`) — new spec
  files, e.g. `wildcard-sources.spec.ts`, `wildcard-why-news.spec.ts`; every class of "Ranges & invariants" below.
- E2E (stub stack, new spec file e.g. `e2e/tests/wildcard-results.spec.ts`, body PE of wildcard-evidence.md FR-59
  delta: New pandemic 8 then Energy crisis 3, started through the panel or `POST /api/runs`):
  1. FR-60 acceptance 1 — mode `ok`: `open-sources` → `source-group-W01`, `source-group-W02` in this order with titles
     `New pandemic 8/10`, `Energy crisis 3/10`; per group the `source-item-<p>-<e>` testids equal, in order, the API
     group's sources; per item id / title / publisher / date texts as above, link `href` = url, `target` `_blank`,
     `rel` `noopener noreferrer`; `source-fragment-*` texts = the API fragments; `source-used-*` ⇔ `usedInScenario`;
     0 elements matching `^source-counter-` or `^source-item-E\d+$`.
  2. FR-60 acceptance 2 — mode `publisher-fail`: every item has `source-not-retrieved-<p>-<e>` `content not retrieved`
     and no `source-excerpt-*`; `summary-content` `0 sources with content`.
  3. FR-60 acceptance 3 — mode `ok`, `open-why-news`: groups W01, W02; Q01–Q03 under W01, Q04–Q06 under W02, status
     `OK`, count text from `articlesReturned`; the five summary lines equal `GET /api/runs/{id}` counts (`6 searches
     performed`, …); none of the legacy summary / intent / `why-news-empty` elements.
  4. FR-60 acceptance 4 — mode `empty-for` term `W02`: `source-group-empty-W02` and `why-news-group-empty-W02` `no current
     sources found`, Q04–Q06 `EMPTY` `0 items`, W01 normal.
  5. FR-59 acceptance 2 — mode `empty`: `sources-empty` `No sources`, no `source-group-*`; WHY THESE NEWS? still shows
     `why-news-group-W01` and `-W02` with every query `EMPTY` and both `why-news-group-empty-*`; mode `down`: every
     `why-news-query-status-*` `FAILED`.
  6. Highlight — mode `ok`: e1 = first evidenceId of causal step 1 (the stub cites the first pack item, `E001`, the
     shared stub article); click `why-evidence-1-<e1>` → `sources-panel` visible, `[data-highlighted="true"]` count =
     number of API groups listing e1 (2), each `source-item-<p>-<e1>` has class `highlighted`, the first is in the
     viewport; after 3.5 s the count is 0.
  7. Reload `/futures/<id>` → panels closed, one `GET …/result` per load.

- Changes earlier behaviour: `getFutureResult` of a new run had exactly the nine keys runId … openCriticIssues and no `wildcardGroups` → new runs also carry `wildcardGroups` (ten keys); FutureResultIT `aCompletedRunServesTheFullFutureResult` key set gains `wildcardGroups` (its legacy-pack case keeps no `wildcardGroups`); OptionalWireFieldsAbsentIT moves `wildcardGroups` from the "absent" walk to "present" (2 groups for V4 under body A, no `null` inside), so with FR-59 its OPTIONAL walk becomes empty and both fields are asserted present (tests: backend/src/test/java/com/oracul/app/result/FutureResultIT.java, backend/src/test/java/com/oracul/app/result/OptionalWireFieldsAbsentIT.java)
- Changes earlier behaviour: the SOURCES panel of a new run listed flat `source-item-<E>` items with `source-counter-<E>` badges and the WHY chip highlighted `source-item-<E>` → new runs show `source-group-<W>` with `source-item-<W>-<E>` (and the `-<W>-<E>` id / title / publisher / date / link / used testids), no counter badge, and the chip highlights every `source-item-<W>-<E>` of that id (2 for the shared stub article E001); the FR-26 chip test and the FR-27 list test switch to the grouped testids (tests: e2e/tests/why-and-sources.spec.ts)
- Changes earlier behaviour: WHY THESE NEWS? of a new run showed `why-news-empty` "No research intents recorded" and the six legacy lines (`summary-events`, `summary-selected`, `summary-counter-signals`, `summary-sources-used`) → `why-news-group-W01` / `-W02` with their queries and the five lines `summary-searches`, `summary-articles`, `summary-kept`, `summary-content`, `summary-used`; no `why-news-empty` (tests: e2e/tests/why-these-news.spec.ts)
- Ranges & invariants: frontend unit, parameterized (fixtures built in code): (a) layout switch — `wildcardGroups` absent → the legacy elements only (`source-item-<E>`, `why-news-empty` / intents, six legacy summary lines) and no grouped testid; present → grouped testids only; (b) groups 1, 2, 3 in API order (testid order = API order, headings verbatim incl. `General`); (c) per group 0 sources (→ `source-group-empty-<p>` and `why-news-group-empty-<p>`), 1, 5 sources; all groups empty → `sources-empty` only and no `source-group-*`, while WHY THESE NEWS? lists every group and its queries; (d) fragments 0 / 1 / 3 → `source-not-retrieved` / 1 / 3 `source-fragment-*` with exact texts; `contentRetrieved` × `usedInScenario` (4 classes) → excerpt / not-retrieved and `source-used` present ⇔ flag; (e) url classes `https://a.example/x`, `HTTP://A.EXAMPLE/x` → link; `javascript:alert(1)`, `ftp://a/b`, `""`, `/relative` → `source-no-link` `Link unavailable`; (f) date classes `2026-09-30T23:59:59Z` → `30 Sep 2026`, `2026-01-01T00:00:00+02:00` → `31 Dec 2025` (UTC), absent → `date unknown`, `not-a-date` → `date unknown`; empty title / publisher → `Untitled source` / `Unknown publisher`; (g) query status OK / EMPTY / FAILED / PENDING shown verbatim; count n ∈ {0, 1, 2, 1234} → `0 items`, `1 item`, `2 items`, `1234 items`; group sources n ∈ {1, 2} → `1 source`, `2 sources`; every summary line n ∈ {0, 1, 2, 1234} singular iff 1; `sourcesKept` / `sourcesWithContent` absent → `0 sources kept` / `0 sources with content`; (h) a source in two groups → two items with the same `source-id` text, one chip click highlights both (class and `data-highlighted="true"`, count 2), all others have neither, after 3,000 ms (fake timers) none; a chip whose id is not in the flat `sources` is disabled and a click highlights nothing; a second chip click within 3,000 ms moves the highlight; (i) XSS classes — `<img src=x onerror=alert(1)>` as title, publisher, fragment, query text and custom label/heading render literally (text equals the string, no `img` element in the panel); (j) HTTP — exactly one `GET /api/runs/<id>/result`, no `/research`, `/sources`, `/events`, `/evidence-pack` request (`HttpTestingController.verify`). Backend invariants for every completed run of the ITs: groups = pipelines (ids, order, headings, kinds, levels); per group `queries` = the plan's pipeline queries; per group `sources` = that pipeline's pack section items in order; ⋃ group evidenceIds = flat `sources` ids = pack Evidence IDs; distinct group evidenceIds = `counts.sourcesKept` = `counts.eventsSelected`; `contentRetrieved` ⇔ `fragments` non-empty, `fragments` ≤ 3; `usedInScenario` ⇔ cited by the accepted scenario's `factsUsed`, and the number of distinct used evidenceIds = `counts.sourcesUsed`; no `null` inside `wildcardGroups`. E2E invariants for every grouped result: group testids and headings = `GET /research` pipelines; per group item testids = `GET /evidence-pack` `wildcardSections[*].items`; `summary-*` numbers = `GET /api/runs/{id}` counts; `summary-kept` n = distinct evidenceIds over all groups; `summary-content` n ≤ `summary-kept` n; `summary-used` n = distinct evidenceIds with `usedInScenario`.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/result | getFutureResult | — | 200 FutureResult (+ optional `wildcardGroups`; `research.counts.sourcesKept` / `sourcesWithContent`) · 404 RUN_NOT_FOUND · 409 RESULT_NOT_READY · 500 INTERNAL_ERROR |

## UI
- Route `/futures/:runId` (result view), unchanged. Components in `src/app/result/`: the SOURCES and WHY THESE NEWS?
  panels gain the grouped layout (e.g. `wildcard-sources.ts` `app-wildcard-sources`, `wildcard-why-news.ts`
  `app-wildcard-why-news`, chosen by the existing panels when `wildcardGroups` is present). Material: `mat-card`,
  `mat-list`, `mat-chip` (status), `mat-divider`.
- States: loading (`result-loading`) · grouped with sources · grouped, some groups empty · grouped, all empty
  (`sources-empty`) · legacy (phase-01 layout) · error (failure view).
- `data-testid`s (new): `source-group-<pipelineId>`, `source-group-title-<pipelineId>`, `source-group-empty-<pipelineId>`,
  `source-item-<pipelineId>-<evidenceId>`, `source-id-<p>-<e>`, `source-title-<p>-<e>`, `source-publisher-<p>-<e>`,
  `source-date-<p>-<e>`, `source-link-<p>-<e>`, `source-no-link-<p>-<e>`, `source-used-<p>-<e>`,
  `source-excerpt-<p>-<e>`, `source-fragment-<p>-<e>-<k>`, `source-not-retrieved-<p>-<e>`,
  `why-news-group-<pipelineId>`, `why-news-group-title-<pipelineId>`, `why-news-query-<queryId>`,
  `why-news-query-text-<queryId>`, `why-news-query-status-<queryId>`, `why-news-query-count-<queryId>`,
  `why-news-group-sources-<pipelineId>`, `why-news-group-empty-<pipelineId>`, `summary-kept`, `summary-content`,
  `summary-used`. Kept from phase 01: `open-sources`, `sources-panel`, `sources-title`, `sources-empty`,
  `open-why-news`, `why-news-panel`, `why-news-title`, `research-summary`, `research-summary-title`,
  `summary-searches`, `summary-articles`, and the whole legacy set for legacy results.
