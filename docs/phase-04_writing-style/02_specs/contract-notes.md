# Contract notes — phase-04_writing-style

Decisions behind the phase-04 changes to `api/openapi.yaml` (version 0.7.0 → 0.8.0). Phase-01 to phase-03 conventions
stay in force (`docs/phase-01_mvp/02_specs/contract-notes.md`, `docs/phase-02_codex-provider/02_specs/contract-notes.md`,
`docs/phase-03_wildcard-search/02_specs/contract-notes.md`): every 4xx/5xx is `#/components/schemas/ApiError`
`{code, message}`, tags = capabilities, UUID run ids, ISO-8601 UTC instants, no schema named `Error`, null optional
fields left out of the JSON (never `required` + `nullable`).

Capability specs of this phase:
| Spec | Covers | Contract surface |
|---|---|---|
| `writing-styles.md` | FR-62, FR-63, FR-64 | `getWritingStyles` (tag `writing-styles`), `WritingStyleCatalogue`, `WritingModeOption`, `WritingStyleProfile`, `StoryLanguageOption`, `WritingMode`, `StoryLanguage`, `WritingStyleSelection`, `OutputSettings.writingStyle` / `language`; `startRun` validation |
| `styled-story.md` | FR-65, FR-66 (+ the NFR-12 release check) | `FutureStory.dateline` pattern / descriptions; no new operation |
| `story-rewrite.md` | FR-67, FR-68 | `startRewriteRun` (tag `rewrite`), `RewriteRequest`, `RunKind.REWRITE`, `GenerationRun.scenarioRunId`, `ScenarioMetadata.styleLabel` / `languageLabel` / `rewrittenFrom`, `RewriteOrigin`, `RecentRunSummary.styleLabel` / `languageLabel`; descriptions of `startAlternativeRun`, `getStructuredScenario`, `getFutureResult`, `listRecentRuns` |

## Operations
| Change | Operation | Backward compatible |
|---|---|---|
| new | `GET /api/writing-styles` `getWritingStyles` (tag `writing-styles`) → 200 `WritingStyleCatalogue` · 500 | yes (addition) |
| new | `POST /api/runs/{runId}/rewrites` `startRewriteRun` (tag `rewrite`) → 202 `GenerationRun` · 400 · 401 · 403 · 404 · 409 · 503 · 500 | yes (addition) |
| description only | `startRun` (two new 400 messages, absent style/language stay absent), `startAlternativeRun` (REWRITE parent eligible, style kept), `getStructuredScenario` (REWRITE → record of `scenarioRunId`), `getFutureResult` (language, labels, REWRITE data), `listRecentRuns` (REWRITE runs, labels) | yes — same paths, statuses and error codes |
| none removed | every phase-01 … phase-03 operation and operationId is kept (19 → 21 operations) | — |

Rewrite is a sub-resource `…/rewrites` of the run, like `…/alternatives`; it has its own tag `rewrite` because it is
the operation of the capability `story-rewrite.md` (generated `RewriteApi` / `rewrite.service.ts`); the backend may
implement it in the `runs` package next to `startAlternativeRun`.

