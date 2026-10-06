# Spec — Result views grouped by wildcard: SOURCES and WHY THESE NEWS?

Covers: FR-60

Delta against phase-01 `future-result.md` FR-27 (SOURCES list with "used in scenario" and "counter-signal" badges,
slice 13) and FR-28 (WHY THESE NEWS? with intents, `drivenBy` chips and six summary numbers, slice 14), and the current
code `FutureResultService` (backend) and `src/app/result/` (`future-result.ts`, `why-sources.ts`, `why-news-panel.ts`).
FR-23, FR-25 and FR-26 stay as they are (the WHY chips now point at grouped items, see "Highlight").

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-27: one flat list `source-item-<evidenceId>` in Evidence-ID order, badge `source-counter-<id>` "counter-signal" | **Superseded for runs with `wildcardGroups`**: one group per wildcard, items `source-item-<pipelineId>-<evidenceId>`, excerpt or "content not retrieved", no counter-signal badge. Results without `wildcardGroups` (runs stored before this phase, reopened from Recent futures) keep the phase-01 rendering unchanged. |
| FR-28: intents with `drivenBy` chips; six numbers incl. "unique events identified", "events selected", "counter-signals retained" | **Superseded for runs with `wildcardGroups`**: per wildcard its queries with status and item count; five numbers: searches, articles considered, sources kept, sources with content, sources used in the scenario. Legacy results unchanged. |

## Purpose
The user sees, per wildcard, what ORACUL searched, what came back, and which real articles — with the passages that
mattered — fed the future, and sees plainly which wildcard found nothing.

## Data
`getFutureResult` (`FutureResult`) gains the optional `wildcardGroups` — present iff the run (for ALTERNATIVE runs:
the run that built the pack) has a search plan with `pipelines`:
| Field | Source |
|---|---|
| `wildcardGroups[]` | one per pipeline, plan order |
| `.pipelineId`, `.kind`, `.label`, `.level` (absent for GENERAL), `.heading` | the pipeline (`heading` = `New pandemic 8/10`, `Ocean desalination boom 7/10`, `General`) |
| `.queries[]` | the pipeline's `PipelineQuery` entries (`id`, `text`, `status`, `articlesReturned`) |
| `.sources[]` | the pack section's items (FR-57) in section order, each `{evidenceId, sourceId, title, publisher, publishedAt?, url, contentRetrieved, fragments, usedInScenario}`; `usedInScenario` = the evidenceId is cited by a fact of the accepted scenario's `factsUsed` |
| `research.counts` | run counts incl. the new `sourcesKept`, `sourcesWithContent` |
| `research.intents` | `[]` for new runs |
| `sources` (flat) | unchanged meaning: one entry per Evidence ID in ID order; new runs: `section` CORE, `counterSignal` false (feeds the FR-26 chip rule) |

## Behaviour

