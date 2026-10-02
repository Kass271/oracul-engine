# Spec — Scenario Panel and main page

Covers: FR-1, FR-2, FR-3, FR-4, FR-5, FR-6, FR-34

## Purpose
One page that welcomes the user ("What happens next?") and lets them set the lens of the future: Realism, Darkness,
Optimism, Time Horizon, catalogue and custom wildcards, and output options. The panel stays usable before, during and
after a generation; on narrow screens it becomes a drawer. The panel produces a `ScenarioConfiguration`, which is the
only input of a Generation Run (generation-runs.md).

## Data

### ScenarioConfiguration (contract schema `ScenarioConfiguration`, snapshotted into every run)
| Entity | Field | Type | Rules |
|---|---|---|---|
| ScenarioConfiguration | realism | int | required, 1–10, default 8 |
| ScenarioConfiguration | darkness | int | required, 1–10, default 5 |
| ScenarioConfiguration | optimism | int | required, 1–10, default 5; independent of darkness (no coupling anywhere) |
| ScenarioConfiguration | horizon | HorizonCode | required, one of `1d 1w 1m 1y 5y 10y 20y`, default `1y` |
| ScenarioConfiguration | wildcards | WildcardSetting[] | required (may be empty), only enabled wildcards, ids unique and from the catalogue, ≤ 30 |
| WildcardSetting | wildcardId | string | catalogue id, `^[a-z0-9-]+$`, 1–60 chars |
| WildcardSetting | intensity | int | 1–10; UI default when enabled = 5 |
| ScenarioConfiguration | customWildcards | CustomWildcard[] | required (may be empty), ≤ 3 |
| CustomWildcard | label | string | trimmed, 1–40 characters after trimming; labels compared case-insensitively must be unique |
| CustomWildcard | intensity | int | 1–10, default 5 when added |
| ScenarioConfiguration | output.story | boolean | must be `true` (Story ON, the only output this version) |
| ScenarioConfiguration | output.illustration | boolean | must be `false` (MVP+1) |

Defaults (`ScenarioCatalogue.defaults`): realism 8, darkness 5, optimism 5, horizon `1y`, wildcards `[]`,
customWildcards `[]`, output `{story: true, illustration: false}`.

### Horizon options (order as shown)
| code | label | horizon end used elsewhere |
|---|---|---|
| `1d` | Tomorrow | cutoff + 1 day |
| `1w` | 1 week | cutoff + 7 days |
| `1m` | 1 month | cutoff + 1 month |
| `1y` | 1 year | cutoff + 1 year |
| `5y` | 5 years | cutoff + 5 years |
| `10y` | 10 years | cutoff + 10 years |
| `20y` | 20 years | cutoff + 20 years |

### Wildcard catalogue (spec §12) — served by `GET /api/scenario/catalogue`, static in the backend
| Category id | Category label | Wildcard id → label |
|---|---|---|
| ai | AI | ai-agi-breakthrough → AGI breakthrough · ai-stagnation → AI stagnation · ai-loss-of-control → AI loss of control |
| robotics | Robotics | robotics-massive-automation → Massive automation · robotics-humanoid-boom → Humanoid robot boom · robotics-robot-uprising → Robot uprising |
| biology | Biology | biology-new-pandemic → New pandemic · biology-dangerous-mutation → Dangerous mutation · biology-medical-breakthrough → Major medical breakthrough · biology-synthetic-biology → Synthetic biology breakthrough |
| political | Political / institutional | political-democracy-strengthens → Democratic institutions strengthen · political-authoritarian-expansion → Authoritarian systems expand · political-international-institutions → International institutions strengthen · political-global-fragmentation → Global fragmentation increases |
| economy | Economy | economy-global-boom → Global economic boom · economy-global-recession → Global recession · economy-financial-crisis → Financial crisis |
| energy | Energy | energy-fusion-breakthrough → Fusion breakthrough · energy-cheap-energy → Cheap energy · energy-energy-crisis → Energy crisis |
| environment | Environment | environment-extreme-climate-event → Extreme climate event · environment-climate-stabilization → Climate stabilization · environment-ecosystem-collapse → Ecosystem collapse |
| space | Space | space-major-discovery → Major space discovery · space-asteroid-threat → Asteroid threat · space-moon-settlement → Moon settlement · space-mars-breakthrough → Mars breakthrough |
| extreme | Extreme speculation | extreme-alien-contact → Alien contact · extreme-unknown-intelligence → Unknown intelligence · extreme-unexplained-phenomenon → Unexplained global phenomenon |

Acceptance configuration (spec §46) maps "Viruses 8" → `biology-new-pandemic` 8 and "Robotics 6" →
`robotics-humanoid-boom` 6.

