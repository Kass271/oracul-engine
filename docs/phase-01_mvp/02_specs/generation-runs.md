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
