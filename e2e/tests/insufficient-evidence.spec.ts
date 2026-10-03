import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const MESSAGE10 = 'ORACUL found insufficient current evidence to construct this scenario at Realism 10.';
const MESSAGE1 = 'ORACUL found insufficient current evidence to construct this scenario at Realism 1.';
const FIXED_ID = '22222222-2222-2222-2222-222222222222';

/** Acceptance body A with realism 10 (A10); A is the same body with realism 8. */
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
const A10 = { ...A, realism: 10 };

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await page.request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode: 'ok' } })).status()).toBe(204);
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

/** Acceptance configuration A10 through the panel. */
async function configureA10(page: Page): Promise<void> {
  await page.getByTestId('slider-realism-input').fill('10');
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

async function recorded(page: Page): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=responses`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

function purposeOf(request: any): string | undefined {
  const text = (request.input ?? [])
    .flatMap((m: any) => (Array.isArray(m.content) ? m.content : []))
    .map((c: any) => c.text ?? '')
    .join('\n');
  return /ORACUL REQUEST ([A-Z_]+)/.exec(text)?.[1];
}

// @trace FR-31
test.describe('FR-31 Insufficient evidence', () => {
  test('FR-31 Realism 10 with 2 core items ends in the insufficient view and LOWER REALISM starts a Realism 8 run', async ({ page }) => {
    test.setTimeout(240_000);
    expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'sparse' } })).status()).toBe(204);
    await connect(page);
    await configureA10(page);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').pop()!;

    // acceptance 1
    await expect(page.getByTestId('insufficient-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('insufficient-message')).toHaveText(MESSAGE10);
    await expect(page.getByTestId('lower-realism')).toBeVisible();
    await expect(page.getByTestId('lower-realism')).toBeEnabled();
    await expect(page.getByTestId('lower-realism')).toHaveText('LOWER REALISM');
    for (const absent of ['progress-view', 'failure-view', 'result-view']) {
      await expect(page.getByTestId(absent)).toHaveCount(0);
    }
    await evidence(page, 'FR-31', 'insufficient-view');

    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    expect(run.status).toBe('INSUFFICIENT_EVIDENCE');
    expect(run.failure.code).toBe('INSUFFICIENT_EVIDENCE');
    expect(run.suggestedRealism).toBe(8);
    const pack = await (await page.request.get(`/api/runs/${id}/evidence-pack`)).json();
    expect(pack.core).toHaveLength(2);
    const result = await page.request.get(`/api/runs/${id}/result`);
    expect(result.status()).toBe(409);
    expect((await result.json()).code).toBe('RESULT_NOT_READY');
    const purposes = (await recorded(page)).map(purposeOf);
    for (const forbidden of ['SCENARIO_GENERATION', 'SCENARIO_CRITIC', 'STORY_WRITING']) {
      expect(purposes, 'no story generated').not.toContain(forbidden);
    }

    // acceptance 2
    const posted = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/api/runs'));
    await page.getByTestId('lower-realism').click();
    expect((await posted).postDataJSON()).toEqual(A);
    await expect(page.getByTestId('value-realism')).toHaveText('8');
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const newId = page.url().split('/').pop()!;
    expect(newId).not.toBe(id);
    await evidence(page, 'FR-31', 'lower-realism-started');
    const next = await (await page.request.get(`/api/runs/${newId}`)).json();
    expect(next.configuration.realism).toBe(8);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
  });

  test('FR-31 Realism 1 has no LOWER REALISM button', async ({ page }) => {
    const run = {
      id: FIXED_ID,
      generationId: 'ORC-2026-10-02-1842',
      kind: 'STANDARD',
      status: 'INSUFFICIENT_EVIDENCE',
      stage: 'RANKING',
      stageLabel: 'Ranking evidence…',
      stageIndex: 6,
      stageCount: 10,
      configuration: {
        realism: 1,
        darkness: 5,
        optimism: 5,
        horizon: '1y',
        wildcards: [],
        customWildcards: [],
        output: { story: true, illustration: false },
      },
      counts: { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 },
      failure: { code: 'INSUFFICIENT_EVIDENCE', message: MESSAGE1 },
      createdAt: '2026-10-02T18:42:31Z',
      updatedAt: '2026-10-02T18:43:31Z',
      completedAt: '2026-10-02T18:43:31Z',
      hasOpenCriticIssues: false,
    };
    await page.route(`**/api/runs/${FIXED_ID}`, (route) => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(run) }));
    await page.goto(`/futures/${FIXED_ID}`);
    await expect(page.getByTestId('insufficient-message')).toHaveText(MESSAGE1);
    await expect(page.getByTestId('lower-realism')).toHaveCount(0);
    await evidence(page, 'FR-31', 'insufficient-realism-1');
  });
});
