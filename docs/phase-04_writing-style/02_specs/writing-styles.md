# Spec — Writing styles: catalogue, Writing Style selector, story language

Covers: FR-62, FR-63, FR-64

Delta against phase-01 `scenario-panel.md` FR-6 (Output section, `OutputSettings` validation), FR-33 (session
configuration = configuration of the newest run, `recent-futures.md`) and the current code `OutputSettings`
(frontend `src/app/scenario/output-settings.ts`), `ScenarioStore`, `ScenarioLoader`, backend
`ScenarioConfigurationDeserializer` / output rules in `com.oracul.app.scenario`. How the style changes the story
request is in `styled-story.md`; rewriting and showing the style is in `story-rewrite.md`.

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-6: the Output section holds only the Story and Illustration checkboxes | **Extended** by FR-63 / FR-64: below them the Writing Style group (Official / Briefing · Narrative, plus the Narrative Style list) and the Story language select. Story stays checked and read-only, Illustration stays unchecked and disabled. |
| FR-6: "The panel always sends `output` `{"story":true,"illustration":false}`" | **Superseded**: the panel always sends `output` `{"story":true,"illustration":false,"writingStyle":{"mode":…,"profileId":…},"language":…}`. |
| FR-6 validation order "output (story, then illustration)" | **Extended**: output (story → illustration → writingStyle → language). |
| slice 18: `ScenarioStore.load(config)` keeps `output` exactly as given | **Superseded**: `load` keeps `story` / `illustration` handling as before but normalises the style (absent or unknown → Official / Briefing) and the language (absent or unknown → `en`). |
| `ScenarioLoader`: the panel is ready after catalogue + configuration | **Extended**: ready after catalogue + configuration + writing styles (three parallel requests); any failure → the existing error state. |

## Purpose
The user chooses how the future is told — as an Official / Briefing report or as Narrative prose, optionally inspired
by one of seven authors — and in which language (English or Ukrainian), without touching what the future is. The
choice is part of the scenario configuration, snapshotted into every run. Style profiles live in a backend file so a
profile can be added without changing the pipeline.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| style file (`oracul.writing-styles.location`, default `classpath:writing-styles.json`, i.e. `backend/src/main/resources/writing-styles.json`) | `profiles` | array of entries, 2–50 | validated once at startup (FR-62 rules); order = file order |
| style file entry | `id` | string | `^[a-z0-9-]{1,40}$`, unique in the file |
| style file entry | `mode` | string | `OFFICIAL` or `NARRATIVE` |
| style file entry | `label` | string | 1–80 characters, single line |
| style file entry | `characteristics` | string | 1–300 characters, single line |
| style file entry | `instructions` | string | 1–2000 characters, single line — the profile's prompt text (never sent to the browser) |
| `ScenarioConfiguration.output` (in `generation_run.configuration` jsonb, no migration) | `writingStyle` | `WritingStyleSelection` `{mode, profileId}` (optional) | absent / `null` → Official / Briefing (`OFFICIAL`, `official-briefing`); stored exactly as sent (absent stays absent) |
| `ScenarioConfiguration.output` | `language` | `StoryLanguage` `en` / `uk` (optional) | absent / `null` → `en`; stored exactly as sent |
| API `WritingStyleCatalogue` | `modes`, `profiles`, `languages` | | served by `getWritingStyles` (FR-62) |

Modes are fixed in code (`OFFICIAL` "Official / Briefing", `NARRATIVE` "Narrative"); profiles come only from the file.
Languages are fixed in code: `en` "English", `uk` "Ukrainian".

### Shipped style file (`writing-styles.json`, this order)
| id | mode | label | characteristics | instructions |
|---|---|---|---|---|
| `official-briefing` | OFFICIAL | Official / Briefing | factual, concise, structured, neutral; events, consequences, actors, dates and causal relationships; no literary description | Report the future like an analytical briefing, situation report or intelligence summary written shortly after the events: clear, information-dense sentences about events, consequences, actors, dates and causal relationships. |
| `neutral-narrative` | NARRATIVE | Neutral Narrative | literary and immersive prose; atmosphere, scene setting, imagery, tension, narrative flow and pacing; no particular author's voice | Tell the future as immersive literary prose with atmosphere, scene setting, imagery, tension and narrative flow, in a neutral contemporary voice that imitates no particular author. |
| `poe` | NARRATIVE | Narrative — inspired by Edgar Allan Poe | dark, gothic, psychologically intense, atmospheric, ominous; dread and decay | Evoke a dark, gothic, psychologically intense atmosphere of dread and decay, with ominous foreshadowing and a brooding inner perspective. |
| `kafka` | NARRATIVE | Narrative — inspired by Franz Kafka | bureaucratic absurdity, alienation, impersonal systems, helplessness, surreal institutional logic, understated anxiety | Evoke bureaucratic absurdity and alienation: impersonal institutions, procedures without visible purpose, helpless individuals and understated anxiety, told in a calm, matter-of-fact voice. |
| `wells` | NARRATIVE | Narrative — inspired by H. G. Wells | speculative and observational; technological and societal transformation and its consequences | Evoke a speculative, observational narrator who watches technological and societal transformation unfold and reflects on its consequences for ordinary people. |
| `verne` | NARRATIVE | Narrative — inspired by Jules Verne | exploratory, technological, adventurous, detailed; curiosity about future inventions and environments | Evoke exploratory, adventurous storytelling rich in technical detail, curiosity about new inventions and vivid descriptions of new environments. |
| `orwell` | NARRATIVE | Narrative — inspired by George Orwell | clear, restrained prose; institutions, political power, social control, language, surveillance, systemic consequences | Evoke clear, restrained prose focused on institutions, political power, social control, the language of officials, surveillance and their systemic consequences. |
| `clarke` | NARRATIVE | Narrative — inspired by Arthur C. Clarke | large-scale scientific and technological perspective, rational tone, sense of discovery, humanity's relationship with transformative technologies | Evoke a large-scale scientific and technological perspective with a rational tone, a sense of discovery and attention to humanity's relationship with transformative technology. |
| `harrison` | NARRATIVE | Narrative — inspired by Harry Harrison | fast-moving science fiction with irony, social satire, technological absurdity and energetic descriptions of future societies | Evoke fast-moving science fiction with irony, social satire, technological absurdity and energetic descriptions of future society. |

