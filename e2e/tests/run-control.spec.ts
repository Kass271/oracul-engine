import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const NO_RUN = '00000000-0000-0000-0000-000000000000';
const STOPPED_HINT = 'Change the settings or click GENERATE THE FUTURE to start a new one.';

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  expect((await request.post(`${STUB}/__control/google`, { data: { mode: 'ok' } })).status()).toBe(204);
  for (const name of ['events', 'scenario', 'story']) {
    expect((await request.post(`${STUB}/__control/${name}`, { data: { mode: 'ok' } })).status()).toBe(204);
  }
});

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

async function configureAcceptance(page: Page): Promise<void> {
  await page.getByTestId('slider-darkness-input').fill('9');
  await page.getByTestId('slider-optimism-input').fill('2');
  await page.getByTestId('horizon-option-5y').click();
  for (const [category, id, intensity] of [
    ['biology', 'biology-new-pandemic', '8'],
    ['robotics', 'robotics-humanoid-boom', '6'],
  ]) {
    await page.getByTestId(`wildcard-category-header-${category}`).click();
    await page.getByTestId(`wildcard-toggle-${id}`).getByRole('switch').click();
    await page.getByTestId(`wildcard-intensity-${id}-input`).fill(intensity);
  }
}

/** Connects, configures acceptance A, generates and waits for the progress view; returns the run id. */
async function startAcceptanceRun(page: Page): Promise<string> {
  await connect(page);
  await configureAcceptance(page);
  await page.getByTestId('generate-button').click();
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  await expect(page.getByTestId('progress-view')).toBeVisible();
  return page.url().split('/').pop()!;
}

async function recordedCount(page: Page, kind: 'responses' | 'rss' | 'google-page' | 'decode' | 'article' | 'all'): Promise<number> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return (Array.isArray(body) ? body : body.requests).length;
}

async function traffic(page: Page): Promise<Record<string, number>> {
  return {
    responses: await recordedCount(page, 'responses'),
    rss: await recordedCount(page, 'rss'),
    // FR-54 / FR-61: the retrieval requests (Google page, decode, publisher page) stop with the run as well
    googlePage: await recordedCount(page, 'google-page'),
    decode: await recordedCount(page, 'decode'),
    article: await recordedCount(page, 'article'),
    all: await recordedCount(page, 'all'),
  };
}

async function getRun(page: Page, id: string): Promise<any> {
  const res = await page.request.get(`/api/runs/${id}`);
  expect(res.status()).toBe(200);
  return res.json();
}

