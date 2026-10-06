# Plan — phase-03_wildcard-search

Status: APPROVED ✔ 2026-10-06

Slices are built in this order. A slice starts only when every slice in "Depends on" is DONE.
Every FR of this phase is in exactly one slice.

Ordering principle: the research stage is replaced stage by stage, in the order the data flows (search → queries →
selection → pack → prompt → article text → result views). Each slice swaps one stage, so after every slice a run still
goes from start to a COMPLETED story. Stages that are not yet replaced keep their phase-01/02 code until their slice.
The first slice clears the ground. It removes GDELT and moves the test harness to Google-RSS fixtures, so later slices
change behaviour on the harness they keep. Each slice is backend + frontend (where its FRs have UI) + tests, built
test-first against `api/openapi.yaml` 0.7.0 and the specs in `02_specs/` (`wildcard-search.md`,
`article-retrieval.md`, `wildcard-evidence.md`, `wildcard-result-views.md`, `contract-notes.md`). Automated tests stay
stubbed (phase-01 NFR-7). NFR-10 is delivered in three parts: the search window in 03, the query-generation window in
04, and the stage budget plus the 3-wildcard E2E in 08. NFR-11 is the gate after release.

| Slice | FRs | Depends on | Scope |
|---|---|---|---|
| 01_gdelt-removal | FR-49 | — | Backend: delete the GDELT client, provider, DTOs, parser, `GdeltQueryGroups`, the fallback path and every `oracul.news.gdelt.*` / removed `oracul.news.*` property (`request-spacing`, `query-timeout`, `rate-limit-wait`, `max-requests`, `max-records-per-query`, `provider`). `NewsProvider` keeps one implementation: the existing Google News RSS search (FR-48 shape, still transitional until 03). A failed Google group is FAILED with no fallback (the FR-47 note covers it). A configuration that still sets a removed property starts normally. Plain backend scan test over the FR-49 scan scope. Harness: rename `StubGdelt` → `StubNews`, field `gdelt` → `news`, `gdeltArticles`/`gdeltF240` → `newsArticles`/`newsF240`. The fixtures are served through the in-process `/rss/search` (the existing RSS F240 responder of `GoogleNewsRunIT` is the model), so the call sites of the ~45 fixture-driven ITs keep their assertions. Infra/stub: the E2E stub drops `/api/v2/doc/doc`, `POST /__control/news` and request kind `gdelt`. `docker-compose.e2e.yml` drops `ORACUL_NEWS_GDELT_BASE_URL`, `ORACUL_NEWS_REQUEST_SPACING`, `ORACUL_NEWS_RATE_LIMIT_WAIT`. README: the GDELT section is removed. |
| 02_safe-fetching | FR-56 | 01_gdelt-removal | Backend: `SafeFetcher` (http/https only; refuses loopback, any-local, private, link-local and unique-local addresses incl. IPv4-mapped IPv6, checked on every redirect hop and connected to the checked address only; ≤ 5 redirects; body ≤ 2 MB; one timeout per fetch). It is wired into the existing article metadata fetch, which fixes the phase-01 low finding "SSRF via article redirects" right away; FR-54 reuses it in 08. Config `oracul.news.fetch.allowed-private-hosts` (exact host names), `article-max-bytes` 2 MB, `article-max-redirects` 5. The backend test base registers `127.0.0.1`. `docker-compose.e2e.yml` sets `ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub`; real mode sets none. Unit tests with an injected resolver cover the FR-56 address/scheme/redirect/size classes. No UI. |
| 03_parallel-search | FR-52 | 01_gdelt-removal | Backend: `GoogleNewsSearch` behind `NewsSearchProvider`. Every planned query is one `GET <google>/rss/search` (`q` = text + ` when:<N>d`, `hl`/`gl`/`ceid`), with no OR-groups and no spacing. Requests run on virtual threads under an 8-permit semaphore. A 429 is retried once after `rate-limit-wait` (2 s). OK/EMPTY/FAILED per query plus item count. A join: no selection or article request starts before the last search has ended. The guard is checked before every request and retry (STOP, deadline). This slice still runs on the phase-01 plan (QUERY_EXPANSION queries, statuses in `queries[]`). Each item is attributed to the query whose request returned it, replacing title attribution and preparing FR-50 acceptance 5. NFR-10 part 1: `SearchBudget(Clock, t0, windows, deadlineAt)` and `oracul.search.search-window` (PT60S) replace `oracul.news.search-budget`. Unsent or cut-off queries are FAILED, never EMPTY. The in-process `StubNews` gains per-request arrival/finish nanos, max-open tracking, 429-once and slow responders. `docker-compose.e2e.yml`: `ORACUL_NEWS_GOOGLE_RATE_LIMIT_WAIT: PT0.2S`, drop `ORACUL_NEWS_GOOGLE_REQUEST_SPACING`. Resolves release finding R1. No UI. |
| 04_wildcard-queries | FR-50, FR-51 | 03_parallel-search | Backend: `SearchPlanner` builds one pipeline per enabled catalogue or custom wildcard in profile order (`W01…`, kind, label, level, topicKey, heading), or one GENERAL pipeline. 3 queries each (2 when ≥ 9 wildcards), ids `Q01…`. `buckets`/`intents`/`queries` are `[]` for new runs; `queryBudget` = Σ queries. `QueryGenerator` makes one tool-less QUERY_GENERATION Responses call per pipeline, ≤ 4 in parallel, with the fixed `QueryGenerationPrompt` and the strict `query_generation` schema. Post-processing applies rule Q, dedup and template fill. A failed or unusable call falls back to templates for that pipeline only. Session/registration/eligibility failures still end the run before any Google request. `QueryTemplates.forPipeline` covers the band × direction vocabulary and the N/P term rules. NFR-10 part 2: `oracul.search.query-generation-window` (PT30S) plus the startup validation of the three windows. SEARCHING reads `pipelines[].queries[]` and commits statuses there. `Source.pipelineIds` comes from the queries that found the source (FR-50 acceptance 5). This needs the first Flyway migration of the phase (`source.pipeline_ids`). QUERY_EXPANSION and `oracul.research.query-budget` are no longer used by new runs. E2E stub: answers QUERY_GENERATION (one per pipeline) instead of QUERY_EXPANSION. UI: none. The legacy WHY THESE NEWS? panel now shows no intents for new runs, and its grouped replacement comes in 09. |
| 05_wildcard-selection | FR-53 | 04_wildcard-queries | Backend: the pure `WildcardSelector` replaces `SourceCap`/FR-46 topic round robin. Per pipeline: candidates of its OK queries, the basic filter, in-pipeline dedup (H, best feed position), the deterministic relevance score (label terms × 3, query terms × 1, +2 per extra query) with its tie order, and the top 4. A shared article is one source with every finding pipeline in `pipelineIds` and all its `queryIds`. Cap 30 by round robin over pipelines. Groups go per pipeline. Sources are numbered `S00k` in Evidence order. `articlesConsidered`, `sourcesKept`, and per pipeline `candidatesConsidered`/`sourceIds` are written in one guarded commit. A source's `topic` is the `topicKey` of its first pipeline, so the event stages (CONNECTING_SIGNALS, RANKING, decision 1) keep working on the kept sources. The article metadata fetch (via SafeFetcher) runs for kept sources only. The FR-53 selection has no counter-signal quota. The phase-01 event-level `EvidenceSelector` still feeds the old pack until 06. Harness: the F240 helper enables ≥ 9 wildcards, so event tests that need 30 sources still get them (9 × 2 queries = the old budget 18). No UI. |
| 06_wildcard-pack | FR-57 | 05_wildcard-selection | Backend: `WildcardPackRenderer` builds `promptText` from the scenario parameters, the WILDCARDS line and one `Wildcard: <label> <level>/10` section per pipeline. Each item has `[E00k]` title · publisher · date · url, then `Excerpt:` lines or `Content not retrieved. Snippet: …`. An empty section reads `no current sources found`. Untrusted text is sanitised. `EvidencePack.wildcardSections`. New packs have empty `core`/`supporting`/`counterSignals`. `eventsSelected` = distinct Evidence IDs and `counterSignals` = 0. Evidence IDs match source numbers, and a shared source has the same ID in every section. The Evidence Guard takes known IDs from the section items. The flat `FutureResult.sources` are CORE with `counterSignal` false. `EvidenceSelector` is retired for new runs, and events get no `selection`. Flyway: `evidence_pack.sections`. Until 08 every item has no fragments, so the pack shows the snippet form. ALTERNATIVE runs reuse the parent pack. No UI. |
| 07_starting-conditions | FR-58 | 06_wildcard-pack | Backend: `StartingConditions.INSTRUCTIONS` replaces the Closed Evidence Mode block in SCENARIO_GENERATION (block + G, new first TASK line, "leave counterSignalsConsidered empty") and SCENARIO_CRITIC (block + K, reworded types, extrapolation matching the parameters is never an issue). `CriticParser` and the critic schema accept exactly six issue types, without IGNORED_COUNTER_SIGNALS. STORY_WRITING keeps `ClosedEvidenceMode`. The Evidence Guard and the tool guard are unchanged. Injection classes for title, fragment, snippet and custom label stay inside the data blocks. E2E stub: critic answers without IGNORED_COUNTER_SIGNALS. No UI. |
| 08_article-text | FR-54, FR-55, FR-61 | 02_safe-fetching, 06_wildcard-pack | Backend: `ArticleRetriever` on virtual threads under one 8-permit semaphore. For a Google link: fetch the Google page through SafeFetcher (same host only) and read `data-n-a-id`/`-ts`/`-sg` with jsoup. `ArticleUrlDecoder` then POSTs the fixed `f.req` to `oracul.news.google.decode-url` and takes the first http(s) URL, never a Google host. The publisher page is fetched through SafeFetcher with an 8 s timeout and must be html/xhtml/text. Source fields per outcome: `url`, `publisherHost`, `contentStatus` RETRIEVED / DECODE_FAILED / PAGE_FAILED / NO_TEXT / REFUSED / NOT_ATTEMPTED, `summary`, `metadataFetched`. Two Google links of one article are merged before numbering. `sourcesWithContent`. Pure `FragmentExtractor` takes ≤ 3 fragments, ≤ 1,200 characters per source and pipeline, by match strength. It strips script/style/nav/header/footer/aside/form and falls back to the first paragraph of ≥ 80 characters. No ChatGPT call. Flyway: `source.content_status`, `excerpts`, `publisher_host`. The pack (06) now shows `Excerpt:` lines. NFR-10 part 3: `oracul.search.stage-budget` (PT90S). Unfinished retrievals are NOT_ATTEMPTED. E2E: 3 wildcards, run < 10 s plus stage pacing. Stubs (FR-61): the E2E stub and `StubNews` answer like real Google. OR of multi-word or quoted elements, or parentheses, returns 0 items. Article link → 302 → Google page with the three attributes → batchexecute decode → publisher page with nav, script, style and 6 paragraphs. `POST /__control/google` modes `ok`, `empty`, `empty-for`, `down`, `malformed`, `rate-limited-once`, `slow`, `decode-fail`, `decode-google-host`, `publisher-fail`, `publisher-timeout`. `/__control/rss` is removed. `docker-compose.e2e.yml`: `ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT: PT3S`. Resolves release findings R2 and R3. No UI. |
| 09_wildcard-results | FR-59, FR-60 | 08_article-text | Backend: `EvidenceNotes.decide` with the FR-59 decision table. A pack with total 0 gives NO_EVIDENCE (unchanged text). Below the realism threshold gives INSUFFICIENT_EVIDENCE, prefixed by the missing sentence. Otherwise, missing wildcards give the new kind `MISSING_WILDCARD_SOURCES` with "No current sources found for: <labels>. This part of the future is speculative." `EvidenceNote.wildcardsWithoutSources` and the message limit of 2000. Flyway: `generation_run.evidence_note_wildcards`. ALTERNATIVE runs copy the note. `FutureResult.wildcardGroups` (pipeline, heading, queries with status/count, section items with excerpt flags and `usedInScenario`). Frontend (`src/app/result/`): grouped SOURCES (`source-group-*`, `source-item-<p>-<e>`, excerpt fragments or `content not retrieved`, external link in a new tab, `sources-empty` when every group is empty). Grouped WHY THESE NEWS? (`why-news-group-*`, `why-news-query-*` with OK/EMPTY/FAILED/PENDING and item count, the five summary lines `summary-searches/-articles/-kept/-content/-used`). WHY-chip highlight across groups. Legacy results (no `wildcardGroups`) keep the phase-01 layout. The run view shows the note through the existing `evidence-note` / `lower-realism` elements. E2E: the user-visible FR-59 / FR-60 cases on the stubbed stack. |

