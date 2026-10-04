import { expect, test, type Page } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

const CODES = ['1d', '1w', '1m', '1y', '5y', '10y', '20y'];
const LABELS = ['Tomorrow', '1 week', '1 month', '1 year', '5 years', '10 years', '20 years'];

const BASE = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

async function checkedHorizons(page: Page): Promise<string[]> {
  const out: string[] = [];
  for (const c of CODES) {
    const cls = (await page.getByTestId(`horizon-option-${c}`).getAttribute('class')) ?? '';
    if (cls.split(/\s+/).includes('mat-button-toggle-checked')) out.push(c);
  }
  return out;
}

// @trace FR-1
test.describe('FR-1 main page welcome state', () => {
  test('shows header, panel, welcome question and generate button with ORACUL spelling', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('scenario-panel')).toBeVisible();
    await expect(page.getByTestId('app-header')).toBeVisible();
    await expect(page.getByTestId('app-wordmark')).toHaveText('ORACUL');
    await expect(page.getByTestId('welcome-question')).toHaveText('What happens next?');
    await expect(page.getByTestId('generate-button')).toHaveText('GENERATE THE FUTURE');
    await expect(page.getByTestId('generate-button')).toBeDisabled();
    await expect(page.getByTestId('generate-hint')).toHaveText('Connect ChatGPT to generate');
    await expect(page).toHaveTitle('ORACUL');
    const body = (await page.locator('body').innerText()).toLowerCase();
    expect(body).not.toContain('oracle');
    expect(await page.locator('[title*="racle" i], [aria-label*="racle" i], [alt*="racle" i]').count()).toBe(0);
    await evidence(page, 'FR-1', 'welcome');
  });

  test('shows backend-unavailable on 503 and recovers after retry', async ({ page }) => {
    await page.route('**/api/scenario/**', (r) => r.fulfill({ status: 503, body: '' }));
    await page.goto('/');
    await expect(page.getByTestId('backend-unavailable')).toContainText('ORACUL is unavailable — try again shortly');
    await expect(page.getByTestId('backend-retry')).toHaveText('Try again');
    await expect(page.getByTestId('scenario-panel')).toHaveCount(0);
    await expect(page.getByTestId('welcome-view')).toHaveCount(0);
    await expect(page.getByTestId('app-wordmark')).toHaveText('ORACUL');
    await evidence(page, 'FR-1', 'backend-unavailable');

    await page.unroute('**/api/scenario/**');
    await page.getByTestId('backend-retry').click();
    await expect(page.getByTestId('scenario-panel')).toBeVisible();
    await expect(page.getByTestId('welcome-view')).toBeVisible();
    await expect(page.getByTestId('backend-unavailable')).toHaveCount(0);
  });

  test('shows backend-unavailable when the request is aborted', async ({ page }) => {
    await page.route('**/api/scenario/**', (r) => r.abort());
    await page.goto('/');
    await expect(page.getByTestId('backend-unavailable')).toContainText('ORACUL is unavailable — try again shortly');
    await expect(page.getByTestId('scenario-panel')).toHaveCount(0);
  });
});

// @trace FR-2
test.describe('FR-2 intensity controls', () => {
  test('fresh session shows Darkness 5, Optimism 5, Realism 8', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('value-darkness')).toHaveText('5');
    await expect(page.getByTestId('value-optimism')).toHaveText('5');
    await expect(page.getByTestId('value-realism')).toHaveText('8');
    await expect(page.getByTestId('slider-darkness-input')).toHaveValue('5');
    await expect(page.getByTestId('slider-optimism-input')).toHaveValue('5');
    await expect(page.getByTestId('slider-realism-input')).toHaveValue('8');
    await expect(page.getByTestId('label-darkness')).toContainText('Darkness');
    await expect(page.getByTestId('label-optimism')).toContainText('Optimism');
    await expect(page.getByTestId('label-realism')).toContainText('Realism');
  });

  test('darkness 9 and optimism 9 are independent and leave realism untouched', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('slider-darkness-input').fill('9');
    await expect(page.getByTestId('value-darkness')).toHaveText('9');
    await expect(page.getByTestId('value-optimism')).toHaveText('5');
    await page.getByTestId('slider-optimism-input').fill('9');
    await expect(page.getByTestId('value-optimism')).toHaveText('9');
    await expect(page.getByTestId('value-darkness')).toHaveText('9');
    await expect(page.getByTestId('value-realism')).toHaveText('8');
    await evidence(page, 'FR-2', 'darkness-9-optimism-9');
  });

  test('sliders are operable with the keyboard', async ({ page }) => {
    await page.goto('/');
    const input = page.getByTestId('slider-realism-input');
    await input.focus();
    await page.keyboard.press('ArrowLeft');
    await expect(page.getByTestId('value-realism')).toHaveText('7');
    await page.keyboard.press('Home');
    await expect(page.getByTestId('value-realism')).toHaveText('1');
    await page.keyboard.press('End');
    await expect(page.getByTestId('value-realism')).toHaveText('10');
    await expect(page.getByTestId('value-darkness')).toHaveText('5');
  });

  for (const [field, bad] of [
    ['darkness', 0],
    ['darkness', 11],
    ['optimism', 0],
    ['realism', 11],
  ] as const) {
    test(`API rejects ${field} ${bad}`, async ({ request }) => {
      const res = await request.post('/api/runs', { data: { ...BASE, [field]: bad } });
      expect(res.status()).toBe(400);
      expect(await res.json()).toEqual({
        code: 'VALIDATION_FAILED',
        message: `${field} must be between 1 and 10`,
      });
    });
  }
});

