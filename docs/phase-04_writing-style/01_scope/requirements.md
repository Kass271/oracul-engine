# Requirements — phase-04_writing-style

Status: APPROVED ✔ 2026-10-07

Numbering continues across phases (next free ids come from `state.mjs next-fr`).
Format is parsed by the checks — keep the heading and bullet shapes exactly.

This phase separates what the future is from how it is told. The writing style and the story language change only
the final story-writing call (STORY_WRITING); search, Evidence Pack, scenario generation, critic and Evidence Guard
are untouched, so the same validated scenario can be told as an official briefing or as an author-inspired narrative,
in English or Ukrainian, and re-told without a new search. It corrects phase-01 FR-6 (Output section), FR-23 (story
prompt and dateline), FR-25 (metadata) and FR-33 (Recent futures). The phase-03 real check (NFR-11) was not done and
is carried into NFR-12.

## Functional

### FR-62 — Writing style catalogue
- UI: no
- Description: ORACUL offers a catalogue of writing styles — the mode Official / Briefing, and the mode Narrative with the profiles Neutral Narrative, Edgar Allan Poe, Franz Kafka, H. G. Wells, Jules Verne, George Orwell, Arthur C. Clarke and Harry Harrison — defined as entries in a backend style file (id, mode, label, characteristics, prompt instructions), so a profile can be added later without changing the pipeline.
- Acceptance:
  - Given the running backend, when I call GET /api/writing-styles, then I get the two modes and the nine profiles with id, mode, label and characteristics, Official / Briefing first and marked as the default, Neutral Narrative marked as the Narrative default
  - Given an author profile, when it is listed, then its label reads "Narrative — inspired by <author>" (e.g. "Narrative — inspired by Franz Kafka")
  - Given the style file gains a new valid profile, when the backend restarts, then the profile is listed and usable without any other code change
  - Given a style file entry with a missing id, label or prompt text, when the backend starts, then it refuses to start with a message naming the entry

### FR-63 — Writing Style selector
- UI: yes
- Corrects: FR-6 (Output section)
- Description: In the Output section, next to Story, the user picks the Writing Style — Official / Briefing (default) or Narrative — and, when Narrative is selected, a Narrative Style (default Neutral Narrative); the choice is part of the scenario configuration of the session and of every run started from it.
- Acceptance:
  - Given a fresh session, when the page loads, then Writing Style shows Official / Briefing selected and no Narrative Style list is visible
  - Given I select Narrative, when I look at the panel, then a Narrative Style list with the eight narrative profiles appears with Neutral Narrative selected
  - Given I select Narrative and Franz Kafka and generate, when the run is created, then its configuration holds writing style Narrative / Franz Kafka
  - Given I reload the page in the same session, when the panel loads, then my last style choice is still selected
  - Given an API request with an unknown style id, or a narrative profile with mode Official / Briefing, when the backend validates it, then it answers 400 with "Unknown writing style" and no run is started
  - Given a phone-width screen, when Narrative is selected, then the Narrative Style choice is a dropdown instead of radio buttons

### FR-64 — Story language select
- UI: yes
- Corrects: FR-6 (Output section)
- Description: In the Output section the user picks the story language from a select list — English (default) or Ukrainian; the choice is part of the scenario configuration of the session and of every run started from it, and applies to the generated story only.
- Acceptance:
  - Given a fresh session, when the page loads, then the Story language select shows English
  - Given I select Ukrainian and generate, when the run is created, then its configuration holds language Ukrainian
  - Given I reload the page in the same session, when the panel loads, then my last language choice is still selected
  - Given an API request with a language other than English or Ukrainian, when the backend validates it, then it answers 400 with "Unknown story language" and no run is started

### FR-65 — Style applied only when the story is written
- UI: no
- Corrects: FR-23 (story prompt)
- Description: The writing style and language change only the STORY_WRITING request: it gains a WRITING STYLE section (mode, profile, characteristics, language) and the rule that the scenario's events, dates, actors and causes take priority over style; Official / Briefing tells the future as a concise analytical report, Narrative profiles as literary prose that evokes the author's tone and themes without copying or quoting the author's text, signature phrases or characters, and without adding events, dates or outcomes beyond the scenario.
- Acceptance:
  - Given two runs with identical configuration except the writing style, when their requests are recorded by the stub, then the search, query-generation, scenario-generation and critic requests are identical and only the STORY_WRITING request differs
  - Given any STORY_WRITING request, when it is recorded, then it contains the WRITING STYLE section with the selected mode, profile label, characteristics and language, the priority rule, and the structured scenario as delimited data, and has no tools
  - Given Official / Briefing, when the request is recorded, then its instructions ask for factual, concise, neutral prose structured by short capitalised lead-ins (SITUATION, CONSEQUENCES, OUTLOOK) and no literary description
  - Given an author profile, when the request is recorded, then its instructions forbid copying or quoting the author's text, signature phrases and characters and forbid new events, dates or outcomes
  - Given any style, when the story is validated, then the existing rules apply unchanged (plain text, 150–900 words, headline 1–160 characters, date inside the horizon) and the result still shows both AI labels

