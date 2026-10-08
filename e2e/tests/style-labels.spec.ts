import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const KAFKA = 'Narrative — inspired by Franz Kafka';
const POE = 'Narrative — inspired by Edgar Allan Poe';
const DEFAULT_HEADLINE = 'Stub headline from the future';
const POE_UK_HEADLINE = `Заголовок із майбутнього — ${POE}`;

test.describe.configure({ mode: 'serial' });

async function control(page: Page, name: string, data: object): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/${name}`, { data })).status()).toBe(204);
}

test.beforeEach(async ({ page }) => {
  expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  await control(page, 'google', { mode: 'ok' });
  await control(page, 'events', { mode: 'ok' });
  await control(page, 'scenario', { mode: 'ok' });
  await control(page, 'story', { mode: 'ok' });
});

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

/** The id of the run in the URL once the result of a run is shown. */
async function resultOf(page: Page): Promise<string> {
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
  return page.url().split('/').pop()!;
}

async function getJson(page: Page, url: string): Promise<any> {
  const res = await page.request.get(url);
  expect(res.status(), url).toBe(200);
  return res.json();
}

async function recorded(page: Page, kind: string): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
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

/** Lines of one ORACUL_UNTRUSTED_DATA block, markers excluded. */
function dataBlock(text: string, name: string): string {
  const open = `<<<ORACUL_UNTRUSTED_DATA name="${name}">>>\n`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const end = text.indexOf('\n<<<END_ORACUL_UNTRUSTED_DATA>>>', start + open.length);
  return end < 0 ? '' : text.slice(start + open.length, end);
}

async function generationTexts(page: Page): Promise<string[]> {
  return (await recorded(page, 'responses')).map(inputText).filter((t) => t.includes('ORACUL REQUEST SCENARIO_GENERATION'));
}

/** Chooses a REWRITE AS… item and waits until the URL shows the new run. */
async function rewriteAs(page: Page, parentId: string, profileId: string, language: string): Promise<void> {
  await page.getByTestId('quick-rewrite').click();
  await expect(page.getByTestId('rewrite-menu')).toBeVisible();
  const sent = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === `/api/runs/${parentId}/rewrites`);
  await page.getByTestId(`rewrite-option-${profileId}-${language}`).click();
  await sent;
  await expect.poll(() => page.url().split('/').pop()).not.toBe(parentId);
}

/** A run whose configuration has no writing style and no language (an API client of an earlier phase). */
async function startLegacyRun(page: Page): Promise<string> {
  const res = await page.request.post('/api/runs', {
    data: { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], customWildcards: [], output: { story: true, illustration: false } },
  });
  expect(res.status(), await res.text()).toBe(202);
  const id = (await res.json()).id as string;
  await expect.poll(async () => (await getJson(page, `/api/runs/${id}`)).status, { timeout: 90_000, intervals: [500, 1000] }).toBe('COMPLETED');
  return id;
}

// @trace FR-68
test.describe('FR-68 style and language shown with the result', () => {
  test('FR-68 the metadata names the style and language, and a rewrite names its origin', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);

    await expect(page.getByTestId('meta-style')).toHaveText('Style: Official / Briefing');
    await expect(page.getByTestId('meta-language')).toHaveText('Language: English');
    await expect(page.getByTestId('meta-rewritten-from')).toHaveCount(0);
    await evidence(page, 'FR-68', 'metadata-official-english');
    const first = await getJson(page, `/api/runs/${r1}`);
    expect(first.headline).toBe(DEFAULT_HEADLINE);

    await rewriteAs(page, r1, 'kafka', 'uk');
    const r2 = await resultOf(page);
    expect(r2).not.toBe(r1);
    await expect(page.getByTestId('story-headline')).toHaveText(`Заголовок із майбутнього — ${KAFKA}`);
    await expect(page.getByTestId('meta-style')).toHaveText(`Style: ${KAFKA}`);
    await expect(page.getByTestId('meta-language')).toHaveText('Language: Ukrainian');
    await expect(page.getByTestId('meta-rewritten-from')).toHaveText(`Rewritten from: ${first.headline}`);
    await expect(page.getByTestId('meta-rewritten-from')).toHaveText('Rewritten from: Stub headline from the future');
    await evidence(page, 'FR-68', 'metadata-rewritten-from');

    const result2 = await getJson(page, `/api/runs/${r2}/result`);
    expect(result2.metadata.styleLabel).toBe(KAFKA);
    expect(result2.metadata.languageLabel).toBe('Ukrainian');
    expect(result2.metadata.rewrittenFrom).toEqual({ runId: r1, headline: first.headline });
    const result1 = await getJson(page, `/api/runs/${r1}/result`);
    expect(result1.metadata.styleLabel).toBe('Official / Briefing');
    expect(result1.metadata.languageLabel).toBe('English');
    expect('rewrittenFrom' in result1.metadata).toBe(false);
  });

  test('FR-68 a run started without a style is labelled legacy and English', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    const legacy = await startLegacyRun(page);
    await page.goto(`/futures/${legacy}`);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('meta-style')).toHaveText('Style: Official / Briefing (legacy)');
    await expect(page.getByTestId('meta-language')).toHaveText('Language: English');
    await expect(page.getByTestId('meta-rewritten-from')).toHaveCount(0);
    await evidence(page, 'FR-68', 'metadata-legacy');

    const result = await getJson(page, `/api/runs/${legacy}/result`);
    expect(result.metadata.styleLabel).toBe('Official / Briefing (legacy)');
    expect(result.metadata.languageLabel).toBe('English');
  });

  test('FR-68 Recent futures label every entry with its style and language', async ({ page }) => {
    test.setTimeout(300_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);
    await rewriteAs(page, r1, 'kafka', 'uk');
    const r2 = await resultOf(page);
    expect(r2).not.toBe(r1);
    const legacy = await startLegacyRun(page);

    await page.goto('/');
    await page.getByTestId('recent-futures-button').click();
    await expect(page.getByTestId('recent-futures-list')).toBeVisible();
    await expect(page.getByTestId(`recent-future-style-${r2}`)).toHaveText(`${KAFKA} · Ukrainian`);
    await expect(page.getByTestId(`recent-future-style-${r1}`)).toHaveText('Official / Briefing · English');
    await expect(page.getByTestId(`recent-future-style-${legacy}`)).toHaveText('Official / Briefing (legacy) · English');
    await evidence(page, 'FR-68', 'recent-futures-labels');

    const list = await getJson(page, '/api/runs');
    for (const id of [r1, r2, legacy]) {
      const entry = list.items.find((i: any) => i.id === id);
      expect(entry, id).toBeTruthy();
      await expect(page.getByTestId(`recent-future-style-${id}`)).toHaveText(`${entry.styleLabel} · ${entry.languageLabel}`);
    }
    // the entries are still found by their own test id
    const entryIds = await page.evaluate(() =>
      Array.from(document.querySelectorAll('[data-testid]'))
        .map((n) => n.getAttribute('data-testid')!)
        .filter((t) => /^recent-future-[0-9a-f-]{36}$/.test(t)),
    );
    expect(entryIds).toEqual([`recent-future-${legacy}`, `recent-future-${r2}`, `recent-future-${r1}`]);
  });

  test('FR-68 DARKER and ALTERNATIVE FUTURE on a Poe / Ukrainian result keep Poe / Ukrainian', async ({ page }) => {
    test.setTimeout(300_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);
    const scenario1 = await getJson(page, `/api/runs/${r1}/structured-scenario`);
    const title1: string = scenario1.structuredScenario.futureEvent.title;

    await rewriteAs(page, r1, 'poe', 'uk');
    const poeRun = await resultOf(page);
    await expect(page.getByTestId('meta-style')).toHaveText(`Style: ${POE}`);
    await expect(page.getByTestId('meta-language')).toHaveText('Language: Ukrainian');

    // quick regeneration keeps the telling
    const posted = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.getByTestId('quick-darker').click();
    const request = await posted;
    const body = request.postDataJSON();
    expect(body.output.writingStyle).toEqual({ mode: 'NARRATIVE', profileId: 'poe' });
    expect(body.output.language).toBe('uk');
    await expect.poll(() => page.url().split('/').pop()).not.toBe(poeRun);
    const darker = await resultOf(page);
    expect(darker).not.toBe(poeRun);
    await expect(page.getByTestId('meta-style')).toHaveText(`Style: ${POE}`);
    await expect(page.getByTestId('meta-language')).toHaveText('Language: Ukrainian');
    await evidence(page, 'FR-68', 'darker-keeps-poe-ukrainian');

    // ALTERNATIVE FUTURE on the rewrite
    await page.goto(`/futures/${poeRun}`);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    const generationsBefore = (await generationTexts(page)).length;
    const alternativePosted = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === `/api/runs/${poeRun}/alternatives`);
    await page.getByTestId('quick-alternative').click();
    await alternativePosted;
    await expect.poll(() => page.url().split('/').pop()).not.toBe(poeRun);
    const alt = await resultOf(page);

    const altRun = await getJson(page, `/api/runs/${alt}`);
    expect(altRun.kind).toBe('ALTERNATIVE');
    expect(altRun.parentRunId).toBe(poeRun);
    expect(altRun.configuration.output.writingStyle).toEqual({ mode: 'NARRATIVE', profileId: 'poe' });
    expect(altRun.configuration.output.language).toBe('uk');

    const generations = await generationTexts(page);
    expect(generations).toHaveLength(generationsBefore + 1);
    expect(dataBlock(generations[generations.length - 1], 'futures-to-avoid')).toContain(`Future 1: ${title1}`);

    await expect(page.getByTestId('story-headline')).toHaveText(POE_UK_HEADLINE);
    await expect(page.getByTestId('meta-style')).toHaveText(`Style: ${POE}`);
    await expect(page.getByTestId('meta-language')).toHaveText('Language: Ukrainian');
    await expect(page.getByTestId('meta-rewritten-from')).toHaveCount(0);
    await evidence(page, 'FR-68', 'alternative-keeps-poe-ukrainian');
  });
});
