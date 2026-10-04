# Spec — Run control: STOP a generation, always generate with an evidence note

Covers: FR-45, FR-47

Delta against phase-01 `generation-runs.md` (run lifecycle, single active run, run view, FR-31 insufficient
evidence), `scenario-reasoning.md` (Evidence Guard FR-21, empty-pack rule), `future-result.md`,
`recent-futures.md` and the current code `RunService`, `RunsController`, `PipelineExecutor`, `ResearchPipeline`
(stage RANKING), `ReasoningPipeline` (`Result.EMPTY_PACK`), `EvidenceGuard`, `ScenarioGenerationPrompt`,
`GenerationRunRepository`, frontend `RunStore`, `GenerateButton`, `ProgressView`, `RunView`, `InsufficientView`,
`RecentFuturesComponent`. Added by the approved scope changes of 2026-10-04 (clarification-log rounds 4–5).

## Purpose
The user can stop a slow generation and start a new one at once, and a lack of evidence never ends a run: ORACUL
writes the future from whatever evidence it has (never inventing evidence or sources) and says at the end how
grounded it is, with LOWER REALISM as the next step.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| `generation_run` | `status` | VARCHAR(32) | + value `STOPPED` (terminal, not active: the partial unique index `one_active_run_per_session` covers only QUEUED/RUNNING, so a STOPPED run frees the slot) |
| `generation_run` | `evidence_note_kind` | VARCHAR(32) NULL (Flyway `V10`) | `INSUFFICIENT_EVIDENCE` / `NO_EVIDENCE` / NULL; written in the stage-RANKING commit together with the pack; copied from the parent on `insertAlternativeQueued` |
| `generation_run` | `evidence_core_items` | INTEGER NULL (`V10`) | CORE items of the pack; set iff the note is set |
| `generation_run` | `evidence_core_needed` | INTEGER NULL (`V10`) | `MinCoreThresholds.forRealism(realism)` at RANKING; set iff the note is set |
| `generation_run` | `suggested_realism` | INTEGER NULL (existing) | max(1, realism − 2) iff note kind INSUFFICIENT_EVIDENCE and realism > 1 (copied to alternatives) |
| `source` | `publisher_url` | TEXT NULL (`V10`) | news-search.md FR-48 |
| API `GenerationRun` | `evidenceNote` | `EvidenceNote` (optional) | from the three columns; `message` built from kind, numbers and `configuration.realism` (not stored) |
| API `RecentRunSummary` | `status` | `RunStatus` (optional in the schema, always sent) | `COMPLETED` or `STOPPED`; `headline` present iff COMPLETED |

Legacy values stay readable: stored runs with status `INSUFFICIENT_EVIDENCE` or failure `NEWS_UNAVAILABLE` keep
rendering as in phase 01 (insufficient view, failure view); new runs never get them.

## Behaviour

