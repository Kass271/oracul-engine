# Plan — phase-01_mvp

Status: APPROVED ✔ 2026-10-02

Slices are built in this order. A slice starts only when every slice in "Depends on" is DONE.
Every FR of this phase is in exactly one slice.

Ordering principle (spec §49): reach the working vertical chain
settings → search plan → real search → sources → events → ranking → evidence pack → ChatGPT → validated scenario →
story as early as possible (slices 01–09), then harden it (critic, failures, insufficient evidence), then add the
explanation views, regeneration, history and secondary UI. Each slice is backend + frontend (where the FRs have UI) +
tests, built test-first against `api/openapi.yaml` and the specs in `02_specs/`.

Test infrastructure is delivered inside the slice that first needs it (NFR-7):
- Backend tests: stub HTTP servers (in-process) for the OpenAI auth endpoints, the Responses API and GDELT, wired
  through the `oracul.chatgpt.*`, `oracul.openai.*` and `oracul.news.gdelt.*` properties (contract-notes.md
  "External endpoints").
- E2E: one deterministic stub service (`e2e/stubs`, started by a Compose override used only for E2E) serving the
  OpenAI authorize/token/registration endpoints, `POST /v1/responses` with scripted answers per prompt purpose, and
  GDELT `/api/v2/doc/doc` with fixture articles, plus a small control endpoint so a test can select a scenario
  (e.g. 429, slow, malformed output, sparse news). Each slice extends the stub only with what its FRs need.

