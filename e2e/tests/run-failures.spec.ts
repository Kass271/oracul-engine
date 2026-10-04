import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const TIMEOUT_RUN_ID = '11111111-1111-1111-1111-111111111111';
const RATE_LIMIT_MESSAGE = 'ChatGPT usage limit reached — try again later';

// acceptance configuration A (as filled through the panel by configureAcceptance)
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
// slice 04 base body B
const B = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};
const ZERO_COUNTS = {
  searches: 0,
  articlesRetrieved: 0,
  articlesConsidered: 0,
  uniqueEvents: 0,
  eventsSelected: 0,
  counterSignals: 0,
  sourcesUsed: 0,
};

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await page.request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode: 'ok' } })).status()).toBe(204);
}

async function setStoryMode(page: Page, mode: string): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode } })).status()).toBe(204);
}

test.beforeEach(async ({ page }) => {
  await resetStub(page);
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

function runId(page: Page): string {
  return page.url().split('/').pop()!;
}

// @trace FR-32, FR-39
test.describe('FR-32 Run failure handling', () => {
  test('FR-32 a ChatGPT 429 ends the run with the friendly message, the panel stays usable and Try again re-submits', async ({ page }) => {
    test.setTimeout(240_000);
    await setStoryMode(page, 'rate-limited');
    await connect(page);
    await configureAcceptance(page);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const failedId = runId(page);

    await expect(page.getByTestId('failure-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('failure-message')).toHaveText(RATE_LIMIT_MESSAGE);
    // @trace FR-39  the 429 also offers the ChatGPT usage settings
    const usage = page.getByTestId('failure-usage-link');
    await expect(usage).toHaveText('Open ChatGPT Settings → Usage');
    await expect(usage).toHaveAttribute('href', 'https://chatgpt.com/#settings/Usage');
    await expect(usage).toHaveAttribute('target', '_blank');
    await expect(usage).toHaveAttribute('rel', 'noopener noreferrer');
    await expect(page.getByTestId('progress-view')).toHaveCount(0);
    await expect(page.getByTestId('result-view')).toHaveCount(0);
    const inner = await page.getByTestId('failure-view').innerText();
    expect(inner).not.toMatch(/https?:\/\/|[{}]|Exception|Error:|rate_limited|429|ORC-/);
    await evidence(page, 'FR-32', 'failure-view');
    const run = await (await page.request.get(`/api/runs/${failedId}`)).json();
    expect(run.status).toBe('FAILED');
    expect(run.failure.code).toBe('CHATGPT_RATE_LIMITED');

    // the panel stays usable after the failure
    await page.getByTestId('slider-darkness-input').fill('3');
    await expect(page.getByTestId('value-darkness')).toHaveText('3');

    // Try again re-submits the failed run's configuration, not the changed panel
    await setStoryMode(page, 'ok');
    const request = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/api/runs'));
    await page.getByTestId('try-again').click();
    expect((await request).postDataJSON()).toEqual(A);
    // navigation to the new run is asynchronous: wait for the URL to leave the failed run
    await expect.poll(() => runId(page), { timeout: 15_000 }).not.toBe(failedId);
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('value-darkness')).toHaveText('3');
  });

  test('FR-32 a timed-out run shows the timeout message and an enabled Try again', async ({ page }) => {
    await page.route(`**/api/runs/${TIMEOUT_RUN_ID}`, (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          id: TIMEOUT_RUN_ID,
          generationId: 'ORC-2026-10-02-1842',
          kind: 'STANDARD',
          status: 'FAILED',
          stage: 'SEARCHING',
          stageLabel: 'Searching current events…',
          stageIndex: 3,
          stageCount: 10,
          configuration: B,
          counts: ZERO_COUNTS,
          failure: { code: 'RUN_TIMEOUT', message: 'Generation took too long — try again' },
          createdAt: '2026-10-02T18:42:31Z',
          updatedAt: '2026-10-02T18:45:31Z',
          completedAt: '2026-10-02T18:45:31Z',
          hasOpenCriticIssues: false,
        }),
      }),
    );
    await page.goto(`/futures/${TIMEOUT_RUN_ID}`);
    await expect(page.getByTestId('failure-message')).toHaveText('Generation took too long — try again');
    await expect(page.getByTestId('try-again')).toBeVisible();
    await expect(page.getByTestId('try-again')).toBeEnabled();
    await evidence(page, 'FR-32', 'timeout-message');
  });

  // run-control.md FR-47: an unreachable news provider is no failure any more (the run goes on, see search-sources.spec.ts);
  // an unusable scenario answer still shows its fixed friendly message
  test('FR-32 an unusable scenario answer shows its friendly message', async ({ page }) => {
    test.setTimeout(120_000);
    expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'invalid' } })).status()).toBe(204);
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('failure-message')).toHaveText('ORACUL could not construct a valid scenario', {
      timeout: 90_000,
    });
  });

  test('FR-32 errors over HTTP carry only code and message', async ({ page }) => {
    await connect(page);
    const notFound = await page.request.get('/api/runs/abc');
    expect(notFound.status()).toBe(404);
    expect(await notFound.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });

    const bad = await page.request.post('/api/runs', { data: 'not json', headers: { 'content-type': 'application/json' } });
    expect(bad.status()).toBe(400);
    const body = await bad.json();
    expect(Object.keys(body).sort()).toEqual(['code', 'message']);
    expect(JSON.stringify(body)).not.toMatch(/trace|exception/i);
  });
});
