# Spec — Generation runs (start, progress, regeneration, failures)

Covers: FR-10, FR-24, FR-29, FR-30, FR-31, FR-32

## Purpose
GENERATE THE FUTURE turns the panel configuration into a Generation Run that executes the ORACUL pipeline
asynchronously (research-pipeline.md → scenario-reasoning.md → future-result.md). The user watches high-level
progress, can change one variable with quick controls or ask for an alternative future, and always ends in a clear
state: a result, "insufficient evidence" with LOWER REALISM, or a friendly failure with "Try again".

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| generation_run | id | uuid | PK, API `id` |
| generation_run | generation_id | varchar(32) | unique; `ORC-YYYY-MM-DD-HHmm` (UTC of creation); on collision suffix `-2`, `-3`, … |
| generation_run | session_id | uuid | FK browser_session; every read is filtered by the caller's session |
| generation_run | kind | STANDARD / ALTERNATIVE | |
| generation_run | parent_run_id | uuid null | ALTERNATIVE only |
| generation_run | status | QUEUED / RUNNING / COMPLETED / INSUFFICIENT_EVIDENCE / FAILED | QUEUED, RUNNING = active |
| generation_run | stage | RunStage null | null while QUEUED |
| generation_run | configuration | jsonb | exact ScenarioConfiguration snapshot (custom labels trimmed) |
| generation_run | research_profile | jsonb null | research-pipeline.md FR-11 |
| generation_run | search_plan | jsonb null | research-pipeline.md FR-12 |
| generation_run | evidence_pack_id | uuid null | FK evidence_pack; ALTERNATIVE runs reference the parent's pack |
| generation_run | counts | jsonb | ResearchCounts, zeros at start; ALTERNATIVE copies the parent's |
| generation_run | failure_code / failure_message | varchar null | RunFailureCode + fixed message |
| generation_run | suggested_realism | int null | INSUFFICIENT_EVIDENCE only |
| generation_run | headline | text null | COMPLETED only |
| generation_run | deadline_at | timestamptz | created_at + 180 s (`oracul.run.timeout`, default PT3M) |
| generation_run | created_at / updated_at / completed_at | timestamptz | completed_at set for every terminal status |

Partial unique index `one_active_run_per_session` on `session_id WHERE status IN ('QUEUED','RUNNING')` enforces the
single-active-run rule in the database as well.

### Stages (order, `stageIndex`, `stageLabel`)
| # | RunStage | stageLabel | pipeline work |
|---|---|---|---|
| 1 | UNDERSTANDING | Understanding your future… | Research Profile (FR-11) |
| 2 | RESEARCH_STRATEGY | Building research strategy… | search plan + query expansion (FR-12) |
| 3 | SEARCHING | Searching current events… | news provider queries (FR-13) |
| 4 | READING_SOURCES | Reading relevant sources… | article metadata fetch, filtering (FR-13) |
| 5 | CONNECTING_SIGNALS | Connecting signals… | normalisation, dedup, classification (FR-14, FR-15) |
| 6 | RANKING | Ranking evidence… | ranking, selection, Evidence Pack, sufficiency check (FR-16–18, FR-31) |
| 7 | EXPLORING_FUTURES | Exploring possible futures… | structured scenario generation (FR-19, FR-20) |
| 8 | CHALLENGING_ASSUMPTIONS | Challenging assumptions… | Evidence Guard + critic (FR-21, FR-22) |
| 9 | CONSTRUCTING_SCENARIO | Constructing scenario… | regeneration when required, final scenario accepted |
| 10 | WRITING_STORY | Writing from the future… | final story (FR-23) |

ALTERNATIVE runs skip stages 1–6 (they jump from QUEUED to stage 7).

