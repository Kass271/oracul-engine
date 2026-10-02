import { expect, test } from '@playwright/test';

test('app shell loads and reaches the backend', async ({ page }) => {
  await page.goto('/');
  await expect(page.getByTestId('app-header')).toBeVisible();
  await expect(page.getByTestId('app-wordmark')).toHaveText('ORACUL');

  const res = await page.request.get('/api/ping');
  expect(res.status()).toBe(200);
  expect(await res.json()).toEqual({ message: 'pong' });
});
