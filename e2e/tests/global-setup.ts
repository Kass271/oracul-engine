import { execSync } from 'node:child_process';

/**
 * The text.format fallback flag is remembered for the backend's lifetime (spec), so every E2E invocation restarts
 * the backend to start from structured output. Skip with E2E_SKIP_BACKEND_RESTART=1 (e.g. no docker available).
 * `docker compose` honours COMPOSE_PROJECT_NAME / COMPOSE_FILE from the environment; it runs from the app dir.
 */
export default async function globalSetup(): Promise<void> {
  if (process.env.E2E_SKIP_BACKEND_RESTART) return;
  const appDir = new URL('../../', import.meta.url).pathname;
  try {
    execSync('docker compose restart backend', { cwd: appDir, stdio: 'inherit', timeout: 120_000 });
  } catch (e) {
    console.warn(`backend restart skipped: ${(e as Error).message}`);
    return;
  }
  const health = process.env.BACKEND_HEALTH_URL ?? 'http://localhost:8080/actuator/health';
  const deadline = Date.now() + 120_000;
  while (Date.now() < deadline) {
    try {
      const r = await fetch(health);
      if (r.ok) return;
    } catch {
      // not up yet
    }
    await new Promise((res) => setTimeout(res, 1000));
  }
  throw new Error(`backend did not become healthy at ${health}`);
}