## Dependency graph

```
01_gdelt-removal ─┬─► 02_safe-fetching ───────────────────────────────────────────────────────────────┐
                  │                                                                                    ▼
                  └─► 03_parallel-search ─► 04_wildcard-queries ─► 05_wildcard-selection ─► 06_wildcard-pack ─┬─► 08_article-text ─► 09_wildcard-results ─► [release] ─► [GATE: NFR-11 real check]
                                                                                                             └─► 07_starting-conditions
```
Build order is the table order (01 → 09). 07 and 08 are independent of each other.

## Fallout on older tests: where it lands

The core research pipeline is replaced, so many phase-01/02 tests are superseded. Each superseded test is rewritten or
deleted in exactly one slice: the slice whose FR corrects the behaviour it asserts. No slice repairs another slice's
fallout. The lists below come from a grep of `backend/src/test`, `e2e/tests` and `frontend/src/**/*.spec.ts`
(2026-10-06). They are a planning estimate. The step-4a spec delta of each slice names the exact files (`Changes earlier
behaviour … (tests: …)`).

| Slice | Assertion rewrites / deletions (estimate) | Mechanical only (no assertion change) |
|---|---|---|
| `01_gdelt-removal` | **Delete** (GDELT-only FR-44 tests): `NewsSearchGroupingIT`, `NewsSearchGroupingMax0IT`, `NewsSearchGroupingMax1IT`, `NewsSearchGroupingMax10IT`, `NewsSearchGroupingMaxMinus1IT`, `NewsSearchTimingIT`, `NewsSearchAttributionIT`, `NewsSearchBudgetIT`, `NewsSearchRunIT`, `NewsSearchDeadlineIT`. **Edit** (GDELT fallback cases): `GoogleNewsSearchIT`, `GoogleNewsTimingIT`, `GoogleNewsBudgetIT`, `NewsSearchNoBudgetIT`, `StopRunIT`; E2E `search-sources`, `run-control`, `alternative-future`, `insufficient-evidence`, `run-modes` | rename `StubGdelt` → `StubNews` and the `gdelt*` helpers in ~45 backend ITs and base classes. Remove the unasserted `POST /__control/news` setup line in ~13 E2E specs |
| `02_safe-fetching` | `SourceMetadataIT` (3 → 5 redirects, 512 KB → 2 MB), the redirect case of `GoogleNewsSourceIT` | test base property `allowed-private-hosts` (`AbstractRunIT`) |
| `03_parallel-search` | `GoogleNewsSearchIT`, `GoogleNewsTimingIT`, `GoogleNewsBudgetIT`, `GoogleNewsRunIT`, `GoogleNewsSourceIT` (attribution), `SourceQueryTimeoutIT`, `StopRunIT` (GOOGLE gate), `AbstractNewsSearchIT`; E2E `search-sources` (request shape) | F240 helper: per OR element → per query (`AbstractEventIT`, `F240Support`) |
| `04_wildcard-queries` | `SearchPlannerTest`, `QueryTemplatesTest`, `ResearchPlanIT`, `ResearchPlanPendingIT`, `ResearchPlanTimeoutIT`, `CustomWildcardRunIT`, `FutureResultIT` (intents), `PlanSupport`, `StubResponses`; E2E `events`, `search-sources`, `why-these-news` (intents) | purpose `QUERY_EXPANSION` → `QUERY_GENERATION` in the gating tests `ModelResolutionIT`, `PlanUsageTransportIT`, `StreamTimeoutIT`, `SourceRetrievalIT`, `RunDeadlineIT`, `RunDeadlineQueuedIT`, `RunStartupSweepIT`, `StopQueuedRunIT`, `StoppedRunDeadlineIT`, `AbstractEventIT` |
| `05_wildcard-selection` | `SourceCapIT`, `CapOracle`, `EventSourceCapIT`, `SourceFixtureIT`, `SourceFilteringIT`, `SourceRetrievalIT`, `GoogleNewsRunIT` (cap); E2E `search-sources` (source count/order) | F240 profile with ≥ 9 wildcards (`AbstractEventIT`) |
| `06_wildcard-pack` | `EvidencePackIT`, `EvidencePackAtomicIT`, `EvidencePackRendererTest`, `EvidenceSelectorTest` (delete), `RankingSelectionIT`, `RankingHarness`, `EvidenceConfigurationTest`, `AbstractEvidenceIT`, `FutureResultIT` (flat sources); E2E `evidence-pack` | — |
| `07_starting-conditions` | `ScenarioGenerationPromptTest`, `ScenarioCriticPromptTest`, `ScenarioFixtures`, `CriticFixtures`, `ReasoningHarness`, `CriticParserTest`, `CriticIT`; E2E `critic`, `future-story` (instruction check) | — |
| `08_article-text` | `GoogleNewsSourceIT` (R2: redirect-resolved → decode), `SourceMetadataIT` (summary/metadataFetched), `StopRunIT` (ARTICLE gate); E2E `search-sources`, `run-control`, `insufficient-evidence` (`/__control/rss` → `/__control/google`) | — |
| `09_wildcard-results` | `InsufficientEvidenceIT`, `EvidenceNoteNoThresholdIT`, `SpeculativeScenarioIT`, `FutureResultIT` (wildcardGroups); E2E `insufficient-evidence`, `why-these-news`, `why-and-sources` | frontend legacy specs (`sources-panel.spec.ts`, `why-news-panel.spec.ts`) stay green unchanged, because the legacy layout is kept |

