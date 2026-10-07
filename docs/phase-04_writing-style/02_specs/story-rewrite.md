# Spec — Rewrite the same future; style and language shown with the result

Covers: FR-67, FR-68

Delta against phase-01 `generation-runs.md` FR-29 (quick regeneration, "Slice 16"), FR-30 (ALTERNATIVE FUTURE,
"Slice 17": `startAlternativeRun`, `AvoidedFutures`, `PipelineExecutor` kind handling, shared Evidence Pack lookup),
`future-result.md` FR-25 (metadata panel, `ScenarioMetadataMapper`), `recent-futures.md` FR-33 (Recent futures
entries), phase-02 `run-control.md` FR-45 / FR-47 (STOP, evidence note copied to ALTERNATIVE runs) and the current code
`RunsController`, `RunService`, `PipelineExecutor`, `StoryWriter`, `FutureResultService`, `HistoryController`, frontend
`src/app/runs/quick-actions.ts`, `run.store.ts`, `src/app/result/scenario-metadata.ts`,
`src/app/history/recent-futures.ts`. Style catalogue and prompt: `writing-styles.md`, `styled-story.md`.

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| `RunKind` STANDARD / ALTERNATIVE; `parentRunId` set iff ALTERNATIVE | **Extended** by FR-67: + REWRITE; `parentRunId` set iff ALTERNATIVE or REWRITE; new `scenarioRunId` (REWRITE only). |
| FR-30 slice 17: `quick-actions` has exactly 5 buttons, `quick-alternative` last | **Superseded** by FR-67: 6 buttons, `quick-rewrite` "REWRITE AS…" last. |
| FR-30: an ALTERNATIVE parent must be COMPLETED with `final_attempt` and an Evidence Pack | **Extended** by FR-68: a COMPLETED REWRITE parent with a story is eligible; its told scenario (that of its `scenarioRunId`) is Future 1. |
| FR-25: metadata shows Realism, Darkness, Optimism, Horizon, wildcards, counts | **Extended** by FR-68: + "Style: …", "Language: …" and, for a rewrite, "Rewritten from: …". |
| FR-33: a Recent futures entry shows time, headline and "R<n> D<n> O<n> · <horizon>" | **Extended** by FR-68: + "<style label> · <language label>". |
| FR-33 / API `listRecentRuns`: STANDARD and ALTERNATIVE runs | **Extended**: REWRITE runs are listed by the same rules. |

## Purpose
A completed future can be told again — in another style and/or language — without a new search and without a new
scenario: only the story-writing step runs, on the original run's Evidence Pack and validated scenario. Every result and
Recent futures entry says how it was told, and regenerating keeps the chosen telling.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| `generation_run` | `kind` | varchar(16) | + `REWRITE` |
| `generation_run` | `parent_run_id` | uuid null | REWRITE: the run the rewrite was started from |
| `generation_run` (Flyway, next free version, e.g. `V15__run_scenario_source.sql`) | `scenario_run_id` | uuid null, FK `generation_run(id)` | set iff REWRITE: the STANDARD or ALTERNATIVE run whose accepted (`final_attempt`) cleaned scenario is told; a rewrite of a rewrite copies its parent's value |
| REWRITE row | `configuration` | jsonb | parent's configuration with `output.writingStyle` and `output.language` replaced by the request's (every other key, incl. `output.story` / `illustration`, as stored in the parent) |
| REWRITE row | `research_profile`, `search_plan`, `counts`, `evidence_pack_id`, evidence-note columns (incl. `evidence_note_wildcards`), `suggested_realism` | | copied from the parent (as ALTERNATIVE) |
| REWRITE row | `final_attempt` | null | a rewrite has no scenario attempts; `scenario_attempt` and critic rows are never written for it |
| REWRITE row | `future_story` | own row | its own story (headline, dateline in its language, body) |
| API `GenerationRun` | `scenarioRunId` | uuid (optional) | present iff kind REWRITE |
| API `ScenarioMetadata` | `styleLabel`, `languageLabel`, `rewrittenFrom` `{runId, headline}` | | FR-68 |
| API `RecentRunSummary` | `styleLabel`, `languageLabel` | | FR-68 |

Label rules (backend, one helper used by result and history): `styleLabel` = label of the configuration's
`output.writingStyle.profileId` in the style file; configuration without `writingStyle` → `Official / Briefing (legacy)`;
profile no longer in the file → the profileId. `languageLabel` = `English` for `en` or absent, `Ukrainian` for `uk`.