### FR-45 — Stop a generation and start a new one
- Happy path:
  1. While the tracked run is QUEUED or RUNNING, the generate button (`generate-button`) reads exactly `STOP` and is
     enabled (also when the ChatGPT connection is no longer CONNECTED); otherwise it reads `GENERATE THE FUTURE` with
     the phase-01 enabled rule. The button is shown on the welcome view (`/`, as today), **at the bottom of the
     progress view** (`/futures/<id>` while active, new) and in the stopped view (new).
  2. Click `STOP` → the button is disabled (label stays `STOP`) until the answer; the frontend sends exactly one
     `POST /api/runs/{runId}/stop` (no body) for the tracked run.
  3. Backend `stopRun`: run visible to the session (else 404); one conditional update
     `status = 'STOPPED', updated_at = now, completed_at = now WHERE id = ? AND status IN ('QUEUED','RUNNING')`;
     answer 200 with `getRun` of the run after the update.
  4. Pipeline: every run-guard check fails for a STOPPED run (it is not RUNNING), so no further outbound request
     starts for it: ChatGPT Responses calls (incl. retries and the text.format repeat), `GET /v1/models`, token
     refreshes for it, Google News RSS and GDELT requests (incl. retries), and — new — article-page / Google-link
     fetches of READING_SOURCES (the guard is checked before each fetch). Every later commit of the pipeline is
     conditional on RUNNING and changes nothing; the abandon path (`failTimedOut`) only touches QUEUED/RUNNING rows,
     so STOPPED is never overwritten by RUN_TIMEOUT, FAILED or COMPLETED. A QUEUED run that is stopped before the
     executor picks it up never starts (`startRunning` is conditional on QUEUED).
  5. Frontend on the 200: polling of that run stops (a poll answer that was already in flight is ignored), the run
     signal takes the answer; status STOPPED → the run view shows `stopped-view` with `stopped-message` "Generation
     stopped", `stopped-hint` "Change the settings or click GENERATE THE FUTURE to start a new one." and the generate
     button (`GENERATE THE FUTURE`, enabled when connected); the quick actions are not shown (no result).
  6. Click `GENERATE THE FUTURE` after a stop → `POST /api/runs` with the panel's current configuration (phase-01
     FR-10 flow): 202, navigation to the new run.
  7. Recent futures lists STOPPED runs (newest first with the COMPLETED ones, at most 20 together): instead of the
     headline the entry shows `recent-future-status-<id>` "Stopped" (no `recent-future-headline-<id>`); time and
     settings as today; a click opens `/futures/<id>` (stopped view, panel loaded with the run's configuration).
  8. Reset ChatGPT connection is allowed once the run is STOPPED (the reset refuses only QUEUED/RUNNING runs).
- Rules:
  - A run that is already terminal (COMPLETED, FAILED, INSUFFICIENT_EVIDENCE, STOPPED) is not changed by a stop
    request: 200 with its unchanged body; the UI shows that final state (result view, failure view, …).
  - A stop is not a failure: STOPPED runs have no `failure`, no `headline`, no `evidenceNote` unless RANKING had
    already committed one; `stage` / `stageIndex` stay those of the moment of the stop (absent / 0 when QUEUED);
    `completedAt` = the stop time.
  - Stop needs no ChatGPT connection and makes no outbound call itself.
  - STOPPED runs: `startAlternativeRun` → 409 RUN_NOT_COMPLETED; `getFutureResult` → 409 RESULT_NOT_READY;
    `getStructuredScenario` → 200 if an attempt was already parsed, else 409 SCENARIO_NOT_READY; `getEvidencePack` →
    200 iff RANKING had committed, else 409 EVIDENCE_PACK_NOT_READY; research / sources / events as stored.
  - Requests already in flight at the stop may still complete; their answers are discarded (no row written).
- Errors:
  - unknown, malformed or foreign runId → 404 `RUN_NOT_FOUND` → snackbar "Future not found" (`run-error-message`),
    polling continues
  - backend down / 5xx → 500 `INTERNAL_ERROR` (or no ApiError) → snackbar "Something went wrong — try again", the
    button reads `STOP` and is enabled again, polling continues
  - stop arrives after the run ended → 200 unchanged run → UI shows the final state (no message)
- Changes earlier behaviour: while a run was QUEUED/RUNNING the generate button read `GENERATE THE FUTURE` and was disabled on the welcome view (and absent from the progress view) → it reads `STOP`, is enabled and sends `POST /api/runs/{id}/stop`; it is also rendered in the progress view and the stopped view (tests: frontend/src/app/runs/generate-button.spec.ts, frontend/src/app/runs/run.store.spec.ts, e2e/tests/run-start.spec.ts)
- Changes earlier behaviour: `listRecentRuns` listed only COMPLETED runs with a headline and every item had `headline` → STOPPED runs are listed too, every item has `status`, STOPPED items have no `headline`; existing seeds contain no STOPPED run and no test compares whole items (tests: none)
- Changes earlier behaviour: `RunStatus` had 5 values → + `STOPPED` (terminal); the reset rule "only QUEUED/RUNNING refuse" already covers it; `ChatGptResetIT` iterates `RunStatus.values()` and expects 204 for every non-active value, which STOPPED satisfies (tests: none)
- Ranges & invariants: status classes for `stopRun` (parameterized IT over every `RunStatus` value, seeded rows): QUEUED → 200 STOPPED, `completedAt` set, stageIndex 0; RUNNING at each of the 10 stages → 200 STOPPED with that stage/stageIndex; COMPLETED / FAILED / INSUFFICIENT_EVIDENCE / STOPPED → 200 with a body equal to getRun before the request and the row unchanged (all columns). runId classes: own run → 200; another session's run / unknown UUID / `abc` → 404 RUN_NOT_FOUND with exactly `{code, message}`. Stop during each outbound kind (gate the stub at QUERY_EXPANSION, `GET /v1/models`, SEARCHING Google / GDELT request, READING_SOURCES article fetch, EVENT_NORMALIZATION, EVENT_CLASSIFICATION, SCENARIO_GENERATION, SCENARIO_CRITIC, STORY_WRITING): after the 200 and release of the gate, for 2 s no new request of any kind arrives for the run, the row stays STOPPED with the same `updated_at`, no source / event / pack / attempt / story row is added after the stop, and a new `POST /api/runs` of the same session answers 202. Race invariant: concurrent stop and pipeline completion → the row ends either COMPLETED with a story (stop answered with the COMPLETED body) or STOPPED without a story — never both, never FAILED. Frontend: N rapid clicks on STOP → exactly 1 POST; label `STOP` iff the tracked run is QUEUED/RUNNING; after a STOPPED answer no further `GET /api/runs/{id}`.

### FR-47 — Always generate; note insufficient evidence at the end
- Happy path:
  1. Stage SEARCHING never ends a run: when every group failed on both providers or nothing could be sent, every
     query is FAILED and the run continues to READING_SOURCES with 0 sources (no `NEWS_UNAVAILABLE`).
  2. Stage RANKING builds and stores the Evidence Pack as today, then — in the same commit — decides the note
     instead of the phase-01 sufficiency stop: `core` = CORE items, `total` = core + supporting + counter-signals,
     `needed` = `MinCoreThresholds.forRealism(realism)` (`oracul.evidence.min-core.*`, defaults 9–10 → 5, 6–8 → 3,
     1–5 → 1):
     - total = 0 → note `NO_EVIDENCE` (coreItems 0, coreNeeded `needed`), no `suggestedRealism`;
     - else core < needed → note `INSUFFICIENT_EVIDENCE` (coreItems core, coreNeeded needed), `suggestedRealism` =
       max(1, realism − 2) iff realism > 1;
     - else no note. The run always continues with EXPLORING_FUTURES (status stays RUNNING).
  3. **Normal mode** (pack not empty, also when INSUFFICIENT_EVIDENCE): scenario generation, Evidence Guard, critic
     and story exactly as today, using only the evidence in the pack.
  4. **Speculative mode** (pack empty; replaces `ReasoningPipeline.Result.EMPTY_PACK` and the placeholder stages
     8–10):
     - the SCENARIO_GENERATION input text gets, right after the line `Cite Evidence IDs exactly as written in the
       pack. …`, the TASK line `The Evidence Pack is empty: no current news could be used. Write a fully speculative
       scenario: factsUsed, inferences and counterSignalsConsidered are empty arrays, the causal chain has only
       SPECULATION steps followed by the single FUTURE_EVENT, and no Evidence ID appears anywhere.` (instructions,
       schema, data blocks and every other line unchanged; the `evidence-pack` block is the renderer's output for the
       empty pack as today);
     - Evidence Guard in speculative mode = the normal rules with three differences: (a) the chain-shape rule "first
       step must be FACT" is not applied (order 1..n, non-decreasing class order and the single last FUTURE_EVENT
       still are); (b) "no FACT step remains" is not checked — after removals the chain needs ≥ 2 steps, else
       CAUSAL_CHAIN_INVALID / regeneration; (c) the outcome is FAIL only when a regeneration is requested (the "no
       fact left → FAIL" condition is not applied). Every fact, inference, counter-signal and FACT/INFERENCE step is
       removed by the existing rules (the pack has no Evidence ID), so an accepted speculative scenario never cites
       evidence;
     - critic, regeneration limits and story writing as in normal mode; the result's SOURCES panel is empty
       (`sources-empty`), WHY THESE NEWS shows the counts (0 sources).
  5. The run ends COMPLETED with a headline and story (or FAILED for a reason unrelated to evidence: ChatGPT errors,
     INVALID_SCENARIO, SCENARIO_REJECTED, RUN_TIMEOUT …).
  6. Result view: after `<app-future-result>` the run view shows the note when `evidenceNote` is present:
     `evidence-note` containing `evidence-note-message` with exactly `evidenceNote.message`, and — only when
     `suggestedRealism` is present — the button `lower-realism` "LOWER REALISM". No note element when the evidence
     meets the realism.
  7. LOWER REALISM: loads the run's configuration into the panel, sets realism to `suggestedRealism` (= realism − 2,
     at least 1) and starts a new run with that configuration (`POST /api/runs`); disabled while a start is pending,
     a run is active or ChatGPT is not CONNECTED.
