# Contract notes — phase-01_mvp

Decisions behind `api/openapi.yaml` (naming, error model, pagination, ids, dates) and the data contracts the
specification §48 asks for. Capability specs: `scenario-panel.md` (FR-1–6, FR-34), `chatgpt-connection.md`
(FR-7–9), `generation-runs.md` (FR-10, FR-24, FR-29–32), `research-pipeline.md` (FR-11–18),
`scenario-reasoning.md` (FR-19–22), `future-result.md` (FR-23, FR-25–28), `recent-futures.md` (FR-33).

## Conventions
- Errors: every 4xx/5xx returns `#/components/schemas/ApiError` { code, message }. `code` is a stable UPPER_SNAKE
  machine code, `message` a fixed user-facing English sentence taken from the specs — never an exception message,
  provider body, stack trace, URL or token. Error responses are shared `components/responses` (BadRequest,
  ChatGptRequired, PlanNotEligible, NotFound, Conflict, InternalError).
- Tags = capabilities, one per operation: `system`, `scenario`, `chatgpt`, `runs`, `research`, `reasoning`,
  `result`, `history` → backend interfaces `SystemApi`, `ScenarioApi`, `ChatgptApi`, `RunsApi`, `ResearchApi`,
  `ReasoningApi`, `ResultApi`, `HistoryApi`; frontend services `<tag>.service.ts`. `GET /api/runs` (history) and
  `POST /api/runs` (runs) share a path but belong to different capabilities. The existing `ping` operation is kept.
- Paths: resource-oriented under `/api`; run sub-resources under `/api/runs/{runId}/…`.
- Ids: runs and Evidence Packs are UUIDs (`format: uuid`); a malformed or foreign `runId` → 404 `RUN_NOT_FOUND`
  (MethodArgumentTypeMismatchException mapped to 404, not 400, so ids of other sessions are indistinguishable from
  missing ones). Display id `generationId` = `ORC-YYYY-MM-DD-HHmm` (UTC) with `-2`, `-3` suffix on collision.
  Per-run human ids: sources `S001`, events `EV001`, Evidence IDs `E001` (pack scope, no gaps), intents `I01`,
  queries `Q01`, claims `F1`/`I1`/`P1`.
- Dates: `date-time` = ISO-8601 UTC instants (`Instant`/`timestamptz`); `date` for event dates and future dates
  (`LocalDate`).
- Pagination: none. Recent futures are capped at 20 (`maxItems: 20`); per-run lists are bounded by the pipeline
  (≤ budget × max-records sources).
- Enums: `HorizonCode` uses the profile codes `1d 1w 1m 1y 5y 10y 20y` (Java constants `_1D`… with `@JsonValue`).
  Other enums are UPPER_SNAKE.
- No schema is named `Error`; the error schema is `ApiError`.
- Session: anonymous cookie `ORACUL_SID` (HttpOnly, SameSite=Lax) created by the backend; not declared as a security
  scheme because it is not a credential and the generated clients do not need to handle it (the browser sends it).

