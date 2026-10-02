# Spec — Future result (story, metadata, WHY, SOURCES, WHY THESE NEWS?)

Covers: FR-23, FR-25, FR-26, FR-27, FR-28

## Purpose
Turn the validated structured scenario into an engaging "story from the future" that can never be mistaken for real
news, and make it transparent: the settings and counts that produced it, the user-facing causal chain (not model
chain-of-thought), the real sources with their Evidence IDs, and why ORACUL researched what it researched.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| future_story | run_id | uuid | PK/FK, one per COMPLETED run |
| future_story | headline | text | 1–160 chars |
| future_story | dateline | varchar | `ORACUL FUTURE — <Month d, yyyy>` of futureDate (English month name) |
| future_story | future_date | date | > cutoff date and ≤ cutoff date + horizon (`1d` → exactly cutoff + 1 day) |
| future_story | body | text | plain text, paragraphs separated by a blank line, 150–900 words, no HTML/Markdown |
| future_story | created_at | timestamptz | |

`FutureResult` (GET /api/runs/{runId}/result) is assembled from run, story, final scenario attempt, Evidence Pack and
search plan:
| Field | Source |
|---|---|
| labels | constant `["AI-GENERATED FUTURE SCENARIO", "POSSIBLE FUTURE — NOT CURRENT NEWS"]` |
| story | future_story |
| metadata.configuration / horizonLabel | run configuration; horizon label as in scenario-panel.md |
| metadata.wildcards | enabled catalogue wildcards (catalogue label) then custom wildcards, each with intensity |
| metadata.counts | run counts |
| causalChain | final (guard-cleaned) structured scenario causalChain |
| sources | every Evidence Pack item in E-order: evidenceId, section, title / publisher / publishedAt / url of its first (highest-quality) source; `usedInScenario` = evidenceId cited by a fact in `factsUsed`; `counterSignal` = section COUNTER_SIGNAL |
| research.intents | search plan intents with `drivenBy` |
| research.counts | run counts (`sourcesUsed` = number of items with usedInScenario) |
| openCriticIssues | issues of the last critic report when the run completed with a failing critic, else [] |

## Behaviour

### FR-23 — Final future story with AI labels
- Happy path: stage WRITING_STORY sends a tool-less STORY_WRITING call (Closed Evidence Mode; data block
  `structured-scenario`; SETTINGS include the allowed date window and the futureEvent date) returning
  `{headline, dateline, futureDate, body}`; the backend validates, stores future_story, sets run headline,
  status COMPLETED, completed_at. The result view (`result-view`) shows both labels (`label-ai-generated`,
  `label-not-current-news`) above the headline (`story-headline`), then the dateline (`story-dateline`) and body
  (`story-body`).
- Rules: the backend overrides the model's dateline with one formatted from futureDate. futureDate outside the window
  or body/headline length out of bounds → one retry with the violation; still outside → futureDate = futureEvent
  date of the scenario (already guard-checked) and the dateline is rebuilt; headline/body bounds failing twice →
  INVALID_SCENARIO. Labels are rendered by the frontend from `labels` and are not part of the generated text; the
  story never shows today's date. Body is rendered as text (no `innerHTML`).
- Errors:
  - story call 429 / unavailable / expired → run FAILED with those codes (generation-runs.md)
  - story output invalid twice → run FAILED `INVALID_SCENARIO` "ORACUL could not construct a valid scenario"
  - `GET /api/runs/{runId}/result` for an active / failed / insufficient run → 409 `RESULT_NOT_READY` → "This future
    is not ready yet"; unknown / other session's run → 404 `RUN_NOT_FOUND` → "Future not found"

### FR-25 — Scenario metadata panel
- Happy path: the result view shows a metadata panel (`scenario-metadata`) with "Realism 8/10" (`meta-realism`),
  "Darkness 9/10" (`meta-darkness`), "Optimism 2/10" (`meta-optimism`), "Horizon 5 years" (`meta-horizon`), one line
  per wildcard "<label> <n>/10" (`meta-wildcard-<index>`), and "Articles considered: <n>"
  (`meta-articles-considered`), "Unique events: <n>" (`meta-unique-events`), "Evidence used: <n>"
  (`meta-evidence-used`, = eventsSelected).
- Rules: values come from the run snapshot, never from the current panel state; no wildcards → the wildcard block is
  omitted. When `openCriticIssues` is non-empty the panel shows a notice (`critic-issues`) listing their descriptions
  under "Open questions from ORACUL's critic".
- Errors: as FR-23 (result endpoint); UI shows the failure view with "Future not found" / "Try again".

