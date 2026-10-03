// @trace FR-33
import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  await page.request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode: 'ok' } })).status()).toBe(204);
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

async function generateWithDarkness(page: Page, darkness: string): Promise<string> {
  await page.getByTestId('slider-darkness-input').fill(darkness);
  await page.getByTestId('generate-button').click();
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  const id = page.url().split('/').pop()!;
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
  return id;
}

test('FR-33 two futures are listed newest first and the older one reopens with its data', async ({ page, browser }) => {
  test.setTimeout(60_000);
  await connect(page);
  const run1 = await generateWithDarkness(page, '9');
  await page.goto('/');
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('9');
  const run2 = await generateWithDarkness(page, '3');
  expect(run2).not.toBe(run1);

  // list: newest first with time, headline and key settings
  await page.getByTestId('recent-futures-button').click();
  await expect(page.getByTestId('recent-futures-list')).toBeVisible();
  const entryIds = await page.evaluate(() =>
    Array.from(document.querySelectorAll('[data-testid]'))
      .map((n) => n.getAttribute('data-testid')!)
      .filter((t) => /^recent-future-[0-9a-f-]{36}$/.test(t)),
  );
  expect(entryIds).toEqual([`recent-future-${run2}`, `recent-future-${run1}`]);
  await expect(page.getByTestId(`recent-future-settings-${run2}`)).toHaveText('R8 D3 O5 · 1 year');
  await expect(page.getByTestId(`recent-future-settings-${run1}`)).toHaveText('R8 D9 O5 · 1 year');
  await expect(page.getByTestId(`recent-future-time-${run2}`)).toHaveText(/^\d{2}:\d{2}$/);
  await expect(page.getByTestId(`recent-future-headline-${run1}`)).not.toBeEmpty();
  await evidence(page, 'FR-33', 'recent-futures-list');

  // reopen the older future
  await page.getByTestId(`recent-future-${run1}`).click();
  await expect(page).toHaveURL(new RegExp(`/futures/${run1}$`));
  await expect(page.getByTestId('result-view')).toBeVisible();
  await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future');
  await expect(page.getByTestId('meta-darkness')).toHaveText('Darkness 9/10');
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('9');
  await page.getByTestId('open-why').click();
  await expect(page.getByTestId('why-panel')).toBeVisible();
  await page.getByTestId('open-sources').click();
  await expect(page.getByTestId('sources-panel')).toBeVisible();
  await page.getByTestId('open-why-news').click();
  await expect(page.getByTestId('why-news-panel')).toBeVisible();
  await evidence(page, 'FR-33', 'recent-future-reopened');

  // reload restores the newest run's configuration
  await page.goto('/');
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('3');

  // another browser session sees nothing
  const origin = new URL(page.url()).origin;
  const other = await browser.newContext({ baseURL: origin });
  try {
    const p2 = await other.newPage();
    await p2.goto('/');
    await p2.getByTestId('recent-futures-button').click();
    await expect(p2.getByTestId('recent-futures-empty')).toHaveText('No futures yet');
    const res = await p2.request.get(`${origin}/api/runs/${run1}`);
    expect(res.status()).toBe(404);
    expect((await res.json()).code).toBe('RUN_NOT_FOUND');
    await p2.goto(`/futures/${run1}`);
    await expect(p2.getByTestId('failure-message')).toHaveText('Future not found');
  } finally {
    await other.close();
  }
});
