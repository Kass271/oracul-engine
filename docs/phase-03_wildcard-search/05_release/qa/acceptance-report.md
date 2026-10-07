# Acceptance report — phase-03_wildcard-search

Built from traceability.md, test reports and screenshots only. Every passed row links its evidence.

| FR | Title | Result | Evidence |
|---|---|---|---|
| FR-49 | GDELT removed | ✔ | [traceability](traceability.md) |
| FR-50 | Independent search pipeline per wildcard | ✔ | [traceability](traceability.md) |
| FR-51 | Wildcard level and scenario parameters shape the queries | ✔ | [traceability](traceability.md) |
| FR-52 | Parallel Google News search per query | ✔ | [traceability](traceability.md) |
| FR-53 | Source selection per wildcard | ✔ | [traceability](traceability.md) |
| FR-54 | Publisher article retrieval | ✔ | [traceability](traceability.md) |
| FR-55 | Relevant text extraction | ✔ | [traceability](traceability.md) |
| FR-56 | Safe article fetching | ✔ | [traceability](traceability.md) |
| FR-57 | Evidence Pack grouped by wildcard | ✔ | [traceability](traceability.md) |
| FR-58 | Sources as starting conditions in the forecasting prompt | ✔ | [traceability](traceability.md) |
| FR-59 | Wildcard search failures are explicit | ✔ | [FR-59-missing-wildcard-lower-realism.png](screenshots/FR-59-missing-wildcard-lower-realism.png) · [FR-59-missing-wildcard-note.png](screenshots/FR-59-missing-wildcard-note.png) · [FR-59-queries-failed.png](screenshots/FR-59-queries-failed.png) · [FR-59-sources-empty.png](screenshots/FR-59-sources-empty.png) · [traceability](traceability.md) |
| FR-60 | SOURCES and WHY THESE NEWS? grouped by wildcard | ✔ | [FR-60-highlighted-shared-source.png](screenshots/FR-60-highlighted-shared-source.png) · [FR-60-source-group-empty.png](screenshots/FR-60-source-group-empty.png) · [FR-60-sources-content-not-retrieved.png](screenshots/FR-60-sources-content-not-retrieved.png) · [FR-60-sources-grouped.png](screenshots/FR-60-sources-grouped.png) · [FR-60-why-these-news-grouped.png](screenshots/FR-60-why-these-news-grouped.png) · [traceability](traceability.md) |
| FR-61 | E2E stub answers like real Google | ✔ | [traceability](traceability.md) |

## Blocked / not delivered
- None. Traceability: FR-49..FR-61 all passed, 0 ✘, 0 blocked.
- NFR-11 (real-service check on real ChatGPT and Google News) is run by the user after release and recorded in `05_release/real-check.md`; the phase is GREEN only after it. Not covered by this pack.

## Open low-severity review findings
Open lows per slice (`04_build/<slice>/review-findings.json`): 01_gdelt-removal 6, 02_safe-fetching 9, 03_parallel-search 3, 04_wildcard-queries 3, 05_wildcard-selection 4, 06_wildcard-pack 8, 07_starting-conditions 2, 08_article-text 3, 09_wildcard-results 4. Not fixed; none blocks release.
Release review (`05_release/review-findings.json`):
- R1 (FR-59): with Realism 9-10 and zero or one wildcard every run ends with the INSUFFICIENT_EVIDENCE note and a LOWER REALISM suggestion regardless of news quality.
- R2 (FR-54): `ArticleMetadataFetcher` still has its own network path but is no longer injected anywhere (dead code).
- R3 (FR-60): query status chip is a standalone `mat-chip` outside a chip set (accessibility roles).
- R4 (NFR-10): 119 Spring contexts in the backend suite; E2E runs 212 tests serially (about 25 min).
