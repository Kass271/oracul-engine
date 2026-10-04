// @trace FR-29
import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

const B = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

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

async function generated(page: Page): Promise<string> {
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  const id = page.url().split('/').pop()!;
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
  return id;
}

test('FR-29 quick DARKER starts a new run and the previous one stays in Recent futures', async ({ page }) => {
  test.setTimeout(120_000);
  await connect(page);
  await page.getByTestId('generate-button').click();
  const run1 = await generated(page);

  await expect(page.getByTestId('quick-actions')).toBeVisible();
  for (const id of ['quick-more-realistic', 'quick-darker', 'quick-more-optimistic', 'quick-more-extreme']) {
    await expect(page.getByTestId(id)).toBeEnabled();
  }
  const reqPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
  await page.getByTestId('quick-darker').click();
  const req = await reqPromise;
  expect(req.postDataJSON()).toEqual({ ...B, darkness: 7 });
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('7');
  await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
  await expect.poll(() => page.url().split('/').pop()).not.toBe(run1);
  const run2 = page.url().split('/').pop()!;
  const res = await page.request.get(`/api/runs/${run2}`);
  expect(res.status()).toBe(200);
  expect((await res.json()).configuration.darkness).toBe(7);
  await evidence(page, 'FR-29', 'quick-darker-started');
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });

  await page.getByTestId('recent-futures-button').click();
  await expect(page.getByTestId('recent-futures-list')).toBeVisible();
  await expect(page.getByTestId(`recent-future-${run2}`)).toBeVisible();
  const entryIds = await page.evaluate(() =>
    Array.from(document.querySelectorAll('[data-testid]'))
      .map((n) => n.getAttribute('data-testid')!)
      .filter((t) => /^recent-future-[0-9a-f-]{36}$/.test(t)),
  );
  expect(entryIds[0]).toBe(`recent-future-${run2}`);
  expect(entryIds).toContain(`recent-future-${run1}`);
  await expect(page.getByTestId(`recent-future-settings-${run1}`)).toHaveText('R8 D5 O5 · 1 year');
  await page.getByTestId(`recent-future-${run1}`).click();
  await expect(page).toHaveURL(new RegExp(`/futures/${run1}$`));
  await expect(page.getByTestId('result-view')).toBeVisible();
  await expect(page.getByTestId('meta-darkness')).toHaveText('Darkness 5/10');
  await evidence(page, 'FR-29', 'previous-result-in-recent-futures');
});

test('FR-29 DARKER clamps at 10 and is then disabled', async ({ page }) => {
  test.setTimeout(120_000);
  await connect(page);
  await page.getByTestId('slider-darkness-input').fill('9');
  await page.getByTestId('generate-button').click();
  const run1 = await generated(page);

  const reqPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
  await page.getByTestId('quick-darker').click();
  expect((await reqPromise).postDataJSON().darkness).toBe(10);
  await expect.poll(() => page.url().split('/').pop()).not.toBe(run1);
  await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
  await expect(page.getByTestId('slider-darkness-input')).toHaveValue('10');
  await expect(page.getByTestId('quick-darker')).toBeDisabled();
  await expect(page.getByTestId('quick-more-realistic')).toBeEnabled();
  await expect(page.getByTestId('quick-more-optimistic')).toBeEnabled();
  await expect(page.getByTestId('quick-more-extreme')).toBeEnabled();
  await evidence(page, 'FR-29', 'darker-disabled-at-10');
});