### Run failure codes and messages (`GenerationRun.failure`)
| code | when | message |
|---|---|---|
| NEWS_UNAVAILABLE | every news query failed (FR-13) | ORACUL could not reach its news sources — try again later |
| CHATGPT_RATE_LIMITED | Responses API 429 | ChatGPT plan limit reached — try again later |
| CHATGPT_UNAVAILABLE | Responses API 5xx / network error / 30 s call timeout, after one retry | ChatGPT is unavailable right now — try again later |
| CHATGPT_SESSION_EXPIRED | 401/403 from Responses API and refresh failed, or user disconnected | ChatGPT session expired — please reconnect |
| RUN_TIMEOUT | deadline_at passed | Generation took too long — try again |
| INVALID_SCENARIO | structured output invalid twice (FR-20) | ORACUL could not construct a valid scenario |
| SCENARIO_REJECTED | Evidence Guard FAIL after regeneration (FR-21/22) | ORACUL could not construct a scenario supported by current evidence |
| ALTERNATIVE_NOT_DISTINCT | alternative equals a previous future twice (FR-30) | ORACUL could not find a different future — try changing a setting |
| INSUFFICIENT_EVIDENCE | status INSUFFICIENT_EVIDENCE (FR-31) | ORACUL found insufficient current evidence to construct this scenario at Realism <n>. |
| RUN_INTERRUPTED | backend restarted while the run was active (startup sweep) | Generation was interrupted — try again |
| INTERNAL_ERROR | any unexpected exception in the pipeline | Something went wrong — try again |

## Behaviour

### FR-10 — Generate the Future (start a run)
- Happy path: `POST /api/runs` with the panel's ScenarioConfiguration → 202 GenerationRun (status QUEUED, kind
  STANDARD, stageIndex 0, stageCount 10, configuration equal to the request, counts all 0). The pipeline starts on a
  bounded executor (`oracul.run.executor-threads`, default 4). The center switches to the progress view
  (`progress-view`) and polls `getRun`.
- Rules:
  - Check order: body validation (scenario-panel.md) → ChatGPT connection (not connected / not eligible / refresh
    failure) → active run of this session. Nothing is stored when a check fails.
  - The configuration is snapshotted; later panel changes never affect a started run.
  - One active run per session (application check inside a transaction + partial unique index; a unique-violation is
    mapped to the same 409).
  - The generate button (`generate-button`) is disabled while `canGenerate` is false (hint `generate-hint`
    "Connect ChatGPT to generate"), while a start request is in flight, and while a run is active.
- Errors:
  - invalid body → 400 `VALIDATION_FAILED` → messages of scenario-panel.md
  - not connected → 401 `CHATGPT_NOT_CONNECTED` → "Connect ChatGPT to generate"
  - token expired and refresh fails → 401 `CHATGPT_SESSION_EXPIRED` → "ChatGPT session expired — please reconnect"
  - plan not eligible → 403 `CHATGPT_PLAN_NOT_ELIGIBLE` → "Your ChatGPT plan is not eligible for ORACUL"
  - an active run exists → 409 `RUN_ALREADY_ACTIVE` → "A generation is already running"
  - unexpected failure → 500 `INTERNAL_ERROR` → "Something went wrong — try again"
  - The UI shows any of these messages in a snackbar (`run-error-message`) and stays on the current view.

### FR-24 — Generation progress
- Happy path: while the run is active the frontend calls `GET /api/runs/{runId}` every 1000 ms; the progress view
  shows `stageLabel` (`progress-stage`), a determinate `mat-progress-bar` (`progress-bar`, value = stageIndex/10) and
  the 10 stage labels as a checklist (`progress-step-<RunStage>` with states done/current/pending). The backend
  persists each stage transition (stage, stageIndex, updatedAt, counts so far) before starting its work, so the UI shows
  a stage within ≤ 2 s of the transition.
- Rules: polling stops when status is terminal; then COMPLETED → result view (future-result.md);
  INSUFFICIENT_EVIDENCE → insufficient view (FR-31); FAILED → failure view (FR-32). The progress view never shows URLs,
  JSON, ids of providers or stack traces — only `stageLabel` and the checklist labels.
- Errors:
  - unknown / malformed / other session's `runId` → 404 `RUN_NOT_FOUND` → "Future not found" (UI: failure view with
    "Try again")
  - a poll fails with a network error → the UI keeps the last state and retries on the next tick; after 10
    consecutive failures it shows `backend-unavailable` "ORACUL is unavailable — try again shortly"