### FR-60 — SOURCES and WHY THESE NEWS? grouped by wildcard
- Happy path — SOURCES (`open-sources` toggles `sources-panel`, a `mat-card`, title `sources-title` `SOURCES`; layout
  chosen by `wildcardGroups` present):
  1. Every group has no sources (empty pack) → only `sources-empty` text exactly `No sources` (phase-02 FR-47 / FR-59
     acceptance 2: the SOURCES view is empty).
  2. Otherwise one `source-group-<pipelineId>` per group in API order, each starting with `source-group-title-<pipelineId>`
     (text exactly `heading`, e.g. `New pandemic 8/10`); a group without sources contains only
     `source-group-empty-<pipelineId>` text exactly `no current sources found`.
  3. Per source of a group, in API order, `source-item-<pipelineId>-<evidenceId>` containing (testid suffix
     `<p>-<e>` = `<pipelineId>-<evidenceId>`):
     | testid | content |
     |---|---|
     | `source-id-<p>-<e>` | evidenceId |
     | `source-title-<p>-<e>` | title; empty → `Untitled source` |
     | `source-publisher-<p>-<e>` | publisher; empty → `Unknown publisher` |
     | `source-date-<p>-<e>` | `publishedAt` as `d MMM yyyy` in UTC (`30 Sep 2026`); absent → `date unknown` |
     | `source-link-<p>-<e>` | `a`, `href` = url, `target="_blank"`, `rel="noopener noreferrer"`, text `Open source` — only for `http://` / `https://` urls (case-insensitive) |
     | `source-no-link-<p>-<e>` | `Link unavailable` — instead of the link for any other url |
     | `source-used-<p>-<e>` | `used in scenario` iff `usedInScenario` |
     | `source-excerpt-<p>-<e>` | iff `contentRetrieved`: container with one `source-fragment-<p>-<e>-<k>` per fragment (k 0-based, API order), text exactly the fragment |
     | `source-not-retrieved-<p>-<e>` | iff not `contentRetrieved`: text exactly `content not retrieved` (no excerpt) |
     No counter-signal badge exists in this layout. A source listed in two groups is rendered in both with its
     group's fragments.
