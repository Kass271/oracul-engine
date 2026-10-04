import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

/** Acceptance body A of generation-runs.md "Slice 04_run-start". */
const A = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [
    { wildcardId: 'biology-new-pandemic', intensity: 8 },
    { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
  ],
  customWildcards: [],
  output: { story: true, illustration: false },
};

const STAGES: [string, string][] = [
  ['UNDERSTANDING', 'Understanding your future…'],
  ['RESEARCH_STRATEGY', 'Building research strategy…'],
  ['SEARCHING', 'Searching current events…'],
  ['READING_SOURCES', 'Reading relevant sources…'],
  ['CONNECTING_SIGNALS', 'Connecting signals…'],
  ['RANKING', 'Ranking evidence…'],
  ['EXPLORING_FUTURES', 'Exploring possible futures…'],
  ['CHALLENGING_ASSUMPTIONS', 'Challenging assumptions…'],
  ['CONSTRUCTING_SCENARIO', 'Constructing scenario…'],
  ['WRITING_STORY', 'Writing from the future…'],
];

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
});

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

async function setAcceptanceConfiguration(page: Page): Promise<void> {
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

async function startAcceptanceRun(page: Page): Promise<string> {
  await connect(page);
  await setAcceptanceConfiguration(page);
  await page.getByTestId('generate-button').click();
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  return page.url().split('/').pop()!;
}

// @trace FR-10, FR-45
test.describe('FR-10 Generate the Future', () => {
  test('connected user starts a run with exactly the panel configuration', async ({ page }) => {
    await connect(page);
    await setAcceptanceConfiguration(page);
    await expect(page.getByTestId('generate-hint')).toHaveCount(0);
    await expect(page.getByTestId('generate-button')).toBeEnabled();

    const requestPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.getByTestId('generate-button').click();
    const req = await requestPromise;
    expect(req.postDataJSON()).toEqual(A);

    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('progress-view')).toBeVisible();
    await evidence(page, 'FR-10', 'run-started');

    const id = page.url().split('/').pop()!;
    const run = await page.request.get(`/api/runs/${id}`);
    expect(run.status()).toBe(200);
    expect((await run.json()).configuration).toEqual(A);

    const second = await page.request.post('/api/runs', { data: A });
    expect(second.status()).toBe(409);
    expect(await second.json()).toEqual({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' });
  });

  test('not connected: the generate button is disabled with the hint', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('generate-button')).toBeDisabled();
    await expect(page.getByTestId('generate-hint')).toHaveText('Connect ChatGPT to generate');
    await evidence(page, 'FR-10', 'not-connected-disabled');
  });

  test('the generate button turns into an enabled STOP while a run is active (run-control.md FR-45)', async ({ page }) => {
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').pop()!;
    await expect(page.getByTestId('progress-view')).toBeVisible();
    // FR-45: the progress view has the button too; it reads STOP and is enabled
    await expect(page.getByTestId('progress-view').getByTestId('generate-button')).toHaveText('STOP');
    await expect(page.getByTestId('generate-button')).toBeEnabled();

    // A second start request from the same browser session is rejected while the run is active.
    const r = await page.request.post('/api/runs', {
      data: { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], customWildcards: [], output: { story: true, illustration: false } },
    });
    expect(r.status()).toBe(409);
    expect(await r.json()).toEqual({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' });

    // Client-side (SPA) navigation back to the welcome view, no reload: the button still reads STOP and is enabled.
    await page.evaluate(() => {
      history.pushState({}, '', '/');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    await expect(page.getByTestId('welcome-view')).toBeVisible();
    await expect(page.getByTestId('generate-button')).toHaveText('STOP');
    await expect(page.getByTestId('generate-button')).toBeEnabled();

    // Reloading /futures/<id> resumes the progress view (generation-runs.md, run view).
    await page.goto(`/futures/${id}`);
    await expect(page.getByTestId('progress-view')).toBeVisible();
  });

  test('API error paths: invalid body and unconnected session', async ({ request }) => {
    const base = { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], customWildcards: [], output: { story: true, illustration: false } };
    const invalid = await request.post('/api/runs', { data: { ...base, darkness: 11 } });
    expect(invalid.status()).toBe(400);
    expect(await invalid.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'darkness must be between 1 and 10' });
    const notConnected = await request.post('/api/runs', { data: base });
    expect(notConnected.status()).toBe(401);
    expect(await notConnected.json()).toEqual({ code: 'CHATGPT_NOT_CONNECTED', message: 'Connect ChatGPT to generate' });
  });
});

// @trace FR-11
test.describe('FR-11 Research Profile', () => {
  test('the running acceptance run exposes its Research Profile', async ({ page }) => {
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('progress-step-RESEARCH_STRATEGY')).toHaveAttribute('data-state', 'current', { timeout: 10_000 });
    const res = await page.request.get(`/api/runs/${id}/research`);
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.runId).toBe(id);
    expect(body.profile).toEqual({
      darkness: 0.9,
      optimism: 0.2,
      realism: 0.8,
      horizon: '5y',
      topics: [
        { key: 'biology-new-pandemic', label: 'New pandemic', category: 'biology', weight: 0.8, custom: false },
        { key: 'robotics-humanoid-boom', label: 'Humanoid robot boom', category: 'robotics', weight: 0.6, custom: false },
      ],
    });
  });

  test('research of an unknown run is 404', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/research');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});

// @trace FR-24
test.describe('FR-24 Generation progress', () => {
  test('progress walks through every stage label and ends with the result view', async ({ page }) => {
    test.setTimeout(60_000);
    await startAcceptanceRun(page);
    for (const [stage, label] of STAGES.slice(1)) {
      await expect(page.getByTestId('progress-stage')).toHaveText(label, { timeout: 5000 });
      await expect(page.getByTestId(`progress-step-${stage}`)).toHaveAttribute('data-state', 'current');
      // @trace FR-24 no technical details in the progress view
      const progressView = page.getByTestId('progress-view');
      if (await progressView.isVisible()) {
        expect(await progressView.innerText()).not.toMatch(/https?:\/\/|[{}]|Exception/);
      }
      if (stage === 'SEARCHING') await evidence(page, 'FR-24', 'progress-searching');
    }
    // slice 09: after "Writing from the future…" the center shows the result view
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 15_000 });
    await expect(page.getByTestId('progress-view')).toHaveCount(0);
    await evidence(page, 'FR-24', 'progress-finished');

    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible();
    await expect(page.getByTestId('progress-view')).toHaveCount(0);
  });

  test('an unknown run shows "Future not found" and Try again returns to the welcome view', async ({ page }) => {
    await connect(page);
    await page.goto('/futures/00000000-0000-0000-0000-000000000000');
    await expect(page.getByTestId('failure-message')).toHaveText('Future not found');
    await evidence(page, 'FR-24', 'run-not-found');
    await page.getByTestId('try-again').click();
    await expect(page.getByTestId('welcome-view')).toBeVisible();
  });

  test('API: malformed run id is 404 RUN_NOT_FOUND', async ({ request }) => {
    const res = await request.get('/api/runs/abc');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});
