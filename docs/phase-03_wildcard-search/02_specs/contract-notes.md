# Contract notes — phase-03_wildcard-search

Decisions behind the phase-03 changes to `api/openapi.yaml` (version 0.6.0 → 0.7.0). Phase-01 and phase-02
conventions stay in force (`docs/phase-01_mvp/02_specs/contract-notes.md`, `docs/phase-02_codex-provider/02_specs/
contract-notes.md`): every 4xx/5xx is `#/components/schemas/ApiError` `{code, message}`, tags = capabilities, UUID run
ids, ISO-8601 UTC instants, no schema named `Error`, null optional fields left out of the JSON (never `required` +
`nullable`).

Capability specs of this phase:
| Spec | Covers | Contract surface |
|---|---|---|
| `wildcard-search.md` | FR-49, FR-50, FR-51, FR-52, FR-61 (+ NFR-10, NFR-11) | `SearchPlan.pipelines`, `WildcardPipeline`, `PipelineQuery`, `WildcardPipelineKind`; `getRunResearch` |
| `article-retrieval.md` | FR-53, FR-54, FR-55, FR-56 | `Source` (+ `contentStatus`, `excerpts`, `publisherHost`, `pipelineIds`), `ArticleContentStatus`, `SourceExcerpt`, `ResearchCounts.sourcesKept` / `sourcesWithContent`; `listRunSources` |
| `wildcard-evidence.md` | FR-57, FR-58, FR-59 | `EvidencePack.wildcardSections`, `PackWildcardSection`, `PackSourceItem`, `EvidenceNoteKind.MISSING_WILDCARD_SOURCES`, `EvidenceNote.wildcardsWithoutSources`; `getEvidencePack`, `getRun` |
| `wildcard-result-views.md` | FR-60 | `FutureResult.wildcardGroups`, `ResultWildcardGroup`, `ResultGroupSource`; `getFutureResult` |

## Operations
| Change | Operation | Backward compatible |
|---|---|---|
| description only | `getRunResearch` (pipelines), `listRunSources` (Evidence-order numbering, new fields), `getEvidencePack` (wildcard sections), `getFutureResult` (wildcard groups), `stopRun` (no GDELT wording) | yes — same paths, statuses, error codes |
| none new, none removed | every phase-01 / phase-02 operation and operationId is kept; no tag added (the new specs extend the existing tags `research` and `result`, whose descriptions now name them) | — |

## Schemas
| Schema | Change |
|---|---|
| `SearchPlan` | + optional `pipelines` (`WildcardPipeline[]`, 1–33). New runs: `buckets` / `intents` / `queries` are `[]` (required arrays kept, so stored plans and existing consumers stay valid) |
| `WildcardPipeline`, `PipelineQuery`, `WildcardPipelineKind` | new |
| `SearchQueryStatus` | description only (one request per query; no OR-groups, no GDELT) |
| `SearchQuery` | description only: `articlesReturned` = items of the query's own RSS answer, at most 100 (slice 03_parallel-search) |
| `ResearchCounts` | + optional `sourcesKept`, `sourcesWithContent` (0–30, always sent for new runs); descriptions of `searches`, `articlesRetrieved`, `articlesConsidered` (distinct usable candidates again), `eventsSelected` (= distinct Evidence IDs), `counterSignals` (0 for new runs) |
| `Source` | + optional `publisherHost`, `contentStatus`, `excerpts`, `pipelineIds`; descriptions of `url`, `summary`, `topic`, `metadataFetched`, `publisherUrl` |
| `ArticleContentStatus`, `SourceExcerpt` | new |
| `EvidencePack` | + optional `wildcardSections`; new packs have empty `core` / `supporting` / `counterSignals` |
| `PackWildcardSection`, `PackSourceItem` | new |
| `EvidenceNoteKind` | + `MISSING_WILDCARD_SOURCES` |
| `EvidenceNote` | + optional `wildcardsWithoutSources` (≤ 33 labels); `message` maxLength 200 → 2000 (a note can name 33 labels of 40 characters) |
| `CriticIssueType` | description only: IGNORED_COUNTER_SIGNALS no longer requested or accepted; kept for stored reports |
| `ResultSource`, `ResearchExplanation` | description only (new runs: section CORE, counterSignal false, intents `[]`) |
| `FutureResult` | + optional `wildcardGroups` |
| `ResultWildcardGroup`, `ResultGroupSource` | new |

No schema, property or enum value is renamed or removed in 0.7.0 (so there is no `Renamed:` line). Every new property
is optional, so existing generated constructors (required arguments only) stay valid and a field this phase adds does
not change responses of stored runs.

Enum addition `EvidenceNoteKind.MISSING_WILDCARD_SOURCES` affects consumers that map the kind exhaustively (backend
`EvidenceNotes` / `RunService` message building); the frontend renders `evidenceNote.message` as text and has no
`Record<EvidenceNoteKind, …>` today. The implementing slice updates the backend mapping.