## Behaviour

### FR-67 — Rewrite the same future in another style or language
- Happy path: on a COMPLETED result the `quick-actions` bar shows a sixth button `quick-rewrite` "REWRITE AS…"
  (`mat-stroked-button`, U+2026) that opens the `mat-menu` `rewrite-menu`: a non-interactive heading
  `rewrite-language-en` "English" followed by one `mat-menu-item` `rewrite-option-<profileId>-en` per profile of
  `getWritingStyles` in API order (text = profile label), then the heading `rewrite-language-uk` "Ukrainian" and the
  items `rewrite-option-<profileId>-uk` (18 items with the shipped file). Choosing an item sends
  `POST /api/runs/{runId}/rewrites` (`startRewriteRun`) with `{"writingStyle":{"mode":"<profile mode>","profileId":
  "<id>"},"language":"<en|uk>"}` → 202 `GenerationRun` (kind REWRITE, `parentRunId` = runId, `scenarioRunId`, status
  QUEUED, stageIndex 0, configuration = parent's with the new style and language, `evidencePackId` / `counts` /
  `evidenceNote` / `suggestedRealism` = parent's). The UI follows the new run like ALTERNATIVE FUTURE (URL
  `/futures/<newId>` with `replaceUrl`, `progress-view`, then its `result-view`) and loads the new run's configuration
  into the panel (`ScenarioStore.load`), so the panel shows the new style and language.
- Rules:
  - Pipeline of a REWRITE run (`PipelineExecutor`): conditional transition QUEUED → RUNNING with stage WRITING_STORY,
    stageIndex 10, "Writing from the future…" (`WHERE status = 'QUEUED' AND deadline_at > now`; else RUN_TIMEOUT as
    for STANDARD); model resolution as every run (phase-02 FR-38); no Research Profile, no news / article / Google
    request, no QUERY_GENERATION, EVENT_NORMALIZATION, EVENT_CLASSIFICATION, SCENARIO_GENERATION or SCENARIO_CRITIC
    request, no Evidence Guard; then `StoryWriter` with the pack `evidence_pack_id` and the accepted cleaned scenario of
    `scenario_run_id`, style and language from the rewrite's own configuration (`styled-story.md` FR-65). Same ≤ 2
    attempts, date fallback, validation, transport failures, deadline, `min-stage-duration` and STOP (FR-45) as stage 10
    of a STANDARD run. Success → `future_story` row of the rewrite, `headline`, COMPLETED, `completedAt`.
  - A REWRITE run is observed only at stageIndex 0 (QUEUED) and 10; it sends exactly the STORY_WRITING requests of its
    attempts (1, or 2 after an invalid first story) and nothing else to the Responses API.
  - The rewrite's attempt-1 STORY_WRITING input text equals the parent's attempt-1 STORY_WRITING input text (parent
    created in this phase, so its request has a WRITING STYLE section) except inside the WRITING STYLE section: the
    `structured-scenario` block is byte-identical, the SETTINGS lines (Attempt … Future event date), TASK lines and
    trailer are identical, and `instructions` is identical.
  - Rewrite of a rewrite: `scenarioRunId` = the parent's `scenarioRunId` (the original STANDARD / ALTERNATIVE run);
    its STORY_WRITING uses that run's scenario. A rewrite of an ALTERNATIVE run tells the alternative's own scenario.
  - Any style / language combination is accepted by the API, also the parent's own; the UI disables the menu item of
    the result's own effective combination (profile `output.writingStyle.profileId`, else `official-briefing`;
    language `output.language`, else `en`), so a legacy result disables `rewrite-option-official-briefing-en`.
  - `quick-rewrite` is disabled exactly when `quick-alternative` is (no connection, a start request in flight, an
    active run, or no run loaded); a disabled item or button sends nothing.
  - Reads of a REWRITE run: `getRun` (own row; `hasOpenCriticIssues` = that of `scenarioRunId`), `getRunResearch`
    (copied profile and plan, own counts), `listRunSources` / `listRunEvents` / `getEvidencePack` (the pack owner's, as
    ALTERNATIVE), `getStructuredScenario` (the record of `scenarioRunId` unchanged, its `runId`), `getFutureResult`
    (own story and configuration; `causalChain`, `sources[].usedInScenario`, `wildcardGroups[].sources[].usedInScenario`
    and `openCriticIssues` from the scenario of `scenarioRunId`).
  - The parent run (and the scenario run) are never modified, whatever the rewrite's outcome.
