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

### Interfaces (slice 03_styled-story — exact names, so tests can reach them by reflection)
| Element | Signature / content |
|---|---|
| domain language | `com.oracul.app.style.Language` enum `EN` (`code()` `"en"`, `label()` `"English"`), `UK` (`"uk"`, `"Ukrainian"`); generated `StoryLanguage` is mapped to it at the boundary (`StoryStyle.of`), never switched on in domain code |
| effective style | `com.oracul.app.result.StoryStyle` record `(WritingStyleProfile profile, Language language)`; `static StoryStyle of(ScenarioConfiguration configuration, WritingStyles styles)`: `profile` = `styles.find(output.writingStyle.profileId)`, else `styles.find("official-briefing")` (configuration `null`, `output` `null`, `writingStyle` absent / `null`, `profileId` not in the file). The stored `mode` is not used for the lookup: the profile's own `mode` is the effective mode. `language` = `UK` for stored `uk`, else `EN` (absent / `null`) |
| mode label | `WritingMode.OFFICIAL` → `Official / Briefing`, `WritingMode.NARRATIVE` → `Narrative` (the same texts as `modes[].label` of `GET /api/writing-styles`) |
| prompt | `StoryWritingPrompt.inputText(EvidencePack pack, StructuredScenario scenario, StoryRequest req, StoryStyle style)` and `StoryWritingPrompt.body(String model, EvidencePack pack, StructuredScenario scenario, StoryRequest req, StoryStyle style)`. The former 3-arg `inputText` / 4-arg `body` are removed. `StoryRequest(attempt, storyErrors)` is unchanged (2 components). `static String styleSection(StoryStyle style)` = the WRITING STYLE section lines joined by `\n`, first line `WRITING STYLE`, no trailing newline (T contains it verbatim). `INSTRUCTIONS` stays a public constant |
| dateline | `Datelines.format(LocalDate d, Language language)`; `Datelines.format(LocalDate d)` stays = `format(d, Language.EN)`. Ukrainian months come from the fixed table of FR-66, not from JDK locale data |
| writer | `StoryWriter` reads the run's own `GenerationRunRepository.Row.configuration()`, builds `StoryStyle.of(configuration, writingStyles)` once per stage, passes it to both attempts, and sets the stored and served `dateline` = `Datelines.format(story.futureDate, style.language())` for the parsed story and for the date fallback alike. `StoryParser.parse(outputText, cutoff, windowEnd)` is unchanged (it still returns the English dateline, which `StoryWriter` replaces) |

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
- Tests (what the tester can assert):
  - Unit (`StoryWritingPromptTest`, pure, profiles from `new WritingStyles("classpath:writing-styles.json",
    new DefaultResourceLoader())`): `INSTRUCTIONS` = `StoryFixtures.INSTRUCTIONS` with the new rules; the full T of the
    FR-23 example run for `official-briefing`/`EN` and for `kafka`/`UK` (lines 1–8 as FR-23, lines 9–20 the section,
    then `TASK`); attempt 2 keeps the correction line and the `story-errors` block after `TASK`.
  - Unit (`StoryStyleTest`): every class of the `StoryStyle.of` row of "Interfaces".
  - Backend IT (stub `StubResponses`, run started through `POST /api/runs` with `output.writingStyle` /
    `output.language`): the recorded STORY_WRITING `input` contains the section of the run's style; two runs with
    `{OFFICIAL, official-briefing}`/`en` and `{NARRATIVE, kafka}`/`uk` in one session record pairwise equal bodies for
    every non-STORY_WRITING request (same order, compared as JSON) and STORY_WRITING inputs that are equal after
    `styleSection` is removed; no request but STORY_WRITING contains `WRITING STYLE`, `Style profile:`, any shipped
    profile label, `Ukrainian` or `kafka`; an ALTERNATIVE of the Kafka/uk run sends the Kafka/uk section.
  - E2E (`e2e/tests/styled-story.spec.ts`): two runs started via `POST /api/runs` that differ only in
    `output.writingStyle` (`official-briefing` vs `poe`, both `en`): the Responses requests of
    `GET http://localhost:4010/__control/requests?kind=responses` per run (between `POST /__control/reset` and the
    terminal status) are pairwise identical except STORY_WRITING; the two `structured-scenario` blocks are byte-identical;
    the Poe result shows `story-headline` `Stub headline from the future — Narrative — inspired by Edgar Allan Poe`.