- Rules:
  - Messages (exact; `'` is U+0027): INSUFFICIENT_EVIDENCE `Realism <r> couldn't be fully met: only <core> core
    evidence <item|items> (needs <needed>). This future is less grounded.` — `item` iff core = 1;
    NO_EVIDENCE `No current news could be used — this future is speculative, not grounded in evidence.` (— is U+2014).
  - The note never ends, pauses or fails a run; ORACUL never invents evidence or sources to reach a threshold.
  - With all thresholds 0 (E2E medium/low) INSUFFICIENT_EVIDENCE cannot occur; NO_EVIDENCE still can.
  - ALTERNATIVE runs reuse the parent's pack, so they copy the parent's note and `suggestedRealism` at creation and
    run in the same mode (speculative iff the pack is empty).
  - New runs never get status INSUFFICIENT_EVIDENCE or failure codes INSUFFICIENT_EVIDENCE / NEWS_UNAVAILABLE; the
    enum values, `RunFailures` texts and the frontend insufficient / failure views stay for stored runs.
  - `oracul.run.placeholder-stage-delay` has no effect any more (no placeholder stages); the property may stay.
- Errors:
  - no news at all (Google and GDELT down, budget exhausted) → no error: COMPLETED with the NO_EVIDENCE note
  - guard fails twice in speculative mode → FAILED `SCENARIO_REJECTED` "ORACUL could not construct a scenario
    supported by current evidence" (unchanged rule)
  - LOWER REALISM start refused (409 / 401 / 503 …) → the phase-01 start error snackbar
