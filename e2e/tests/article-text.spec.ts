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

/** NFR-10 acceptance 2: three wildcards. */
const THREE = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [
    { wildcardId: 'biology-new-pandemic', intensity: 8 },
    { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
    { wildcardId: 'energy-energy-crisis', intensity: 5 },
  ],
  customWildcards: [],
  output: { story: true, illustration: false },
};

/** The paragraphs of the stub's publisher page (wildcard-search.md FR-61): the generic ones and the ones that carry the match text. */
const P1 = 'Opening paragraph of this publisher page. It introduces the report in plain words for every reader.';
const p2 = (m: string) => `${m} is the subject of this second paragraph, which gives the details the reader asked about.`;
const p4 = (m: string) => `Further details on ${m} follow in the fourth paragraph, with dates, names and a short quote.`;
const CHROME_TEXTS = ['NAVIGATION TEXT', 'HEADER TEXT', 'FOOTER TEXT', 'SCRIPT TEXT', 'STYLE TEXT'];

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

async function startRun(page: Page, body: object): Promise<string> {
  const res = await page.request.post('/api/runs', { data: body });
  expect(res.status(), await res.text()).toBe(202);
  return (await res.json()).id;
}

async function awaitStatus(page: Page, id: string, wanted: string, timeout: number) {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout, intervals: [500] })
    .toBe(wanted);
  return (await page.request.get(`/api/runs/${id}`)).json();
}

async function runAndWait(page: Page, body: object = A, timeout = 60_000): Promise<{ id: string; run: any }> {
  const id = await startRun(page, body);
  return { id, run: await awaitStatus(page, id, 'COMPLETED', timeout) };
}

async function recorded(page: Page, kind: 'responses' | 'rss' | 'google-page' | 'decode' | 'article' | 'all'): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

const purposeOf = (request: any): string => /ORACUL REQUEST ([A-Z_]+)/.exec(JSON.stringify(request))?.[1] ?? '';

async function sourcesOf(page: Page, id: string): Promise<any[]> {
  const res = await page.request.get(`/api/runs/${id}/sources`);
  expect(res.status()).toBe(200);
  return (await res.json()).items;
}

async function packOf(page: Page, id: string): Promise<any> {
  const res = await page.request.get(`/api/runs/${id}/evidence-pack`);
  expect(res.status()).toBe(200);
  return res.json();
}

const excerptsOf = (source: any) => source.excerpts as { pipelineId: string; fragments: string[] }[];

/** Invariants of every run (article-retrieval.md range (j)). */
function expectInvariants(run: any, sources: any[]): void {
  let retrieved = 0;
  for (const s of sources) {
    const excerpts = excerptsOf(s);
    expect(excerpts, `${s.id} excerpts present`).toBeDefined();
    expect(excerpts.length > 0, `${s.id}: RETRIEVED iff excerpts`).toBe(s.contentStatus === 'RETRIEVED');
    expect(s.metadataFetched, `${s.id} metadataFetched`).toBe(s.contentStatus === 'RETRIEVED' || s.contentStatus === 'NO_TEXT');
    expect('publisherHost' in s, `${s.id}: publisherHost present iff the url is not the Google link`).toBe(!s.url.startsWith('http://stub:4010/rss/articles/'));
    expect(new URL(s.url).hostname).not.toMatch(/(^|\.)(google\.com|gstatic\.com|googleusercontent\.com)$/);
    for (const e of excerpts) {
      expect(s.pipelineIds).toContain(e.pipelineId);
      expect(e.fragments.length).toBeGreaterThan(0);
      expect(e.fragments.length).toBeLessThanOrEqual(3);
      expect(e.fragments.join('').length).toBeLessThanOrEqual(1200);
    }
    if (s.contentStatus === 'RETRIEVED') retrieved += 1;
  }
  expect(new Set(sources.map((s) => s.url)).size, 'stored urls are distinct').toBe(sources.length);
  expect(run.counts.sourcesWithContent).toBe(retrieved);
  expect(run.counts.sourcesKept).toBe(sources.length);
}

