import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

type Mode = 'ok' | 'not_eligible' | 'deny' | 'token_error' | 'refresh_error';

async function setMode(request: APIRequestContext, mode: Mode): Promise<void> {
  const r = await request.post(`${STUB}/__control/mode`, { data: { mode } });
  expect(r.status()).toBe(204);
}

async function signIn(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
}

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
});

// @trace FR-7
test.describe('FR-7 Continue with ChatGPT', () => {
  test('sign-in round trip ends connected with a snackbar and a clean URL', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connected');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await expect(page.getByTestId('chatgpt-disconnect')).toHaveText('Disconnect');
    await expect(page.getByTestId('chatgpt-connect')).toHaveCount(0);
    expect(new URL(page.url()).search).toBe('');
    expect(new URL(page.url()).pathname).toBe('/');
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await evidence(page, 'FR-7', 'connected');
  });

  test('the connection survives a page reload', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await page.reload();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
  });

  test('denying access ends not connected with the not-completed snackbar', async ({ page, request }) => {
    await setMode(request, 'deny');
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    expect(new URL(page.url()).search).toBe('');
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await evidence(page, 'FR-7', 'not-completed');
  });

  test('a failing token endpoint ends not connected', async ({ page, request }) => {
    await setMode(request, 'token_error');
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  });

  test('a callback with an unknown state ends not connected', async ({ page }) => {
    await page.goto('/');
    await page.goto('http://127.0.0.1:4200/auth/callback?code=x&state=unknown');
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  });
});

// @trace FR-8
test.describe('FR-8 connection status and sign-out', () => {
  test('a fresh visitor sees Not connected and the generate button stays disabled', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    await expect(page.getByTestId('chatgpt-connect')).toHaveAttribute('href', '/api/auth/chatgpt/authorize');
    await expect(page.getByTestId('generate-button')).toBeDisabled();
    await evidence(page, 'FR-8', 'not-connected');
  });

  test('Disconnect returns to Not connected', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-disconnect')).toBeVisible();
    await page.getByTestId('chatgpt-disconnect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    await expect(page.getByTestId('chatgpt-disconnect')).toHaveCount(0);
    const state = await page.evaluate(async () => (await fetch('/api/auth/chatgpt/connection')).json());
    expect(state).toEqual({ state: 'NOT_CONNECTED', canGenerate: false });
    await evidence(page, 'FR-8', 'disconnected');
  });

  test('a plan without the required scope shows Plan not eligible', async ({ page, request }) => {
    await setMode(request, 'not_eligible');
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('Your ChatGPT plan is not eligible for ORACUL');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Plan not eligible');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    await expect(page.getByTestId('chatgpt-disconnect')).toHaveCount(0);
    await evidence(page, 'FR-8', 'plan-not-eligible');
  });

  test('a failing connection request shows Not connected without a snackbar', async ({ page }) => {
    await page.route('**/api/auth/chatgpt/connection', (r) =>
      r.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}' }),
    );
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toBeVisible();
    await expect(page.getByTestId('chatgpt-message')).toHaveCount(0);
  });

  test('a failing DELETE shows the error snackbar and keeps the connection', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await page.route('**/api/auth/chatgpt/connection', (r) =>
      r.request().method() === 'DELETE'
        ? r.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}' })
        : r.continue(),
    );
    await page.getByTestId('chatgpt-disconnect').click();
    await expect(page.getByTestId('chatgpt-message')).toHaveText('Something went wrong — try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
  });
});

// @trace FR-9
test.describe('FR-9 runtime-only credentials', () => {
  test('no token, code or verifier reaches browser storage, cookies, the page or the API', async ({ page, request }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    const issued = ((await (await request.get(`${STUB}/__control/issued`)).json()) as { values: string[] }).values;
    expect(issued.length).toBeGreaterThan(0);

    const seen = await page.evaluate(async () => {
      const api = await (await fetch('/api/auth/chatgpt/connection')).text();
      return JSON.stringify({
        local: { ...localStorage },
        session: { ...sessionStorage },
        cookie: document.cookie,
        html: document.documentElement.outerHTML,
        api,
        url: location.href,
        dbs: (await indexedDB.databases?.()) ?? [],
      });
    });
    for (const secret of issued) expect(seen).not.toContain(secret);
    expect(seen).not.toContain('STUBSECRET');
    const parsed = JSON.parse(seen) as { local: object; session: object; dbs: unknown[] };
    expect(Object.keys(parsed.local)).toHaveLength(0);
    expect(Object.keys(parsed.session)).toHaveLength(0);
    expect(parsed.dbs).toHaveLength(0);
    await evidence(page, 'FR-9', 'no-credentials-in-browser');
  });
});
