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
  - unexpected failure while answering → 500 `INTERNAL_ERROR` → "Something went wrong — try again"
  - invalid style file → no HTTP answer at all: the backend does not start (message above in the log)
- Ranges & invariants: profiles count 2..50 (1 and 51 → startup fails); id `^[a-z0-9-]{1,40}$` (40 chars ok, 41 / `Kafka` /
  `k_a` / `""` → fails); label 1..80, characteristics 1..300, instructions 1..2000 (boundary and boundary + 1 per field);
  invariants: `profiles[0].id` = `official-briefing`; exactly one profile with `isDefault`; exactly one `isModeDefault`
  per mode; every profile's `mode` ∈ modes; ids unique; every `defaultProfileId` names a listed profile of that mode;
  the response never contains a key `instructions`; listed NARRATIVE order = file order.

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
    "official-briefing"}` or `{"mode":"NARRATIVE","profileId":"<id>"}`), sent with every `startRun` (generate, quick
    regeneration, Try again, LOWER REALISM) and therefore snapshotted into the run's configuration.
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
- Ranges & invariants: valid class = the 9 (mode, profileId) pairs of the shipped file → 202; invalid classes above →
  400 "Unknown writing style" (each tested); precedence: a custom wildcard violation, `story` false or
  `illustration` true together with an unknown style → that earlier message; an unknown style together with an unknown
  language → "Unknown writing style". Invariants: `configuration.output.writingStyle` of the 202 / `getRun` deep-equals
  the request's; the panel shows `narrative-style-section` ⇔ mode NARRATIVE; exactly one option checked in each
  visible group; the store's profile always belongs to the store's mode; every `startRun` body sent by the UI has
  `output.writingStyle` and `output.language`.

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
- Ranges & invariants: valid classes `en`, `uk`, absent, `null` → 202 (absent and `null` stored as absent); every
  invalid class above → 400 (each tested); invariants: the select shows exactly one of English / Ukrainian; the
  language of `getRun.configuration` equals the request's.

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