## Behaviour

### FR-62 — Writing style catalogue
- Happy path: at startup a `WritingStyles` bean (package `com.oracul.app.style`) reads and validates the style file
  once. `GET /api/writing-styles` (`getWritingStyles`) → 200 `WritingStyleCatalogue`:
  - `modes` = `[{"id":"OFFICIAL","label":"Official / Briefing","isDefault":true,"defaultProfileId":"official-briefing"},
    {"id":"NARRATIVE","label":"Narrative","isDefault":false,"defaultProfileId":"neutral-narrative"}]`
  - `profiles` = the OFFICIAL profile first, then every NARRATIVE profile in file order, each `{id, mode, label,
    characteristics, isDefault, isModeDefault}`; `isDefault` true only for `official-briefing`; `isModeDefault` true
    for `official-briefing` and `neutral-narrative`. With the shipped file: 9 profiles in the table order.
  - `languages` = `[{"code":"en","label":"English","isDefault":true},{"code":"uk","label":"Ukrainian","isDefault":false}]`
  - No `instructions` anywhere in the response.
- Rules:
  - A profile added to the file (valid entry, e.g. `{"id":"dickens","mode":"NARRATIVE","label":"Narrative — inspired
    by Charles Dickens","characteristics":"…","instructions":"…"}`) is listed after a restart and accepted by
    `startRun` / `startRewriteRun` and used in the STORY_WRITING request, with no other code change.
  - File checks at startup, in this order; the first failure stops the backend (the Spring context fails) with an
    `IllegalStateException` whose message is exactly one of (`<n>` = 1-based entry position, `<id>` = the entry's id):
    | Problem | Message |
    |---|---|
    | file missing / unreadable / not JSON / top level not an object with a `profiles` array | `Writing style file <location> cannot be read` |
    | `profiles` has fewer than 2 or more than 50 entries | `Writing style file <location> must have 2 to 50 profiles` |
    | entry not an object, or `id` missing / not a string / blank | `Writing style entry #<n>: missing id` |
    | `id` not matching `^[a-z0-9-]{1,40}$` | `Writing style entry #<n> (<id>): invalid id` |
    | `id` equal to an earlier entry's | `Writing style entry #<n> (<id>): duplicate id` |
    | `mode` missing or not `OFFICIAL` / `NARRATIVE` | `Writing style entry #<n> (<id>): unknown mode` |
    | `label` missing / not a string / blank | `Writing style entry #<n> (<id>): missing label` |
    | `characteristics` missing / not a string / blank | `Writing style entry #<n> (<id>): missing characteristics` |
    | `instructions` missing / not a string / blank | `Writing style entry #<n> (<id>): missing prompt text` |
    | `label` > 80, `characteristics` > 300, `instructions` > 2000 characters | `Writing style entry #<n> (<id>): <field> is too long` |
    | any of the four strings contains a control character (incl. line breaks), `<<<` or `>>>` | `Writing style entry #<n> (<id>): <field> contains forbidden characters` |
    | after all entries: no `official-briefing` with mode OFFICIAL, or a second OFFICIAL entry | `Writing style file <location> must have exactly one OFFICIAL profile, official-briefing` |
    | after all entries: no `neutral-narrative` with mode NARRATIVE | `Writing style file <location> must have the NARRATIVE profile neutral-narrative` |
    Entries are checked in file order and the fields of one entry in the order of the table. Unknown keys of an entry
    are ignored. Strings are used as written (no trimming).
- Errors:
  - unexpected failure while answering (e.g. the `WritingStyles` bean throws a `RuntimeException`) → 500
    `INTERNAL_ERROR` → "Something went wrong — try again" (body exactly `{"code":"INTERNAL_ERROR","message":"Something
    went wrong — try again"}`, the existing `ApiExceptionHandler` fallback; no stack trace, no exception text)
  - invalid style file → no HTTP answer at all: the backend does not start (message above in the log)

#### Slice 01_style-catalogue — precise delta (step 4a)
- Operation: `GET /api/writing-styles` (`getWritingStyles`, tag `writing-styles`, generated `WritingStylesApi`), no
  parameters, no request body. 200 with `Content-Type: application/json`. The answer is independent of the session
  (same body with and without a session cookie, before and after runs) and stores nothing.
- Exact 200 body with the shipped file (key sets are exact: top level `modes`, `profiles`, `languages`; each mode
  `id`, `label`, `isDefault`, `defaultProfileId`; each profile `id`, `mode`, `label`, `characteristics`, `isDefault`,
  `isModeDefault`; each language `code`, `label`, `isDefault`):
  - `modes` and `languages` exactly as in the happy path above.
  - `profiles` = 9 items in the order `official-briefing`, `neutral-narrative`, `poe`, `kafka`, `wells`, `verne`,
    `orwell`, `clarke`, `harrison`; `mode`, `label`, `characteristics` byte-identical to the "Shipped style file" table
    (the `—` in labels is U+2014 with one space on each side); `isDefault` true only for `official-briefing`;
    `isModeDefault` true only for `official-briefing` and `neutral-narrative`.
  - Example item: `{"id":"kafka","mode":"NARRATIVE","label":"Narrative — inspired by Franz Kafka","characteristics":
    "bureaucratic absurdity, alienation, impersonal systems, helplessness, surreal institutional logic, understated
    anxiety","isDefault":false,"isModeDefault":false}`.
  - Every author profile (all NARRATIVE profiles except `neutral-narrative`) has a label matching
    `^Narrative — inspired by .+$`. `official-briefing` and `neutral-narrative` do not.
- Catalogue order rule: the OFFICIAL profile is listed first wherever it stands in the file, and the NARRATIVE
  profiles keep their relative file order. Example: a file `[neutral-narrative, kafka, official-briefing, poe]` lists
  `official-briefing, neutral-narrative, kafka, poe`.
- Read once: the file is read only at startup. Changing or deleting the file while the backend runs does not change
  the answer, and a restart (new context) shows the new content.
- Backend shape (so tests can address it without guessing):
  - `com.oracul.app.style.WritingStyles` (Spring `@Component`): its constructor gets the property
    `oracul.writing-styles.location` (default `classpath:writing-styles.json`) and a Spring `ResourceLoader`, and
    nothing else. It parses with its own Jackson mapper, so `new ApplicationContextRunner().withUserConfiguration(
    WritingStyles.class).withPropertyValues("oracul.writing-styles.location=…")` is enough to start or fail it.
    Validation runs in the constructor (or `@PostConstruct`) and throws `IllegalStateException` with the exact message
    of the table. `<location>` in a message is the property value verbatim (e.g. `classpath:writing-styles/too-few.json`,
    `file:/tmp/x/styles.json`).
  - Public API: `List<WritingStyleProfile> profiles()` in catalogue order (OFFICIAL first, then NARRATIVE in file
    order) and `Optional<WritingStyleProfile> find(String id)` (exact, case-sensitive id; unknown / `null` → empty).
    `com.oracul.app.style.WritingStyleProfile` is a record `(String id, WritingMode mode, String label, String
    characteristics, String instructions)`, and `com.oracul.app.style.WritingMode` is the domain enum `OFFICIAL`,
    `NARRATIVE`. These are domain types. The generated `com.oracul.app.api.model.WritingMode` / `StoryLanguage` are
    mapped only in the controller.
  - `com.oracul.app.style.WritingStylesController implements WritingStylesApi` takes `ObjectProvider<WritingStyles>`
    (the convention of every controller), so the seven existing `@WebMvcTest` classes, which declare no mock beans,
    keep starting unchanged. A `@WebMvcTest` of the controller uses `@MockitoBean WritingStyles`.
- Validation details (refine the startup table; the order of the table is unchanged):
  - "cannot be read": location not found, empty file, invalid JSON, top level an array / string / number, no
    `profiles` key, `profiles` not an array (`null`, object, string). Extra top-level keys are ignored.
  - "must have 2 to 50 profiles" is checked before any entry (so `[]` and a single valid entry both give it).
  - "missing id": entry `null` / number / string / array, or `id` absent, `null`, not a JSON string (`5`, `true`),
    `""` or whitespace only (`String.isBlank()`). A non-blank id that does not match the pattern (`Kafka`, `k_a`,
    `" kafka"`, 41 × `a`) gives "invalid id" with the id verbatim.
  - "unknown mode": `mode` absent, `null`, not a string, or not exactly `OFFICIAL` / `NARRATIVE` (`official`,
    `Narrative`, `POETIC`, `""`).
  - missing label / characteristics / prompt text: absent, `null`, not a string, `""` or whitespace only.
  - Lengths count Unicode code points (`label` 80 ok / 81 too long, `characteristics` 300 / 301, `instructions`
    2000 / 2001). `<field>` in the message is the JSON key: `label`, `characteristics`, `instructions`. Within one
    entry: all presence checks first, then the length checks in the order label → characteristics → instructions,
    then the forbidden-character checks in the same order.
  - Forbidden characters: any `Character.isISOControl` code point (U+0000–U+001F, U+007F–U+009F, so `\n`, `\r`, `\t`
    too), U+2028, U+2029, the substring `<<<` or `>>>`. (`<<` or `>>` alone are allowed.)
  - OFFICIAL rule: fails when no entry is `official-briefing` with mode OFFICIAL (missing, or `official-briefing`
    with mode NARRATIVE), or when there is any second OFFICIAL entry (e.g. `neutral-narrative` or `dickens` with mode
    OFFICIAL). It is checked before the NARRATIVE rule. The NARRATIVE rule fails when `neutral-narrative` is missing.
- Shipped file: `backend/src/main/resources/writing-styles.json` = `{"profiles":[…]}` with the 9 entries of the
  "Shipped style file" table in table order, all five fields verbatim (it passes every rule above).
- Test resources: the startup-failure files live under `backend/src/test/resources/writing-styles/` (one file per
  problem class / boundary) or are written to a `@TempDir` and passed as `file:…`. Tests never edit the shipped file.
  The restart case uses a copy of the shipped entries plus the `dickens` entry appended last (`{"id":"dickens","mode":
  "NARRATIVE","label":"Narrative — inspired by Charles Dickens","characteristics":"Victorian social observation,
  vivid characters, moral contrast","instructions":"Evoke Victorian social observation with vivid characters and
  moral contrast."}`). With it, a fresh context lists 10 profiles, `dickens` last with `isDefault` false and
  `isModeDefault` false, and `WritingStyles.find("dickens")` returns its instructions. "Usable in a run" is proven by
  02_style-selection / 03_styled-story, not here.
- UI: none (FR-62 has no UI, no `data-testid`). No frontend change in this slice. The generated
  `writing-styles.service.ts` exists already and is not called yet.
- Stack: unchanged. No compose file, profile, stub or nginx route changes, and `.oracul/stack.json` stays as is
  (`e2e` = `docker-compose.yml` + `docker-compose.e2e.yml`, `run` = `docker-compose.yml`). No migration.
- Changes earlier behaviour: none. `GET /api/writing-styles` moves from the generated 501 to 200, but no existing test
  calls it (grep of `backend/src/test`, `frontend/src/**/*.spec.ts`, `e2e/tests` and `e2e/stubs` for `writing-styles`,
  `writingStyle`, `WritingStyle`, `getWritingStyles` and `styleLabel` has 0 hits). The new startup dependency uses the
  shipped default file, which every existing `@SpringBootTest` / IT keeps. The controller uses `ObjectProvider`, so the
  existing `@WebMvcTest` classes are unaffected. No existing response, error code, ordering or outbound call changes (tests: none)
- Ranges & invariants: profile count 2..50 (0, 1, 51 → "must have 2 to 50 profiles"; 2 and 50 → starts); id pattern `^[a-z0-9-]{1,40}$` (1 char `a` and 40 chars ok; 41 chars, `Kafka`, `k_a`, `" kafka"` → invalid id; `""`, `"  "`, `5`, `null`, absent → missing id); label 1..80, characteristics 1..300, instructions 1..2000 code points (each boundary ok and boundary + 1 → `<field> is too long`; `""` / whitespace / absent / `null` / non-string → missing); forbidden characters per field (`\n`, `\r`, `\t`, U+0000, U+007F, U+2028, `<<<`, `>>>` → forbidden; `<<` ok); mode (`OFFICIAL`, `NARRATIVE` ok; `official`, `POETIC`, `""`, `null`, absent, `1` → unknown mode); duplicate id at positions 2 and 9 → message names entry #9; file-level classes (no `official-briefing`, `official-briefing` as NARRATIVE, second OFFICIAL entry → OFFICIAL message; no `neutral-narrative` → NARRATIVE message; missing location, not JSON, top-level array, `profiles` not an array → cannot be read); precedence (an entry with several problems reports only the first in table order; a bad entry #2 and a bad entry #5 report #2); invariants over every successfully loaded file (shipped, `dickens`, reordered): top-level and item key sets exactly as listed; no key `instructions` anywhere in the JSON; `profiles[0].id` = `official-briefing`; exactly one profile with `isDefault` true; exactly one `isModeDefault` true per mode; every profile `mode` ∈ {OFFICIAL, NARRATIVE}; ids unique; each mode's `defaultProfileId` names a listed profile of that mode; NARRATIVE order = file order; listed count = number of file entries; `modes` = OFFICIAL, NARRATIVE and `languages` = en, uk in that order.

### FR-63 — Writing Style selector
- Happy path (panel, below the Story / Illustration rows of `output-section`): sub-heading "Writing Style" and a
  `mat-radio-group` `writing-style-group` (`aria-label` "Writing Style") with `writing-style-option-OFFICIAL`
  "Official / Briefing" and `writing-style-option-NARRATIVE` "Narrative" (labels from `modes`). Fresh session (no run)
  → OFFICIAL checked and no `narrative-style-section` in the DOM. Selecting NARRATIVE shows `narrative-style-section`
  (sub-heading "Narrative Style") with one option per NARRATIVE profile in API order (8 with the shipped file), label
  = profile label, `neutral-narrative` selected. Selecting another profile selects it. Selecting OFFICIAL again
  removes the section and sets the profile to `official-briefing`; selecting NARRATIVE again starts at
  `neutral-narrative`.
- Rules:
  - The selection is part of `ScenarioStore.configuration().output.writingStyle` (`{"mode":"OFFICIAL","profileId":
    "official-briefing"}` or `{"mode":"NARRATIVE","profileId":"<id>"}`), sent with every `startRun` built from the panel (generate, quick
    regeneration, LOWER REALISM) and therefore snapshotted into the run's configuration; Try again re-submits the
    failed run's stored configuration verbatim (FR-32), which for a UI-started run already holds the choice.
  - Desktop (viewport ≥ 768 px): the Narrative Style choice is a `mat-radio-group` `narrative-style-group`
    (`aria-label` "Narrative Style") with `narrative-style-option-<profileId>`. Mobile (`MOBILE_QUERY`
    `(max-width: 767.98px)` of `src/app/layout.ts`): a `mat-select` `narrative-style-select` (`aria-label`
    "Narrative Style") with `mat-option`s `narrative-style-select-option-<profileId>` and no `narrative-style-group`.
  - Reload: the panel loads `getScenarioConfiguration` (configuration of the session's newest run, FR-33), so the
    style of the newest run is selected again; absent `writingStyle` → OFFICIAL; a profile that is not in the
    catalogue or whose mode differs → OFFICIAL / `official-briefing`. A selection that was never used for a run is not
    stored (same as every other panel control).
  - Changing the style never changes realism, darkness, optimism, horizon, wildcards, custom wildcards, story,
    illustration or language, and vice versa.
- Errors (backend, `POST /api/runs`; checked after `output.illustration`, before `output.language`):
  - `output.writingStyle` not an object (`"kafka"`, `5`, `[]`, `true`); `mode` missing / `null` / not exactly
    `OFFICIAL` or `NARRATIVE` (`"official"`, `"POETIC"`, `1`); `profileId` missing / `null` / not a string / not an id
    of the style file (`"Kafka"`, `"dickens"`, `""`, `5`); profile of the other mode (`{"mode":"OFFICIAL",
    "profileId":"kafka"}`, `{"mode":"NARRATIVE","profileId":"official-briefing"}`) → 400 `VALIDATION_FAILED` →
    "Unknown writing style"; no run is stored.
  - `output.writingStyle` absent or `null` → accepted (Official / Briefing); unknown keys inside it are ignored.
  - writing styles request fails in the UI → the panel's existing error state (`backend-unavailable`, `backend-retry`).
#### Slice 02_style-selection — precise delta (step 4a, covers FR-63 and FR-64)
Transitional state: the style and the language are validated, stored and restored, but they do not change the pipeline
yet (03_styled-story). Tests of this slice must not assert STORY_WRITING prompt text or story content. No stub change,
no migration, no stack change.

Backend — `POST /api/runs` (`startRun`):
- Complete check order (first failure wins): JSON syntax → realism → darkness → optimism → horizon → wildcards →
  customWildcards → output (missing / not an object / story) → output.illustration → **output.writingStyle** →
  **output.language** → ChatGPT connection (401 / 403 / 503) → single active run (409). So both new 400s come
  without a ChatGPT connection: a `@WebMvcTest` sees 400 for an invalid class and the existing 401
  `CHATGPT_NOT_CONNECTED` "Connect ChatGPT to generate" for a valid one (the technique of `StartRunOutputValidationTest`),
  and E2E can call the API without connecting.
- 400 bodies are exactly `{"code":"VALIDATION_FAILED","message":"Unknown writing style"}` and
  `{"code":"VALIDATION_FAILED","message":"Unknown story language"}` (two keys, `Content-Type: application/json`). No
  bean-validation text (`must not be null`, pattern, size) may reach the client for anything inside `writingStyle`
  or for `language`. Nothing is stored: no `generation_run` row, `GET /api/runs` (`listRecentRuns`) unchanged.
- `writingStyle` is valid exactly when: absent, or JSON `null`, or a JSON object whose `mode` is the JSON string
  `OFFICIAL` or `NARRATIVE` (case-sensitive) and whose `profileId` is a JSON string equal (case-sensitive, no trimming)
  to the id of a profile of the loaded style file (`WritingStyles.find`) **with that same mode**. Other keys inside the
  object are ignored.
- `language` is valid exactly when absent, JSON `null`, or the JSON string `"en"` or `"uk"`.
- Stored configuration (202 body, `GET /api/runs/{runId}`, `GET /api/scenario/configuration` while it is the newest
  run): `output.writingStyle` = `{"mode":…,"profileId":…}` (exactly these two keys, unknown inner keys dropped) when a
  valid object was sent; key absent when absent or `null` was sent. `output.language` = the sent string; key absent
  when absent or `null` was sent. Unknown keys of `output` stay dropped (unchanged). Requests without the two keys
  therefore keep their configuration deep-equal to the request (contract-notes decision 2).
- Shape: the style lookup goes through `ObjectProvider<WritingStyles>` (`com.oracul.app.style.WritingStyles` of slice
  01) and runs only when `writingStyle` is present and not `null`, so the existing `@WebMvcTest` classes (they declare
  no `WritingStyles` bean and send no style) keep their results. New `@WebMvcTest` tests for the style classes use
  `@Import(WritingStyles.class)` (shipped file, default location) or `@MockitoBean WritingStyles`.
- FR-62 "usable without any other code change": a context started with the slice-01 `dickens` file
  (`oracul.writing-styles.location`) accepts `{"mode":"NARRATIVE","profileId":"dickens"}` (passes validation → 401
  without connection, 202 with one); with the shipped file the same body → 400 "Unknown writing style".
- `startAlternativeRun` and quick regeneration are not changed by this slice (ALTERNATIVE copies the parent's stored
  configuration as before, including the two keys when present).

Frontend (`src/app/scenario/`):
- `ScenarioLoader.load()` issues three GETs synchronously and in parallel: `/api/scenario/catalogue`,
  `/api/scenario/configuration` and `/api/writing-styles` (generated `WritingStylesService.getWritingStyles()`).
  New signal `ScenarioLoader.writingStyles: Signal<WritingStyleCatalogue | null>`. `state()` is `'ready'` only after
  all three succeeded (any arrival order), `'error'` as soon as any one fails (`backend-unavailable` + `backend-retry`,
  existing texts), `'loading'` otherwise. `backend-retry` re-issues all three. The store is fed once the
  configuration **and** the writing styles have arrived: `ScenarioStore.setWritingStyles(catalogue)` then
  `ScenarioStore.load(config)`.
- `ScenarioStore`:
  - `DEFAULT_CONFIGURATION.output` = `{"story":true,"illustration":false,"writingStyle":{"mode":"OFFICIAL",
    "profileId":"official-briefing"},"language":"en"}` (so even a never-loaded store sends both fields).
  - New computed signals `writingStyle()` (`WritingStyleSelection`) and `language()` (`StoryLanguage`).
  - `load(config)`: realism … customWildcards, `output.story`, `output.illustration` as before (kept as given);
    `output.writingStyle` normalised: kept as `{mode, profileId}` (other inner keys dropped) when it is an object with
    `mode` `OFFICIAL` and `profileId` `official-briefing`, or `mode` `NARRATIVE` and `profileId` the id of a NARRATIVE
    profile of the known catalogue (when no catalogue was set yet: any non-empty string other than
    `official-briefing`); everything else (absent, `null`, non-object, unknown mode, cross-mode, unknown profile) →
    `{"mode":"OFFICIAL","profileId":"official-briefing"}`. `output.language` kept when `"en"` / `"uk"`, else `"en"`.
  - `setWritingMode(mode)`: `OFFICIAL` → `{OFFICIAL, official-briefing}`; `NARRATIVE` → `{NARRATIVE, <NARRATIVE
    mode's defaultProfileId, i.e. neutral-narrative>}`; selecting the current mode again changes nothing; anything
    else is ignored. `setNarrativeProfile(id)`: only when the mode is NARRATIVE and `id` is a NARRATIVE profile of the
    catalogue, else ignored. `setLanguage(code)`: only `"en"` / `"uk"`, else ignored. No setter touches any other field,
    and no other setter touches `writingStyle` / `language`.
- Every `startRun` the UI sends takes its body from `ScenarioStore.configuration()` — GENERATE, the four quick
  regeneration buttons, LOWER REALISM (insufficient view and evidence note) — so it always has `output.writingStyle`
  and `output.language`. **Try again** on the failure view (FR-32) is unchanged: it re-submits the failed run's
  stored configuration verbatim (a UI-started run carries both fields; a run started through the API without them is
  retried without them). Opening a run (`RunStore` `panel.load(run.configuration)`) normalises like `load`.
- `OutputSettings` (`app-output-settings`) DOM inside `output-section`, in this order: `output-story`, the
  illustration row, `writing-style-section`, `narrative-style-section` (only when mode NARRATIVE),
  `story-language-select`'s form field.
  - `writing-style-section`: sub-heading text `Writing Style`, `mat-radio-group` `data-testid="writing-style-group"`
    `aria-label="Writing Style"`, `mat-radio-button`s `data-testid="writing-style-option-OFFICIAL"` /
    `"writing-style-option-NARRATIVE"` (the test id is on the `mat-radio-button` host; checked state = its inner
    `input[type="radio"]`), labels = `modes[].label` (`Official / Briefing`, `Narrative`).
  - `narrative-style-section`: sub-heading text `Narrative Style`; desktop (`BreakpointObserver.observe(MOBILE_QUERY)`
    not matching): `mat-radio-group` `narrative-style-group` (`aria-label="Narrative Style"`) with one `mat-radio-button`
    `narrative-style-option-<profileId>` per NARRATIVE profile in catalogue order, label = profile `label`
    (shipped: `neutral-narrative`, `poe`, `kafka`, `wells`, `verne`, `orwell`, `clarke`, `harrison`). Mobile (matching):
    `mat-select` `narrative-style-select` (`aria-label="Narrative Style"`, shows the selected profile's label) whose
    overlay holds `mat-option`s `narrative-style-select-option-<profileId>` in the same order and labels; then there is
    no `narrative-style-group` in the DOM. A breakpoint change keeps the selected profile.
  - `mat-form-field` with `mat-label` `Story language` and `mat-select` `data-testid="story-language-select"`
    (shows `English` / `Ukrainian`); its overlay holds `mat-option`s `story-language-option-en` `English` and
    `story-language-option-uk` `Ukrainian` (labels from `languages`).
  - All labels are rendered by interpolation from the catalogue; nothing is hard-coded except the two sub-headings
    and `Story language`.
- Reload (FR-63 / FR-64 acceptance 4 / 3): the panel shows the newest run's stored choice after `page.reload()`, both on
  `/` and on `/futures/<thatRunId>`.

E2E (new spec, e.g. `e2e/tests/writing-style.spec.ts`, desktop 1280 px unless stated):
1. Fresh context, `/`: `writing-style-option-OFFICIAL` checked, `-NARRATIVE` not, `narrative-style-section` count 0,
   `story-language-select` text `English`.
2. Click NARRATIVE: `narrative-style-section` visible, the 8 `narrative-style-option-*` in shipped order with their
   labels, `narrative-style-option-neutral-narrative` checked; click OFFICIAL → section count 0.
3. Connect, NARRATIVE + `narrative-style-option-kafka` + `story-language-option-uk`, GENERATE: the POST body's `output`
   equals `{"story":true,"illustration":false,"writingStyle":{"mode":"NARRATIVE","profileId":"kafka"},"language":"uk"}`;
   `GET /api/runs/<id>` `configuration.output` equals the same; wait until the run is terminal.
4. `page.reload()`: NARRATIVE checked, `narrative-style-option-kafka` checked, `story-language-select` text `Ukrainian`;
   `GET /api/scenario/configuration` `output` equals the body of 3.
5. Width 390 px: open the drawer, click NARRATIVE: `narrative-style-select` visible, `narrative-style-group` count 0;
   open it, choose `narrative-style-select-option-kafka` → the select shows `Narrative — inspired by Franz Kafka`.
6. API without connection: every invalid `writingStyle` and `language` class (lists below) → 400 with the exact body;
   `GET /api/runs` length unchanged.

- Changes earlier behaviour: the panel's `startRun` body and `ScenarioStore.configuration()` gain `output.writingStyle` + `output.language` (default `{OFFICIAL, official-briefing}` / `"en"`, `load` normalises), so every deep-equal of a UI-sent body or of the store's configuration against a config without them breaks (tests: frontend/src/app/runs/quick-actions.spec.ts, frontend/src/app/runs/run.store.spec.ts, frontend/src/app/runs/run-view.spec.ts, frontend/src/app/runs/stop-run.spec.ts, frontend/src/app/runs/evidence-note.spec.ts, frontend/src/app/runs/insufficient-evidence.spec.ts, frontend/src/app/scenario/scenario.store.spec.ts, frontend/src/app/scenario/scenario.store.custom.spec.ts, frontend/src/app/scenario/scenario.store.wildcards.spec.ts, frontend/src/app/scenario/output-settings.spec.ts, frontend/src/app/app.mobile.spec.ts, e2e/tests/run-start.spec.ts, e2e/tests/quick-regeneration.spec.ts, e2e/tests/run-failures.spec.ts, e2e/tests/insufficient-evidence.spec.ts, e2e/tests/custom-wildcards-output.spec.ts)
- Changes earlier behaviour: the panel is ready after catalogue + configuration → after catalogue + configuration + `GET /api/writing-styles` (third parallel load request; failure → `backend-unavailable`), so every App-level unit test that flushes only two load requests never reaches the ready panel, and `run-view.spec.ts`'s `http.verify()` sees the open request (tests: frontend/src/app/app.spec.ts, frontend/src/app/app.mobile.spec.ts, frontend/src/app/scenario/output-settings.spec.ts, frontend/src/app/scenario/custom-wildcards.spec.ts, frontend/src/app/scenario/wildcard-catalogue.spec.ts, frontend/src/app/history/recent-futures.spec.ts, frontend/src/app/chatgpt/chatgpt-connection.spec.ts, frontend/src/app/runs/run-view.spec.ts, frontend/src/app/runs/stop-run.spec.ts, frontend/src/app/runs/run.store.spec.ts, frontend/src/app/runs/progress-view.spec.ts, frontend/src/app/runs/generate-button.spec.ts)
- Changes earlier behaviour: `startRun` output validation story → illustration becomes story → illustration → writingStyle → language; requests without the new keys keep every earlier result and stored configuration (grep: no backend test, E2E or stub sends `writingStyle` / `language` in a run body; `StartRunOutputValidationTest`, `StartRunValidationTest`, the wildcard / custom-wildcard validation tests and all `AbstractRunIT` ITs stay green; E2E `scenario-controls.spec.ts` routes only `**/api/scenario/**` and uses `toMatchObject`, `run-failure.spec.ts` passes its configuration in directly, Try again stays verbatim) (tests: none)
- Ranges & invariants: writingStyle (backend, each class its own parameterized case): valid → passes validation (401 in `@WebMvcTest` without connection, 202 + stored in an IT): every one of the 9 shipped (mode, profileId) pairs, absent, `null` (both stored absent), `{"mode":"NARRATIVE","profileId":"kafka","foo":1}` (stored without `foo`); exhaustive mode × profile grid: all 2 × 9 combinations, valid iff the mode equals the profile's mode (9 valid, 9 → 400); invalid → 400 "Unknown writing style": not an object (`"kafka"`, `""`, `5`, `true`, `[]`, `[{"mode":"OFFICIAL","profileId":"official-briefing"}]`), `{}`, mode missing / `null` / `"official"` / `"Narrative"` / `"POETIC"` / `""` / `1`, profileId missing / `null` / `""` / `5` / `"Kafka"` / `" kafka"` / `"kafka "` / `"dickens"` (shipped file) / 41 × `a`, cross-mode `{OFFICIAL, kafka}` / `{OFFICIAL, neutral-narrative}` / `{NARRATIVE, official-briefing}`; `dickens` file context: `{NARRATIVE, dickens}` valid, `{OFFICIAL, dickens}` → 400. Precedence (one case each): realism 11 + bad style → realism message; unknown wildcard + bad style → wildcard message; 4 custom wildcards + bad style → "At most 3 custom wildcards"; story false + bad style → "Story output is required"; illustration true + bad style → illustration message; bad style + bad language → "Unknown writing style"; bad style without connection → 400 not 401; valid style + an active run (connected) → 409 `RUN_ALREADY_ACTIVE` unchanged. Store normalisation (frontend, parameterized): absent, `null`, `"kafka"`, `{}`, `{mode:"narrative",profileId:"kafka"}`, `{OFFICIAL, kafka}`, `{NARRATIVE, official-briefing}`, `{NARRATIVE, dickens}` (not in catalogue) → `{OFFICIAL, official-briefing}`; each of the 9 valid pairs kept; `{NARRATIVE, kafka, foo:1}` → `{NARRATIVE, kafka}`. Invariants: the configuration of the 202 / `getRun` / `getScenarioConfiguration` has `output.writingStyle` deep-equal to the request's (minus unknown inner keys) or absent when not sent; `narrative-style-section` present ⇔ store mode NARRATIVE; exactly one checked radio in each visible group, and it is the store's value; the store's profile always has the store's mode after any sequence of `load` / `setWritingMode` / `setNarrativeProfile` (parameterized over both modes × every profile id + an unknown id); narrative option count = number of NARRATIVE catalogue profiles (8 shipped) in catalogue order with the catalogue labels; desktop shows only the radio group, mobile only the select, never both; every UI-sent `startRun` body (GENERATE, MORE REALISTIC, DARKER, MORE OPTIMISTIC, MORE EXTREME, LOWER REALISM in both views) has `output.writingStyle` and `output.language` equal to the store's; a style change leaves every other configuration field unchanged and every other setter (realism, darkness, optimism, horizon, wildcard on/off/intensity, custom add/remove/intensity, language) leaves `writingStyle` unchanged; the panel becomes ready only with all three load responses, for each of the 6 arrival orders, and goes to the error state when any one of the three fails (3 cases).

### FR-64 — Story language select
- Happy path (panel, below the Writing Style group): `mat-form-field` with label "Story language" and `mat-select`
  `story-language-select` with `mat-option`s `story-language-option-en` "English" and `story-language-option-uk`
  "Ukrainian" (from `languages`). Fresh session → shows "English". Selecting Ukrainian → `ScenarioStore.configuration()
  .output.language` = `"uk"`; the next `startRun` sends it and the run's configuration holds it.
- Rules: the language applies only to the story (`styled-story.md` FR-66); the UI, the AI labels, metadata labels and
  every other text stay English. Reload restores the newest run's language (absent → English, unknown → English).
  Changing the language never changes any other panel value.
- Errors (backend, `POST /api/runs`, checked after `output.writingStyle`): `output.language` present, not `null` and
  not exactly the JSON string `"en"` or `"uk"` (`"EN"`, `"ua"`, `"uk-UA"`, `"de"`, `""`, `1`, `true`, `["uk"]`, `{}`)
  → 400 `VALIDATION_FAILED` → "Unknown story language"; no run is stored. Absent / `null` → accepted (`en`).
- Slice 02_style-selection delta: see "Slice 02_style-selection — precise delta" under FR-63 (validation order, stored
  configuration, `ScenarioStore.setLanguage` / normalisation, `story-language-select` DOM, E2E cases 1, 3, 4, 6).
- Changes earlier behaviour: none — the `output.language` part of the panel body / store change and the third load request are one change with `output.writingStyle` and are listed (with their test files) under FR-63, so each file is rewritten once
- Ranges & invariants: language (backend, each class its own parameterized case): valid → passes validation (401 in `@WebMvcTest` without connection, 202 + stored in an IT): `"en"`, `"uk"` (stored as sent), absent, `null` (both stored absent); invalid → 400 "Unknown story language": `"EN"`, `"Uk"`, `"UK"`, `"ua"`, `"uk-UA"`, `"en-US"`, `"de"`, `""`, `" en"`, `"uk "`, `1`, `0`, `true`, `["uk"]`, `{}`, `{"code":"uk"}`. Precedence: story false + bad language → "Story output is required"; illustration true + bad language → illustration message; bad style + bad language → "Unknown writing style"; valid style + bad language → "Unknown story language"; bad language without connection → 400 not 401. Store (frontend, parameterized): `load` with language absent, `null`, `"de"`, `"EN"`, `"ua"`, `1` → `"en"`; `"en"`, `"uk"` kept; `setLanguage` with anything but `"en"` / `"uk"` ignored. Invariants: `story-language-select` shows exactly one of `English` / `Ukrainian`, equal to the store's language; the overlay has exactly the 2 options in order en, uk; `getRun.configuration.output.language` equals the request's (absent when not sent); a language change leaves every other configuration field (including `writingStyle`) unchanged and no other setter changes the language; every UI-sent `startRun` body carries the store's language.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/writing-styles | getWritingStyles | — | 200 WritingStyleCatalogue · 500 INTERNAL_ERROR |
| POST | /api/runs | startRun | ScenarioConfiguration (+ optional `output.writingStyle`, `output.language`) | as before; new 400 messages "Unknown writing style", "Unknown story language" |
| GET | /api/scenario/configuration | getScenarioConfiguration | — | unchanged; the newest run's configuration now may carry `output.writingStyle` / `output.language` |

`ScenarioCatalogue.defaults` is unchanged (no style keys); the style defaults come from `getWritingStyles`.

## UI
- Route: `/` and `/futures/:runId` (panel) · component `OutputSettings` (`app-output-settings`) in
  `src/app/scenario/output-settings.ts` extended; catalogue in a `WritingStyleStore` (or the `ScenarioLoader`) loaded by
  `ScenarioLoader.load()` in parallel with catalogue and configuration · Material: `mat-radio-group`, `mat-radio-button`,
  `mat-form-field`, `mat-select`, `mat-option`.
- States: loading / error as the panel (`app-loading`, `backend-unavailable`) · OFFICIAL (no narrative section) ·
  NARRATIVE desktop (radio list) · NARRATIVE mobile (dropdown).
- `data-testid`s: `writing-style-section`, `writing-style-group`, `writing-style-option-OFFICIAL`,
  `writing-style-option-NARRATIVE`, `narrative-style-section`, `narrative-style-group`,
  `narrative-style-option-<profileId>`, `narrative-style-select`, `narrative-style-select-option-<profileId>`,
  `story-language-select`, `story-language-option-en`, `story-language-option-uk`. Existing: `output-section`,
  `output-story`, `output-illustration`, `output-illustration-badge`.
- Keyboard: radio groups operable with arrow keys, selects with Enter / arrows; accessible names equal the visible
  labels (NFR-5).
