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
| generation_run | research_profile | jsonb null | research-pipeline.md FR-11; ALTERNATIVE copies the parent's |
| generation_run | search_plan | jsonb null | research-pipeline.md FR-12; ALTERNATIVE copies the parent's |
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
- Precise test contract: "Slice 16_quick-regeneration — FR-29 test contract" below (wins where more precise).
- Changes earlier behaviour: none (additive: `result-view` gains the `quick-actions` bar; no API, error code, ordering or outbound call of an earlier slice changes; existing result/run-view specs never click inside `result-view` buttons other than `open-why` / `open-sources` / `open-why-news` and assert no button count; the only new outbound call is the `POST /api/runs` a quick button sends on click) (tests: none)
- Ranges & invariants: each control value v is an integer 1..10 (the ScenarioStore never holds anything else); per action the target is DARKER / MORE OPTIMISTIC / MORE REALISTIC: v 1..8 → v + 2, v 9 → 10 (clamped), v 10 → disabled (no change, no request); MORE EXTREME: v 4..10 → v − 3, v 2..3 → 1 (clamped), v 1 → disabled; `quickTarget(action, v)` is tested for all 4 actions × v = 1…10 (40 cases) and equals the table; invariants: the request body of a quick click deep-equals the panel configuration before the click with exactly one field (the action's control) replaced by the target — horizon, wildcards (order and intensities), customWildcards and output unchanged; the panel after the click equals that body (also after a 4xx/5xx answer); a button is enabled iff its value is not at the bound and `canGenerate()` and not `starting()` and not `active()`; one click sends at most one `POST /api/runs` and a disabled button sends none and changes nothing; the previous run is never modified (status COMPLETED, headline, configuration, completedAt unchanged) and stays listed by `listRecentRuns`.

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
  - check order: run lookup (404) → ChatGPT connection (401/403) → parent completed (409 `RUN_NOT_COMPLETED`) →
    active run of this session (409 `RUN_ALREADY_ACTIVE`); nothing is stored when a check fails.
  - The UI shows any of these messages in the snackbar (`run-error-message`) and stays on the parent's result view.
- Precise test contract: "Slice 17_alternative-future — FR-30 test contract" below (wins where more precise).
- Changes earlier behaviour: the result view's `quick-actions` bar had exactly 4 buttons and no `quick-alternative` (slice 16) → it has 5 buttons, `quick-alternative` "ALTERNATIVE FUTURE" last; the slice 16 rendering test that asserts the exact 4-button list and the absence of `quick-alternative` must expect the 5-button list (tests: frontend/src/app/runs/quick-actions.spec.ts)
- Changes earlier behaviour: `POST /api/runs/{runId}/alternatives` answered 501 without body (slice 04 interim) → 202 GenerationRun / 401 / 403 / 404 / 409 / 500 ApiError as above; no existing test calls the path (grep of `backend/src/test`, `frontend/src/**/*.spec.ts`, `e2e/tests` for `alternatives`, `startAlternativeRun`, `501`: no hit) (tests: none)
- Changes earlier behaviour: none for STANDARD runs — their SCENARIO_GENERATION / SCENARIO_CRITIC / STORY_WRITING request texts, stage sequence, counts, Evidence Pack, `getRunResearch`, `listRunSources`, `listRunEvents`, `getEvidencePack` and `getFutureResult` stay byte-identical (the `futures-to-avoid` / `duplicate-future` blocks and TASK lines exist only in ALTERNATIVE runs; the internal Evidence Pack lookup by pack id returns the same pack for a STANDARD run); `RunFailuresTest` already expects the ALTERNATIVE_NOT_DISTINCT message; `ReasoningHarness` keeps working because `GenerationRequest` keeps its 5 components and the 2-arg `inputText` / 3-arg `body` stay (tests: none)
- Ranges & invariants: avoided futures n (runs of the session with the parent's Evidence Pack, COMPLETED with an accepted scenario, created before the alternative): classes n = 1 (only the parent), 2, 10 → all listed; n = 11 → 10 listed (parent as Future 1 + the 9 newest others, oldest first; the oldest other is dropped); steps per avoided future: 2…12 → all listed, 13 → first 12 + `- … and 1 more steps`; a title or statement of ≤ 300 code points → verbatim (after the data-line rule), 301 → first 300 + `…`; distinctness (`normalize` = trim, whitespace runs → one space, lower case `Locale.ROOT`): candidate title equal to any avoided title exactly / differing only in case / only in leading, trailing or repeated whitespace → finding `same future event title as future <i>`, any other title → none; candidate chain whose every normalized step statement occurs in the parent's (Future 1) chain (identical chain, reordered, subset) → finding `same causal steps as future 1`, ≥ 1 statement not in the parent's chain (even if it occurs in another avoided future) → none; distinct ⇔ no finding; invariants: an alternative's `configuration` deep-equals the parent's, `evidencePackId` = parent's, `parentRunId` = parent id, `kind` ALTERNATIVE; an ALTERNATIVE run sends 0 QUERY_EXPANSION / EVENT_NORMALIZATION / EVENT_CLASSIFICATION and 0 news / metadata requests and is never observed at `stageIndex` 1–6 (only 0, 7, 8, 9, 10); its `counts` equal the parent's except `sourcesUsed` (set from its own accepted scenario); every SCENARIO_GENERATION request of an ALTERNATIVE run contains exactly one `futures-to-avoid` block and the alternative TASK line, no STANDARD request contains either; each attempt reason at most once per run (≤ 5 SCENARIO_GENERATION requests, attempts 1..5); a COMPLETED alternative's accepted futureEvent title differs (normalized) from every avoided title and its chain has ≥ 1 statement not in the parent's chain; the parent row is never modified; a rejected request (any 4xx) creates no row.

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
- Precise test contract: "Slice 12_insufficient-evidence — FR-31 test contract" below (wins where more precise).
- Changes earlier behaviour: a run whose Evidence Pack has 0 items ended COMPLETED without headline at stage WRITING_STORY (interim of slices 07–09; asserted by `StructuredScenarioIT` #16, `CriticIT` #13, `EvidencePackIT`, `FutureResultIT` #12, `GetRunTerminalIT`, `SourceRetrievalIT` #13 and every IT extending `AbstractRunIT` on the default GDELT `{}`) → it ends INSUFFICIENT_EVIDENCE at stage RANKING / 6 under the default thresholds; those ITs keep their assertions unchanged because `AbstractRunIT` sets the test-only thresholds 0 ("Threshold 0 in earlier tests"), so only the base class is updated (tests: backend/src/test/java/com/oracul/app/runs/AbstractRunIT.java)
- Changes earlier behaviour: a pack with fewer CORE items than the threshold (e.g. fixture V4 = 1 core, E2E stub mode `ok` = 0 core under body `A`, realism 8) went on to scenario, critic and story → it ends INSUFFICIENT_EVIDENCE under the default thresholds; the slice 07–11 ITs (`AbstractEvidenceIT`, `AbstractReasoningIT`, `AbstractStoryIT`, `AbstractDeadlineIT` subclasses, `SourceRetrievalIT`) stay on the old path through the `AbstractRunIT` thresholds 0, and the existing E2E specs (realism 8) through `docker-compose.override.yml` (`ORACUL_EVIDENCE_MIN_CORE_MEDIUM` / `_LOW` = 0, not a test file), so no E2E spec changes (tests: backend/src/test/java/com/oracul/app/runs/AbstractRunIT.java)
- Changes earlier behaviour: the run view showed `progress-view` for INSUFFICIENT_EVIDENCE runs (slice 11 run-view rule 5) → `insufficient-view`; no existing assertion covers the old state, `run-view.spec.ts` only gains the new `describe('slice 12_insufficient-evidence')` (tests: none)
- Ranges & invariants: realism 1..10 (outside → `IllegalArgumentException` in `forRealism` / `insufficientEvidence`; API validation unchanged from FR-10) in three bands with default thresholds 1–5 → 1, 6–8 → 3, 9–10 → 5 CORE items (`forRealism` for r = 1…10 = 1,1,1,1,1,3,3,3,5,5); per band the classes core = threshold − 1 → INSUFFICIENT_EVIDENCE and core = threshold → continues to COMPLETED, checked at every band edge (realism 10/9 with 4/5 core, 8/6 with 2/3, 5 with 0/1, 2 with 0); only CORE items count (SUPPORTING / COUNTER_SIGNAL never; empty pack = 0 core); `suggestedRealism(r)` = empty for r = 1, otherwise max(1, r − 2) (2 → 1, 3 → 1, 4 → 2, 8 → 6, 10 → 8) and LOWER REALISM is rendered iff it is set; message ends exactly `Realism <r>.` for every r = 1…10; `oracul.evidence.min-core.high|medium|low` integer 0 … `oracul.evidence.core` (−1 or > core → startup fails naming the property; 0 → every pack sufficient for that band); invariants: an INSUFFICIENT_EVIDENCE run has `stageIndex` 6, `sourcesUsed` 0, no headline, 0 SCENARIO_GENERATION / SCENARIO_CRITIC / STORY_WRITING requests and 0 `scenario_attempt` / `future_story` rows; a run past its deadline never becomes INSUFFICIENT_EVIDENCE.

### FR-32 — Run failure handling
- Happy path (of the failure path): any failure ends the run with status FAILED, a RunFailureCode and its fixed
  message (table above), completed_at set, the active-run slot released. The failure view (`failure-view`) shows the
  message (`failure-message`) and "Try again" (`try-again`) which re-submits the failed run's configuration with
  `POST /api/runs`. The panel stays usable during and after the failure.
- Rules:
  - Timeout: a scheduler (every 5 s, injectable `Clock`) fails active (QUEUED or RUNNING) runs whose deadline_at
    passed with RUN_TIMEOUT; the pipeline checks the deadline between steps and abandons its work; late results of an abandoned
    run are discarded. From slice 06 on the check is `RunGuard` (status still RUNNING and now < deadline_at,
    injected `Clock`): before every ChatGPT request of stage 5 and inside the transaction that persists its results;
    a failed check sends no further request and writes nothing; if the run is still RUNNING past its deadline the
    pipeline itself commits RUN_TIMEOUT (`WHERE status = 'RUNNING'`), otherwise it leaves the run row untouched; later
    stage commits are conditional on `status = 'RUNNING'` (research-pipeline.md "Slice 06_events — Run guard").
  - ChatGPT 429 → CHATGPT_RATE_LIMITED immediately (no retry). 5xx/network/timeout → one retry after 1 s, then
    CHATGPT_UNAVAILABLE. 401/403 → one refresh + retry, then CHATGPT_SESSION_EXPIRED (connection state SESSION_EXPIRED).
  - On startup (before the web server accepts requests), every QUEUED/RUNNING run is set FAILED RUN_INTERRUPTED.
  - A FAILED run with code CHATGPT_SESSION_EXPIRED also refreshes the header connection state ("Session expired").
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
for "Future not found" only; slice 11 extends it — see "Slice 11_run-failures"), `run-error-message.ts` (snackbar content). `app.routes.ts`:
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

## Slice 11_run-failures — FR-32 test contract

Delivers the run deadline scheduler, the "between steps" deadline checks of stages 1–4, the startup sweep, one
central failure-message table, and the failure view for FAILED runs with "Try again". Most failure codes are already
produced by slices 05–10 (NEWS_UNAVAILABLE, CHATGPT_*, INVALID_SCENARIO, SCENARIO_REJECTED, INTERNAL_ERROR) and their
tests stay as they are; this slice adds RUN_TIMEOUT from the scheduler, RUN_INTERRUPTED, the UI and the hygiene
checks. Not in this slice: the insufficient-evidence view (12; INSUFFICIENT_EVIDENCE runs keep showing
`progress-view`), ALTERNATIVE_NOT_DISTINCT (17). No contract operation changes (only descriptions in
`api/openapi.yaml`: `GenerationRun.failure`, `RunFailureCode`).

### Backend (`com.oracul.app.runs`, `com.oracul.app.common`)

#### Failure table (`RunFailures`, `com.oracul.app.runs`)
`public final class RunFailures` with `public static String message(RunFailureCode code)` returning exactly the
messages of the "Run failure codes and messages" table above for every code except INSUFFICIENT_EVIDENCE (realism
dependent, slice 12: `message(INSUFFICIENT_EVIDENCE)` throws `IllegalArgumentException`). Every writer of a
`failure_message` (`ResearchPipeline`, `ReasoningPipeline`, `StoryWriter`, `ChatGptCallException` factories,
`PipelineExecutor`, `GenerationRunRepository.failTimedOut`, the scheduler and the startup sweep) takes the text from
`RunFailures` (existing constants may delegate). The stored message never depends on an exception message.

#### Deadline scheduler (`RunDeadlineScheduler`)
- `@EnableScheduling` on a configuration class in `com.oracul.app.common` (`SchedulingConfig`).
- `@Scheduled(fixedDelayString = "${oracul.run.deadline-check-interval:PT5S}", initialDelayString =
  "${oracul.run.deadline-check-interval:PT5S}")` calls `public int sweep()`.
- `sweep()`: `now` = injected `Clock` (truncated to µs, UTC); one statement
  `UPDATE generation_run SET status='FAILED', failure_code='RUN_TIMEOUT', failure_message='Generation took too long —
  try again', updated_at=now, completed_at=now WHERE status IN ('QUEUED','RUNNING') AND deadline_at <= now`; returns
  the number of updated rows. `stage` / `stageIndex` stay as they were (QUEUED → `stage` absent, `stageIndex` 0).
  Terminal runs are never touched. Any exception inside `sweep()` is logged (no row data) and swallowed, returning 0,
  so the next tick runs again.
- Boundary: a run is timed out iff `now >= deadline_at` (`deadline_at = created_at + oracul.run.timeout`, default
  180 s) — same rule as `RunGuard` (passes iff `now < deadline_at`).

#### Deadline checks between steps (pipeline)
- `PipelineExecutor` starts a task with a conditional transition `QUEUED → RUNNING, stage UNDERSTANDING` only
  `WHERE status = 'QUEUED' AND deadline_at > now`; if no row is updated the task ends at once: no ChatGPT / news /
  metadata request, no write — except that a still-QUEUED run past its deadline is committed RUN_TIMEOUT (see
  `failTimedOut`).
- Before each stage transition 2…10 the pipeline calls `RunGuard.check(runId)`; a failing check abandons the run:
  `GenerationRunRepository.failTimedOut(runId, now)` (now: `status IN ('QUEUED','RUNNING') AND deadline_at <= now`
  → RUN_TIMEOUT; otherwise no change) and the task ends — no further provider request, no further write.
- All run-row writes of stages 1–4 (`storeProfile`, `storeSearchPlan`, `storeSearchResults`, `storeCounts`) are
  conditional on `status = 'RUNNING'`; the stage-4 `source` insert runs in a transaction that first calls
  `RunGuard.lockAndCheck(runId)` and inserts nothing when it fails (then abandon as above). In-flight news / metadata
  requests of an abandoned run may complete, their results are discarded. Stages 5–10 keep their slice 06–10 guards.

#### Startup sweep (`RunStartupSweep`)
`@Component` implementing `SmartInitializingSingleton` (runs after Flyway, before the web server accepts requests);
`afterSingletonsInstantiated()` calls `public int sweep()`: `UPDATE generation_run SET status='FAILED',
failure_code='RUN_INTERRUPTED', failure_message='Generation was interrupted — try again', updated_at=now,
completed_at=now WHERE status IN ('QUEUED','RUNNING')`; returns the count; logs only the count.

#### No internals in any error
- Unchanged and regression-tested: the global handler maps any unexpected exception to `500
  {"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}` (`Content-Type: application/json`, exactly
  the keys `code`, `message`; no `trace`, `exception`, `path`, `error`, `timestamp`); `SessionFilter` answers the same
  body when the session lookup throws.
- The pipeline catch-all (`PipelineExecutor`) stores `INTERNAL_ERROR` "Something went wrong — try again" for any
  `RuntimeException`; the exception (message, class, stack) goes only to the redacted log.

### Backend tests
Common setup (extend `AbstractEvidenceIT` / `AbstractReasoningIT` as slice 09): connected session, V4 fixtures, body
`A`, `oracul.run.placeholder-stage-delay=PT0S`, `oracul.run.min-stage-duration=PT0S`, `oracul.openai.retry-delay=PT0S`.
`MutableClock` (new test class `com.oracul.app.runs.MutableClock extends Clock`, UTC, starts at `Instant.now()`,
`advance(Duration)`, `set(Instant)`) is registered as `@Primary Clock` bean by the deadline classes (the ChatGPT token
expiry and sessions use it too; the stub token lifetime of 3600 s is not crossed by +180 s). `T` = `"Generation took
too long — try again"`, `I` = `"Generation was interrupted — try again"`. "Unchanged row" = `status, stage,
failure_code, failure_message, updated_at, completed_at, counts` equal to the snapshot. "Slot released" = a new
`startRun` of the same session → 202.

`RunDeadlineIT` (`// @trace FR-32` and `// @trace NFR-2`; `MutableClock`; `oracul.run.deadline-check-interval=PT1H` so
only explicit `sweep()` calls act):
| # | Setup | Expected |
|---|---|---|
| 1 | `responses.gate("SCENARIO_GENERATION")`, run started, `awaitArrived(SCENARIO_GENERATION, 1)`; `clock.advance(PT179S)`; `sweep()` | returns 0; `getRun` `status` RUNNING, `stage` EXPLORING_FUTURES |
| 2 | #1 then `clock.advance(PT1S)` (now = createdAt + 180 s); `sweep()` | returns 1; `getRun`: `status` FAILED, `failure` `{"code":"RUN_TIMEOUT","message":T}`, `stage` EXPLORING_FUTURES, `stageIndex` 7, `completedAt` = clock now; `headline` absent |
| 3 | #2 then `responses.release("SCENARIO_GENERATION")`, watch 2.5 s | no SCENARIO_CRITIC / STORY_WRITING request; run row unchanged; 0 `future_story` rows; `getFutureResult` 409 `RESULT_NOT_READY`; slot released; a second `sweep()` returns 0 |
| 4 | `StubGdelt` responder delays every query 3000 ms; run started; `getRun` polled until `stage` SEARCHING; `clock.advance(PT180S)`; `sweep()`; wait 5 s | returns 1; FAILED RUN_TIMEOUT, `stage` SEARCHING, `stageIndex` 3; after 5 s: row unchanged, `counts` all 0, `GET /api/runs/{id}/sources` `{"items":[]}`, 0 `source` rows, 0 EVENT_NORMALIZATION requests |
| 5 | `responses.gate("QUERY_EXPANSION")`, run started, arrived 1; `clock.advance(PT180S)`; **no** `sweep()`; release | the pipeline itself commits FAILED `{"code":"RUN_TIMEOUT","message":T}` with `stage` RESEARCH_STRATEGY, `stageIndex` 2 (≤ 5 s); 0 GDELT requests (the check before the SEARCHING transition stops the task) |
| 6 | a COMPLETED run, a FAILED NEWS_UNAVAILABLE run (`/news` down) and an active gated run of 3 sessions; `clock.advance(PT180S)`; `sweep()` | returns 1; COMPLETED and FAILED rows unchanged (incl. `failure`, `completedAt`); only the active run is RUN_TIMEOUT |
| 7 | parameterized purpose P ∈ {EVENT_NORMALIZATION, STORY_WRITING}: gate P, arrive, `advance(PT180S)`, `sweep()`, release, watch 2.5 s | FAILED RUN_TIMEOUT at the stage of P (5 / 10); no request of a later purpose; row unchanged after release |

`RunDeadlineQueuedIT` (`// @trace FR-32`; `MutableClock`; `oracul.run.executor-threads=1`,
`deadline-check-interval=PT1H`):
| # | Setup | Expected |
|---|---|---|
| 1 | session X: gate QUERY_EXPANSION, start, arrived 1; session Y: `startRun` body `B` → 202 QUEUED; `clock.advance(PT180S)`; `sweep()` | returns 2; Y: `status` FAILED, `failure` `{RUN_TIMEOUT, T}`, `stage` and `stageLabel` absent, `stageIndex` 0, `completedAt` set |
| 2 | #1 then release; watch 3 s | QUERY_EXPANSION requests stay 1 (Y's task makes no request); Y row unchanged; Y slot released |
| 3 | X gated, Y QUEUED, `advance(PT180S)`, **no** `sweep()`, release X's gate | Y ends FAILED RUN_TIMEOUT (≤ 5 s, committed by Y's task start), `stageIndex` 0, QUERY_EXPANSION requests 1 |

`RunDeadlineSchedulerIT` (`// @trace FR-32`; `MutableClock`; `oracul.run.deadline-check-interval=PT1S`): gate
SCENARIO_GENERATION, arrived 1, `clock.advance(PT180S)`; without calling `sweep()`, `getRun` polled every 200 ms →
FAILED `{RUN_TIMEOUT, T}` within 3 s (the scheduled tick did it); release afterwards.

`RunStartupSweepIT` (`// @trace FR-32`): `@Autowired RunStartupSweep`.
| # | Setup | Expected |
|---|---|---|
| 1 | session X run active (gate QUERY_EXPANSION, arrived 1), session Y COMPLETED run, session Z FAILED NEWS_UNAVAILABLE run; `sweep()` | returns 1; X: FAILED `{"code":"RUN_INTERRUPTED","message":I}`, `completedAt` set, `stage` RESEARCH_STRATEGY unchanged; Y and Z rows unchanged |
| 2 | #1 then release, watch 2.5 s | 0 GDELT requests; X row unchanged; X slot released |
| 3 | row inserted by JDBC with `status` QUEUED (valid session, `deadline_at` = now + 180 s); `sweep()` | FAILED RUN_INTERRUPTED, `stageIndex` 0 |
`RunStartupSweepTest` (unit, `// @trace FR-32`): `RunStartupSweep` implements `SmartInitializingSingleton` and
`afterSingletonsInstantiated()` executes the sweep statement exactly once (mocked `JdbcTemplate` / repository).

`RunFailuresTest` (unit, `// @trace FR-32`): `RunFailures.message` for each code equals exactly:
NEWS_UNAVAILABLE "ORACUL could not reach its news sources — try again later", CHATGPT_RATE_LIMITED "ChatGPT plan limit
reached — try again later", CHATGPT_UNAVAILABLE "ChatGPT is unavailable right now — try again later",
CHATGPT_SESSION_EXPIRED "ChatGPT session expired — please reconnect", RUN_TIMEOUT "Generation took too long — try
again", INVALID_SCENARIO "ORACUL could not construct a valid scenario", SCENARIO_REJECTED "ORACUL could not construct a
scenario supported by current evidence", ALTERNATIVE_NOT_DISTINCT "ORACUL could not find a different future — try
changing a setting", RUN_INTERRUPTED "Generation was interrupted — try again", INTERNAL_ERROR "Something went wrong —
try again" (all U+2014); INSUFFICIENT_EVIDENCE → `IllegalArgumentException`. No message matches
`/https?:|[{}<>]|Exception|Error:|Bearer/`.

`RunFailureHygieneIT` (`// @trace FR-32`):
| # | Setup | Expected |
|---|---|---|
| 1 | `@MockitoSpyBean ResearchProfileFactory`: `from(any())` throws `new IllegalStateException("boom http://internal.example {\"x\":1} Bearer sk-test at com.oracul.app.X")` | run FAILED `{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}`, `stage` UNDERSTANDING, `completedAt` set; the raw `getRun` body contains none of `boom`, `IllegalState`, `http://internal`, `Bearer`, `sk-test`, `com.oracul`; slot released |
| 2 | ChatGPT 429 on STORY_WRITING (FR-32 acceptance 1, end to end in one place) | FAILED `{"code":"CHATGPT_RATE_LIMITED","message":"ChatGPT plan limit reached — try again later"}`; exactly 1 STORY_WRITING request (no retry); raw `getRun` body contains neither `rate_limited` nor `429` |
| 3 | `@MockitoSpyBean RunService`: `get(any(), any())` throws `new RuntimeException("SELECT * FROM generation_run password=x")` | `GET /api/runs/{id}` → 500, `Content-Type` `application/json`, body exactly `{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}` (key set exactly `code`, `message`); same for `start(any(), any())` on `POST /api/runs` body `B` |
| 4 | `@MockitoSpyBean SessionService`: `resolve(any())` throws | `GET /api/runs/{NO_RUN}` → 500 with exactly the body of #3 |

Existing tests stay green; rows of earlier slices that asserted "progress view until slice 11" are superseded only in
the frontend/E2E (below).

### Frontend (`src/app/runs/`)
- `run-failure.ts` (`app-run-failure`): inputs `message: string` (default `"Future not found"`) and
  `configuration: ScenarioConfiguration | null` (default `null`). Template unchanged ids: `failure-view` containing
  `failure-message` (exactly the message text) and `try-again` (`mat-flat-button`, text "Try again"). Click:
  `configuration` null → `router.navigateByUrl('/')` (not-found case, unchanged); otherwise
  `RunStore.start(configuration)` (same flow as FR-10: 202 → navigate `/futures/<newId>` with `replaceUrl`, the new
  run's `progress-view` replaces the failure view; error → snackbar `run-error-message` with `ApiError.message` or
  "Something went wrong — try again", the failure view stays). `try-again` is disabled while `RunStore.starting()`.
- `run-view.ts`, exactly one of (in this order):
  1. `notFound()` → `<app-run-failure>` with defaults ("Future not found", navigate `/`);
  2. `unavailable()` → `backend-unavailable` (unchanged);
  3. `run().status === 'FAILED'` → `<app-run-failure [message]="run.failure?.message ?? 'Something went wrong — try
     again'" [configuration]="run.configuration">`; no `progress-view`, no `app-future-result`, no result request;
  4. COMPLETED with `headline` → `app-future-result` (unchanged);
  5. otherwise (QUEUED, RUNNING, COMPLETED without headline, INSUFFICIENT_EVIDENCE until slice 12) → `progress-view`.
  (Slice 12 inserts `INSUFFICIENT_EVIDENCE` → `app-insufficient-evidence` between 3 and 4 — see "Slice
  12_insufficient-evidence".)
- The failure view shows only `failure.message` and "Try again": never `failure.code`, ids, URLs, JSON or stack text.
- `RunStore`: when a `getRun` response (poll or `open`) has `status` FAILED and `failure.code`
  `CHATGPT_SESSION_EXPIRED`, call `ConnectionStore.load()` once for that run (header shows "Session expired").
- Panel stays usable: the Scenario Panel (`slider-*`, `horizon-option-*`, `wildcard-toggle-*`) is never disabled by
  `RunStore` state (QUEUED / RUNNING / FAILED); "Try again" re-submits the **failed run's** configuration and does not
  change the panel; changes made in the panel are kept.

### Frontend unit tests (Vitest; `// @trace FR-32`)
- `run-failure.spec.ts`: default inputs → text "Future not found", click → navigates `/`, no `startRun`; with
  `configuration` C and message M → `failure-message` exactly M, click → one `POST /api/runs` whose body deep-equals
  C; while pending `try-again` is disabled; 202 → router navigates to `/futures/<newId>` with `replaceUrl: true`; 409
  `{"code":"RUN_ALREADY_ACTIVE","message":"A generation is already running"}` → `run-error-message` that text, failure
  view still rendered, button enabled again; network error → "Something went wrong — try again".
- `run-view.spec.ts` (`describe('slice 11_run-failures')`): for each code with its table message (parameterized over
  NEWS_UNAVAILABLE, CHATGPT_RATE_LIMITED, CHATGPT_UNAVAILABLE, CHATGPT_SESSION_EXPIRED, RUN_TIMEOUT, INVALID_SCENARIO,
  SCENARIO_REJECTED, RUN_INTERRUPTED, INTERNAL_ERROR) a FAILED run → only `failure-view` among
  `progress-view` / `failure-view` / `backend-unavailable` / `app-future-result`, `failure-message` = the message, no
  `GET /api/runs/{id}/result`; FAILED without `failure` → "Something went wrong — try again"; polling RUNNING →
  FAILED `{RUN_TIMEOUT}` switches the progress view to the failure view within one tick (1000 ms, fake timers) and
  polling stops; `failure-view` textContent never contains the code, the run id, the generationId, `http`, `{`, `}`.
- `run.store.spec.ts`: FAILED `CHATGPT_SESSION_EXPIRED` → `ConnectionStore.load` called exactly once (also after
  later re-renders); FAILED with another code → not called.
- `app.spec.ts` (or `scenario-panel` spec): with `RunStore.run()` RUNNING and FAILED, `slider-darkness-input` and
  `horizon-option-5y` are enabled and changing darkness updates `value-darkness`.

### E2E (`e2e/tests/run-failures.spec.ts`; `// @trace FR-32`; fresh context per test, stub reset as in
`future-story.spec.ts`, serial)
No new stub modes: 429 uses the existing `POST /__control/story {"mode":"rate-limited"}`; a real 180 s timeout is not
run in E2E (stack timeout stays PT3M; the timing proof is `RunDeadlineIT`, as NFR-2 prescribes).
1. 429: story `rate-limited`; connect, configure acceptance `A`, `generate-button` → `failure-view` visible (timeout
   90 s), `failure-message` exactly "ChatGPT plan limit reached — try again later"; `progress-view` and `result-view`
   count 0; `failure-view` innerText matches none of `/https?:\/\/|[{}]|Exception|Error:|rate_limited|429|ORC-/`;
   `GET /api/runs/<id>` → `status` FAILED, `failure.code` CHATGPT_RATE_LIMITED.
2. Panel usable + Try again (same test): fill `slider-darkness-input` `3` → `value-darkness` "3"; set story `ok`;
   click `try-again` with `page.waitForRequest` on `POST /api/runs` → `postDataJSON()` deep-equals `A` (darkness 9, the
   failed run's configuration); URL becomes `/futures/<newId>` (≠ old id); `result-view` visible (timeout 90 s);
   `value-darkness` still "3".
3. Timeout message (UI): `page.route('**/api/runs/11111111-1111-1111-1111-111111111111', …)` fulfils 200 with
   `{"id":"11111111-1111-1111-1111-111111111111","generationId":"ORC-2026-10-02-1842","kind":"STANDARD","status":"FAILED","stage":"SEARCHING","stageLabel":"Searching current events…","stageIndex":3,"stageCount":10,"configuration":B,"counts":<zero counts>,"failure":{"code":"RUN_TIMEOUT","message":"Generation took too long — try again"},"createdAt":"2026-10-02T18:42:31Z","updatedAt":"2026-10-02T18:45:31Z","completedAt":"2026-10-02T18:45:31Z","hasOpenCriticIssues":false}`
   (B = slice 04 base body); `goto('/futures/11111111-1111-1111-1111-111111111111')` → `failure-message` exactly
   "Generation took too long — try again", `try-again` visible and enabled.
4. News unavailable: `/__control/news` `down`, start a run → `failure-message` "ORACUL could not reach its news
   sources — try again later".
5. No internals over HTTP: `page.request.get('/api/runs/abc')` → 404 body exactly `{"code":"RUN_NOT_FOUND","message":
   "Future not found"}`; `page.request.post('/api/runs', {data: 'not json', headers: {'content-type':
   'application/json'}})` → 400 body with exactly the keys `code`, `message` and no `trace` / `exception`.

Superseded assertions of earlier slices (FAILED runs now show the failure view):
- `e2e/tests/search-sources.spec.ts` "news provider down": `progress-view` visible → `failure-view` with
  `failure-message` "ORACUL could not reach its news sources — try again later" (research-pipeline.md slice 05 note).
- `e2e/tests/future-story.spec.ts` `invalid`: additionally `failure-message` "ORACUL could not construct a valid
  scenario" (future-result.md "the progress view stays until slice 11").
- scenario-reasoning.md slice 08 UI note "failed runs still show `progress-view` until slice 11" → failure view.

### data-testid (this slice)
Reused: `failure-view`, `failure-message`, `try-again`, `run-error-message`, `progress-view`, `result-view`,
`slider-darkness-input`, `value-darkness`, `horizon-option-<code>`, `generate-button`, `chatgpt-status`. No new ids.

## Slice 12_insufficient-evidence — FR-31 test contract

Delivers the sufficiency check at the end of stage 6 RANKING, the terminal status INSUFFICIENT_EVIDENCE with its
realism-dependent message and `suggestedRealism`, and the insufficient view with LOWER REALISM. No new operation and
no Flyway migration (`generation_run.suggested_realism` exists since V3); `api/openapi.yaml` changes descriptions only
(`GenerationRun.suggestedRealism` / `failure` / `stage`, `RunFailureCode`, `getEvidencePack`, `getStructuredScenario`,
`getFutureResult`). Not in this slice: Recent futures (15; INSUFFICIENT_EVIDENCE runs are never listed), quick
actions (16), alternatives (17; ALTERNATIVE runs skip stage 6 and are never checked).

### Configuration (new; `EvidenceConfig`, `@Value`; invalid values fail startup naming the property)
| Property | Env (Compose) | Default | Applies to realism | Rule |
|---|---|---|---|---|
| `oracul.evidence.min-core.high` | `ORACUL_EVIDENCE_MIN_CORE_HIGH` | 5 | 9–10 | integer 0 … `oracul.evidence.core` |
| `oracul.evidence.min-core.medium` | `ORACUL_EVIDENCE_MIN_CORE_MEDIUM` | 3 | 6–8 | integer 0 … `oracul.evidence.core` |
| `oracul.evidence.min-core.low` | `ORACUL_EVIDENCE_MIN_CORE_LOW` | 1 | 1–5 | integer 0 … `oracul.evidence.core` |
Startup failure message contains the property name (as `EvidenceConfigurationTest` asserts for slice 07), e.g.
`oracul.evidence.min-core.high=-1` or `=11` (default `oracul.evidence.core` 10). The value 0 disables the check for
that band (test configuration only, see "Threshold 0 in earlier tests").

### Pure classes (`com.oracul.app.research`, no Spring)
- `record MinCoreThresholds(int high, int medium, int low)` with `static MinCoreThresholds defaults()` = `(5, 3, 1)`
  and `int forRealism(int realism)`: 9–10 → high, 6–8 → medium, 1–5 → low; realism outside 1–10 →
  `IllegalArgumentException`. Exposed as a Spring bean by `EvidenceConfig`.
- `final class EvidenceSufficiency` with
  - `static boolean sufficient(int coreItems, int realism, MinCoreThresholds t)` = `coreItems >= t.forRealism(realism)`;
  - `static OptionalInt suggestedRealism(int realism)` = empty for realism 1, otherwise `max(1, realism − 2)`.
  Only CORE items count (SUPPORTING and COUNTER_SIGNAL never do; an empty pack has 0 core items).
- `RunFailures.insufficientEvidence(int realism)` (`com.oracul.app.runs`) returns exactly
  `ORACUL found insufficient current evidence to construct this scenario at Realism <realism>.` (ASCII full stop at the
  end, realism as plain integer); realism outside 1–10 → `IllegalArgumentException`. `RunFailures.message
  (INSUFFICIENT_EVIDENCE)` keeps throwing (slice 11).

### Pipeline (`ResearchPipeline`, end of stage 6)
1. Stage 6 steps 1–3 unchanged (slice 07). In the same guarded transaction as step 4 (after `SELECT … FOR UPDATE` and
   `RunGuard.check`, conditional on `status = 'RUNNING'`): write rankings, insert `evidence_pack`, set
   `evidence_pack_id` and counts as before; then evaluate `EvidenceSufficiency.sufficient(pack.core.size(),
   configuration.realism, thresholds)`.
2. Insufficient → in that same transaction: `status = INSUFFICIENT_EVIDENCE`, `failure_code = INSUFFICIENT_EVIDENCE`,
   `failure_message = RunFailures.insufficientEvidence(realism)`, `suggested_realism` = `suggestedRealism(realism)`
   (null for realism 1), `updated_at = completed_at = now`; `stage` stays RANKING, `stageIndex` 6. The task then ends
   immediately: no `min-stage-duration` wait, no EXPLORING_FUTURES transition, no SCENARIO_GENERATION /
   SCENARIO_CRITIC / STORY_WRITING request, no `scenario_attempt` / `future_story` row. The active-run slot is released.
3. Sufficient → unchanged (wait remainder, stage 7 …).
4. Guard fails → nothing of the transaction is written; RUN_TIMEOUT handling exactly as slice 06/11 (a run past its
   deadline never becomes INSUFFICIENT_EVIDENCE). The deadline scheduler and startup sweep never touch
   INSUFFICIENT_EVIDENCE rows (terminal).
5. `counts` of an insufficient run: `searches` … `counterSignals` as computed, `sourcesUsed` 0. `headline` absent,
   `hasOpenCriticIssues` false, `evidencePackId` set.

### API of an INSUFFICIENT_EVIDENCE run
| Operation | Status | Body |
|---|---|---|
| `GET /api/runs/{runId}` | 200 | `status` `INSUFFICIENT_EVIDENCE`, `stage` `RANKING`, `stageIndex` 6, `stageLabel` `Ranking evidence…`, `failure` `{"code":"INSUFFICIENT_EVIDENCE","message":"ORACUL found insufficient current evidence to construct this scenario at Realism <n>."}`, `suggestedRealism` (absent for realism 1), `completedAt` set, `headline` absent, `hasOpenCriticIssues` false |
| `GET /api/runs/{runId}/evidence-pack` | 200 | the stored pack (CORE items < threshold) |
| `GET /api/runs/{runId}/events`, `/sources`, `/research` | 200 | as for any run past stage 6 |
| `GET /api/runs/{runId}/structured-scenario` | 409 | `{"code":"SCENARIO_NOT_READY","message":"The scenario is not ready yet"}` |
| `GET /api/runs/{runId}/result` | 409 | `{"code":"RESULT_NOT_READY","message":"This future is not ready yet"}` |
| `POST /api/runs` (same session, valid body) | 202 | new run (slot released) |

### Threshold 0 in earlier tests (test configuration, no product behaviour)
- Backend: `AbstractRunIT` gets `@TestPropertySource(properties = {"oracul.evidence.min-core.high=0",
  "oracul.evidence.min-core.medium=0", "oracul.evidence.min-core.low=0"})`. With threshold 0 every pack is sufficient,
  so all ITs of slices 04–11 keep their asserted behaviour (an empty pack still skips generation and ends COMPLETED
  without headline, the slice-08 path). FR-31 ITs override with the default values explicitly.
- E2E: `docker-compose.override.yml` backend env adds `ORACUL_EVIDENCE_MIN_CORE_MEDIUM: "0"` and
  `ORACUL_EVIDENCE_MIN_CORE_LOW: "0"`; `high` keeps the default 5. Existing E2E specs use realism 8 and stay
  unchanged; FR-31 is exercised in E2E at Realism 10. The production `docker-compose.yml` sets none of them.

### Backend tests
Fixture **K(c, s)** (helper in `InsufficientEvidenceIT` or `AbstractEvidenceIT`): the first GDELT request returns
c + s articles named `k-1` … `k-<c+s>` (domain `reuters.com`, titles `K article <i>`, seendate testNow − 1 day,
English), every other request `{}`; each page `k-<i>` has its own site name `Publisher <i>` (`gdelt.site(...)`, so the
per-publisher cap never applies). Default normalisation (one event per source, entity `Entity S<nnn>`) gives EV001 …
EV<c+s> ↔ S001 … in article order. Classification answers `StubResponses.defaultClassificationEntry` with risk 0.9 /
opportunity 0.1 for EV001 … EV<c> and risk 0.1 / opportunity 0.8 for the rest. Under a dark body (darkness 9,
optimism 2) the pack is c CORE (c ≤ 10), 0 SUPPORTING, min(s, 5) COUNTER_SIGNAL. `A(n)` = body `A` with `realism` n.
Every test asserts the pack's core size equals c before asserting the outcome.

`InsufficientEvidenceIT` (`// @trace FR-31`; extends `AbstractStoryIT`; `@TestPropertySource`
`oracul.evidence.min-core.high=5`, `medium=3`, `low=1`):
| # | Setup | Expected |
|---|---|---|
| 1 | K(2, 3), body A(10) (FR-31 acceptance 1) | `getRun` as the table above with message `…at Realism 10.`, `suggestedRealism` 8; `counts.eventsSelected` 5, `counterSignals` 3, `sourcesUsed` 0; 0 requests of SCENARIO_GENERATION, SCENARIO_CRITIC, STORY_WRITING; 0 `scenario_attempt` and 0 `future_story` rows for the run; `getEvidencePack` 200 with 2 core / 3 counter-signals; `getStructuredScenario` 409 SCENARIO_NOT_READY; `getFutureResult` 409 RESULT_NOT_READY; a new `startRun` of the session → 202 |
| 2 | parameterized (realism, c, expected): (10,4,INSUFFICIENT/8) (10,5,COMPLETED) (9,4,INSUFFICIENT/7) (9,5,COMPLETED) (8,2,INSUFFICIENT/6) (8,3,COMPLETED) (6,2,INSUFFICIENT/4) (6,3,COMPLETED) (5,0,INSUFFICIENT/3) (5,1,COMPLETED) (2,0,INSUFFICIENT/1); K(c, 3), body A(realism) | INSUFFICIENT rows: status, `failure.message` with that realism, `suggestedRealism` as given, `stageIndex` 6, no SCENARIO_GENERATION request. COMPLETED rows: status COMPLETED with `headline`, `suggestedRealism` and `failure` absent, exactly 1 STORY_WRITING request |
| 3 | K(0, 3), body A(1) | INSUFFICIENT_EVIDENCE, message `…at Realism 1.`, `suggestedRealism` absent (key not present) |
| 4 | default GDELT `{}` (0 sources, empty pack), body `A` | INSUFFICIENT_EVIDENCE, message `…at Realism 8.`, `suggestedRealism` 6, `stageIndex` 6, `counts.eventsSelected` 0, `getEvidencePack` 200 with `core` / `supporting` / `counterSignals` all `[]`; 0 SCENARIO_GENERATION, SCENARIO_CRITIC and STORY_WRITING requests |
| 5 | K(2, 3), body A(10); raw `getRun` body | contains none of `http`, `Exception`, `com.oracul`, `{"core` (the message is the fixed text only) |

`EvidenceSufficiencyTest` (unit, `// @trace FR-31`): `MinCoreThresholds.defaults().forRealism(r)` for r = 1…10 is
1,1,1,1,1,3,3,3,5,5; `forRealism(0)` / `(11)` throw; `sufficient(c, r, defaults)` true iff c ≥ threshold for the
boundary pairs of IT #2 (both sides); `sufficient(0, r, new MinCoreThresholds(0,0,0))` true for every r;
`suggestedRealism`: 1 → empty, 2 → 1, 3 → 1, 4 → 2, 8 → 6, 10 → 8.
`RunFailuresTest` (extend, `// @trace FR-31`): `insufficientEvidence(10)` equals exactly "ORACUL found insufficient
current evidence to construct this scenario at Realism 10."; for r = 1…10 ends with `Realism <r>.`; 0 and 11 throw.
`EvidenceConfigurationTest` (extend, `// @trace FR-31`): `oracul.evidence.min-core.high=-1`, `high=11`,
`medium=-1`, `medium=11`, `low=-1`, `low=11` each fail startup naming the property; a context with
`oracul.evidence.core=4` and `min-core.high=5` fails; a context without overrides exposes `MinCoreThresholds(5,3,1)`.

### Frontend (`src/app/runs/`)
- `insufficient-evidence.ts` (`app-insufficient-evidence`), input `run: GenerationRun` (required). Template:
  - `insufficient-view` (container) containing `insufficient-message`: exactly `run.failure.message`, or when
    `failure` is missing `ORACUL found insufficient current evidence to construct this scenario at Realism
    <run.configuration.realism>.`;
  - `lower-realism`: `mat-flat-button`, text exactly "LOWER REALISM", rendered iff `run.suggestedRealism` is set;
    disabled while `RunStore.starting()` or `RunStore.active()` or `!ConnectionStore.canGenerate()`.
  - Nothing else: no failure code, run id, generationId, counts, URLs or JSON; no "Try again".
- Click `lower-realism`: `ScenarioStore.load({ ...run.configuration, realism: run.suggestedRealism })` (panel shows
  the run's configuration with the lowered realism, `value-realism` = suggestedRealism), then
  `RunStore.start(ScenarioStore.configuration())` → `POST /api/runs` whose body deep-equals `run.configuration` with
  `realism` = `suggestedRealism`. 202 → navigate `/futures/<newId>` with `replaceUrl: true` (FR-10 flow, the new run's
  `progress-view` replaces the insufficient view). Error → snackbar `run-error-message` with `ApiError.message` or
  "Something went wrong — try again"; the insufficient view stays, the panel keeps the lowered realism, the button is
  enabled again.
- `run-view.ts` order becomes: 1 `notFound()` → failure view; 2 `unavailable()` → `backend-unavailable`; 3 FAILED →
  `app-run-failure`; **4 INSUFFICIENT_EVIDENCE → `app-insufficient-evidence [run]="run"`** (no `progress-view`,
  `failure-view` or `app-future-result`, no `GET …/result`); 5 COMPLETED with `headline` → `app-future-result`;
  6 otherwise → `progress-view`. `RunStore` polling already stops on INSUFFICIENT_EVIDENCE (unchanged).
- UI route: `/futures/:runId` (unchanged).

### Frontend unit tests (Vitest; `// @trace FR-31`)
- `insufficient-evidence.spec.ts`: run realism 10 / suggestedRealism 8 / failure message M → `insufficient-message`
  exactly M, `lower-realism` text "LOWER REALISM" enabled (connected, no active run); click → `ScenarioStore.realism`
  8 and exactly one `POST /api/runs` whose body deep-equals the run configuration with realism 8; pending → button
  disabled; 202 → navigate `['/futures', newId]` with `replaceUrl: true`; 409 `RUN_ALREADY_ACTIVE` → `run-error-message`
  "A generation is already running", view still rendered, realism stays 8; run realism 1 without suggestedRealism →
  no `lower-realism` element, message `…at Realism 1.`; `failure` missing → fallback text with the configuration's
  realism; not connected → button disabled; textContent never contains `INSUFFICIENT_EVIDENCE`, the run id, `ORC-`,
  `http`, `{`.
- `run-view.spec.ts` (`describe('slice 12_insufficient-evidence')`): INSUFFICIENT_EVIDENCE run → only
  `insufficient-view` among `progress-view` / `failure-view` / `backend-unavailable` / `app-future-result` /
  `insufficient-view` is rendered; no `GET /api/runs/{id}/result`; polling RUNNING → INSUFFICIENT_EVIDENCE switches to the
  insufficient view within one tick (1000 ms) and polling stops.

### E2E (`e2e/tests/insufficient-evidence.spec.ts`; `// @trace FR-31`; serial, fresh context per test, stub reset as
in `future-story.spec.ts`)
Stub extension (`e2e/stubs/server.mjs`): `POST /__control/events {"mode":"sparse"}` (204; added to the accepted
modes, reset to `ok` by `/__control/reset`). In mode `sparse` EVENT_CLASSIFICATION answers like `ok` except risk /
opportunity: EV001 and EV002 → risk 1.0 / opportunity 0.0; every other event → risk 0.1 / opportunity 0.8. With
`A10` = acceptance body `A` with realism 10 this gives exactly 2 CORE items (all others are counter-signal
candidates).
1. Acceptance 1: events `sparse`; connect; configure `A10` through the panel (`slider-realism-input` 10, darkness 9,
   optimism 2, horizon `5y`, `biology-new-pandemic` 8, `robotics-humanoid-boom` 6); click `generate-button` →
   `insufficient-view` visible (timeout 90 s); `insufficient-message` exactly "ORACUL found insufficient current
   evidence to construct this scenario at Realism 10."; `lower-realism` visible, enabled, text "LOWER REALISM";
   `progress-view`, `failure-view`, `result-view` count 0; `GET /api/runs/<id>` → `status` INSUFFICIENT_EVIDENCE,
   `failure.code` INSUFFICIENT_EVIDENCE, `suggestedRealism` 8; `GET /api/runs/<id>/evidence-pack` → `core` length 2;
   `GET /api/runs/<id>/result` → 409 `RESULT_NOT_READY`; `/__control/requests?kind=responses` contains no
   SCENARIO_GENERATION, SCENARIO_CRITIC or STORY_WRITING request (no story generated).
2. Acceptance 2 (same test): click `lower-realism` with `page.waitForRequest` on `POST /api/runs` → `postDataJSON()`
   deep-equals `A10` with realism 8 (= `A`); `value-realism` "8"; URL becomes `/futures/<newId>` (≠ old id);
   `GET /api/runs/<newId>` → `configuration.realism` 8; wait until `result-view` visible (timeout 90 s; E2E threshold
   for realism 8 is 0) so no run leaks into the next test.
3. Realism 1 (UI only): `page.route('**/api/runs/22222222-2222-2222-2222-222222222222', …)` fulfils 200 with a
   GenerationRun `{"id":"22222222-2222-2222-2222-222222222222","generationId":"ORC-2026-10-02-1842","kind":"STANDARD","status":"INSUFFICIENT_EVIDENCE","stage":"RANKING","stageLabel":"Ranking evidence…","stageIndex":6,"stageCount":10,"configuration":<B with realism 1>,"counts":<zero counts>,"failure":{"code":"INSUFFICIENT_EVIDENCE","message":"ORACUL found insufficient current evidence to construct this scenario at Realism 1."},"createdAt":"2026-10-02T18:42:31Z","updatedAt":"2026-10-02T18:43:31Z","completedAt":"2026-10-02T18:43:31Z","hasOpenCriticIssues":false}`
   (no `suggestedRealism`); `goto('/futures/22222222-2222-2222-2222-222222222222')` → `insufficient-message` exactly
   "…at Realism 1.", `lower-realism` count 0.

### data-testid (this slice)
New in use: `insufficient-view`, `insufficient-message`, `lower-realism`. Reused: `generate-button`,
`slider-realism-input`, `value-realism`, `slider-darkness-input`, `slider-optimism-input`, `horizon-option-<code>`,
`wildcard-toggle-<wildcardId>`, `wildcard-intensity-<wildcardId>-input`, `run-error-message`, `progress-view`, `failure-view`, `result-view`.

## Slice 16_quick-regeneration — FR-29 test contract

Delivers the quick regeneration bar on the result view. Frontend only: no migration, no change to
`api/openapi.yaml` (it reuses `startRun`), no new `ApiError.code`. Not in this slice: ALTERNATIVE FUTURE
(`quick-alternative`, slice 17 adds it to the same component), mobile layout (FR-34). Where this section is more
precise than "Behaviour" or "UI", this section wins.

### Frontend (`src/app/runs/quick-actions.ts`, selector `app-quick-actions`)
- Rendered by `FutureResultComponent` inside `result-view` (after `app-scenario-metadata`, before `app-why-sources`),
  so it exists only when a COMPLETED run's result is loaded; never in `progress-view`, `insufficient-view`,
  `failure-view`, `result-loading` or `welcome-view`. No inputs: it reads `ScenarioStore`, `RunStore`, `ConnectionStore`.
- Markup: container `<div data-testid="quick-actions">` with exactly 4 `mat-stroked-button`s in this order:
  | data-testid | text (exact) | control | target |
  |---|---|---|---|
  | `quick-more-realistic` | MORE REALISTIC | realism | `min(10, v + 2)` |
  | `quick-darker` | DARKER | darkness | `min(10, v + 2)` |
  | `quick-more-optimistic` | MORE OPTIMISTIC | optimism | `min(10, v + 2)` |
  | `quick-more-extreme` | MORE EXTREME | realism | `max(1, v − 3)` |
- Pure helper exported from `quick-actions.ts`:
  `export type QuickAction = 'MORE_REALISTIC' | 'DARKER' | 'MORE_OPTIMISTIC' | 'MORE_EXTREME';`
  `export function quickTarget(action: QuickAction, value: number): number | null` — the target of the table, or
  `null` when `value` is already at the bound (10 for the three "+2" actions, 1 for MORE EXTREME).
- `v` is the **current panel value** (`ScenarioStore.realism()` / `darkness()` / `optimism()`). On `/futures/:runId`
  opened through `RunStore.open` the panel already holds that run's configuration (slice 15); after a `start()` it holds
  the configuration that was started. Panel edits made while the result is shown are part of the next quick run.
- Button state (native `disabled` attribute): disabled iff `quickTarget(action, v) === null` or
  `!ConnectionStore.canGenerate()` or `RunStore.starting()` or `RunStore.active()`.
- Click on an enabled button, in this order: `ScenarioStore.setRealism|setDarkness|setOptimism(target)`, then
  `RunStore.start(ScenarioStore.configuration())`. The handler re-checks the disabled conditions and does nothing
  (no store change, no request) when one holds.
  - 202 → (existing `RunStore.start` flow) URL `/futures/<newId>` (`replaceUrl`), center shows `progress-view` of the
    new run, then its result when COMPLETED; the panel keeps the target value (no `ScenarioStore.load` after `start`).
  - error → snackbar `run-error-message` with `ApiError.message` (or "Something went wrong — try again" when the body
    is not an ApiError), e.g. 409 → "A generation is already running", 401 → "Connect ChatGPT to generate" plus
    `ConnectionStore.load()`; URL and `result-view` of the previous run stay; the panel keeps the target value; the
    buttons are enabled again (by the rule above).
- Examples: darkness 5 → DARKER → panel and body darkness 7; darkness 9 → 10; darkness 10 → `quick-darker` disabled;
  realism 2 → MORE EXTREME → 1; realism 1 → `quick-more-extreme` disabled, `quick-more-realistic` enabled;
  realism 10 → `quick-more-realistic` disabled, `quick-more-extreme` enabled (→ 7).

### Frontend unit tests (Vitest; `// @trace FR-29`)
- `src/app/runs/quick-actions.spec.ts`:
  1. `quickTarget` for all 4 actions × v = 1…10 (parameterized, 40 cases) equals the table (`null` at the bound).
  2. Rendering (component created with `provideRouter`, `provideHttpClient`, `provideHttpClientTesting`; panel via
     `ScenarioStore.load`, connection via `ConnectionStore.canGenerate.set(true)`): 4 buttons with the exact texts in
     table order; no `quick-alternative`.
  3. Per action, parameterized over v = 1…10 with every other control at 5: enabled iff the target is not null; click
     → exactly one `POST /api/runs` whose body deep-equals the start configuration with only that control replaced by
     the target, and `ScenarioStore.configuration()` equals that body; at the bound: click → 0 requests, panel unchanged.
     The start configuration includes horizon `5y`, wildcards `[{biology-new-pandemic, 8}, {robotics-humanoid-boom, 6}]`
     and one custom wildcard so the "only one field changes" invariant is checked on a full body.
  4. `canGenerate` false → all 4 disabled, click → 0 requests.
  5. While the POST is pending (`starting()`) all 4 disabled; a second click → still exactly 1 request.
  6. `RunStore.run()` QUEUED/RUNNING (`active()`) → all 4 disabled.
  7. 202 → router URL `/futures/<newId>`; panel darkness still 7.
  8. 409 `RUN_ALREADY_ACTIVE` → `run-error-message` "A generation is already running"; panel darkness 7; buttons enabled
     again; URL unchanged. Status 0 / non-ApiError 500 → "Something went wrong — try again".
- `src/app/runs/run-view.spec.ts` (new `describe('slice 16_quick-regeneration')` only): `/futures/<id>` with a COMPLETED
  run (headline set) and its `getFutureResult` flushed → `quick-actions` inside `result-view`; QUEUED, FAILED and
  INSUFFICIENT_EVIDENCE runs → `quick-actions` count 0; with the run configuration darkness 5 (loaded into the panel by
  `open`), click `quick-darker` → `POST /api/runs` body = configuration with darkness 7; flush 202 with a new QUEUED run
  → `progress-view` visible, URL `/futures/<newId>`, `ScenarioStore.darkness()` 7.

### Backend test (`backend/src/test/java/com/oracul/app/runs/QuickRegenerationIT.java`, extends `AbstractRunIT`; `// @trace FR-29`)
No product change; proves acceptance 3 on the API. `connectedSid()`; insert run 1 with `jdbc` for that session as in
`RecentRunsIT.seed` (status COMPLETED, headline "Quick base headline", configuration `B` = darkness 5, created_at
now − 1 h, completed_at set); `startOk(sid, B with "darkness":7)` → 202 `kind` STANDARD, `configuration.darkness` 7,
`configuration` otherwise deep-equal to `B`, `parentRunId` absent, `id` ≠ run 1; `awaitTerminal`; then run 1's row is
unchanged (status, headline, configuration jsonb, completed_at, updated_at as inserted) and `GET /api/runs` (same
cookie) contains run 1's `id` with headline "Quick base headline" and `configuration.darkness` 5.

### E2E (`e2e/tests/quick-regeneration.spec.ts`; `// @trace FR-29`; serial; `resetStub` and `connect` as in `recent-futures.spec.ts`; fresh context per test; `test.setTimeout(120_000)`)
1. Acceptance 1 + 3: connect; panel defaults (darkness 5); `generate-button` → wait for `result-view` (timeout 60 s),
   remember run 1 id from the URL; `quick-actions` visible with 4 enabled buttons; click `quick-darker` with
   `page.waitForRequest` on `POST /api/runs` → `postDataJSON()` deep-equals `B` with darkness 7;
   `slider-darkness-input` value `7`; URL becomes `/futures/<run2>` (≠ run 1); `GET /api/runs/<run2>` →
   `configuration.darkness` 7. Evidence `FR-29 quick-darker-started`. Wait for `result-view` of run 2 (timeout 60 s).
   Open `recent-futures-button` → `recent-future-<run2>` first, `recent-future-<run1>` present with settings
   `R8 D5 O5 · 1 year`; click it → URL `/futures/<run1>`, `result-view` visible, `meta-darkness` "Darkness 5/10".
   Evidence `FR-29 previous-result-in-recent-futures`.
2. Acceptance 2 + clamp: connect; set `slider-darkness-input` `9`; generate, wait for `result-view`; click
   `quick-darker` → request body darkness 10 (clamped); wait for `result-view` of the new run; `quick-darker` is
   disabled; `quick-more-realistic`, `quick-more-optimistic`, `quick-more-extreme` enabled;
   `slider-darkness-input` value `10`. Evidence `FR-29 darker-disabled-at-10`.
   (E2E uses only darkness changes: realism 10 would hit the E2E sufficiency threshold for high realism.)

### data-testid (this slice)
New in use: `quick-actions`, `quick-more-realistic`, `quick-darker`, `quick-more-optimistic`, `quick-more-extreme`.
Reused: `result-view`, `generate-button`, `slider-darkness-input`, `progress-view`, `run-error-message`,
`recent-futures-button`, `recent-future-<runId>`, `recent-future-settings-<runId>`, `meta-darkness`,
`chatgpt-status`, `chatgpt-connect`.

## Slice 17_alternative-future — FR-30 test contract

Delivers `startAlternativeRun`, ALTERNATIVE runs that reuse the parent's Evidence Pack (stages 1–6 skipped, no
search), the `futures-to-avoid` prompt block, the distinctness check with one ALTERNATIVE_DISTINCT regeneration and
the failure ALTERNATIVE_NOT_DISTINCT, and the ALTERNATIVE FUTURE button. No Flyway migration (`kind`,
`parent_run_id`, `evidence_pack_id` exist since V3/V6; reason ALTERNATIVE_DISTINCT exists in V7's varchar), no new
`ApiError.code` (`RUN_NOT_COMPLETED` is in the contract since step 2), no change to STANDARD runs. Where this section
is more precise than "Behaviour" FR-30, "UI" or scenario-reasoning.md "Alternative runs", this section wins.

### API — `POST /api/runs/{runId}/alternatives` (`startAlternativeRun`, no request body; a body is ignored)
Checks in this order; every error body has exactly the keys `code`, `message`; "no row" = `select count(*) from
generation_run` unchanged:
| # | Situation | Status | Body |
|---|---|---|---|
| 1 | connected; parent P = own run, status COMPLETED, `final_attempt` set, `evidence_pack_id` set; no active run | 202 | GenerationRun: new `id` ≠ P; `generationId` new (`^ORC-\d{4}-\d{2}-\d{2}-\d{4}(-\d+)?$`, rules of FR-10); `kind` `ALTERNATIVE`; `parentRunId` = P; `status` `QUEUED`; `stageIndex` 0; `stageCount` 10; `stage`, `stageLabel`, `failure`, `suggestedRealism`, `headline`, `completedAt` absent; `configuration` deep-equals P's `configuration`; `evidencePackId` = P's; `counts` deep-equals P's `counts`; `hasOpenCriticIssues` false; `createdAt` = `updatedAt`. Built from the committed QUEUED row. |
| 2 | P is itself ALTERNATIVE and COMPLETED (alternative of an alternative) | 202 | as #1 with `parentRunId` = P, `evidencePackId` = P's (= the root STANDARD run's pack) |
| 3 | `runId` = `00000000-0000-0000-0000-000000000000`, malformed `abc`, or a run of another session (also without cookie) | 404 | `{"code":"RUN_NOT_FOUND","message":"Future not found"}`; no row (404 wins over 401: a fresh, unconnected session asking for a foreign run gets 404) |
| 4 | own run P not connected (fresh session never connected cannot own runs; use: P COMPLETED, then `DELETE /api/auth/chatgpt/connection`) | 401 | `CHATGPT_NOT_CONNECTED` "Connect ChatGPT to generate"; no row |
| 5 | as #1 but access token within refresh-skew and the stub token server answers the refresh 400 (setup as `StartRunIT` #10) | 401 | `CHATGPT_SESSION_EXPIRED` "ChatGPT session expired — please reconnect"; no row |
| 6 | session flagged PLAN_NOT_ELIGIBLE (setup as `StartRunIT` #11) with an own COMPLETED P | 403 | `CHATGPT_PLAN_NOT_ELIGIBLE` "Your ChatGPT plan is not eligible for ORACUL"; no row |
| 7 | own P with status FAILED, INSUFFICIENT_EVIDENCE, QUEUED or RUNNING (parameterized), or COMPLETED without `final_attempt` / without `evidence_pack_id` (row inserted with `jdbc` as `RecentRunsIT.seed`) | 409 | `{"code":"RUN_NOT_COMPLETED","message":"Only a completed future can have an alternative"}`; no row (an active P answers RUN_NOT_COMPLETED, not RUN_ALREADY_ACTIVE) |
| 8 | own COMPLETED P (#1) and another run of the session is QUEUED (inserted with `jdbc`, deadline + 1 day) | 409 | `{"code":"RUN_ALREADY_ACTIVE","message":"A generation is already running"}`; no row |
| 9 | two `startAlternativeRun` for the same P sent concurrently (no active run) | one 202 + one 409 | the 409 is `RUN_ALREADY_ACTIVE`; exactly one active row for the session (unique-violation of `one_active_run_per_session` mapped as in FR-10) |
| 10 | two alternatives in sequence for the same P (the first awaited to terminal) | 202 ×2 | both with `parentRunId` = P and P's `evidencePackId` |
Stored row after #1 (DB assertions allowed): `kind` ALTERNATIVE, `parent_run_id` P, `session_id` caller's,
`configuration`, `research_profile`, `search_plan`, `counts` jsonb deep-equal P's, `evidence_pack_id` = P's,
`deadline_at` = `created_at` + `oracul.run.timeout`. P's row is unchanged (status, headline, configuration,
counts, final_attempt, updated_at, completed_at).

### Backend (`com.oracul.app.runs`, `com.oracul.app.reasoning`, `com.oracul.app.research`, `com.oracul.app.result`)
- `RunsController.startAlternativeRun(runId)`: `RunService.get`-style lookup filtered by the caller's session (404) →
  `ChatGptAuthService.requireUsableCredentials` (401/403) → `RunService.startAlternative(sessionId, parentRow)`.
- `RunService.startAlternative`: in one transaction: P still COMPLETED with `final_attempt` and `evidence_pack_id`
  (else 409 RUN_NOT_COMPLETED) → no active run (else 409 RUN_ALREADY_ACTIVE) → insert the QUEUED row (columns as
  "Stored row" above; `GenerationRunRepository.insertAlternativeQueued(...)`); `generation_id` collision handling and
  the unique-violation mapping exactly as `start`. Then `PipelineExecutor.submit`.
- `PipelineExecutor`: for `kind` ALTERNATIVE the task starts with the conditional transition `QUEUED → RUNNING,
  stage EXPLORING_FUTURES` (`WHERE status = 'QUEUED' AND deadline_at > now`; no row → `failTimedOut` as for
  STANDARD), builds no Research Profile, calls no `ResearchPipeline` (no sufficiency check, no news, metadata or
  EVENT_* request), then `ReasoningPipeline.run` and on ACCEPTED `StoryWriter.run` — the same catch-all and guards as
  STANDARD. STANDARD runs are unchanged.
- Evidence Pack lookup: new `EvidencePackRepository.findById(UUID packId)` (pack + its sources, sources read by the
  pack's own `evidence_pack.run_id`). `ReasoningPipeline`, `StoryWriter`, `FutureResultService` and
  `ResearchController.getEvidencePack` load the pack with `findById(row.evidencePackId())` after the session-filtered
  run lookup. For an ALTERNATIVE run: `getEvidencePack` deep-equals the parent's; `getRunResearch` returns the copied
  `researchProfile`, `searchPlan` and the run's `counts` (`runId` = the alternative); `listRunSources` /
  `listRunEvents` return the lists of the pack's owner run (`evidence_pack.run_id`); `getFutureResult` /
  `getStructuredScenario` use the alternative's own attempts and story with the shared pack (FR-26/27/28 views work).
- Futures to avoid (`AvoidedFutures.load(runId)` in `com.oracul.app.reasoning`, read once at the start of stage 7 of
  an ALTERNATIVE run): runs of the same session with `evidence_pack_id` = the run's pack, `status` COMPLETED,
  `final_attempt` set, `created_at` < the run's `created_at`, `id` ≠ the run. Order: the parent first (Future 1),
  then the others by `created_at` ascending, `id` ascending; at most 10 (the parent + the 9 newest others, still
  listed oldest first). Each = `record AvoidedFuture(String title, List<String> steps)` from that run's accepted
  attempt's **cleaned** scenario: `futureEvent.title` and `causalChain[].statement` in `order`.
- Distinctness (pure `AlternativeDistinctness.findings(StructuredScenario candidate, List<AvoidedFuture> avoided)` →
  `List<String>`, `avoided.get(0)` = parent): `normalize(s)` = trim, whitespace runs → one space, `toLowerCase
  (Locale.ROOT)`. For each avoided future i (1-based) with `normalize(title)` = `normalize(candidate.futureEvent.title)`
  → `same future event title as future <i>` (in i order); then, when every `normalize(step.statement)` of the
  candidate chain occurs among the normalized statements of future 1 → `same causal steps as future 1`. Distinct ⇔
  empty list.
- `ReasoningPipeline` for ALTERNATIVE runs (slice 10 steps unchanged except): every SCENARIO_GENERATION request
  carries the `Alternative` context below; in step 2, before **accept `a`** (2c critic PASS or 2e open critic issues),
  `findings` on `a`'s cleaned scenario:
  - empty → accept `a` (unchanged step 3; story follows).
  - non-empty and ALTERNATIVE_DISTINCT not yet used (`D`) → ALTERNATIVE_DISTINCT request with `duplicateFindings` =
    findings and `rejectedTitle` = `a`'s `futureEvent.title` (no critique, no guard violations) → parse; invalid and
    not `S` → one SCHEMA_CORRECTION repeating it; still invalid → FAILED `INVALID_SCENARIO`. Guard the new attempt
    with `finalAttempt = G`, store; it becomes `a`; continue at 2a (critic runs on it; a critic FAIL with `K` unused
    leads to CRITIC_REGENERATION as in slice 10).
  - non-empty and `D` → commit `status=FAILED`, `failure={ALTERNATIVE_NOT_DISTINCT, "ORACUL could not find a
    different future — try changing a setting"}`, `completedAt`; stage stays CONSTRUCTING_SCENARIO / 9;
    `final_attempt` null; no STORY_WRITING request; slot released.
  The check never runs for STANDARD runs. Bounds: ≤ 5 SCENARIO_GENERATION requests (INITIAL, SCHEMA_CORRECTION,
  GUARD_REGENERATION, CRITIC_REGENERATION, ALTERNATIVE_DISTINCT; attempts 1..5 in call order), ≤ 3 critiqued
  attempts.
- Run failures of an ALTERNATIVE run (transport, INVALID_SCENARIO, SCENARIO_REJECTED, RUN_TIMEOUT, startup sweep)
  behave exactly as for STANDARD runs at stages 7–10.

### Prompt — SCENARIO_GENERATION of an ALTERNATIVE run (`ScenarioGenerationPrompt`)
- New overloads `inputText(EvidencePack pack, GenerationRequest req, Alternative alt)` and `body(String model,
  EvidencePack pack, GenerationRequest req, Alternative alt)`; `record Alternative(List<AvoidedFuture>
  futuresToAvoid, String rejectedTitle, List<String> duplicateFindings)` with `Alternative.NONE` (empty lists, null
  title). The existing 2-arg `inputText` / 3-arg `body` delegate with `NONE` and stay byte-identical for every
  STANDARD request. `GenerationRequest` keeps exactly its 5 components. `instructions` stays
  `ScenarioGenerationPrompt.INSTRUCTIONS`; SETTINGS unchanged (`Attempt: <n> | Reason: <R>`, the first attempt of an
  alternative is `INITIAL`).
- With non-empty `futuresToAvoid`: TASK gains, directly after the two base lines, `Follow a different causal path than
  every future listed in futures-to-avoid; do not paraphrase them.` and, after `custom-wildcards`, the block:
  ```
  <<<ORACUL_UNTRUSTED_DATA name="futures-to-avoid">>>
  Future 1: <title>
  - <step 1 statement>
  - <step 2 statement>
  Future 2: <title>
  - …
  <<<END_ORACUL_UNTRUSTED_DATA>>>
  ```
  Every title / statement is sanitized with the slice 06 data-line rule, then cut to 300 code points + `…` when
  longer; at most 12 step lines per future, a 13th+ step is replaced by one line `- … and <k> more steps`.
- With reason ALTERNATIVE_DISTINCT (or non-empty `duplicateFindings`): TASK gains, after the futures-to-avoid line,
  `Your previous scenario repeated a future listed in futures-to-avoid. Return a new complete scenario with a different
  future event and at least one different causal step; the problems are listed in duplicate-future.` and, after
  `futures-to-avoid`, the block `<<<ORACUL_UNTRUSTED_DATA name="duplicate-future">>>` with the line
  `Rejected future: <rejectedTitle>` then one line per finding in order, then `<<<END_ORACUL_UNTRUSTED_DATA>>>`
  (same sanitizing and cut). A SCHEMA_CORRECTION of an ALTERNATIVE_DISTINCT repeats this line and block.
  GUARD_REGENERATION and CRITIC_REGENERATION of an alternative carry `futures-to-avoid` but never `duplicate-future`.
- Fixed order of extra TASK lines: alternative, duplicate, critique, guard, schema. Fixed order of blocks:
  `evidence-pack`, `custom-wildcards`, `futures-to-avoid`, `duplicate-future`, `critique`, `guard-violations`,
  `schema-errors`. Each block appears at most once (one start and one end marker each).
- SCENARIO_CRITIC and STORY_WRITING requests of an ALTERNATIVE run are built exactly as for STANDARD runs.
- Example (SC-DEFAULT parent on pack V4, `e1` = E001, INITIAL): `futures-to-avoid` content =
  `Future 1: Stub future A\n- Stub fact citing E001.\n- Stub inference.\n- Stub speculation.\n- Stub future event.`

### Backend test stubs (test support)
- **SC-ALT(k)** = SC-DEFAULT (same ids, D, Y) with `candidateFutures[0].title` and `futureEvent.title` = `Stub
  alternative future <k>`, `candidateFutures[1].title` `Stub alternative future <k> B`, speculation P1 and chain step
  3 statement `Stub alternative speculation <k>.`, chain step 4 statement and `futureEvent.summary` `Stub alternative
  future event <k>.`; k = number of lines starting `Future ` in the request's `futures-to-avoid` block.
- `StubResponses` default responder: a SCENARIO_GENERATION request containing a `futures-to-avoid` block → SC-ALT(k);
  otherwise unchanged (SC-DEFAULT). Helper `StubResponses.alternativeFixture(inputText)`.

### Backend tests
- Unit `backend/src/test/java/com/oracul/app/reasoning/AlternativeDistinctnessTest.java` (`// @trace FR-30`),
  parameterized; parent chain `[Stub fact citing E001., Stub inference., Stub speculation., Stub future event.]`
  title `Stub future A`:
  | # | candidate | avoided | findings |
  |---|---|---|---|
  | D1 | SC-ALT(1) | [parent] | `[]` |
  | D2 | title `Stub future A`, steps of SC-ALT(1) | [parent] | `[same future event title as future 1]` |
  | D3 | title `  stub   FUTURE a ` | [parent] | title finding |
  | D4 | title `Stub future A2`, parent's steps | [parent] | `[same causal steps as future 1]` |
  | D5 | SC-DEFAULT (identical) | [parent] | `[same future event title as future 1, same causal steps as future 1]` |
  | D6 | parent's steps reordered / only steps 1, 2, 4 / `  STUB inference. ` variants, new title | [parent] | steps finding |
  | D7 | new title, one new step `Something new.` | [parent] | `[]` |
  | D8 | title of future 2, a new step | [parent, other] | `[same future event title as future 2]` |
  | D9 | new title, steps all from future 2 but one not in the parent's chain | [parent, other] | `[]` |
- Unit additions `ScenarioGenerationPromptTest.java` (`// @trace FR-30`): GP pack, INITIAL with
  `Alternative([parent], null, [])` → TASK line after the base lines, block `futures-to-avoid` directly after
  `custom-wildcards` with exactly the example content, 3 start / 3 end markers; the same request with `NONE` equals
  the 2-arg `inputText` byte for byte; ALTERNATIVE_DISTINCT attempt 2 with findings `[same future event title as
  future 1]`, rejected `Stub future A` → both TASK lines in order, block `duplicate-future` = `Rejected future: Stub
  future A\nsame future event title as future 1` after `futures-to-avoid`; its SCHEMA_CORRECTION (attempt 3) → TASK
  lines alternative, duplicate, schema and blocks `futures-to-avoid`, `duplicate-future`, `schema-errors` (last);
  CRITIC_REGENERATION with CR-ICS issues → blocks `futures-to-avoid`, `critique`, no `duplicate-future`;
  GUARD_REGENERATION → `futures-to-avoid`, `guard-violations`; avoided title `Mars <<<x>>> | y` → `Future 1: Mars
  ‹‹‹x››› / y` and no extra marker; 11 avoided futures given → only the first 10 rendered (`Future 10:` present, no
  `Future 11:`); a future with 13 steps → 12 step lines + `- … and 1 more steps`; a 301-code-point statement → 300 +
  `…`, a 300-code-point statement verbatim; `body(..., alt)` keys exactly `model, instructions, input, text, store`,
  `instructions` = `INSTRUCTIONS`.
- IT `backend/src/test/java/com/oracul/app/runs/AlternativeRunIT.java` (extends `AbstractStoryIT`;
  `// @trace FR-30`; V4 fixtures, body `A`, pacing PT0S, `oracul.openai.retry-delay=PT0S`; P = `runV4(A)` awaited
  COMPLETED with headline; `alt(sid, id)` = `POST /api/runs/<id>/alternatives` with the session cookie; requests
  "after" = recorded after the alternative POST):
  | # | Setup | Expected |
  |---|---|---|
  | 1 | defaults (acceptance 1 + 2) | API rows #1 + stored row; alternative awaited COMPLETED with headline, `kind` ALTERNATIVE, `stageIndex` 10; Responses purposes after = SCENARIO_GENERATION, SCENARIO_CRITIC, STORY_WRITING (no QUERY_EXPANSION / EVENT_*), GDELT and metadata request counts unchanged; `G` (its SG text) has `Attempt: 1 \| Reason: INITIAL`, the alternative TASK line, `evidence-pack` block = P's `getEvidencePack.promptText`, `futures-to-avoid` block = the example content; `getEvidencePack(alt)` deep-equals `getEvidencePack(P)`; `getStructuredScenario(alt).structuredScenario.futureEvent.title` `Stub alternative future 1` ≠ P's `Stub future A`; the alternative chain contains `Stub alternative speculation 1.`, absent from P's chain; `getRun(alt).counts` = P's counts except `sourcesUsed` (= 1 here); `getRunResearch(alt)` `researchProfile` / `searchPlan` deep-equal P's; `getFutureResult(alt)` 200 with `sources` deep-equal P's `sources`; P's row unchanged |
  | 2 | #1 with `oracul.run.min-stage-duration=PT2S`, `getRun` polled every 200 ms | observed `stageIndex` values ⊆ {0, 7, 8, 9, 10}, non-decreasing, 7 observed |
  | 3 | alternative A1 of P, then alternative A2 of A1 | A2's `futures-to-avoid`: `Future 1: Stub alternative future 1` (parent A1) then `Future 2: Stub future A`; A2 COMPLETED with title `Stub alternative future 2`; A2 `evidencePackId` = P's |
  | 4 | SG answers for the alternative: 1 → SC-DEFAULT (repeat), then default (acceptance 2, regenerate once) | COMPLETED; SG reasons `[INITIAL, ALTERNATIVE_DISTINCT]`; `G(2)` has the duplicate TASK line and `duplicate-future` = `Rejected future: Stub future A\nsame future event title as future 1\nsame causal steps as future 1`, and still `futures-to-avoid`; 2 SCENARIO_CRITIC requests (attempts 1, 2); accepted attempt 2 |
  | 5 | SG for the alternative always SC-DEFAULT | FAILED `{"code":"ALTERNATIVE_NOT_DISTINCT","message":"ORACUL could not find a different future — try changing a setting"}`, `stage` CONSTRUCTING_SCENARIO, `stageIndex` 9, no headline, `completedAt` set; 2 SG requests; 0 STORY_WRITING after; `final_attempt` null; `getFutureResult` 409 `RESULT_NOT_READY`; slot released (`startRun` → 202) |
  | 6 | SG for the alternative: 1 → SC-DEFAULT with title `  stub FUTURE a ` and SC-ALT(1) steps, then default | as #4 with findings `[same future event title as future 1]` only |
  | 7 | SG for the alternative: 1 → SC-ALT(1) with title `Stub alternative future 1` but P's 4 step statements, then default | as #4 with findings `[same causal steps as future 1]` only |
  | 8 | SG for the alternative: 1 → `not json`, 2 → SC-DEFAULT, 3 → `not json` | FAILED `INVALID_SCENARIO`, `stageIndex` 9; reasons `[INITIAL, SCHEMA_CORRECTION, ALTERNATIVE_DISTINCT]` (no second schema correction) |
  | 9 | alternative's first SG answers 429 | FAILED `CHATGPT_RATE_LIMITED`, `stage` EXPLORING_FUTURES, `stageIndex` 7; P unchanged |
  | 10 | API rows #3–#10 | as the table |
  | 11 | after #1: `GET /api/runs` (same cookie) | contains the alternative (first, headline set) and P |
  | 12 | a STANDARD run after the alternative (`startRun(A)`) | its SG text has no `futures-to-avoid` / `duplicate-future` block and no alternative TASK line; its research runs (QUERY_EXPANSION present) |

### Frontend (`src/app/runs/`)
- `RunStore.startAlternative(parentRunId: string): void`: ignored while `starting()`; sets `starting` true;
  `RunsService.startAlternativeRun({ runId: parentRunId })` (`POST /api/runs/<parentRunId>/alternatives`, no body).
  202 → exactly the `start` success flow (reset, `run` = response, polling 1000 ms, `router.navigate(['/futures',
  id], { replaceUrl: true })`) plus `ScenarioStore.load(run.configuration)` (= the parent's configuration). Error →
  the `start` error flow (snackbar `run-error-message` with `ApiError.message` or "Something went wrong — try
  again"; `ConnectionStore.load()` on the 3 connection codes); URL, `result-view` and panel unchanged.
- `QuickActions` (`app-quick-actions`) gains a 5th `mat-stroked-button` last in `quick-actions`:
  `data-testid="quick-alternative"`, text exactly `ALTERNATIVE FUTURE`. Disabled (native `disabled`) iff
  `!ConnectionStore.canGenerate()` or `RunStore.starting()` or `RunStore.active()` or `RunStore.run()?.status !==
  'COMPLETED'`. Click on an enabled button → `RunStore.startAlternative(RunStore.run()!.id)`; no `ScenarioStore`
  change before the response, no `POST /api/runs`. The 4 slice-16 buttons are unchanged.

### Frontend unit tests (Vitest; `// @trace FR-30`)
- `src/app/runs/quick-actions.spec.ts`: the slice-16 rendering test now expects 5 buttons
  `[quick-more-realistic, quick-darker, quick-more-optimistic, quick-more-extreme, quick-alternative]` with texts
  `[MORE REALISTIC, DARKER, MORE OPTIMISTIC, MORE EXTREME, ALTERNATIVE FUTURE]` (its `// @trace FR-29` stays, add
  FR-30). New: enabled for a COMPLETED run with `canGenerate`; disabled when `canGenerate` false / `starting()` /
  `active()` — click then → 0 requests; click → exactly 1 `POST /api/runs/<runId>/alternatives` with body `null` and 0
  `POST /api/runs`; while pending a second click → still 1 request; 202 (QUEUED ALTERNATIVE run, configuration = the
  parent's) → URL `/futures/<newId>`, `ScenarioStore.configuration()` deep-equals the parent's configuration even
  after a panel edit (darkness 3) made before the click; 409 `RUN_NOT_COMPLETED` → `run-error-message` "Only a
  completed future can have an alternative", URL unchanged, button enabled again; 409 `RUN_ALREADY_ACTIVE` → "A
  generation is already running"; 401 `CHATGPT_NOT_CONNECTED` → message + `ConnectionStore.load()`; status 0 →
  "Something went wrong — try again".
- `src/app/runs/run.store.spec.ts` (new `describe('slice 17_alternative-future')`): `startAlternative('P')` → one
  POST to `/api/runs/P/alternatives`; 202 → `run()` = response, polls `GET /api/runs/<newId>` after 1000 ms; a call
  while `starting()` sends nothing.

### E2E (`e2e/tests/alternative-future.spec.ts`; `// @trace FR-30`; serial; `resetStub` (also `/__control/scenario` `ok`) and `connect` as `quick-regeneration.spec.ts`; fresh context per test; `test.setTimeout(120_000)`)
E2E stub (`e2e/stubs/server.mjs`): SCENARIO_GENERATION requests containing `name="futures-to-avoid"` answer SC-ALT(k)
(as the backend stub, built from `scenarioDefault`); `/__control/scenario` gains modes `alt-repeat-once` (first
alternative SG request → SC-DEFAULT, later ones SC-ALT(k)) and `alt-repeat` (every alternative SG request →
SC-DEFAULT); STANDARD requests ignore these two modes (SC-DEFAULT); a separate counter `alternativeCalls`, reset by
`/__control/reset`.
1. Acceptance 1 + 2: connect; panel defaults (body `B`); `generate-button` → `result-view` (60 s), run 1 id from the
   URL; `quick-alternative` visible, text `ALTERNATIVE FUTURE`, enabled; remember the GDELT request count
   (`GET /__control/requests?kind=gdelt`); click with `page.waitForRequest` on `POST
   /api/runs/<run1>/alternatives`; URL becomes `/futures/<run2>` (≠ run 1); `slider-darkness-input` value `5`;
   `GET /api/runs/<run2>` → `kind` ALTERNATIVE, `parentRunId` run 1, `evidencePackId` = run 1's; wait for
   `result-view` (60 s); GDELT request count unchanged; the last SCENARIO_GENERATION request's input text contains
   `<<<ORACUL_UNTRUSTED_DATA name="futures-to-avoid">>>` and `Future 1: Stub future A`;
   `GET /api/runs/<run2>/structured-scenario` → `structuredScenario.futureEvent.title` `Stub alternative future 1`
   ≠ run 1's; some `causalChain[].statement` of run 2 is absent from run 1's chain. Evidence `FR-30
   alternative-result`. Open `recent-futures-button` → `recent-future-<run2>` and `recent-future-<run1>` present.
2. Not distinct: mode `alt-repeat`; generate, `result-view`; `quick-alternative` → `failure-view` with
   `failure-message` "ORACUL could not find a different future — try changing a setting" (60 s); `GET
   /api/runs/<run2>` `failure.code` ALTERNATIVE_NOT_DISTINCT. Evidence `FR-30 alternative-not-distinct`.
3. Mode `alt-repeat-once`: alternative reaches `result-view`; 2 SCENARIO_GENERATION requests after the click, the
   second contains `Reason: ALTERNATIVE_DISTINCT` and `name="duplicate-future"`.

### data-testid (this slice)
New in use: `quick-alternative`. Reused: `quick-actions`, `result-view`, `progress-view`, `failure-view`,
`failure-message`, `generate-button`, `run-error-message`, `slider-darkness-input`, `recent-futures-button`,
`recent-future-<runId>`, `chatgpt-status`, `chatgpt-connect`.
