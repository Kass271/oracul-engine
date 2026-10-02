# Spec — Session state and Recent futures

Covers: FR-33

## Purpose
Keep everything a user generated in their anonymous browser session — configuration, runs, scenarios, Evidence
Packs, sources — in PostgreSQL (never credentials), and let them reopen any of their 20 newest futures with all its
views. Other browser sessions never see these runs.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| browser_session | id | uuid | from cookie `ORACUL_SID` (chatgpt-connection.md); created by `SessionFilter` on the first `/api` request without a valid cookie |
| generation_run | session_id | uuid | every run, pack, source, event, attempt and story is reached only via a run of the caller's session |
| RecentRunSummary | id, generationId, kind, createdAt, completedAt, headline, configuration | | from generation_run (status COMPLETED) |

Rules for the session filter: cookie missing, not a UUID or unknown → new browser_session row and new cookie
(`Set-Cookie: ORACUL_SID=<uuid>; Path=/; HttpOnly; SameSite=Lax`). The session id is not a ChatGPT credential and
is never logged in full (first 8 characters only).

## Behaviour

### FR-33 — Session state and Recent futures
- Happy path: `GET /api/runs` → `{items}`: the 20 newest COMPLETED runs of the session ordered by created_at desc
  (ties by id). The header button "Recent futures" (`recent-futures-button`) opens a `mat-menu`/side sheet
  (`recent-futures-list`) with one entry per run (`recent-future-<runId>`) showing time (`HH:mm`, local, plus date when
  not today), headline and key settings "R<n> D<n> O<n> · <horizon label>". Selecting an entry navigates to
  `/futures/<runId>`, which loads `getRun` + `getFutureResult` and shows that run's story, metadata, WHY, SOURCES and
  WHY THESE NEWS? data and puts its configuration into the panel.
- Rules: runs of other sessions are invisible (list filtered by session; direct access → 404). Runs beyond the 20
  newest stay stored but are not listed. FAILED / INSUFFICIENT_EVIDENCE / active runs are not listed. The list
  refreshes whenever a run completes. Empty list → text "No futures yet" (`recent-futures-empty`).
  `GET /api/scenario/configuration` returns the newest run's configuration (scenario-panel.md), so a reload restores
  the panel.
- Errors:
  - `/futures/<runId>` with an unknown, malformed or other session's id → `getRun` 404 `RUN_NOT_FOUND` → failure view
    "Future not found" with "Try again" hidden and a "Back to ORACUL" link (`back-home`) to `/`
  - list request fails (network/5xx) → the menu shows "Recent futures are unavailable" (`recent-futures-error`);
    backend unexpected failure → 500 `INTERNAL_ERROR` → "Something went wrong — try again"

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs | listRecentRuns | — | 200 RecentRunList (≤ 20 items) · 500 INTERNAL_ERROR |

Reopening uses `getRun` (generation-runs.md), `getFutureResult` (future-result.md).

## UI
- Route: `/futures/:runId` · component `RecentFuturesComponent` in `src/app/history/` (header) · Material:
  `mat-menu` (desktop) / bottom sheet on < 768 px, `mat-list`, `mat-icon-button`.
- States: loading (`recent-futures-loading`) · empty · error · list.
- `data-testid`s: `recent-futures-button`, `recent-futures-list`, `recent-future-<runId>`,
  `recent-future-headline-<runId>`, `recent-futures-empty`, `recent-futures-error`, `recent-futures-loading`,
  `back-home`.