### FR-29 — Quick regeneration controls
- Happy path: on the result view an action bar shows MORE REALISTIC (`quick-more-realistic`, realism +2), DARKER
  (`quick-darker`, darkness +2), MORE OPTIMISTIC (`quick-more-optimistic`, optimism +2), MORE EXTREME
  (`quick-more-extreme`, realism −3). Click → the frontend updates the panel store (value clamped to 1–10) and calls
  `POST /api/runs` with the updated configuration (same flow as FR-10). The previous run stays COMPLETED and listed in
  Recent futures.
- Rules: a button is disabled when its control is already at the limit (DARKER at darkness 10, MORE OPTIMISTIC at
  optimism 10, MORE REALISTIC at realism 10, MORE EXTREME at realism 1), when a run is active, or when `canGenerate` is
  false. Clamping: 9 + 2 → 10; 2 − 3 → 1.
- Errors: same as FR-10 (`startRun`); on error the panel keeps the updated value and the snackbar shows the message.

### FR-30 — Alternative future
- Happy path: ALTERNATIVE FUTURE (`quick-alternative`) → `POST /api/runs/{runId}/alternatives` → 202 GenerationRun with
  kind ALTERNATIVE, parentRunId = runId, configuration = parent's configuration, evidencePackId = parent's
  evidencePackId, counts copied from the parent. No search runs (stages 1–6 skipped). The generation prompt lists the
  futureEvent title + causal-chain statements of the parent and of every earlier run that used the same Evidence Pack
  as "futures to avoid" (scenario-reasoning.md FR-19).
- Rules:
  - The new futureEvent title must differ (case-insensitive, trimmed) from every avoided title and its causal chain
    must contain at least one step statement not present in the parent's chain. If not, regenerate once with the
    duplicate named; if still not distinct → FAILED `ALTERNATIVE_NOT_DISTINCT`.
  - The panel shows the parent's configuration while the alternative runs.
  - Button disabled while a run is active or `canGenerate` is false.
- Errors:
  - unknown / other session's run → 404 `RUN_NOT_FOUND` → "Future not found"
  - parent not COMPLETED (no validated scenario) → 409 `RUN_NOT_COMPLETED` → "Only a completed future can have an alternative"
  - an active run exists → 409 `RUN_ALREADY_ACTIVE` → "A generation is already running"
  - not connected / expired / not eligible → 401 / 401 / 403 as in FR-10
  - unexpected failure → 500 `INTERNAL_ERROR` → "Something went wrong — try again"

### FR-31 — Insufficient evidence
- Happy path: after selection (stage RANKING) the backend compares the number of CORE evidence items with the
  threshold for the run's realism: realism 9–10 → 5, realism 6–8 → 3, realism 1–5 → 1
  (`oracul.evidence.min-core.*`). Below the threshold the run ends with status INSUFFICIENT_EVIDENCE, failure code
  INSUFFICIENT_EVIDENCE, message "ORACUL found insufficient current evidence to construct this scenario at Realism
  <realism>." (e.g. Realism 10), suggestedRealism = max(1, realism − 2) when realism > 1. ChatGPT is never called
  for generation, critic or story; the Evidence Pack is still stored for transparency.
- UI: insufficient view (`insufficient-view`) shows the message (`insufficient-message`) and LOWER REALISM
  (`lower-realism`). Click → panel realism = suggestedRealism, then `POST /api/runs` (FR-10 flow).
- Rules: LOWER REALISM is hidden when realism is 1 (no suggestedRealism); no story, no invented evidence.
- Errors: `startRun` errors as in FR-10, shown in the snackbar.

### FR-32 — Run failure handling
- Happy path (of the failure path): any failure ends the run with status FAILED, a RunFailureCode and its fixed
  message (table above), completed_at set, the active-run slot released. The failure view (`failure-view`) shows the
  message (`failure-message`) and "Try again" (`try-again`) which re-submits the failed run's configuration with
  `POST /api/runs`. The panel stays usable during and after the failure.
