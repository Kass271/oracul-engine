import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

async function noOverflow(page: Page, width: number): Promise<void> {
  const m = await page.evaluate(() => ({
    sw: document.documentElement.scrollWidth,
    iw: window.innerWidth,
  }));
  expect(m.sw).toBeLessThanOrEqual(m.iw);
  const box = await page.locator('mat-sidenav-content').boundingBox();
  expect(Math.abs(box!.x)).toBeLessThanOrEqual(1);
  expect(Math.abs(box!.width - width)).toBeLessThanOrEqual(1);
}

async function headerWithin(page: Page, width: number): Promise<void> {
  const boxes = await page.getByTestId('app-header').locator('> *').evaluateAll((els) =>
    els.map((e) => {
      const r = e.getBoundingClientRect();
      return { l: r.left, r: r.right, w: r.width };
    }),
  );
  for (const b of boxes) {
    if (b.w === 0) continue;
    expect(b.l).toBeGreaterThanOrEqual(-1);
    expect(b.r).toBeLessThanOrEqual(width + 1);
  }
}

// @trace FR-34
test.describe('FR-34 mobile layout', () => {
  test('390 px: closed drawer, welcome view, no horizontal scroll', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    await expect(page.getByTestId('scenario-drawer-toggle')).toBeVisible();
    await expect(page.getByTestId('scenario-drawer-toggle')).toHaveText('Scenario');
    await expect(page.getByTestId('scenario-panel')).toBeHidden();
    await expect(page.getByTestId('welcome-view')).toBeVisible();
    await noOverflow(page, 390);
    await evidence(page, 'FR-34', 'mobile-closed');
  });

  test('open, edit, close via button / Escape / backdrop keeps values', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    const toggle = page.getByTestId('scenario-drawer-toggle');
    const panel = page.getByTestId('scenario-panel');
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.click();
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await evidence(page, 'FR-34', 'mobile-drawer-open');
    await page.getByTestId('slider-darkness-input').fill('9');
    await page.getByTestId('scenario-drawer-close').click();
    await expect(panel).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.click();
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('value-darkness')).toHaveText('9');
    await page.keyboard.press('Escape');
    await expect(panel).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.click();
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('value-darkness')).toHaveText('9');
    await page.locator('.mat-drawer-backdrop').click({ position: { x: 380, y: 400 } });
    await expect(panel).toBeHidden();
    await expect(toggle).toHaveAttribute('aria-expanded', 'false');
    await toggle.click();
    await expect(panel).toBeVisible();
    await expect(toggle).toHaveAttribute('aria-expanded', 'true');
    await expect(page.getByTestId('value-darkness')).toHaveText('9');
  });

  for (const width of [360, 414, 767]) {
    test(`${width} px is mobile`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/');
      await expect(page.getByTestId('scenario-drawer-toggle')).toBeVisible();
      await expect(page.getByTestId('scenario-panel')).toBeHidden();
      await noOverflow(page, width);
      await headerWithin(page, width);
    });
  }

  for (const width of [768, 1280]) {
    test(`${width} px is desktop`, async ({ page }) => {
      await page.setViewportSize({ width, height: 844 });
      await page.goto('/');
      await expect(page.getByTestId('scenario-panel')).toBeVisible();
      await expect(page.getByTestId('scenario-drawer-toggle')).toHaveCount(0);
      await expect(page.getByTestId('scenario-drawer-close')).toHaveCount(0);
    });
  }
});
