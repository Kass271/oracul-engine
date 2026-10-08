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

#### Slice 04_story-rewrite — precise delta (step 4a)
Scope of the slice: FR-67 only. Labels (`meta-style`, `meta-language`, `meta-rewritten-from`,
`recent-future-style-*`, `ScenarioMetadata.styleLabel` / `languageLabel` / `rewrittenFrom`,
`RecentRunSummary.styleLabel` / `languageLabel`) and ALTERNATIVE FUTURE on a REWRITE parent are FR-68 = slice
05_style-labels: this slice's tests must not assert them. Until 05, `startAlternativeRun` on a REWRITE parent answers
the old 409 `RUN_NOT_COMPLETED` "Only a completed future can have an alternative" (no `final_attempt`) — not tested here.

Backend — endpoint (`POST /api/runs/{runId}/rewrites`, generated `com.oracul.app.api.RewriteApi#startRewriteRun(UUID,
RewriteRequest)`, implemented in package `com.oracul.app.runs`; tests address it over HTTP only):
- Every error body is exactly `{"code":…,"message":…}` (no other key). Check order with the combined cases the tests
  must cover (one case each):
  1. 404 `RUN_NOT_FOUND` "Future not found" — unknown UUID, malformed id (`abc`, `123`), run of another session, no
     `ORACUL_SID` cookie. 404 wins over every body problem: unknown UUID + body `{` → 404; unknown UUID + body `{}` → 404;
     malformed id + valid body → 404.
  2. 400 `VALIDATION_FAILED` "Request body is not valid JSON" — body bytes `{`, `not json`, `{"writingStyle":`, an empty
     body (0 bytes), `Content-Type: text/plain` with a valid JSON text.
  3. 400 `VALIDATION_FAILED` "Unknown writing style" — JSON `null`, `[]`, `"kafka"`, `1`, `true`, `{}`,
     `{"language":"uk"}`; `writingStyle` missing / `null`; every invalid `writingStyle` class of `writing-styles.md`
     FR-63 "Ranges & invariants" (not an object, `{}`, mode classes, profileId classes, cross-mode). Bad style + bad
     language → "Unknown writing style".
  4. 400 `VALIDATION_FAILED` "Unknown story language" — valid `writingStyle` and `language` missing, `null`, or any
     invalid language class of `writing-styles.md` FR-64 "Ranges & invariants" (`"EN"`, `"Uk"`, `"UK"`, `"ua"`,
     `"uk-UA"`, `"en-US"`, `"de"`, `""`, `" en"`, `"uk "`, `1`, `0`, `true`, `["uk"]`, `{}`, `{"code":"uk"}`). Unlike
     `startRun`, absent and `null` are invalid here (both fields are required in `RewriteRequest`).
  5. 401 / 403 / 503 — exactly the ChatGPT checks and bodies of `startAlternativeRun` (not connected → 401
     `CHATGPT_NOT_CONNECTED` "Connect ChatGPT to generate"; refresh failed → 401 `CHATGPT_SESSION_EXPIRED` "ChatGPT
     session expired — please reconnect"; refresh `invalid_client` → 401 `CHATGPT_REGISTRATION_INVALID`; plan not
     eligible → 403 `CHATGPT_PLAN_NOT_ELIGIBLE` "Your ChatGPT plan is not eligible for ORACUL"; transient refresh
     failure → 503 `CHATGPT_UNAVAILABLE`, messages as in `startAlternativeRun`). Any 400 class without a connection →
     400, not 401. Not connected + parent FAILED → 401, not 409.
  6. 409 `RUN_NOT_COMPLETED` "Only a completed future can be rewritten" — parent QUEUED, RUNNING, FAILED,
     INSUFFICIENT_EVIDENCE, STOPPED, or COMPLETED without a `future_story` row (one case each). A RUNNING parent → this
     409, not `RUN_ALREADY_ACTIVE`.
  7. 409 `RUN_ALREADY_ACTIVE` "A generation is already running" — COMPLETED parent while another run of the session
     is QUEUED or RUNNING; two concurrent valid requests on the same parent → exactly one 202 and one 409 (unique index
     `one_active_run_per_session`).
  8. 500 `INTERNAL_ERROR` "Something went wrong — try again" — an unexpected exception inside the start (e.g. the
     insert throws); body contains no exception text, class name or URL.
