# Clarification log — phase-03_wildcard-search

Each round: the questions asked in one batch and the user's answers, verbatim where possible.
Defaults the user accepted are marked "(default accepted)".

Context (real calls, 2026-10-06, before round 1): Google News RSS answered 37 items for a bare query; its article
links redirect only to a Google News page, but Google's undocumented batchexecute decode (Fbv4je, using the page's
data-n-a-id / data-n-a-ts / data-n-a-sg) returned the real publisher URL; Bing News RSS gives direct publisher links
but only 0–7 items per query; a publisher page answered 200 with readable `<p>` text.

## Round 1 — 2026-10-06

| # | Question | Answer |
|---|---|---|
| 1 | Search provider and article content: Google News RSS only (GDELT removed), one bare query per request ≥1 s apart; publisher URL via Google's decode call; on decode/fetch failure the source keeps RSS title + snippet marked "content not retrieved"; no Bing | Google News RSS + decode, no second provider (default accepted) |
| 2 | Volume: every enabled wildcard (catalogue or custom) runs its own pipeline; 3 queries per wildcard (2 when more than 8 are on); up to 4 most relevant sources per wildcard get article content; total cap 30 sources (FR-46); up to 3 extracted fragments per source, ≤1,200 characters, keyword-matched, no extra ChatGPT call | as proposed (default accepted) |
| 3 | Parameters in queries: tool-less ChatGPT call per wildcard from wildcard + level + Darkness + Optimism + Realism + horizon; template fallback with bands 1–3 current reality, 4–7 serious risks, 8–10 extreme/catastrophic; Darkness ≥7 negative terms, ≤3 neutral/positive; old 40/30/20/10 buckets dropped; no wildcards → one "General" pipeline (major current events) | as proposed (default accepted) |
| 4 | Evidence context and prompt: Evidence Pack grouped by wildcard (Evidence ID, title, publisher, date, URL, extracted text); counter-signal quota dropped; Closed Evidence Mode replaced by the §7 "starting conditions" principle; Evidence IDs for present-day facts, no invented sources, injection protection, Evidence Guard and Critic stay | as proposed (default accepted) |
| 5 | Failures and UI: a wildcard whose search fails or finds nothing → run continues, context shows "no current sources found", FR-47 note names it, no hidden fallback; SOURCES and WHY THESE NEWS? grouped by wildcard with queries, sources and excerpts; progress view unchanged | as proposed (default accepted) |

## Round 2 — 2026-10-06

| # | Question | Answer |
|---|---|---|
| 1 | Time budget: run ≤180 s; search stage ≤90 s; Google requests serial ≥1 s apart, 10 s timeout; decode/fetch 4 in parallel, 8 s timeout; budget-cut queries FAILED | "maybe run in parallel and use virtual threads, then when all google queries was finsihed join and continue as in req." → all pipelines and Google queries run in parallel on virtual threads (max 8 open at once, 429 → one retry after 2 s), join after the last query, then selection / decode / fetch (also parallel, max 8, 8 s timeout); search stage ≤90 s stays. Verified with real calls 2026-10-06: bursts of 12 and 24 parallel requests all answered 200 in 0.5–1.1 s |
| 2 | GDELT removal and test stub: delete client, config, env vars, stub endpoints, tests, README parts; FR-44 and FR-48's fallback superseded; stub answers like real Google (bare queries only, Google-page link, decode endpoint, publisher pages) with failure modes | as proposed (default accepted) |
| 3 | Same article found by two wildcards: listed under each, stored and counted once toward the 30-source cap | as proposed (default accepted) |
| 4 | Safe article fetching: refuse loopback/private/link-local targets, ≤5 redirects, HTML/text only, ≤2 MB, extracted text only as delimited untrusted data | in scope as proposed (default accepted) |
| 5 | Real check after release (every wildcard has a source with article text, level-1 vs level-10 queries differ, no GDELT request) and out of scope (second provider, paid keys, headless browser, paywall bypass, cross-run cache, count controls, request-log redaction low finding) | as proposed (default accepted) |