- Errors (`startRewriteRun`, in this order; nothing is stored; the UI shows `ApiError.message` in the snackbar
  `run-error-message` and stays on the parent's result):
  - unknown UUID / malformed id / run of another session (also without cookie) → 404 `RUN_NOT_FOUND` → "Future not found"
  - body not valid JSON → 400 `VALIDATION_FAILED` → "Request body is not valid JSON"
  - body `null` / not an object, `writingStyle` missing / `null` / not an object / unknown mode / unknown profileId /
    profile of the other mode → 400 `VALIDATION_FAILED` → "Unknown writing style"
  - `language` missing / `null` / not exactly `"en"` or `"uk"` → 400 `VALIDATION_FAILED` → "Unknown story language"
  - not connected → 401 `CHATGPT_NOT_CONNECTED` → "Connect ChatGPT to generate"; refresh failed → 401
    `CHATGPT_SESSION_EXPIRED` → "ChatGPT session expired — please reconnect"; refresh `invalid_client` → 401
    `CHATGPT_REGISTRATION_INVALID`; plan not eligible → 403 `CHATGPT_PLAN_NOT_ELIGIBLE` → "Your ChatGPT plan is not
    eligible for ORACUL"; transient refresh failure → 503 `CHATGPT_UNAVAILABLE` (messages of the contract)
  - parent QUEUED, RUNNING, FAILED, INSUFFICIENT_EVIDENCE, STOPPED, or COMPLETED without a story → 409
    `RUN_NOT_COMPLETED` → "Only a completed future can be rewritten"
  - another run of the session QUEUED / RUNNING (also two concurrent rewrite requests: one 202, one 409 via the
    `one_active_run_per_session` index) → 409 `RUN_ALREADY_ACTIVE` → "A generation is already running"
  - unexpected failure → 500 `INTERNAL_ERROR` → "Something went wrong — try again"
  - run failures of the rewrite (story invalid twice, 429, unavailable, expired, timeout, interrupted) → the rewrite
    ends FAILED with the existing code and message (`RunFailureCode`), the failure view shows it with "Try again"; the
    parent's status, headline, story and configuration are unchanged.
- Ranges & invariants: valid request classes = 9 profiles × 2 languages (18) → 202; invalid classes as FR-63 / FR-64
  (each tested on this endpoint too); invariants: a REWRITE run has `parentRunId` = request runId, `scenarioRunId` = a
  STANDARD or ALTERNATIVE run (never a REWRITE), `evidencePackId` = parent's = `scenarioRunId`'s, `counts` deep-equal
  the parent's, configuration equal to the parent's except `output.writingStyle` / `output.language`; for chains of
  length 1, 2 and 3 (rewrite of a rewrite of a rewrite) the `structured-scenario` block of every STORY_WRITING request
  is byte-identical to the original's; 0 non-STORY_WRITING Responses requests and 0 news requests per rewrite; stageIndex
  ∈ {0, 10}; a rejected request (any 4xx) creates no row; the menu has exactly one disabled item, the result's own
  combination.

### FR-68 — Style and language shown with the result
- Happy path: `scenario-metadata` shows, after `meta-horizon`, `meta-style` "Style: <styleLabel>" and `meta-language`
  "Language: <languageLabel>" (e.g. "Style: Narrative — inspired by Franz Kafka", "Language: Ukrainian"), and for a
  REWRITE run `meta-rewritten-from` "Rewritten from: <rewrittenFrom.headline>" (headline of its `parentRunId`). A run
  whose configuration has no `writingStyle` (created before this phase, or an API request without it) shows "Style:
  Official / Briefing (legacy)" and "Language: English". Recent futures entries show `recent-future-style-<runId>`
  "<styleLabel> · <languageLabel>" (e.g. "Narrative — inspired by Franz Kafka · Ukrainian"), for STOPPED entries too.
- Rules:
  - Values come from the API (`metadata.styleLabel`, `languageLabel`, `rewrittenFrom`; `RecentRunSummary.styleLabel`,
    `languageLabel`), never from the panel or the style catalogue in the browser; an element is rendered only when its
    field is present (`meta-rewritten-from` ⇔ `rewrittenFrom`).
  - ALTERNATIVE FUTURE copies the parent's configuration (FR-30), so the alternative keeps `output.writingStyle` and
    `output.language` (a Poe / Ukrainian parent → Poe / Ukrainian alternative, whose story is written in that style).
  - Quick regeneration (MORE REALISTIC, DARKER, MORE OPTIMISTIC, MORE EXTREME), Try again and LOWER REALISM send the
    panel configuration, which holds the opened run's configuration (FR-33 `RunStore.open`, or the configuration loaded
    after a rewrite), so they keep the style and language; only the action's control changes.
  - ALTERNATIVE FUTURE on a REWRITE run: eligible when the REWRITE run is COMPLETED with a story; the alternative gets
    `parentRunId` = the rewrite, the rewrite's configuration (its style and language) and pack; its futures to avoid
    list as Future 1 the scenario of the rewrite's `scenarioRunId`, then the other eligible runs on the pack as in FR-30
    without that run (REWRITE runs, which have no `final_attempt`, are never listed separately).