Expected step-4a SLICE SIZE warnings (more than 10 older tests) and why continuing is reasonable:
- **01_gdelt-removal.** About 10 of its files are pure deletions of GDELT-only tests, and about 45 more need only a
  mechanical rename that the FR-49 scan forces. A rename of a shared helper class cannot be split across slices.
  Suggest continuing, with the rename as its own RED commit.
- **04_wildcard-queries.** About 9 rewrites plus about 10 one-string purpose renames in gating tests. Suggest
  continuing. The tester routes the gating purpose through one `StubResponses` constant, so a later rename is one line.
- E2E `search-sources.spec.ts` is touched by 01, 03, 04, 05 and 08, each time for that slice's own behaviour. The tester
  of 01 may split it by concern (provider / request shape / sources) to keep later diffs small.

## Gates and open items
- **After release — NFR-11 real check (manual, user).** Real mode (`docker compose up -d`, real ChatGPT account, real
  Google News) is run twice. First run: New pandemic 1 + custom `AI takeover` 5, Darkness 5. Second run: New pandemic
  10 + custom `AI takeover` 5, Darkness 5. Pass criteria:
  - every pipeline has ≥ 1 source with `contentStatus` RETRIEVED
  - the level-1 and level-10 New pandemic queries differ visibly
  - the story completes
  - `docker compose logs backend | grep -ci gdelt` = 0

  Evidence goes in `05_release/real-check.md`. The phase is GREEN only after this check.