- Rules:
  - Timeout: a scheduler (every 5 s, injectable `Clock`) fails active runs whose deadline_at passed with
    RUN_TIMEOUT; the pipeline checks the deadline between steps and abandons its work; late results of an abandoned
    run are discarded.
  - ChatGPT 429 → CHATGPT_RATE_LIMITED immediately (no retry). 5xx/network/timeout → one retry after 1 s, then
    CHATGPT_UNAVAILABLE. 401/403 → one refresh + retry, then CHATGPT_SESSION_EXPIRED (connection state SESSION_EXPIRED).
  - On startup, every QUEUED/RUNNING run is set FAILED RUN_INTERRUPTED.
  - The failure message never includes stack traces, provider bodies, URLs, JSON or tokens; technical detail goes to
    the redacted log only.
- Errors: the API never answers 500 with internals: the global handler maps unexpected exceptions to 500
  `INTERNAL_ERROR` "Something went wrong — try again" (body = ApiError only).

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| POST | /api/runs | startRun | ScenarioConfiguration | 202 GenerationRun · 400 VALIDATION_FAILED · 401 CHATGPT_NOT_CONNECTED / CHATGPT_SESSION_EXPIRED · 403 CHATGPT_PLAN_NOT_ELIGIBLE · 409 RUN_ALREADY_ACTIVE · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId} | getRun | — | 200 GenerationRun · 404 RUN_NOT_FOUND · 500 INTERNAL_ERROR |
| POST | /api/runs/{runId}/alternatives | startAlternativeRun | — | 202 GenerationRun · 401 CHATGPT_NOT_CONNECTED / CHATGPT_SESSION_EXPIRED · 403 CHATGPT_PLAN_NOT_ELIGIBLE · 404 RUN_NOT_FOUND · 409 RUN_ALREADY_ACTIVE / RUN_NOT_COMPLETED · 500 INTERNAL_ERROR |

## UI
- Route: `/` (center area) and `/futures/:runId` · components in `src/app/runs/`: `GenerateButtonComponent`,
  `ProgressViewComponent`, `QuickActionsComponent`, `InsufficientEvidenceComponent`, `RunFailureComponent`;
  state in `RunStore` (signals: currentRun, polling) · Material: `mat-flat-button`, `mat-progress-bar`, `mat-list`,
  `mat-icon`, `MatSnackBar`.
- States: idle (welcome) · starting (button spinner) · running (progress) · completed (result) · insufficient ·
  failed.
- After a run starts the URL becomes `/futures/<runId>` (`replaceUrl`), so reload resumes polling/showing that run.
- `data-testid`s: `generate-button`, `generate-hint`, `run-error-message`, `progress-view`, `progress-stage`,
  `progress-bar`, `progress-step-<RunStage>` (e.g. `progress-step-SEARCHING`), `quick-actions`,
  `quick-more-realistic`, `quick-darker`, `quick-more-optimistic`, `quick-more-extreme`, `quick-alternative`,
  `insufficient-view`, `insufficient-message`, `lower-realism`, `failure-view`, `failure-message`, `try-again`.

## Slice 04_run-start — test contract (FR-10, FR-11, FR-24)

Delivers run creation, the run record, the async pipeline skeleton with all 10 stage transitions, the Research
Profile (stage 1, details in research-pipeline.md "Slice 04_run-start — FR-11 test contract"), polling and the
progress view. Not in this slice: real search/reasoning/story (05–09), run deadline scheduler and startup sweep
(11), result/insufficient views (09/12), history (15), alternatives (17). Slices 01–03 behaviour and tests stay
unchanged, except that a valid `startRun` with a usable connection now answers `202` instead of the interim `501`.

### Backend (`com.oracul.app.runs`, `com.oracul.app.research`, `com.oracul.app.common`)
Files: Flyway `V3__generation_run.sql` (table `generation_run` exactly as in "Data", incl. the partial unique index
`one_active_run_per_session`); `GenerationRun` entity + `GenerationRunRepository`; `RunService`; `PipelineExecutor`
(bounded pool `oracul.run.executor-threads`, default 4); `RunsController implements RunsApi` (`startRun`, `getRun`;
`startAlternativeRun` keeps 501); `ResearchController implements ResearchApi` (`getRunResearch` only; the other
research operations keep 501).

