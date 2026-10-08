import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const KAFKA_UK_HEADLINE = 'Заголовок із майбутнього — Narrative — inspired by Franz Kafka';
const PROFILE_IDS = ['official-briefing', 'neutral-narrative', 'poe', 'kafka', 'wells', 'verne', 'orwell', 'clarke', 'harrison'];
const MENU_ENTRIES = [
  'rewrite-language-en',
  ...PROFILE_IDS.map((id) => `rewrite-option-${id}-en`),
  'rewrite-language-uk',
  ...PROFILE_IDS.map((id) => `rewrite-option-${id}-uk`),
];

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

function purposeOf(request: any): string | undefined {
  return /ORACUL REQUEST ([A-Z_]+)/.exec(inputText(request))?.[1];
}

async function storyRequests(page: Page): Promise<any[]> {
  return (await recorded(page, 'responses')).filter((r) => purposeOf(r) === 'STORY_WRITING');
}

/** Lines of one ORACUL_UNTRUSTED_DATA block, markers excluded. */
function dataBlock(text: string, name: string): string {
  const open = `<<<ORACUL_UNTRUSTED_DATA name="${name}">>>\n`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const end = text.indexOf('\n<<<END_ORACUL_UNTRUSTED_DATA>>>', start + open.length);
  return end < 0 ? '' : text.slice(start + open.length, end);
}

/** The input without the lines from "WRITING STYLE" up to the line before "TASK". */
function withoutStyleSection(text: string): string {
  const lines = text.split('\n');
  const from = lines.indexOf('WRITING STYLE');
  const to = lines.indexOf('TASK', from + 1);
  expect(from, 'a WRITING STYLE line').toBeGreaterThanOrEqual(0);
  expect(to, 'a TASK line after it').toBeGreaterThan(from);
  return [...lines.slice(0, from), ...lines.slice(to)].join('\n');
}

async function getJson(page: Page, url: string): Promise<any> {
  const res = await page.request.get(url);
  expect(res.status(), url).toBe(200);
  return res.json();
}

async function menuEntries(page: Page): Promise<string[]> {
  return page.locator('[data-testid^="rewrite-language-"], [data-testid^="rewrite-option-"]').evaluateAll((els) => els.map((e) => e.getAttribute('data-testid') ?? ''));
}

async function openMenu(page: Page): Promise<void> {
  await page.getByTestId('quick-rewrite').click();
  await expect(page.getByTestId('rewrite-menu')).toBeVisible();
}

/** Chooses a menu item and returns the POST request it sent. */
async function rewriteAs(page: Page, parentId: string, profileId: string, language: string): Promise<any> {
  await openMenu(page);
  const sent = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === `/api/runs/${parentId}/rewrites`);
  await page.getByTestId(`rewrite-option-${profileId}-${language}`).click();
  const request = await sent;
  await expect.poll(() => page.url().split('/').pop()).not.toBe(parentId);
  return request;
}

const radio = (page: Page, testId: string) => page.getByTestId(testId).locator('input[type="radio"]');

