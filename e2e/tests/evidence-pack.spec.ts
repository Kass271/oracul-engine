import { expect, test, type Page } from '@playwright/test';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

async function beforeEachReset(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  const events = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } });
  expect(events.status()).toBe(204);
}

test.beforeEach(async ({ page }) => {
  await beforeEachReset(page);
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

async function listEvents(page: Page, id: string): Promise<any[]> {
  const res = await page.request.get(`/api/runs/${id}/events`);
  expect(res.status()).toBe(200);
  return (await res.json()).items;
}

async function getPack(page: Page, id: string): Promise<any> {
  const res = await page.request.get(`/api/runs/${id}/evidence-pack`);
  expect(res.status()).toBe(200);
  return res.json();
}

async function listSources(page: Page, id: string): Promise<any[]> {
  const res = await page.request.get(`/api/runs/${id}/sources`);
  expect(res.status()).toBe(200);
  return (await res.json()).items;
}

async function recorded(page: Page): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=responses`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

function inputText(request: any): string {
  return (request.input ?? [])
    .flatMap((m: any) => (Array.isArray(m.content) ? m.content : []))
    .map((c: any) => c.text ?? '')
    .join('\n');
}

/** Content of the named ORACUL_UNTRUSTED_DATA block ('' when missing). */
function block(text: string, name: string): string {
  const open = `<<<ORACUL_UNTRUSTED_DATA name="${name}">>>`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const from = start + open.length + (text[start + open.length] === '\n' ? 1 : 0);
  const end = text.indexOf('<<<END_ORACUL_UNTRUSTED_DATA>>>', from);
  return end < 0 ? '' : text.slice(from, end).trimEnd();
}

function ids(section: any): string[] {
  return section.items.map((i: any) => i.evidenceId);
}

/** The sections of a pack without the fields that depend on the time of the run. */
function stable(pack: any): any[] {
  return pack.wildcardSections.map((s: any) => ({
    ...s,
    items: s.items.map(({ publishedAt: _publishedAt, ...rest }: any) => rest),
  }));
}

async function runToCompletion(page: Page): Promise<{ id: string; run: any; pack: any }> {
  const id = await startAcceptanceRun(page);
  const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
  return { id, run, pack: await getPack(page, id) };
}

// @trace FR-16
// @trace FR-17
// @trace FR-18
// @trace FR-46
// @trace FR-50
// @trace FR-53
// @trace FR-57
// @trace FR-55
test.describe('FR-16 / FR-17 / FR-18 / FR-57 Ranking and the Evidence Pack grouped by wildcard', () => {
  test('FR-57 / FR-55 acceptance run: one section per wildcard, shared article E001 in both, an Excerpt for every item', async ({ page }) => {
    test.setTimeout(90_000);
    const { id, run, pack } = await runToCompletion(page);
    // FR-53: 7 kept sources (W01 4, W02 4 with the shared article); the pack lists every kept source
    expect(run.counts.sourcesKept).toBe(7);
    expect(run.counts.eventsSelected).toBe(7);
    expect(run.counts.counterSignals).toBe(0);
    expect(run.evidencePackId).toBeTruthy();
    expect(pack.id).toBe(run.evidencePackId);
    expect(pack.generationId).toBe(run.generationId);
    expect(pack.core).toEqual([]);
    expect(pack.supporting).toEqual([]);
    expect(pack.counterSignals).toEqual([]);

    const [w1, w2] = pack.wildcardSections;
    expect(pack.wildcardSections).toHaveLength(2);
    expect([w1.pipelineId, w1.kind, w1.label, w1.level, w1.heading]).toEqual(['W01', 'CATALOGUE', 'New pandemic', 8, 'New pandemic 8/10']);
    expect([w2.pipelineId, w2.kind, w2.label, w2.level, w2.heading]).toEqual([
      'W02',
      'CATALOGUE',
      'Humanoid robot boom',
      6,
      'Humanoid robot boom 6/10',
    ]);
    expect(ids(w1)).toEqual(['E001', 'E002', 'E003', 'E004']);
    expect(ids(w2)).toEqual(['E001', 'E005', 'E006', 'E007']);
    expect(w2.items[0]).toEqual(w1.items[0]);

    // every item is the listRunSources source of its number; the publisher pages are read (FR-54 / FR-55), so every item has fragments and no snippet
    const fragmentsOf = (source: any, pipelineId: string): string[] =>
      source.excerpts.find((e: any) => e.pipelineId === pipelineId)?.fragments ?? [];
    const sources = await listSources(page, id);
    expect(pack.sources).toEqual(sources);
    expect(sources).toHaveLength(7);
    for (const item of [...w1.items, ...w2.items]) {
      const source = sources.find((s) => s.id === item.sourceId);
      expect(source, `source ${item.sourceId}`).toBeTruthy();
      expect(item.evidenceId).toBe(`E${item.sourceId.slice(1)}`);
      expect(item.title).toBe(source.title);
      expect(item.publisher).toBe(source.publisher);
      expect(item.url).toBe(source.url);
      expect(item.contentRetrieved).toBe(true);
      expect(item.fragments.length).toBeGreaterThan(0);
      expect(item.snippet).toBeUndefined();
    }
    // the fragments of an item are the excerpts of its pipeline: the shared article differs per pipeline only by pipeline, S002-S007 hold two paragraphs
    for (const item of w1.items) expect(item.fragments).toEqual(fragmentsOf(sources.find((s) => s.id === item.sourceId), 'W01'));
    for (const item of w2.items) expect(item.fragments).toEqual(fragmentsOf(sources.find((s) => s.id === item.sourceId), 'W02'));
    expect(w1.items[0].fragments).toEqual(['Opening paragraph of this publisher page. It introduces the report in plain words for every reader.']);
    for (const item of [...w1.items.slice(1), ...w2.items.slice(1)]) expect(item.fragments).toHaveLength(2);

    // no event has a selection any more
    const events = await listEvents(page, id);
    expect(events.length).toBeGreaterThan(0);
    for (const e of events) expect(e.selection).toBeUndefined();

    // the prompt text: sections by wildcard, 14 Excerpt lines (E001 twice with one, six more sources with two), no snippet line, no legacy sections
    const text: string = pack.promptText;
    expect(text.startsWith(`ORACUL EVIDENCE PACK\nGeneration: ${run.generationId}\nCutoff: `)).toBe(true);
    for (const part of [
      'Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years',
      'New pandemic: 8 | Humanoid robot boom: 6',
      'Wildcard: New pandemic 8/10\n[E001] ',
      'Wildcard: Humanoid robot boom 6/10\n[E001] ',
    ]) {
      expect(text).toContain(part);
    }
    const lines = text.split('\n');
    expect(lines.filter((l) => l.startsWith('Content not retrieved. Snippet: '))).toHaveLength(0);
    expect(lines.filter((l) => l.startsWith('Excerpt: '))).toHaveLength(14);
    expect(lines.filter((l) => l.startsWith('[E001] '))).toHaveLength(2);
    expect(lines.filter((l) => l.startsWith('[E001] '))[0]).toBe(lines.filter((l) => l.startsWith('[E001] '))[1]);
    expect(text).not.toContain('CORE EVIDENCE');
    expect(text).not.toContain('SUPPORTING EVIDENCE');
    expect(text).not.toContain('COUNTER-SIGNALS');
    expect(text).not.toContain('<<<');

    // the evidence-pack block of the scenario request is exactly the promptText
    const generation = (await recorded(page)).filter((r) => /ORACUL REQUEST SCENARIO_GENERATION/.test(inputText(r)));
    expect(generation).toHaveLength(1);
    expect(block(inputText(generation[0]), 'evidence-pack')).toBe(text);
  });

  test('FR-57 the classification no longer changes the pack: events modes ok and evidence give the same wildcardSections', async ({ page }) => {
    test.setTimeout(150_000);
    const first = await runToCompletion(page);
    expect(first.run.counts.eventsSelected).toBe(7);

    await beforeEachReset(page);
    await page.context().clearCookies(); // second run in one test: start from a fresh, not connected session
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'evidence' } });
    expect(mode.status()).toBe(204);
    const second = await runToCompletion(page);
    expect(second.run.counts.sourcesKept).toBe(7);
    expect(second.run.counts.eventsSelected).toBe(7);
    expect(second.run.counts.counterSignals).toBe(0);
    expect(second.pack.core).toEqual([]);
    expect(second.pack.counterSignals).toEqual([]);
    for (const e of await listEvents(page, second.id)) expect(e.selection).toBeUndefined();
    expect(stable(second.pack)).toEqual(stable(first.pack));
    expect(second.pack.promptText.includes('CORE EVIDENCE') || second.pack.promptText.includes('COUNTER-SIGNALS')).toBe(false);
  });

  test('FR-18 an unknown run has no Evidence Pack', async ({ page }) => {
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/evidence-pack');
    expect(res.status()).toBe(404);
    expect((await res.json()).code).toBe('RUN_NOT_FOUND');
  });
});