- Nothing is stored on any 4xx / 500: the session's `generation_run` row count and the parent row (every column) are
  unchanged, and no pipeline is submitted (the stub records no request).
- Unknown top-level keys are ignored; unknown keys inside `writingStyle` are ignored and not stored (stored
  `writingStyle` is exactly `{mode, profileId}`). The parent's own combination is accepted (202).
- 202 body (`GenerationRun`): `id` new UUID; `generationId` new (same format as every run); `kind` `REWRITE`;
  `parentRunId` = path runId; `scenarioRunId` = the parent's id when the parent is STANDARD or ALTERNATIVE, the
  parent's `scenarioRunId` when the parent is REWRITE; `status` `QUEUED`; `stageIndex` 0; `configuration` = the
  parent's stored configuration with `output.writingStyle` = `{mode, profileId}` of the request and `output.language`
  = the request's (every other key, incl. `output.story` / `output.illustration`, deep-equal to the parent's; a parent
  without the two keys gets them added); `evidencePackId`, `counts`, `evidenceNote` (absent when the parent has none),
  `suggestedRealism` deep-equal to the parent's; `hasOpenCriticIssues` = that of `scenarioRunId`; no `headline`,
  `failure`, `completedAt`. `getRun(newId)` right after the 202 returns the same values.
- `scenarioRunId` appears in `GenerationRun` only for REWRITE runs (absent — not `null` — for STANDARD and
  ALTERNATIVE, so earlier responses are unchanged).

Backend — persistence: `backend/src/main/resources/db/migration/V15__run_scenario_source.sql` =
`ALTER TABLE generation_run ADD COLUMN scenario_run_id UUID NULL REFERENCES generation_run (id);`. The REWRITE row
copies `research_profile`, `search_plan`, `counts`, `evidence_pack_id`, `suggested_realism`, `evidence_note_kind`,
`evidence_core_items`, `evidence_core_needed`, `evidence_note_wildcards` from the parent (as `insertAlternativeQueued`),
`final_attempt` null, `deadline_at` = now + the run timeout. DB invariant over all rows: `scenario_run_id is not null`
⇔ `kind = 'REWRITE'`, and the referenced row's kind is never `REWRITE`.

Backend — pipeline of a REWRITE run (`PipelineExecutor` decides by kind explicitly: STANDARD → research path,
ALTERNATIVE → reasoning path, REWRITE → story only; a kind it does not know never falls into the research path):
1. Conditional QUEUED → RUNNING, stage `WRITING_STORY` (stageIndex 10, label "Writing from the future…"), only while
   `deadline_at > now`; otherwise FAILED `RUN_TIMEOUT` "Generation took too long — try again".
2. Model resolution as every run (FR-38; failures → the existing codes).
3. `StoryWriter` with the pack `evidence_pack_id` and the cleaned scenario of `scenario_run_id`'s `final_attempt`,
   style and language from the REWRITE row's own configuration (`styled-story.md` FR-65 / FR-66): ≤ 2 attempts, date
   fallback, validation, transport failures, deadline, `oracul.run.min-stage-duration`, STOP (FR-45) exactly as stage
   10 of a STANDARD run. Success → `future_story` row of the rewrite, `headline`, COMPLETED, `completedAt`.
- Rows of the rewrite: `model_call` only with purpose `STORY_WRITING` (attempt 1, or 1 and 2); no `scenario_attempt`,
  `source`, `event` or `evidence_pack` row with its id; the parent's and the scenario run's rows (`generation_run`,
  `future_story`, `scenario_attempt`, `model_call`) are byte-identical before and after the rewrite, whatever its
  outcome.