`ScenarioLimits`: intensityMin 1, intensityMax 10, customWildcardMax 3, customWildcardLabelMaxLength 40,
defaultWildcardIntensity 5.

### Current configuration
`GET /api/scenario/configuration` returns the configuration of the newest run of this session (any status), or the
defaults when the session has no run. No separate table — read from `generation_run.configuration`.

## Behaviour

### FR-1 — Main page welcome state
- Happy path: `GET /` renders the header (wordmark "ORACUL", ChatGPT connection control — chatgpt-connection.md), the
  Scenario Panel on the left and the center welcome view with "What happens next?" and the button
  "GENERATE THE FUTURE". On load the app calls `getScenarioCatalogue`, `getScenarioConfiguration` and
  `getChatGptConnection` in parallel; the panel renders when catalogue + configuration arrived.
- Rules: every visible occurrence of the product name is exactly "ORACUL" (page `<title>` "ORACUL", wordmark,
  messages); the string "Oracle" never appears in templates or messages. Dark theme (Material 3), English only.
- Errors:
  - catalogue or configuration request fails (network error, 502/503/504 from the proxy, or any 5xx) → no API error
    shown raw → center shows `backend-unavailable` "ORACUL is unavailable — try again shortly" with a
    "Try again" button (`backend-retry`) that repeats the three loads; the panel is not rendered.
  - `GET /api/scenario/catalogue` unexpected backend failure → 500 `INTERNAL_ERROR` → same banner as above.

### FR-2 — Intensity controls: Darkness, Optimism, Realism
- Happy path: three `mat-slider`s (discrete, step 1, min 1, max 10, thumb label) in the order Darkness, Optimism,
  Realism, each with its current value shown next to the label ("Darkness 9"). Initial values come from
  `getScenarioConfiguration` (fresh session = defaults 8/5/5).
- Rules: changing one slider never changes another. Values are integers. The panel state is held in a signal store
  shared by panel, quick controls (FR-29) and the start button; it survives opening/closing the mobile drawer.
- Errors (backend, applies to `POST /api/runs` body — the run is not created):
  - `darkness` 0, 11, missing or non-integer → 400 `VALIDATION_FAILED` → "darkness must be between 1 and 10"
  - `optimism` out of range/missing → 400 `VALIDATION_FAILED` → "optimism must be between 1 and 10"
  - `realism` out of range/missing → 400 `VALIDATION_FAILED` → "realism must be between 1 and 10"
  - body not valid JSON → 400 `VALIDATION_FAILED` → "Request body is not valid JSON"

### FR-3 — Time Horizon
- Happy path: a `mat-button-toggle-group` (single selection) with the 7 options in the table order, labels as in the
  table; fresh session selects "1 year".
- Rules: exactly one option selected at any time; clicking the selected option keeps it selected.
- Errors (backend, `POST /api/runs`):
  - `horizon` not one of the 7 codes (e.g. `"3y"`) or missing → 400 `VALIDATION_FAILED` → "unknown horizon"

### FR-4 — Wildcard catalogue
- Happy path: the WILDCARDS section shows each category (label as heading, collapsed `mat-expansion-panel` per
  category) with its wildcards as `mat-slide-toggle`s, all off on a fresh session. Enabling a wildcard shows its
  intensity slider (1–10, default 5) and the text "<label> <n>/10" (e.g. "New pandemic 8/10").
- Rules: only enabled wildcards are sent in `configuration.wildcards`; disabling removes the entry (its intensity is
  forgotten and resets to 5 when re-enabled). Wildcards are scenario assumptions, never political endorsements.
- Errors (backend, `POST /api/runs`):
  - `wildcardId` not in the catalogue → 400 `VALIDATION_FAILED` → "unknown wildcard: <id>"
  - same `wildcardId` twice → 400 `VALIDATION_FAILED` → "duplicate wildcard: <id>"
  - `intensity` outside 1–10 or missing → 400 `VALIDATION_FAILED` → "wildcard intensity must be between 1 and 10"

### FR-5 — Custom wildcard
- Happy path: below the catalogue a `mat-form-field` input (`custom-wildcard-input`) and an "Add" button
  (`custom-wildcard-add`). Pressing Add (or Enter) with a valid label adds an enabled custom wildcard with intensity 5,
  shown with its own slider and a remove icon button (`custom-wildcard-remove-<index>`); the input is cleared.
- Rules: label trimmed; 1–40 characters; at most 3; a label equal (case-insensitive) to an existing custom label is
  rejected; removing frees a slot.
