# Spec — Research pipeline (profile → search → events → ranking → Evidence Pack)

Covers: FR-11, FR-12, FR-13, FR-14, FR-15, FR-16, FR-17, FR-18

## Purpose
ORACUL searches, ChatGPT reasons. This capability turns the scenario configuration into research: a Research
Profile, a search plan, real current-news retrieval from GDELT DOC 2.0, normalized and deduplicated events with
semantic classification, scenario-aware ranking that never lets preference override reliability, a diverse
selection with counter-signals, and finally the Evidence Pack — the only current-world context ChatGPT ever sees.
Runs stages 1–6 of generation-runs.md. All ChatGPT calls here are tool-less Responses API calls whose source text is
untrusted data (prompt contracts in scenario-reasoning.md).

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| ResearchProfile (run.research_profile jsonb) | darkness / optimism / realism | double | value / 10, exactly one decimal (9 → 0.9) |
| ResearchProfile | horizon | HorizonCode | copied |
| ResearchProfile | topics[] | ResearchTopic | one per enabled catalogue wildcard (key = wildcard id, category = its category) then one per custom wildcard (key `custom-<n>` 1-based in panel order, category `custom`); weight = intensity / 10 |
| SearchPlan (run.search_plan jsonb) | queryBudget | int | `oracul.research.query-budget`, default 20 |
| SearchPlan | buckets[] | BucketAllocation | WILDCARD 0.40 · MAJOR 0.30 · ADJACENT 0.20 · UNEXPECTED 0.10 (configurable) |
| SearchPlan | intents[] / queries[] | SearchIntent / SearchQuery | ids `I01…`, `Q01…` |
| SearchPlan | expansionMode | MODEL / TEMPLATE_FALLBACK | |
| source | id | varchar | `S001…` per run, in retrieval order after filtering |
| source | run_id, url, publisher, title, published_at, retrieved_at, summary, topic, entities (jsonb), source_type, source_quality, metadata_fetched, language, query_ids (jsonb) | | url unique per run; published_at null allowed only if the provider gave none |
| event | id | varchar | `EV001…` per run |
| event | run_id, date, category, entities, summary, disagreement, source_ids (jsonb), confidence, classification (jsonb), ranking (jsonb), selection_section, evidence_id, excluded_reason | | |
| evidence_pack | id | uuid | |
| evidence_pack | run_id, generation_id, cutoff, configuration (jsonb), profile (jsonb), items (jsonb: core/supporting/counterSignals), source_ids (jsonb), prompt_text (text), created_at | | immutable after creation; shared read-only by ALTERNATIVE runs |

### Configuration (tests use stubs — NFR-7)
| Property | Default |
|---|---|
| `oracul.news.provider` | `gdelt` (interface `NewsProvider`; one implementation in MVP) |
| `oracul.news.gdelt.base-url` | `https://api.gdeltproject.org` |
| `oracul.news.max-records-per-query` | 25 |
| `oracul.news.query-timeout` | PT10S |
| `oracul.news.article-fetch-timeout` | PT3S |
| `oracul.news.article-fetch-concurrency` | 8 |
| `oracul.research.query-budget` | 20 |
| `oracul.ranking.weights.*` | see FR-16 |
| `oracul.ranking.min-source-quality` | 0.30 |
| `oracul.evidence.max-items` / `core` / `supporting` / `counter-signals` | 25 / 10 / 10 / 5 |

## Behaviour

### FR-11 — Research Profile
- Happy path: stage UNDERSTANDING builds the profile from the run configuration and stores it on the run.
  Example (acceptance configuration): realism 8, darkness 9, optimism 2, horizon `5y`, `biology-new-pandemic` 8,
  `robotics-humanoid-boom` 6 → `{darkness: 0.9, optimism: 0.2, realism: 0.8, horizon: "5y", topics: [{key:
  "biology-new-pandemic", label: "New pandemic", category: "biology", weight: 0.8, custom: false}, {key:
  "robotics-humanoid-boom", label: "Humanoid robot boom", category: "robotics", weight: 0.6, custom: false}]}`.
- Rules: pure function `ResearchProfileFactory.from(ScenarioConfiguration)`, no I/O; topic order = catalogue
  wildcards in request order, then custom wildcards. No wildcards → `topics: []`, profile valid.
- Errors: none reachable — the configuration was validated at `startRun`. `GET /api/runs/{runId}/research` before the
  profile exists → 409 `RESEARCH_NOT_READY` → "Research has not started yet"; unknown run → 404 `RUN_NOT_FOUND` →
  "Future not found".

