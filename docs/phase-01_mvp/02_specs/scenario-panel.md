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
  - `darkness` 0, 11, -1, missing, `null`, a fraction (`5.5`), a string (`"9"`) or a boolean → 400 `VALIDATION_FAILED`
    → "darkness must be between 1 and 10" (JSON numbers must be integers; no coercion from strings or fractions)
  - `optimism` same inputs → 400 `VALIDATION_FAILED` → "optimism must be between 1 and 10"
  - `realism` same inputs → 400 `VALIDATION_FAILED` → "realism must be between 1 and 10"
  - body not valid JSON, empty body, or a JSON value that is not an object (`[]`, `"x"`) → 400 `VALIDATION_FAILED` →
    "Request body is not valid JSON"
  - `Content-Type` not `application/json` → 400 `VALIDATION_FAILED` → "Request body is not valid JSON"
  - Unknown extra properties in the body are ignored (no error).

### FR-3 — Time Horizon
- Happy path: a `mat-button-toggle-group` (single selection) with the 7 options in the table order, labels as in the
  table; fresh session selects "1 year".
- Rules: exactly one option selected at any time; clicking the selected option keeps it selected.
- Errors (backend, `POST /api/runs`):
  - `horizon` not one of the 7 codes (e.g. `"3y"`, `"1Y"` — codes are case-sensitive, `""`), missing, `null` or not a
    string (e.g. `5`) → 400 `VALIDATION_FAILED` → "unknown horizon"

### FR-4 — Wildcard catalogue
- Happy path: the WILDCARDS section shows each category (label as heading, collapsed `mat-expansion-panel` per
  category) with its wildcards as `mat-slide-toggle`s, all off on a fresh session. Enabling a wildcard shows its
  intensity slider (1–10, default 5) and the text "<label> <n>/10" (e.g. "New pandemic 8/10").
- Rules: only enabled wildcards are sent in `configuration.wildcards`; disabling removes the entry (its intensity is
  forgotten and resets to 5 when re-enabled). Wildcards are scenario assumptions, never political endorsements.
- Errors (backend, `POST /api/runs`; elements are checked in array order, per element id → duplicate → intensity):
  - `wildcardId` a string not in the catalogue (incl. wrong case, pattern violation, > 60 chars, `""`) → 400
    `VALIDATION_FAILED` → "unknown wildcard: <id>" (the received string verbatim)
  - `wildcardId` missing, `null` or not a string → 400 `VALIDATION_FAILED` → "wildcards[<i>].wildcardId is invalid"
  - same `wildcardId` as an earlier element → 400 `VALIDATION_FAILED` → "duplicate wildcard: <id>"
  - `intensity` outside 1–10, missing, `null`, a fraction, a string or a boolean → 400 `VALIDATION_FAILED` →
    "wildcard intensity must be between 1 and 10"
  - `wildcards` missing, `null`, not an array, or an element that is not an object → 400 `VALIDATION_FAILED` →
    "wildcards is invalid"
  - more than 30 entries: never reported on its own — 31 entries always contain an unknown or duplicate id, which is
    reported first.

### FR-5 — Custom wildcard
- Happy path: below the catalogue (inside the WILDCARDS section) a `mat-form-field` input (`custom-wildcard-input`)
  and an "Add" button (`custom-wildcard-add`). Pressing Add (or Enter in the input) with a valid label adds an enabled
  custom wildcard (label trimmed) with intensity 5, shown as a row with its label text "<label> <n>/10", its own slider
  and a remove icon button (`custom-wildcard-remove-<index>`); the input is cleared. Custom wildcards have no on/off
  toggle: present = enabled = sent in `configuration.customWildcards` (panel order).