Configuration (`oracul.run.*`, tests override with `@DynamicPropertySource` / `@TestPropertySource`):
| Property | Default | Meaning |
|---|---|---|
| `oracul.run.executor-threads` | 4 | pipeline thread pool size |
| `oracul.run.timeout` | PT3M | `deadline_at = created_at + timeout` (stored now; enforced by slice 11) |
| `oracul.run.placeholder-stage-delay` | PT1S | time each **placeholder** stage (2–10) stays current before the next transition; real stages that later slices deliver do not use it |

Pipeline in this slice (`PipelineExecutor`, one task per run, started after the QUEUED row is committed):
1. Commit `status=RUNNING, stage=UNDERSTANDING, stageIndex=1, updatedAt=now`; build the Research Profile
   (`ResearchProfileFactory.from(configuration)`) and commit it into `research_profile`.
2. For each stage 2…10 in table order: commit `stage=<stage>, stageIndex=<n>, updatedAt=now`, then wait
   `placeholder-stage-delay` (interruptible).
3. After WRITING_STORY: commit `status=COMPLETED, stage=WRITING_STORY, stageIndex=10, completedAt=now`; `headline`,
   `failure`, `evidencePackId`, `suggestedRealism` stay absent; counts stay all 0. (Interim terminal state: slice 09
   replaces it with a real story and headline; tests of this slice may assert it.)
4. Any unexpected exception in the task → `status=FAILED`, `failure={code: INTERNAL_ERROR, message: "Something went
   wrong — try again"}`, completedAt set (slot released). Not asserted by slice-04 tests.

Every transition is its own committed transaction before the stage's work, so `getRun` sees it immediately.

#### `POST /api/runs` (`startRun`)
Base valid body `B` (as slice 01):
`{"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],"customWildcards":[],"output":{"story":true,"illustration":false}}`.
Acceptance body `A`:
`{"realism":8,"darkness":9,"optimism":2,"horizon":"5y","wildcards":[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"robotics-humanoid-boom","intensity":6}],"customWildcards":[],"output":{"story":true,"illustration":false}}`.
"Connected" = session signed in through the stub token server (`StubOpenAi`, flow of `AbstractChatGptIT.connect`;
tests in `com.oracul.app.runs` may reuse/move that helper into a shared test package).