### FR-12 — Search plan and query generation
- Happy path (stage RESEARCH_STRATEGY):
  1. Bucket allocation of the budget B with the largest-remainder method over the active buckets. With topics:
     shares 0.4/0.3/0.2/0.1 → B = 20 gives WILDCARD 8, MAJOR 6, ADJACENT 4, UNEXPECTED 2. Without topics the
     WILDCARD share is redistributed proportionally (MAJOR 0.5, ADJACENT 0.3333, UNEXPECTED 0.1667) → B = 20 gives
     MAJOR 10, ADJACENT 7, UNEXPECTED 3; every active bucket gets ≥ 1 (taken from the largest bucket if needed).
  2. Intents: one WILDCARD intent per topic (its queries split proportionally to topic weight, each topic ≥ 1),
     one MAJOR intent ("major current world events"), one ADJACENT intent per topic category (or "science,
     technology and economy" when no topics), one UNEXPECTED intent ("unusual early signals and research").
     Intent descriptions are slanted by direction: darkness ≥ 6 adds risk aspects (threats, failures, warnings),
     optimism ≥ 6 adds opportunity aspects (breakthroughs, recoveries), both may apply.
  3. `drivenBy` per intent: the topic as "<label> <intensity>/10" (WILDCARD intents), "Darkness <n>/10" when
     darkness ≥ 6, "Optimism <n>/10" when optimism ≥ 6, "Realism <n>/10" for UNEXPECTED intents when realism ≤ 5, and
     always "Horizon <label>".
  4. Query expansion: one tool-less ChatGPT call (prompt contract QUERY_EXPANSION) receives the intents and the
     per-intent query counts and returns `{queries:[{intentId, text}]}`; texts are trimmed, ≤ 120 chars, deduplicated.
     Missing or surplus queries per intent are filled from / cut to the template list.
- Rules: the plan is stored before searching; expansionMode records MODEL or TEMPLATE_FALLBACK. Static templates
  exist per catalogue wildcard, per category, and for MAJOR/UNEXPECTED (`QueryTemplates`), so a plan is always
  complete without ChatGPT.
- Errors:
  - expansion call fails (any ChatGPT error except 401/403-after-refresh), times out, or returns malformed JSON →
    expansionMode TEMPLATE_FALLBACK, all queries from templates, the run continues (no user message).
  - expansion call fails with an expired session (refresh failed) → run FAILED `CHATGPT_SESSION_EXPIRED`
    (generation-runs.md).

### FR-13 — Current-news search and source retrieval
- Happy path (stages SEARCHING, READING_SOURCES):
  1. Each query → `GET {gdelt.base-url}/api/v2/doc/doc?query=<url-encoded text> sourcelang:english&mode=ArtList&format=json&maxrecords=<max>&sort=HybridRel&timespan=<t>`
     with t by horizon: `1d`,`1w` → `7d`; `1m` → `14d`; `1y`,`5y`,`10y`,`20y` → `3months`. Queries run with
     concurrency 4; each query records status OK / EMPTY / FAILED and `articlesReturned`.
  2. `counts.searches` = number of executed queries; `counts.articlesRetrieved` = raw articles.
  3. Basic filtering: drop articles without URL or title, duplicate URLs (normalized: lower-case host, no fragment,
     no `utm_*`), non-English, and articles older than the timespan. Remaining = sources;
     `counts.articlesConsidered` = number of sources (S001… in provider order).
  4. Metadata fetch: `GET <article url>` with a 3 s timeout, max 512 KB, redirects ≤ 3; extract `og:description` or
     `meta[name=description]` as summary, `og:site_name` as publisher. `metadataFetched` = true on success.
  5. Per source store: url, publisher (site name, else GDELT `domain`), title, publishedAt (GDELT `seendate`, UTC),
     retrievedAt (now), summary (page description, else the title), topic (intent bucket/topic key of the first
     query), entities (filled by FR-14), sourceType and sourceQuality from `SourceQualityTable` (configurable domain
     list: OFFICIAL gov/int/official domains 0.95, RESEARCH journals/universities 0.9, NEWS established outlets 0.85,
     other NEWS 0.6, BLOG 0.35, OTHER 0.4).
- Rules: more candidates than will be used (all filtered sources go to normalisation, never the full raw pool to the
  generation prompt). Article text is never executed or rendered; only title/description/site name are extracted.
- Errors:
  - an article page times out, is unreachable or not HTML → source keeps provider metadata, `metadataFetched` false,
    run continues
  - some queries fail (timeout/5xx/invalid JSON) → those queries FAILED, the run continues with the others
  - all queries FAILED (provider unavailable) → run FAILED `NEWS_UNAVAILABLE` "ORACUL could not reach its news
    sources — try again later"; no further ChatGPT call is made
  - all queries OK/EMPTY but 0 sources after filtering → continue; FR-31 ends it as INSUFFICIENT_EVIDENCE
  - `GET /api/runs/{runId}/sources` unknown run → 404 `RUN_NOT_FOUND` → "Future not found" (before SEARCHING it returns
    `items: []`)

### FR-14 — Event normalisation and deduplication
- Happy path (stage CONNECTING_SIGNALS): sources are sent in batches of ≤ 40 to a tool-less ChatGPT call
  (prompt contract EVENT_NORMALIZATION) that returns `{events:[{sourceIds, date, category, entities, summary,
  disagreement|null, confidence}]}`. Every source id appears in exactly one event; ids are those of the batch.
  Events from different batches are merged when they share ≥ 1 entity and their summaries have token Jaccard ≥ 0.5.
  Events get ids EV001… ; `counts.uniqueEvents` = number of events.
- Rules: same real-world event reported by several publishers → one event listing all source ids; unrelated
  articles → separate events. When sources disagree on a detail, `disagreement` names it and the summary states it
  ("Reports differ on …") — no version is chosen. Source `entities` are filled from their event.
- Errors:
  - normalisation output malformed or violating the id rules (unknown id, id twice, id missing) → one retry of the
    batch with the error list; still invalid → deterministic fallback for that batch: one event per source,
    then merge sources whose normalized titles have token Jaccard ≥ 0.6; category = source topic; confidence 0.5;
    run continues
  - ChatGPT 429 / unavailable / session expired → run FAILED with the matching code (generation-runs.md)

### FR-15 — Semantic classification (event enrichment)
- Happy path: events in batches of ≤ 20 go to a tool-less ChatGPT call (prompt contract EVENT_CLASSIFICATION)
  returning per event: topic, subtopics, sentiment (−1..1), risk, opportunity, impact, novelty (0..1), trend
  (EMERGING/ESTABLISHED/DECLINING), geography, wildcardMatches (score 0..1 per ResearchTopic key; 0 when unrelated).
  `sourceQuality` is NOT taken from the model: it is the maximum sourceQuality of the event's sources.
- Rules: the instructions require semantic judgement of direction (risk vs opportunity), not keywords: e.g. a vaccine
  breakthrough is opportunity > risk although it mentions a virus. Output is validated: numbers must be within range
  (no clamping of out-of-range model values — they count as malformed).
- Errors:
  - an event's classification missing, malformed or out of range → that event is retried once in a follow-up batch;
    still bad → event kept with `excludedReason` "CLASSIFICATION_FAILED", not ranked, not selected
  - whole call fails with 429 / unavailable / session expired → run FAILED with the matching code

### FR-16 — Scenario-aware ranking
- Happy path (stage RANKING): for every classified event with p = profile:
  | factor | formula (all in 0..1) | default weight |
  |---|---|---|
  | topicMatch | 1.0 if event topic/category equals a topic's category; 0.5 if the event came only from MAJOR/ADJACENT queries; else 0.3 | 1.0 |
  | wildcardMatch | max over topics of (wildcardMatches[key] × weight[key]); 0 without topics | 1.5 |
  | darknessMatch | p.darkness × risk | 1.5 |
  | optimismMatch | p.optimism × opportunity | 1.5 |
  | recency | 1 − min(ageDays / timespanDays, 1) | 0.75 |
  | sourceQuality | event sourceQuality | 1.0 |
  | impact | impact | 1.0 |
  | trendStrength | ESTABLISHED 1.0 · EMERGING 0.7 · DECLINING 0.3 | 0.5 |
  | crossTopic | max(0, min(1, (n − 1) / 2)), n = number of topics with wildcardMatch ≥ 0.5 | 0.5 |
  | realismCompatibility | p.realism × corroboration + (1 − p.realism) × novelty, corroboration = 0.5 × min(1, sourceCount/3) + 0.5 × (ESTABLISHED 1 · EMERGING 0.5 · DECLINING 0.5) | 1.0 |
  relevance = Σ(w·f) / Σw; reliability = sourceQuality; score = relevance × (0.25 + 0.75 × reliability).
  Events are sorted by score desc, ties by sourceQuality desc, then date desc, then id.
- Rules: weights come from `oracul.ranking.weights.<factor>`; reliability is a separate multiplier no preference
  factor can compensate, and events below `min-source-quality` (0.30) are never selected (they stay listed with
  their score). Ranking is deterministic (pure `EventRanker`, unit-testable). Darkness/Optimism change order, never
  event content.
- Errors: none user-visible (pure computation on validated data); `GET /api/runs/{runId}/events` unknown run → 404
  `RUN_NOT_FOUND` → "Future not found" (before normalisation `items: []`).

### FR-17 — Evidence selection with diversity and counter-signals
- Happy path: from ranked, non-excluded events with sourceQuality ≥ 0.30:
  1. Requested direction d = p.darkness − p.optimism. d > 0.1 → counter-signal candidates are events with
     opportunity − risk ≥ 0.2; d < −0.1 → risk − opportunity ≥ 0.2; otherwise (balanced) → events whose dominant
     direction (sign of risk − opportunity, |diff| ≥ 0.2) is opposite to the majority of the selected core events.
  2. CORE: greedy by score, up to 10, excluding counter-signal candidates. SUPPORTING: next, up to 10.
     COUNTER_SIGNAL: best-scored counter-signal candidates, up to 5; at least 1 when any candidate exists (even if its
     score is low). Total ≤ 25.
  3. Diversity caps applied while picking (an event violating a cap is skipped): ≤ 2 events sharing the same primary
     entity (first entity, case-insensitive), ≤ 3 events whose primary source has the same publisher, ≤ 6 events
     with the same geography (except "global"), ≤ 40 % of the pack from one category when ≥ 3 categories are
     available.
  4. Evidence IDs E001… assigned in order CORE (by score), SUPPORTING, COUNTER_SIGNAL, no gaps; stored on the event
     (`selection`) and never renumbered for this pack. `counts.eventsSelected`, `counts.counterSignals` updated.
- Rules: counter-signals are never dropped because they contradict the settings (invariant 7).
- Errors: fewer events than slots → smaller pack (FR-31 decides sufficiency); 0 eligible events → empty pack →
  INSUFFICIENT_EVIDENCE.

### FR-18 — Evidence Pack
- Happy path: after selection the backend creates the immutable Evidence Pack: id, generationId, cutoff (instant of
  pack creation, UTC, minute precision), configuration, profile, core / supporting / counterSignals (EvidenceItem:
  evidenceId, section, eventId, date, category, summary incl. disagreement, entities, sourceIds, sourceQuality,
  confidence), the referenced sources, and `promptText` rendered by `EvidencePackRenderer`:
  ```
  ORACUL EVIDENCE PACK
  Generation: ORC-2026-10-02-1842
  Cutoff: 2026-10-02T18:42Z
  SCENARIO
  Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years
  WILDCARDS
  New pandemic: 8 | Humanoid robot boom: 6
  CORE EVIDENCE
  [E001] <date> · <category> · <summary> · sources: <publisher> (S003), <publisher> (S017) · quality 0.92
  …
  SUPPORTING EVIDENCE
  [E011] …
  COUNTER-SIGNALS
  [E021] …
  ```
  (empty section → "none"). `run.evidence_pack_id` is set; the pack is stored also when FR-31 stops the run.
- Rules: `promptText` is the exact string the prompt builder places in the untrusted evidence block
  (scenario-reasoning.md); the generation, critic and story prompts never include other current-world content.
  Text fields are sanitized before rendering: control characters removed, the delimiter tokens `<<<` / `>>>`
  replaced by `‹‹‹` / `›››`, each summary ≤ 600 chars.
- Errors:
  - `GET /api/runs/{runId}/evidence-pack` before the pack exists → 409 `EVIDENCE_PACK_NOT_READY` → "The Evidence Pack
    is not ready yet"
  - unknown / other session's run → 404 `RUN_NOT_FOUND` → "Future not found"

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/research | getRunResearch | — | 200 RunResearch · 404 RUN_NOT_FOUND · 409 RESEARCH_NOT_READY · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId}/sources | listRunSources | — | 200 SourceList · 404 RUN_NOT_FOUND · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId}/events | listRunEvents | — | 200 EventList · 404 RUN_NOT_FOUND · 500 INTERNAL_ERROR |
| GET | /api/runs/{runId}/evidence-pack | getEvidencePack | — | 200 EvidencePack · 404 RUN_NOT_FOUND · 409 EVIDENCE_PACK_NOT_READY · 500 INTERNAL_ERROR |