- Errors: none new — the metadata comes with `getFutureResult` (404 `RUN_NOT_FOUND` / 409 `RESULT_NOT_READY` as
  FR-23), the list with `listRecentRuns` (500 `INTERNAL_ERROR`; UI "Recent futures are unavailable"); ALTERNATIVE /
  quick regeneration errors unchanged (FR-29 / FR-30).
- Ranges & invariants: `styleLabel` classes — each of the 9 shipped profiles → its label; configuration without
  `writingStyle` → `Official / Briefing (legacy)`; a profileId missing from the file → the id; `languageLabel` classes
  `en` → English, `uk` → Ukrainian, absent → English; `meta-style` / `meta-language` texts are exactly `Style: ` /
  `Language: ` + the API value; for every real run `metadata.styleLabel` = the same run's `RecentRunSummary.styleLabel`;
  `rewrittenFrom` present ⇔ kind REWRITE, with `runId` = `parentRunId` and `headline` = the parent's `getRun.headline`;
  for each quick action and ALTERNATIVE FUTURE on a run with style S and language L (tested with Poe / uk) the new run's
  `output.writingStyle` and `output.language` deep-equal S and L; texts rendered by interpolation (never `innerHTML`).

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| POST | /api/runs/{runId}/rewrites | startRewriteRun | RewriteRequest `{writingStyle, language}` | 202 GenerationRun (kind REWRITE) · 400 VALIDATION_FAILED · 401 CHATGPT_NOT_CONNECTED / CHATGPT_SESSION_EXPIRED / CHATGPT_REGISTRATION_INVALID · 403 CHATGPT_PLAN_NOT_ELIGIBLE · 404 RUN_NOT_FOUND · 409 RUN_NOT_COMPLETED / RUN_ALREADY_ACTIVE · 503 CHATGPT_UNAVAILABLE · 500 INTERNAL_ERROR |
| POST | /api/runs/{runId}/alternatives | startAlternativeRun | — | unchanged statuses; REWRITE parents eligible |
| GET | /api/runs/{runId} | getRun | — | + `scenarioRunId` (REWRITE), kind REWRITE |
| GET | /api/runs/{runId}/structured-scenario | getStructuredScenario | — | REWRITE → the record of `scenarioRunId` |
| GET | /api/runs/{runId}/result | getFutureResult | — | + `metadata.styleLabel`, `languageLabel`, `rewrittenFrom` |
| GET | /api/runs | listRecentRuns | — | + `styleLabel`, `languageLabel` per item; REWRITE runs listed |

## UI
- Route: `/futures/:runId` · components: `QuickActionsComponent` (`src/app/runs/quick-actions.ts`, + REWRITE AS… menu),
  `ScenarioMetadataComponent` (`src/app/result/scenario-metadata.ts`), `RecentFuturesComponent`
  (`src/app/history/recent-futures.ts`), `RunStore` (+ `rewrite(runId, writingStyle, language)` following the new run as
  `alternative`) · Material: `mat-stroked-button`, `mat-menu`, `mat-menu-item`, `MatSnackBar`.
- States: menu closed / open; item enabled / disabled; rewrite starting (all quick buttons disabled) · progress ·
  result · failure (existing views).
- `data-testid`s: `quick-rewrite`, `rewrite-menu`, `rewrite-language-en`, `rewrite-language-uk`,
  `rewrite-option-<profileId>-<en|uk>`, `meta-style`, `meta-language`, `meta-rewritten-from`,
  `recent-future-style-<runId>`. Reused: `quick-actions`, `quick-alternative`, quick buttons of FR-29, `result-view`,
  `progress-view`, `run-error-message`, `failure-view`, `failure-message`, `try-again`, `scenario-metadata`,
  `recent-futures-button`, `recent-future-<runId>`.