- Changes earlier behaviour: `RULES` lines "Write a short news story from the future …", "headline: one line of at most 160 characters.", "dateline: … written as Month d, yyyy." and "body: plain text of 150 to 900 words, …" → the 12 lines of FR-65 above (tests: backend/src/test/java/com/oracul/app/result/StoryFixtures.java, backend/src/test/java/com/oracul/app/result/StoryWritingIT.java)
- Changes earlier behaviour: STORY_WRITING `input` line `Future event date: <d>` directly followed by `TASK` → the WRITING STYLE section between them; `StoryWritingPrompt.inputText(pack, scenario, req)` / `body(model, pack, scenario, req)` → `inputText(…, req, StoryStyle)` / `body(…, req, StoryStyle)` (tests: backend/src/test/java/com/oracul/app/result/StoryWritingPromptTest.java, backend/src/test/java/com/oracul/app/result/StoryHarness.java)
- Changes earlier behaviour: backend stub default STORY_WRITING answer always ST-DEFAULT → the styled answer of "Stubs" (Official / Briefing + English, i.e. every earlier run, still ST-DEFAULT) (tests: backend/src/test/java/com/oracul/app/research/StubResponses.java)
- Ranges & invariants: for each of the 9 shipped profiles × 2 languages (18 cases, parameterized): T contains exactly
  one `WRITING STYLE` line, directly after `Future event date:` and before `TASK`; the section has the profile's label,
  characteristics and instructions verbatim, the mode lines of its mode (OFFICIAL 2 lines with `SITUATION.`,
  `CONSEQUENCES.`, `OUTLOOK.`; NARRATIVE 3 lines with "Do not copy or quote" and "Do not add events, dates or outcomes")
  and the language line; `instructions` identical in all 18; T with the WRITING STYLE section removed is identical in all
  18; exactly one start and one end marker per data block; no `{` or `<` in `instructions`; no profile text inside a
  data block. Absent `writingStyle` / `language` → the same T as `official-briefing` / `en`. `StoryStyle.of` classes
  (parameterized): configuration `null`, `output` `null`, `writingStyle` absent, `writingStyle` `null`, profileId
  `dickens` (not in the file) → `official-briefing`; each of the 9 shipped `(mode, profileId)` pairs → that profile;
  stored `{OFFICIAL, kafka}` → `kafka` (profile's own mode NARRATIVE); language absent / `null` / `en` → `EN`, `uk` → `UK`.
  Uk suffix only for OFFICIAL + `uk` (exactly 1 of the 18 cases contains `СИТУАЦІЯ.`).

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
- Tests (what the tester can assert):
  - Unit (`DatelinesTest` gets new cases only): the Ukrainian table and the English parity below.
  - Backend IT: a run with `output.language` `uk` (style `kafka`) completes; `GET /api/runs/{runId}/result` → 200 with
    `story.headline` `Заголовок із майбутнього — Narrative — inspired by Franz Kafka`, `story.body` = UBODY(3),
    `story.dateline` = `ORACUL FUTURE — <d> <місяць> <yyyy>` of `story.futureDate`, `labels` =
    `["AI-GENERATED FUTURE SCENARIO","POSSIBLE FUTURE — NOT CURRENT NEWS"]`; `future_story.dateline` row equal; with the
    `BADDATE` fixture on attempt 1 and 2 (date fallback) the dateline is the Ukrainian form of the future event date;
    the same run with `en` keeps `ORACUL FUTURE — <Month d, yyyy>`.
  - Frontend unit (`frontend/src/app/result/future-result.spec.ts`, new `it` only): a result with headline
    `Заголовок із майбутнього — Narrative — inspired by Franz Kafka`, dateline `ORACUL FUTURE — 12 вересня 2032` and a
    2-paragraph Ukrainian body renders them verbatim in `story-headline`, `story-dateline`, `story-paragraph` (×2), and
    `label-ai-generated` / `label-not-current-news` keep the English texts above `story-headline`.
  - E2E (`e2e/tests/styled-story.spec.ts`): a run started from the panel with `writing-style-option-NARRATIVE`,
    `narrative-style-option-kafka` and `story-language-option-uk`, opened at `/futures/:runId`: `story-dateline` matches
    `^ORACUL FUTURE — [1-9][0-9]? (січня|…|грудня) [0-9]{4}$` and equals the Ukrainian form of `story.futureDate`,
    `story-headline` is the Ukrainian stub headline, the two labels are English.
- Changes earlier behaviour: `Datelines.format(d)` is the only formatter → `Datelines.format(d, language)` added, the 1-arg form unchanged (tests: backend/src/test/java/com/oracul/app/result/StoryHarness.java)
- Changes earlier behaviour: none for stored / served datelines of English runs — every existing run in the tests has no `output.language` or `en` (grep: `StoryWritingIT`, `StoryParserTest`, `DatelinesTest`, `e2e/tests/future-story.spec.ts`, `e2e/tests/why-and-sources.spec.ts`, `frontend/src/app/result/*.spec.ts`, `frontend/src/app/runs/*.spec.ts` assert `ORACUL FUTURE — <Month d, yyyy>` only for English runs, so they stay green)
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
UBODY(3) = 180 words.
Parsing: `<L>` = the text after `Style profile: ` up to the line end (regex `^Style profile: (.+)$`, multiline);
Ukrainian when the `^Story language: (.+)$` value is `Ukrainian`, else English. Mode precedence (E2E stub): `rate-limited`
→ 429; `invalid` / `invalid-once` (call 1) → `not json`; `bad-date` / `bad-date-once` (call 1) → ST-DEFAULT with the
cutoff date (unchanged, English headline whatever the style); every other call → the styled answer of the table. Backend
`StubResponses.defaultResponder()` answers STORY_WRITING with the styled answer (`StoryFixtures.styled(inputText)`, new);
named fixtures (`StoryFixtures.fixture`) are unchanged. Owners: `e2e/stubs/server.mjs` = backend-builder (stub service);
`StubResponses` / `StoryFixtures` = tester. The E2E stub image is not rebuilt by `stack.mjs up` (`e2e/` is in
`IMAGE_EXCLUDE`): the first E2E run of this slice needs `node factory-engine/bin/stack.mjs up --build`. `.oracul/stack.json`
is unchanged (no new compose file, profile or service). Every Responses request keeps being recorded (`GET /__control/requests?kind=responses`, request
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
- data-testid used by the slice's tests (all existing): result `result-view`, `story-labels`, `label-ai-generated`,
  `label-not-current-news`, `story-headline`, `story-dateline`, `story-body`, `story-paragraph`; panel (slice 02)
  `writing-style-option-NARRATIVE`, `narrative-style-option-<profileId>` (desktop radios), `story-language-select` →
  `story-language-option-uk`.
- States: unchanged (loading · success · failure).
