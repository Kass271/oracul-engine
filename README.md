# ORACUL

ORACUL turns today's news into a story from the future. You choose how realistic, dark and optimistic the future
should be, ORACUL reads current news, builds an evidence pack and writes one possible future grounded in it. It uses
your own ChatGPT plan for the writing; no API key is needed and none is ever asked for.

## Prerequisites

- A personal ChatGPT Plus or Pro account (ORACUL signs in with it and uses your plan).
- Docker Desktop with Compose v2 (the `docker compose` command).
- Free ports 4200 (the app) and 8080 (the backend).
- A browser on the same computer that runs Docker (OpenAI only redirects back to the loopback address).
- Only for running the tests: JDK 17+ (Gradle provisions Java 25 itself) and Node.js 22+.

## Run for real

From this folder:

```bash
docker compose up -d --build
```

Then open http://localhost:4200. The first build takes a few minutes. This starts the database, the backend and the
frontend against the real ChatGPT and news services, with its own database volume.

## Sign in step by step

1. Click "Continue with ChatGPT".
2. Sign in on OpenAI's page. Logging in with Google is fine.
3. The first time, OpenAI shows "Connect your ORACUL to ChatGPT". This registers this ORACUL installation as an app that
   is allowed to use your ChatGPT plan. You may edit the name. Confirm it.
4. You return to ORACUL and the header says "ChatGPT connected".
5. Choose your settings and click GENERATE THE FUTURE. While a generation runs the button reads STOP; click it to end a
   slow generation and start a new one.

Later sign-ins skip the connect screen. Tokens live only in memory, so after a restart of the backend you have to
connect again. The callback address is http://127.0.0.1:4200/callback: OpenAI only accepts the loopback address, so do not
change it. To remove the connection completely, disconnect the app in ChatGPT Settings.

## Run the tests

Backend:

```bash
(cd backend && ./gradlew test)
```

Frontend:

```bash
(cd frontend && npm ci && npm run test:ci)
```

End-to-end tests run against a separate stack with a stub for OpenAI and the news services. Start it, run the tests, stop it:

```bash
docker compose -f docker-compose.yml -f docker-compose.e2e.yml up -d --build
(cd e2e && npm ci && npx playwright install chromium && npx playwright test)
docker compose -f docker-compose.yml -f docker-compose.e2e.yml down
```

The E2E stack uses its own database volume (`db-e2e-data`), so your real futures and registration are never touched.
Running the E2E tests against the plain stack from "Run for real" fails at the first call to the stub (connection refused).

## Troubleshooting

### invalid_authorize_request on OpenAI's page

Cause: OpenAI rejected the sign-in request, usually because the app was changed or the registration is stale.
Fix: use Reset ChatGPT connection in the header menu, then click "Continue with ChatGPT" again.

### Your ChatGPT plan is not eligible for ORACUL

Cause: the signed-in account is not a personal ChatGPT Plus or Pro plan.
Fix: sign in with an eligible personal account; business and free plans cannot be used.

### ChatGPT usage limit reached

Cause: your ChatGPT plan has used up its allowance for now.
Fix: wait until the limit resets, then click GENERATE THE FUTURE again.

### Port 4200 or 8080 is already in use

Cause: another program already listens on port 4200 or 8080, so Docker cannot start the frontend or the backend.
Fix: close the other program (or run `docker compose down` if an older ORACUL is running) and start again.

### Docker disk is full

Cause: Docker has no free space left for images or the database.
Fix: free space in Docker Desktop (Settings, Resources) or run `docker system prune`, then start again.

### ChatGPT registration is no longer valid

Cause: OpenAI no longer knows the stored registration of this ORACUL installation.
Fix: choose Reset ChatGPT connection in the header menu (it is allowed when no generation is running), then connect again.

### Resetting the database

Cause: stored futures or a stale registration keep causing trouble.
Fix: run `docker compose down -v`; this deletes all stored futures and the registration.

## Stop and reset

- `docker compose down` stops ORACUL and keeps your futures and registration.
- `docker compose down -v` stops ORACUL and deletes the database volume: all futures and the registration are gone.
- Reset ChatGPT connection in the UI forgets only the registration and the tokens; your futures stay.
