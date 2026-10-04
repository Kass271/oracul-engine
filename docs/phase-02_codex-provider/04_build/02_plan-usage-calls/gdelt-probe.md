# Real GDELT probe (FR-44) — 2026-10-04

Real calls to `https://api.gdeltproject.org/api/v2/doc/doc` from this Mac (`mode=artlist&format=json&maxrecords=10&timespan=7d`). These are evidence for the spec risk "GDELT OR syntax unproven".

| Query | Gap before the call | Result |
|---|---|---|
| `economy sourcelang:english` | — | 429, 10.4 s · then 200, 18.3 s |
| `("interest rates" OR "central bank" OR inflation) sourcelang:english` | 6 s, then 6 tries 20 s apart | 429 every time (≈10 s each) |
| plain / OR alternating | 30 s | plain 429 (11.1 s) · **OR 200, 14.4 s, 10 relevant articles** · plain 429 (9.7 s) · **OR 200, 13.0 s** |

Findings:
- The OR-group syntax with quoted phrases works. It returned 10 on-topic articles (rates, inflation, central banks).
- GDELT answers 429 "Please limit requests to one every 5 seconds" at random, about half of all calls, even 20–30 s apart. A 429 still takes about 10 s.
- A successful answer takes 13–18 s, so the 30 s per-request timeout is required.
- Therefore the 429 retry, keeping partial results, and the search budget are all essential. Expect some real runs to work with fewer sources.