- Errors:
  - UI: empty / whitespace-only / > 40 characters → `mat-error` "Wildcard name must be 1–40 characters", nothing added
  - UI: 3 already exist and Add is pressed → `mat-error` "At most 3 custom wildcards", nothing added (the Add button
    stays enabled so the message can be shown)
  - UI: duplicate label → `mat-error` "This wildcard already exists", nothing added
  - API `POST /api/runs`: label empty/blank or > 40 after trim → 400 `VALIDATION_FAILED` → "Wildcard name must be 1–40 characters"
  - API: more than 3 custom wildcards → 400 `VALIDATION_FAILED` → "At most 3 custom wildcards"
  - API: duplicate custom label → 400 `VALIDATION_FAILED` → "This wildcard already exists"
  - API: custom intensity outside 1–10 → 400 `VALIDATION_FAILED` → "wildcard intensity must be between 1 and 10"

### FR-6 — Output settings
- Happy path: OUTPUT section with two `mat-checkbox`es: "Story" checked, "Illustration" unchecked + disabled with a
  `mat-chip`/badge "MVP+1".
- Rules: Illustration can never become checked (disabled control, click is a no-op). Story stays checked (it is the
  only output in this version; the checkbox is shown checked and read-only).
- Errors (backend, `POST /api/runs`):
  - `output.illustration` = true → 400 `VALIDATION_FAILED` → "Illustration is not available yet (MVP+1)"
  - `output.story` = false or `output` missing → 400 `VALIDATION_FAILED` → "Story output is required"

### FR-34 — Mobile layout
- Happy path: viewport width < 768 px → the panel lives in a `mat-sidenav` in `over` mode, closed by default; the
  header shows a "Scenario" button (`scenario-drawer-toggle`) that opens it; a close button (`scenario-drawer-close`)
  or backdrop click closes it. The center content fills the width (no horizontal scroll at 390 px).
- Rules: ≥ 768 px → `side` mode, always open, no "Scenario" button. Panel values are kept in the shared store, so
  closing the drawer never resets them. Starting a generation from the drawer closes it.
- Errors: none (pure layout).

### Validation precedence (all 400s above)
The backend reports the first violation in this order: JSON syntax → realism → darkness → optimism → horizon →
wildcards (in array order: id unknown, duplicate, intensity) → customWildcards (count, then per item label, duplicate,
intensity) → output. Exactly one message per response.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/scenario/catalogue | getScenarioCatalogue | — | 200 ScenarioCatalogue · 500 ApiError INTERNAL_ERROR |
| GET | /api/scenario/configuration | getScenarioConfiguration | — | 200 ScenarioConfiguration · 500 ApiError INTERNAL_ERROR |

The validation errors listed above are produced by `startRun` (generation-runs.md), which takes this configuration as
its body.

## UI
- Route: `/` (also `/futures/:runId`, recent-futures.md) · lazy component `ScenarioPanelComponent` in
  `src/app/scenario/` · Material: `mat-toolbar`, `mat-sidenav-container`, `mat-slider`, `mat-button-toggle-group`,
  `mat-expansion-panel`, `mat-slide-toggle`, `mat-form-field` + `mat-error`, `mat-checkbox`, `mat-chip`.
- States: loading (`app-loading` spinner until catalogue + configuration) · error (`backend-unavailable`) ·
  ready (panel + welcome) · drawer open/closed on mobile.
- `data-testid`s:
  - page: `app-header`, `app-wordmark` (text "ORACUL"), `app-loading`, `backend-unavailable`, `backend-retry`
  - welcome: `welcome-view`, `welcome-question` ("What happens next?"), `generate-button` ("GENERATE THE FUTURE")
  - panel: `scenario-panel`, `slider-darkness`, `slider-optimism`, `slider-realism`, `value-darkness`,
    `value-optimism`, `value-realism`
  - horizon: `horizon-group`, `horizon-option-1d`, `horizon-option-1w`, `horizon-option-1m`, `horizon-option-1y`,
    `horizon-option-5y`, `horizon-option-10y`, `horizon-option-20y`
  - wildcards: `wildcard-category-<categoryId>`, `wildcard-toggle-<wildcardId>`, `wildcard-intensity-<wildcardId>`,
    `wildcard-label-<wildcardId>` (text "<label> <n>/10" when enabled)
  - custom: `custom-wildcard-input`, `custom-wildcard-add`, `custom-wildcard-error`, `custom-wildcard-<index>`
    (0-based), `custom-wildcard-intensity-<index>`, `custom-wildcard-remove-<index>`
  - output: `output-story`, `output-illustration`, `output-illustration-badge` ("MVP+1")
  - mobile: `scenario-drawer-toggle` ("Scenario"), `scenario-drawer-close`
- Keyboard: every control reachable by Tab with visible focus, sliders operable with arrow keys, accessible names
  equal to the visible labels (NFR-5).