- Changes earlier behaviour: too few CORE items for the realism ended the run at RANKING with status INSUFFICIENT_EVIDENCE, failure `INSUFFICIENT_EVIDENCE` "ORACUL found insufficient current evidence … at Realism <n>.", stageIndex 6, no scenario/story and the insufficient view → the run continues and ends COMPLETED with a story, `evidenceNote` INSUFFICIENT_EVIDENCE, `suggestedRealism`, the note with LOWER REALISM below the result (tests: backend/src/test/java/com/oracul/app/result/InsufficientEvidenceIT.java, e2e/tests/insufficient-evidence.spec.ts)
- Changes earlier behaviour: an empty Evidence Pack (no sources or every event excluded) ended COMPLETED without scenario, story or headline after placeholder stages, with only the QUERY_EXPANSION (+ event) calls → speculative mode: SCENARIO_GENERATION, SCENARIO_CRITIC and STORY_WRITING are called, the run ends COMPLETED with a headline, story, result and the NO_EVIDENCE note; tests that assert "expansion is the only Responses call", no GEN/CRITIC/STORY request, no attempt rows, no headline or 409 SCENARIO/RESULT_NOT_READY for an empty-news run, or whose responder answers every purpose with the same expansion failure/answer and expects COMPLETED, must change (tests: backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java, backend/src/test/java/com/oracul/app/reasoning/CriticIT.java, backend/src/test/java/com/oracul/app/result/FutureResultIT.java, backend/src/test/java/com/oracul/app/runs/GetRunTerminalIT.java, backend/src/test/java/com/oracul/app/research/EventNormalizationIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanIT.java, backend/src/test/java/com/oracul/app/research/ResearchPlanTimeoutIT.java, backend/src/test/java/com/oracul/app/research/ErrorClassificationIT.java, e2e/tests/events.spec.ts)
- Changes earlier behaviour: every news group FAILED (or none sent) ended the run FAILED `NEWS_UNAVAILABLE` "ORACUL could not reach its news sources — try again later" at SEARCHING (stageIndex 3) with only the expansion call → every query FAILED, 0 sources, the run continues in speculative mode and ends COMPLETED with the NO_EVIDENCE note; tests that use "news down" to get a FAILED run or a run without pack must use another failure (tests: backend/src/test/java/com/oracul/app/research/EvidencePackIT.java, backend/src/test/java/com/oracul/app/reasoning/StructuredScenarioIT.java, backend/src/test/java/com/oracul/app/research/SourceRetrievalIT.java, backend/src/test/java/com/oracul/app/research/SourceQueryTimeoutIT.java, backend/src/test/java/com/oracul/app/research/NewsSearchRunIT.java, backend/src/test/java/com/oracul/app/research/NewsSearchNoBudgetIT.java, backend/src/test/java/com/oracul/app/runs/RunDeadlineIT.java, e2e/tests/search-sources.spec.ts, e2e/tests/run-failures.spec.ts)
- Ranges & invariants: note decision (parameterized IT or unit test of the pure decision, default thresholds 5/3/1, every realism 1–10): core = needed − 1 → INSUFFICIENT_EVIDENCE, core = needed → no note, core = needed + 1 → no note (realism 10: 4 → note, 5 → none; 9: 4 / 5; 8: 2 / 3; 6: 2 / 3; 5: 0 with 1 supporting item → note / 1 → none; 1: 0 with a counter-signal → note without `suggestedRealism`); total = 0 → NO_EVIDENCE for every realism 1–10 and also with all thresholds 0; thresholds 0 and total > 0 → no note for every realism. `suggestedRealism`: 10 → 8, 9 → 7, 3 → 1, 2 → 1, 1 → absent; absent for NO_EVIDENCE and no note. Message classes: core 2 / needs 5 at realism 10 → exactly `Realism 10 couldn't be fully met: only 2 core evidence items (needs 5). This future is less grounded.`; core 1 / needs 3 at realism 7 → `… only 1 core evidence item (needs 3). …`; core 0 / needs 1 → `… only 0 core evidence items (needs 1). …`. Speculative guard classes (unit, empty pack): SC-DEFAULT-like scenario (F1 citing E001, I1 based on F1, P1, FUTURE_EVENT) → PASS_WITH_REMOVALS, chain [SPECULATION, FUTURE_EVENT]; fully speculative scenario (no facts, chain P1, P2, FUTURE_EVENT) → PASS, nothing removed; only FACT + FUTURE_EVENT steps → FAIL (fewer than 2 steps remain), regeneration; FUTURE_EVENT outside the window → FAIL; FUTURE_EVENT not last → FAIL; the normal-mode guard results for non-empty packs are unchanged (existing `EvidenceGuardTest` stays green). Invariants for every new run: terminal status ∈ {COMPLETED, FAILED, STOPPED}; never INSUFFICIENT_EVIDENCE, never failure NEWS_UNAVAILABLE / INSUFFICIENT_EVIDENCE; a COMPLETED run has a headline, a story and a result; `evidenceNote` present iff the pack is empty or core < needed, kind NO_EVIDENCE iff the pack is empty; an accepted scenario's every cited Evidence ID is in the pack (none for an empty pack); an ALTERNATIVE run's note and `suggestedRealism` equal its parent's; UI: `evidence-note` rendered iff `evidenceNote` present, `lower-realism` iff `suggestedRealism` present.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| POST | `/api/runs/{runId}/stop` | `stopRun` (tag `runs`, new) | none (body ignored) | 200 `GenerationRun` (STOPPED, or unchanged when already terminal) · 404 `RUN_NOT_FOUND` "Future not found" · 500 `INTERNAL_ERROR` |
| GET | `/api/runs/{runId}` | `getRun` | — | `status` may be `STOPPED`; optional `evidenceNote`; `suggestedRealism` per FR-47 |
| POST | `/api/runs/{runId}/alternatives` | `startAlternativeRun` | — | STOPPED parent → 409 `RUN_NOT_COMPLETED`; 202 body copies `evidenceNote` / `suggestedRealism` |
| GET | `/api/runs` | `listRecentRuns` | — | COMPLETED (with headline) and STOPPED runs; items carry `status` |
| GET | `/api/runs/{runId}/result` · `/structured-scenario` · `/evidence-pack` | unchanged ids | — | STOPPED handled as in FR-45 rules; an empty-pack run now has a scenario and a result |