## Error codes added
None. Every new failure mode of this phase (a wildcard without sources, decode / page / refusal failures, budget
cut-offs) ends as data (query status, `contentStatus`, evidence note), never as an HTTP error or a run failure.

## Database
The DDL lands in four slices, so each of them adds its own Flyway file and never edits one already applied
(plan.md "One Flyway file per slice"):
- 04_wildcard-queries — `V11__source_pipeline_ids.sql`: `ALTER TABLE source ADD COLUMN pipeline_ids JSONB NULL`
- 06_wildcard-pack — `V12__evidence_pack_sections.sql`: `ALTER TABLE evidence_pack ADD COLUMN sections JSONB NULL`
- 08_article-text — next free version: `ALTER TABLE source ADD COLUMN content_status VARCHAR(32) NULL, ADD COLUMN
  excerpts JSONB NULL, ADD COLUMN publisher_host TEXT NULL`
- 09_wildcard-results — next free version: `ALTER TABLE generation_run ADD COLUMN evidence_note_wildcards JSONB NULL`
`search_plan` (jsonb) and `counts` (jsonb) need no DDL. No credential column (NFR-1 scan still applies). Older
migrations keep their GDELT history (FR-49 excludes `db/migration/**`).

## External endpoints (all configurable — NFR-7)
| Property | Default | Used by |
|---|---|---|
| `oracul.news.google.base-url` | `https://news.google.com` (`/rss/search`) | FR-52 |
| `oracul.news.google.decode-url` | `<google.base-url>/_/DotsSplashUi/data/batchexecute` | FR-54 (undocumented Google call, only in `ArticleUrlDecoder`) |
| Google article links, publisher pages | from the RSS items / decode answer | FR-54, guarded by FR-56 |
| `oracul.news.gdelt.base-url` | **removed** | — (FR-49) |
Configuration tables: `wildcard-search.md` (search, budget) and `article-retrieval.md` (retrieval, safe fetching).
Docker E2E (`docker-compose.e2e.yml`, backend environment): remove `ORACUL_NEWS_GDELT_BASE_URL`,
`ORACUL_NEWS_REQUEST_SPACING`, `ORACUL_NEWS_RATE_LIMIT_WAIT`, `ORACUL_NEWS_GOOGLE_REQUEST_SPACING`; add
`ORACUL_NEWS_FETCH_ALLOWED_PRIVATE_HOSTS: stub`, `ORACUL_NEWS_GOOGLE_RATE_LIMIT_WAIT: PT0.2S`,
`ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT: PT3S`. The stack modes (`.oracul/stack.json`: `e2e` = base + e2e file, `run` =
base) do not change.

## Analyst decisions to confirm at spec approval
1. **Event stages stay, but no longer feed the pack.** FR-14 / FR-15 / FR-16 are not corrected by this phase, so
   CONNECTING_SIGNALS (normalisation + classification of the kept sources) and RANKING (event ranking) still run and
   still fill `listRunEvents` and `counts.uniqueEvents` (metadata panel FR-25). The Evidence Pack is built from the
   per-wildcard sources (FR-57), so events no longer get a `selection`. Cost in a real run: 2–3 extra ChatGPT calls
   (≈ 20–40 s). Dropping the event stages would need a scope change.
2. **FR-17 is superseded as a whole**, not only its counter-signal quota: selection moves before article retrieval and
   works on sources per wildcard (FR-53); `counts.counterSignals` is 0; the `oracul.evidence.core / supporting /
   counter-signals / max-*` properties are no longer read. `oracul.evidence.min-core.*` (FR-47 thresholds) stays.
3. **Evidence IDs are source numbers.** Sources are numbered in Evidence order (pipelines in order, each pipeline's
   group order, first appearance wins), so `S00k` ↔ `E00k`; a source listed under two wildcards has one ID (FR-57
   acceptance 2).
4. **"Found by" attribution.** A kept source is attributed to, and listed under, every pipeline whose usable results
   contained it (FR-50 acceptance 5, FR-53 acceptance 2) — so a group can show more than its own 4 selections when
   another pipeline selected an article it also found. Selection itself stays ≤ 4 per pipeline.
5. **FR-47 threshold on a wildcard pack**: every pack item counts as core evidence (`coreItems` = distinct Evidence
   IDs). The new note kind MISSING_WILDCARD_SOURCES carries the FR-59 sentence; when the realism threshold also fails,
   the kind is INSUFFICIENT_EVIDENCE and the message starts with the FR-59 sentence (so LOWER REALISM still shows).
6. **NFR-10 windows**: query generation must end by 30 s, Google search by 60 s, retrieval by 90 s after the start of
   RESEARCH_STRATEGY (configurable), so a slow phase cannot eat the whole budget of the next one.
