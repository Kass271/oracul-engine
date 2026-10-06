import { expect, test, type Page } from '@playwright/test';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  // the reset must also bring the event answers back to normal
  const events = await request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } });
  expect(events.status()).toBe(204);
});

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

/** Acceptance configuration A of generation-runs.md "Slice 04_run-start" through the panel. */
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

async function recorded(page: Page): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=responses`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

function purposeOf(request: any): string | undefined {
  return /ORACUL REQUEST ([A-Z_]+)/.exec(JSON.stringify(request))?.[1];
}

async function listEvents(page: Page, id: string): Promise<any[]> {
  const res = await page.request.get(`/api/runs/${id}/events`);
  expect(res.status()).toBe(200);
  return (await res.json()).items;
}

// @trace FR-14
// @trace FR-15
// @trace FR-46
// @trace FR-47
test.describe('FR-14 / FR-15 Event normalisation and semantic classification', () => {
  test('the acceptance run produces 15 normalized, classified events from its 30 kept sources', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    // news-search.md FR-46: the run keeps 30 sources; the stub pairs them up -> 15 events
    expect(run.counts.articlesConsidered).toBe(30);
    expect(run.counts.uniqueEvents).toBe(15);

    const events = await listEvents(page, id);
    expect(events).toHaveLength(15);
    expect(events[0].id).toBe('EV001');
    expect(events[14].id).toBe('EV015');
    expect(events[0].sourceIds).toEqual(['S001', 'S002']);
    expect(events[14].sourceIds).toEqual(['S029', 'S030']);
    for (const e of events) {
      expect(e.excludedReason).toBeUndefined();
      expect(e.classification).toBeTruthy();
      const c = e.classification;
      expect(c.sentiment).toBeGreaterThanOrEqual(-1);
      expect(c.sentiment).toBeLessThanOrEqual(1);
      for (const k of ['risk', 'opportunity', 'impact', 'novelty', 'sourceQuality']) {
        expect(c[k]).toBeGreaterThanOrEqual(0);
        expect(c[k]).toBeLessThanOrEqual(1);
      }
      // the stub answers wildcardMatches [] -> one 0.0 entry per profile topic, in profile order
      expect(c.wildcardMatches).toEqual([
        { key: 'biology-new-pandemic', score: 0 },
        { key: 'robotics-humanoid-boom', score: 0 },
      ]);
    }

    const sources = await page.request.get(`/api/runs/${id}/sources`);
    const s001 = (await sources.json()).items.find((s: any) => s.id === 'S001');
    expect(s001.entities).toEqual(['Entity S001']);

    const requests = await recorded(page);
    const purposes = requests.map(purposeOf);
    expect(purposes.filter((p) => p === 'QUERY_EXPANSION')).toHaveLength(1);
    // 30 sources fit one normalisation batch (40), 15 events one classification batch (20)
    expect(purposes.filter((p) => p === 'EVENT_NORMALIZATION')).toHaveLength(1);
    expect(purposes.filter((p) => p === 'EVENT_CLASSIFICATION')).toHaveLength(1);
    // slice 08: the pack is not empty, so exactly one SCENARIO_GENERATION request follows
    expect(purposes.filter((p) => p === 'SCENARIO_GENERATION')).toHaveLength(1);
    // slice 10: the critic request sits between the scenario and the story
    expect(purposes.filter((p) => p === 'SCENARIO_CRITIC')).toHaveLength(1);
    expect(purposes.indexOf('SCENARIO_CRITIC')).toBeGreaterThan(purposes.indexOf('SCENARIO_GENERATION'));
    expect(purposes.indexOf('SCENARIO_CRITIC')).toBeLessThan(purposes.indexOf('STORY_WRITING'));
    // slice 09: the accepted scenario is followed by exactly one STORY_WRITING request, last
    expect(purposes.filter((p) => p === 'STORY_WRITING')).toHaveLength(1);
    expect(purposes[purposes.length - 1]).toBe('STORY_WRITING');
    expect(requests.some((r) => JSON.stringify(r).includes('"tools"'))).toBe(false);
  });

  test('FR-15 malformed classification answers exclude every event with CLASSIFICATION_FAILED; the empty pack is written up speculatively', async ({ page }) => {
    test.setTimeout(90_000);
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'malformed-classification' } });
    expect(mode.status()).toBe(204);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    expect(run.counts.uniqueEvents).toBe(15);
    const events = await listEvents(page, id);
    expect(events).toHaveLength(15);
    for (const e of events) {
      expect(e.excludedReason).toBe('CLASSIFICATION_FAILED');
      expect(e.classification).toBeUndefined();
    }
    const purposes = (await recorded(page)).map(purposeOf);
    // one batch, answered twice (the content retry)
    expect(purposes.filter((p) => p === 'EVENT_CLASSIFICATION')).toHaveLength(2);
    // run-control.md FR-47: the pack is empty (every event excluded) -> speculative mode, still scenario, critic and story
    expect(purposes.filter((p) => p === 'SCENARIO_GENERATION')).toHaveLength(1);
    expect(purposes.filter((p) => p === 'SCENARIO_CRITIC')).toHaveLength(1);
    expect(purposes.filter((p) => p === 'STORY_WRITING')).toHaveLength(1);
    expect(run.status).toBe('COMPLETED');
    expect(run.headline).toBeTruthy();
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
  });

  test('FR-14 a rate-limited normalisation fails the run with the usage-limit message and writes no events', async ({ page }) => {
    test.setTimeout(90_000);
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'rate-limited' } });
    expect(mode.status()).toBe(204);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'FAILED', 40_000);
    expect(run.failure.code).toBe('CHATGPT_RATE_LIMITED');
    expect(run.failure.message).toBe('ChatGPT usage limit reached — try again later');
    expect(run.stageIndex).toBe(5);
    expect(run.counts.uniqueEvents).toBe(0);
    const res = await page.request.get(`/api/runs/${id}/events`);
    expect(res.status()).toBe(200);
    expect(await res.json()).toEqual({ items: [] });
  });

  test('events of an unknown run is 404 RUN_NOT_FOUND', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/events');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});
