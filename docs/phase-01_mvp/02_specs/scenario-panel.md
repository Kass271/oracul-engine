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
  - custom: `custom-wildcard-input`, `custom-wildcard-add`, `custom-wildcard-error`, `custom-wildcard-<index>`
    (0-based), `custom-wildcard-intensity-<index>`, `custom-wildcard-remove-<index>`
  - output: `output-story`, `output-illustration`, `output-illustration-badge` ("MVP+1")
  - mobile: `scenario-drawer-toggle` ("Scenario"), `scenario-drawer-close`
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

