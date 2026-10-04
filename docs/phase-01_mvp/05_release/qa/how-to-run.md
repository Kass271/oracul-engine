# How to run — ORACUL (oracul-engine)

## Start
```bash
cd apps/oracul-engine
docker compose up -d --build
```
- Frontend: http://localhost:4200
- Backend API: http://localhost:8080/api · health: http://localhost:8080/actuator/health
- E2E stub server (OAuth, Responses API, GDELT news): http://localhost:4010

`docker-compose.override.yml` is merged automatically and points the backend at the stub, so no real ChatGPT account or network is needed. For real use: `docker compose -f docker-compose.yml up -d`.

## Stop
```bash
docker compose down        # keep data
docker compose down -v     # wipe the database
```

## Test users / seed data
- No seed data and no user accounts; the session is browser-based.
- "Continue with ChatGPT" authenticates against the stub (any sign-in completes there); database is postgres `app` / `app` / `app`.
- Stage delay is shortened to 2 s via the override for easier observation.

## Automated tests
- Backend: `cd backend && ./gradlew test`; frontend: `cd frontend && npm test`; E2E: `cd e2e && npx playwright test`.
