# Real check 2 (NFR-8): real mode, real ChatGPT, real news

Date: 2026-10-06 (user's manual runs, 12:04–12:10 UTC). The stack was started with `stack.mjs up --mode run`
(`docker-compose.yml` only, no stub). Code: f33fcb3 (all 3 slices DONE).

## Result: ❌ PARTIAL. Generation is real; news evidence is NOT
| check | result | evidence |
|---|---|---|
| Sign in with ChatGPT (SIWC) | ✔ | the user signed in through the UI; the run used model `gpt-6-astra` from /v1/models |
| Real generation (not stub text) | ✔ | run 15213ed6 COMPLETED, story "The Night the Shelters Refused to Open" (real model text, not "Stub headline from the future") |
| STOP (FR-45) | ✔ | run 0a6370d3 STOPPED at stage WRITING_STORY after 1 m 42 s |
| Lack of evidence never stops a run (FR-47) | ✔ | run 15213ed6 COMPLETED with the NO_EVIDENCE note |
| Real news search (FR-48) | ✘ | run 15213ed6: 20 searches, 0 articles retrieved, every query EMPTY; run 0a6370d3: 1 article |

## Root cause of the missing articles (verified with real calls, 2026-10-06)
FR-48 sends Google News one OR-group per request, e.g.
`(energy crisis supply shortage warnings OR geopolitical fragmentation trade bloc tensions OR AI loss of control safety warnings OR autonomous robots control failures safety risks) when:90d`.
Google's OR binds only the two adjacent words and ANDs the rest, so this returned **1 item**. Nested groups and quoted
phrases returned **0**. The same queries sent alone returned **75, 30, 70 and 44** items. 12 single requests spaced
1 s apart all answered 200. The E2E stub accepted any `q`, so the E2E suite (171/171) did not catch this.

Fix (next phase): one Google request per query, ≥1 s apart, with GDELT as the per-query fallback, and a stub that
enforces Google's AND/OR semantics. See `docs/for-factory-improvements/issues-phase-02-session-3.md` item 14 (in the
oracul root).
