# How to run — ORACUL (oracul-engine), phase-03_wildcard-search

## Start (stub mode, no real accounts)
The stack is started through the factory's `stack.mjs` (see the app README, E2E section), which runs docker compose with `docker-compose.yml` with `docker-compose.e2e.yml`.

- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api · health: http://localhost:8080/actuator/health
- E2E stub server (OAuth, Responses API, Google News RSS / article / decode / publisher pages): http://localhost:4010

The E2E stack uses its own database volume (`db-e2e-data`), so real data is untouched. Real mode (real ChatGPT account and real Google News) uses `docker-compose.yml` alone (see README).

## Stop
Stop the stack the same way it was started (`stack.mjs`); the E2E database volume `db-e2e-data` can be removed to wipe data.

## Test users / seed data
- No seed data and no user accounts; the session is browser-based.
- "Continue with ChatGPT" authenticates against the stub (any sign-in completes); database is postgres `app` / `app` / `app`.
- Google News stub modes (`POST http://localhost:4010/__control/google`, body `{"mode": ...}`): `ok`, `empty`, `empty-for` (empty for one wildcard, set `term`), `down`, `malformed`, `rate-limited-once`, `slow` (`ms`), `decode-fail`, `decode-google-host`, `publisher-fail`, `publisher-timeout`.
- The stub records requests (rss, google-page, decode, article, responses); no GDELT request may ever appear.

## Automated tests
- Backend: `cd backend && ./gradlew test`; frontend: `cd frontend && npm test`; E2E: only via the workflow's E2E step / `stack.mjs`.

## Real-service check (NFR-11)
Not part of this pack: run in real mode with New pandemic 1, then 10 (Darkness 5), plus AI takeover 5, and record it in `05_release/real-check.md`; the phase is GREEN only after that.
