import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

/** Body PE (wildcard-evidence.md slice 09): New pandemic 8, then Energy crisis 3 -> W01 CATALOGUE (Q01-Q03), W02 CATALOGUE (Q04-Q06). */
const PE = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [
    { wildcardId: 'biology-new-pandemic', intensity: 8 },
    { wildcardId: 'energy-energy-crisis', intensity: 3 },
  ],
  customWildcards: [],
  output: { story: true, illustration: false },
};
const PE10 = { ...PE, realism: 10 };

const MISSING_SENTENCE = 'No current sources found for: Energy crisis. This part of the future is speculative.';
const NO_EVIDENCE = 'No current news could be used — this future is speculative, not grounded in evidence.';
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  expect((await request.post(`${STUB}/__control/google`, { data: { mode: 'ok' } })).status()).toBe(204);
  for (const name of ['events', 'scenario', 'story']) {
    expect((await request.post(`${STUB}/__control/${name}`, { data: { mode: 'ok' } })).status()).toBe(204);
  }
});

async function googleMode(page: Page, mode: string, term?: string): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/google`, { data: term ? { mode, term } : { mode } });
  expect(r.status(), `google mode ${mode}`).toBe(204);
}

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

/** Connects, starts body PE through POST /api/runs, waits for COMPLETED and opens the result view. */
async function runPE(page: Page, body: object = PE): Promise<string> {
  await connect(page);
  const res = await page.request.post('/api/runs', { data: body });
  expect(res.status(), await res.text()).toBe(202);
  const id: string = (await res.json()).id;
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout: 90_000, intervals: [500] })
    .toBe('COMPLETED');
  await page.goto(`/futures/${id}`);
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
  return id;
}

const getJson = async (page: Page, path: string): Promise<any> => {
  const res = await page.request.get(path);
  expect(res.status(), path).toBe(200);
  return res.json();
};

async function recorded(page: Page, kind: 'rss' | 'all'): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

/** All data-testid values on the page matching {@code re}, in document order. */
const testids = (page: Page, re: RegExp): Promise<string[]> =>
  page.evaluate(
    ([source, flags]) => {
      const rx = new RegExp(source, flags);
      return Array.from(document.querySelectorAll('[data-testid]'))
        .map((n) => n.getAttribute('data-testid') as string)
        .filter((t) => rx.test(t));
    },
    [re.source, re.flags],
  );

const formatDate = (iso?: string): string => {
  if (!iso) return 'date unknown';
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? 'date unknown' : `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
};
const plural = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`;
const itemsText = (n: number): string => plural(n, 'item', 'items');

/** E2E invariants for every grouped result (wildcard-result-views.md "Ranges & invariants"). */
async function expectGroupInvariants(page: Page, id: string, result: any): Promise<void> {
  const research = await getJson(page, `/api/runs/${id}/research`);
  const pack = await getJson(page, `/api/runs/${id}/evidence-pack`);
  const run = await getJson(page, `/api/runs/${id}`);
  const groups: any[] = result.wildcardGroups;
  const pipelines: any[] = research.searchPlan.pipelines;
  expect(groups.map((g) => g.pipelineId)).toEqual(pipelines.map((p) => p.id));
  expect(groups.map((g) => g.heading)).toEqual(pipelines.map((p) => p.heading));
  groups.forEach((g, i) => {
    expect(g.sources.map((s: any) => s.evidenceId), `${g.pipelineId} items = the pack section items`).toEqual(
      pack.wildcardSections[i].items.map((it: any) => it.evidenceId),
    );
    expect(g.queries).toEqual(pipelines[i].queries);
  });
  const distinct = new Set(groups.flatMap((g) => g.sources.map((s: any) => s.evidenceId)));
  const used = new Set(groups.flatMap((g) => g.sources.filter((s: any) => s.usedInScenario).map((s: any) => s.evidenceId)));
  expect(distinct.size, 'summary-kept = distinct evidenceIds over all groups').toBe(run.counts.sourcesKept);
  expect(run.counts.sourcesWithContent).toBeLessThanOrEqual(run.counts.sourcesKept);
  expect(used.size, 'summary-used = distinct evidenceIds with usedInScenario').toBe(run.counts.sourcesUsed);
}

// @trace FR-60
test.describe('FR-60 SOURCES and WHY THESE NEWS? grouped by wildcard', () => {
  test('FR-60 SOURCES lists one group per wildcard with its sources, excerpts, links and marks', async ({ page }) => {
    test.setTimeout(150_000);
    const id = await runPE(page);
    const result = await getJson(page, `/api/runs/${id}/result`);
    const groups: any[] = result.wildcardGroups;
    expect(groups.map((g) => g.pipelineId)).toEqual(['W01', 'W02']);
    expect(groups.map((g) => g.heading)).toEqual(['New pandemic 8/10', 'Energy crisis 3/10']);
    expect(groups[0].sources).toHaveLength(4);
    expect(groups[1].sources).toHaveLength(4);
    expect(result.research.intents).toEqual([]);
    await expectGroupInvariants(page, id, result);

    await expect(page.getByTestId('sources-panel')).toHaveCount(0);
    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('sources-panel')).toBeVisible();
    await expect(page.getByTestId('sources-title')).toHaveText('SOURCES');
    expect(await testids(page, /^source-group-W\d+$/)).toEqual(['source-group-W01', 'source-group-W02']);
    await expect(page.getByTestId('source-group-title-W01')).toHaveText('New pandemic 8/10');
    await expect(page.getByTestId('source-group-title-W02')).toHaveText('Energy crisis 3/10');

    for (const g of groups) {
      const p: string = g.pipelineId;
      const inGroup = await page.getByTestId(`source-group-${p}`).locator('[data-testid^="source-item-"]').evaluateAll((nodes) =>
        nodes.map((n) => n.getAttribute('data-testid')),
      );
      expect(inGroup, `items of ${p} in API order`).toEqual(g.sources.map((s: any) => `source-item-${p}-${s.evidenceId}`));
      for (const s of g.sources) {
        const k = `${p}-${s.evidenceId}`;
        await expect(page.getByTestId(`source-id-${k}`)).toHaveText(s.evidenceId);
        await expect(page.getByTestId(`source-title-${k}`)).toHaveText(s.title || 'Untitled source');
        await expect(page.getByTestId(`source-publisher-${k}`)).toHaveText(s.publisher || 'Unknown publisher');
        await expect(page.getByTestId(`source-date-${k}`)).toHaveText(formatDate(s.publishedAt));
        const link = page.getByTestId(`source-link-${k}`);
        await expect(link).toHaveAttribute('href', s.url);
        await expect(link).toHaveAttribute('target', '_blank');
        await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
        await expect(link).toHaveText('Open source');
        await expect(page.getByTestId(`source-used-${k}`)).toHaveCount(s.usedInScenario ? 1 : 0);
        expect(s.contentRetrieved, `${k} content retrieved on the stub`).toBe(true);
        expect(s.fragments.length).toBeGreaterThan(0);
        await expect(page.getByTestId(`source-excerpt-${k}`)).toBeVisible();
        await expect(page.getByTestId(`source-not-retrieved-${k}`)).toHaveCount(0);
        for (let f = 0; f < s.fragments.length; f++) {
          await expect(page.getByTestId(`source-fragment-${k}-${f}`)).toHaveText(s.fragments[f]);
        }
      }
    }
    expect(await testids(page, /^source-counter-/), 'no counter-signal badge in the grouped layout').toEqual([]);
    expect(await testids(page, /^source-item-E\d+$/), 'no flat item in the grouped layout').toEqual([]);
    await evidence(page, 'FR-60', 'sources-grouped');
  });

  test('FR-60 WHY THESE NEWS? lists the queries per wildcard and five summary numbers; a chip highlights every item of its id; a reload closes the panels', async ({ page }) => {
    test.setTimeout(150_000);
    const resultGets: string[] = [];
    page.on('request', (r) => {
      if (r.method() === 'GET' && /\/api\/runs\/[0-9a-f-]{36}\/result$/.test(r.url())) resultGets.push(r.url());
    });
    const id = await runPE(page);
    const result = await getJson(page, `/api/runs/${id}/result`);
    const run = await getJson(page, `/api/runs/${id}`);
    const groups: any[] = result.wildcardGroups;
    await expectGroupInvariants(page, id, result);

    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('why-news-panel')).toBeVisible();
    await expect(page.getByTestId('why-news-title')).toHaveText('WHY THESE NEWS?');
    expect(await testids(page, /^why-news-group-W\d+$/)).toEqual(['why-news-group-W01', 'why-news-group-W02']);
    expect(await testids(page, /^why-news-query-Q\d+$/)).toEqual(['Q01', 'Q02', 'Q03', 'Q04', 'Q05', 'Q06'].map((q) => `why-news-query-${q}`));
    for (const g of groups) {
      const p: string = g.pipelineId;
      await expect(page.getByTestId(`why-news-group-title-${p}`)).toHaveText(g.heading);
      const own = await page.getByTestId(`why-news-group-${p}`).locator('[data-testid^="why-news-query-Q"]').evaluateAll((nodes) =>
        nodes.map((n) => n.getAttribute('data-testid')).filter((t) => /^why-news-query-Q\d+$/.test(t as string)),
      );
      expect(own).toEqual(g.queries.map((q: any) => `why-news-query-${q.id}`));
      for (const q of g.queries) {
        await expect(page.getByTestId(`why-news-query-text-${q.id}`)).toHaveText(q.text);
        await expect(page.getByTestId(`why-news-query-status-${q.id}`)).toHaveText('OK');
        await expect(page.getByTestId(`why-news-query-count-${q.id}`)).toHaveText(itemsText(q.articlesReturned));
      }
      await expect(page.getByTestId(`why-news-group-sources-${p}`)).toHaveText(plural(g.sources.length, 'source', 'sources'));
      await expect(page.getByTestId(`why-news-group-empty-${p}`)).toHaveCount(0);
    }
    const c = run.counts;
    expect(c.searches).toBe(6);
    await expect(page.getByTestId('summary-searches')).toHaveText(plural(c.searches, 'search performed', 'searches performed'));
    await expect(page.getByTestId('summary-articles')).toHaveText(plural(c.articlesConsidered, 'article considered', 'articles considered'));
    await expect(page.getByTestId('summary-kept')).toHaveText(plural(c.sourcesKept, 'source kept', 'sources kept'));
    await expect(page.getByTestId('summary-content')).toHaveText(plural(c.sourcesWithContent, 'source with content', 'sources with content'));
    await expect(page.getByTestId('summary-used')).toHaveText(plural(c.sourcesUsed, 'source used in the scenario', 'sources used in the scenario'));
    expect(await testids(page, /^summary-/)).toEqual(['summary-searches', 'summary-articles', 'summary-kept', 'summary-content', 'summary-used']);
    for (const legacy of ['why-news-empty', 'summary-events', 'summary-selected', 'summary-counter-signals', 'summary-sources-used']) {
      await expect(page.getByTestId(legacy)).toHaveCount(0);
    }
    expect(await testids(page, /^why-news-(intent|description|drivers|driver)-/)).toEqual([]);
    await evidence(page, 'FR-60', 'why-these-news-grouped');

    // Highlight (FR-26 in the grouped layout): the first evidence id of causal step 1 is the shared stub article E001, listed in both groups
    const e1: string = result.causalChain[0].evidenceIds[0];
    const holding = groups.filter((g) => g.sources.some((s: any) => s.evidenceId === e1)).map((g) => g.pipelineId);
    expect(holding, 'the shared article is in both groups').toEqual(['W01', 'W02']);
    await page.getByTestId('open-why').click();
    const chip = page.getByTestId(`why-evidence-1-${e1}`);
    await expect(chip).toBeEnabled();
    await chip.click();
    await expect(page.getByTestId('sources-panel')).toBeVisible();
    await expect(page.locator('[data-highlighted="true"]')).toHaveCount(holding.length);
    for (const p of holding) await expect(page.getByTestId(`source-item-${p}-${e1}`)).toHaveClass(/highlighted/);
    await expect(page.getByTestId(`source-item-${holding[0]}-${e1}`)).toBeInViewport();
    await evidence(page, 'FR-60', 'highlighted-shared-source');
    await page.waitForTimeout(3500);
    await expect(page.locator('[data-highlighted="true"]')).toHaveCount(0);

    // Reload: both panels closed again, exactly one GET /result per load
    resultGets.length = 0;
    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('sources-panel')).toHaveCount(0);
    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
    expect(resultGets, 'one GET /result per load').toHaveLength(1);
  });

  test('FR-60 mode publisher-fail: every source says "content not retrieved" and no excerpt is shown', async ({ page }) => {
    test.setTimeout(150_000);
    await googleMode(page, 'publisher-fail');
    const id = await runPE(page);
    const result = await getJson(page, `/api/runs/${id}/result`);
    const groups: any[] = result.wildcardGroups;
    expect(groups.flatMap((g) => g.sources)).toHaveLength(8);
    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('sources-panel')).toBeVisible();
    for (const g of groups) {
      for (const s of g.sources) {
        expect(s.contentRetrieved).toBe(false);
        expect(s.fragments).toEqual([]);
        const k = `${g.pipelineId}-${s.evidenceId}`;
        await expect(page.getByTestId(`source-not-retrieved-${k}`)).toHaveText('content not retrieved');
        await expect(page.getByTestId(`source-excerpt-${k}`)).toHaveCount(0);
      }
    }
    expect(await testids(page, /^source-excerpt-|^source-fragment-/)).toEqual([]);
    await evidence(page, 'FR-60', 'sources-content-not-retrieved');
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('summary-content')).toHaveText('0 sources with content');
  });
});

// @trace FR-59
test.describe('FR-59 a wildcard without sources is explicit', () => {
  // @trace FR-59, FR-60
  test('FR-59 / FR-60 mode empty-for W02: the note names Energy crisis, no LOWER REALISM, W02 shows "no current sources found"', async ({ page }) => {
    test.setTimeout(150_000);
    await googleMode(page, 'empty-for', 'W02');
    const id = await runPE(page);
    await expect(page.getByTestId('evidence-note')).toBeVisible();
    await expect(page.getByTestId('evidence-note-message')).toHaveText(MISSING_SENTENCE);
    await expect(page.getByTestId('lower-realism')).toHaveCount(0);
    await evidence(page, 'FR-59', 'missing-wildcard-note');

    const run = await getJson(page, `/api/runs/${id}`);
    expect(run.status).toBe('COMPLETED');
    expect(run.evidenceNote).toEqual({
      kind: 'MISSING_WILDCARD_SOURCES',
      message: MISSING_SENTENCE,
      coreItems: 4,
      coreNeeded: 0,
      wildcardsWithoutSources: ['Energy crisis'],
    });
    expect(run.suggestedRealism ?? null).toBeNull();
    // Google was asked once per planned query and nothing else was (no fallback provider)
    expect(await recorded(page, 'rss')).toHaveLength(6);
    expect((await recorded(page, 'all')).filter((r) => String(r.path).startsWith('/api/v2/doc'))).toHaveLength(0);

    // FR-60 acceptance 4: the empty group says so, the other one is normal
    const result = await getJson(page, `/api/runs/${id}/result`);
    await expectGroupInvariants(page, id, result);
    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('source-group-empty-W02')).toHaveText('no current sources found');
    await expect(page.getByTestId('source-group-empty-W01')).toHaveCount(0);
    expect(await testids(page, /^source-item-W01-E\d+$/)).toHaveLength(4);
    expect(await testids(page, /^source-item-W02-/)).toEqual([]);
    await evidence(page, 'FR-60', 'source-group-empty');
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('why-news-group-empty-W02')).toHaveText('no current sources found');
    for (const q of ['Q04', 'Q05', 'Q06']) {
      await expect(page.getByTestId(`why-news-query-status-${q}`)).toHaveText('EMPTY');
      await expect(page.getByTestId(`why-news-query-count-${q}`)).toHaveText('0 items');
    }
    for (const q of ['Q01', 'Q02', 'Q03']) await expect(page.getByTestId(`why-news-query-status-${q}`)).toHaveText('OK');
    await expect(page.getByTestId('why-news-group-sources-W01')).toHaveText('4 sources');
    await expect(page.getByTestId('why-news-group-empty-W01')).toHaveCount(0);
  });

  test('FR-59 mode empty-for W02 at Realism 10: the missing sentence prefixes the insufficiency sentence and LOWER REALISM shows', async ({ page }) => {
    test.setTimeout(150_000);
    await googleMode(page, 'empty-for', 'W02');
    const id = await runPE(page, PE10);
    const message = `${MISSING_SENTENCE} Realism 10 couldn't be fully met: only 4 core evidence items (needs 5). This future is less grounded.`;
    await expect(page.getByTestId('evidence-note-message')).toHaveText(message);
    await expect(page.getByTestId('lower-realism')).toBeVisible();
    await evidence(page, 'FR-59', 'missing-wildcard-lower-realism');
    const run = await getJson(page, `/api/runs/${id}`);
    expect(run.evidenceNote).toEqual({
      kind: 'INSUFFICIENT_EVIDENCE',
      message,
      coreItems: 4,
      coreNeeded: 5,
      wildcardsWithoutSources: ['Energy crisis'],
    });
    expect(run.suggestedRealism).toBe(8);
    expect(await recorded(page, 'rss')).toHaveLength(6);
    expect((await recorded(page, 'all')).filter((r) => String(r.path).startsWith('/api/v2/doc'))).toHaveLength(0);
  });

  test('FR-59 mode empty: "No sources", the NO_EVIDENCE note, and WHY THESE NEWS? still lists both groups with EMPTY queries', async ({ page }) => {
    test.setTimeout(150_000);
    await googleMode(page, 'empty');
    const id = await runPE(page);
    await expect(page.getByTestId('evidence-note-message')).toHaveText(NO_EVIDENCE);
    await expect(page.getByTestId('lower-realism')).toHaveCount(0);
    const run = await getJson(page, `/api/runs/${id}`);
    expect(run.evidenceNote).toEqual({ kind: 'NO_EVIDENCE', message: NO_EVIDENCE, coreItems: 0, coreNeeded: 0 });
    expect(await recorded(page, 'rss')).toHaveLength(6);
    expect((await recorded(page, 'all')).filter((r) => String(r.path).startsWith('/api/v2/doc'))).toHaveLength(0);

    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('sources-empty')).toHaveText('No sources');
    expect(await testids(page, /^source-group/)).toEqual([]);
    await evidence(page, 'FR-59', 'sources-empty');
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('why-news-panel')).toBeVisible();
    await expect(page.getByTestId('why-news-group-W02')).toBeVisible();
    expect(await testids(page, /^why-news-group-W\d+$/)).toEqual(['why-news-group-W01', 'why-news-group-W02']);
    for (const p of ['W01', 'W02']) await expect(page.getByTestId(`why-news-group-empty-${p}`)).toHaveText('no current sources found');
    for (const q of ['Q01', 'Q02', 'Q03', 'Q04', 'Q05', 'Q06']) {
      await expect(page.getByTestId(`why-news-query-status-${q}`)).toHaveText('EMPTY');
      await expect(page.getByTestId(`why-news-query-count-${q}`)).toHaveText('0 items');
    }
    await expect(page.getByTestId('summary-kept')).toHaveText('0 sources kept');
    await expect(page.getByTestId('summary-used')).toHaveText('0 sources used in the scenario');
    const result = await getJson(page, `/api/runs/${id}/result`);
    expect(result.sources).toEqual([]);
    expect(result.wildcardGroups.map((g: any) => g.sources)).toEqual([[], []]);
  });

  test('FR-59 mode down: every query is FAILED, the note is NO_EVIDENCE and the run completes', async ({ page }) => {
    test.setTimeout(150_000);
    await googleMode(page, 'down');
    const id = await runPE(page);
    await expect(page.getByTestId('evidence-note-message')).toHaveText(NO_EVIDENCE);
    const run = await getJson(page, `/api/runs/${id}`);
    expect(run.status).toBe('COMPLETED');
    expect(run.evidenceNote.kind).toBe('NO_EVIDENCE');
    expect(await recorded(page, 'rss')).toHaveLength(6);
    await page.getByTestId('open-why-news').click();
    for (const q of ['Q01', 'Q02', 'Q03', 'Q04', 'Q05', 'Q06']) {
      await expect(page.getByTestId(`why-news-query-status-${q}`)).toHaveText('FAILED');
      await expect(page.getByTestId(`why-news-query-count-${q}`)).toHaveText('0 items');
    }
    await evidence(page, 'FR-59', 'queries-failed');
  });
});