## UI
- No own screen: FR-11–18 are backend-only (UI: no). Their data is shown by future-result.md (SOURCES, WHY THESE NEWS?,
  metadata counts) and the progress stages of generation-runs.md.
- Backend layout: `com.oracul.app.research` (`ResearchProfileFactory`, `SearchPlanner`, `QueryTemplates`,
  `NewsProvider` + `GdeltNewsProvider`, `ArticleMetadataFetcher`, `SourceQualityTable`, `EventNormalizer`,
  `EventClassifier`, `EventRanker`, `EvidenceSelector`, `EvidencePackRenderer`, `ResearchController implements ResearchApi`).

## Slice 04_run-start — FR-11 test contract

Delivers `ResearchProfileFactory` (stage UNDERSTANDING, generation-runs.md "Slice 04_run-start") and the profile part
of `getRunResearch`. Search plan, sources, events and counts other than 0 come in 05–07.

### Unit (`com.oracul.app.research.ResearchProfileFactory`, pure, no Spring context)
Signature: `ResearchProfile from(ScenarioConfiguration configuration)` (instance method; the catalogue is injected
through the constructor or read from `ScenarioCatalogueData`). Returns the generated model
`com.oracul.app.api.model.ResearchProfile`. `darkness/optimism/realism` = `BigDecimal.valueOf(value).movePointLeft(1)
.doubleValue()` semantics, i.e. exactly `value / 10` with one decimal (assert with `assertEquals(0.9, d)` — no delta).

