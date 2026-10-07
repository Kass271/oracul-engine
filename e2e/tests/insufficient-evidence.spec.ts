import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
// wildcard-evidence.md slice 06: every kept source counts - one wildcard (New pandemic 8) keeps 4 sources, 5 are needed at Realism 10
const NOTE10 = "Realism 10 couldn't be fully met: only 4 core evidence items (needs 5). This future is less grounded.";
const NO_EVIDENCE = 'No current news could be used — this future is speculative, not grounded in evidence.';
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
/** The one-wildcard bodies of the "Realism 10 with 4 items" test: A10 and A (realism 8) with only New pandemic 8. */
const PANDEMIC = { wildcardId: 'biology-new-pandemic', intensity: 8 };
const ONE10 = { ...A10, wildcards: [PANDEMIC] };
const ONE8 = { ...A, wildcards: [PANDEMIC] };

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
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

/** Acceptance configuration A10 through the panel; `onlyPandemic`: just New pandemic 8 (one pipeline). */
async function configureA10(page: Page, onlyPandemic = false): Promise<void> {
  await page.getByTestId('slider-realism-input').fill('10');
  await page.getByTestId('slider-darkness-input').fill('9');
  await page.getByTestId('slider-optimism-input').fill('2');
  await page.getByTestId('horizon-option-5y').click();
  const wildcards = [
    ['biology', 'biology-new-pandemic', '8'],
    ['robotics', 'robotics-humanoid-boom', '6'],
  ];
  for (const [category, id, intensity] of onlyPandemic ? wildcards.slice(0, 1) : wildcards) {
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

// @trace FR-47, FR-50, FR-52, FR-57
test.describe('FR-47 Always generate, note insufficient evidence at the end', () => {
  test('FR-47 Realism 10 with 4 items completes with the note and LOWER REALISM starts a Realism 8 run', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await configureA10(page, true);
    const started = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/api/runs'));
    await page.getByTestId('generate-button').click();
    expect((await started).postDataJSON()).toEqual(ONE10);
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').pop()!;

    // the run is never stopped by the lack of evidence: result view first, the note below it
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('evidence-note')).toBeVisible();
    await expect(page.getByTestId('evidence-note-message')).toHaveText(NOTE10);
    await expect(page.getByTestId('lower-realism')).toBeVisible();
    await expect(page.getByTestId('lower-realism')).toBeEnabled();
    await expect(page.getByTestId('lower-realism')).toHaveText('LOWER REALISM');
    for (const absent of ['progress-view', 'failure-view', 'insufficient-view']) {
      await expect(page.getByTestId(absent)).toHaveCount(0);
    }
    await evidence(page, 'FR-47', 'insufficient-evidence-note');

    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    expect(run.status).toBe('COMPLETED');
    expect(run.failure ?? null).toBeNull();
    expect(run.headline).toBeTruthy();
    expect(run.evidenceNote).toEqual({ kind: 'INSUFFICIENT_EVIDENCE', message: NOTE10, coreItems: 4, coreNeeded: 5 });
    expect(run.suggestedRealism).toBe(8);
    expect(run.counts.eventsSelected).toBe(4);
    expect(run.counts.counterSignals).toBe(0);
    const pack = await (await page.request.get(`/api/runs/${id}/evidence-pack`)).json();
    expect(pack.core).toEqual([]);
    expect(pack.wildcardSections).toHaveLength(1);
    expect(pack.wildcardSections[0].items).toHaveLength(4);
    const result = await page.request.get(`/api/runs/${id}/result`);
    expect(result.status()).toBe(200);
    const purposes = (await recorded(page)).map(purposeOf);
    for (const needed of ['SCENARIO_GENERATION', 'SCENARIO_CRITIC', 'STORY_WRITING']) {
      expect(purposes, 'the future is written from the evidence there is').toContain(needed);
    }

    // LOWER REALISM starts a new run with realism 8
    const posted = page.waitForRequest((r) => r.method() === 'POST' && r.url().endsWith('/api/runs'));
    await page.getByTestId('lower-realism').click();
    expect((await posted).postDataJSON()).toEqual(ONE8);
    await expect(page.getByTestId('value-realism')).toHaveText('8');
    await expect.poll(() => page.url().split('/').pop(), { timeout: 15_000 }).not.toBe(id);
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const newId = page.url().split('/').pop()!;
    await evidence(page, 'FR-47', 'lower-realism-started');
    const next = await (await page.request.get(`/api/runs/${newId}`)).json();
    expect(next.configuration.realism).toBe(8);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    // Realism 8 needs no core item in the E2E stack (thresholds 0): no note this time
    await expect(page.getByTestId('evidence-note')).toHaveCount(0);
  });

  test('FR-47 no news at all: a speculative future with the NO_EVIDENCE note and no LOWER REALISM', async ({ page }) => {
    test.setTimeout(240_000);
    expect((await page.request.post(`${STUB}/__control/rss`, { data: { mode: 'down' } })).status()).toBe(204);
    await connect(page);
    await configureA10(page);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').pop()!;

    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('sources-empty')).toHaveText('No sources');
    await expect(page.getByTestId('evidence-note-message')).toHaveText(NO_EVIDENCE);
    await expect(page.getByTestId('lower-realism')).toHaveCount(0);
    await expect(page.getByTestId('failure-view')).toHaveCount(0);
    await evidence(page, 'FR-47', 'no-evidence-note');

    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    expect(run.status).toBe('COMPLETED');
    expect(run.failure ?? null).toBeNull();
    expect(run.evidenceNote).toEqual({ kind: 'NO_EVIDENCE', message: NO_EVIDENCE, coreItems: 0, coreNeeded: 5 });
    expect(run.suggestedRealism ?? null).toBeNull();
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    expect(result.sources).toEqual([]);
    // exactly one SCENARIO_GENERATION request, carrying the speculative TASK line
    const generations = (await recorded(page)).filter((r) => purposeOf(r) === 'SCENARIO_GENERATION');
    expect(generations).toHaveLength(1);
    expect(JSON.stringify(generations[0])).toContain(
      'The Evidence Pack is empty: no current news could be used. Write a fully speculative scenario',
    );
    // news never reached the run, yet Google News was asked once per planned query (FR-50: two pipelines x 3 queries; a 503 is not retried)
    // and nothing else was (no fallback provider)
    expect(run.counts.searches).toBe(6);
    expect((await (await page.request.get(`${STUB}/__control/requests?kind=rss`)).json()).requests).toHaveLength(6);
    const all = (await (await page.request.get(`${STUB}/__control/requests?kind=all`)).json()).requests;
    expect(all.filter((r: { path: string }) => r.path.startsWith('/api/v2/doc'))).toHaveLength(0);
  });
});

// @trace FR-31
test.describe('FR-31 Insufficient evidence (stored runs keep their view)', () => {
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