| # | Situation | Status | Body |
|---|---|---|---|
| 1 | connected, body `A`, no active run | 202 | GenerationRun: `id` UUID; `generationId` matches `^ORC-\d{4}-\d{2}-\d{2}-\d{4}(-\d+)?$`; `kind` `STANDARD`; `status` `QUEUED`; `stageIndex` 0; `stageCount` 10; `stage`, `stageLabel`, `parentRunId`, `evidencePackId`, `failure`, `suggestedRealism`, `headline`, `completedAt` absent; `configuration` deep-equals `A`; `counts` = `{"searches":0,"articlesRetrieved":0,"articlesConsidered":0,"uniqueEvents":0,"eventsSelected":0,"counterSignals":0,"sourcesUsed":0}`; `createdAt` = `updatedAt` (ISO-8601 UTC). The 202 body is built from the committed QUEUED row, never from a later state. |
| 2 | connected, body `B` plus unknown property `"foo":1` | 202 | as #1; `configuration` deep-equals `B` (no `foo`) |
| 3 | fixed `Clock` `2026-10-02T18:42:31Z` (test `@Primary Clock` bean); three runs in sequence, each waited to COMPLETED (delay PT0S) | 202 ×3 | `generationId` `ORC-2026-10-02-1842`, then `ORC-2026-10-02-1842-2`, then `ORC-2026-10-02-1842-3` (also across sessions — the id is globally unique) |
| 4 | connected, a run of this session is QUEUED/RUNNING (delay PT30S), body `B` | 409 | `{"code":"RUN_ALREADY_ACTIVE","message":"A generation is already running"}`; row count of `generation_run` unchanged |
| 5 | as #4 but the second body is invalid (`darkness` 11) | 400 | `VALIDATION_FAILED` `darkness must be between 1 and 10` (validation before the active-run check) |
| 6 | connected session X has an active run; connected session Y starts body `B` | 202 | Y's run created (the rule is per session) |
| 7 | connected, two `startRun` requests for the same session sent concurrently (no active run) | one 202 + one 409 | the 409 is `RUN_ALREADY_ACTIVE`; exactly one row for the session with status QUEUED/RUNNING |
| 8 | connected, the previous run of the session is COMPLETED | 202 | new run created (slot released) |
| 9 | no cookie / fresh session, body `B` | 401 | `CHATGPT_NOT_CONNECTED` `Connect ChatGPT to generate`; no row |
| 10 | connected session whose access token is within refresh-skew and the stub token server answers the refresh with 400 (setup as slice 02 `ChatGptConnectionIT`) | 401 | `CHATGPT_SESSION_EXPIRED` `ChatGPT session expired — please reconnect`; no row |
| 11 | session flagged PLAN_NOT_ELIGIBLE | 403 | `CHATGPT_PLAN_NOT_ELIGIBLE` `Your ChatGPT plan is not eligible for ORACUL`; no row |
| 12 | not connected and body invalid (`horizon` `"3y"`) | 400 | `VALIDATION_FAILED` `unknown horizon` (validation before connection) |
| 13 | connected, an active run exists, then disconnect (`DELETE /api/auth/chatgpt/connection`) and start | 401 | `CHATGPT_NOT_CONNECTED` (connection before active-run check) |

Every error body has exactly the keys `code`, `message`; "no row" = `select count(*) from generation_run` unchanged.
Stored row after #1 (DB assertions allowed): `session_id` = caller's session, `kind` STANDARD, `configuration` jsonb
deep-equals `A`, `deadline_at` = `created_at` + 180 s, no column contains a token value (NFR-1 scan from slice 02
extended to the new table).

#### `GET /api/runs/{runId}` (`getRun`)
| # | Situation | Status | Body |
|---|---|---|---|
| 1 | own run, delay PT30S, polled until `stageIndex` ≥ 2 (≤ 5 s) | 200 | `status` `RUNNING`, `stage` `RESEARCH_STRATEGY`, `stageIndex` 2, `stageLabel` `Building research strategy…`, `stageCount` 10, `configuration` = request body, `updatedAt` ≥ `createdAt` |
| 2 | own run, delay PT0S, polled until terminal (≤ 5 s) | 200 | `status` `COMPLETED`, `stage` `WRITING_STORY`, `stageIndex` 10, `stageLabel` `Writing from the future…`, `completedAt` set, `failure`/`headline` absent, counts all 0 |
| 3 | own run, delay PT1S, `getRun` polled every 200 ms until terminal | 200 | observed `stageIndex` values never decrease; every observed `(stage, stageIndex, stageLabel)` is a row of the "Stages" table; at least 5 distinct stages observed |
| 4 | random UUID `00000000-0000-0000-0000-000000000000` | 404 | `{"code":"RUN_NOT_FOUND","message":"Future not found"}` |
| 5 | malformed id `abc` | 404 | `RUN_NOT_FOUND` `Future not found` (not 400) |
| 6 | run of session X requested with session Y's cookie, or without cookie | 404 | `RUN_NOT_FOUND` `Future not found` |

`stageLabel` values are exactly (U+2026 ellipsis, no three dots): `Understanding your future…`, `Building research
strategy…`, `Searching current events…`, `Reading relevant sources…`, `Connecting signals…`, `Ranking evidence…`,
`Exploring possible futures…`, `Challenging assumptions…`, `Constructing scenario…`, `Writing from the future…`.
No `getRun` body contains a URL, stack trace, Java class name or token.

