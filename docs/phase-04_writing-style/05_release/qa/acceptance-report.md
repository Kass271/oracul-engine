# Acceptance report — phase-04_writing-style

Built from traceability.md, test reports and screenshots only. Every ✔ links its evidence.

Totals from traceability (all phases): 68 FR ✔, 0 ✘, 0 blocked. Playwright (e2e/report/results.json): 228 expected, 0 unexpected, 0 flaky, 0 skipped. Backend JUnit XML (backend/build/test-results/test): 5335 tests, 0 failures or errors, 0 skipped.

| FR | Title | Result | Evidence |
|---|---|---|---|
| FR-62 | Writing style catalogue | ✔ | [traceability](traceability.md) |
| FR-63 | Writing Style selector | ✔ | [FR-63-official-default.png](screenshots/FR-63-official-default.png) · [FR-63-narrative-profiles.png](screenshots/FR-63-narrative-profiles.png) · [FR-63-kafka-ukrainian-started.png](screenshots/FR-63-kafka-ukrainian-started.png) · [FR-63-narrative-dropdown-mobile.png](screenshots/FR-63-narrative-dropdown-mobile.png) · [traceability](traceability.md) |
| FR-64 | Story language select | ✔ | [FR-64-ukrainian-restored-after-reload.png](screenshots/FR-64-ukrainian-restored-after-reload.png) · [traceability](traceability.md) |
| FR-65 | Style applied only when the story is written | ✔ | [FR-65-poe-story.png](screenshots/FR-65-poe-story.png) · [traceability](traceability.md) |
| FR-66 | Story in the chosen language | ✔ | [FR-66-ukrainian-story.png](screenshots/FR-66-ukrainian-story.png) · [traceability](traceability.md) |
| FR-67 | Rewrite the same future in another style or language | ✔ | [FR-67-rewrite-menu.png](screenshots/FR-67-rewrite-menu.png) · [FR-67-rewrite-result.png](screenshots/FR-67-rewrite-result.png) · [FR-67-rewrite-of-rewrite.png](screenshots/FR-67-rewrite-of-rewrite.png) · [FR-67-rewrite-failed.png](screenshots/FR-67-rewrite-failed.png) · [FR-67-rewrite-refused-for-failed-run.png](screenshots/FR-67-rewrite-refused-for-failed-run.png) · [FR-67-rewrite-refused-while-active.png](screenshots/FR-67-rewrite-refused-while-active.png) · [traceability](traceability.md) |
| FR-68 | Style and language shown with the result | ✔ | [FR-68-metadata-official-english.png](screenshots/FR-68-metadata-official-english.png) · [FR-68-metadata-legacy.png](screenshots/FR-68-metadata-legacy.png) · [FR-68-metadata-rewritten-from.png](screenshots/FR-68-metadata-rewritten-from.png) · [FR-68-recent-futures-labels.png](screenshots/FR-68-recent-futures-labels.png) · [FR-68-alternative-keeps-poe-ukrainian.png](screenshots/FR-68-alternative-keeps-poe-ukrainian.png) · [FR-68-darker-keeps-poe-ukrainian.png](screenshots/FR-68-darker-keeps-poe-ukrainian.png) · [traceability](traceability.md) |

FR-62 has no UI; its evidence is the passing tests in its traceability row.

## Blocked / not delivered
- None. No BLOCKED slices and no failure-note.md in this phase.
- NFR-12 (real-service check on the real ChatGPT account and Google News) is a manual step after release and is not part of this pack.

## Open low-severity review findings
- R1 (FR-66, frontend/src/app/result/future-story.ts:15): a Ukrainian story is shown inside html lang="en" with no lang attribute of its own; screen readers use English pronunciation (WCAG 3.1.2).
- R2 (FR-67, backend/src/main/java/com/oracul/app/runs/RewriteRunLookupFilter.java:39): a RuntimeException in the run lookup filter bypasses ApiExceptionHandler, so Spring's /error body is returned instead of ApiError INTERNAL_ERROR.
- R3 (FR-67, frontend/src/app/runs/quick-actions.ts:30): language headings in the rewrite menu are plain divs, not grouped with the 9 items that follow (no accessible name).