| Configuration | Expected profile |
|---|---|
| realism 8, darkness 9, optimism 2, horizon `5y`, wildcards `[biology-new-pandemic 8, robotics-humanoid-boom 6]` | `{"darkness":0.9,"optimism":0.2,"realism":0.8,"horizon":"5y","topics":[{"key":"biology-new-pandemic","label":"New pandemic","category":"biology","weight":0.8,"custom":false},{"key":"robotics-humanoid-boom","label":"Humanoid robot boom","category":"robotics","weight":0.6,"custom":false}]}` |
| same wildcards in reverse request order | topics in reverse order (request order is kept) |
| realism 8, darkness 5, optimism 5, horizon `1y`, no wildcards | `{"darkness":0.5,"optimism":0.5,"realism":0.8,"horizon":"1y","topics":[]}` |
| all three = 1, horizon `1d` / all three = 10, horizon `20y` | 0.1/0.1/0.1 `1d` / 1.0/1.0/1.0 `20y` |
| darkness 3, optimism 7 (any other values 1–10) | 0.3 / 0.7 (no floating artefacts such as 0.30000000000000004) |
| wildcard intensity 1 / 10 | weight 0.1 / 1.0 |
| custom wildcards `[{"label":"  Mars colony ","intensity":7}]` after `biology-new-pandemic` 8 (slice 18 validates custom input; the factory already maps it) | topics `[…biology-new-pandemic 0.8…, {"key":"custom-1","label":"Mars colony","category":"custom","weight":0.7,"custom":true}]` |

