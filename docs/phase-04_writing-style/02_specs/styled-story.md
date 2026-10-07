# Spec — Styled story: WRITING STYLE section in STORY_WRITING, story language and dateline

Covers: FR-65, FR-66

Delta against phase-01 `future-result.md` FR-23 ("Slice 09_future-story": `StoryWritingPrompt`, `StoryParser`,
`Datelines`, `StoryWriter`, E2E stub STORY_WRITING answer) and the current code
`backend/src/main/java/com/oracul/app/result/StoryWritingPrompt.java` (`ClosedEvidenceMode.INSTRUCTIONS + RULES`),
`Datelines.java`, `StoryWriter.java`, `e2e/stubs/server.mjs` (purpose STORY_WRITING), backend test support
`StubResponses` / `StoryFixtures`. The style catalogue and the configuration fields are in `writing-styles.md`.
Nothing before stage WRITING_STORY changes (search, Evidence Pack, scenario generation, Evidence Guard, critic).

## Superseded behaviour
| Earlier rule | Status from this phase on |
|---|---|
| FR-23 `RULES` line "Write a short news story from the future about the future event of the scenario in structured-scenario, reported on a date inside the story date window given in SETTINGS." | **Superseded** by FR-65: the story is written "in the writing style and the story language given in WRITING STYLE" (rules text below). Official / Briefing is now an analytical report, not a "short news story". |
| FR-23 `RULES` line "dateline: ORACUL FUTURE — followed by futureDate written as Month d, yyyy." | **Superseded** by FR-66: "dateline: ORACUL FUTURE — followed by futureDate; ORACUL replaces it with its own dateline." |
| FR-23 STORY_WRITING `input`: SETTINGS lines, then TASK | **Extended** by FR-65: a `WRITING STYLE` section between the `Future event date:` line and `TASK`. Lines before it, the TASK lines, the `structured-scenario` block, the `story-errors` block and the trailer are unchanged. |
| FR-23 / `Datelines.format(d)`: always English `ORACUL FUTURE — <Month d, yyyy>` | **Superseded** by FR-66: `Datelines.format(d, language)` — English unchanged, Ukrainian `ORACUL FUTURE — <d> <genitive month> <yyyy>`. API `FutureStory.dateline` pattern extended. |
| E2E / backend stub: every STORY_WRITING request answered with ST-DEFAULT | **Extended**: per style and language canned stories (below); Official / Briefing in English keeps ST-DEFAULT, so earlier assertions on "Stub headline from the future" stay valid. |

## Purpose
The same validated scenario can be told as a concise analytical briefing or as literary prose evoking an author, in
English or Ukrainian, while the events, dates, actors and causes stay exactly those of the scenario and the story is
still clearly labelled as an AI-generated possible future.

## Data
| Entity | Field | Type | Rules |
|---|---|---|---|
| run configuration | `output.writingStyle`, `output.language` | see `writing-styles.md` | read from the run's **own** `generation_run.configuration` (never from `evidence_pack.configuration`, which is the root STANDARD run's) |
| `future_story` | `dateline` | varchar(64) | built by `Datelines.format(futureDate, language)` (longest Ukrainian form `ORACUL FUTURE — 30 листопада 2046` fits) |
| `future_story` | headline, body | text | in the story language; validation unchanged |

Effective style of a run = its `output.writingStyle`, or `official-briefing` when absent, or `official-briefing` when the
stored profile is no longer in the style file. Effective language = `output.language`, or `en` when absent.

## Behaviour

