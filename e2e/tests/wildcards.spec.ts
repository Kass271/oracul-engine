import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const CATEGORIES: Record<string, string[]> = {
  ai: ['ai-agi-breakthrough', 'ai-stagnation', 'ai-loss-of-control'],
  robotics: ['robotics-massive-automation', 'robotics-humanoid-boom', 'robotics-robot-uprising'],
  biology: ['biology-new-pandemic', 'biology-dangerous-mutation', 'biology-medical-breakthrough', 'biology-synthetic-biology'],
  political: ['political-democracy-strengthens', 'political-authoritarian-expansion', 'political-international-institutions', 'political-global-fragmentation'],
  economy: ['economy-global-boom', 'economy-global-recession', 'economy-financial-crisis'],
  energy: ['energy-fusion-breakthrough', 'energy-cheap-energy', 'energy-energy-crisis'],
  environment: ['environment-extreme-climate-event', 'environment-climate-stabilization', 'environment-ecosystem-collapse'],
  space: ['space-major-discovery', 'space-asteroid-threat', 'space-moon-settlement', 'space-mars-breakthrough'],
  extreme: ['extreme-alien-contact', 'extreme-unknown-intelligence', 'extreme-unexplained-phenomenon'],
};

const BASE = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [] as unknown[],
  customWildcards: [],
  output: { story: true, illustration: false },
};

const toggle = (page: Page, id: string) => page.getByTestId(`wildcard-toggle-${id}`).getByRole('switch');

async function expand(page: Page, category: string): Promise<void> {
  await page.getByTestId(`wildcard-category-header-${category}`).click();
  await expect(page.getByTestId(`wildcard-category-${category}`)).toHaveClass(/mat-expanded/);
}

// @trace FR-4
test.describe('FR-4 wildcard catalogue', () => {
  test('fresh page: every wildcard is off and grouped under its category', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('wildcard-section')).toBeVisible();
    for (const [category, ids] of Object.entries(CATEGORIES)) {
      await expect(page.getByTestId(`wildcard-category-${category}`)).not.toHaveClass(/mat-expanded/);
      await expand(page, category);
      for (const id of ids) {
        const row = page.getByTestId(`wildcard-category-${category}`).getByTestId(`wildcard-toggle-${id}`);
        await expect(row).toBeVisible();
        await expect(toggle(page, id)).not.toBeChecked();
      }
    }
    await expect(page.locator('[data-testid^="wildcard-intensity-"]')).toHaveCount(0);
    await evidence(page, 'FR-4', 'all-off-grouped');
  });

  test('enable New pandemic, set 8 -> "New pandemic 8/10"; disable removes it', async ({ page }) => {
    await page.goto('/');
    await expand(page, 'biology');
    await expect(page.getByTestId('wildcard-label-biology-new-pandemic')).toHaveText('New pandemic');

    await toggle(page, 'biology-new-pandemic').click();
    await expect(toggle(page, 'biology-new-pandemic')).toBeChecked();
    await expect(page.getByTestId('wildcard-intensity-biology-new-pandemic-input')).toHaveValue('5');
    await expect(page.getByTestId('wildcard-label-biology-new-pandemic')).toHaveText('New pandemic 5/10');

    await page.getByTestId('wildcard-intensity-biology-new-pandemic-input').fill('8');
    await expect(page.getByTestId('wildcard-label-biology-new-pandemic')).toHaveText('New pandemic 8/10');
    await evidence(page, 'FR-4', 'new-pandemic-8');

    await toggle(page, 'biology-new-pandemic').click();
    await expect(toggle(page, 'biology-new-pandemic')).not.toBeChecked();
    await expect(page.getByTestId('wildcard-label-biology-new-pandemic')).toHaveText('New pandemic');
    await expect(page.getByTestId('wildcard-intensity-biology-new-pandemic')).toHaveCount(0);

    await toggle(page, 'biology-new-pandemic').click();
    await expect(page.getByTestId('wildcard-label-biology-new-pandemic')).toHaveText('New pandemic 5/10');
  });

  test('API rejects an unknown wildcard id', async ({ request }) => {
    const res = await request.post('/api/runs', {
      data: { ...BASE, wildcards: [{ wildcardId: 'biology-zombies', intensity: 5 }] },
    });
    expect(res.status()).toBe(400);
    expect(await res.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'unknown wildcard: biology-zombies' });
  });

  test('API rejects a wildcard intensity of 11 and a duplicate id', async ({ request }) => {
    const bad = await request.post('/api/runs', {
      data: { ...BASE, wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 11 }] },
    });
    expect(bad.status()).toBe(400);
    expect(await bad.json()).toEqual({
      code: 'VALIDATION_FAILED',
      message: 'wildcard intensity must be between 1 and 10',
    });
    const dup = await request.post('/api/runs', {
      data: {
        ...BASE,
        wildcards: [
          { wildcardId: 'biology-new-pandemic', intensity: 5 },
          { wildcardId: 'biology-new-pandemic', intensity: 7 },
        ],
      },
    });
    expect(dup.status()).toBe(400);
    expect(await dup.json()).toEqual({
      code: 'VALIDATION_FAILED',
      message: 'duplicate wildcard: biology-new-pandemic',
    });
  });
});