7. **One QUERY_GENERATION call per pipeline, at most 4 in parallel** (Google requests and retrievals use 8): up to 33
   calls on the user's ChatGPT plan; 4 keeps the burst moderate. No retry (phase-02 decision 19); a failed call falls
   back to templates for that pipeline only.
8. **Template vocabulary** (FR-51 acceptance 3/4): fixed band words and direction words; the negative-term list N and
   positive list P are spelled out in `wildcard-search.md`; words of the wildcard label itself (e.g. "crisis" in
   "Energy crisis") do not count for the N/P checks.
9. **Rule Q is stricter than "no OR operator"**: the token `or` is forbidden in any letter case, and `:` is forbidden
   (no `when:` / `site:` in model text), because only the plain-word shape is proven to return items.
10. **Relevance = deterministic keyword score** (label terms × 3, query terms × 1, +2 per extra query that returned the
    item; ties by Google's feed position) — no ChatGPT call, so selection is reproducible in tests.
11. **The decode call is undocumented** and isolated in `ArticleUrlDecoder` (configurable URL, fixed `f.req` shape, "first
    http(s) URL in the answer"). If Google changes it, sources degrade to "content not retrieved" (DECODE_FAILED) and the
    run still completes; NFR-11 is the live proof.
12. **Excerpts are per wildcard** (FR-50 "results never mix"): an article shared by two wildcards gets each wildcard's
    own fragments (`Source.excerpts[]` per pipeline); `contentStatus` describes the one page fetch.
13. **Two Google links decoding to the same publisher URL** are merged into the earlier source after retrieval (keeps
    `url` unique per run and "stored once").
14. **SSRF exemption = exact host names** (`stub` in E2E, `127.0.0.1` in backend tests); any-local and IPv4-mapped IPv6
    forms are refused in addition to the ranges named in FR-56; the Google search and decode endpoints are trusted
    configuration and not checked.
15. **Legacy results keep the phase-01 layout**: results without `wildcardGroups` (runs before this phase, reopened from
    Recent futures) render the flat SOURCES list and the intents view unchanged.
16. **All-empty SOURCES**: when no wildcard found a source, SOURCES shows only "No sources" (FR-59 acceptance 2 / FR-47);
    when some did, every empty group shows "no current sources found" (FR-60 acceptance 4). WHY THESE NEWS? always
    lists every group.
17. **STORY_WRITING keeps the Closed Evidence Mode block** (FR-23 is not corrected); only generation and critic switch to
    the starting-conditions block (FR-58).
18. **"AI takeover"** in FR-50 / NFR-11 is not a catalogue label; tests and the real check enter it as a custom wildcard.
19. **E2E stub control** moves to `POST /__control/google` (modes in `wildcard-search.md` FR-61); `/__control/rss`,
    `/__control/news` and the GDELT route are removed with FR-49.

## Slice 06_wildcard-pack (step 4a)
Description-only edits of 0.7.0, no shape change and no rename (so still no `Renamed:` line):
- `EventSelection` — never set for runs created since phase 03 (the pack is built from sources per wildcard).
- `EvidencePack.sources` — wildcard packs list every kept source of the run, by id ascending.
- `PackSourceItem.snippet` — the stored source summary (else its title), `maxLength: 600` (a response-side bound; the
  summary is already ≤ 600).
- `FutureResult.sources` — for packs with `wildcardSections`: one entry per distinct Evidence ID, CORE, no counter-signal.
Wire rule of the slice: `wildcardSections` present for every new pack; `PackWildcardSection.level`,
`PackSourceItem.publishedAt` and `PackSourceItem.snippet` are absent when unset (NON_NULL mixins), never `null`.

## Slice 08_article-text (step 4a)
No shape change of 0.7.0 and no rename (still no `Renamed:` line): `Source.contentStatus` / `excerpts` /
`publisherHost`, `ArticleContentStatus`, `SourceExcerpt` and `ResearchCounts.sourcesWithContent` already describe the
slice. Wire rule of the slice: for runs created since this slice every source carries `contentStatus` and `excerpts`
(possibly `[]`), `publisherHost` only when `url` is the publisher URL, and `counts.sourcesWithContent` is sent from the
READING_SOURCES commit on (also `0`); stored older runs send none of them (NULL columns map to absent fields).
Flyway file: `V13__source_content.sql` (`content_status`, `excerpts`, `publisher_host` on `source`). New configuration:
`oracul.news.google.decode-url` (default derived from `oracul.news.google.base-url`), `oracul.news.article-fetch-timeout`
default `PT8S`, `oracul.search.stage-budget` now also bounds retrieval; Docker E2E adds
`ORACUL_NEWS_ARTICLE_FETCH_TIMEOUT: PT3S`. The stack modes of `.oracul/stack.json` do not change.
