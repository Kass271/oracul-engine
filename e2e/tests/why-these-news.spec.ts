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

// @trace FR-28, FR-50
test.describe('FR-28 WHY THESE NEWS? and research summary', () => {
  test('FR-28 a new run records no intents (FR-50): the panel is grouped by wildcard (FR-60) and shows the five summary numbers', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    const R = await (await page.request.get(`/api/runs/${id}/result`)).json();
    const G = await (await page.request.get(`/api/runs/${id}`)).json();
    const P = await (await page.request.get(`/api/runs/${id}/research`)).json();
    // FR-50: a new run has pipelines, not intents: research.intents is []
    expect(P.searchPlan.intents).toEqual([]);
    expect(P.searchPlan.pipelines).toHaveLength(2);
    expect(R.research.intents).toEqual([]);

    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
    await page.getByTestId('open-why-news').click();
    await expect(page.getByTestId('why-news-panel')).toBeVisible();

    // wildcard-result-views.md slice 09: one group per wildcard with its queries, no "No research intents recorded", no intent element
    await expect(page.getByTestId('why-news-empty')).toHaveCount(0);
    const testids = (re: string) =>
      page.evaluate(
        (source) =>
          Array.from(document.querySelectorAll('[data-testid]'))
            .map((n) => n.getAttribute('data-testid')!)
            .filter((t) => new RegExp(source).test(t)),
        re,
      );
    expect(await testids('^why-news-intent-')).toEqual([]);
    expect(await testids('^why-news-group-W\\d+$')).toEqual(P.searchPlan.pipelines.map((p: any) => `why-news-group-${p.id}`));
    for (const p of P.searchPlan.pipelines) {
      await expect(page.getByTestId(`why-news-group-title-${p.id}`)).toHaveText(p.heading);
      for (const q of p.queries) {
        await expect(page.getByTestId(`why-news-query-text-${q.id}`)).toHaveText(q.text);
        await expect(page.getByTestId(`why-news-query-status-${q.id}`)).toHaveText(q.status);
        await expect(page.getByTestId(`why-news-query-count-${q.id}`)).toHaveText(plural(q.articlesReturned, 'item', 'items'));
      }
    }

    // the five summary numbers equal GET /api/runs/{id} counts; the six legacy lines are gone
    const c = G.counts;
    expect(c.searches).toBe(6);
    await expect(page.getByTestId('summary-searches')).toHaveText(plural(c.searches, 'search performed', 'searches performed'));
    await expect(page.getByTestId('summary-articles')).toHaveText(plural(c.articlesConsidered, 'article considered', 'articles considered'));
    await expect(page.getByTestId('summary-kept')).toHaveText(plural(c.sourcesKept, 'source kept', 'sources kept'));
    await expect(page.getByTestId('summary-content')).toHaveText(plural(c.sourcesWithContent, 'source with content', 'sources with content'));
    const used = new Set(R.wildcardGroups.flatMap((g: any) => g.sources.filter((s: any) => s.usedInScenario).map((s: any) => s.evidenceId)));
    expect(used.size).toBeGreaterThanOrEqual(1);
    await expect(page.getByTestId('summary-used')).toHaveText(plural(used.size, 'source used in the scenario', 'sources used in the scenario'));
    await expect(page.getByTestId('summary-used')).toHaveText(plural(c.sourcesUsed, 'source used in the scenario', 'sources used in the scenario'));
    for (const legacy of ['summary-events', 'summary-selected', 'summary-counter-signals', 'summary-sources-used']) {
      await expect(page.getByTestId(legacy)).toHaveCount(0);
    }
    await evidence(page, 'FR-28', 'why-these-news-panel');

    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('why-news-panel')).toHaveCount(0);
  });
});