## Validation → error mapping (`@RestControllerAdvice` in `com.oracul.app.common`)
| Source | HTTP | code | message |
|---|---|---|---|
| Bean Validation on `realism`/`darkness`/`optimism` (`@Min/@Max/@NotNull`), or a non-integer JSON value for them (fraction, string, boolean — no scalar coercion) | 400 | VALIDATION_FAILED | "`<field>` must be between 1 and 10" |
| `horizon` not in enum (case-sensitive) / missing / null / not a string | 400 | VALIDATION_FAILED | "unknown horizon" |
| wildcard id (a string) not in catalogue / duplicate (service check; elements in array order, per element id → duplicate → intensity) | 400 | VALIDATION_FAILED | "unknown wildcard: `<id>`" / "duplicate wildcard: `<id>`" |
| wildcard id missing / null / not a string | 400 | VALIDATION_FAILED | "wildcards[`<i>`].wildcardId is invalid" |
| `wildcards` missing / null / not an array / element not an object; > 30 entries is never reported alone (implies an unknown or duplicate id) | 400 | VALIDATION_FAILED | "wildcards is invalid" |
| wildcard or custom intensity (`@Min/@Max/@NotNull`) | 400 | VALIDATION_FAILED | "wildcard intensity must be between 1 and 10" |
| `customWildcards` `@Size(max=3)` | 400 | VALIDATION_FAILED | "At most 3 custom wildcards" |
| custom `label` `@Size/@Pattern/@NotNull` or blank after trim | 400 | VALIDATION_FAILED | "Wildcard name must be 1–40 characters" |
| duplicate custom label (service check) | 400 | VALIDATION_FAILED | "This wildcard already exists" |
| `output.illustration` true / `output.story` false or missing | 400 | VALIDATION_FAILED | "Illustration is not available yet (MVP+1)" / "Story output is required" |
| malformed JSON, empty body, top-level non-object, or `Content-Type` not `application/json` | 400 | VALIDATION_FAILED | "Request body is not valid JSON" |
| any other field violation not listed here (fallback) | 400 | VALIDATION_FAILED | "`<field path>` is invalid" |
| not connected | 401 | CHATGPT_NOT_CONNECTED | "Connect ChatGPT to generate" |
| expired, refresh failed | 401 | CHATGPT_SESSION_EXPIRED | "ChatGPT session expired — please reconnect" |
| plan lacks `chatgpt.tokens.use.direct` | 403 | CHATGPT_PLAN_NOT_ELIGIBLE | "Your ChatGPT plan is not eligible for ORACUL" |
| run missing / foreign / malformed id | 404 | RUN_NOT_FOUND | "Future not found" |
| second active run | 409 | RUN_ALREADY_ACTIVE | "A generation is already running" |
| alternative of a non-COMPLETED run | 409 | RUN_NOT_COMPLETED | "Only a completed future can have an alternative" |
| research / pack / scenario / result requested too early | 409 | RESEARCH_NOT_READY / EVIDENCE_PACK_NOT_READY / SCENARIO_NOT_READY / RESULT_NOT_READY | "Research has not started yet" / "The Evidence Pack is not ready yet" / "The scenario is not ready yet" / "This future is not ready yet" |
| anything unexpected | 500 | INTERNAL_ERROR | "Something went wrong — try again" (last resort only; every known path above has its own code) |

Error bodies contain exactly `code` and `message` (no Spring `timestamp/path/error/trace`). Unknown request
properties are ignored. Until slice 02 delivers the ChatGPT connection, a `startRun` body that passes validation
answers 401 `CHATGPT_NOT_CONNECTED` (FR-10 check order).
Generated Bean Validation messages are not used verbatim: the advice builds the messages above from the field path
(e.g. `customWildcards[1].label`). Only the first violation (spec precedence order in scenario-panel.md) is reported.
Run failures inside the async pipeline are not HTTP errors: they appear as `GenerationRun.status = FAILED` with
`failure {code: RunFailureCode, message}` (table in generation-runs.md).

## Browser-redirect endpoints (OAuth)
`startChatGptSignIn` and `completeChatGptSignIn` are browser navigations answering only `302` with `Location`
(generated as `ResponseEntity<Void>`); they never answer 4xx/5xx — every failure redirects to
`<frontend-base-url>/?chatgpt=not_completed|not_eligible`. The OpenAI loopback redirect URI is
`http://127.0.0.1:<port>/auth/callback` (never `localhost`); the frontend nginx and the Angular dev proxy must forward
`/auth/callback` → backend `/api/auth/chatgpt/callback` with the query string. Because the callback arrives on host
`127.0.0.1` while the app usually runs on `localhost:4200` (different cookie jar), the callback identifies the session
through the in-memory `state` entry, not the cookie, and then redirects to `oracul.frontend-base-url`.
The callback query parameters carry no `maxLength` in the contract on purpose: generated Bean Validation would turn an
oversize value into a 400, but this endpoint must always redirect. The length limits (code 4096, state 512, error 256,
error_description 2048, client_id 256) are enforced by the service and lead to `?chatgpt=not_completed`
(chatgpt-connection.md, callback rule 1).

## Data contracts required by spec §48
| §48 item | Where defined |
|---|---|
| ScenarioConfiguration | schema `ScenarioConfiguration` (+ `WildcardSetting`, `CustomWildcard`, `OutputSettings`, `HorizonCode`); rules in scenario-panel.md |
| ResearchProfile | schema `ResearchProfile` / `ResearchTopic`; construction in research-pipeline.md FR-11 |
| Event | schemas `NormalizedEvent`, `EventClassification`, `EventRanking`, `EventSelection`; research-pipeline.md FR-14–17 |
| EvidencePack | schemas `EvidencePack`, `EvidenceItem`, `Source`; rendering in research-pipeline.md FR-18 |
| StructuredScenario | schema `StructuredScenario` (+ claims, `CausalStep`, `FutureEvent`); scenario-reasoning.md |
| GenerationRun | schema `GenerationRun` (status, stage, stageIndex/stageCount, counts `ResearchCounts`, failure, suggestedRealism); generation-runs.md |
| Ranking strategy | research-pipeline.md FR-16 (factor formulas, default weights, reliability multiplier, min source quality) and FR-17 (selection, diversity caps, counter-signals) |
| Credential lifecycle | chatgpt-connection.md "Credential lifecycle" (start → callback → eligibility → use/refresh → end); runtime-only `ChatGptCredentialStore` |
| Prompt contracts | scenario-reasoning.md "Prompt contracts" (6 purposes, Closed Evidence Mode block, untrusted-data delimiters, no tools) |
| Evidence Guard | scenario-reasoning.md FR-21 (checks, violation types, actions, outcomes) — schemas `GuardReport`, `GuardViolation` |
| UI component hierarchy | below |
| Database schema | Data tables of each spec: browser_session, chatgpt_client_registration, generation_run, source, event, evidence_pack, scenario_attempt, model_call, future_story (Flyway, `ddl-auto=validate`); no credential column anywhere |