`EvidenceNote` `{kind: INSUFFICIENT_EVIDENCE | NO_EVIDENCE, message, coreItems ≥ 0, coreNeeded 0–10}`, all required.

## UI
- Routes: `/` (welcome view), `/futures/:runId` (run view) — unchanged.
- `data-testid`s (new or changed):
  | testid | Where | Content / behaviour |
  |---|---|---|
  | `generate-button` | welcome view, progress view (new, below the steps), stopped view (new) | `STOP` + enabled while the tracked run is QUEUED/RUNNING (disabled only while the stop request is pending); otherwise `GENERATE THE FUTURE` with the phase-01 rule |
  | `stopped-view` | run view, status STOPPED | container |
  | `stopped-message` | in `stopped-view` | `Generation stopped` |
  | `stopped-hint` | in `stopped-view` | `Change the settings or click GENERATE THE FUTURE to start a new one.` |
  | `recent-future-status-<id>` | recent futures entry of a STOPPED run (replaces `recent-future-headline-<id>`) | `Stopped` |
  | `evidence-note` | run view after `result-view`, iff `evidenceNote` | container (Material card, role `note`) |
  | `evidence-note-message` | in `evidence-note` | `evidenceNote.message` as text |
  | `lower-realism` | in `evidence-note`, iff `suggestedRealism` | `LOWER REALISM` (same testid as the legacy insufficient view, which renders only for stored INSUFFICIENT_EVIDENCE runs; never both) |
  | `run-error-message` | snackbar | stop failures: API message or `Something went wrong — try again` |