### FR-26 — WHY COULD THIS HAPPEN?
- Happy path: button "WHY COULD THIS HAPPEN?" (`open-why`) toggles the WHY panel (`why-panel`) listing the causal
  chain in order as cards (`why-step-<order>`), each with a class label (`why-step-class-<order>`): FACT, INFERENCE,
  SPECULATION or "ORACUL FUTURE — <year>" for FUTURE_EVENT; FACT and INFERENCE steps show their Evidence IDs as
  chips (`why-evidence-<order>-<evidenceId>`), connected by "↓".
- Rules: clicking an Evidence ID chip opens the SOURCES panel and scrolls to / highlights that item
  (`source-item-<evidenceId>` gets the `highlighted` state for 3 s). The chain is the guard-cleaned one; no private
  model reasoning is shown.
- Errors: an Evidence ID without a matching source item (should not happen after the guard) → chip rendered
  disabled, no navigation.

### FR-27 — SOURCES view
- Happy path: button "SOURCES" (`open-sources`) toggles the SOURCES panel (`sources-panel`) listing every evidence item
  (`source-item-<evidenceId>`) with Evidence ID, title, publisher, publication date (`d MMM yyyy`, "date unknown" when
  missing) and an external link (`source-link-<evidenceId>`, `target="_blank"`, `rel="noopener noreferrer"`).
- Rules: badges "used in scenario" (`source-used-<evidenceId>`) for usedInScenario and "counter-signal"
  (`source-counter-<evidenceId>`) for counter-signals; both may apply. Only `http`/`https` URLs are rendered as links.
- Errors: as FR-23 (result endpoint).

### FR-28 — WHY THESE NEWS? and research summary
- Happy path: button "WHY THESE NEWS?" (`open-why-news`) toggles the panel (`why-news-panel`) listing each research
  intent (`why-news-intent-<intentId>`) with its description and its `drivenBy` attributions as chips
  (e.g. "New pandemic 8/10", "Darkness 9/10"), followed by the research summary (`research-summary`) with six numbers:
  "<n> searches performed" (`summary-searches`), "<n> articles considered" (`summary-articles`),
  "<n> unique events identified" (`summary-events`), "<n> events selected" (`summary-selected`),
  "<n> counter-signals retained" (`summary-counter-signals`), "<n> sources directly influenced the scenario"
  (`summary-sources-used`).
- Rules: numbers are the run's counts (searches, articlesConsidered, uniqueEvents, eventsSelected, counterSignals,
  sourcesUsed) — identical to `GET /api/runs/{runId}` counts. For ALTERNATIVE runs intents and counts are the
  parent's (same pack).
- Errors: as FR-23 (result endpoint).

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/result | getFutureResult | — | 200 FutureResult · 404 RUN_NOT_FOUND · 409 RESULT_NOT_READY · 500 INTERNAL_ERROR |

## UI
- Route: `/futures/:runId` (center area; also reached right after a run completes) · components in `src/app/result/`:
  `FutureResultComponent` → `FutureStoryComponent`, `ScenarioMetadataComponent`, `WhyPanelComponent`,
  `SourcesPanelComponent`, `WhyNewsPanelComponent` (+ `QuickActionsComponent` from generation-runs.md) · Material:
  `mat-card`, `mat-chip-set`, `mat-button`, `mat-list`, `mat-divider`, `mat-icon`.
- States: loading (`result-loading`) · success (`result-view`) · error (`failure-view` via generation-runs.md) — the
  three panels are closed initially and independent (several may be open).
- `data-testid`s: `result-view`, `result-loading`, `label-ai-generated`, `label-not-current-news`, `story-headline`,
  `story-dateline`, `story-body`, `scenario-metadata`, `meta-realism`, `meta-darkness`, `meta-optimism`,
  `meta-horizon`, `meta-wildcard-<index>`, `meta-articles-considered`, `meta-unique-events`, `meta-evidence-used`,
  `critic-issues`, `open-why`, `why-panel`, `why-step-<order>`, `why-step-class-<order>`,
  `why-evidence-<order>-<evidenceId>`, `open-sources`, `sources-panel`, `source-item-<evidenceId>`,
  `source-link-<evidenceId>`, `source-used-<evidenceId>`, `source-counter-<evidenceId>`, `open-why-news`,
  `why-news-panel`, `why-news-intent-<intentId>`, `research-summary`, `summary-searches`, `summary-articles`,
  `summary-events`, `summary-selected`, `summary-counter-signals`, `summary-sources-used`.
