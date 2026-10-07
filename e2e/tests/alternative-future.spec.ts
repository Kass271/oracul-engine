// @trace FR-30, FR-52
import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

async function control(page: Page, name: string, mode: string): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/${name}`, { data: { mode } })).status()).toBe(204);
}

async function resetStub(page: Page): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  await control(page, 'events', 'ok');
  await control(page, 'scenario', 'ok');
  await control(page, 'story', 'ok');
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

async function generated(page: Page): Promise<string> {
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  const id = page.url().split('/').pop()!;
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
  return id;
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

async function generationTexts(page: Page): Promise<string[]> {
  return (await recorded(page, 'responses')).map(inputText).filter((t) => t.includes('ORACUL REQUEST SCENARIO_GENERATION'));
}

async function startAlternative(page: Page, parentId: string): Promise<string> {
  const reqPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === `/api/runs/${parentId}/alternatives`);
  await page.getByTestId('quick-alternative').click();
  await reqPromise;
  await expect.poll(() => page.url().split('/').pop()).not.toBe(parentId);
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  return page.url().split('/').pop()!;
}

test('FR-30 ALTERNATIVE FUTURE reuses the evidence and produces a different future', async ({ page }) => {
  test.setTimeout(120_000);
  await connect(page);
  await page.getByTestId('generate-button').click();
  const run1 = await generated(page);

  await expect(page.getByTestId('quick-alternative')).toBeVisible();
  await expect(page.getByTestId('quick-alternative')).toHaveText('ALTERNATIVE FUTURE');
  await expect(page.getByTestId('quick-alternative')).toBeEnabled();
  const rssBefore = (await recorded(page, 'rss')).length;
  const first = await (await page.request.get(`/api/runs/${run1}`)).json();

  const run2 = await startAlternative(page, run1);
  expect(run2).not.toBe(run1);
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('5');
  const res = await page.request.get(`/api/runs/${run2}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  expect(body.kind).toBe('ALTERNATIVE');
  expect(body.parentRunId).toBe(run1);
  expect(body.evidencePackId).toBe(first.evidencePackId);
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });

  // an ALTERNATIVE run searches nothing: no Google News request either (news-search.md FR-48); the first run sent one request per planned query (FR-52)
  expect(first.counts.searches).toBe(20);
  expect(rssBefore).toBe(first.counts.searches);
  expect((await recorded(page, 'rss')).length).toBe(rssBefore);
  const texts = await generationTexts(page);
  const last = texts[texts.length - 1];
  expect(last).toContain('<<<ORACUL_UNTRUSTED_DATA name="futures-to-avoid">>>');
  expect(last).toContain('Future 1: Stub future A');

  const s1 = await (await page.request.get(`/api/runs/${run1}/structured-scenario`)).json();
  const s2 = await (await page.request.get(`/api/runs/${run2}/structured-scenario`)).json();
  expect(s2.structuredScenario.futureEvent.title).toBe('Stub alternative future 1');
  expect(s2.structuredScenario.futureEvent.title).not.toBe(s1.structuredScenario.futureEvent.title);
  const chain1: string[] = s1.structuredScenario.causalChain.map((c: any) => c.statement);
  const chain2: string[] = s2.structuredScenario.causalChain.map((c: any) => c.statement);
  expect(chain2.some((s) => !chain1.includes(s))).toBe(true);
  await evidence(page, 'FR-30', 'alternative-result');

  await page.getByTestId('recent-futures-button').click();
  await expect(page.getByTestId(`recent-future-${run2}`)).toBeVisible();
  await expect(page.getByTestId(`recent-future-${run1}`)).toBeVisible();
});

test('FR-30 an alternative that repeats the future twice fails with ALTERNATIVE_NOT_DISTINCT', async ({ page }) => {
  test.setTimeout(120_000);
  await control(page, 'scenario', 'alt-repeat');
  await connect(page);
  await page.getByTestId('generate-button').click();
  const run1 = await generated(page);

  const run2 = await startAlternative(page, run1);
  await expect(page.getByTestId('failure-view')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('failure-message')).toHaveText('ORACUL could not find a different future — try changing a setting');
  const body = await (await page.request.get(`/api/runs/${run2}`)).json();
  expect(body.failure.code).toBe('ALTERNATIVE_NOT_DISTINCT');
  await evidence(page, 'FR-30', 'alternative-not-distinct');
});

test('FR-30 one repeated future is regenerated once with the duplicate named', async ({ page }) => {
  test.setTimeout(120_000);
  await control(page, 'scenario', 'alt-repeat-once');
  await connect(page);
  await page.getByTestId('generate-button').click();
  const run1 = await generated(page);

  const before = (await generationTexts(page)).length;
  await startAlternative(page, run1);
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
  const after = (await generationTexts(page)).slice(before);
  expect(after).toHaveLength(2);
  expect(after[1]).toContain('Reason: ALTERNATIVE_DISTINCT');
  expect(after[1]).toContain('name="duplicate-future"');
});