### FR-65 — Style applied only when the story is written
- Happy path: stage WRITING_STORY (`StoryWriter`, unchanged flow: ≤ 2 attempts, date fallback, transport rules) sends
  the STORY_WRITING request built by `StoryWritingPrompt` with the run's effective style and language:
  - `instructions` = `ClosedEvidenceMode.INSTRUCTIONS + "\n" + RULES`, byte-identical for every run, style and attempt,
    no `{` or `<`. `RULES` (lines joined by `\n`):
    ```
    Return only JSON matching the schema.
    Write the story of the future event of the scenario in structured-scenario, reported on a date inside the story date window given in SETTINGS, in the writing style and the story language given in WRITING STYLE.
    Use only the facts, inferences, speculations and the future event of that scenario. Do not add current-world facts that are not facts of the scenario.
    Keep present-day facts recognisable as reported facts and everything after the cutoff date recognisably hypothetical.
    The scenario's events, dates, actors and causes take priority over the writing style: the style changes only how the future is told, never what happens, when, who acts or why.
    Never add events, dates or outcomes that are not in the scenario.
    headline: one line of at most 160 characters in the story language.
    futureDate: the date of the story as yyyy-MM-dd inside the story date window; prefer the future event date.
    dateline: ORACUL FUTURE — followed by futureDate; ORACUL replaces it with its own dateline.
    body: plain text of 150 to 900 words in the story language, paragraphs separated by one blank line, no HTML and no Markdown.
    Do not write the labels AI-GENERATED FUTURE SCENARIO or POSSIBLE FUTURE — NOT CURRENT NEWS; ORACUL adds them.
    Match the tone to Darkness and Optimism; they never permit invented evidence.
    ```
  - `input` text T: the FR-23 lines up to and including `Future event date: <date>`, then the WRITING STYLE section,
    then `TASK` and everything after it exactly as in FR-23 (the TASK line stays "Write the story of the future event
    of the scenario in structured-scenario under the settings above."). WRITING STYLE section (lines joined by `\n`):
    ```
    WRITING STYLE
    Writing mode: <mode label: Official / Briefing | Narrative>
    Style profile: <profile label>
    Style characteristics: <profile characteristics>
    Story language: <English | Ukrainian>
    Style instructions: <profile instructions>
    <mode lines>
    <language line>
    Priority: the scenario's events, dates, actors and causes take priority over this writing style.
    ```
    Mode lines OFFICIAL (2 lines; for language `uk` the second line gets the suffix
    ` Write the lead-ins in Ukrainian: СИТУАЦІЯ., НАСЛІДКИ., ПЕРСПЕКТИВА.`):
    ```
    Write a factual, concise and neutral analytical report about events, consequences, actors, dates and causal relationships, with no literary description and no invented scenes or characters.
    Structure the body by short capitalised lead-ins: the first paragraph starts with SITUATION., a middle paragraph with CONSEQUENCES. and the last paragraph with OUTLOOK.
    ```
    Mode lines NARRATIVE (3 lines, for every NARRATIVE profile incl. `neutral-narrative` and profiles added later):
    ```
    Write literary prose that evokes the tone and themes of this style profile; atmosphere, scene setting, imagery and invented minor characters or scenes are allowed.
    Do not copy or quote the author's text, signature phrases or characters; write original prose that is only inspired by the tone and themes.
    Do not add events, dates or outcomes beyond the scenario.
    ```
    Language line: `en` → `Write the headline and the body in English.`; `uk` → `Write the headline and the body in
    Ukrainian.`
  - Body keys, `model`, `text` (JSON schema `future_story`), `store` false: unchanged; no `tools`, `tool_choice` or
    `web_search*` (NFR-3).
  - Example (FR-23 example run, profile `kafka`, language `uk`), lines 9–20 of T:
    `WRITING STYLE` · `Writing mode: Narrative` · `Style profile: Narrative — inspired by Franz Kafka` ·
    `Style characteristics: bureaucratic absurdity, alienation, impersonal systems, helplessness, surreal institutional logic, understated anxiety` ·
    `Story language: Ukrainian` · `Style instructions: Evoke bureaucratic absurdity …` · the 3 NARRATIVE lines ·
    `Write the headline and the body in Ukrainian.` · `Priority: …` — then `TASK`.
- Rules:
  - Style and language appear only in the STORY_WRITING request: no QUERY_GENERATION, EVENT_NORMALIZATION,
    EVENT_CLASSIFICATION, SCENARIO_GENERATION or SCENARIO_CRITIC request, news / article request, Evidence Pack
    `promptText`, research profile or search plan contains a profile id, a profile label, a language code / label or the
    WRITING STYLE section. Two runs whose configurations differ only in `output.writingStyle` / `output.language`
    (same session, same UTC day, same stub state) send pairwise identical non-STORY_WRITING Responses requests and
    identical news requests; their STORY_WRITING requests differ only inside the WRITING STYLE section.
  - The `structured-scenario` block is built exactly as in FR-23 from the accepted cleaned scenario (byte-identical for
    the same scenario, whatever the style).
  - Story validation is unchanged for every style and language (`StoryParser`: plain text, 150–900 words, headline
    1–160 characters, futureDate in the window; same retry and fallback); the result still carries both AI labels.
    The parser does not check the lead-ins, the language or the tone.
  - ALTERNATIVE runs (configuration copied from the parent) and REWRITE runs (`story-rewrite.md`) use their own
    configuration's style and language.
- Errors: unchanged from FR-23 — story invalid twice → FAILED `INVALID_SCENARIO` "ORACUL could not construct a valid
  scenario"; 429 → `CHATGPT_RATE_LIMITED`; 5xx / timeout → `CHATGPT_UNAVAILABLE`; 401/403 after refresh →
  `CHATGPT_SESSION_EXPIRED` (messages of `RunFailureCode`). A style can never cause a new failure code.
- Ranges & invariants: for each of the 9 shipped profiles × 2 languages (18 cases, parameterized): T contains exactly
  one `WRITING STYLE` line, directly after `Future event date:` and before `TASK`; the section has the profile's label,
  characteristics and instructions verbatim, the mode lines of its mode (OFFICIAL 2 lines with `SITUATION.`,
  `CONSEQUENCES.`, `OUTLOOK.`; NARRATIVE 3 lines with "Do not copy or quote" and "Do not add events, dates or outcomes")
  and the language line; `instructions` identical in all 18; T with the WRITING STYLE section removed is identical in all
  18; exactly one start and one end marker per data block; no `{` or `<` in `instructions`; no profile text inside a
  data block. Absent `writingStyle` / `language` → the same T as `official-briefing` / `en`.

### FR-66 — Story in the chosen language
- Happy path: with language `uk` the request asks for Ukrainian headline and body (FR-65); the stored story keeps the
  returned headline and body; `dateline` = `Datelines.format(futureDate, UK)` = `ORACUL FUTURE — <d> <month> <yyyy>`
  (day without leading zero, Ukrainian genitive month name, 4-digit year, U+2014 em dash with one space each side),
  e.g. 2032-09-12 → `ORACUL FUTURE — 12 вересня 2032`. With `en` (or absent) → `ORACUL FUTURE — September 12, 2032`
  (unchanged). The result view shows `story-headline`, `story-dateline`, `story-body` as before; `label-ai-generated`
  "AI-GENERATED FUTURE SCENARIO" and `label-not-current-news` "POSSIBLE FUTURE — NOT CURRENT NEWS" stay English and
  above the headline.
- Rules:
  - Ukrainian month names (month 1–12): січня, лютого, березня, квітня, травня, червня, липня, серпня, вересня,
    жовтня, листопада, грудня.
  - The date fallback of FR-23 (attempt 2 only date errors → futureDate = scenario future event date) rebuilds the
    dateline in the run's language.
  - Word count is language-independent: words = tokens of `\s+` after normalisation (FR-23 parser), so Cyrillic words
    count exactly like English words.
  - Everything else in the UI (panel, metadata labels, buttons, failure messages) stays English.
- Errors: as FR-65 (unchanged FR-23 failures); a Ukrainian story outside 150–900 words follows the FR-23 retry → FAILED
  `INVALID_SCENARIO` path.
- Ranges & invariants: `Datelines.format(d, UK)` for the 1st and the last day of each of the 12 months (24 cases) and
  for days 1, 9, 10, 31 → the table month and no leading zero; English output identical to the phase-01 formatter for
  the same 24 dates; every produced dateline matches the `FutureStory.dateline` pattern of the contract; Ukrainian body
  word counts 149 → error `body must have 150 to 900 words (found 149)`, 150 → ok, 900 → ok, 901 → error (found 901);
  the API `labels` are exactly the two English strings for every language.

## Stubs (deterministic; E2E `e2e/stubs/server.mjs` and backend `StubResponses` / `StoryFixtures` alike)
The STORY_WRITING answer is chosen from the request's `Style profile:` and `Story language:` lines (no WRITING STYLE
section → as Official / Briefing, English); the existing story modes (`bad-date`, `bad-date-once`, `invalid`,
`invalid-once`, `rate-limited`) still apply first; futureDate = the `Future event date:` value as before:
| Style profile | Language | headline | body |
|---|---|---|---|
| Official / Briefing | English | `Stub headline from the future` (ST-DEFAULT, unchanged) | BODY(3) |
| any other label `<L>` | English | `Stub headline from the future — <L>` | BODY(3) |
| any label `<L>` | Ukrainian | `Заголовок із майбутнього — <L>` | UBODY(3) |
`UBODY(n)` = n paragraphs joined by `\n\n`; paragraph k = `Абзац <k>` followed by 58 × ` слово` (60 words), so
UBODY(3) = 180 words. Every Responses request keeps being recorded (`GET /__control/requests?kind=responses`, request
bodies as received), so tests can compare the `structured-scenario` blocks and WRITING STYLE sections byte for byte.

