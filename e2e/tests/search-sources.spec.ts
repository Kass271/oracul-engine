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
  // the reset must also bring the news providers back up (Google News RSS and GDELT)
  await request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  await request.post(`${STUB}/__control/rss`, { data: { mode: 'ok' } });
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

async function recorded(page: Page, kind: 'responses' | 'gdelt' | 'rss'): Promise<any[]> {
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

// @trace FR-46, FR-47, FR-48
test.describe('FR-48 / FR-46 Google News RSS first, at most 30 sources', () => {
  async function rssMode(page: Page, mode: string): Promise<void> {
    const r = await page.request.post(`${STUB}/__control/rss`, { data: { mode } });
    expect(r.status(), `rss mode ${mode}`).toBe(204);
  }
  async function newsMode(page: Page, mode: string): Promise<void> {
    const r = await page.request.post(`${STUB}/__control/news`, { data: { mode } });
    expect(r.ok(), `news mode ${mode}`).toBeTruthy();
  }

  test('the acceptance run searches 20 queries as 4 Google News RSS requests and keeps 30 sources', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    expect(run.counts.searches).toBe(20);
    expect(run.counts.articlesRetrieved).toBe(100);
    expect(run.counts.articlesConsidered).toBe(30);

    const rss = await recorded(page, 'rss');
    expect(rss).toHaveLength(4);
    for (const r of rss) {
      expect(Object.keys(r.params).sort()).toEqual(['ceid', 'gl', 'hl', 'q']);
      expect(r.params.hl).toBe('en-US');
      expect(r.params.gl).toBe('US');
      expect(r.params.ceid).toBe('US:en');
      expect(r.q).toMatch(/^\(.+( OR .+)+\) when:\d+d$/);
      expect(r.q).toMatch(/ when:90d$/);
    }
    // the E2E stack spaces Google request starts by 0.2 s (slack for clock jitter between the two ends)
    for (let k = 1; k < rss.length; k++) expect(rss[k].at - rss[k - 1].at).toBeGreaterThanOrEqual(150);
    expect(await recorded(page, 'gdelt'), 'Google answers every group: no GDELT request').toHaveLength(0);

    const res = await page.request.get(`/api/runs/${id}/sources`);
    expect(res.status()).toBe(200);
    const items = (await res.json()).items;
    expect(items).toHaveLength(30);
    expect(items.map((s: any) => s.id)).toEqual(Array.from({ length: 30 }, (_, n) => `S${String(n + 1).padStart(3, '0')}`));
    for (const s of items) {
      expect(s.url).toMatch(/^http:\/\/stub:4010\/articles\//);
      expect(s.publisherUrl).toBe('https://www.reuters.com');
      expect(s.publisher).toBeTruthy();
      expect(s.title).toBeTruthy();
      expect(s.title).not.toContain(' - Reuters');
      expect(s.publishedAt).toBeTruthy();
      expect(s.retrievedAt).toBeTruthy();
      expect(s.metadataFetched).toBe(true);
    }
    expect(new Set(items.map((s: any) => s.url)).size).toBe(30);
    // the first candidate is the article all queries share; every group answers it with the same title, which
    // matches no query element, so each group attributes it to its first query (news-search FR-44 steps 7 and 9)
    expect(items[0].url).toBe('http://stub:4010/articles/shared');
    expect(items[0].queryIds).toEqual(['Q01', 'Q06', 'Q11', 'Q16']);
    // topic round robin: 6 topics, all with at least 5 candidates -> exactly 5 each
    const perTopic = new Map<string, number>();
    for (const s of items) perTopic.set(s.topic, (perTopic.get(s.topic) ?? 0) + 1);
    expect([...perTopic.keys()].sort()).toEqual(['biology', 'biology-new-pandemic', 'major', 'robotics', 'robotics-humanoid-boom', 'unexpected']);
    for (const n of perTopic.values()) expect(n).toBe(5);

    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    expect(plan.queries).toHaveLength(20);
    for (const q of plan.queries) expect(q.status).toBe('OK');
  });

  test('Google down: every group falls back to GDELT and the run completes from the GDELT answers', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'down');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss')).toHaveLength(4);
    const gdelt = await recorded(page, 'gdelt');
    expect(gdelt).toHaveLength(4);
    for (const r of gdelt) expect(r.query).toMatch(/^\(.+( OR .+)+\) sourcelang:english$/);
    expect(run.counts.articlesRetrieved).toBe(100);
    expect(run.counts.articlesConsidered).toBe(30);
    const items = (await (await page.request.get(`/api/runs/${id}/sources`)).json()).items;
    expect(items).toHaveLength(30);
    for (const s of items) expect(s.publisherUrl ?? null, 'GDELT sources have no publisherUrl').toBeNull();
  });

  test('Google empty: queries are EMPTY, GDELT is not asked and the run is speculative', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'empty');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss')).toHaveLength(4);
    expect(await recorded(page, 'gdelt')).toHaveLength(0);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    for (const q of plan.queries) expect(q.status).toBe('EMPTY');
    expect(run.counts.articlesRetrieved).toBe(0);
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
    expect((await (await page.request.get(`/api/runs/${id}/sources`)).json()).items).toEqual([]);
  });

  test('Google malformed and GDELT down: every query is FAILED and the run still completes with the NO_EVIDENCE note', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'malformed');
    await newsMode(page, 'down');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss')).toHaveLength(4);
    expect(await recorded(page, 'gdelt')).toHaveLength(4);
    expect(run.counts.searches).toBe(20);
    expect(run.counts.articlesRetrieved).toBe(0);
    expect(run.counts.articlesConsidered).toBe(0);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    expect(plan.queries).toHaveLength(20);
    for (const q of plan.queries) {
      expect(q.status).toBe('FAILED');
      expect(q.articlesReturned).toBe(0);
    }
    expect(run.failure ?? null).toBeNull();
    expect(run.evidenceNote).toEqual({
      kind: 'NO_EVIDENCE',
      message: 'No current news could be used — this future is speculative, not grounded in evidence.',
      coreItems: 0,
      coreNeeded: 0,
    });
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('evidence-note-message')).toHaveText(
      'No current news could be used — this future is speculative, not grounded in evidence.',
    );
  });

  test('an unknown rss mode is rejected by the stub control API', async ({ request }) => {
    const r = await request.post(`${STUB}/__control/rss`, { data: { mode: 'sideways' } });
    expect(r.status()).toBe(400);
    expect(await r.json()).toEqual({ error: 'unknown_mode' });
  });
});