| Slice | FRs | Depends on | Scope |
|---|---|---|---|
| 01_scenario-controls | FR-1, FR-2, FR-3 | — | Backend: `getScenarioCatalogue` (defaults, limits, horizon options; wildcard catalogue data may be served already), `getScenarioConfiguration` returning defaults, `ApiError` advice with the darkness/optimism/realism/horizon validation messages. Frontend: AppComponent shell (header with ORACUL wordmark, sidenav Scenario Panel, center welcome "What happens next?" + GENERATE THE FUTURE button, disabled for now), three intensity sliders (8/5/5, independent), horizon selector (default 1 year), "ORACUL is unavailable — try again shortly" when the backend is down. Unit + E2E for FR-1..3. |
| 02_chatgpt-connection | FR-7, FR-8, FR-9 | 01_scenario-controls | Backend: `SessionFilter` + browser_session (ORACUL_SID cookie), `startChatGptSignIn` / `completeChatGptSignIn` (OAuth 2.0 + PKCE, dynamic client registration, persisted client_id only, 302 redirects to `?chatgpt=not_completed|not_eligible`), runtime-only `ChatGptCredentialStore`, token refresh, `getChatGptConnection` / `disconnectChatGpt`, log/error redactor ("Bearer [REDACTED]"), startup check that the redirect URI is on 127.0.0.1 (NFR-6). Infra: nginx.conf and Angular proxy.conf.json forward `/auth/callback` → backend `/api/auth/chatgpt/callback` with query string; configurable `oracul.chatgpt.authorize-url/token-url/redirect-uri`; first version of the E2E stub service (authorize auto-redirect with code+state, token and registration endpoints, scope variants for not-eligible) and the E2E Compose override. Frontend: ChatGptConnectionComponent (Continue with ChatGPT / ChatGPT connected / Plan not eligible / Session expired, Disconnect), ConnectionStore, no credential in web storage. Tests: DB/log/browser-storage scans for the stub token values (NFR-1). |
| 03_wildcards | FR-4 | 01_scenario-controls | Backend: full static wildcard catalogue in `getScenarioCatalogue`, wildcard validation messages (unknown id, duplicate, intensity range, ≤ 30). Frontend: WildcardCatalogueComponent → categories → toggles, all off by default, intensity 1–10 default 5 when enabled, "New pandemic 8/10" display; disabled wildcards are dropped from the ScenarioStore configuration. |
| 04_run-start | FR-10, FR-11, FR-24 | 02_chatgpt-connection, 03_wildcards | Backend: generation_run table, `startRun` (configuration snapshot, run id ORC-YYYY-MM-DD-HHMM, 401/409 errors incl. "A generation is already running", connection/expiry check), `getRun` (status, stage, stageIndex/stageCount, counts), async `PipelineExecutor` with the 10 stage labels committed before each stage, ResearchProfile builder (normalised 0–1 settings and topic weights) stored with the run; remaining stages are no-op placeholders that later slices replace. Frontend: GenerateButtonComponent enabled only when connected ("Connect ChatGPT to generate"), RunStore polling 1 s, ProgressViewComponent showing only stage labels (no URLs/JSON/stack traces), route `/futures/:runId`. |
| 05_search-sources | FR-12, FR-13 | 04_run-start | Backend: SearchPlanner (intents per enabled wildcard, budget split 8/6/4/2 of 20, redistribution with no wildcards), tool-less Responses API query-expansion call with template fallback, GDELT DOC 2.0 provider behind a `NewsProvider` interface (configurable `oracul.news.gdelt.base-url`), metadata fetch with short timeout keeping provider metadata on failure, source table with quality score, run counts searches/articlesConsidered, NEWS_UNAVAILABLE failure when every query fails (no generation call), `listRunSources`, `getRunResearch` (plan part). Infra: configurable `oracul.openai.responses-base-url` / `oracul.openai.model`; backend stub servers for Responses and GDELT; E2E stub extended with GDELT fixtures and the scripted query-expansion answer. |
| 06_events | FR-14, FR-15 | 05_search-sources | Backend: event normalisation and deduplication (multi-source events, disagreement stated in summary), semantic classification via tool-less Responses call (scores in range, one retry on malformed output then exclusion with reason), event table, `listRunEvents`, uniqueEvents count. Stub: scripted normalisation/classification answers incl. vaccine-breakthrough and malformed cases. |
| 07_evidence-pack | FR-16, FR-17, FR-18 | 06_events | Backend: configurable ranking weights and factors with reliability as a separate multiplier, selection of ≤ 25 events (core/supporting/counter-signals, diversity cap 2 per entity cluster), stable Evidence IDs E001…, evidence_pack table, `getEvidencePack` returning exactly the content later sent to the generation prompt, evidenceUsed/counter-signal counts. |
| 08_validated-scenario | FR-19, FR-20, FR-21 | 07_evidence-pack | Backend: generation prompt builder (spec §29 stable instructions, Closed Evidence Mode block, untrusted-data delimiters, no `tools`), model_call recording of prompts/request bodies (never headers), structured scenario JSON-schema parsing with one correction request then INVALID_SCENARIO failure, Evidence Guard (unknown/missing Evidence IDs, present-day claims without evidence, causal-chain references; remove / regenerate once / reject) with GuardReport, scenario_attempt table, `getStructuredScenario`. Stub: scripted structured scenarios incl. invalid schema, E099 citation and injection payload in a source. |
| 09_future-story | FR-23, FR-25 | 08_validated-scenario | Backend: story-writing call (Closed Evidence Mode, dateline within the horizon, never today), future_story table, run COMPLETED, `getFutureResult` with settings and counts. Frontend: FutureResultComponent with "AI-GENERATED FUTURE SCENARIO" and "POSSIBLE FUTURE — NOT CURRENT NEWS" labels above headline/dateline/body, ScenarioMetadataComponent ("Realism 8/10", "Darkness 9/10", "Optimism 2/10", "Horizon 5 years", wildcards with intensities, three counts). First full E2E: connect → configure → generate → story under 10 s with stubs (NFR-2), NFR-3 request assertions over the whole run. |
| 10_critic | FR-22 | 09_future-story | Backend: critic call between guard and story (tool-less, Closed Evidence Mode), one regeneration with the critique attached on failure, second failure → show scenario with open critic issues if the guard passed, otherwise fail with a clear message; result exposes open issues. Frontend: open-issues note on the result. Stub: scripted critic pass/fail sequences. |
| 11_run-failures | FR-32 | 09_future-story | Backend: RunFailureCode mapping (ChatGPT 429 "ChatGPT plan limit reached — try again later", provider errors, invalid output, news unavailable), `RunDeadlineScheduler` with injected Clock (180 s → "Generation took too long — try again"), no 500 bodies with internals. Frontend: RunFailureComponent with friendly message and "Try again", panel stays usable. Stub: 429 and slow scenarios via the control endpoint. |
| 12_insufficient-evidence | FR-31 | 09_future-story | Backend: realism-dependent core-evidence thresholds (configurable), run status INSUFFICIENT_EVIDENCE with suggestedRealism and no story call. Frontend: InsufficientEvidenceComponent with "ORACUL found insufficient current evidence to construct this scenario at Realism 10." and LOWER REALISM (−2, new run). Stub: sparse-news scenario. |
| 13_why-and-sources | FR-26, FR-27 | 09_future-story | Frontend (data from `getFutureResult` / `getEvidencePack` / `listRunSources`, backend additions only if the result lacks fields): WhyPanelComponent with FACT/INFERENCE/SPECULATION/ORACUL FUTURE chain and clickable Evidence IDs that reveal the source entry; SourcesPanelComponent listing every evidence item with ID, title, publisher, date, external link in a new tab, "used in scenario" and "counter-signal" marks. |
| 14_why-these-news | FR-28 | 09_future-story | Backend: `getRunResearch` intent attribution to controls (e.g. "New pandemic 8/10", "Darkness 9/10") and the six research-summary numbers. Frontend: WhyNewsPanelComponent. |
| 15_recent-futures | FR-33 | 09_future-story | Backend: `listRecentRuns` (20 newest COMPLETED runs of the session, newest first), session isolation (other session's run → 404 RUN_NOT_FOUND), `getScenarioConfiguration` returns the newest run's configuration. Frontend: RecentFuturesComponent in the header, reopening `/futures/:runId` shows that run's story, metadata, WHY, SOURCES and WHY THESE NEWS. |
| 16_quick-regeneration | FR-29 | 15_recent-futures | Frontend: QuickActionsComponent MORE REALISTIC (+2 Realism), DARKER (+2 Darkness), MORE OPTIMISTIC (+2 Optimism), MORE EXTREME (−3 Realism), clamped 1–10 with disabled state at the bound, updating the ScenarioStore panel and starting a new run via `startRun`; previous result stays in Recent futures. |
| 17_alternative-future | FR-30 | 09_future-story | Backend: `startAlternativeRun` reusing the same Evidence Pack id (no new search), prompt lists previous futureEvent(s) to avoid, alternative-specific prompt purpose. Frontend: ALTERNATIVE FUTURE action. Stub: scripted alternative scenario with a different futureEvent and causal step. |
| 18_custom-wildcards-output | FR-5, FR-6 | 03_wildcards, 05_search-sources | Backend: custom wildcard validation ("Wildcard name must be 1–40 characters", "At most 3 custom wildcards", "This wildcard already exists"), output validation (story must be true, illustration must be false), custom wildcards included as ResearchProfile topics and search intents. Frontend: CustomWildcardsComponent (add/remove, default intensity 5), OutputSettingsComponent (Story checked read-only, Illustration unchecked, disabled, "MVP+1"). |
| 19_mobile-layout | FR-34 | 01_scenario-controls | Frontend: breakpoint 768 px — Scenario Panel as a drawer opened by the "Scenario" header button, center fills width without horizontal scroll at 390 px, drawer state preserved in the ScenarioStore. E2E at 390 px viewport. |

## Dependency graph

```
01_scenario-controls ──► 02_chatgpt-connection ──┐
01_scenario-controls ──► 03_wildcards ───────────┼──► 04_run-start ──► 05_search-sources ──► 06_events
01_scenario-controls ──► 19_mobile-layout        │
06_events ──► 07_evidence-pack ──► 08_validated-scenario ──► 09_future-story
09_future-story ──► 10_critic
09_future-story ──► 11_run-failures
09_future-story ──► 12_insufficient-evidence
09_future-story ──► 13_why-and-sources
09_future-story ──► 14_why-these-news
09_future-story ──► 15_recent-futures ──► 16_quick-regeneration
09_future-story ──► 17_alternative-future
03_wildcards + 05_search-sources ──► 18_custom-wildcards-output
```

## Risks
- Sign in with ChatGPT (dynamic client registration, loopback port, plan-usage scopes) is only verified against the
  stub in automated tests; a manual check with a real ChatGPT plan is needed before release (contract-notes.md
  decision 8 — only `oracul.chatgpt.redirect-uri` and the web-server forward change if the port differs).
- Cookie jar mismatch between `localhost:4200` and the `127.0.0.1` callback: the callback resolves the session via the
  in-memory `state` entry; 02 must cover this in E2E.
- Slices 05–08 depend on scripted model answers; the stub's prompt-purpose routing must stay in sync with the prompt
  contracts in scenario-reasoning.md, or tests become brittle.
- Real GDELT rate limits and latency may push real runs near the 180 s budget (NFR-2); query budget and timeouts are
  configuration, tuned after 05.
- Slice 04 introduces placeholder stages that 05–09 replace; each later slice must keep the stage order and labels of
  FR-24 unchanged.
- Ranking/selection (07) asserts relative behaviour only (contract-notes.md decision 6); exact weights may need tuning
  once real news is used.