// @trace FR-3
test.describe('FR-3 time horizon', () => {
  test('fresh session selects 1 year among 7 ordered options', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('horizon-group')).toBeVisible();
    for (let i = 0; i < CODES.length; i++) {
      await expect(page.getByTestId(`horizon-option-${CODES[i]}`)).toContainText(LABELS[i]);
    }
    expect(await checkedHorizons(page)).toEqual(['1y']);
    await evidence(page, 'FR-3', 'default-1-year');
  });

  test('selecting 5 years selects only 5 years; re-clicking keeps it selected', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('horizon-option-5y').locator('button').click();
    await expect.poll(() => checkedHorizons(page)).toEqual(['5y']);
    await evidence(page, 'FR-3', 'selected-5-years');
    await page.getByTestId('horizon-option-5y').locator('button').click();
    await expect.poll(() => checkedHorizons(page)).toEqual(['5y']);
  });

  test('API rejects horizon "3y" with unknown horizon', async ({ request }) => {
    const res = await request.post('/api/runs', { data: { ...BASE, horizon: '3y' } });
    expect(res.status()).toBe(400);
    expect(await res.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'unknown horizon' });
  });
});

async function connectChatGpt(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

async function seriousViolations(page: Page) {
  // Scan only a settled page: wait for any running animation (e.g. snackbar fade-in) to finish.
  await page.waitForFunction(() => document.getAnimations().every((a) => a.playState !== 'running'));
  const results = await new AxeBuilder({ page }).analyze();
  return results.violations
    .filter((v) => v.impact === 'serious' || v.impact === 'critical')
    .map((v) => `${v.impact}: ${v.id} (${v.nodes.length} nodes)`);
}

/** Tabs until the element with the given test id (or its inner input) has focus. */
async function tabTo(page: Page, testId: string): Promise<void> {
  for (let i = 0; i < 80; i++) {
    await page.keyboard.press('Tab');
    const focused = await page.evaluate((id) => {
      const el = document.activeElement as HTMLElement | null;
      if (!el) return false;
      return el.getAttribute('data-testid') === id || !!el.closest(`[data-testid="${id}"]`);
    }, testId);
    if (focused) return;
  }
  throw new Error(`could not reach ${testId} with Tab`);
}

// @trace NFR-5
test.describe('NFR-5 accessibility', () => {
  test.describe.configure({ mode: 'serial' });

  test.beforeEach(async ({ request }) => {
    const r = await request.post(`${STUB}/__control/reset`);
    expect(r.status()).toBe(204);
  });

  test('axe reports no serious or critical violations on the main page', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('scenario-panel')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
    await connectChatGpt(page);
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('axe reports no serious or critical violations on a result view', async ({ page }) => {
    test.setTimeout(60_000);
    await connectChatGpt(page);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('progress-view')).toBeVisible();
    expect(await seriousViolations(page)).toEqual([]);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    expect(await seriousViolations(page)).toEqual([]);
  });

  test('keyboard-only flow sets sliders and starts generation', async ({ page }) => {
    await connectChatGpt(page);

    await tabTo(page, 'slider-darkness-input');
    await page.keyboard.press('ArrowRight');
    await page.keyboard.press('ArrowRight');
    await expect(page.getByTestId('value-darkness')).toHaveText('7');

    await tabTo(page, 'slider-optimism-input');
    await page.keyboard.press('ArrowLeft');
    await page.keyboard.press('ArrowLeft');
    await page.keyboard.press('ArrowLeft');
    await expect(page.getByTestId('value-optimism')).toHaveText('2');

    await tabTo(page, 'slider-realism-input');
    await page.keyboard.press('ArrowLeft');
    await expect(page.getByTestId('value-realism')).toHaveText('7');

    await tabTo(page, 'generate-button');
    await expect(page.getByTestId('generate-button')).toBeFocused();
    const requestPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.keyboard.press('Enter');
    const req = await requestPromise;
    expect(req.postDataJSON()).toMatchObject({ darkness: 7, optimism: 2, realism: 7 });

    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    await expect(page.getByTestId('progress-view')).toBeVisible();
    const id = page.url().split('/').pop()!;
    const run = await page.request.get(`/api/runs/${id}`);
    expect(run.status()).toBe(200);
    expect((await run.json()).configuration).toMatchObject({ darkness: 7, optimism: 2, realism: 7 });
  });
});