- **Optional, recommended — early real smoke after 08_article-text (user decides).** The decode call is undocumented
  (contract-notes decision 11). One real run before 09 shows early whether Google still answers the `f.req` shape. If
  it does not, sources degrade to "content not retrieved" and runs still complete. This is not a blocking gate.
- **Spec decisions 1–19 in `contract-notes.md`** are approved with the specs. This plan relies on decision 1 (event
  stages stay), decision 2 (FR-17 superseded as a whole, finished in 06) and decision 15 (legacy layout for old
  results).

## Risks
- **Transitional states between slices.**
  - 01–02 still send the FR-48 OR-group Google requests, without fallback.
  - 03 searches the phase-01 plan.
  - 05 keeps the event-level `EvidenceSelector` for the old pack until 06.
  - 06–07 render every pack item in the snippet form until 08.
  - 04–08 show new runs in the legacy result layout with empty intents until 09.

  Each state completes runs. The tests of a slice must not assert on a transitional detail that a later slice is
  specified to change.
- **One Flyway file per slice.** `contract-notes.md` names one `V11__wildcard_search.sql`. Because the DDL lands in
  04, 06, 08 and 09, each of those slices adds its own migration (V11, V12, …) and never edits one already applied.
  The step-4a delta of 04 should record this.
- **Fixture harness drift.** Up to 05, many ITs get their articles from the `newsArticles`/F240 helpers. If a helper
  changes meaning (per query in 03, ≥ 9 wildcards in 05), every caller is affected. Helpers change only in the slice
  that owns the behaviour (table above). Callers whose assertions really change are listed in that slice's delta.
- **Undocumented Google decode** (`ArticleUrlDecoder`, decision 11) can break without notice. The stubs mirror the
  2026-10-06 shape, so only the real check (NFR-11, or the optional early smoke) catches drift.
- **Load on the user's ChatGPT plan.** With 33 wildcards, QUERY_GENERATION costs up to 33 calls per run
  (≤ 4 in parallel, decision 7). The extra event-stage calls of decision 1 also count toward the 90 s / 180 s
  budgets. The NFR-10 tests use an injected clock and short windows.
- **Enum addition `EvidenceNoteKind.MISSING_WILDCARD_SOURCES`** (09) breaks exhaustive switches in backend
  `EvidenceNotes` / `RunService`. The frontend shows the message as text.
- **SSRF exemption** (`stub`, `127.0.0.1`) applies to exact host names only. A wrong compose value would make every
  E2E source REFUSED (visible as "content not retrieved" in 08's E2E).
