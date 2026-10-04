import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const NAME = 'Wildcard name must be 1–40 characters';

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
});

async function addCustom(page: Page, label: string, viaEnter = false): Promise<void> {
  const input = page.getByTestId('custom-wildcard-input');
  await input.fill(label);
  if (viaEnter) await input.press('Enter');
  else await page.getByTestId('custom-wildcard-add').click();
}

async function rowCount(page: Page): Promise<number> {
  return page.locator('[data-testid^="custom-wildcard-label-"]').count();
}

// @trace FR-5
test.describe('FR-5 custom wildcards', () => {
  test('user adds, limits, removes and rejects duplicates', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('custom-wildcard-section')).toBeVisible();

    await addCustom(page, 'Ocean desalination boom');
    await expect(page.getByTestId('custom-wildcard-label-0')).toHaveText('Ocean desalination boom 5/10');
    await expect(page.getByTestId('custom-wildcard-input')).toHaveValue('');
    await evidence(page, 'FR-5', 'custom-wildcard-added');

    await addCustom(page, '');
    await expect(page.getByTestId('custom-wildcard-error')).toHaveText(NAME);
    await addCustom(page, 'a'.repeat(41));
    await expect(page.getByTestId('custom-wildcard-error')).toHaveText(NAME);
    expect(await rowCount(page)).toBe(1);

    await addCustom(page, 'Mars colony', true);
    await expect(page.getByTestId('custom-wildcard-label-1')).toHaveText('Mars colony 5/10');
    await addCustom(page, 'Fusion towns');
    await expect(page.getByTestId('custom-wildcard-label-2')).toHaveText('Fusion towns 5/10');
    expect(await rowCount(page)).toBe(3);

    await addCustom(page, 'Fourth');
    await expect(page.getByTestId('custom-wildcard-error')).toHaveText('At most 3 custom wildcards');
    expect(await rowCount(page)).toBe(3);
    await evidence(page, 'FR-5', 'custom-wildcard-limit');

    await page.getByTestId('custom-wildcard-remove-0').click();
    await expect(page.getByTestId('custom-wildcard-label-2')).toHaveCount(0);
    expect(await rowCount(page)).toBe(2);
    await expect(page.getByTestId('custom-wildcard-label-0')).toHaveText('Mars colony 5/10');

    await addCustom(page, 'mars colony');
    await expect(page.getByTestId('custom-wildcard-error')).toHaveText('This wildcard already exists');
    expect(await rowCount(page)).toBe(2);
  });

  test('connected run sends the custom wildcard and the fixed output', async ({ page }) => {
    test.setTimeout(90_000);
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await page.getByTestId('chatgpt-connect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');

    await addCustom(page, 'Ocean desalination boom');
    await page.getByTestId('custom-wildcard-intensity-0-input').fill('7');
    await expect(page.getByTestId('custom-wildcard-label-0')).toHaveText('Ocean desalination boom 7/10');

    const requestPromise = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.getByTestId('generate-button').click();
    const req = await requestPromise;
    const body = req.postDataJSON();
    expect(body.customWildcards).toEqual([{ label: 'Ocean desalination boom', intensity: 7 }]);
    expect(body.output).toEqual({ story: true, illustration: false });
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);

    // Never leave the run in flight: it would pollute the shared stub log of later specs.
    const runId = new URL(page.url()).pathname.split('/').pop();
    let run: { status?: string; configuration?: { customWildcards?: unknown } } = {};
    await expect
      .poll(
        async () => {
          const r = await page.request.get(`/api/runs/${runId}`);
          run = r.ok() ? await r.json() : {};
          return run.status;
        },
        { timeout: 40_000, intervals: [500, 1000] },
      )
      .toMatch(/^(COMPLETED|FAILED|INSUFFICIENT_EVIDENCE)$/);
    expect(run.configuration?.customWildcards).toEqual([{ label: 'Ocean desalination boom', intensity: 7 }]);
  });

  test('API rejects invalid customWildcards', async ({ request }) => {
    const base = { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], output: { story: true, illustration: false } };
    const four = ['A', 'B', 'C', 'D'].map((label) => ({ label, intensity: 5 }));
    const cases: [unknown, string][] = [
      [four, 'At most 3 custom wildcards'],
      [[{ label: '', intensity: 5 }], NAME],
      [[{ label: 'a'.repeat(41), intensity: 5 }], NAME],
      [[{ label: 'A', intensity: 5 }, { label: ' a ', intensity: 5 }], 'This wildcard already exists'],
      [[{ label: 'A', intensity: 11 }], 'wildcard intensity must be between 1 and 10'],
      [null, 'customWildcards is invalid'],
    ];
    for (const [customWildcards, message] of cases) {
      const r = await request.post('/api/runs', { data: { ...base, customWildcards } });
      expect(r.status(), message).toBe(400);
      expect(await r.json()).toEqual({ code: 'VALIDATION_FAILED', message });
    }
  });
});

// @trace FR-6
test.describe('FR-6 output settings', () => {
  test('Story is fixed on, Illustration is disabled with the MVP+1 badge', async ({ page }) => {
    await page.goto('/');
    const story = page.getByTestId('output-story').locator('input[type="checkbox"]');
    const illustration = page.getByTestId('output-illustration').locator('input[type="checkbox"]');
    await expect(page.getByTestId('output-section')).toBeVisible();
    await expect(story).toBeChecked();
    await expect(illustration).not.toBeChecked();
    await expect(illustration).toBeDisabled();
    await expect(page.getByTestId('output-illustration-badge')).toHaveText('MVP+1');
    await evidence(page, 'FR-6', 'output-settings');

    await page.getByTestId('output-illustration').click({ force: true });
    await expect(illustration).not.toBeChecked();
    await page.getByTestId('output-story').click();
    await expect(story).toBeChecked();
  });

  test('API rejects invalid output', async ({ request }) => {
    const base = { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], customWildcards: [] };
    const cases: [unknown, string][] = [
      [{ story: false, illustration: false }, 'Story output is required'],
      [{ story: false, illustration: true }, 'Story output is required'],
      [{ story: true, illustration: true }, 'Illustration is not available yet (MVP+1)'],
    ];
    for (const [output, message] of cases) {
      const r = await request.post('/api/runs', { data: { ...base, output } });
      expect(r.status(), message).toBe(400);
      expect(await r.json()).toEqual({ code: 'VALIDATION_FAILED', message });
    }
  });
});