- Happy path — WHY THESE NEWS? (`open-why-news` toggles `why-news-panel`, title `why-news-title` `WHY THESE NEWS?`):
  4. One `why-news-group-<pipelineId>` per group (also when every group is empty), title
     `why-news-group-title-<pipelineId>` = `heading`; then per query in API order `why-news-query-<queryId>` with
     `why-news-query-text-<queryId>` (text exactly the query text), `why-news-query-status-<queryId>` (exactly `OK`,
     `EMPTY`, `FAILED`, or `PENDING`) and `why-news-query-count-<queryId>` (`1 item` / `<n> items`, n =
     `articlesReturned`, `0 items`); then `why-news-group-sources-<pipelineId>` `1 source` / `<n> sources` (n = the
     group's sources) or, for an empty group, `why-news-group-empty-<pipelineId>` text exactly `no current sources found`.
  5. Then `research-summary` (title `research-summary-title` `Research summary`) with exactly five lines in this order,
     n from `research.counts` as a plain integer, singular iff n = 1:
     | testid | field | n = 1 | n ≠ 1 |
     |---|---|---|---|
     | `summary-searches` | `searches` | `1 search performed` | `<n> searches performed` |
     | `summary-articles` | `articlesConsidered` | `1 article considered` | `<n> articles considered` |
     | `summary-kept` | `sourcesKept` (absent → 0) | `1 source kept` | `<n> sources kept` |
     | `summary-content` | `sourcesWithContent` (absent → 0) | `1 source with content` | `<n> sources with content` |
     | `summary-used` | `sourcesUsed` | `1 source used in the scenario` | `<n> sources used in the scenario` |
     No `summary-events`, `summary-selected`, `summary-counter-signals`, intent or `drivenBy` element in this layout.
- Rules:
  - Layout switch: `wildcardGroups` present → grouped layout in both panels; absent → the phase-01 layout of
    future-result.md slices 13/14, unchanged (testids, texts, badges).
  - **Highlight (FR-26 in the grouped layout)**: a click on an enabled WHY chip (enabled ⇔ its id is in the flat
    `sources`) opens SOURCES, gives every `source-item-*-<evidenceId>` of that id the class `highlighted` and
    `data-highlighted="true"` for 3,000 ms, and scrolls the first of them into view; all other items have neither.
  - All texts are interpolated (never `innerHTML`): titles, fragments, query texts and custom labels show literally.
  - No extra HTTP call: both panels use `getFutureResult` (called once per runId); `getRunResearch` and
    `getEvidencePack` are never called by the result view. ALTERNATIVE runs show the parent's groups and counts.
  - Panels start closed and stay independent (phase-01).
- Errors: as FR-23 (result endpoint: 404 RUN_NOT_FOUND "Future not found" / 409 RESULT_NOT_READY → failure view).
- Ranges & invariants (frontend unit, parameterized; E2E for the real path): groups 0 (absent → legacy), 1, 2, 3 in
  API order; per group 0 sources (→ `source-group-empty-*`, and `why-news-group-empty-*`), 1 and 5 sources; all groups
  empty → `sources-empty` only and no `source-group-*` while WHY THESE NEWS? still lists every group with its queries;
  fragments 0 / 1 / 3 → `source-not-retrieved` / 1 / 3 `source-fragment-*`; `contentRetrieved` × `usedInScenario` = 4
  classes, badge / excerpt present ⇔ flag; url classes `https://…`, `http://…` → link; `javascript:…`, `ftp://…`,
  `""` → `source-no-link`; date classes as FR-27; query status classes OK / EMPTY / FAILED / PENDING shown verbatim;
  count classes n = 0, 1, 2, 1234 for every count line and for `why-news-query-count` (`0 items`, `1 item`, `2
  items`, `1234 items`) and `why-news-group-sources` (`1 source`, `2 sources`); a source in two groups → two items
  with the same `source-id` text and both highlighted by one chip click; XSS classes (`<img src=x onerror=alert(1)>`
  as title, fragment, query text, custom label) render literally with no element. E2E invariants for every grouped
  result: group ids and headings = `getRunResearch` pipelines; per group the source items = the pack section's items
  (`getEvidencePack.wildcardSections`); `summary-*` numbers = `GET /api/runs/{id}` counts; `summary-kept` n = number of
  distinct evidenceIds over all groups; `summary-content` n ≤ `summary-kept` n; `summary-used` n = number of distinct
  evidenceIds with `usedInScenario`.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/result | getFutureResult | — | 200 FutureResult (+ optional `wildcardGroups`; `research.counts.sourcesKept` / `sourcesWithContent`) · 404 RUN_NOT_FOUND · 409 RESULT_NOT_READY · 500 INTERNAL_ERROR |

## UI
- Route `/futures/:runId` (result view), unchanged. Components in `src/app/result/`: the SOURCES and WHY THESE NEWS?
  panels gain the grouped layout (e.g. `wildcard-sources.ts` `app-wildcard-sources`, `wildcard-why-news.ts`
  `app-wildcard-why-news`, chosen by the existing panels when `wildcardGroups` is present). Material: `mat-card`,
  `mat-list`, `mat-chip` (status), `mat-divider`.
- States: loading (`result-loading`) · grouped with sources · grouped, some groups empty · grouped, all empty
  (`sources-empty`) · legacy (phase-01 layout) · error (failure view).
- `data-testid`s (new): `source-group-<pipelineId>`, `source-group-title-<pipelineId>`, `source-group-empty-<pipelineId>`,
  `source-item-<pipelineId>-<evidenceId>`, `source-id-<p>-<e>`, `source-title-<p>-<e>`, `source-publisher-<p>-<e>`,
  `source-date-<p>-<e>`, `source-link-<p>-<e>`, `source-no-link-<p>-<e>`, `source-used-<p>-<e>`,
  `source-excerpt-<p>-<e>`, `source-fragment-<p>-<e>-<k>`, `source-not-retrieved-<p>-<e>`,
  `why-news-group-<pipelineId>`, `why-news-group-title-<pipelineId>`, `why-news-query-<queryId>`,
  `why-news-query-text-<queryId>`, `why-news-query-status-<queryId>`, `why-news-query-count-<queryId>`,
  `why-news-group-sources-<pipelineId>`, `why-news-group-empty-<pipelineId>`, `summary-kept`, `summary-content`,
  `summary-used`. Kept from phase 01: `open-sources`, `sources-panel`, `sources-title`, `sources-empty`,
  `open-why-news`, `why-news-panel`, `why-news-title`, `research-summary`, `research-summary-title`,
  `summary-searches`, `summary-articles`, and the whole legacy set for legacy results.