### Frontend
Files (`src/app/runs/`): `run.store.ts` (`RunStore`, `providedIn: 'root'`), `generate-button.ts`
(`app-generate-button`, used inside `welcome-view` instead of the static button), `run-view.ts` (route component of
`/futures/:runId`), `progress-view.ts` (`app-progress-view`), `run-failure.ts` (`app-run-failure`, slice 04 uses it
for "Future not found" only; slice 11 extends it), `run-error-message.ts` (snackbar content). `app.routes.ts`:
`''` → welcome view, `'futures/:runId'` → run view, `'**'` → redirect to `''`; `app.html` renders `<router-outlet />`
in the `ready` state (header, panel, loading and backend-unavailable behaviour of slices 01–03 unchanged).

- Generate button (`generate-button`, `mat-flat-button`, text "GENERATE THE FUTURE"):
  - enabled iff `ConnectionStore.canGenerate()` and not `RunStore.starting()` and not `RunStore.active()`.
  - `generate-hint` "Connect ChatGPT to generate" is present iff `canGenerate()` is false; absent when connected.
  - click → `RunStore.start(ScenarioStore.configuration())` → `RunsService.startRun` with exactly that object as body
    (E2E asserts the request body deep-equals the panel state, e.g. body `A` after setting the acceptance
    configuration); while the request is in flight the button is disabled.
  - 202 → `router.navigate(['/futures', run.id], { replaceUrl: true })`; the center shows `progress-view` (≤ 2 s).
  - error → snackbar via `MatSnackBar.openFromComponent` (duration 6000 ms) whose content element has
    `data-testid="run-error-message"` and exactly the text: `ApiError.message` of the response when the body is an
    `ApiError`, otherwise "Something went wrong — try again". The view stays on `/`, button enabled again (if
    `canGenerate`). On `CHATGPT_NOT_CONNECTED` / `CHATGPT_SESSION_EXPIRED` / `CHATGPT_PLAN_NOT_ELIGIBLE` the app also
    calls `ConnectionStore.load()` (header updates, e.g. "Session expired").
  - Example: 409 → `run-error-message` "A generation is already running".
- `RunStore` (unit-test surface): signals `run(): GenerationRun | null`, `starting(): boolean`, `active(): boolean`
  (computed: status QUEUED or RUNNING), `notFound(): boolean`, `unavailable(): boolean`; methods
  `start(config): void`, `open(runId: string): void` (used by the run view: immediate `getRun`, then polls),
  `retry(): void` (clears `unavailable`, resumes polling), `stop(): void`.
  - Polling: `setTimeout` loop, `POLL_INTERVAL_MS = 1000` (exported constant), next `getRun` 1000 ms after the
    previous response; after `start` the first poll is 1000 ms after the 202. Polling stops on a terminal status
    (COMPLETED / INSUFFICIENT_EVIDENCE / FAILED), on 404, and on `stop()`. Polling is app-wide (navigating to `/`
    does not stop it), so `active()` stays correct for the generate button.
  - A failed poll (network error or status ≥ 500) keeps the last `run()` and retries on the next tick; after
    `MAX_POLL_FAILURES = 10` consecutive failures `unavailable()` becomes true and polling pauses; a successful poll
    resets the counter.
  - 404 `RUN_NOT_FOUND` → `notFound()` true, `run()` null.
- Run view (`/futures/:runId`), exactly one of:
  - `progress-view` while `run()` is set and not `unavailable()` (active **or** COMPLETED in this slice — the
    result view replaces COMPLETED in slice 09; FAILED/INSUFFICIENT_EVIDENCE views come in 11/12, until then they
    also show the progress view);
  - `failure-view` when `notFound()`: `failure-message` text exactly "Future not found" and `try-again` button
    ("Try again") that navigates to `/` (welcome view);
  - `backend-unavailable` (text exactly "ORACUL is unavailable — try again shortly") with `backend-retry`
    ("Try again") → `RunStore.retry()`, when `unavailable()`.
  - Reloading `/futures/<id>` (fresh app) loads the run with `open` and shows its current state (resume).
