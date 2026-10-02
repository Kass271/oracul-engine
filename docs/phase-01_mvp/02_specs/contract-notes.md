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
| Bean Validation on `realism`/`darkness`/`optimism` (`@Min/@Max/@NotNull`) | 400 | VALIDATION_FAILED | "`<field>` must be between 1 and 10" |
| `horizon` not in enum / missing (HttpMessageNotReadable on HorizonCode, or null) | 400 | VALIDATION_FAILED | "unknown horizon" |
| wildcard id not in catalogue / duplicate (service check) | 400 | VALIDATION_FAILED | "unknown wildcard: `<id>`" / "duplicate wildcard: `<id>`" |
| wildcard or custom intensity (`@Min/@Max/@NotNull`) | 400 | VALIDATION_FAILED | "wildcard intensity must be between 1 and 10" |
| `customWildcards` `@Size(max=3)` | 400 | VALIDATION_FAILED | "At most 3 custom wildcards" |
| custom `label` `@Size/@Pattern/@NotNull` or blank after trim | 400 | VALIDATION_FAILED | "Wildcard name must be 1–40 characters" |
| duplicate custom label (service check) | 400 | VALIDATION_FAILED | "This wildcard already exists" |
| `output.illustration` true / `output.story` false or missing | 400 | VALIDATION_FAILED | "Illustration is not available yet (MVP+1)" / "Story output is required" |
| malformed JSON body | 400 | VALIDATION_FAILED | "Request body is not valid JSON" |
| not connected | 401 | CHATGPT_NOT_CONNECTED | "Connect ChatGPT to generate" |
| expired, refresh failed | 401 | CHATGPT_SESSION_EXPIRED | "ChatGPT session expired — please reconnect" |
| plan lacks `chatgpt.tokens.use.direct` | 403 | CHATGPT_PLAN_NOT_ELIGIBLE | "Your ChatGPT plan is not eligible for ORACUL" |
| run missing / foreign / malformed id | 404 | RUN_NOT_FOUND | "Future not found" |
| second active run | 409 | RUN_ALREADY_ACTIVE | "A generation is already running" |
| alternative of a non-COMPLETED run | 409 | RUN_NOT_COMPLETED | "Only a completed future can have an alternative" |
| research / pack / scenario / result requested too early | 409 | RESEARCH_NOT_READY / EVIDENCE_PACK_NOT_READY / SCENARIO_NOT_READY / RESULT_NOT_READY | "Research has not started yet" / "The Evidence Pack is not ready yet" / "The scenario is not ready yet" / "This future is not ready yet" |
| anything unexpected | 500 | INTERNAL_ERROR | "Something went wrong — try again" (last resort only; every known path above has its own code) |

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

## NFR hooks
NFR-1 → chatgpt-connection.md FR-9 (no credential column, redactor, no web storage). NFR-2 → generation-runs.md FR-32
(deadline, injected Clock). NFR-3 → scenario-reasoning.md (no `tools`, client-side guard, Closed Evidence Mode block
in generation/critic/story). NFR-4 → untrusted-data delimiters in every prompt type. NFR-5 → keyboard/labels in
scenario-panel.md UI. NFR-6 → 127.0.0.1 redirect validated at startup. NFR-7 → all external URLs configurable.