// @trace FR-67
test.describe('FR-67 rewrite the same future in another style or language', () => {
  test('FR-67 REWRITE AS… tells the same future again in Kafka / Ukrainian with only a story request', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);
    const runBefore = await getJson(page, `/api/runs/${r1}`);
    const resultBefore = await getJson(page, `/api/runs/${r1}/result`);
    const responsesBefore = await recorded(page, 'responses');
    const firstStory = responsesBefore.filter((r) => purposeOf(r) === 'STORY_WRITING');
    expect(firstStory).toHaveLength(1);
    const traffic = async () => ({
      rss: (await recorded(page, 'rss')).length,
      googlePage: (await recorded(page, 'google-page')).length,
      decode: (await recorded(page, 'decode')).length,
      article: (await recorded(page, 'article')).length,
    });
    const trafficBefore = await traffic();

    // the menu: 2 headings + 18 items in API order, the result's own combination disabled
    await expect(page.getByTestId('quick-rewrite')).toHaveText('REWRITE AS…');
    await expect(page.getByTestId('quick-rewrite')).toBeEnabled();
    await openMenu(page);
    expect(await menuEntries(page)).toEqual(MENU_ENTRIES);
    await expect(page.getByTestId('rewrite-language-en')).toHaveText('English');
    await expect(page.getByTestId('rewrite-language-uk')).toHaveText('Ukrainian');
    await expect(page.getByTestId('rewrite-option-official-briefing-en')).toBeDisabled();
    for (const id of MENU_ENTRIES.filter((e) => e.startsWith('rewrite-option-') && e !== 'rewrite-option-official-briefing-en')) {
      await expect(page.getByTestId(id), id).toBeEnabled();
    }
    await evidence(page, 'FR-67', 'rewrite-menu');
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('rewrite-menu')).toHaveCount(0);

    // choose Kafka / Ukrainian
    const request = await rewriteAs(page, r1, 'kafka', 'uk');
    expect(request.postDataJSON()).toEqual({ writingStyle: { mode: 'NARRATIVE', profileId: 'kafka' }, language: 'uk' });
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const r2 = page.url().split('/').pop()!;
    expect(r2).not.toBe(r1);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('story-headline')).toHaveText(KAFKA_UK_HEADLINE);
    await expect(radio(page, 'writing-style-option-NARRATIVE')).toBeChecked();
    await expect(radio(page, 'narrative-style-option-kafka')).toBeChecked();
    await expect(page.getByTestId('story-language-select')).toHaveText(/Ukrainian/);
    await evidence(page, 'FR-67', 'rewrite-result');

    // exactly one new Responses request, a STORY_WRITING one; nothing searched or read
    const responsesAfter = await recorded(page, 'responses');
    const added = responsesAfter.slice(responsesBefore.length);
    expect(added).toHaveLength(1);
    expect(inputText(added[0])).toContain('ORACUL REQUEST STORY_WRITING');
    expect(await traffic()).toEqual(trafficBefore);

    // the input differs from the first story request only inside the WRITING STYLE section
    const first = inputText(firstStory[0]);
    const second = inputText(added[0]);
    expect(second).not.toBe(first);
    expect(second).toContain('Style profile: Narrative — inspired by Franz Kafka');
    expect(withoutStyleSection(second)).toBe(withoutStyleSection(first));
    expect(dataBlock(second, 'structured-scenario')).not.toBe('');
    expect(dataBlock(second, 'structured-scenario')).toBe(dataBlock(first, 'structured-scenario'));
    expect(added[0].instructions).toBe(firstStory[0].instructions);

    // the rewrite run and the untouched original
    const rewrite = await getJson(page, `/api/runs/${r2}`);
    expect(rewrite.kind).toBe('REWRITE');
    expect(rewrite.parentRunId).toBe(r1);
    expect(rewrite.scenarioRunId).toBe(r1);
    expect(rewrite.evidencePackId).toBe(runBefore.evidencePackId);
    expect(rewrite.counts).toEqual(runBefore.counts);
    expect(await getJson(page, `/api/runs/${r1}`)).toEqual(runBefore);
    expect(await getJson(page, `/api/runs/${r1}/result`)).toEqual(resultBefore);
  });

  test('FR-67 a rewrite of a rewrite tells the original scenario and disables its own menu item', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);
    const original = await getJson(page, `/api/runs/${r1}/structured-scenario`);
    const originalStory = (await storyRequests(page))[0];

    await rewriteAs(page, r1, 'kafka', 'uk');
    const r2 = await resultOf(page);
    await expect(page.getByTestId('story-headline')).toHaveText(KAFKA_UK_HEADLINE);

    const before = (await storyRequests(page)).length;
    const total = (await recorded(page, 'responses')).length;
    await rewriteAs(page, r2, 'poe', 'en');
    await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future — Narrative — inspired by Edgar Allan Poe', {
      timeout: 90_000,
    });
    const r3 = await resultOf(page);
    expect(r3).not.toBe(r2);

    const run3 = await getJson(page, `/api/runs/${r3}`);
    expect(run3.kind).toBe('REWRITE');
    expect(run3.parentRunId).toBe(r2);
    expect(run3.scenarioRunId).toBe(r1);
    const stories = await storyRequests(page);
    expect(stories).toHaveLength(before + 1);
    expect((await recorded(page, 'responses')).length).toBe(total + 1);
    expect(dataBlock(inputText(stories[before]), 'structured-scenario')).toBe(dataBlock(inputText(originalStory), 'structured-scenario'));
    expect(await getJson(page, `/api/runs/${r3}/structured-scenario`)).toEqual(original);

    await openMenu(page);
    for (const id of MENU_ENTRIES.filter((e) => e.startsWith('rewrite-option-'))) {
      if (id === 'rewrite-option-poe-en') await expect(page.getByTestId(id), id).toBeDisabled();
      else await expect(page.getByTestId(id), id).toBeEnabled();
    }
    await evidence(page, 'FR-67', 'rewrite-of-rewrite');
  });

  test('FR-67 a run that is not COMPLETED cannot be rewritten', async ({ page }) => {
    test.setTimeout(150_000);
    await control(page, 'story', { mode: 'invalid' });
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('failure-view')).toBeVisible({ timeout: 90_000 });
    const failed = page.url().split('/').pop()!;
    const listBefore = await getJson(page, '/api/runs');
    const requestsBefore = (await recorded(page, 'responses')).length;

    const res = await page.request.post(`/api/runs/${failed}/rewrites`, { data: { writingStyle: { mode: 'NARRATIVE', profileId: 'kafka' }, language: 'uk' } });
    expect(res.status()).toBe(409);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_COMPLETED', message: 'Only a completed future can be rewritten' });
    expect(await getJson(page, '/api/runs')).toEqual(listBefore);
    expect((await recorded(page, 'responses')).length).toBe(requestsBefore);
    await evidence(page, 'FR-67', 'rewrite-refused-for-failed-run');
  });

  test('FR-67 a failing rewrite shows the failure and leaves the original result alone', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);
    const runBefore = await getJson(page, `/api/runs/${r1}`);
    const resultBefore = await getJson(page, `/api/runs/${r1}/result`);

    await control(page, 'story', { mode: 'invalid' });
    await rewriteAs(page, r1, 'kafka', 'en');
    await expect(page.getByTestId('failure-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('failure-message')).toHaveText('ORACUL could not construct a valid scenario');
    await expect(page.getByTestId('try-again')).toBeVisible();
    await evidence(page, 'FR-67', 'rewrite-failed');

    const after = await getJson(page, `/api/runs/${r1}`);
    expect(after.status).toBe('COMPLETED');
    expect(after.headline).toBe(runBefore.headline);
    expect(after).toEqual(runBefore);
    expect(await getJson(page, `/api/runs/${r1}/result`)).toEqual(resultBefore);
  });

  test('FR-67 a rewrite is refused while another run of the session is active', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    await page.getByTestId('generate-button').click();
    const r1 = await resultOf(page);

    await control(page, 'google', { mode: 'slow', ms: 3000 });
    // generate-button exists only in the welcome view, not on a completed result
    await page.goto('/');
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('progress-view')).toBeVisible();
    await expect.poll(() => page.url().split('/').pop()).not.toBe(r1);
    const second = page.url().split('/').pop()!;

    const res = await page.request.post(`/api/runs/${r1}/rewrites`, { data: { writingStyle: { mode: 'NARRATIVE', profileId: 'poe' }, language: 'en' } });
    expect(res.status()).toBe(409);
    expect(await res.json()).toEqual({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' });
    await evidence(page, 'FR-67', 'rewrite-refused-while-active');

    await expect
      .poll(async () => (await getJson(page, `/api/runs/${second}`)).status, { timeout: 120_000, intervals: [500, 1000] })
      .toMatch(/^(COMPLETED|FAILED|INSUFFICIENT_EVIDENCE|STOPPED)$/);
    const list = await getJson(page, '/api/runs');
    expect(list.items.filter((i: any) => i.kind === 'REWRITE')).toEqual([]);
  });
});