## Schemas
| Schema | Change |
|---|---|
| `OutputSettings` | + optional `writingStyle` (`WritingStyleSelection`), + optional `language` (`StoryLanguage`). Absent → Official / Briefing, English; stored as sent (absent stays absent) |
| `WritingMode` | new enum `OFFICIAL`, `NARRATIVE` (fixed in code; profiles come from the style file) |
| `StoryLanguage` | new enum `en`, `uk` (ISO 639-1; Ukrainian is `uk`, not `ua`) |
| `WritingStyleSelection` | new `{mode, profileId}` both required; profileId `^[a-z0-9-]{1,40}$` |
| `WritingStyleCatalogue`, `WritingModeOption`, `WritingStyleProfile`, `StoryLanguageOption` | new (response only); default flags are `isDefault` / `isModeDefault` (a property named `default` would be a Java keyword) |
| `RewriteRequest` | new `{writingStyle, language}` both required |
| `RunKind` | + `REWRITE` |
| `GenerationRun` | `parentRunId` also for REWRITE; + optional `scenarioRunId` (REWRITE only) |
| `FutureStory` | `dateline` pattern accepts the Ukrainian form `ORACUL FUTURE — <d> <genitive month> <yyyy>`; descriptions of `dateline`, `body` |
| `ScenarioMetadata` | + optional `styleLabel` (1–100), `languageLabel`, `rewrittenFrom` (always sent for styleLabel / languageLabel since this phase) |
| `RewriteOrigin` | new `{runId, headline}` |
| `RecentRunSummary` | + optional `styleLabel`, `languageLabel` (always sent since this phase) |
| responses `BadRequest`, `Conflict` | + examples `unknownWritingStyle`, `unknownStoryLanguage`, `runNotRewritable` |

No schema, property or enum value is renamed or removed in 0.8.0 (so there is no `Renamed:` line). Every new property
is optional, so existing generated constructors (required arguments only) stay valid, stored runs keep their responses,
and API clients that omit the new request fields keep working.

Enum additions: `RunKind.REWRITE` — backend code that decides by kind (`PipelineExecutor` "alternative = kind ==
ALTERNATIVE", `RunService` mapping) must handle REWRITE explicitly (a REWRITE run must never fall into the STANDARD
research path); the frontend has no `Record<RunKind, …>` / exhaustive switch today. `WritingMode` and `StoryLanguage`
are new, so no existing switch is affected; map them to domain enums at the boundary (stack rules).

## Error codes added
None. New messages on existing codes:
| Source | HTTP | code | message |
|---|---|---|---|
| `startRun` / `startRewriteRun`: unknown or mismatched writing style | 400 | VALIDATION_FAILED | Unknown writing style |
| `startRun` / `startRewriteRun`: language other than en / uk | 400 | VALIDATION_FAILED | Unknown story language |
| `startRewriteRun`: parent not COMPLETED with a story | 409 | RUN_NOT_COMPLETED | Only a completed future can be rewritten |
`RUN_ALREADY_ACTIVE`, `RUN_NOT_FOUND`, the ChatGPT 401/403/503 codes and `INTERNAL_ERROR` are reused unchanged. Run
failures of a rewrite use the existing `RunFailureCode`s (no new code).

## Database
- One migration (slice that delivers FR-67, next free version, e.g. `V15__run_scenario_source.sql`):
  `ALTER TABLE generation_run ADD COLUMN scenario_run_id UUID NULL REFERENCES generation_run (id)`. `kind`
  (varchar(16)) already fits `REWRITE`.
- Style and language live inside `generation_run.configuration` (jsonb) — no column; `future_story.dateline`
  varchar(64) fits the Ukrainian form. No credential column (NFR-1 scan unchanged).

## Configuration
| Property | Default | Used by |
|---|---|---|
| `oracul.writing-styles.location` | `classpath:writing-styles.json` (`backend/src/main/resources/writing-styles.json`) | FR-62 — Spring resource location; tests point it at a test file (`classpath:…` or `file:…`) |
Docker compose files, `.oracul/stack.json` (`e2e` = base + e2e file, `run` = base) and nginx routes do not change; the
E2E stub only gains the per-style STORY_WRITING answers (`styled-story.md` "Stubs").

## Analyst decisions to confirm at spec approval
1. **"Reload keeps my last style choice" = the newest run's configuration.** As for every other panel control (FR-33),
   the panel restores the configuration of the session's newest run; a style picked but never used for a run is not
   stored. The alternative (persisting unsent panel changes) would be a new mechanism for all controls.