- Failures (each its own case): story invalid twice (stub story mode `invalid`) → FAILED `INVALID_SCENARIO` "ORACUL
  could not construct a valid scenario"; 429 (`rate-limited`) → FAILED `CHATGPT_RATE_LIMITED` "ChatGPT usage limit
  reached — try again later"; deadline → `RUN_TIMEOUT`; startup sweep of a QUEUED / RUNNING REWRITE → `RUN_INTERRUPTED`;
  STOP while QUEUED or RUNNING → STOPPED (FR-45). Story mode `invalid-once` → COMPLETED with exactly 2 STORY_WRITING
  calls; `bad-date` → COMPLETED via the date fallback.
- Try again on a FAILED rewrite is the existing FR-32 behaviour (a `startRun` with the failed run's stored
  configuration, i.e. a full STANDARD run in the rewrite's style and language) — unchanged, not a rewrite.

Backend — reads of a REWRITE run:
- `getStructuredScenario(rewriteId)` → 200 at every status of the rewrite, body deep-equal to
  `getStructuredScenario(scenarioRunId)` (its `runId` = `scenarioRunId`).
- `getFutureResult(rewriteId)`: not COMPLETED → 409 `RESULT_NOT_READY` (as FR-23); COMPLETED → `story` = the
  rewrite's own `future_story`; `causalChain`, `sources[].usedInScenario`, `wildcardGroups[].sources[].usedInScenario`
  and `openCriticIssues` deep-equal to those of `getFutureResult(scenarioRunId)`; `configuration` = the rewrite's.
- `getRun(rewriteId).hasOpenCriticIssues` = `getRun(scenarioRunId).hasOpenCriticIssues`.
- `getRunResearch`, `listRunSources`, `listRunEvents`, `getEvidencePack` of the rewrite deep-equal those of its parent
  (the pack owner's data, as for ALTERNATIVE).
- `listRecentRuns` lists a COMPLETED / STOPPED REWRITE like any run (existing query, no kind filter);
  `getScenarioConfiguration` returns the newest run's configuration, so after a rewrite it is the rewrite's.

Frontend (`src/app/runs/`, route `/futures/:runId`, no new route):
- `QuickActions` (`quick-actions.ts`): after `quick-alternative` a sixth `button mat-stroked-button`
  `data-testid="quick-rewrite"`, text exactly `REWRITE AS…` (U+2026), `[matMenuTriggerFor]` the rewrite menu;
  `disabled` exactly when `quick-alternative` is (`!connection.canGenerate() || runs.starting() || runs.active() ||
  !runs.run()`). Bar order of `button`s: `quick-more-realistic`, `quick-darker`, `quick-more-optimistic`,
  `quick-more-extreme`, `quick-alternative`, `quick-rewrite`.
- `rewrite-menu`: an element inside the `mat-menu` content (so it exists in the overlay container under
  `document.body`, not inside the component's host, and only while the menu is open) that holds, in DOM order: a
  `div` `rewrite-language-en` (text = `languages[0].label`, `English`), then one `button mat-menu-item`
  `rewrite-option-<profileId>-en` per entry of `ScenarioStore.writingStyles().profiles` in API order (text = profile
  `label`), then `div` `rewrite-language-uk` (`Ukrainian`), then the same items with suffix `-uk`. Shipped file: 2
  headings + 18 items; order `official-briefing`, `neutral-narrative`, `poe`, `kafka`, `wells`, `verne`, `orwell`,
  `clarke`, `harrison`. Headings are not `mat-menu-item`s, not buttons and do nothing on click. The catalogue comes
  from `ScenarioStore.writingStyles()` (loaded with the panel, slice 02) — opening the menu sends no request; with no
  catalogue loaded the menu holds only the two headings. All texts by interpolation.
- Own combination: the item for profile `run().configuration.output.writingStyle?.profileId ?? 'official-briefing'`
  and language `run().configuration.output.language ?? 'en'` has the `disabled` attribute; every other item is
  enabled. It comes from `RunStore.run()` (the shown result), never from the panel. A legacy run (no
  `writingStyle` / `language`) disables `rewrite-option-official-briefing-en`; a profile id not in the catalogue
  disables none.
- Choosing an enabled item calls `RunStore.rewrite(parentRunId: string, writingStyle: WritingStyleSelection, language:
  StoryLanguage)` with the shown run's id, `{mode: <profile's mode>, profileId: <id>}` and the item's language. It
  sends exactly one `POST /api/runs/<runId>/rewrites` through the generated `RewriteService.startRewriteRun` with body
  deep-equal `{"writingStyle":{"mode":…,"profileId":…},"language":…}`, and no `POST /api/runs` / `…/alternatives`.
  `starting()` is true until the answer, so every quick button and `quick-rewrite` are disabled and a second choice
  sends nothing. A disabled item or a disabled `quick-rewrite` sends nothing.
- 202: as `startAlternative` — `run` = the new run, `panel.load(run.configuration)` (panel shows the new style and
  language: `writing-style-option-*`, `narrative-style-*`, `story-language-select`), polling starts, router navigates
  to `/futures/<newId>` with `replaceUrl: true`; `progress-view`, then the new run's `result-view`.
- Error answer: snackbar `run-error-message` with `ApiError.message` (a non-`ApiError` answer → "Something went
  wrong — try again"); the URL, the shown `result-view` and the panel stay unchanged; buttons enabled again; a
  connection code (`CHATGPT_NOT_CONNECTED`, `CHATGPT_SESSION_EXPIRED`, `CHATGPT_PLAN_NOT_ELIGIBLE`,
  `CHATGPT_REGISTRATION_INVALID`) reloads the connection status (as `startAlternative`). Unit cases: 409
  `RUN_NOT_COMPLETED`, 409 `RUN_ALREADY_ACTIVE`, 401 `CHATGPT_NOT_CONNECTED`, 400 `VALIDATION_FAILED`, 500
  `INTERNAL_ERROR`, a network error (status 0).

E2E (new spec, e.g. `e2e/tests/story-rewrite.spec.ts`, desktop, stubbed stack, reset stub before each test):
1. Connect, GENERATE with the defaults (Official / Briefing, English) → result R1. Read `GET
   http://localhost:4010/__control/requests?kind=responses` (and `kind=rss`, `google-page`, `decode`, `article`).
   Click `quick-rewrite`: `rewrite-menu` visible, 2 headings + 18 items in the order above,
   `rewrite-option-official-briefing-en` disabled, the 17 others enabled. Click `rewrite-option-kafka-uk`: the POST
   body equals `{"writingStyle":{"mode":"NARRATIVE","profileId":"kafka"},"language":"uk"}`; URL becomes
   `/futures/<R2>` (R2 ≠ R1); `result-view` visible; `story-headline` = `Заголовок із майбутнього — Narrative — inspired
   by Franz Kafka`; the panel shows NARRATIVE / kafka / `Ukrainian`. Stub deltas: exactly 1 new `responses` request,
   whose input contains `ORACUL REQUEST STORY_WRITING`; 0 new `rss`, `google-page`, `decode`, `article` requests.
   The new request's input equals R1's attempt-1 STORY_WRITING input except the lines from `WRITING STYLE` up to the
   line before `TASK`; its `structured-scenario` block is byte-identical; `instructions` identical.
   `GET /api/runs/<R2>`: kind REWRITE, `parentRunId` R1, `scenarioRunId` R1, `evidencePackId` and `counts` = R1's.
   `GET /api/runs/<R1>` and `/api/runs/<R1>/result` unchanged (deep-equal to before).
2. Rewrite of a rewrite: on R2 choose `rewrite-option-poe-en` → R3: `parentRunId` R2, `scenarioRunId` R1, exactly 1
   new STORY_WRITING request whose `structured-scenario` block equals R1's; `GET /api/runs/<R3>/structured-scenario`
   deep-equals R1's. On R3's result the menu disables exactly `rewrite-option-poe-en`.
3. 409 for a parent that is not COMPLETED: stub story mode `invalid`, GENERATE → FAILED run F; `POST
   /api/runs/<F>/rewrites` (page.request, session cookie) with a valid body → 409 `{"code":"RUN_NOT_COMPLETED",
   "message":"Only a completed future can be rewritten"}`; `GET /api/runs` (Recent futures) unchanged, no new
   Responses request.
4. Failing rewrite: on a COMPLETED R1 set stub story mode `invalid`, choose `rewrite-option-kafka-en` → the rewrite
   ends FAILED: `failure-message` "ORACUL could not construct a valid scenario", `try-again` visible; `GET
   /api/runs/<R1>` status COMPLETED with the same headline and `GET /api/runs/<R1>/result` unchanged.
5. One active run: with R1 COMPLETED, set `POST /__control/google {"mode":"slow"}` and start a second run through
   GENERATE; while it is QUEUED / RUNNING, `POST /api/runs/<R1>/rewrites` (valid body) → 409 `{"code":"RUN_ALREADY_ACTIVE",
   "message":"A generation is already running"}`; it creates no run: after the second run ends, `GET /api/runs`
   has no item of kind `REWRITE`.

- Changes earlier behaviour: `quick-actions` has exactly 5 buttons, `quick-alternative` last → 6 buttons, `quick-rewrite` "REWRITE AS…" last (tests: frontend/src/app/runs/quick-actions.spec.ts)
- Changes earlier behaviour: `POST /api/runs/{runId}/rewrites` answers the generated 501 → the FR-67 statuses; `RunKind` gains `REWRITE` and `GenerationRun` gains `scenarioRunId` (absent for STANDARD / ALTERNATIVE, so no earlier response changes); `PipelineExecutor` gains the REWRITE branch while STANDARD / ALTERNATIVE keep their paths; `generation_run` gains `scenario_run_id` (grep: no test calls `/rewrites` or asserts 501; no backend test or E2E asserts the set of `RunKind` values, the exact key set of a `GenerationRun`, or the exact column list of `generation_run` — `ChatGptCredentialHandlingIT` uses `contains` and its forbidden-name pattern does not match `scenario_run_id`; `AvoidedFutures` and `isCompletedWithScenario` require `final_attempt`, so REWRITE rows never enter ALTERNATIVE's futures to avoid; `run-view.spec.ts` / `stop-run.spec.ts` count the `quick-actions` bar, not its buttons; E2E `alternative-future.spec.ts` / `quick-regeneration.spec.ts` address buttons by test id; `RunStore` gains `RewriteService`, which is `providedIn: 'root'`, so existing TestBed setups need no provider) (tests: none)
- Ranges & invariants: valid request classes = 9 profiles × 2 languages (18, each its own case, parameterized) → 202
  with the stored `output.writingStyle` / `output.language` equal to the request's; each invalid body / style /
  language class listed in the check order above → its exact 400 (parameterized, one case per class, each also
  without a connection → still 400); the cross-mode grid 2 modes × 9 profiles (9 valid, 9 → "Unknown writing
  style"); parent status classes QUEUED, RUNNING, FAILED, INSUFFICIENT_EVIDENCE, STOPPED, COMPLETED without story →
  409 `RUN_NOT_COMPLETED`; COMPLETED STANDARD, ALTERNATIVE and REWRITE parents → 202. Invariants: a REWRITE run has
  `parentRunId` = request runId, `scenarioRunId` = a STANDARD or ALTERNATIVE run (never a REWRITE), `evidencePackId` =
  parent's = `scenarioRunId`'s, `counts` deep-equal the parent's, configuration equal to the parent's except
  `output.writingStyle` / `output.language`; for chains of length 1, 2 and 3 (rewrite of a rewrite of a rewrite) the
  `structured-scenario` block of every STORY_WRITING request is byte-identical to the original's and the input differs
  from the original's attempt-1 input only inside the WRITING STYLE section; per rewrite 0 non-STORY_WRITING
  Responses requests, 0 news / article requests and 1 (or 2 after an invalid first story) STORY_WRITING requests;
  every observed `stageIndex` of a REWRITE run ∈ {0, 10}; a rejected request (any 4xx / 500) creates no row and
  changes no row; parent and scenario run rows are unchanged after every rewrite outcome (COMPLETED, FAILED,
  STOPPED); the menu has 2 headings + (2 × profile count) items and exactly one disabled item — the shown result's own
  effective combination — for each of the 18 combinations as the shown run's configuration plus the legacy
  configuration (19 cases, parameterized); `quick-rewrite.disabled` = `quick-alternative.disabled` in every state
  (not connected, starting, QUEUED, RUNNING, COMPLETED, no run).

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
