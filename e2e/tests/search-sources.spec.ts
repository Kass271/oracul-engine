import { expect, test, type Page } from '@playwright/test';

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

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  // the reset must also bring the news provider back up
  await request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
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

/** Connects, configures acceptance A in the panel, generates and returns the run id. */
async function startAcceptanceRun(page: Page): Promise<string> {
  await connect(page);
  await configureAcceptance(page);
  await page.getByTestId('generate-button').click();
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  return page.url().split('/').pop()!;
}

async function awaitStatus(page: Page, id: string, wanted: string, timeout: number) {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout, intervals: [500] })
    .toBe(wanted);
  return (await page.request.get(`/api/runs/${id}`)).json();
}

async function recorded(page: Page, kind: 'responses' | 'gdelt'): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

// @trace FR-12
test.describe('FR-12 Search plan and query generation', () => {
  test('the acceptance run stores a plan with 8/6/4/2 queries and one tool-less expansion request', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);

    const res = await page.request.get(`/api/runs/${id}/research`);
    expect(res.status()).toBe(200);
    const plan = (await res.json()).searchPlan;
    expect(plan.queryBudget).toBe(20);
    expect(plan.expansionMode).toBe('MODEL');
    expect(plan.buckets.map((b: any) => b.queries)).toEqual([8, 6, 4, 2]);
    expect(plan.buckets.map((b: any) => b.bucket)).toEqual(['WILDCARD', 'MAJOR', 'ADJACENT', 'UNEXPECTED']);
    const i01 = plan.intents[0];
    expect(i01.id).toBe('I01');
    expect(i01.topicKey).toBe('biology-new-pandemic');
    expect(i01.drivenBy).toEqual(expect.arrayContaining(['New pandemic 8/10', 'Darkness 9/10']));
    expect(plan.queries).toHaveLength(20);

    const requests = await recorded(page, 'responses');
    const expansions = requests.filter((r) => JSON.stringify(r).includes('ORACUL REQUEST QUERY_EXPANSION'));
    expect(expansions).toHaveLength(1);
    expect(requests.some((r) => JSON.stringify(r).includes('"tools"'))).toBe(false);
  });

  test('research of an unknown run is 404 with the stable error code', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/research');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});

// @trace FR-13
test.describe('FR-13 Current-news search and source retrieval', () => {
  test('the acceptance run searches 20 queries and keeps 81 sources', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    expect(run.counts.searches).toBe(20);
    expect(run.counts.articlesRetrieved).toBe(100);
    expect(run.counts.articlesConsidered).toBe(81);

    const res = await page.request.get(`/api/runs/${id}/sources`);
    expect(res.status()).toBe(200);
    const items = (await res.json()).items;
    expect(items).toHaveLength(81);
    for (const s of items) {
      expect(s.url).toBeTruthy();
      expect(s.publisher).toBeTruthy();
      expect(s.title).toBeTruthy();
      expect(s.publishedAt).toBeTruthy();
      expect(s.retrievedAt).toBeTruthy();
    }
    expect(new Set(items.map((s: any) => s.url)).size).toBe(81);
    expect(await recorded(page, 'gdelt')).toHaveLength(20);
  });

  test('news provider down: the run fails with NEWS_UNAVAILABLE and no later ChatGPT call', async ({ page }) => {
    test.setTimeout(90_000);
    const down = await page.request.post(`${STUB}/__control/news`, { data: { mode: 'down' } });
    expect(down.ok()).toBeTruthy();
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'FAILED', 40_000);
    expect(run.failure.message).toBe('ORACUL could not reach its news sources — try again later');
    expect(run.failure.code).toBe('NEWS_UNAVAILABLE');
    expect(await recorded(page, 'responses')).toHaveLength(1);
    const sources = await page.request.get(`/api/runs/${id}/sources`);
    expect(await sources.json()).toEqual({ items: [] });
    // slice 11: a FAILED run shows the failure view with the fixed message
    await expect(page.getByTestId('failure-view')).toBeVisible();
    await expect(page.getByTestId('failure-message')).toHaveText('ORACUL could not reach its news sources — try again later');
  });

  test('sources of an unknown run is 404 RUN_NOT_FOUND', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/sources');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});