- States: active (progress + STOP) · stopping (STOP disabled) · stopped (stopped view) · completed with note ·
  completed without note · legacy insufficient (unchanged) · legacy failure (unchanged).

## Slice 03_run-modes-readme — FR-45 / FR-47 test contract
- Backend IT (`// @trace FR-45`): `stopRun` status / runId classes above; gated stub stop at every outbound kind (the
  run stays STOPPED, no new request for 2 s, slot free, reset 204); QUEUED stop before the executor starts (0
  requests); `listRecentRuns` with seeded COMPLETED + STOPPED + FAILED rows → COMPLETED and STOPPED in createdAt
  order, STOPPED items `status` STOPPED without `headline`, COMPLETED items `status` COMPLETED.
- Backend unit / IT (`// @trace FR-47`): the note decision table, message texts, `suggestedRealism`, speculative
  prompt line (present iff the pack is empty), speculative guard classes; IT: realism 10 with 2 core items →
  COMPLETED, headline, `evidenceNote` `{INSUFFICIENT_EVIDENCE, "…only 2 core evidence items (needs 5)…", 2, 5}`,
  `suggestedRealism` 8, `getFutureResult` 200; default empty news → COMPLETED with headline, NO_EVIDENCE note,
  `getFutureResult.sources` [] and exactly one SCENARIO_GENERATION request containing the speculative TASK line;
  news 503 everywhere → same; alternative of a NO_EVIDENCE run → same note.
- Frontend unit (`// @trace FR-45`): `generate-button` label/enabled per status, one POST per click burst, 200
  STOPPED → stopped view and no more polls, 404 / 500 → snackbar and STOP enabled again, button present in the
  progress view; recent futures STOPPED entry. (`// @trace FR-47`): note rendered iff `evidenceNote`, exact message,
  `lower-realism` iff `suggestedRealism`, click → POST with realism = suggestedRealism and the rest of the run's
  configuration.
- E2E (`// @trace FR-45`, new `e2e/tests/run-control.spec.ts`): start the acceptance run, STOP while the progress
  view shows → `stopped-message`, `GET /api/runs/{id}` STOPPED, the stub's recorded `responses` / `rss` / `gdelt`
  counts unchanged for 3 s, `generate-button` reads `GENERATE THE FUTURE` and starts a new run (202, new URL);
  Recent futures lists the stopped run as `Stopped`; Reset ChatGPT connection right after a stop succeeds.
  (`// @trace FR-47`): `e2e/tests/insufficient-evidence.spec.ts` realism 10 with stub events `sparse` → result view
  plus `evidence-note-message` "Realism 10 couldn't be fully met: only 2 core evidence items (needs 5). This future
  is less grounded." and LOWER REALISM → new run with realism 8; rss `down` + news `down` → result view, `sources-empty`,
  note "No current news could be used — this future is speculative, not grounded in evidence.", no `lower-realism`.