// @trace FR-45, FR-61
test.describe('FR-45 Stop a generation and start a new one', () => {
  test('FR-45 STOP ends the generation, nothing more is sent, and GENERATE THE FUTURE starts a new run', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);

    // the progress view carries the STOP button (enabled), the stage walks on
    await expect(page.getByTestId('progress-stage')).toHaveText('Reading relevant sources…', { timeout: 30_000 });
    await expect(page.getByTestId('progress-view').getByTestId('generate-button')).toHaveText('STOP');
    await expect(page.getByTestId('generate-button')).toBeEnabled();

    const stopPosted = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith(`/api/runs/${id}/stop`));
    await page.getByTestId('generate-button').click();
    await stopPosted;

    await expect(page.getByTestId('stopped-message')).toHaveText('Generation stopped');
    await expect(page.getByTestId('stopped-hint')).toHaveText(STOPPED_HINT);
    await expect(page.getByTestId('progress-view')).toHaveCount(0);
    await expect(page.getByTestId('quick-actions')).toHaveCount(0);
    await expect(page.getByTestId('generate-button')).toHaveText('GENERATE THE FUTURE');
    await expect(page.getByTestId('generate-button')).toBeEnabled();
    await evidence(page, 'FR-45', 'stopped');

    const run = await getRun(page, id);
    expect(run.status).toBe('STOPPED');
    expect(run.failure ?? null).toBeNull();
    expect(run.headline ?? null).toBeNull();
    expect(run.completedAt).toBeTruthy();

    // 3 s later the stub has seen nothing new: no Responses, Google News or other request for the stopped run
    const before = await traffic(page);
    await page.waitForTimeout(3000);
    expect(await traffic(page)).toEqual(before);
    expect((await getRun(page, id)).status).toBe('STOPPED');
    await expect(page.getByTestId('stopped-message')).toBeVisible();

    // GENERATE THE FUTURE starts a new run with the panel configuration
    const started = page.waitForResponse((r) => r.request().method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.getByTestId('generate-button').click();
    expect((await started).status()).toBe(202);
    await expect.poll(() => page.url().split('/').pop(), { timeout: 15_000 }).not.toBe(id);
    const newId = page.url().split('/').pop()!;
    expect(newId).toMatch(/^[0-9a-f-]{36}$/);
    await expect(page.getByTestId('progress-view')).toBeVisible();
    await expect(page.getByTestId('generate-button')).toHaveText('STOP');
    await evidence(page, 'FR-45', 'new-run-after-stop');

    // leave nothing running
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('stopped-message')).toBeVisible();
    expect((await getRun(page, newId)).status).toBe('STOPPED');
  });

  test('FR-45 Recent futures lists the stopped run as Stopped and reopens the stopped view', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('progress-stage')).toHaveText('Searching current events…', { timeout: 30_000 });
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('stopped-message')).toBeVisible();

    await page.goto('/');
    await page.getByTestId('recent-futures-button').click();
    await expect(page.getByTestId('recent-futures-list')).toBeVisible();
    await expect(page.getByTestId(`recent-future-status-${id}`)).toHaveText('Stopped');
    await expect(page.getByTestId(`recent-future-headline-${id}`)).toHaveCount(0);
    await expect(page.getByTestId(`recent-future-time-${id}`)).toHaveText(/^\d{2}:\d{2}$/);
    await expect(page.getByTestId(`recent-future-settings-${id}`)).toHaveText('R8 D9 O2 · 5 years');
    await evidence(page, 'FR-45', 'recent-futures-stopped');

    await page.getByTestId(`recent-future-${id}`).click();
    await expect(page).toHaveURL(new RegExp(`/futures/${id}$`));
    await expect(page.getByTestId('stopped-message')).toHaveText('Generation stopped');
    // the panel is loaded with the run's configuration
    await expect(page.getByTestId('slider-darkness-input')).toHaveValue('9');
    await expect(page.getByTestId('slider-optimism-input')).toHaveValue('2');
  });

  test('FR-45 Reset ChatGPT connection right after a stop succeeds', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('progress-stage')).toHaveText('Searching current events…', { timeout: 30_000 });
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('stopped-message')).toBeVisible();
    expect((await getRun(page, id)).status).toBe('STOPPED');

    await page.getByTestId('chatgpt-menu').click();
    await page.getByTestId('chatgpt-reset').click();
    await expect(page.getByTestId('chatgpt-reset-dialog')).toBeVisible();
    await page.getByTestId('chatgpt-reset-confirm').click();
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection reset');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await evidence(page, 'FR-45', 'reset-after-stop');
  });

  test('FR-45 a failing stop request shows a snackbar and keeps an enabled STOP; polling goes on', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('progress-stage')).toHaveText('Searching current events…', { timeout: 30_000 });

    await page.route(`**/api/runs/${id}/stop`, (route) =>
      route.fulfill({ status: 500, contentType: 'application/json', body: JSON.stringify({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }) }),
    );
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('run-error-message')).toHaveText('Something went wrong — try again');
    await expect(page.getByTestId('generate-button')).toHaveText('STOP');
    await expect(page.getByTestId('generate-button')).toBeEnabled();
    await expect(page.getByTestId('progress-view')).toBeVisible();
    await evidence(page, 'FR-45', 'stop-failed');

    await page.unroute(`**/api/runs/${id}/stop`);
    await page.route(`**/api/runs/${id}/stop`, (route) =>
      route.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ code: 'RUN_NOT_FOUND', message: 'Future not found' }) }),
    );
    // A real user click: Playwright waits until the button is visible, enabled and not covered (e.g. by the snackbar).
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('run-error-message')).toHaveText('Future not found');
    await expect(page.getByTestId('generate-button')).toHaveText('STOP');
    await expect(page.getByTestId('generate-button')).toBeEnabled();
    // the run was never stopped by those answers
    expect((await getRun(page, id)).status).toMatch(/^(QUEUED|RUNNING)$/);

    await page.unroute(`**/api/runs/${id}/stop`);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('stopped-message')).toBeVisible();
    expect((await getRun(page, id)).status).toBe('STOPPED');
  });

  test('FR-45 API: unknown, malformed and foreign runs are 404 RUN_NOT_FOUND with exactly code and message', async ({ page, browser }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    for (const bad of [NO_RUN, 'abc']) {
      const res = await page.request.post(`/api/runs/${bad}/stop`);
      expect(res.status(), bad).toBe(404);
      expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
    }
    // another browser session cannot stop it
    const origin = new URL(page.url()).origin;
    const other = await browser.newContext({ baseURL: origin });
    try {
      const otherPage = await other.newPage();
      await otherPage.goto('/');
      const foreign = await otherPage.request.post(`${origin}/api/runs/${id}/stop`);
      expect(foreign.status()).toBe(404);
      expect(await foreign.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
    } finally {
      await other.close();
    }
    expect((await getRun(page, id)).status).toMatch(/^(QUEUED|RUNNING)$/);

    // the owner can: 200 with the STOPPED run; a second stop answers the same body
    const first = await page.request.post(`/api/runs/${id}/stop`);
    expect(first.status()).toBe(200);
    const body = await first.json();
    expect(body.status).toBe('STOPPED');
    expect(body.id).toBe(id);
    const second = await page.request.post(`/api/runs/${id}/stop`);
    expect(second.status()).toBe(200);
    expect(await second.json()).toEqual(body);
    await expect(page.getByTestId('stopped-message')).toBeVisible({ timeout: 10_000 });
  });

  test('FR-45 a stop that arrives after the run ended changes nothing', async ({ page }) => {
    test.setTimeout(150_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    const before = await getRun(page, id);
    expect(before.status).toBe('COMPLETED');
    const res = await page.request.post(`/api/runs/${id}/stop`);
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual(before);
    expect(await getRun(page, id)).toEqual(before);
    await expect(page.getByTestId('result-view')).toBeVisible();
    await expect(page.getByTestId('stopped-view')).toHaveCount(0);
  });
});