// @trace FR-13
test.describe('FR-13 Current-news search and source retrieval', () => {
  test('sources of an unknown run is 404 RUN_NOT_FOUND', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/sources');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});

// @trace FR-44, FR-47
test.describe('FR-44 Real news search within GDELT limits (GDELT is the fallback of a failed Google group)', () => {
  async function newsMode(page: Page, mode: string): Promise<void> {
    const r = await page.request.post(`${STUB}/__control/news`, { data: { mode } });
    expect(r.ok(), `news mode ${mode}`).toBeTruthy();
  }
  async function googleDown(page: Page): Promise<void> {
    const r = await page.request.post(`${STUB}/__control/rss`, { data: { mode: 'down' } });
    expect(r.status()).toBe(204);
  }

  test('FR-44 with Google down the acceptance run sends 4 spaced OR-group requests to GDELT and keeps the counts 20 / 100 / 30', async ({ page }) => {
    test.setTimeout(90_000);
    await googleDown(page);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(run.counts.searches).toBe(20);
    expect(run.counts.articlesRetrieved).toBe(100);
    expect(run.counts.articlesConsidered).toBe(30);

    const requests = await recorded(page, 'gdelt');
    expect(requests).toHaveLength(4);
    for (const r of requests) {
      expect(r.query).toMatch(/^\(.+( OR .+)+\) sourcelang:english$/);
      expect(r.params.maxrecords).toBe('125');
      expect(r.params.mode).toBe('ArtList');
      expect(r.params.format).toBe('json');
      expect(r.params.sort).toBe('HybridRel');
    }
    // the E2E stack spaces request starts by 0.5 s (a little slack for clock jitter between the two ends)
    for (let k = 1; k < requests.length; k++) {
      expect(requests[k].at - requests[k - 1].at).toBeGreaterThanOrEqual(450);
    }
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    expect(plan.queries).toHaveLength(20);
    for (const q of plan.queries) expect(q.status).toBe('OK');
  });

  test('FR-44 a 429 is retried once after the rate-limit wait and the run completes', async ({ page }) => {
    test.setTimeout(90_000);
    await googleDown(page);
    await newsMode(page, 'rate-limited-once');
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);
    const requests = await recorded(page, 'gdelt');
    expect(requests).toHaveLength(5);
    expect(requests[1].query).toBe(requests[0].query);
    expect(requests[1].at - requests[0].at).toBeGreaterThanOrEqual(450);
  });

  test('FR-44 groups that fail do not stop the run: the queries of groups 1-2 are FAILED, the others OK', async ({ page }) => {
    test.setTimeout(90_000);
    await googleDown(page);
    await newsMode(page, 'partial');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(run.counts.searches).toBe(20);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    plan.queries.forEach((q: any, i: number) => expect(q.status, q.id).toBe(i < 10 ? 'FAILED' : 'OK'));
    expect(await recorded(page, 'gdelt')).toHaveLength(4);
  });

  test('FR-44 every group rate-limited twice no longer ends the run: 8 GDELT requests, every query FAILED, NO_EVIDENCE note', async ({ page }) => {
    test.setTimeout(90_000);
    await googleDown(page);
    await newsMode(page, 'rate-limited');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(run.failure ?? null).toBeNull();
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
    expect(await recorded(page, 'gdelt')).toHaveLength(8);
    expect(await recorded(page, 'rss')).toHaveLength(4);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    for (const q of plan.queries) expect(q.status).toBe('FAILED');
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('failure-view')).toHaveCount(0);
  });
});
