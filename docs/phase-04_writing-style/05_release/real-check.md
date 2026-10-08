# Real check (NFR-12, incl. carried phase-03 NFR-11) — real ChatGPT, real Google News

Date: 2026-10-08. Stack: `stack.mjs up --mode run` (`docker-compose.yml` only, no stub), code a8a7da7, run limits
`ORACUL_RUN_TIMEOUT=PT10M`, `ORACUL_OPENAI_STREAM_TIMEOUT=PT5M`. The user ran the checks in the UI and accepted the
result ("Good i accpeted"). Evidence below was read from the database and the backend log after the runs.

## Result: ✅ ACCEPTED by the user — with the gaps listed at the end

## History of this check
| time (UTC) | run | outcome | action |
|---|---|---|---|
| 07:35 | b5d2cd18 (Kafka, en) | FAILED RUN_TIMEOUT at EXPLORING_FUTURES after 180 s; research took ~20 s, 12/16 sources with article text | user: raise the limit → PT10M run / PT5M stream (docker-compose.yml) |
| ~08:00 | c7b30f82 (Kafka, en) | COMPLETED, but the story was an analytical report: paragraphs labelled FACT — / INFERENCE — / SPECULATION — / FUTURE EVENT — with [E001] citations | defect: STORY_WRITING used the Closed Evidence Mode reasoning instructions; fixed by the post-release correction of FR-65 (styled-story.md), commit a8a7da7 |
| 18:20 | a19ed1cc (Narrative — inspired by Edgar Allan Poe, Ukrainian) | COMPLETED in 364 s; headline «Пакт постійного навантаження: світло стає привілеєм, темрява — спадком» | accepted by the user |
| 18:34 | d374ea45 (Narrative — inspired by Harry Harrison, Ukrainian) | COMPLETED in 239 s; headline «Право вимкнути світ скасовано: уряди підписали Угоду про безперервність…» | accepted by the user |

## NFR-12 — writing style and language
| check | result | evidence |
|---|---|---|
| Narrative style in real mode, no analytical labels / citations | ✔ (user) | runs a19ed1cc (Poe) and d374ea45 (Harrison) accepted after the fix; the story validator rejects labelled or cited stories (StoryParser LABEL / citation rules) |
| Ukrainian story | ✔ | both accepted runs have language `uk` and Ukrainian headlines |
| Same future re-told by REWRITE AS… without a new search | not exercised | both accepted runs are STANDARD runs; no REWRITE run exists in the real database for this check |
| Official / Briefing vs. narrative on the same scenario | not exercised in real mode | covered by stub tests only (StyledStoryIT, story-rewrite.spec.ts) |

## Carried phase-03 NFR-11 — wildcard search
| check | result | evidence |
|---|---|---|
| every enabled wildcard has sources, most with retrieved article text | ✔ | a19ed1cc: 25 sources, 21 with article text; d374ea45: 16 sources, 10 with text; b5d2cd18: 16 sources, 12 with text. Failed fetches were publisher refusals (403/406) |
| wildcard level shapes query intensity | ✔ | a19ed1cc: level 1 "Democratic institutions strengthen" → "Democratic reform initiatives face political backlash", "Citizens assemblies struggle with public trust"; level 8–9 "Energy crisis" / "Unexplained global phenomenon" → "solar storm electricity grid collapse transformer shortages", "unexplained global seismic signals catastrophic geological instability" (same wildcard at 1 vs 10 not run) |
| no GDELT request | ✔ | `docker logs oracul-engine-backend-1 | grep -ci gdelt` → 0 |
| run completes in real mode | ✔ with the raised limit | 239–364 s per run, above the old 180 s (NFR-2), within the user's PT10M |

## Gaps (accepted by the user, open for a later phase)
- REWRITE AS… and the Official vs. narrative comparison on one scenario were not run against the real model.
- Real runs take 4–6 minutes; the original 180 s budget (NFR-2) is superseded for the running stack only.
- Low review finding R5: the scenario data passed to the story call is still structured as facts / inferences /
  speculations; if a future real story drifts back to report form, pass a prose summary instead.
