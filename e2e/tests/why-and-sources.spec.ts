import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
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

/** Acceptance configuration A through the panel (realism stays 8). */
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

// @trace FR-26, FR-27
test.describe('FR-26 / FR-27 WHY and SOURCES panels', () => {
  test('FR-26 WHY COULD THIS HAPPEN? shows the chain and a chip reveals its source', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    const chain = result.causalChain;
    const e1: string = chain[0].evidenceIds[0];
    expect(chain.length).toBe(4);

    await expect(page.getByTestId('why-panel')).toHaveCount(0);
    await page.getByTestId('open-why').click();
    await expect(page.getByTestId('why-panel')).toBeVisible();
    const stepCount = await page.evaluate(
      () => Array.from(document.querySelectorAll('[data-testid]')).filter((n) => /^why-step-\d+$/.test(n.getAttribute('data-testid')!)).length,
    );
    expect(stepCount).toBe(chain.length);
    await expect(page.getByTestId('why-step-class-1')).toHaveText('FACT');
    await expect(page.getByTestId('why-step-class-2')).toHaveText('INFERENCE');
    await expect(page.getByTestId('why-step-class-3')).toHaveText('SPECULATION');
    await expect(page.getByTestId('why-step-class-4')).toHaveText(`ORACUL FUTURE — ${chain[3].year}`);
    await expect(page.getByTestId('why-step-statement-1')).toHaveText(chain[0].statement);

    const chip = page.getByTestId(`why-evidence-1-${e1}`);
    await expect(chip).toBeVisible();
    await expect(chip).toBeEnabled();
    await chip.click();
    await expect(page.getByTestId('sources-panel')).toBeVisible();
    const item = page.getByTestId(`source-item-${e1}`);
    await expect(item).toBeInViewport();
    await expect(item).toHaveClass(/highlighted/);
    await evidence(page, 'FR-26', 'why-chain-highlighted-source');
    await page.waitForTimeout(3500);
    await expect(page.locator('[data-highlighted="true"]')).toHaveCount(0);
  });

  test('FR-27 SOURCES lists every evidence item with links and marks', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    const sources: any[] = result.sources;
    const e1: string = result.causalChain[0].evidenceIds[0];
    expect(sources.length).toBeGreaterThan(0);

    await expect(page.getByTestId('sources-panel')).toHaveCount(0);
    await page.getByTestId('open-sources').click();
    await expect(page.getByTestId('sources-panel')).toBeVisible();
    const itemIds = await page.evaluate(() =>
      Array.from(document.querySelectorAll('[data-testid]'))
        .map((n) => n.getAttribute('data-testid')!)
        .filter((t) => /^source-item-E\d+$/.test(t)),
    );
    expect(itemIds).toEqual(sources.map((s) => `source-item-${s.evidenceId}`));

    for (const s of sources) {
      await expect(page.getByTestId(`source-id-${s.evidenceId}`)).toHaveText(s.evidenceId);
      await expect(page.getByTestId(`source-title-${s.evidenceId}`)).toHaveText(s.title);
      await expect(page.getByTestId(`source-publisher-${s.evidenceId}`)).toHaveText(s.publisher);
      const link = page.getByTestId(`source-link-${s.evidenceId}`);
      await expect(link).toHaveAttribute('href', s.url);
      await expect(link).toHaveAttribute('target', '_blank');
      await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
      await expect(page.getByTestId(`source-used-${s.evidenceId}`)).toHaveCount(s.usedInScenario ? 1 : 0);
      await expect(page.getByTestId(`source-counter-${s.evidenceId}`)).toHaveCount(s.section === 'COUNTER_SIGNAL' ? 1 : 0);
    }
    await expect(page.getByTestId(`source-used-${e1}`)).toHaveCount(1);
    await expect(page.locator('[data-testid^="source-used-"]')).toHaveCount(run.counts.sourcesUsed);
    await expect(page.locator('[data-testid^="source-counter-"]')).toHaveCount(run.counts.counterSignals);
    await evidence(page, 'FR-27', 'sources-panel');
  });
});
