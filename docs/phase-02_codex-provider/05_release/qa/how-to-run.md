# How to run — ORACUL (oracul-engine), phase-02_codex-provider

The Docker stack is started and stopped only through the factory's `stack.mjs` (see README.md for the plain compose commands for a human user).

## Start (E2E stub mode, no real ChatGPT account or network needed)
- Run mode with the stub: opt-in via `docker-compose.e2e.yml` merged on top of `docker-compose.yml` (see README.md, "End-to-end tests").
- Frontend: http://localhost:4200 (sign-in redirect URI is `http://127.0.0.1:4200/callback`; open the app in a browser on the same computer)
- Backend API: http://localhost:8080/api · health: http://localhost:8080/actuator/health
- E2E stub (OAuth, JWKS, Responses API, Google News RSS, GDELT): http://localhost:4010

The E2E stack uses its own database volume `db-e2e-data`, so real futures and the real registration are never touched. The stub is opt-in: the plain stack never starts it.

## Start (real mode: real ChatGPT Plus/Pro account, real news)
Use `docker-compose.yml` alone (README.md, "Run for real").

Known defect in real mode (accepted by the user, rewritten in the next phase): Google News queries are sent as OR-groups and real runs find almost no news, so they end with the "no evidence" note. See [real-check.md](../real-check.md).

## Stop
- Keep data: `docker compose down` (add the same `-f` files that were used to start).
- Wipe the database (and the registration): `docker compose down -v`.

## Test users / seed data
- No seed data and no user accounts; the session is browser-based.
- In stub mode "Continue with ChatGPT" authenticates against the stub (any sign-in completes there). Stub behaviour is switched through its `__control/*` endpoints (used by `e2e/tests/*.spec.ts`).
- Database: postgres `app` / `app` / `app`.
- Stage delay is shortened to 2 s and request spacing to 0.2-0.5 s in stub mode.

## Automated tests
- Backend: `cd backend && ./gradlew test`
- Frontend: `cd frontend && npm test`
- E2E: only through the workflow's E2E step (`stack.mjs e2e`).
