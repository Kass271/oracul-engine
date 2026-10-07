# Plan — phase-04_writing-style

Status: APPROVED ✔ 2026-10-07

Slices are built in this order. A slice starts only when every slice in "Depends on" is DONE.
Every FR of this phase is in exactly one slice.

Ordering principle: the slices follow the data flow of a style choice. The catalogue comes first (style file → API),
then the choice (panel → run configuration), then its use (configuration → STORY_WRITING request → story/dateline),
then re-use (rewrite of a completed run), and last the display (labels on the result and in Recent futures). After every
slice a run still goes from start to a COMPLETED story. Each slice is backend + frontend (where its FRs have UI) + tests,
built test-first against `api/openapi.yaml` 0.8.0 and the specs in `02_specs/` (`writing-styles.md`,
`styled-story.md`, `story-rewrite.md`, `contract-notes.md`). The whole 0.8.0 contract is already in place: operations a
slice has not implemented yet answer the generated 501 and are not called by the UI. Automated tests stay stubbed
(phase-01 NFR-7). NFR-12 is the gate after release.

| Slice | FRs | Depends on | Scope |
|---|---|---|---|
| 01_style-catalogue | FR-62 | — | Backend only (FR-62 has no UI). Package `com.oracul.app.style`: the `WritingStyles` bean reads `oracul.writing-styles.location` (default `classpath:writing-styles.json`) once at startup with Jackson. It validates the file in the order and with the exact messages of the `writing-styles.md` table: 2–50 profiles, id pattern and uniqueness, mode, label/characteristics/instructions presence and length, forbidden characters, exactly one OFFICIAL `official-briefing`, NARRATIVE `neutral-narrative`. Any failure stops the Spring context with an `IllegalStateException`. The shipped `backend/src/main/resources/writing-styles.json` contains the 9 profiles of the spec table, in that order and with the verbatim texts. `WritingStylesController implements WritingStylesApi`: `GET /api/writing-styles` → `WritingStyleCatalogue` (modes fixed in code, OFFICIAL profile first, NARRATIVE profiles in file order, `isDefault`/`isModeDefault`, languages `en`/`uk`, never `instructions`). An unexpected failure → 500 `INTERNAL_ERROR`. Domain enums `WritingMode`/`StoryLanguage` are mapped at the boundary. The bean also offers a lookup by profile id (used by 02, 03, 05). Tests: a parameterized startup-failure test over test style files (`file:`/`classpath:` locations) for every problem class and boundary (+1), the restart-with-added-profile case (`dickens`), and the response invariants. No stub change. No migration. |
| 02_style-selection | FR-63, FR-64 | 01_style-catalogue | Backend: `startRun` output validation is extended in the order story → illustration → `writingStyle` → `language`. Rejected requests get 400 `VALIDATION_FAILED` "Unknown writing style" (not an object, mode not exactly OFFICIAL/NARRATIVE, profileId missing/not in the file, profile of the other mode) or "Unknown story language" (anything but `"en"`/`"uk"`). Absent and `null` are accepted, and absent stays absent in the stored configuration. Unknown keys inside `writingStyle` are ignored. `getRun`/`getScenarioConfiguration` return the fields as stored. The style has no effect on the pipeline yet. Frontend (`src/app/scenario/`): `ScenarioLoader` makes a third parallel request `getWritingStyles` (ready after all three; any failure → existing `backend-unavailable`). `ScenarioStore` gains `output.writingStyle`/`language`, and `load` normalises absent or unknown values to OFFICIAL/`official-briefing` and `en`. The panel therefore sends both fields in every `startRun` (generate, quick regeneration, Try again, LOWER REALISM). `OutputSettings` gets `writing-style-section`, `writing-style-group` (`writing-style-option-OFFICIAL`/`-NARRATIVE`) and `narrative-style-section`. The Narrative Style choice is `narrative-style-group` radios on desktop and `narrative-style-select` on mobile (`MOBILE_QUERY`). The `story-language-select` has `story-language-option-en`/`-uk`. Reload restores the newest run's choice. E2E: fresh defaults, Narrative/Kafka + Ukrainian stored in the run configuration, reload, phone-width dropdown, and the 400 classes via API. No stub change. No migration. Older-test fallout: see the table below (expected SLICE SIZE warning). |
| 03_styled-story | FR-65, FR-66 | 02_style-selection | Backend (`com.oracul.app.result`): `StoryWritingPrompt` gets the new `RULES` text (byte-identical `instructions` for every style). The WRITING STYLE section goes between `Future event date:` and `TASK`: mode label, profile label, characteristics, language, profile instructions, the OFFICIAL 2 lines (with the Ukrainian lead-in suffix for `uk`) or the NARRATIVE 3 lines, the language line and the priority line. The effective style and language come from the run's own `generation_run.configuration` (absent → `official-briefing`/`en`, a profile no longer in the file → `official-briefing`). `Datelines.format(d, language)` adds the Ukrainian genitive months, and English output is unchanged. The date fallback rebuilds the dateline in the run's language. `StoryParser` is unchanged (word count is language-independent). No other stage's request changes. Frontend: no new element. `story-dateline`/`story-headline`/`story-body` render the Ukrainian text by interpolation, and the two AI labels stay English (unit test with a Ukrainian result). Stubs: per-style and per-language STORY_WRITING answers, chosen from the `Style profile:`/`Story language:` lines after the existing story modes. Official/English keeps ST-DEFAULT `Stub headline from the future`, any other label gives `… — <L>`, and Ukrainian gives `Заголовок із майбутнього — <L>` + UBODY(3). They go both in `e2e/stubs/server.mjs` and in backend `StubResponses`/`StoryFixtures`. E2E: two runs that differ only in style send identical non-STORY_WRITING requests (via `GET /__control/requests?kind=responses`), plus the Ukrainian dateline `ORACUL FUTURE — <d> <місяць> <yyyy>` with English labels. No migration. |
| 04_story-rewrite | FR-67 | 03_styled-story | Backend: `startRewriteRun` (`POST /api/runs/{runId}/rewrites`, implemented in the `runs` package next to `startAlternativeRun`). It runs the error order of the spec (404 → JSON 400 → style 400 → language 400 → ChatGPT 401/403/503 → 409 `RUN_NOT_COMPLETED` → 409 `RUN_ALREADY_ACTIVE` → 500) and creates nothing on any 4xx. The new kind `RunKind.REWRITE`, with `parentRunId` and `scenarioRunId`, is copied from the parent for a rewrite of a rewrite. Flyway `V15__run_scenario_source.sql` adds `generation_run.scenario_run_id` (FK). The configuration is the parent's with the new style/language. Counts, pack, evidence note and suggested realism are copied, and the row has no `final_attempt`. `PipelineExecutor`/`ReasoningPipeline` handle REWRITE explicitly: QUEUED → RUNNING at stage WRITING_STORY (stageIndex 10), then only `StoryWriter` on the pack and the accepted scenario of `scenarioRunId`, with the same attempts, fallback, deadline, STOP and failure codes. The REWRITE reads follow the spec: `getRun.hasOpenCriticIssues`, `getStructuredScenario` = record of `scenarioRunId`, pack/sources/events of the pack owner, and `getFutureResult` causal chain and `usedInScenario` from `scenarioRunId`. Parent and scenario run are never modified. Frontend (`src/app/runs/`): the sixth `quick-rewrite` "REWRITE AS…" button opens `rewrite-menu`. It has the headings `rewrite-language-en`/`-uk` and 18 `rewrite-option-<profileId>-en` / `-uk` items from the catalogue. The result's own effective combination is disabled. The button is disabled exactly when `quick-alternative` is. `RunStore.rewrite(...)` follows the new run like `alternative` (`replaceUrl`, progress, result) and loads its configuration into the panel. Errors go to the `run-error-message` snackbar. E2E: rewrite Official/en → Kafka/uk: exactly one new Responses request (STORY_WRITING), no news request, and a byte-identical `structured-scenario` block. Also: a rewrite of a rewrite, the disabled own combination, and 409 for a non-completed parent. No stub change (uses 03's answers and the existing story modes). |
| 05_style-labels | FR-68 | 04_story-rewrite | Backend: one label helper (profile label, `Official / Briefing (legacy)` without `writingStyle`, the profile id when the profile left the file; `English`/`Ukrainian`, absent → English). It fills `ScenarioMetadata.styleLabel`/`languageLabel` (always) and `rewrittenFrom {runId, headline}` (REWRITE only, the direct parent), and `RecentRunSummary.styleLabel`/`languageLabel` (REWRITE runs are listed like the others). `startAlternativeRun` accepts a COMPLETED REWRITE parent with a story: it uses the rewrite's configuration and pack. Future 1 of its avoided futures is the scenario of the rewrite's `scenarioRunId`, and REWRITE runs are never listed separately. Quick regeneration and ALTERNATIVE keep the style/language (configuration copy / panel). Frontend: `scenario-metadata` gets `meta-style`, `meta-language` and `meta-rewritten-from` (rendered only when present) after `meta-horizon`. Recent futures entries get `recent-future-style-<runId>` "<style> · <language>" (STOPPED entries too). All texts come from the API, by interpolation. E2E: Kafka/uk metadata, "Rewritten from: <headline>", a legacy run (API start without style) labelled `Official / Briefing (legacy)` / `English`, Recent futures labels, and Poe/uk DARKER and ALTERNATIVE FUTURE keeping Poe/uk. No stub change. No migration. |

## Dependency graph

```
01_style-catalogue ─► 02_style-selection ─► 03_styled-story ─► 04_story-rewrite ─► 05_style-labels ─► [release] ─► [GATE: NFR-12 real check]
```
The build order is the table order (01 → 05). The chain is linear: each slice needs the data its predecessor adds. 02
needs the catalogue, 03 needs `output.writingStyle`/`language` in the stored configuration, and 04 needs the styled
STORY_WRITING request. 05 needs REWRITE runs for `rewrittenFrom` and REWRITE parents of ALTERNATIVE.

## Fallout on older tests: where it lands

Each older test whose assertion a phase-04 rule breaks is rewritten in exactly one slice: the slice whose FR changes that
behaviour. No slice repairs another slice's fallout. The lists come from a grep of `backend/src/test`, `e2e/tests` and
`frontend/src/**/*.spec.ts` (2026-10-07). They are a planning estimate. The step-4a delta of each slice names the exact
files (`Changes earlier behaviour … (tests: …)`).

| Slice | Cause | Assertion rewrites (estimate) | Mechanical only (no assertion change) |
|---|---|---|---|
| `01_style-catalogue` | new operation, new bean | none expected | none. The test base may point `oracul.writing-styles.location` at the shipped file, which is the default anyway |
| `02_style-selection` | **the panel always sends `output.writingStyle` + `output.language`** (store normalises on `load`) | frontend `request.body` / `output` deep-equals: `quick-actions.spec.ts`, `run.store.spec.ts`, `run-view.spec.ts`, `run-failure.spec.ts`, `stop-run.spec.ts`, `evidence-note.spec.ts`, `runs/insufficient-evidence.spec.ts`, `scenario.store.wildcards.spec.ts`, `output-settings.spec.ts`. E2E `postDataJSON()` deep-equals: `run-start.spec.ts`, `quick-regeneration.spec.ts`, `run-failures.spec.ts`, `insufficient-evidence.spec.ts`, `custom-wildcards-output.spec.ts` | — |
| `02_style-selection` | **the extra panel load request** `GET /api/writing-styles` (ready after 3 requests) | `app.spec.ts` (counts the load requests; the error-state cases fail one of three) | flush `/api/writing-styles` next to catalogue + configuration in `app.spec.ts`, `app.mobile.spec.ts`, `output-settings.spec.ts`, `custom-wildcards.spec.ts`, `wildcard-catalogue.spec.ts`, `recent-futures.spec.ts`, `chatgpt-connection.spec.ts`, `run-view.spec.ts`, `stop-run.spec.ts`, `run.store.spec.ts`, `progress-view.spec.ts`, `generate-button.spec.ts`. E2E `scenario-controls.spec.ts` routes only `**/api/scenario/**`, so its error-state cases stay green unchanged |
| `02_style-selection` | backend output validation extended | none: the existing `StartRunOutputValidationTest` classes keep their results (new classes are added). `StartRunValidationTest` precedence cases unchanged | — |
| `03_styled-story` | new `RULES` text, WRITING STYLE section, `Datelines.format(d, language)`, per-style stub answers | `StoryWritingPromptTest` (RULES lines "short news story" / "Month d, yyyy", input layout), `StoryWritingIT` / `StoryWritingStageIT` (full STORY_WRITING input comparisons, if any), `StoryHarness` / `StoryFixtures` (expected prompt text) | `DatelinesTest` stays green (the English overload is unchanged). Official/English stub answer = ST-DEFAULT, so the ~16 files asserting "Stub headline from the future" are untouched |
| `04_story-rewrite` | **sixth result button** `quick-rewrite` | `quick-actions.spec.ts` (exact button list = 5 actions + `quick-alternative`, line ~170) | `run-view.spec.ts` counts the `quick-actions` bars, not the buttons, so no change. E2E `alternative-future.spec.ts` addresses buttons by test id, so no change |
| `04_story-rewrite` | `RunKind.REWRITE` | none expected: the kind checks in `PipelineExecutor` / `ReasoningPipeline` are `== ALTERNATIVE` comparisons, and REWRITE is handled before them | — |
| `05_style-labels` | metadata + Recent futures fields | none expected: `FutureResultIT` asserts metadata per key, and `RecentRunsIT`, `recent-futures.spec.ts`, `scenario-metadata.spec.ts` assert per test id. The step-4a grep confirms there is no exact key set or child list | — |

Expected step-4a SLICE SIZE warning (more than 10 older tests) and why continuing is reasonable:
- **02_style-selection.** About 22 distinct files: 14 body-assertion rewrites and 12 load-request flushes, 5 files in
  both lists. Both causes come from the approved spec (the panel always sends both fields; decision 14: the catalogue
  loads with the panel). They cannot move to another slice because FR-63 and FR-64 each need both. FR-63 and FR-64
  share one slice on purpose. Split, the same ~14 body assertions would be rewritten twice, once per field. Suggested
  handling, to continue:
  - The tester first adds two shared test helpers in one mechanical RED commit: a frontend `flushPanelLoad(http, …)`
    that answers all three load requests, and a `panelBody(config)` / E2E `withPanelOutput(config)` that adds the
    default `writingStyle` + `language`.
  - The behaviour tests then use them, so a later change of the panel body or of the load is a one-line edit.

## Stubs per slice
| Slice | Stub change |
|---|---|
| 01, 02 | none |
| 03_styled-story | `e2e/stubs/server.mjs`: STORY_WRITING answer by `Style profile:` / `Story language:` (table of `styled-story.md` "Stubs"), after the existing story modes. Backend `StubResponses` / `StoryFixtures`: the same answers. Request recording unchanged |
| 04, 05 | none (they use the 03 answers, the existing story modes and `GET /__control/requests?kind=responses`) |

## Gates and open items
- **After release: the NFR-12 real check (manual, user).** It runs in real mode (`docker compose up -d`, real ChatGPT
  account, real Google News):
  - one scenario as Official / Briefing in English, then "Rewrite as…" Narrative — inspired by Franz Kafka (Ukrainian)
    and Narrative — inspired by Edgar Allan Poe (Ukrainian). Pass: same event and date, clearly different tone, no
    search for the rewrites, Ukrainian month in the dateline.
  - the carried phase-03 NFR-11 check: New pandemic 1 and then 10, Darkness 5, custom "AI takeover" 5. Pass: ≥ 1
    RETRIEVED source per wildcard, the level-1 and level-10 queries differ visibly, the story completes, and the log has
    no GDELT request.

  Evidence goes in `05_release/real-check.md`. The phase is GREEN only after this check.
- **Spec decisions 1–14 in `contract-notes.md`** are approved with the specs. This plan relies on:
  - decision 2: absent stays absent, so older API tests keep their configuration deep-equals
  - decision 8: the WRITING STYLE section is in the input, not `instructions`
  - decision 11: REWRITE kind + `scenarioRunId`
  - decision 14: the third load request

## Risks
- **The E2E stub image is not rebuilt automatically.** `e2e/` is excluded from the image inputs
  (`factory-engine/checks/lib/hash.mjs` `IMAGE_EXCLUDE`). `stack.mjs up` therefore keeps the old stub after
  `e2e/stubs/server.mjs` changes. In 03_styled-story the first E2E run after the stub change must use
  `node factory-engine/bin/stack.mjs up --build` (or `docker compose … build stub`). Otherwise the per-style answers are
  missing and the Ukrainian / style E2E fail for the wrong reason. The same applies to any later stub fix in 04 or 05.
- **Transitional states between slices.**
  - 02: runs store the style and language, but the story is still the English FR-23 news story. 02's tests must not
    assert prompt or story content.
  - 03–04: results show no Style / Language metadata until 05.
  - 04: ALTERNATIVE FUTURE on a REWRITE result still answers the old 409 rule (no `final_attempt`) until 05. 04's tests
    must not assert ALTERNATIVE on a rewrite.

  Each state completes runs.
- **Hidden kind switches.** A REWRITE run that falls into the STANDARD path would run a full search. 04 must cover
  every `kind` decision (`PipelineExecutor`, `ReasoningPipeline`, `AvoidedFutures`, `GenerationRunRepository`, the
  startup sweep, the deadline / STOP handling). The "0 non-STORY_WRITING requests" E2E check guards it.
- **One migration.** `V15__run_scenario_source.sql` lands in 04 only. No other slice needs DDL (style and language live
  in the configuration jsonb).
- **The style file is a startup dependency.** A bad file stops the backend. The shipped file is covered by a test that
  loads it, and the startup-failure tests use their own files through `oracul.writing-styles.location`, never the
  shipped one.
- **Prompt drift between stubs.** The E2E stub and the backend `StubResponses` both pick the answer from the
  `Style profile:` / `Story language:` lines. If 03 changes these line prefixes, both stubs must change in the same
  slice.
- **Real-model compliance (NFR-12).** The stubs cannot show whether the real model keeps to the scenario or writes in
  Ukrainian. The parser checks neither tone nor language (decision 7), so only the real check finds this.
