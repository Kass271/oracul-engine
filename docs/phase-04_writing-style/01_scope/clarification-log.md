# Clarification log — phase-04_writing-style

Each round: the questions asked in one batch and the user's answers, verbatim where possible.
Defaults the user accepted are marked "(default accepted)".

Context (code read 2026-10-07): the story is produced by one tool-less STORY_WRITING call (StoryWritingPrompt =
ClosedEvidenceMode + RULES "Write a short news story from the future…") from the validated structured scenario;
search, scenario generation, critic and Evidence Guard run before it.

## Round 1 — 2026-10-07

| # | Question | Answer |
|---|---|---|
| 1 | Where the style applies: only the final STORY_WRITING call; search, scenario, critic, guard untouched; style in its own WRITING STYLE prompt section with "the scenario's events, dates and causes take priority over style"; Official / Briefing is the default and the story prompt is rewritten to the analytical-report tone of §2 | as proposed (default accepted) |
| 2 | Re-telling the same future: "Rewrite as…" on a completed result starts a new run that reuses the same Evidence Pack and validated scenario and only runs story writing (no search, scenario or critic); it is its own Recent-futures entry linked to the original, like ALTERNATIVE FUTURE | as proposed (default accepted) |
| 3 | Catalogue: Official / Briefing; Narrative with Neutral Narrative, Poe, Kafka, Wells, Verne, Orwell, Clarke, Harrison; profiles are entries in a backend style file (id, label, characteristics, prompt text) so new profiles need no pipeline change; UI loads GET /api/writing-styles; unknown id → 400 "Unknown writing style" | as proposed (default accepted) |
| 4 | Author styles and safety: labelled "Narrative — inspired by <author>"; evoke tone/themes only, never copy or quote text, signature phrases or characters; AI labels on every style; same story validation (150–900 words, headline, date in horizon); narrative may add invented characters/scenes but no new events, dates or outcomes | as proposed (default accepted) |
| 5 | UI and memory: Writing Style group in the Output section; Narrative shows a Narrative Style list (default Neutral Narrative); saved in the session configuration and kept by quick regeneration; shown in metadata and Recent futures; older runs show "Official / Briefing (legacy)"; mobile uses a dropdown | as proposed (default accepted) |

## Round 2 — 2026-10-07

| # | Question | Answer |
|---|---|---|
| 1 | Story format: plain text, 150–900 words for every style; Official / Briefing structured by short capitalised lead-ins (SITUATION. CONSEQUENCES. OUTLOOK.), no Markdown; Narrative free prose | as proposed (default accepted) |
| 2 | Rewrite rules: only COMPLETED runs; rewrite of a rewrite uses the original scenario; current style disabled; failure → new run FAILED with existing messages, original untouched; one active run per session; ALTERNATIVE FUTURE and quick regeneration keep the style; metadata shows "Rewritten from <headline>" | as proposed (default accepted) |
| 3 | Language: English only for every style | "add options to output final story in english or ukrainian(like select list)" → a Story language select (English default, Ukrainian). Defaults taken: headline and body in the chosen language; dateline month in that language; UI and the two AI labels stay English; "Rewrite as…" can also change the language on the same scenario |
| 4 | Testing and real check: stub returns per-style canned stories; tests assert the WRITING STYLE section, byte-identical scenario block between original and rewrite, no search/scenario/critic call in a rewrite; real check: one scenario as Official, rewritten as Kafka and Poe, same events and dates, different tone; plus the carried-over phase-03 NFR-11 check | as proposed (default accepted) |
| 5 | Out of scope: user-defined styles or free-text prompts, other languages, length/tone sliders, styling of WHY/SOURCES/WHY THESE NEWS?, illustrations, audio, styling scenario metadata or causal chain | as proposed (default accepted); "other languages" now means languages other than English and Ukrainian |
