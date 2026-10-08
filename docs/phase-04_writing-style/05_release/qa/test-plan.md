# Manual test plan — phase-04_writing-style

Start the app: see [how-to-run.md](how-to-run.md). Open http://localhost:4200 and sign in with ChatGPT (stub) unless a step says otherwise.

## FR-62 — Writing style catalogue
| # | Step | Expected |
|---|---|---|
| 1 | Open http://localhost:8080/api/writing-styles | Two modes and nine profiles with id, mode, label, characteristics; Official / Briefing first and marked default; Neutral Narrative marked as Narrative default |
| 2 | Read the labels of the author profiles | Each reads "Narrative — inspired by <author>", e.g. "Narrative — inspired by Franz Kafka" |
| 3 | Add a valid profile to the style file, restart the backend, reload the list | The new profile is listed and usable with no other code change |
| 4 | Error path: remove id, label or prompt text of one entry, restart the backend | Backend refuses to start; the message names the entry |

## FR-63 — Writing Style selector
| # | Step | Expected |
|---|---|---|
| 1 | Load the page in a fresh session, look at Output | Writing Style shows Official / Briefing selected; no Narrative Style list |
| 2 | Select Narrative | Narrative Style list with eight profiles appears, Neutral Narrative selected |
| 3 | Select Franz Kafka, click Generate the Future | Run starts; its configuration holds Narrative / Franz Kafka |
| 4 | Reload the page in the same session | Last style choice is still selected |
| 5 | Error path: POST /api/runs with an unknown style id, or a narrative profile with mode Official / Briefing | 400 "Unknown writing style"; no run is started |
| 6 | Narrow the window to phone width, select Narrative | Narrative Style is a dropdown instead of radio buttons |

## FR-64 — Story language select
| # | Step | Expected |
|---|---|---|
| 1 | Load the page in a fresh session | Story language select shows English |
| 2 | Select Ukrainian, generate | Run configuration holds language Ukrainian |
| 3 | Reload the page in the same session | Ukrainian is still selected |
| 4 | Error path: POST /api/runs with a language other than English or Ukrainian | 400 "Unknown story language"; no run is started |

## FR-65 — Style applied only when the story is written
| # | Step | Expected |
|---|---|---|
| 1 | Run twice with identical settings except the writing style; compare the stub-recorded requests | Search, query-generation, scenario-generation and critic requests are identical; only STORY_WRITING differs |
| 2 | Inspect a STORY_WRITING request | Contains WRITING STYLE section (mode, profile label, characteristics, language), the priority rule, the structured scenario as delimited data; no tools |
| 3 | Inspect it for Official / Briefing | Asks for factual, concise, neutral prose with lead-ins SITUATION, CONSEQUENCES, OUTLOOK; no literary description |
| 4 | Inspect it for an author profile | Forbids copying or quoting the author's text, signature phrases, characters; forbids new events, dates, outcomes |
| 5 | Open the result of a styled run | Story passes existing rules (plain text, 150-900 words, headline 1-160 characters, date in horizon); both AI labels shown |

## FR-66 — Story in the chosen language
| # | Step | Expected |
|---|---|---|
| 1 | Generate with Ukrainian; future date 12 September 2032 | Dateline reads "ORACUL FUTURE — 12 вересня 2032"; headline and body in Ukrainian |
| 2 | Generate with English, same date | Dateline reads "ORACUL FUTURE — September 12, 2032" |
| 3 | Look above the Ukrainian story | "AI-GENERATED FUTURE SCENARIO" and "POSSIBLE FUTURE — NOT CURRENT NEWS" are in English |
| 4 | Error path: have the stub return a Ukrainian body outside 150-900 words | Story is rejected by the same word-count rule as English |

## FR-67 — Rewrite the same future in another style or language
| # | Step | Expected |
|---|---|---|
| 1 | On a completed Official / Briefing result open "Rewrite as…", choose Narrative — inspired by Franz Kafka | New run starts and completes with a new story; stub records one new STORY_WRITING request and nothing else |
| 2 | Compare original and rewrite STORY_WRITING requests | Scenario data block byte-identical; only WRITING STYLE section differs |
| 3 | Rewrite a rewrite | Uses the original run's scenario |
| 4 | Open the "Rewrite as…" menu | The result's own style and language combination is disabled |
| 5 | Error path: call the rewrite API for a run that is not COMPLETED | 409 "Only a completed future can be rewritten"; no run starts |
| 6 | Error path: make the rewrite's story call fail | Run ends FAILED with the existing failure message; original result unchanged |
| 7 | Error path: start a rewrite while another run is active | One-active-run rule applies; rewrite refused |

## FR-68 — Style and language shown with the result
| # | Step | Expected |
|---|---|---|
| 1 | Open the result of a Kafka run in Ukrainian | Metadata shows "Style: Narrative — inspired by Franz Kafka" and "Language: Ukrainian" |
| 2 | Open a rewrite result | Metadata shows "Rewritten from: <original headline>" |
| 3 | Reopen a run created before this phase | Metadata shows "Style: Official / Briefing (legacy)" and "Language: English" |
| 4 | Look at Recent futures | Each entry shows its style label and language |
| 5 | On a Poe run in Ukrainian click ALTERNATIVE FUTURE, then DARKER | New runs have Narrative / Edgar Allan Poe and the same language |