## Pipeline (modular monolith, async)
`RunsController.startRun` → `RunService` (validation, connection check, single active run, persist QUEUED) →
`PipelineExecutor` (bounded thread pool) → `ResearchPipeline` (stages 1–6) → `ReasoningPipeline` (7–9) →
`StoryWriter` (10). Each stage transition is committed before its work (poll-visible). `RunDeadlineScheduler` (every
5 s, injected `Clock`) enforces 180 s. Packages: `common`, `session`, `chatgpt`, `scenario`, `runs`, `research`,
`reasoning`, `result`, `history`.

## External endpoints (all configurable for stub servers — NFR-7)
| Property | Default | Used by |
|---|---|---|
| `oracul.chatgpt.authorize-url` | `https://auth.openai.com/api/accounts/authorize` | FR-7 |
| `oracul.chatgpt.token-url` | `https://auth.openai.com/api/accounts/oauth/token` | FR-7, refresh |
| `oracul.chatgpt.redirect-uri` | `http://127.0.0.1:4200/auth/callback` | FR-7, NFR-6 |
| `oracul.openai.responses-base-url` | `https://api.openai.com/v1` (`POST /responses`) | FR-12, 14, 15, 19–23 |
| `oracul.openai.model` | configurable, one model for all calls | all ChatGPT calls |
| `oracul.news.gdelt.base-url` | `https://api.gdeltproject.org` (`/api/v2/doc/doc`) | FR-13 |
| `oracul.frontend-base-url` | `http://localhost:4200` | post-callback redirect |
| `oracul.run.executor-threads` / `oracul.run.timeout` / `oracul.run.placeholder-stage-delay` | 4 / PT3M / PT1S | FR-10, FR-24, FR-32 |
| `oracul.run.deadline-check-interval` | PT5S (ISO-8601, fixed delay of `RunDeadlineScheduler`) | FR-32, NFR-2 (generation-runs.md "Slice 11_run-failures") |
| `oracul.openai.retry-delay` / `oracul.events.normalization-batch-size` / `oracul.events.classification-batch-size` | PT1S / 40 / 20 | FR-14, FR-15 (research-pipeline.md "Slice 06_events") |
| `oracul.events.normalization-concurrency` / `oracul.events.classification-concurrency` / `oracul.events.max-sources` | 4 / 4 / 120 | FR-14, FR-15, NFR-2 (research-pipeline.md "Slice 06_events": parallel batches, source cap) |
| `oracul.ranking.weights.*` / `oracul.ranking.min-source-quality` / `oracul.evidence.*` (max-items, core, supporting, counter-signals, max-per-entity, max-per-publisher, max-per-geography, max-category-share) | see spec / 0.30 / 25, 10, 10, 5, 2, 3, 6, 0.4 | FR-16, FR-17 (research-pipeline.md "Slice 07_evidence-pack") |