### Integration (`GET /api/runs/{runId}/research`, `getRunResearch`)
| # | Situation | Status | Body |
|---|---|---|---|
| 1 | connected, `startRun` body `A` (generation-runs.md), delay PT30S, polled until `stageIndex` ≥ 2 | 200 | `runId` = run id; `profile` deep-equals the first row of the unit table; `counts` all 0; `searchPlan` absent (slice 05 fills it) |
| 2 | connected, body `B` | 200 | `profile.topics` = `[]`, `profile.horizon` `1y`, darkness/optimism 0.5, realism 0.8 |
| 3 | run exists but `research_profile` is still null (a `generation_run` row in status QUEUED with `research_profile` null inserted with `JdbcTemplate` for the caller's session) | 409 | `{"code":"RESEARCH_NOT_READY","message":"Research has not started yet"}` |
| 4 | random UUID / malformed id `abc` / run of another session | 404 | `{"code":"RUN_NOT_FOUND","message":"Future not found"}` |

Also asserted (DB): `generation_run.research_profile` jsonb of row #1 deep-equals the same profile ("stored with the
run").

### Test locations and traces (`// @trace FR-11`)
- `backend/src/test/java/com/oracul/app/research/ResearchProfileFactoryTest.java` (unit table)
- `backend/src/test/java/com/oracul/app/research/RunResearchIT.java` (integration rows)
