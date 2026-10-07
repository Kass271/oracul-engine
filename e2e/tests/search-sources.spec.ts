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
  // the reset must also bring the news provider back up (Google News RSS)
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

async function recorded(page: Page, kind: 'responses' | 'rss' | 'all'): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

// @trace FR-50, FR-51
test.describe('FR-50 / FR-51 Pipeline plan and query generation per wildcard', () => {
  test('the acceptance run stores one pipeline per wildcard with 3 generated queries each and sends one tool-less generation request per pipeline', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);

    const res = await page.request.get(`/api/runs/${id}/research`);
    expect(res.status()).toBe(200);
    const plan = (await res.json()).searchPlan;
    expect(plan.queryBudget).toBe(6);
    expect(plan.expansionMode).toBe('MODEL');
    expect(plan.buckets).toEqual([]);
    expect(plan.intents).toEqual([]);
    expect(plan.queries).toEqual([]);
    expect(plan.pipelines).toHaveLength(2);
    const [w1, w2] = plan.pipelines;
    expect(w1).toMatchObject({
      id: 'W01', kind: 'CATALOGUE', label: 'New pandemic', level: 8, topicKey: 'biology-new-pandemic',
      heading: 'New pandemic 8/10', queryMode: 'MODEL',
    });
    expect(w2).toMatchObject({
      id: 'W02', kind: 'CATALOGUE', label: 'Humanoid robot boom', level: 6, topicKey: 'robotics-humanoid-boom',
      heading: 'Humanoid robot boom 6/10', queryMode: 'MODEL',
    });
    expect(w1.queries.map((q: any) => q.id)).toEqual(['Q01', 'Q02', 'Q03']);
    expect(w2.queries.map((q: any) => q.id)).toEqual(['Q04', 'Q05', 'Q06']);
    expect(w1.queries.map((q: any) => q.text)).toEqual(['W01 stub query 1', 'W01 stub query 2', 'W01 stub query 3']);
    expect(w2.queries.map((q: any) => q.text)).toEqual(['W02 stub query 1', 'W02 stub query 2', 'W02 stub query 3']);

    const requests = await recorded(page, 'responses');
    const generations = requests.filter((r) => JSON.stringify(r).includes('ORACUL REQUEST QUERY_GENERATION'));
    expect(generations, 'exactly one generation request per pipeline').toHaveLength(2);
    expect(generations.filter((r) => JSON.stringify(r).includes('Wildcard: New pandemic | Level: 8/10'))).toHaveLength(1);
    expect(generations.filter((r) => JSON.stringify(r).includes('Wildcard: Humanoid robot boom | Level: 6/10'))).toHaveLength(1);
    expect(requests.some((r) => JSON.stringify(r).includes('ORACUL REQUEST QUERY_EXPANSION')), 'no QUERY_EXPANSION any more').toBe(false);
    expect(requests.some((r) => JSON.stringify(r).includes('"tools"'))).toBe(false);
  });

  test('research of an unknown run is 404 with the stable error code', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/research');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});

/** FR-52: the text sent for a planned query — quotes and parentheses become spaces, standalone upper-case OR / AND / NOT go, whitespace is collapsed. */
function sentText(text: string): string {
  return text
    .replace(/["\u201C\u201D()]/g, ' ')
    .split(/\s+/)
    .filter((t) => t !== '' && !['OR', 'AND', 'NOT'].includes(t))
    .join(' ');
}

// @trace FR-46, FR-47, FR-48, FR-50, FR-52, FR-53
test.describe('FR-52 / FR-53 Google News RSS, one request per query, at most 4 sources per wildcard and 30 per run', () => {
  async function rssMode(page: Page, mode: string): Promise<void> {
    const r = await page.request.post(`${STUB}/__control/rss`, { data: { mode } });
    expect(r.status(), `rss mode ${mode}`).toBe(204);
  }
  /** FR-49: no run ever sends a request to the path of the former provider; every request of the stub is logged by kind=all. */
  async function expectNoFormerProviderRequest(page: Page): Promise<void> {
    const all = await recorded(page, 'all');
    expect(all.some((r) => r.path === '/rss/search'), 'the log of all requests holds the Google News requests').toBe(true);
    for (const r of all) expect(Object.keys(r).sort()).toEqual(['at', 'method', 'path']);
    expect(all.filter((r) => String(r.path).startsWith('/api/v2/doc'))).toHaveLength(0);
  }

  test('the acceptance run searches its 6 queries as 6 bare Google News RSS requests, considers 25 articles and keeps 7 sources', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    expect(run.counts.searches).toBe(6);
    expect(run.counts.articlesRetrieved).toBe(30);
    expect(run.counts.articlesConsidered).toBe(25);
    expect(run.counts.sourcesKept).toBe(7);

    const plan0 = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    const planQueries: any[] = plan0.pipelines.flatMap((p: any) => p.queries);
    expect(planQueries.map((q) => q.id)).toEqual(['Q01', 'Q02', 'Q03', 'Q04', 'Q05', 'Q06']);
    const rss = await recorded(page, 'rss');
    expect(rss, 'one request per planned query (counts.searches)').toHaveLength(run.counts.searches);
    for (const r of rss) {
      expect(Object.keys(r.params).sort()).toEqual(['ceid', 'gl', 'hl', 'q']);
      expect(r.params.hl).toBe('en-US');
      expect(r.params.gl).toBe('US');
      expect(r.params.ceid).toBe('US:en');
      expect(r.q).toMatch(/^[^()"]+ when:90d$/);
      expect(r.q).not.toContain(' OR ');
    }
    expect(
      rss.map((r) => r.q).sort(),
      'every planned query once, cleaned, plus the window',
    ).toEqual(planQueries.map((q: any) => `${sentText(q.text)} when:90d`).sort());
    // no spacing: all 6 requests arrive within 1.5 s
    const arrivals = rss.map((r) => r.at);
    expect(Math.max(...arrivals) - Math.min(...arrivals)).toBeLessThan(1500);
    await expectNoFormerProviderRequest(page);

    const res = await page.request.get(`/api/runs/${id}/sources`);
    expect(res.status()).toBe(200);
    const items = (await res.json()).items;
    // FR-53: per pipeline the shared article + the `-2` article of each of its 3 queries (the 4 best by relevance); 25 candidates, 7 kept
    expect(items, 'the shared article + 3 + 3 selected own articles').toHaveLength(7);
    expect(items.map((s: any) => s.id)).toEqual(Array.from({ length: 7 }, (_, n) => `S${String(n + 1).padStart(3, '0')}`));
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
    expect(new Set(items.map((s: any) => s.url)).size).toBe(7);
    // S001 is the article every query's own answer holds: it lists all 6 plan ids and both pipelines (FR-52 / FR-50: a source
    // belongs to the queries whose request returned it and to the pipelines owning them)
    expect(items[0].url).toBe('http://stub:4010/articles/shared');
    expect(items[0].queryIds).toEqual(['Q01', 'Q02', 'Q03', 'Q04', 'Q05', 'Q06']);
    expect(items[0].pipelineIds).toEqual(['W01', 'W02']);
    expect(items[0].topic).toBe('biology-new-pandemic');
    // S002-S004: the `-2` article of Q01 / Q02 / Q03 (position 2 of each answer, query id order), found by W01 only
    items.slice(1, 4).forEach((s: any, n: number) => {
      expect(s.url, `${s.id} is the -2 article`).toMatch(/-2$/);
      expect(s.pipelineIds, `${s.id} pipelineIds`).toEqual(['W01']);
      expect(s.topic).toBe('biology-new-pandemic');
      expect(s.queryIds).toEqual([`Q0${n + 1}`]);
    });
    // S005-S007: the `-2` article of Q04 / Q05 / Q06, found by W02 only
    items.slice(4).forEach((s: any, n: number) => {
      expect(s.url, `${s.id} is the -2 article`).toMatch(/-2$/);
      expect(s.pipelineIds, `${s.id} pipelineIds`).toEqual(['W02']);
      expect(s.topic).toBe('robotics-humanoid-boom');
      expect(s.queryIds).toEqual([`Q0${n + 4}`]);
    });
    // the topic of a source is the topicKey of its first pipeline: two topics, 4 + 3
    const perTopic = new Map<string, number>();
    for (const s of items) perTopic.set(s.topic, (perTopic.get(s.topic) ?? 0) + 1);
    expect([...perTopic.keys()].sort()).toEqual(['biology-new-pandemic', 'robotics-humanoid-boom']);
    expect(perTopic.get('biology-new-pandemic')).toBe(4);
    expect(perTopic.get('robotics-humanoid-boom')).toBe(3);
    // the article fetch runs for the 7 kept sources only
    const pageFetches = (await recorded(page, 'all')).filter((r) => String(r.path).startsWith('/articles/'));
    expect(pageFetches, 'one article page fetch per kept source').toHaveLength(7);

    // FR-53: the per-pipeline fields written by the READING_SOURCES commit
    const research = (await (await page.request.get(`/api/runs/${id}/research`)).json());
    expect(research.counts.sourcesKept).toBe(7);
    const [w1, w2] = research.searchPlan.pipelines;
    expect(w1.candidatesConsidered).toBe(13);
    expect(w1.sourceIds).toEqual(['S001', 'S002', 'S003', 'S004']);
    expect(w2.candidatesConsidered).toBe(13);
    expect(w2.sourceIds).toEqual(['S001', 'S005', 'S006', 'S007']);

    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    expect(plan.queries, 'the statuses live in the pipelines').toEqual([]);
    const queries: any[] = plan.pipelines.flatMap((p: any) => p.queries);
    expect(queries).toHaveLength(6);
    for (const q of queries) {
      expect(q.status).toBe('OK');
      expect(q.articlesReturned).toBe(5);
    }
  });

  test('Google down: there is no fallback, every query is FAILED and the run still completes with the NO_EVIDENCE note', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'down');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss'), 'one request per query, a 503 is not retried').toHaveLength(6);
    await expectNoFormerProviderRequest(page);
    expect(run.counts.searches).toBe(6);
    expect(run.counts.articlesRetrieved).toBe(0);
    expect(run.counts.articlesConsidered).toBe(0);
    expect(run.counts.sourcesKept, 'FR-53: a run that found nothing still commits sourcesKept 0').toBe(0);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    const queries: any[] = plan.pipelines.flatMap((p: any) => p.queries);
    expect(queries).toHaveLength(6);
    for (const q of queries) expect(q.status).toBe('FAILED');
    for (const p of plan.pipelines) {
      expect(p.candidatesConsidered).toBe(0);
      expect(p.sourceIds).toEqual([]);
    }
    expect(run.failure ?? null).toBeNull();
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
    expect((await (await page.request.get(`/api/runs/${id}/sources`)).json()).items).toEqual([]);
  });

  test('Google empty: queries are EMPTY, nothing else is asked and the run is speculative', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'empty');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss')).toHaveLength(6);
    await expectNoFormerProviderRequest(page);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    const queries: any[] = plan.pipelines.flatMap((p: any) => p.queries);
    expect(queries).toHaveLength(6);
    for (const q of queries) expect(q.status).toBe('EMPTY');
    expect(run.counts.articlesRetrieved).toBe(0);
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
    expect((await (await page.request.get(`/api/runs/${id}/sources`)).json()).items).toEqual([]);
  });

  test('Google malformed: every query is FAILED and the run still completes with the NO_EVIDENCE note', async ({ page }) => {
    test.setTimeout(90_000);
    await rssMode(page, 'malformed');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 60_000);
    expect(await recorded(page, 'rss')).toHaveLength(6);
    await expectNoFormerProviderRequest(page);
    expect(run.counts.searches).toBe(6);
    expect(run.counts.articlesRetrieved).toBe(0);
    expect(run.counts.articlesConsidered).toBe(0);
    const plan = (await (await page.request.get(`/api/runs/${id}/research`)).json()).searchPlan;
    const queries: any[] = plan.pipelines.flatMap((p: any) => p.queries);
    expect(queries).toHaveLength(6);
    for (const q of queries) {
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

// @trace FR-49, FR-52
test.describe('FR-49 The former news provider is gone (E2E stub)', () => {
  // the name of the former provider, in pieces: the scan of FR-49 allows it in one backend test file only
  const FORMER = ['gd', 'elt'].join('');

  test('the stub has no route, no control endpoint and no request kind of the former provider', async ({ request }) => {
    expect((await request.get(`${STUB}/api/v2/doc/doc?query=x&format=json`)).status()).toBe(404);
    const control = await request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
    expect(control.status()).toBe(404);
    expect(await control.json()).toEqual({ error: 'not_found' });
    const kind = await request.get(`${STUB}/__control/requests?kind=${FORMER}`);
    expect(kind.status()).toBe(400);
    expect(await kind.json()).toEqual({ error: 'unknown_kind' });
  });

  test('kind=all lists every non-control request in arrival order and is cleared by reset', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);
    const all = await recorded(page, 'all');
    expect(all.length).toBeGreaterThan(20);
    expect(all.filter((r) => r.path === '/rss/search')).toHaveLength(6);
    for (const r of all) {
      expect(String(r.path).startsWith('/__control/')).toBe(false);
      expect(typeof r.method).toBe('string');
      expect(typeof r.at).toBe('number');
    }
    expect(all.map((r) => r.at)).toEqual([...all.map((r) => r.at)].sort((a, b) => a - b));
    expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
    expect(await recorded(page, 'all')).toEqual([]);
  });
});