// @trace FR-54
// @trace FR-55
// @trace FR-61
test.describe('FR-54 / FR-55 / FR-61 Publisher article text through the Google-like stub', () => {
  test('mode ok: every publisher page of the acceptance run is read and its fragments are kept per wildcard', async ({ page }) => {
    test.setTimeout(120_000);
    await connect(page);
    const { id, run } = await runAndWait(page);
    expect(run.counts).toMatchObject({ searches: 6, articlesRetrieved: 30, articlesConsidered: 25, sourcesKept: 7, sourcesWithContent: 7 });

    const sources = await sourcesOf(page, id);
    expect(sources).toHaveLength(7);
    const research = await (await page.request.get(`/api/runs/${id}/research`)).json();
    const queryTexts: string[] = research.searchPlan.pipelines.flatMap((p: any) => p.queries.map((q: any) => q.text));
    for (const [k, s] of sources.entries()) {
      expect(s.id).toBe(`S${String(k + 1).padStart(3, '0')}`);
      expect(s.contentStatus, s.id).toBe('RETRIEVED');
      expect(s.publisherHost, s.id).toBe('stub');
      expect(s.metadataFetched, s.id).toBe(true);
      expect(s.url, s.id).toMatch(/^http:\/\/stub:4010\/articles\//);
      const name = s.url.split('/').pop();
      expect(s.summary, s.id).toBe(`Summary of ${name}`);
      if (k === 0) {
        expect(s.url).toBe('http://stub:4010/articles/shared');
        expect(excerptsOf(s)).toEqual([
          { pipelineId: 'W01', fragments: [P1] },
          { pipelineId: 'W02', fragments: [P1] },
        ]);
      } else {
        const pipeline = k <= 3 ? 'W01' : 'W02';
        const q = queryTexts[Number(s.queryIds[0].slice(1)) - 1];
        expect(q).toMatch(/^W0[12] stub query [123]$/);
        expect(excerptsOf(s), s.id).toEqual([{ pipelineId: pipeline, fragments: [p2(q), p4(q)] }]);
      }
      for (const e of excerptsOf(s)) for (const f of e.fragments) for (const chrome of CHROME_TEXTS) expect(f).not.toContain(chrome);
    }

    // what the stub saw: 7 decodes, 14 Google page requests (7 redirects + 7 pages), 7 publisher requests
    const decodes = await recorded(page, 'decode');
    expect(decodes).toHaveLength(7);
    for (const d of decodes) {
      expect(String(d.ts)).toBe('1759737600');
      expect(d.sg).toBe(`sig-${d.id}`);
      expect(d.contentType).toBe('application/x-www-form-urlencoded;charset=UTF-8');
      expect(d.status).toBe(200);
    }
    const pages = await recorded(page, 'google-page');
    expect(pages).toHaveLength(14);
    expect(pages.filter((p) => p.step === 'redirect')).toHaveLength(7);
    expect(pages.filter((p) => p.step === 'page')).toHaveLength(7);
    expect(await recorded(page, 'article')).toHaveLength(7);

    // the pack: 14 Excerpt lines and no snippet form
    const lines: string[] = (await packOf(page, id)).promptText.split('\n');
    expect(lines.filter((l) => l.startsWith('Excerpt: '))).toHaveLength(14);
    expect(lines.filter((l) => l.startsWith('Content not retrieved'))).toHaveLength(0);
    expectInvariants(run, sources);
  });

  test('mode decode-fail: every source keeps its Google link, the run completes, the pack is in snippet form and no extra ChatGPT call is made', async ({ page }) => {
    test.setTimeout(180_000);
    await connect(page);
    const first = await runAndWait(page);
    const okPurposes = (await recorded(page, 'responses')).map(purposeOf);
    const articlesBefore = (await recorded(page, 'article')).length;

    expect((await page.request.post(`${STUB}/__control/google`, { data: { mode: 'decode-fail' } })).status()).toBe(204);
    const { id, run } = await runAndWait(page);
    expect(id).not.toBe(first.id);
    expect(run.status).toBe('COMPLETED');
    const sources = await sourcesOf(page, id);
    expect(sources).toHaveLength(7);
    for (const s of sources) {
      expect(s.contentStatus).toBe('DECODE_FAILED');
      expect(s.url).toMatch(/^http:\/\/stub:4010\/rss\/articles\//);
      expect(s).not.toHaveProperty('publisherHost');
      expect(s.metadataFetched).toBe(false);
      expect(s.excerpts).toEqual([]);
      expect(s.publisher).toBe('Reuters');
    }
    expect(sources[0].url).toBe('http://stub:4010/rss/articles/shared');
    expect(sources[0].summary).toBe('Shared stub article - Reuters Reuters');
    expect(run.counts.sourcesWithContent).toBe(0);
    expect((await recorded(page, 'article')).length, 'no publisher page was requested for the second run').toBe(articlesBefore);
    const lines: string[] = (await packOf(page, id)).promptText.split('\n');
    expect(lines.filter((l) => l.startsWith('Excerpt: '))).toHaveLength(0);
    expect(lines.filter((l) => l.startsWith('Content not retrieved. Snippet: '))).toHaveLength(8); // one line per pack item: W01 S001-S004, W02 S001, S005-S007
    expectInvariants(run, sources);

    // FR-55: extraction is no ChatGPT call: the same multiset of purposes for a run with and without extracted text
    const all = (await recorded(page, 'responses')).map(purposeOf);
    const secondPurposes = all.slice(okPurposes.length);
    expect([...secondPurposes].sort()).toEqual([...okPurposes].sort());
  });

  test('mode decode-google-host: a Google URL in the decode answer is never the article', async ({ page }) => {
    test.setTimeout(120_000);
    expect((await page.request.post(`${STUB}/__control/google`, { data: { mode: 'decode-google-host' } })).status()).toBe(204);
    await connect(page);
    const { id, run } = await runAndWait(page);
    const sources = await sourcesOf(page, id);
    expect(sources).toHaveLength(7);
    for (const s of sources) {
      expect(s.contentStatus).toBe('DECODE_FAILED');
      expect(s).not.toHaveProperty('publisherHost');
      expect(s.url).toMatch(/^http:\/\/stub:4010\/rss\/articles\//);
    }
    expect(await recorded(page, 'article')).toHaveLength(0);
    expectInvariants(run, sources);
  });

  for (const mode of ['publisher-fail', 'publisher-timeout']) {
    test(`mode ${mode}: every source keeps the publisher url with PAGE_FAILED and the run completes`, async ({ page }) => {
      test.setTimeout(150_000);
      expect((await page.request.post(`${STUB}/__control/google`, { data: { mode } })).status()).toBe(204);
      await connect(page);
      const { id, run } = await runAndWait(page, A, 90_000);
      const sources = await sourcesOf(page, id);
      expect(sources).toHaveLength(7);
      for (const s of sources) {
        expect(s.contentStatus).toBe('PAGE_FAILED');
        expect(s.url).toMatch(/^http:\/\/stub:4010\/articles\//);
        expect(s.publisherHost).toBe('stub');
        expect(s.metadataFetched).toBe(false);
        expect(s.excerpts).toEqual([]);
      }
      expect(run.counts.sourcesWithContent).toBe(0);
      expectInvariants(run, sources);
    });
  }

  test('mode rate-limited-once: the first search is retried once and every query is OK', async ({ page }) => {
    test.setTimeout(120_000);
    expect((await page.request.post(`${STUB}/__control/google`, { data: { mode: 'rate-limited-once' } })).status()).toBe(204);
    await connect(page);
    const { id } = await runAndWait(page);
    expect(await recorded(page, 'rss'), '6 queries and one retry').toHaveLength(7);
    const research = await (await page.request.get(`/api/runs/${id}/research`)).json();
    for (const q of research.searchPlan.pipelines.flatMap((p: any) => p.queries)) expect(q.status).toBe('OK');
  });

  test('mode empty-for W02: the three queries of W02 are EMPTY, the four sources belong to W01 only', async ({ page }) => {
    test.setTimeout(120_000);
    expect((await page.request.post(`${STUB}/__control/google`, { data: { mode: 'empty-for', term: 'W02' } })).status()).toBe(204);
    await connect(page);
    const { id, run } = await runAndWait(page);
    const research = await (await page.request.get(`/api/runs/${id}/research`)).json();
    const [w1, w2] = research.searchPlan.pipelines;
    for (const q of w1.queries) expect(q.status).toBe('OK');
    for (const q of w2.queries) expect(q.status).toBe('EMPTY');
    const sources = await sourcesOf(page, id);
    expect(sources).toHaveLength(4);
    for (const s of sources) expect(s.pipelineIds).toEqual(['W01']);
    expect(w2.sourceIds).toEqual([]);
    expectInvariants(run, sources);
  });
});

// @trace FR-54
// @trace FR-61
test.describe('NFR-10 part 3: three wildcards finish fast on the stub', () => {
  test('three wildcards in mode ok: 9 searches, 10 RETRIEVED sources, the run under 30 s and the retrieval within 10 s of the first model call', async ({ page }) => {
    test.setTimeout(120_000);
    await connect(page);
    const { id, run } = await runAndWait(page, THREE, 90_000);
    expect(run.status).toBe('COMPLETED');
    expect(await recorded(page, 'rss')).toHaveLength(9);
    const sources = await sourcesOf(page, id);
    expect(sources).toHaveLength(10);
    for (const s of sources) expect(s.contentStatus, s.id).toBe('RETRIEVED');
    expect(run.counts.sourcesWithContent).toBe(10);
    // 10 s plus 10 stages paced to 2 s each
    expect(new Date(run.completedAt).getTime() - new Date(run.createdAt).getTime()).toBeLessThan(30_000);
    const all = await recorded(page, 'all');
    const firstModelCall = all.find((r) => r.method === 'POST' && r.path === '/v1/responses');
    const lastArticle = [...all].reverse().find((r) => String(r.path).startsWith('/articles/'));
    expect(firstModelCall, 'a first POST /v1/responses').toBeTruthy();
    expect(lastArticle, 'a last /articles/ request').toBeTruthy();
    expect(lastArticle.at - firstModelCall.at).toBeLessThan(10_000);
    expectInvariants(run, sources);
  });
});
