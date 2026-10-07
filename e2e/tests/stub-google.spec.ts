import { expect, test, type APIRequestContext } from '@playwright/test';

const STUB = 'http://localhost:4010';

/**
 * wildcard-search.md FR-61 "E2E stub answers like real Google": API-level checks of the Docker stub (the in-process twin is
 * StubNewsTest): the q classes, the control modes and `reset`, the Google page, the decode answer and the publisher page.
 */
test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
});

const items = (xml: string): number => (xml.match(/<item>/g) ?? []).length;

async function search(request: APIRequestContext, q: string): Promise<{ status: number; body: string }> {
  const res = await request.get(`${STUB}/rss/search`, { params: { q, hl: 'en-US', gl: 'US', ceid: 'US:en' } });
  return { status: res.status(), body: await res.text() };
}

async function control(request: APIRequestContext, data: unknown) {
  return request.post(`${STUB}/__control/google`, { data: data as object });
}

/** The f.req value of the Google decode call for (id, ts, sg), exactly as the backend sends it. */
function fReq(id: string, ts: number, sg: string): string {
  const inner = `["garturlreq",[["X","X",["X","X"],null,null,1,1,"US:en",null,1,null,null,null,null,null,0,1],"X","X",1,[1,1,1],1,1,null,0,0,null,0],${JSON.stringify(id)},${ts},${JSON.stringify(sg)}]`;
  return `[[["Fbv4je",${JSON.stringify(inner)},null,"generic"]]]`;
}

async function decode(request: APIRequestContext, id: string, sg: string) {
  return request.post(`${STUB}/_/DotsSplashUi/data/batchexecute`, { form: { 'f.req': fReq(id, 1759737600, sg) } });
}

const Q_CLASSES: [string, number][] = [
  ['vaccines when:90d', 5],
  ['energy crisis supply shortage warnings when:90d', 5],
  ['"mRNA vaccine approval" when:90d', 5],
  ['fusion OR fission when:90d', 10],
  ['(fusion OR fission) when:90d', 0],
  ['(energy crisis OR geopolitical fragmentation) when:90d', 0],
  ['"mRNA vaccine" OR "fusion plant" when:90d', 0],
  ['energy crisis OR fusion when:90d', 0],
  ['vaccines', 5],
];

