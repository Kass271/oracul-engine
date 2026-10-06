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

const plural = (n: number, one: string, many: string): string => `${n} ${n === 1 ? one : many}`;

// @trace FR-28
test.describe('FR-28 WHY THESE NEWS? and research summary', () => {
  test('FR-28 panel lists intents with attributions and the six summary numbers', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    const R = await (await page.request.get(`/api/runs/${id}/result`)).json();
    const G = await (await page.request.get(`/api/runs/${id}`)).json();
    const P = await (await page.request.get(`/api/runs/${id}/research`)).json();
    const intents: any[] = P.searchPlan.intents;
    expect(intents.length).toBe(6);

    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('why-news-panel')).toBeVisible();

    const itemIds = await page.evaluate(() =>
      Array.from(document.querySelectorAll('[data-testid]'))
        .map((n) => n.getAttribute('data-testid')!)
        .filter((t) => /^why-news-intent-I\d+$/.test(t)),
    );
    expect(itemIds).toEqual(intents.map((i) => `why-news-intent-${i.id}`));

    for (const i of intents) {
      await expect(page.getByTestId(`why-news-description-${i.id}`)).toHaveText(i.description);
      for (let k = 0; k < i.drivenBy.length; k++) {
        await expect(page.getByTestId(`why-news-driver-${i.id}-${k}`)).toHaveText(i.drivenBy[k]);
      }
      await expect(page.locator(`[data-testid^="why-news-driver-${i.id}-"]`)).toHaveCount(i.drivenBy.length);
    }
    const i01 = page.getByTestId('why-news-intent-I01');
    await expect(i01).toContainText('New pandemic 8/10');
    await expect(i01).toContainText('Darkness 9/10');
    await expect(page.getByTestId('why-news-intent-I02')).toContainText('Humanoid robot boom 6/10');

    const c = G.counts;
    expect(c.searches).toBe(20);
    await expect(page.getByTestId('summary-searches')).toHaveText(plural(c.searches, 'search performed', 'searches performed'));
    await expect(page.getByTestId('summary-articles')).toHaveText(plural(c.articlesConsidered, 'article considered', 'articles considered'));
    await expect(page.getByTestId('summary-events')).toHaveText(plural(c.uniqueEvents, 'unique event identified', 'unique events identified'));
    await expect(page.getByTestId('summary-selected')).toHaveText(plural(R.sources.length, 'event selected', 'events selected'));
    await expect(page.getByTestId('summary-selected')).toHaveText(plural(c.eventsSelected, 'event selected', 'events selected'));
    const counter = R.sources.filter((s: any) => s.counterSignal).length;
    const used = R.sources.filter((s: any) => s.usedInScenario).length;
    expect(used).toBeGreaterThanOrEqual(1);
    await expect(page.getByTestId('summary-counter-signals')).toHaveText(plural(counter, 'counter-signal retained', 'counter-signals retained'));
    await expect(page.getByTestId('summary-counter-signals')).toHaveText(plural(c.counterSignals, 'counter-signal retained', 'counter-signals retained'));
    await expect(page.getByTestId('summary-sources-used')).toHaveText(
      plural(used, 'source directly influenced the scenario', 'sources directly influenced the scenario'),
    );
    await expect(page.getByTestId('summary-sources-used')).toHaveText(
      plural(c.sourcesUsed, 'source directly influenced the scenario', 'sources directly influenced the scenario'),
    );
    await evidence(page, 'FR-28', 'why-these-news-panel');

    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
  });
});