## Non-functional — NFR-12 (real-service check, release step)
Not a stub test: after the release the user runs ORACUL in real mode (real ChatGPT account, real Google News) and the
evidence goes to `docs/phase-04_writing-style/05_release/real-check.md`: one scenario as Official / Briefing in English,
then "Rewrite as…" Narrative — inspired by Franz Kafka (Ukrainian) and Narrative — inspired by Edgar Allan Poe
(Ukrainian): same future event and date, clearly different tone, no new search for the rewrites (backend log / request
counts), Ukrainian dateline with a Ukrainian month name; plus the carried phase-03 NFR-11 check (New pandemic at 1 and 10
with Darkness 5, custom wildcard "AI takeover" 5: ≥ 1 source with retrieved article text per enabled wildcard, visibly
different query intensity, story completes, no GDELT request in the log). The phase is GREEN only after this check.

## API (must match api/openapi.yaml)
| Method | Path | operationId | Request | Responses |
|---|---|---|---|---|
| GET | /api/runs/{runId}/result | getFutureResult | — | unchanged statuses; `story.dateline` in the run's language (pattern extended), headline/body in the run's language, `labels` English |

No new operation and no new error code.

## UI
- Route: `/futures/:runId` (`result-view`) — no new element; `story-headline`, `story-dateline`, `story-body`,
  `story-paragraph`, `label-ai-generated`, `label-not-current-news` render the language-specific text as text (never
  `innerHTML`).
- States: unchanged (loading · success · failure).