// @trace FR-61
test.describe('FR-61 Stub q classes, control modes and Google page shapes', () => {
  for (const [q, expected] of Q_CLASSES) {
    test(`q [${q}] answers ${expected} items`, async ({ request }) => {
      const r = await search(request, q);
      expect(r.status).toBe(200);
      expect(items(r.body)).toBe(expected);
    });
  }

  test('the feed items have the shape of real Google: shared article, keyed own articles, description with markup', async ({ request }) => {
    const r = await search(request, 'vaccines when:90d');
    expect(r.body).toContain('Shared stub article - Reuters');
    expect(r.body).toMatch(/http:\/\/stub:4010\/rss\/articles\/shared\?utm_source=\d+/);
    expect(r.body).toContain('<source url="https://www.reuters.com">Reuters</source>');
    expect(r.body).toMatch(/<link>http:\/\/stub:4010\/rss\/articles\/[0-9a-f]{8}-[2-5]<\/link>/g);
    expect(r.body).toContain('<description>');
    expect(r.body).toContain('&lt;a href=');
  });

  const MODES: { name: string; body: object; check: (request: APIRequestContext) => Promise<void> }[] = [
    { name: 'empty', body: { mode: 'empty' }, check: async (rq) => expect(items((await search(rq, 'vaccines when:90d')).body)).toBe(0) },
    { name: 'down', body: { mode: 'down' }, check: async (rq) => expect((await search(rq, 'vaccines when:90d')).status).toBe(503) },
    {
      name: 'malformed',
      body: { mode: 'malformed' },
      check: async (rq) => {
        const r = await search(rq, 'vaccines when:90d');
        expect(r.status).toBe(200);
        expect(r.body).toContain('<item><title>broken');
      },
    },
    {
      name: 'empty-for',
      body: { mode: 'empty-for', term: 'W02' },
      check: async (rq) => {
        expect(items((await search(rq, 'W02 stub query 1 when:90d')).body)).toBe(0);
        expect(items((await search(rq, 'W01 stub query 1 when:90d')).body)).toBe(5);
      },
    },
    {
      name: 'rate-limited-once',
      body: { mode: 'rate-limited-once' },
      check: async (rq) => {
        expect((await search(rq, 'vaccines when:90d')).status).toBe(429);
        expect(items((await search(rq, 'vaccines when:90d')).body)).toBe(5);
      },
    },
    {
      name: 'slow',
      body: { mode: 'slow', ms: 500 },
      check: async (rq) => {
        const t0 = Date.now();
        expect((await search(rq, 'vaccines when:90d')).status).toBe(200);
        expect(Date.now() - t0).toBeGreaterThanOrEqual(450);
      },
    },
    { name: 'decode-fail', body: { mode: 'decode-fail' }, check: async (rq) => expect((await decode(rq, 'abc', 'sig-abc')).status()).toBe(500) },
    {
      name: 'decode-google-host',
      body: { mode: 'decode-google-host' },
      check: async (rq) => {
        const r = await decode(rq, 'abc', 'sig-abc');
        expect(r.status()).toBe(200);
        expect(await r.text()).toContain('https://news.google.com/rss/articles/abc');
      },
    },
    { name: 'publisher-fail', body: { mode: 'publisher-fail' }, check: async (rq) => expect((await rq.get(`${STUB}/articles/abc`)).status()).toBe(503) },
    {
      name: 'publisher-timeout',
      body: { mode: 'publisher-timeout' },
      check: async (rq) => {
        await expect(rq.get(`${STUB}/articles/abc`, { timeout: 3000 })).rejects.toThrow();
      },
    },
  ];

  for (const m of MODES) {
    test(`control mode ${m.name}: 204 and its effect, reset returns to ok`, async ({ request }) => {
      expect((await control(request, m.body)).status()).toBe(204);
      await m.check(request);
      expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
      expect(items((await search(request, 'vaccines when:90d')).body), 'reset is mode ok').toBe(5);
      expect((await decode(request, 'abc', 'sig-abc')).status()).toBe(200);
      expect((await request.get(`${STUB}/articles/abc`)).status()).toBe(200);
      for (const kind of ['rss', 'google-page', 'decode', 'article']) {
        // the checks above made records; the reset in the next test starts clean, and the kinds exist
        const res = await request.get(`${STUB}/__control/requests?kind=${kind}`);
        expect(res.status(), kind).toBe(200);
      }
    });
  }

  test('reset clears every record list', async ({ request }) => {
    await search(request, 'vaccines when:90d');
    await request.get(`${STUB}/rss/articles/abc?hl=en-US`);
    await decode(request, 'abc', 'sig-abc');
    await request.get(`${STUB}/articles/abc`);
    expect((await request.post(`${STUB}/__control/reset`)).status()).toBe(204);
    for (const kind of ['rss', 'google-page', 'decode', 'article', 'all']) {
      const res = await request.get(`${STUB}/__control/requests?kind=${kind}`);
      expect((await res.json()).requests, kind).toEqual([]);
    }
  });

  test('unknown mode, a body that is not JSON and empty-for without a term are 400; /__control/rss is 404', async ({ request }) => {
    const unknown = await control(request, { mode: 'sideways' });
    expect(unknown.status()).toBe(400);
    expect(await unknown.json()).toEqual({ error: 'unknown_mode' });
    const notJson = await request.post(`${STUB}/__control/google`, { data: 'not json', headers: { 'content-type': 'application/json' } });
    expect(notJson.status()).toBe(400);
    expect(await notJson.json()).toEqual({ error: 'unknown_mode' });
    const noTerm = await control(request, { mode: 'empty-for' });
    expect(noTerm.status()).toBe(400);
    expect(await noTerm.json()).toEqual({ error: 'term_required' });
    const former = await request.post(`${STUB}/__control/rss`, { data: { mode: 'ok' } });
    expect(former.status()).toBe(404);
    expect(await former.json()).toEqual({ error: 'not_found' });
    const kind = await request.get(`${STUB}/__control/requests?kind=sideways`);
    expect(kind.status()).toBe(400);
    expect(await kind.json()).toEqual({ error: 'unknown_kind' });
  });

  test('the Google page is a 302 with hl, then a page with exactly one element carrying the three attributes', async ({ request }) => {
    const redirect = await request.get(`${STUB}/rss/articles/abc?oc=5`, { maxRedirects: 0 });
    expect(redirect.status()).toBe(302);
    expect(redirect.headers()['location']).toBe('/rss/articles/abc?oc=5&hl=en-US&gl=US&ceid=US:en');
    const page = await request.get(`${STUB}/rss/articles/abc?oc=5&hl=en-US&gl=US&ceid=US:en`, { maxRedirects: 0 });
    expect(page.status()).toBe(200);
    expect(page.headers()['content-type']).toContain('text/html');
    const html = await page.text();
    expect(html.match(/data-n-a-id=/g) ?? []).toHaveLength(1);
    expect(html).toContain('data-n-a-id="abc"');
    expect(html).toContain('data-n-a-ts="1759737600"');
    expect(html).toContain('data-n-a-sg="sig-abc"');
    expect(html).toContain('og:site_name');
    const recordedPages = (await (await request.get(`${STUB}/__control/requests?kind=google-page`)).json()).requests;
    expect(recordedPages.map((p: any) => [p.id, p.step])).toEqual([
      ['abc', 'redirect'],
      ['abc', 'page'],
    ]);
  });

  test('the decode answer carries the structured publisher URL, a wrong signature is 400, the publisher page has nav, script, style and six paragraphs', async ({ request }) => {
    const ok = await decode(request, 'abc', 'sig-abc');
    expect(ok.status()).toBe(200);
    expect(ok.headers()['content-type']).toContain('application/json');
    const body = await ok.text();
    expect(body.startsWith(")]}'\n\n[[\"wrb.fr\",\"Fbv4je\",")).toBe(true);
    const outer = JSON.parse(body.slice(body.indexOf('[')));
    expect(JSON.parse(outer[0][2])).toEqual(['garturlres', 'http://stub:4010/articles/abc', 1]);
    expect((await decode(request, 'abc', 'sig-x')).status()).toBe(400);
    const decodes = (await (await request.get(`${STUB}/__control/requests?kind=decode`)).json()).requests;
    expect(decodes.map((d: any) => d.status)).toEqual([200, 400]);

    const pub = await request.get(`${STUB}/articles/abc`);
    expect(pub.status()).toBe(200);
    expect(pub.headers()['content-type']).toContain('text/html');
    const html = await pub.text();
    for (const part of ['<nav>', '<script>', '<style>', 'NAVIGATION TEXT', 'FOOTER TEXT', 'SCRIPT TEXT', 'STYLE TEXT']) expect(html).toContain(part);
    const article = /<article>([\s\S]*?)<\/article>/.exec(html)?.[1] ?? '';
    expect(article.match(/<p>/g) ?? []).toHaveLength(6);
  });
});