## UI component hierarchy (Angular standalone, signals, lazy routes)
```
AppComponent (mat-sidenav-container, breakpoint 768 px)
├─ HeaderComponent (mat-toolbar)                       app-header
│  ├─ wordmark "ORACUL"                                 app-wordmark
│  ├─ ScenarioDrawerToggle (mobile only)                scenario-drawer-toggle
│  ├─ RecentFuturesComponent  (history/)                recent-futures-*
│  └─ ChatGptConnectionComponent  (chatgpt/)            chatgpt-*
├─ mat-sidenav: ScenarioPanelComponent  (scenario/)    scenario-panel
│  ├─ IntensitySliderComponent ×3 (Darkness, Optimism, Realism)
│  ├─ HorizonSelectorComponent
│  ├─ WildcardCatalogueComponent → WildcardCategoryComponent → WildcardToggleComponent
│  ├─ CustomWildcardsComponent
│  └─ OutputSettingsComponent
└─ mat-sidenav-content: CenterStageComponent (routes `/`, `/futures/:runId`)
   ├─ WelcomeViewComponent + GenerateButtonComponent    welcome-view, generate-button
   ├─ ProgressViewComponent  (runs/)                    progress-*
   ├─ FutureResultComponent  (result/)                  result-view
   │  ├─ FutureStoryComponent (labels, headline, dateline, body)
   │  ├─ ScenarioMetadataComponent
   │  ├─ QuickActionsComponent (runs/)                  quick-*
   │  ├─ WhyPanelComponent · SourcesPanelComponent · WhyNewsPanelComponent
   ├─ InsufficientEvidenceComponent (runs/)             insufficient-*
   └─ RunFailureComponent (runs/)                       failure-*
Stores (signals): ScenarioStore (panel state, shared with quick actions), ConnectionStore, RunStore (polling 1 s)
```

## Analyst decisions to confirm at spec approval
1. Acceptance configuration "Viruses 8 / Robotics 6" maps to catalogue items `biology-new-pandemic` and
   `robotics-humanoid-boom` (the catalogue has items, not the category-level "Viruses"/"Robotics").
2. Story output cannot be switched off (only output in this version): API rejects `story: false`; the checkbox is
   shown checked and read-only.
3. Recent futures list only COMPLETED runs; failed/insufficient runs are not listed.
4. Duplicate custom wildcard labels are rejected ("This wildcard already exists").
5. Insufficient-evidence thresholds: realism 9–10 → 5 core events, 6–8 → 3, 1–5 → 1 (configurable).
6. Ranking weights, diversity caps, query budget 20 and GDELT timespans are defaults in configuration, not
   requirements — tests assert relative behaviour (FR-16/17 acceptance), not exact scores.
7. Prompts and model request bodies (never headers) are stored per run (`model_call`) to make FR-18/FR-19/NFR-3
   verifiable; they contain no credentials.
8. The redirect URI port defaults to the frontend port 4200 on 127.0.0.1; if OpenAI's dynamic registration only
   accepts a specific loopback port, only `oracul.chatgpt.redirect-uri` and the web-server forward change.
9. E2E stubs are wired through `docker-compose.override.yml`, which `docker compose up` merges automatically, because
   `factory-engine/bin/stack.mjs` runs plain `docker compose up`. To use the real OpenAI endpoints, run
   `docker compose -f docker-compose.yml up -d` (documented in how-to-run). Until slice 04, `startRun` with a valid body
   and a usable connection answers an interim `501` without a body; no test asserts it.
10. Slice 04 replaces that interim `501` with `202 GenerationRun`. Stages 2–10 are placeholders until slices 05–09
   deliver them; each placeholder stays current for `oracul.run.placeholder-stage-delay` (default PT1S, E2E override
   PT2S, tests PT0S/PT30S) and the placeholder pipeline ends the run COMPLETED without headline (interim terminal
   state, replaced in slice 09). Until slices 09/11/12 the run view keeps showing the progress view for terminal runs.
   `getRunResearch` returns the Research Profile from slice 04 on (`searchPlan` absent until slice 05).
11. Slice 05 makes stages 2–4 real. New settings: `oracul.openai.model` default `gpt-5`, `oracul.openai.timeout`
   PT30S, `oracul.news.query-concurrency` 4, `oracul.news.article-max-bytes` 512 KB, `oracul.news.quality.*` domain
   lists, and `oracul.run.min-stage-duration` (default PT0S, E2E PT2S) so fast real stages stay visible to the 1 s poll.
   The query-expansion call never retries and falls back to templates on any error except an expired session.
   Public GDELT DOC 2.0 asks clients to send about one request every 5 s. With 20 queries at concurrency 4, the real
   service may throttle us; throttled queries count as FAILED. This is a runtime risk, not a test concern: lower
   `oracul.research.query-budget` or `oracul.news.query-concurrency` if it appears.
12. Slice 06 (review R3, NFR-2): EVENT_NORMALIZATION / EVENT_CLASSIFICATION batches run with bounded parallelism
   (`oracul.events.*-concurrency`, default 4) instead of sequentially; results are merged in batch-index order after
   all batches finished, so events and ids do not depend on completion order. At most `oracul.events.max-sources`
   (default 120; quality desc, then recency, then id) sources are normalised; the rest stay listed with `entities`
   `[]` and never become evidence. `RunGuard` (status RUNNING and before deadline) is checked before every EVENT_*
   request and before persisting; a run past its deadline stops calling ChatGPT, writes nothing and ends RUN_TIMEOUT
   (the slice-11 scheduler uses the same code). No contract change (`api/openapi.yaml` unchanged).