- Rules: label trimmed; 1–40 characters after trimming; at most 3; a label equal (trimmed, case-insensitive) to an
  existing custom label is rejected (catalogue labels are not compared); removing frees a slot. UI check order on Add:
  count → label length → duplicate (first failing rule's message is shown).
- Errors:
  - UI: 3 already exist and Add is pressed → `mat-error` "At most 3 custom wildcards", nothing added (the Add button
    stays enabled so the message can be shown)
  - UI: empty / whitespace-only / > 40 characters after trim → `mat-error` "Wildcard name must be 1–40 characters",
    nothing added
  - UI: duplicate label → `mat-error` "This wildcard already exists", nothing added
  - API `POST /api/runs`: `customWildcards` missing / `null` / not an array / an element that is not an object →
    400 `VALIDATION_FAILED` → "customWildcards is invalid"
  - API: more than 3 elements → 400 `VALIDATION_FAILED` → "At most 3 custom wildcards"
  - API: `label` empty/blank or > 40 characters after trim, missing, `null` or not a JSON string → 400
    `VALIDATION_FAILED` → "Wildcard name must be 1–40 characters"
  - API: label equal (trimmed, case-insensitive) to an earlier element's label → 400 `VALIDATION_FAILED` →
    "This wildcard already exists"
  - API: custom `intensity` outside 1–10, missing, `null`, a fraction, a string or a boolean → 400
    `VALIDATION_FAILED` → "wildcard intensity must be between 1 and 10"
- Changes earlier behaviour: `customWildcards` violations other than shape (e.g. 4 elements, label `""`, label of 41 characters, intensity 11) answered with the generic fallback "`customWildcards…` is invalid" → now the specific messages above; no existing test sends an invalid non-empty `customWildcards` (checked `StartRunValidationTest.java`, `StartRunWildcardValidationTest.java`, `ResearchPlanIT.java`, `e2e/tests/run-start.spec.ts`) (tests: none)
- Changes earlier behaviour: none for the panel — the WILDCARDS section gains `custom-wildcard-section` after `wildcard-section`; checked `wildcard-catalogue.spec.ts` (only asserts WILDCARDS after TIME HORIZON and the expansion panels inside `wildcard-section`), `app.spec.ts`, `scenario.store.spec.ts`, `scenario.store.wildcards.spec.ts`, `quick-actions.spec.ts` (loads a configuration with one custom wildcard, expects it carried unchanged — still true), `e2e/tests/wildcards.spec.ts`, `e2e/tests/scenario-controls.spec.ts` — none counts panel sections, inputs, sliders or buttons
- Ranges & invariants: label length after trim (UTF-16 units, JS `.length` / Java `String.length()`) classes 0 (`""`, `"   "`) → name error, 1 → ok, 40 → ok, 41 → name error, `"  " + 40 chars + "  "` → ok and stored as the 40 trimmed chars (UI and API); count classes 0, 1, 2, 3 → ok, 4 → "At most 3 custom wildcards" (API: any 4-element array, even if an element is also invalid; UI: Add with any input while 3 exist); intensity classes 0, 1, 5, 10, 11 → 1/5/10 ok, 0/11 error (API), UI slider only yields 1–10; duplicate classes: identical, different case (`"mars colony"` vs `"Mars Colony"`), different only in surrounding spaces → duplicate; different inner spacing (`"Mars  colony"`) or equal to a catalogue label (`"New pandemic"`) → ok; API precedence inside `customWildcards`: shape → count → per element in array order (label → duplicate → intensity); invariants: `configuration().customWildcards` lists exactly the rows shown, in row order, labels trimmed, ≤ 3, no two labels equal case-insensitively; row count shown = `customWildcards.length`; adding/removing/changing custom wildcards never changes realism/darkness/optimism/horizon/wildcards/output; indices are 0-based and renumber after a removal (row 2 becomes row 1); an accepted run stores (and returns in `configuration`) the trimmed labels in request order and its ResearchProfile has one topic `custom-<n>` (1-based, request order) per custom wildcard after the catalogue topics.

### FR-6 — Output settings
- Happy path: after the WILDCARDS section the heading "OUTPUT" and `output-section` with two `mat-checkbox`es:
  `output-story` "Story" checked, `output-illustration` "Illustration" unchecked + disabled with a `mat-chip`/badge
  `output-illustration-badge` "MVP+1".
- Rules: Illustration can never become checked (disabled control, click is a no-op). Story stays checked (it is the
  only output in this version; the checkbox is shown checked and read-only: clicking it keeps it checked). The panel
  always sends `output` `{"story":true,"illustration":false}`.
- Errors (backend, `POST /api/runs`, checked after customWildcards; story before illustration):
  - `output` missing, `null` or not an object; `output.story` anything other than JSON `true` (`false`, missing,
    `null`, `"true"`, `1`) → 400 `VALIDATION_FAILED` → "Story output is required"
  - `output.story` true and `output.illustration` anything other than JSON `false` (`true`, missing, `null`,
    `"false"`, `0`) → 400 `VALIDATION_FAILED` → "Illustration is not available yet (MVP+1)"
- Changes earlier behaviour: `output` violations answered with the generic fallback "`output…` is invalid" (or were accepted, e.g. `story` false) → now the two messages above; no existing backend/E2E test sends an invalid `output` (checked `StartRunValidationTest.java`, `StartRunWildcardValidationTest.java`, `e2e/tests/run-start.spec.ts`, `e2e/tests/quick-regeneration.spec.ts`); `scenario.store.spec.ts` loads `{story:false, illustration:true}` and expects `configuration()` to equal it — the store keeps carrying the loaded `output` unchanged (only the checkboxes are fixed), so it stays green (tests: none)
- Ranges & invariants: `output.story` classes `true` → ok; `false`, missing, `null`, `"true"`, `1` → "Story output is required"; `output.illustration` classes `false` → ok; `true`, missing, `null`, `"false"`, `0` → "Illustration is not available yet (MVP+1)"; `output` missing / `null` / `"x"` / `[]` → "Story output is required"; story false and illustration true together → "Story output is required"; any custom wildcard violation together with an output violation → the custom wildcard message; invariants: in the UI `output-story` is checked and `output-illustration` unchecked + disabled on every load (fresh session and loaded configuration) and after any number of clicks on either; the panel's `startRun` body carries `output` `{"story":true,"illustration":false}` in every real session (defaults and every stored configuration have exactly this value).

### FR-34 — Mobile layout
- Happy path: viewport width ≤ 767 px (CDK media query `(max-width: 767.98px)`) → the panel lives in the
  `mat-sidenav` `scenario-drawer` in `over` mode, closed on page load; the header shows the "Scenario" button
  `scenario-drawer-toggle` that opens it; the close button `scenario-drawer-close`, a backdrop click or Escape closes
  it. The center content (`mat-sidenav-content`) starts at x = 0 and is as wide as the viewport; the page has no
  horizontal scroll at 390 px.
- Rules: width ≥ 768 px → `side` mode, always open, `disableClose`, no `scenario-drawer-toggle` and no
  `scenario-drawer-close` in the DOM (desktop unchanged). Panel values live in the shared `ScenarioStore`, so
  opening/closing the drawer never resets or changes them (the panel component is not re-created). Crossing the
  breakpoint at runtime switches the mode; entering mobile mode always starts closed. The generate button stays in
  the center (welcome view), not in the drawer. Loading/error states render no sidenav and no toggle (as FR-1).
- Errors: none (pure layout, no API call, no `ApiError.code`).
- Changes earlier behaviour: below 768 px the panel was a 300 px `side` sidenav always open next to the center → now an `over` drawer closed on load plus a "Scenario" header button; ≥ 768 px is unchanged, Vitest (jsdom, no `matchMedia` → CDK reports no match → desktop) and the E2E project (Desktop Chrome 1280×720) both run in desktop mode, and no existing test sets a narrow viewport (grep `setViewportSize|viewport|matchMedia|sidenav|scenario-drawer` in `frontend/src/app/**/*.spec.ts` and `e2e/tests`: no hits; `app-header` assertions in `app.spec.ts`, `chatgpt-connection.spec.ts`, `smoke.spec.ts`, `scenario-controls.spec.ts` only check containment/order of wordmark and status, which stay) (tests: none)
- Ranges & invariants: viewport width classes — 360, 390, 414, 767 px → mobile (`over`, closed on load, toggle present, `scenario-panel` hidden); 768, 1024, 1280 px → desktop (`side`, open, `scenario-panel` visible, no toggle/close); invariants for every mobile width 360/390/414/767 with the drawer closed: `document.documentElement.scrollWidth ≤ window.innerWidth`, `mat-sidenav-content` bounding box x = 0 and width = viewport width (±1 px), every header child's bounding box lies within [0, viewport width]; for any sequence of open/close (toggle, close button, backdrop, Escape) and any Darkness value 1–10 set while open, after closing `ScenarioStore.darkness()` (and the whole `configuration()`) equals the last value set and `value-darkness` shows it again on reopen; the toggle's `aria-expanded` always equals the drawer's opened state.

### Validation precedence (all 400s above)
The backend reports the first violation in this order: JSON syntax → realism → darkness → optimism → horizon →
wildcards (in array order: id unknown, duplicate, intensity) → customWildcards (shape, count, then per item in array
order: label, duplicate, intensity) → output (story, then illustration). Exactly one message per response.
Any Bean Validation / deserialization violation on a field not named above (e.g. `wildcards` or `customWildcards`
missing) → 400 `VALIDATION_FAILED` → "`<field path>` is invalid" (e.g. "wildcards is invalid") — never a 500. Later
slices replace this fallback with the specific messages of FR-4/5/6.

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
  - welcome hint: `generate-hint` ("Connect ChatGPT to generate", while generation is not possible)
  - panel: `scenario-panel`, `slider-darkness`, `slider-optimism`, `slider-realism` (the `mat-slider`),
    `slider-darkness-input`, `slider-optimism-input`, `slider-realism-input` (the `matSliderThumb` range input),
    `label-darkness`, `label-optimism`, `label-realism`, `value-darkness`, `value-optimism`, `value-realism`
    (text = the integer only)
  - horizon: `horizon-group`, `horizon-option-1d`, `horizon-option-1w`, `horizon-option-1m`, `horizon-option-1y`,
    `horizon-option-5y`, `horizon-option-10y`, `horizon-option-20y`
  - wildcards: `wildcard-section`, `wildcard-category-<categoryId>`, `wildcard-category-header-<categoryId>`,
    `wildcard-toggle-<wildcardId>`, `wildcard-label-<wildcardId>` (text "<label>" when off, "<label> <n>/10" when
    enabled), `wildcard-intensity-<wildcardId>` (the `mat-slider`, only when enabled),
    `wildcard-intensity-<wildcardId>-input` (its `matSliderThumb` input) — details in "Slice 03_wildcards"
  - custom: `custom-wildcard-section`, `custom-wildcard-input`, `custom-wildcard-add`, `custom-wildcard-error`,
    `custom-wildcard-<index>` (0-based row), `custom-wildcard-label-<index>` ("<label> <n>/10"),
    `custom-wildcard-intensity-<index>` (the `mat-slider`), `custom-wildcard-intensity-<index>-input` (its
    `matSliderThumb`), `custom-wildcard-remove-<index>` — details in "Slice 18_custom-wildcards-output"
  - output: `output-section`, `output-story`, `output-illustration`, `output-illustration-badge` ("MVP+1")
  - mobile: `scenario-drawer` (the `mat-sidenav`, both modes), `scenario-drawer-toggle` ("Scenario", mobile only),
    `scenario-drawer-close` (mobile only) — details in "Slice 19_mobile-layout"
- Keyboard: every control reachable by Tab with visible focus, sliders operable with arrow keys, accessible names
  equal to the visible labels (NFR-5).

## Slice 01_scenario-controls — test contract (FR-1, FR-2, FR-3)

What this slice delivers and exactly what its tests assert. Everything else in this spec is delivered by later slices
(03 wildcards, 18 custom wildcards/output, 19 mobile, 02 ChatGPT control, 04 run creation).

### Backend (`com.oracul.app.scenario`, `com.oracul.app.runs`, `com.oracul.app.common`)
`ScenarioController implements ScenarioApi`; `RunsController implements RunsApi` (only `startRun` in this slice);
`ApiExceptionHandler` (`@RestControllerAdvice`) in `com.oracul.app.common`. Operations of other tags/not yet built
keep the generated default (501) — tests do not touch them.

1. `GET /api/scenario/catalogue` → 200, `Content-Type: application/json`, body:
   - `horizons` = exactly, in this order:
     `[{"code":"1d","label":"Tomorrow"},{"code":"1w","label":"1 week"},{"code":"1m","label":"1 month"},`
     `{"code":"1y","label":"1 year"},{"code":"5y","label":"5 years"},{"code":"10y","label":"10 years"},`
     `{"code":"20y","label":"20 years"}]`
   - `defaults` = `{"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],"customWildcards":[],`
     `"output":{"story":true,"illustration":false}}`
   - `limits` = `{"intensityMin":1,"intensityMax":10,"customWildcardMax":3,"customWildcardLabelMaxLength":40,`
     `"defaultWildcardIntensity":5}`
   - `categories` is an array (its content is asserted by slice 03; this slice may already serve the full table).
2. `GET /api/scenario/configuration` → 200, body equal to `defaults` above (no sessions/runs exist yet; slice 15 adds
   "newest run's configuration").
3. `POST /api/runs` (`startRun`) — validation only in this slice. Base valid body used by every test:
   `{"realism":8,"darkness":5,"optimism":5,"horizon":"1y","wildcards":[],"customWildcards":[],`
   `"output":{"story":true,"illustration":false}}`
   | Test input (base body with …) | Status | `code` | `message` (exact) |
   |---|---|---|---|
   | `darkness` 0 / 11 / -1 / `null` / removed / `5.5` / `"9"` / `true` | 400 | VALIDATION_FAILED | `darkness must be between 1 and 10` |
   | `optimism` 0 / 11 / `null` / removed / `5.5` / `"9"` | 400 | VALIDATION_FAILED | `optimism must be between 1 and 10` |
   | `realism` 0 / 11 / `null` / removed / `5.5` / `"9"` | 400 | VALIDATION_FAILED | `realism must be between 1 and 10` |
   | `horizon` `"3y"` / `"1Y"` / `""` / `5` / `null` / removed | 400 | VALIDATION_FAILED | `unknown horizon` |
   | `realism` 0 and `darkness` 11 and `horizon` `"3y"` | 400 | VALIDATION_FAILED | `realism must be between 1 and 10` (precedence) |
   | `darkness` 11 and `horizon` `"3y"` | 400 | VALIDATION_FAILED | `darkness must be between 1 and 10` |
   | body `{"realism":` (truncated) / empty body / `[]` | 400 | VALIDATION_FAILED | `Request body is not valid JSON` |
   | valid JSON sent with `Content-Type: text/plain` | 400 | VALIDATION_FAILED | `Request body is not valid JSON` |
   | `wildcards` removed | 400 | VALIDATION_FAILED | `wildcards is invalid` (fallback, replaced in slice 03) |
   | base body; base body with all three = 1; all three = 10; each of the 7 horizons; `darkness` 9 + `optimism` 9; extra unknown property `"foo":1` | 401 | CHATGPT_NOT_CONNECTED | `Connect ChatGPT to generate` |
   - The 401 row proves the body passed validation: in this slice no ChatGPT connection can exist, so FR-10's check
     order (validation → connection) ends there. Nothing is stored for any row (there is no run table yet).
   - Every error response: `Content-Type: application/json`, body has exactly the keys `code` and `message`; no
     `trace`, `exception`, `path`, `timestamp`, `error` keys and no Java class names in `message`.

### Frontend
Files: `src/app/app.ts` (AppComponent shell), `src/app/scenario/` (`ScenarioPanelComponent`,
`IntensitySliderComponent`, `HorizonSelectorComponent`, `scenario.store.ts`), `src/app/center/`
(`WelcomeViewComponent`). Route `/` renders the shell; there is no other route in this slice.

- Load: on start the app calls `getScenarioCatalogue` and `getScenarioConfiguration` in parallel (the
  `getChatGptConnection` call joins in slice 02). While either is pending `app-loading` is shown and `scenario-panel`
  is absent. When both succeed the store is loaded from the configuration and the panel + welcome view render.
- Failure: if either call fails (network error/abort or any status ≥ 500, including 502/503/504 from the proxy) the
  center shows `backend-unavailable` with the exact text "ORACUL is unavailable — try again shortly" and a button
  `backend-retry` ("Try again"); `scenario-panel` and `welcome-view` are absent; `app-header` with `app-wordmark` is
  still shown. Clicking `backend-retry` shows `app-loading` and repeats both calls; on success the error is gone and
  panel + welcome render.
- Header: `app-header` (`mat-toolbar`) containing `app-wordmark` with text exactly "ORACUL". Document `<title>` is
  "ORACUL". No visible text, `title`, `aria-label` or `alt` anywhere contains "oracle" (case-insensitive).
- Welcome: `welcome-view` contains `welcome-question` text "What happens next?" and `generate-button` (a
  `mat-flat-button`, text "GENERATE THE FUTURE"). In this slice `generate-button` is always `disabled` and
  `generate-hint` shows "Connect ChatGPT to generate" (FR-10 rule `canGenerate = false`; slice 04 enables it).
- Panel (`scenario-panel`, in the `mat-sidenav` of a `mat-sidenav-container`, mode `side`, opened), top to bottom:
  section heading "INTENSITY", then three sliders in the order Darkness, Optimism, Realism, then section heading
  "TIME HORIZON" and the horizon group.
  - Each slider `<x>` ∈ {darkness, optimism, realism}: `mat-slider` with `data-testid="slider-<x>"`, `min` 1, `max`
    10, `step` 1, `discrete`; its `<input matSliderThumb>` has `data-testid="slider-<x>-input"` and
    `aria-label` "Darkness" / "Optimism" / "Realism". Label row: `label-<x>` with text "Darkness" / "Optimism" /
    "Realism" followed by `value-<x>` whose text is exactly the current integer (e.g. "9"). Fresh load:
    `value-realism` "8", `value-darkness` "5", `value-optimism` "5"; `slider-<x>-input` has `value` "8"/"5"/"5".
  - Tests change a slider either with Playwright `locator('[data-testid=slider-darkness-input]').fill('9')` (range
    input; the component reacts to `input` and `change` events) or by focusing the input and pressing
    ArrowRight/ArrowLeft/Home/End. After setting darkness 9 and optimism 9: `value-darkness` "9", `value-optimism`
    "9", `value-realism` still "8"; setting darkness never changes `value-optimism` and vice versa.
  - Horizon: `mat-button-toggle-group` `data-testid="horizon-group"` (single selection, `aria-label` "Time Horizon")
    with 7 `mat-button-toggle`s `data-testid="horizon-option-<code>"` in order `1d 1w 1m 1y 5y 10y 20y`, text from
    `catalogue.horizons[].label` ("Tomorrow", "1 week", "1 month", "1 year", "5 years", "10 years", "20 years").
    The selected toggle's host element (the `data-testid` element) has class `mat-button-toggle-checked`; all others
    do not — tests assert this class (Material's own inner-button ARIA attributes are not asserted). Fresh load: only `horizon-option-1y`
    selected. Clicking `horizon-option-5y` → only `5y` selected; clicking the already selected option keeps it
    selected (never zero selected).
- `ScenarioStore` (`src/app/scenario/scenario.store.ts`, `@Injectable({providedIn: 'root'})`), the unit-test
  surface:
  - read signals: `realism()`, `darkness()`, `optimism()`, `horizon()` (HorizonCode), `configuration()` (computed
    `ScenarioConfiguration`; in this slice `wildcards`/`customWildcards` `[]`, `output` `{story:true,
    illustration:false}` carried over from the loaded configuration);
  - `load(config: ScenarioConfiguration)` replaces the whole state;
  - `setRealism(n)`, `setDarkness(n)`, `setOptimism(n)`: accept integers 1–10; any other value (0, 11, 5.5, NaN)
    leaves the state unchanged; each setter changes only its own field;
  - `setHorizon(code)`: accepts one of the 7 codes; anything else leaves the state unchanged.
  - Before `load` the store holds the defaults 8/5/5/`1y`.

### Test locations and traces
- Backend: `backend/src/test/java/com/oracul/app/scenario/ScenarioControllerTest.java` (`// @trace FR-1, FR-2, FR-3`
  for the catalogue/defaults), `backend/src/test/java/com/oracul/app/runs/StartRunValidationTest.java`
  (`// @trace FR-2` slider rows, `// @trace FR-3` horizon rows).
- Frontend unit (Vitest): `scenario.store.spec.ts` (FR-2, FR-3), `app.spec.ts` (FR-1: loading, unavailable + retry,
  wordmark/title), `intensity-slider` / `horizon-selector` component specs (FR-2, FR-3).
- E2E (Playwright, `e2e/tests/scenario-controls.spec.ts`): FR-1 welcome + spelling; FR-1 unavailable via
  `page.route('**/api/scenario/**', r => r.fulfill({status: 503, body: ''}))` (or `r.abort()`), then `unroute` and
  click `backend-retry`; FR-2 defaults and independence; FR-3 default 1 year, 7 options, select 5 years. API-level
  acceptance (darkness 0/11, horizon "3y") may also be checked with Playwright `request.post('/api/runs', …)`.

## Slice 03_wildcards — test contract (FR-4)

Delivers the wildcard catalogue in the panel and the wildcard validation of `startRun`. Custom wildcards, output
settings (slice 18), run creation (slice 04) are not part of it. Slices 01 and 02 behaviour and tests stay unchanged
(incl. the fallback row "`wildcards` removed → `wildcards is invalid`").

### Backend (`com.oracul.app.scenario`, `com.oracul.app.common`)
1. `GET /api/scenario/catalogue` → 200; `categories` is exactly the 9 categories of the "Wildcard catalogue" table in
   table order (`ai, robotics, biology, political, economy, energy, environment, space, extreme`) with the table
   labels; each category's `wildcards` lists the table's wildcards in table order as
   `{"id":"<wildcardId>","label":"<label>","categoryId":"<categoryId>"}`. 30 wildcards in total, all ids unique.
   Examples tests assert verbatim: category `biology` label "Biology" with ids `biology-new-pandemic`,
   `biology-dangerous-mutation`, `biology-medical-breakthrough`, `biology-synthetic-biology`; `political` label
   "Political / institutional"; `extreme` label "Extreme speculation"; wildcard `biology-new-pandemic` label
   "New pandemic"; `robotics-humanoid-boom` label "Humanoid robot boom". `defaults.wildcards` is `[]`.
2. `POST /api/runs` (`startRun`) — base valid body as in slice 01. Without a ChatGPT connection (fresh session, no
   cookie) a body that passes validation answers `401 CHATGPT_NOT_CONNECTED` "Connect ChatGPT to generate" — that is
   the proof of "valid" in this slice. Every error body: exactly the keys `code`, `message`; nothing is stored.
   | Test input (base body with `wildcards` = …) | Status | `code` | `message` (exact) |
   |---|---|---|---|
   | `[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"robotics-humanoid-boom","intensity":6}]` | 401 | CHATGPT_NOT_CONNECTED | `Connect ChatGPT to generate` |
   | all 30 catalogue ids once each, intensities 1 and 10 alternating | 401 | CHATGPT_NOT_CONNECTED | `Connect ChatGPT to generate` |
   | `[{"wildcardId":"biology-new-pandemic","intensity":1}]` / same with `10` | 401 | CHATGPT_NOT_CONNECTED | `Connect ChatGPT to generate` |
   | `[{"wildcardId":"biology-zombies","intensity":5}]` | 400 | VALIDATION_FAILED | `unknown wildcard: biology-zombies` |
   | `[{"wildcardId":"Biology-New-Pandemic","intensity":5}]` | 400 | VALIDATION_FAILED | `unknown wildcard: Biology-New-Pandemic` |
   | `[{"wildcardId":"","intensity":5}]` | 400 | VALIDATION_FAILED | `unknown wildcard: ` (trailing space, empty id) |
   | `[{"intensity":5}]` / `[{"wildcardId":null,"intensity":5}]` / `[{"wildcardId":7,"intensity":5}]` | 400 | VALIDATION_FAILED | `wildcards[0].wildcardId is invalid` |
   | `[{"wildcardId":"biology-new-pandemic","intensity":5},{"wildcardId":"biology-new-pandemic","intensity":7}]` | 400 | VALIDATION_FAILED | `duplicate wildcard: biology-new-pandemic` |
   | `[{"wildcardId":"biology-new-pandemic","intensity":X}]` for X = `0`, `11`, `-1`, `null`, `5.5`, `"8"`, `true`, and the key removed | 400 | VALIDATION_FAILED | `wildcard intensity must be between 1 and 10` |
   | `[{"wildcardId":"biology-new-pandemic","intensity":11},{"wildcardId":"biology-zombies","intensity":5}]` | 400 | VALIDATION_FAILED | `wildcard intensity must be between 1 and 10` (element 0 fails first) |
   | `[{"wildcardId":"biology-zombies","intensity":11}]` | 400 | VALIDATION_FAILED | `unknown wildcard: biology-zombies` (id before intensity) |
   | `[{"wildcardId":"biology-new-pandemic","intensity":5},{"wildcardId":"biology-new-pandemic","intensity":0}]` | 400 | VALIDATION_FAILED | `duplicate wildcard: biology-new-pandemic` (duplicate before intensity) |
   | 31 entries: all 30 catalogue ids (intensity 5) + `biology-new-pandemic` again | 400 | VALIDATION_FAILED | `duplicate wildcard: biology-new-pandemic` |
   | `null` / `{}` / `"x"` / `[1]` / `[null]` | 400 | VALIDATION_FAILED | `wildcards is invalid` |
   | `realism` 0 and `wildcards` `[{"wildcardId":"biology-zombies","intensity":5}]` | 400 | VALIDATION_FAILED | `realism must be between 1 and 10` |
   | `horizon` `"3y"` and `wildcards` `[{"wildcardId":"biology-zombies","intensity":5}]` | 400 | VALIDATION_FAILED | `unknown horizon` |
   | unknown extra property in an element: `[{"wildcardId":"biology-new-pandemic","intensity":5,"foo":1}]` | 401 | CHATGPT_NOT_CONNECTED | `Connect ChatGPT to generate` |

### Frontend
Files: `src/app/scenario/wildcard-catalogue.ts` (`WildcardCatalogueComponent`, standalone, inputs `categories`
(`WildcardCategory[]` from the loaded catalogue)), used by `scenario-panel.html` below the horizon group;
`scenario.store.ts` extended.

- Layout: after "TIME HORIZON" the panel shows the section heading "WILDCARDS" and `wildcard-section` containing one
  `mat-expansion-panel` per catalogue category in catalogue order, `data-testid="wildcard-category-<categoryId>"`,
  collapsed on load (host without class `mat-expanded`); several may be open at once. Its header
  `wildcard-category-header-<categoryId>` has the category label as text (e.g. "Political / institutional");
  clicking it expands the panel (host gets `mat-expanded`). Panel content is rendered eagerly (no lazy
  `matExpansionPanelContent`), so unit tests find the rows inside collapsed panels; E2E expands the category before
  interacting.
- Row per wildcard, in catalogue order inside its category panel:
  - `mat-slide-toggle` `data-testid="wildcard-toggle-<wildcardId>"` with `aria-label` = the wildcard label (e.g.
    "New pandemic") and no projected text. Tests read/assert state via the inner `button[role=switch]`
    (`getByTestId('wildcard-toggle-<id>').getByRole('switch')`, `aria-checked` "true"/"false", Playwright
    `toBeChecked()`), and click that inner switch.
  - `wildcard-label-<wildcardId>`: text exactly "<label>" when off (e.g. "New pandemic"), exactly "<label> <n>/10"
    when enabled (e.g. "New pandemic 8/10").
  - When enabled only: `mat-slider` `data-testid="wildcard-intensity-<wildcardId>"` (min 1, max 10, step 1,
    discrete) with `<input matSliderThumb>` `data-testid="wildcard-intensity-<wildcardId>-input"`, `aria-label`
    "<label> intensity" (e.g. "New pandemic intensity"). Interaction as the intensity sliders of slice 01
    (`fill('8')` on the input, or ArrowRight/ArrowLeft/Home/End). When off, both elements are absent from the DOM.
- Fresh session: all 30 switches `aria-checked="false"`, no `wildcard-intensity-*` element exists, every
  `wildcard-label-*` shows only its label.
- Enable `biology-new-pandemic` → switch checked, slider value 5, label "New pandemic 5/10"; set the slider to 8 →
  label "New pandemic 8/10"; other wildcards unchanged. Disable it → switch unchecked, slider gone, label
  "New pandemic"; enable again → value 5 ("New pandemic 5/10").
- If the loaded configuration (`getScenarioConfiguration`) already contains wildcards, those rows load enabled with
  their intensities (unit-tested via `ScenarioStore.load`).
- `ScenarioStore` additions (unit-test surface):
  - `wildcards()` — `WildcardSetting[]` of enabled wildcards, in the order they were enabled (or loaded);
    `configuration().wildcards` equals it.
  - `isWildcardEnabled(id): boolean`, `wildcardIntensity(id): number | null` (null when off).
  - `enableWildcard(id)`: appends `{wildcardId: id, intensity: 5}`; no-op if already enabled (intensity kept).
  - `disableWildcard(id)`: removes the entry; no-op if not enabled.
  - `setWildcardIntensity(id, n)`: integers 1–10 only, otherwise (0, 11, 5.5, NaN) unchanged; no-op if `id` is not
    enabled; position in the array unchanged.
  - Changing wildcards never changes realism/darkness/optimism/horizon/customWildcards/output, and vice versa.
  - Example: enable `biology-new-pandemic`, set 8, enable `robotics-humanoid-boom`, set 6, enable
    `ai-stagnation`, disable `ai-stagnation` → `configuration().wildcards` =
    `[{"wildcardId":"biology-new-pandemic","intensity":8},{"wildcardId":"robotics-humanoid-boom","intensity":6}]`
    (FR-4 acceptance 3: a disabled wildcard is not part of the configuration that `startRun` will receive; the
    generate button is still disabled until slice 04, whose E2E asserts the request body).

### Test locations and traces (`// @trace FR-4`)
- Backend: `ScenarioControllerTest.java` (catalogue categories), `StartRunValidationTest.java` or a new
  `StartRunWildcardValidationTest.java` in `com.oracul.app.runs` (table rows above).
- Frontend unit (Vitest): `scenario.store.spec.ts` (store additions), `wildcard-catalogue.spec.ts` (rows, toggle,
  label text, slider presence, re-enable resets to 5, loaded configuration).
- E2E (`e2e/tests/wildcards.spec.ts`): fresh page → expand each category, all switches off, rows grouped under their
  category header; expand `wildcard-category-header-biology`, enable `biology-new-pandemic`, `fill('8')` on
  `wildcard-intensity-biology-new-pandemic-input` → `wildcard-label-biology-new-pandemic` "New pandemic 8/10";
  disable → label "New pandemic", slider absent. API rows may be checked with `request.post('/api/runs', …)`.

## Slice 18_custom-wildcards-output — test contract (FR-5, FR-6)

Delivers custom wildcards and the output section in the panel, and the `customWildcards` / `output` validation of
`startRun`. The research side (custom topics `custom-<n>`, WILDCARD intents, `custom-wildcards` data block) exists since
slices 05/07 and is only re-checked end-to-end here. Behaviour and tests of slices 01–17 stay unchanged (incl. the
fallback row "`wildcards` removed → `wildcards is invalid`").

### Backend (`com.oracul.app.scenario`, `com.oracul.app.common`)
- `ScenarioConfigurationDeserializer` reads `customWildcards` strictly (no scalar coercion): `label` only from a JSON
  string (then trimmed), `intensity` only from a JSON integer; `output.story` / `output.illustration` only from JSON
  booleans. A new pure `CustomWildcardRules.firstViolation(List<CustomWildcard>)` and `OutputRules.firstViolation(
  OutputSettings)` (or one `ScenarioRules`) produce the messages; `ApiExceptionHandler` uses them for the
  `customWildcards` / `output` roots exactly as `WildcardRules` for `wildcards`.
- `POST /api/runs` (`startRun`) — `@WebMvcTest`, base valid body as in slice 01 with `customWildcards` replaced.
  Valid = `401 CHATGPT_NOT_CONNECTED` "Connect ChatGPT to generate" (no connection in a WebMvcTest). Every error body:
  exactly the keys `code`, `message`; status 400, code `VALIDATION_FAILED`. `L40` = `"a"` × 40, `L41` = `"a"` × 41.
  | Test input (base body with `customWildcards` = …) | Status | `message` (exact) |
  |---|---|---|
  | `[{"label":"Ocean desalination boom","intensity":5}]` | 401 | `Connect ChatGPT to generate` |
  | `[{"label":"A","intensity":1},{"label":"B","intensity":10},{"label":<L40>,"intensity":5}]` (3 items; `<L40>` = the 40-character string) | 401 | `Connect ChatGPT to generate` |
  | `[{"label":"  Mars colony  ","intensity":7}]` / `[{"label":"  " + L40 + "  ","intensity":7}]` | 401 | `Connect ChatGPT to generate` |
  | `[{"label":"Mars  colony","intensity":5},{"label":"Mars colony","intensity":5}]` (inner spacing differs) | 401 | `Connect ChatGPT to generate` |
  | `[{"label":"New pandemic","intensity":5}]` (same as a catalogue label) | 401 | `Connect ChatGPT to generate` |
  | `[{"label":"x","intensity":5,"foo":1}]` (unknown property) | 401 | `Connect ChatGPT to generate` |
  | 4 items `A`,`B`,`C`,`D` intensity 5 / 4 items where item 3 has label `""` | 400 | `At most 3 custom wildcards` |
  | label `""` / `"   "` / L41 / `"  " + L41` / `5` / `true` / `null` / key removed | 400 | `Wildcard name must be 1–40 characters` |
  | `[{"label":"Mars colony","intensity":5},{"label":"mars colony","intensity":3}]` / second label `"  MARS COLONY "` | 400 | `This wildcard already exists` |
  | `[{"label":"Mars colony","intensity":X}]` for X = `0`, `11`, `-1`, `null`, `5.5`, `"8"`, `true`, key removed | 400 | `wildcard intensity must be between 1 and 10` |
  | `[{"label":"A","intensity":11},{"label":"","intensity":5}]` | 400 | `wildcard intensity must be between 1 and 10` (element 0 first) |
  | `[{"label":"","intensity":11}]` | 400 | `Wildcard name must be 1–40 characters` (label before intensity) |
  | `[{"label":"A","intensity":5},{"label":"a","intensity":0}]` | 400 | `This wildcard already exists` (duplicate before intensity) |
  | `null` / `{}` / `"x"` / `[1]` / `[null]` / `["A"]` / key removed | 400 | `customWildcards is invalid` |
  | 4 items with item 3 = `1` (not an object) | 400 | `customWildcards is invalid` (shape before count) |
  | `wildcards` `[{"wildcardId":"biology-zombies","intensity":5}]` + `customWildcards` 4 items | 400 | `unknown wildcard: biology-zombies` |
  | `horizon` `"3y"` + `customWildcards` `[{"label":"","intensity":5}]` | 400 | `unknown horizon` |
  | `customWildcards` `[{"label":"","intensity":5}]` + `output` `{"story":false,"illustration":true}` | 400 | `Wildcard name must be 1–40 characters` |

  | Test input (base body with `output` = …) | Status | `message` (exact) |
  |---|---|---|
  | `{"story":true,"illustration":false}` / with extra `"foo":1` | 401 | `Connect ChatGPT to generate` |
  | `{"story":X,"illustration":false}` for X = `false`, `null`, `"true"`, `1`; `{"illustration":false}` | 400 | `Story output is required` |
  | `output` = `null` / `"x"` / `[]` / `{}` / key removed | 400 | `Story output is required` |
  | `{"story":false,"illustration":true}` | 400 | `Story output is required` (story first) |
  | `{"story":true,"illustration":X}` for X = `true`, `null`, `"false"`, `0`; `{"story":true}` | 400 | `Illustration is not available yet (MVP+1)` |
- Integration (`AbstractRunIT`, connected session, stubs): `startOk` with body B (slice 04) plus
  `customWildcards` `[{"label":"  Ocean desalination boom ","intensity":7},{"label":"Mars colony","intensity":3}]` →
  202, `configuration.customWildcards` = `[{"label":"Ocean desalination boom","intensity":7},{"label":"Mars colony",
  "intensity":3}]`; `GET /api/runs/{id}` returns the same; after the run is done `getRunResearch.profile.topics` =
  `[{"key":"custom-1","label":"Ocean desalination boom","category":"custom","weight":0.7,"custom":true},
  {"key":"custom-2","label":"Mars colony","category":"custom","weight":0.3,"custom":true}]` and
  `searchPlan.intents` starts with two WILDCARD intents with `topicKey` `custom-1`, `custom-2`, descriptions
  `Current developments related to Ocean desalination boom` / `… Mars colony`, `drivenBy[0]`
  `Ocean desalination boom 7/10` / `Mars colony 3/10`; no ADJACENT intent has a `category` (custom topics add none).
  A rejected body (any row of the tables above, connected session) creates no `generation_run` row.

### Frontend
Files: `src/app/scenario/custom-wildcards.ts` (`CustomWildcardsComponent`), `src/app/scenario/output-settings.ts`
(`OutputSettingsComponent`), both standalone, used by `scenario-panel.html`: `<app-custom-wildcards>` directly after
`<app-wildcard-catalogue>` (inside WILDCARDS), then `<h2 class="section">OUTPUT</h2>` and `<app-output-settings>`.
`scenario.store.ts` extended.

- Custom section `custom-wildcard-section` (sub-heading "Custom wildcards"):
  - `mat-form-field` with `<input matInput>` `data-testid="custom-wildcard-input"`, `aria-label` "Custom wildcard
    name", placeholder "e.g. Ocean desalination boom", **no `maxlength` attribute** (so > 40 can be typed and
    rejected). `mat-stroked-button` `custom-wildcard-add` text "Add", never disabled. Enter in the input = Add.
  - Error: `custom-wildcard-error` (a `mat-error`, or an element with `role="alert"` directly under the form field)
    shows exactly one of "At most 3 custom wildcards" / "Wildcard name must be 1–40 characters" / "This wildcard
    already exists" after a rejected Add; the input text is kept on rejection. The element is absent when there is no
    error; the error is removed by a successful Add, by typing in the input, and by removing a row.
  - On a successful Add: a row is appended, the input value becomes `""`, no error.
  - Row `i` (0-based, panel order): `custom-wildcard-<i>` containing `custom-wildcard-label-<i>` with text exactly
    "<trimmed label> <n>/10" (e.g. "Ocean desalination boom 5/10"), `mat-slider` `custom-wildcard-intensity-<i>`
    (min 1, max 10, step 1, discrete) with `<input matSliderThumb>` `custom-wildcard-intensity-<i>-input`
    (`aria-label` "<label> intensity", value "5" after Add) and `mat-icon-button` `custom-wildcard-remove-<i>`
    (`aria-label` "Remove <label>"). Sliders are changed as in slice 01 (`fill('8')` or arrow keys).
  - Remove row `i` → that row disappears, later rows shift down (row 2 becomes row 1, testids renumbered), a slot
    is free again.
  - A loaded configuration (`getScenarioConfiguration`) with custom wildcards renders them as rows with their
    intensities.
- Output section `output-section` (after heading "OUTPUT"):
  - `mat-checkbox` `output-story`, label "Story": inner `input[type=checkbox]` `checked` true, not disabled, clicking
    it (once or repeatedly) leaves it checked and the store's `output` unchanged.
  - `mat-checkbox` `output-illustration`, label "Illustration": inner input `checked` false and `disabled` true;
    `click({force: true})` leaves it unchecked. Next to it `output-illustration-badge` (a `mat-chip` or span) with text
    exactly "MVP+1".
  - The checkboxes do not read `output` from the store; they always show Story checked / Illustration unchecked.
- `ScenarioStore` additions (unit-test surface):
  - `customWildcards()` — `CustomWildcard[]` in panel order; `configuration().customWildcards` equals it.
  - `addCustomWildcard(label: string): string | null` — trims; checks in order count (3 exist → returns
    "At most 3 custom wildcards"), length (trimmed length 0 or > 40 → "Wildcard name must be 1–40 characters"),
    duplicate (case-insensitive vs existing → "This wildcard already exists"); on success appends
    `{label: trimmed, intensity: 5}` and returns `null`; on failure the state is unchanged.
  - `removeCustomWildcard(index)`: removes that element; out-of-range index → no-op.
  - `setCustomWildcardIntensity(index, n)`: integers 1–10 only (0, 11, 5.5, NaN → unchanged); out-of-range index →
    no-op; order unchanged.
  - `load(config)` keeps `customWildcards` and `output` as given (unchanged from slice 01).
  - Custom wildcard changes never change realism/darkness/optimism/horizon/wildcards/output, and vice versa.
  - Example: add "Ocean desalination boom", add " Mars colony ", set index 1 to 8, add "OCEAN DESALINATION BOOM"
    (→ "This wildcard already exists") → `customWildcards()` = `[{"label":"Ocean desalination boom","intensity":5},
    {"label":"Mars colony","intensity":8}]`.

### Test locations and traces
- Backend: `backend/src/test/java/com/oracul/app/runs/StartRunCustomWildcardValidationTest.java` (`// @trace FR-5`
  custom table), `StartRunOutputValidationTest.java` (`// @trace FR-6` output table), optional pure unit tests
  `com.oracul.app.scenario.CustomWildcardRulesTest` / `OutputRulesTest`; `backend/src/test/java/com/oracul/app/runs/
  CustomWildcardRunIT.java` (`// @trace FR-5`, integration row).
- Frontend unit (Vitest): `src/app/scenario/scenario.store.custom.spec.ts` (store additions, FR-5),
  `src/app/scenario/custom-wildcards.spec.ts` (FR-5 rows, errors, remove, loaded configuration),
  `src/app/scenario/output-settings.spec.ts` (FR-6, incl. heading OUTPUT after WILDCARDS).
- E2E (`e2e/tests/custom-wildcards-output.spec.ts`, fresh context = defaults):
  - FR-5: fill "Ocean desalination boom", click Add → `custom-wildcard-label-0` "Ocean desalination boom 5/10",
    input empty (`evidence(page, 'FR-5', 'custom-wildcard-added')`); Add with empty input and with 41 characters →
    error "Wildcard name must be 1–40 characters", still 1 row; add "Mars colony" (Enter) and "Fusion towns" → 3 rows;
    fill "Fourth" + Add → "At most 3 custom wildcards", 3 rows (`evidence(page, 'FR-5', 'custom-wildcard-limit')`);
    remove row 0 → 2 rows, `custom-wildcard-label-0` "Mars colony 5/10"; add "mars colony" → "This wildcard already
    exists". Connected run (connect as in `run-start.spec.ts`): add "Ocean desalination boom", `fill('7')` on
    `custom-wildcard-intensity-0-input`, generate → request body `customWildcards` =
    `[{"label":"Ocean desalination boom","intensity":7}]`, `output` `{"story":true,"illustration":false}`.
  - FR-6: fresh page → `output-story` checked, `output-illustration` unchecked + disabled, badge "MVP+1"
    (`evidence(page, 'FR-6', 'output-settings')`); click `output-illustration` with `force: true` → still unchecked;
    click `output-story` → still checked. API rows may be checked with `request.post('/api/runs', …)`.

## Slice 19_mobile-layout — test contract (FR-34)

Frontend only: no migration, no change to `api/openapi.yaml`, no new `ApiError.code`. Where this section is more
precise than "Behaviour" or "UI", this section wins.

### Frontend
Files: `src/app/layout.ts` exports `MOBILE_QUERY = '(max-width: 767.98px)'`; `app.ts` / `app.html` / `app.scss`
changed. `App` decides the mode only via `inject(BreakpointObserver).observe(MOBILE_QUERY)` (`matches` true →
mobile) — unit tests replace it with `{ provide: BreakpointObserver, useValue: { observe: () => subject,
isMatched: () => subject.value.matches } }` where `subject` is a `BehaviorSubject<BreakpointState>`.

- `mat-sidenav` `data-testid="scenario-drawer"` (rendered only when `loader.state() === 'ready'`, as before):
  - desktop: `mode="side"`, opened, `disableClose` true → class `mat-drawer-side` + `mat-drawer-opened`.
  - mobile: `mode="over"`, closed on load and whenever mobile mode is entered → class `mat-drawer-over`, no
    `mat-drawer-opened`; `scenario-panel` is in the DOM but not visible (Playwright `toBeHidden`). Opening →
    class `mat-drawer-opened`, `scenario-panel` visible, a `.mat-drawer-backdrop.mat-drawer-shown` covers the
    center. Width `min(300px, 85vw)`.
  - `scenario-drawer-close`: `mat-icon-button`, `aria-label` "Close scenario panel", `<mat-icon>close</mat-icon>`,
    first element inside the sidenav above `<app-scenario-panel>`; only in mobile mode. Click → closed.
  - Backdrop click and Escape (sidenav default, `disableClose` false in mobile) → closed.
- Header `app-header`:
  - `scenario-drawer-toggle`: `mat-stroked-button` with text exactly "Scenario", first child of the toolbar (before
    `app-wordmark`), only in mobile mode and only when the loader is ready; `aria-expanded` "true"/"false" = drawer
    opened; `aria-controls` the sidenav's id. Click → toggles the drawer.
  - mobile: the toolbar wraps (`height: auto`, `flex-wrap: wrap`, row gap), so wordmark, toggle, "Recent futures",
    `chatgpt-status` and connect/disconnect stay inside the viewport; desktop styling unchanged.
- Center: `mat-sidenav-content` has no left margin in mobile mode (x = 0, full width); `.layout` min-height as before.
- Values: the drawer hosts the same `ScenarioPanel` instance for its whole life; closing it changes nothing in
  `ScenarioStore`.

### Test locations and traces (`// @trace FR-34`)
- Frontend unit (Vitest): `src/app/app.mobile.spec.ts` with the stub `BreakpointObserver` above (same
  catalogue/configuration flush helpers as `app.spec.ts`):
  - matches false → `scenario-drawer` has `mat-drawer-side` + `mat-drawer-opened`, no `scenario-drawer-toggle`, no
    `scenario-drawer-close`.
  - matches true → `mat-drawer-over`, not opened, `scenario-drawer-toggle` text "Scenario" inside `app-header` and
    before `app-wordmark`, `aria-expanded` "false"; while loading/error: no toggle, no `scenario-drawer`.
  - click toggle → opened, `aria-expanded` "true"; `scenario-drawer-close` click → closed, "false".
  - open, set `slider-darkness-input` to 9 (input+change events), close → `ScenarioStore.darkness()` 9, reopen →
    `value-darkness` "9"; other configuration fields unchanged.
  - subject emits false after true while open → side + opened; emits true again → over + closed.
- E2E (`e2e/tests/mobile-layout.spec.ts`, `page.setViewportSize({ width: 390, height: 844 })` before `goto('/')`):
  - load → `scenario-drawer-toggle` visible with text "Scenario", `scenario-panel` hidden, `welcome-view` visible,
    `scrollWidth ≤ innerWidth`, `mat-sidenav-content` box x = 0 / width 390 ± 1 (`evidence(page, 'FR-34',
    'mobile-closed')`).
  - toggle → `scenario-panel` visible (`evidence(page, 'FR-34', 'mobile-drawer-open')`); `fill('9')` on
    `slider-darkness-input`; `scenario-drawer-close` → panel hidden; toggle → `value-darkness` "9". Also close via
    Escape and via backdrop click (`.mat-drawer-backdrop`) → hidden, value kept.
  - parameterized widths 360, 414, 767 → mobile (toggle visible, panel hidden, no horizontal scroll, header children
    within viewport); 768 and 1280 → toggle count 0, `scenario-panel` visible.