- Progress view (`progress-view`), only these texts:
  - `progress-stage`: the current `stageLabel`; while QUEUED (no stage) "Understanding your future…".
  - `progress-bar`: `mat-progress-bar` mode `determinate`, value = `stageIndex * 10` (0–100); tests assert the
    host's `aria-valuenow` ("0" while QUEUED, "20" at RESEARCH_STRATEGY, "100" when COMPLETED).
  - a `mat-list` with 10 items `progress-step-<RunStage>` in stage order, text exactly the stage label, attribute
    `data-state` = `done` (index < stageIndex, or every step when status is COMPLETED), `current`
    (index = stageIndex while active), `pending` (index > stageIndex; every step while QUEUED).
  - `progress-view` text never contains `http`, `{`, `}`, `Exception`, `Error:`, the run's UUID or a generationId
    (FR-24 acceptance 2).

### Test hooks
- Backend: classes needing an active run set `oracul.run.placeholder-stage-delay=PT30S`; classes needing terminal
  runs set `PT0S`; a fixed `Clock` via `@TestConfiguration` `@Bean @Primary Clock` for #3 (the ChatGPT token expiry
  uses the same clock, so sign-in still works).
- E2E: `docker-compose.override.yml` sets backend env `ORACUL_RUN_PLACEHOLDER_STAGE_DELAY: PT2S` so each placeholder
  label is visible ≥ 1 s at a 1 s poll. Connect through the stub as in `chatgpt-connection.spec.ts`. Specs that start
  runs use a fresh browser context per test (fresh `ORACUL_SID`, so no active run leaks between tests) and run
  serially with the chatgpt specs' stub reset.

### Test locations and traces
- Backend: `com.oracul.app.runs.StartRunIT` (`// @trace FR-10`, startRun rows), `GetRunIT` (`// @trace FR-24`,
  getRun rows), `RunStageTest` (`// @trace FR-24`: `com.oracul.app.runs.RunStages.index(RunStage)` = 1…10 and `RunStages.label(RunStage)` = the exact labels, for all 10 values), `GenerationIdTest` or row #3 (`// @trace FR-10`);
  FR-11 tests as in research-pipeline.md.
- Frontend unit (Vitest, fake timers): `run.store.spec.ts` (FR-10 start/errors, FR-24 polling 1000 ms, stop on
  terminal, 10 failures → unavailable, 404), `generate-button.spec.ts` (FR-10 enabled/disabled matrix: not connected
  → disabled + hint; connected → enabled, no hint; starting → disabled; active run → disabled; snackbar text on 409
  / 401 + connection reload), `progress-view.spec.ts` (FR-24 label, `aria-valuenow`, step states for QUEUED /
  RUNNING index 3 / COMPLETED, no technical text), `run-view.spec.ts` (FR-24 not-found and unavailable states).
- E2E (`e2e/tests/run-start.spec.ts`):
  - FR-10: connect; set darkness 9, optimism 2, horizon 5y, enable `biology-new-pandemic` 8 and
    `robotics-humanoid-boom` 6; `generate-hint` absent, `generate-button` enabled; click with
    `page.waitForRequest` on `POST /api/runs` → `postDataJSON()` deep-equals `A`; URL matches
    `/\/futures\/[0-9a-f-]{36}$/`; `progress-view` visible; `page.request.get('/api/runs/<id>')` →
    `configuration` deep-equals `A`; `page.request.post('/api/runs', {data: A})` → 409 `RUN_ALREADY_ACTIVE`
    "A generation is already running".
  - FR-10: not connected → `generate-button` disabled, `generate-hint` "Connect ChatGPT to generate".
  - FR-24: after starting, `progress-stage` shows in order "Building research strategy…", "Searching current
    events…", …, "Writing from the future…" (each `toHaveText` timeout 5 s); the matching `progress-step-<stage>`
    has `data-state="current"` meanwhile; at the end all 10 steps `done`, `progress-bar` `aria-valuenow` "100";
    `progress-view` inner text matches none of `/https?:\/\/|[{}]|Exception/`; reload of the URL shows the same
    finished progress view; `/futures/00000000-0000-0000-0000-000000000000` → `failure-message` "Future not
    found", `try-again` → `welcome-view`.