13. Slice 08 (scenario-reasoning.md "Slice 08_validated-scenario"): stages 7–9 become real without the critic
   (slice 10); stage 10 stays a placeholder, so an accepted scenario still ends COMPLETED without headline. A run
   whose Evidence Pack has 0 items makes no SCENARIO_GENERATION call and completes as before (interim until slice 12
   makes it INSUFFICIENT_EVIDENCE). `StructuredScenarioRecord` gains `attempt`, `accepted` and `attempts`
   (`ScenarioAttemptSummary`, `ScenarioAttemptReason`); `getStructuredScenario` answers 200 as soon as a parsed
   attempt exists (also for SCENARIO_REJECTED runs, `accepted` false). `model_call` records SCENARIO_GENERATION bodies
   only in this slice. `HttpResponsesClient` refuses any body carrying `tools` / `tool_choice` / `web_search*`
   (IllegalStateException, no request sent). `counts.sourcesUsed` = distinct Evidence IDs cited by `factsUsed` of the
   accepted cleaned scenario.
14. Slice 09 (future-result.md "Slice 09_future-story"): stage 10 becomes real — STORY_WRITING (Closed Evidence Mode,
   single data block `structured-scenario`, strict `future_story` schema), one STORY_CORRECTION retry; a futureDate
   still outside the window after the retry falls back to the guard-checked `futureEvent.date`, any other invalid
   second answer fails INVALID_SCENARIO at stage 10. ORACUL rebuilds the dateline (`ORACUL FUTURE — Month d, yyyy`,
   English). `future_story` (Flyway V8); `getFutureResult` 200 only for COMPLETED runs with a story, otherwise 409
   RESULT_NOT_READY. Contract tightened without new operations: `FutureStory` length/pattern constraints, `labels`
   exactly 2, `WildcardDisplay.intensity` 1–10, descriptions on `getFutureResult` / `GenerationRun.headline`.
   Metadata wildcards follow configuration order (catalogue, then custom). NFR-2 budget: the E2E stack paces stages
   to ≥ 2 s for the FR-24 progress E2E, so "< 10 s with stubs" is asserted by a backend IT with pacing PT0S; the E2E
   asserts `completedAt − createdAt` < 10 s + 10 × 2 s. To confirm at approval.
15. Slice 10 (scenario-reasoning.md "Slice 10_critic"): a tool-less SCENARIO_CRITIC call (Closed Evidence Mode,
   data blocks `evidence-pack`, `custom-wildcards`, `structured-scenario`, strict `scenario_critique` schema) runs on
   every attempt whose Evidence Guard passed (stage 8 for the first, stage 9 for regenerations). FAIL → one
   CRITIC_REGENERATION with block `critique`; the regenerated attempt passes through the guard (its own single
   GUARD_REGENERATION if still unused) and the critic again. A second critic FAIL with a passing guard is accepted
   and the story is written; `GenerationRun.hasOpenCriticIssues` true and `FutureResult.openCriticIssues` = that
   report's issues, shown as `critic-issues` in the metadata panel. A guard FAIL after the critic regeneration ends
   SCENARIO_REJECTED, an unparseable regeneration INVALID_SCENARIO (the latest attempt decides; no fallback to an
   earlier attempt). A malformed critic answer is re-asked once with the identical body; still malformed → verdict
   PASS, no issues (the guard stays the hard gate). Contract: no new operation; `CriticIssue.description` 1–301 chars,
   `CriticReport.issues` ≤ 10, `attempt` ≤ 5, descriptions on `hasOpenCriticIssues`, `criticReports`,
   `openCriticIssues`. No Flyway migration (`scenario_attempt.critic_report` exists since V7); `hasOpenCriticIssues`
   is derived from the accepted attempt's critic report. To confirm at approval.

## NFR hooks
NFR-1 → chatgpt-connection.md FR-9 (no credential column, redactor, no web storage). NFR-2 → generation-runs.md FR-32
(deadline, injected Clock). NFR-3 → scenario-reasoning.md (no `tools`, client-side guard, Closed Evidence Mode block
in generation/critic/story). NFR-4 → untrusted-data delimiters in every prompt type. NFR-5 → keyboard/labels in
scenario-panel.md UI. NFR-6 → 127.0.0.1 redirect validated at startup. NFR-7 → all external URLs configurable.
