# Spec — Session state and Recent futures

Covers: FR-33

## Purpose
Keep everything a user generated in their anonymous browser session — configuration, runs, scenarios, Evidence
Packs, sources — in PostgreSQL (never credentials), and let them reopen any of their 20 newest futures with all its
views. Other browser sessions never see these runs.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| browser_session | id | uuid | from cookie `ORACUL_SID` (chatgpt-connection.md); created by `SessionFilter` on the first `/api` request without a valid cookie |
| generation_run | session_id | uuid | every run, pack, source, event, attempt and story is reached only via a run of the caller's session |
| RecentRunSummary | id, generationId, kind, createdAt, completedAt, headline, configuration | | from generation_run rows with status COMPLETED and a non-null headline; no new table, no migration |

Rules for the session filter: cookie missing, not a UUID or unknown → new browser_session row and new cookie
(`Set-Cookie: ORACUL_SID=<uuid>; Path=/; HttpOnly; SameSite=Lax`). The session id is not a ChatGPT credential and
is never logged in full (first 8 characters only).

## Behaviour

### FR-33 — Session state and Recent futures
- Happy path: `GET /api/runs` → 200 `{items}`: the 20 newest COMPLETED runs of the caller's session ordered by
  `created_at` desc, ties by `id` desc. The header button "Recent futures" (`recent-futures-button`) opens a
  `mat-menu` (`recent-futures-list`) with one entry per run (`recent-future-<runId>`) showing time, headline and key
  settings "R<n> D<n> O<n> · <horizon label>". Selecting an entry navigates to `/futures/<runId>`, which loads
  `getRun` + `getFutureResult` and shows that run's story, metadata, WHY, SOURCES and WHY THESE NEWS? data and puts
  its configuration into the Scenario Panel.
- Rules: runs of other sessions are invisible (list filtered by session; direct access → 404). Runs beyond the 20
  newest stay stored but are not listed. FAILED / INSUFFICIENT_EVIDENCE / QUEUED / RUNNING runs are not listed.
  The list is fetched every time the menu is opened (so a run completed since the last opening is included).
  Empty list → text "No futures yet" (`recent-futures-empty`). `GET /api/scenario/configuration` returns the
  configuration of the newest run of the session (any status), else the defaults (scenario-panel.md), so a reload
  restores the panel.
- Errors:
  - `/futures/<runId>` with an unknown, malformed or other session's id → `getRun` 404 `RUN_NOT_FOUND` → existing
    failure view (`failure-view`, `failure-message` "Future not found", `try-again` navigates to `/`) — unchanged from
    generation-runs.md
  - `GET /api/runs` unexpected backend failure → 500 `INTERNAL_ERROR` "Something went wrong — try again"
  - list request fails in the UI (network error or any non-2xx) → the menu shows "Recent futures are unavailable"
    (`recent-futures-error`), no entries, no snackbar