### FR-66 — Story in the chosen language
- UI: yes
- Corrects: FR-23 (dateline)
- Description: The headline and body of the story are written in the chosen language and the dateline uses that language's month name; the UI and the two AI labels stay in English.
- Acceptance:
  - Given language Ukrainian and a future date of 12 September 2032, when the result is shown, then the dateline reads "ORACUL FUTURE — 12 вересня 2032" and the headline and body are those returned in Ukrainian
  - Given language English and the same date, when the result is shown, then the dateline reads "ORACUL FUTURE — September 12, 2032" as today
  - Given language Ukrainian, when the result is shown, then "AI-GENERATED FUTURE SCENARIO" and "POSSIBLE FUTURE — NOT CURRENT NEWS" are shown in English above the story
  - Given language Ukrainian, when the story body is validated, then the 150–900 word bound counts Ukrainian words the same way as English words

### FR-67 — Rewrite the same future in another style or language
- UI: yes
- Description: On a completed result the user picks "Rewrite as…" with another style and/or language; ORACUL starts a new run that reuses the original run's Evidence Pack and validated scenario and performs only the story-writing step, so the same future is told differently without a new search.
- Acceptance:
  - Given a completed Official / Briefing run, when I choose "Rewrite as…" Narrative — inspired by Franz Kafka, then a new run starts, completes with a new story, and the stub records exactly one new request, of purpose STORY_WRITING, with no search, query-generation, scenario-generation or critic request
  - Given the original and the rewrite, when their STORY_WRITING requests are compared, then the structured-scenario data block is byte-identical and only the WRITING STYLE section differs
  - Given a rewrite of a rewrite, when it runs, then it uses the original run's scenario
  - Given the "Rewrite as…" menu of a result, when it opens, then the result's own style and language combination is disabled
  - Given a run that is not COMPLETED, when the rewrite API is called for it, then it answers 409 "Only a completed future can be rewritten" and no run starts
  - Given the rewrite's story call fails, when the run ends, then it is FAILED with the existing failure message and the original result is unchanged
  - Given another run of the session is active, when I choose "Rewrite as…", then the existing one-active-run rule applies

### FR-68 — Style and language shown with the result
- UI: yes
- Corrects: FR-25 (metadata), FR-33 (Recent futures)
- Description: The scenario metadata and Recent futures show each run's writing style and language, a rewrite also names the run it was rewritten from, and ALTERNATIVE FUTURE and the quick-regeneration buttons keep the run's style and language.
- Acceptance:
  - Given a Kafka run in Ukrainian, when the result is shown, then the metadata shows "Style: Narrative — inspired by Franz Kafka" and "Language: Ukrainian"
  - Given a rewrite, when its result is shown, then the metadata shows "Rewritten from: <original headline>"
  - Given a run created before this phase, when it is reopened, then the metadata shows "Style: Official / Briefing (legacy)" and "Language: English"
  - Given Recent futures, when it lists runs, then each entry shows its style label and language
  - Given a Poe run, when I click ALTERNATIVE FUTURE or DARKER, then the new run has writing style Narrative / Edgar Allan Poe and the same language

## Non-functional

### NFR-12 — Real-service check gates GREEN
- Description: Stub tests are not enough: after the release the user runs ORACUL in real mode on the real ChatGPT account and real Google News. The phase is GREEN only after this check; it also completes the phase-03 check (NFR-11) that was not done.
- Acceptance:
  - Writing style: one scenario generated as Official / Briefing in English, then rewritten as Narrative — inspired by Franz Kafka and as Narrative — inspired by Edgar Allan Poe in Ukrainian; the three stories describe the same future event and date with a clearly different tone, the rewrites make no new search, and the Ukrainian dateline uses a Ukrainian month name
  - Carried NFR-11: with New pandemic at 1 and then at 10 (Darkness 5) plus a custom wildcard "AI takeover" 5, every enabled wildcard has at least 1 source with retrieved article text, the level-1 and level-10 queries differ visibly in intensity, the story completes, and the backend log shows no GDELT request
  - Evidence in `05_release/real-check.md`

## Out of scope
- User-defined custom styles or free-text style prompts
- Story languages other than English and Ukrainian; translating the UI or the AI labels
- Length or tone sliders
- Styling of the WHY, SOURCES and WHY THESE NEWS? panels, the scenario metadata or the causal chain
- Illustrations and audio
- Any change to search, Evidence Pack, scenario generation, critic or Evidence Guard