2. **Absent style / language are stored as absent, not filled with defaults.** API requests and stored runs without
   `output.writingStyle` / `output.language` are told as Official / Briefing in English and labelled "Official /
   Briefing (legacy)" / "English". This keeps every earlier API test and stored run unchanged (configuration
   deep-equal to the request). The panel always sends both fields, so every UI run of this phase is labelled with its
   real style.
3. **Style and language belong to `output`** (the Output section of FR-63 / FR-64), not to the top level of
   `ScenarioConfiguration`; validation order output = story → illustration → writingStyle → language.
4. **The request carries mode and profile** (`{mode, profileId}`), so "a narrative profile with mode Official /
   Briefing" is a rejected request (FR-63 acceptance 5). Modes are fixed in code; only profiles come from the style
   file, which must contain exactly one OFFICIAL profile (`official-briefing`) and the NARRATIVE default
   `neutral-narrative`.
5. **Style file format**: JSON resource `writing-styles.json` (Jackson; no YAML dependency needed), path configurable;
   entries `{id, mode, label, characteristics, instructions}`; startup messages name the entry as `#<position> (<id>)`.
   The prompt texts of the nine shipped profiles are fixed in `writing-styles.md`.
6. **Safety rules are in code, not in the file**: the "no copying / quoting, no new events, dates or outcomes" lines
   are added for every NARRATIVE profile and the SITUATION / CONSEQUENCES / OUTLOOK structure for OFFICIAL, so a
   profile added later cannot drop them. The file's `instructions` add only the profile's tone.
7. **Official / Briefing in Ukrainian**: the request keeps the English lead-ins SITUATION / CONSEQUENCES / OUTLOOK and
   asks to write them in Ukrainian as СИТУАЦІЯ / НАСЛІДКИ / ПЕРСПЕКТИВА. The story parser checks neither lead-ins nor
   language (no requirement asks for it).
8. **The WRITING STYLE section is in the trusted input text** (between SETTINGS and TASK), not in `instructions`, so
   `instructions` stays one constant and the original/rewrite comparison is "same input except this section".
9. **The neutral profile's label is "Neutral Narrative"** (as named in FR-62 / the idea), not "Narrative — inspired
   by …"; only author profiles use the "inspired by" label. The UI shows the full profile labels in the panel, the
   "Rewrite as…" menu, the metadata and Recent futures.
10. **Rewrite API accepts the parent's own combination.** FR-67 only asks the UI to disable it; the API does not add a
    rule nobody asked for. "Rewrite as…" is one menu with an English and a Ukrainian group of all 9 profiles.
11. **REWRITE is a new run kind** with `parentRunId` (the run it was rewritten from) and `scenarioRunId` (the original
    STANDARD / ALTERNATIVE run whose scenario is told). "Rewritten from: <headline>" names the direct parent.
    A rewrite copies the parent's counts, evidence note and suggested realism (same pack and scenario), reads like an
    ALTERNATIVE for research / sources / pack, and answers `getStructuredScenario` with the scenario run's record.
12. **ALTERNATIVE FUTURE on a rewrite is allowed** (FR-68 acceptance 5 holds for a Poe rewrite): the told scenario is
    Future 1; REWRITE runs never appear as separate avoided futures.
13. **Labels are computed by the backend** (`styleLabel`, `languageLabel`, `rewrittenFrom`) so the result and Recent
    futures need no catalogue lookup in the browser; a profile later removed from the file shows its id.
14. **The writing-styles catalogue is loaded with the panel** (`ScenarioLoader`, third parallel request); if it fails
    the panel shows the existing "backend unavailable" state, like a failed catalogue.

## Slice deltas (step 4a)
- 02_style-selection: description-only precision in `api/openapi.yaml` — `WritingStyleSelection` (every violation
  inside `writingStyle` answers exactly "Unknown writing style", unknown inner keys are not stored) and
  `getScenarioConfiguration` (returns the newest run's configuration as stored; absent style / language = Official /
  Briefing, English). No operation, schema, property or enum value added, renamed or removed; no `Renamed:` line.