- Changes earlier behaviour: `GET /api/runs` (no controller implements `HistoryApi` yet: 405 `METHOD_NOT_ALLOWED`) → 200 RecentRunList (checked all backend/e2e tests: none sends `GET /api/runs` or asserts 405 on it) (tests: none); `GET /api/scenario/configuration` always the defaults → configuration of the newest run of the session, defaults when the session has no run or no session/DB is available (`@WebMvcTest`), so `ScenarioControllerTest.configurationEqualsDefaultsForFreshSession` and `scenarioResponsesNeverMentionOracle` stay green unchanged (tests: none); `RunStore.open(runId)` for a run not just started in this tab → additionally calls `ScenarioStore.load(run.configuration)` once on the first successful `getRun` (checked `run-view.spec.ts`, `insufficient-evidence.spec.ts`, `scenario-metadata.spec.ts`, `run-failure.spec.ts`, `run.store.spec.ts`, `progress-view.spec.ts`: none asserts the panel keeps its values after opening a run with a different configuration; `scenario-metadata.spec.ts` "takes every value from the result" asserts only `meta-*` texts) (tests: none); header gains `recent-futures-button` between `app-wordmark` and `chatgpt-status` with no HTTP call until it is clicked (checked `app.spec.ts`, `chatgpt-connection.spec.ts` (wordmark-before-status order still holds), `wildcard-catalogue.spec.ts`, e2e `scenario-controls.spec.ts`, `chatgpt-connection.spec.ts`, `run-start.spec.ts` — no test counts header buttons or asserts the absence of extra requests on load beyond the ones they flush) (tests: none)
- Ranges & invariants: list size n of COMPLETED runs in the session ∈ {0 → `items: []` and UI `recent-futures-empty`; 1; 20 → all 20; 21 and 25 → exactly the 20 newest, the oldest 1 / 5 absent}; `items.length` ≤ 20 always; every item has status COMPLETED in the DB (session with 1 COMPLETED + 1 FAILED + 1 INSUFFICIENT_EVIDENCE + 1 RUNNING → exactly 1 item); a COMPLETED row with null headline is not listed; order strictly by (`createdAt` desc, `id` desc) — equal `createdAt` with ids `…0001`/`…0002` → `…0002` first; no duplicate `id`; every item belongs to the caller's session (session B with 0 runs sees `items: []` while session A has 3; no cookie → new session → `items: []`); per item `configuration` = that run's `getRun.configuration` and `headline` = its `getFutureResult.story.headline`; UI shows exactly one `recent-future-<id>` per item in API order (count shown = items returned); key settings text `R<realism> D<darkness> O<optimism> · <horizon label>` for every horizon code (1d Tomorrow, 1w 1 week, 1m 1 month, 1y 1 year, 5y 5 years, 10y 10 years, 20y 20 years) and the bounds 1 and 10 (`R1 D10 O1 · Tomorrow`); time classes on `createdAt` in browser local time: same local calendar day as now → `HH:mm` (`09:05`), any other day (yesterday, other year) → `d MMM yyyy, HH:mm` (`3 Oct 2026, 09:05`); headline rendered as text (never `innerHTML`; `<b>x</b>` shows literally); `getScenarioConfiguration` = configuration of the newest run of any status (newest FAILED after an older COMPLETED → FAILED run's configuration), defaults with 0 runs.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs | listRecentRuns | — | 200 RecentRunList (≤ 20 items) · 500 INTERNAL_ERROR |

Reopening uses `getRun` (generation-runs.md), `getFutureResult` (future-result.md). `getScenarioConfiguration`
(scenario-panel.md) gains the newest-run rule.

## UI
- Route: `/futures/:runId` (existing `RunView`) · component `RecentFuturesComponent` (`app-recent-futures`) in
  `src/app/history/recent-futures.ts`, placed in the `mat-toolbar` after the spacer and before
  `<app-chatgpt-connection />` · Material: `mat-button` + `mat-menu`, `mat-progress-spinner`.
- States (inside the open menu): loading (`recent-futures-loading`) · empty (`recent-futures-empty`) · error
  (`recent-futures-error`) · list (`recent-futures-list`).
- `data-testid`s: `recent-futures-button`, `recent-futures-list`, `recent-futures-loading`, `recent-futures-empty`,
  `recent-futures-error`, `recent-future-<runId>`, `recent-future-time-<runId>`, `recent-future-headline-<runId>`,
  `recent-future-settings-<runId>`. Not-found reuses `failure-view`, `failure-message`, `try-again`.
- Acceptance:
  - Given I generated two futures, when I open "Recent futures", then I see both with time, headline and key settings, newest first
  - Given I reopen an older future, when it loads, then its story, metadata, WHY, SOURCES and WHY THESE NEWS views show that run's data
  - Given 21 completed runs, when I open the list, then exactly the 20 newest are shown
  - Given another browser session, when it opens Recent futures, then it does not see my runs

## Slice 15_recent-futures — FR-33 test contract

Where this section is more precise than "Behaviour" or "UI" above, this section wins. No migration, no new
`ApiError.code`. Not in this slice: quick actions (16), alternative runs (17), mobile layout (FR-34).

### Backend
- `HistoryController implements HistoryApi` (package `com.oracul.app.history`; dependencies via `ObjectProvider` like
  `RunsController`, so the five `@WebMvcTest` classes still start without a DataSource), `listRecentRuns()` →
  `GenerationRunRepository.findRecentCompleted(sessionId, 20)`:
  `select … from generation_run where session_id = ? and status = 'COMPLETED' and headline is not null
  order by created_at desc, id desc limit 20`. Mapping: `id`, `generationId`, `kind`, `createdAt`, `completedAt`
  (omitted when null), `headline`, `configuration` (the stored JSON, as `getRun.configuration`).
- No cookie / unknown cookie: `SessionFilter` creates a new session → `200 {"items":[]}` with `Set-Cookie`.
- `ScenarioController.getScenarioConfiguration()`: resolves `CurrentSession` and the repository via
  `ObjectProvider`; when either is unavailable (WebMvc slice) or the session has no run → `ScenarioCatalogueData.defaults()`;
  else `configuration` of `select … where session_id = ? order by created_at desc, id desc limit 1` (any status).
- Unexpected exception (e.g. repository throws) → 500 `{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}`
  via the existing `ApiExceptionHandler`.

### Backend tests (`backend/src/test/java/com/oracul/app/history/RecentRunsIT.java`, extends `AbstractRunIT`; `// @trace FR-33`)
Rows are inserted with `jdbc` directly into `generation_run` (status, headline, created_at, completed_at set
explicitly; session row in `browser_session`) and read with cookie `ORACUL_SID=<session id>`.
| # | Setup | Request | Expected |
|---|---|---|---|
| 1 | no cookie | `GET /api/runs` | 200 `{"items":[]}`, `Set-Cookie` `ORACUL_SID=` |
| 2 | session A: 2 COMPLETED runs, created 10:00 and 11:00 | `GET /api/runs` (A) | 2 items, 11:00 run first; every field equals the row (`configuration` deep-equal to `GET /api/runs/{id}`.configuration) |
| 3 | A: 1 COMPLETED, 1 FAILED, 1 INSUFFICIENT_EVIDENCE, 1 RUNNING, 1 COMPLETED with null headline | `GET /api/runs` (A) | exactly the 1 COMPLETED-with-headline run |
| 4 | A: n COMPLETED runs, created 1 min apart, n ∈ {20, 21, 25} (parameterized) | `GET /api/runs` (A) | 20 items = the 20 newest in created_at desc order; rows beyond stay in the DB (`count(*)` = n) |
| 5 | A: 2 COMPLETED runs with equal created_at, ids `00000000-0000-0000-0000-000000000001` and `…0002` | `GET /api/runs` (A) | `…0002` first |
| 6 | A: 3 COMPLETED runs; session B: none | `GET /api/runs` (B) | `{"items":[]}`; `GET /api/runs/{A's run}` with B → 404 `RUN_NOT_FOUND` "Future not found" |
| 7 | A: runs COMPLETED (09:00, darkness 3) then FAILED (10:00, darkness 9) | `GET /api/scenario/configuration` (A) | configuration of the 10:00 run (darkness 9); session without runs → defaults |
| 8 | `GenerationRunRepository` `@MockitoSpyBean`/stub throws `RuntimeException` on `findRecentCompleted` | `GET /api/runs` | 500 `{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}`, body contains no exception text |

### Frontend (`src/app/history/recent-futures.ts`, generated `HistoryService.listRecentRuns`)
- Button: `<button mat-button data-testid="recent-futures-button" [matMenuTriggerFor]="menu">Recent futures</button>`.
- No HTTP request on app load. Each `menuOpened` → exactly one `GET /api/runs`; while pending only
  `recent-futures-loading`; 200 with items → `recent-futures-list` containing one `recent-future-<id>`
  (`mat-menu-item`/button) per item in API order with children `recent-future-time-<id>`,
  `recent-future-headline-<id>`, `recent-future-settings-<id>`; 200 `items: []` → `recent-futures-empty` "No futures
  yet"; error → `recent-futures-error` "Recent futures are unavailable". A response arriving after the menu was
  reopened is ignored (only the latest request's result is shown).
- Time (`createdAt`, browser local time): same local day as now → `HH:mm`; otherwise `d MMM yyyy, HH:mm` with English
  3-letter month. Settings: `R${realism} D${darkness} O${optimism} · ${label}` with the label from
  `ScenarioLoader.catalogue().horizons` (fallback: the code).
- Click on `recent-future-<id>` → `router.navigateByUrl('/futures/<id>')`, menu closes.
- `RunStore.open(runId)` (new runId, not the run just started by `start()`): on the first successful `getRun`
  response → `ScenarioStore.load(run.configuration)`; later polls and `start()` never touch the panel.

### Frontend unit tests (Vitest, `// @trace FR-33`)
- `src/app/history/recent-futures.spec.ts`: no `/api/runs` GET before click; click → 1 GET; loading state; 0 / 1 / 20
  items (count of `recent-future-*` entries = items, API order kept); time today vs other day; settings for all 7
  horizons and bounds; headline `<b>x</b>` literal; error (status 0 and 500) → `recent-futures-error`; reopen →
  second GET; click entry → URL `/futures/<id>`.
- `src/app/runs/run.store.spec.ts` (new cases only): `open()` loads `run.configuration` into `ScenarioStore` once;
  a second poll with another configuration does not change the panel; after `start()` the panel is untouched.

### E2E (`e2e/tests/recent-futures.spec.ts`; `// @trace FR-33`; reset stub as in `future-story.spec.ts`; `test.setTimeout(60_000)`)
1. Connect, generate run 1 with darkness 9 via the panel, wait for `result-view`; go to `/`, set darkness 3,
   generate run 2, wait for `result-view`. Open `recent-futures-button` → 2 entries, run 2 first, settings
   `R8 D3 O5 · 1 year` / `R8 D9 O5 · 1 year`, time matches `^\d{2}:\d{2}$`. Evidence `FR-33 recent-futures-list`.
2. Click run 1 → URL `/futures/<run1>`, `result-view` shows run 1's `story-headline` and `meta-darkness`
   "Darkness 9/10"; `open-why`, `open-sources`, `open-why-news` panels render; `slider-darkness-input` value `9`.
   Evidence `FR-33 recent-future-reopened`.
3. Reload `/` → `slider-darkness-input` value `3` (newest run's configuration).
4. New browser context (other session) → `recent-futures-empty` "No futures yet"; `GET /api/runs/<run1>` → 404
   `RUN_NOT_FOUND`; `page.goto('/futures/<run1>')` → `failure-message` "Future not found".
(The 21-run limit is covered by `RecentRunsIT` #4, not by E2E.)
