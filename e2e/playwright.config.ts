import { defineConfig, devices } from '@playwright/test';

// Runs against the Docker stack (docker compose up). Reports feed gen-traceability and check-artifacts.
export default defineConfig({
  testDir: './tests',
  globalSetup: './tests/global-setup.ts',
  timeout: 30_000,
  retries: 0,
  // all specs share one global stub (modes, recorded requests): no parallelism
  workers: 1,
  fullyParallel: false,
  reporter: [
    ['list'],
    ['json', { outputFile: 'report/results.json' }],
    ['junit', { outputFile: 'report/junit.xml' }],
  ],
  use: {
    baseURL: process.env.BASE_URL ?? 'http://localhost:4200',
    trace: 'retain-on-failure',
  },
  projects: [
    { name: 'chromium', testIgnore: /plan-usage-fallback\.spec\.ts/, use: { ...devices['Desktop Chrome'] } },
    // sets the backend-lifetime text.format fallback flag, so it must run after everything else
    {
      name: 'fallback-last',
      testMatch: /plan-usage-fallback\.spec\.ts/,
      dependencies: ['chromium'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
