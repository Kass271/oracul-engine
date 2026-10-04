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

// @trace FR-7, FR-36
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
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed — please try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    expect(new URL(page.url()).search).toBe('');
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await evidence(page, 'FR-7', 'not-completed');
  });

  test('a failing token endpoint ends not connected', async ({ page, request }) => {
    await setMode(request, 'token_error');
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed — please try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  });

  test('a callback with an unknown state ends not connected', async ({ page }) => {
    await page.goto('/');
    // @trace FR-36  the registered loopback redirect is /callback (the old /auth/callback is gone)
    await page.goto('http://127.0.0.1:4200/callback?code=x&state=unknown');
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed — please try again');
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


// ---- phase 02, slice 01_signin-fix ----

const CONDITIONS =
  'Signing in needs a personal ChatGPT Plus or Pro account. Open ORACUL in a browser on the same computer where ORACUL runs.';

/** Records the query of every request the browser sends to the OpenAI authorize endpoint (the E2E stub). */
function recordAuthorizeRequests(page: Page): URLSearchParams[] {
  const seen: URLSearchParams[] = [];
  page.on('request', (r) => {
    const u = new URL(r.url());
    if (u.pathname === '/oauth/authorize') seen.push(u.searchParams);
  });
  return seen;
}

async function resetRegistration(request: APIRequestContext): Promise<void> {
  const r = await request.delete('/api/auth/chatgpt/registration');
  expect(r.status()).toBe(204);
}

// @trace FR-35
test.describe('FR-35 documented authorize request', () => {
  test('first registration and reauthorization send the documented parameters', async ({ page, request }) => {
    await resetRegistration(request);
    const seen = recordAuthorizeRequests(page);

    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    expect(seen).toHaveLength(1);
    const first = seen[0];
    expect([...first.keys()].sort()).toEqual(
      ['agent_name_hint', 'client_id', 'code_challenge', 'code_challenge_method', 'ext_agent_host_id', 'nonce',
        'redirect_uri', 'resource', 'response_type', 'scope', 'state'].sort(),
    );
    expect(first.get('client_id')).toBe('dynamic_agent_client');
    expect(first.get('agent_name_hint')).toBe('ORACUL');
    expect(first.get('ext_agent_host_id')).toMatch(/^urn:uuid:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/);
    expect(first.get('redirect_uri')).toBe('http://127.0.0.1:4200/callback');
    expect(first.get('response_type')).toBe('code');
    expect(first.get('scope')).toBe('openid profile email offline_access resource.invoke chatgpt.tokens.use.direct');
    expect(first.get('resource')).toBe('https://api.openai.com/v1');
    expect(first.get('code_challenge_method')).toBe('S256');
    for (const name of ['state', 'nonce', 'code_challenge']) expect(first.get(name)).toMatch(/^[A-Za-z0-9_-]{43}$/);
    await evidence(page, 'FR-35', 'first-registration-connected');

    // reauthorization: stored issued client id, no agent_name_hint, same host id, fresh state/nonce/challenge
    await page.getByTestId('chatgpt-disconnect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await page.getByTestId('chatgpt-connect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    expect(seen).toHaveLength(2);
    const second = seen[1];
    expect(second.get('client_id')).toBe('oaiapp_stub_client');
    expect(second.has('agent_name_hint')).toBe(false);
    expect(second.get('ext_agent_host_id')).toBe(first.get('ext_agent_host_id'));
    expect(second.get('redirect_uri')).toBe(first.get('redirect_uri'));
    for (const name of ['state', 'nonce', 'code_challenge']) expect(second.get(name)).not.toBe(first.get(name));
    expect(second.has('id_token_hint')).toBe(false);
    expect(second.has('login_hint')).toBe(false);
    await evidence(page, 'FR-35', 'reauthorization-connected');
  });
});

// @trace FR-36
test.describe('FR-36 callback handling', () => {
  test('the loopback /callback is answered by the backend and redirects to ORACUL', async ({ request }) => {
    const r = await request.get('http://127.0.0.1:4200/callback?code=x&state=unknown', { maxRedirects: 0 });
    expect(r.status()).toBe(302);
    expect(r.headers()['location']).toBe('http://localhost:4200/?chatgpt=not_completed');
  });

  test('a first registration returns connected with the issued client id', async ({ page, request }) => {
    await resetRegistration(request);
    await signIn(page);
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connected');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    expect(new URL(page.url()).search).toBe('');
    await evidence(page, 'FR-36', 'connected');
  });

  test('an unknown state shows the not-completed message', async ({ page }) => {
    await page.goto('http://127.0.0.1:4200/callback?code=x&state=unknown');
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection was not completed — please try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    expect(new URL(page.url()).search).toBe('');
    await evidence(page, 'FR-36', 'not-completed');
  });

  test('not_verified and expired outcomes show their messages', async ({ page }) => {
    await page.goto('/?chatgpt=not_verified');
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT sign-in could not be verified — please try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await evidence(page, 'FR-36', 'not-verified');
    await page.goto('/?chatgpt=expired');
    await expect(page.getByTestId('chatgpt-message')).toHaveText('Sign-in expired — click Continue with ChatGPT to start again');
    expect(new URL(page.url()).search).toBe('');
    await evidence(page, 'FR-36', 'expired');
  });

  test('the old /auth/callback path is no longer forwarded to the backend', async ({ request }) => {
    const r = await request.get('http://127.0.0.1:4200/auth/callback?code=x&state=unknown', { maxRedirects: 0 });
    expect(r.status()).toBe(200); // the SPA fallback, not a backend redirect
    expect(r.headers()['location']).toBeUndefined();
  });
});

// @trace FR-37
test.describe('FR-37 reset ChatGPT connection', () => {
  async function openResetDialog(page: Page): Promise<void> {
    await page.getByTestId('chatgpt-menu').click();
    await page.getByTestId('chatgpt-reset').click();
    await expect(page.getByTestId('chatgpt-reset-dialog')).toBeVisible();
  }

  test('reset forgets the registration; the next sign-in is a first registration with the same host id', async ({ page, request }) => {
    await resetRegistration(request);
    const seen = recordAuthorizeRequests(page);
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    const hostId = seen[0].get('ext_agent_host_id');

    await page.getByTestId('chatgpt-menu').click();
    await expect(page.getByTestId('chatgpt-reset')).toHaveText('Reset ChatGPT connection');
    await page.getByTestId('chatgpt-reset').click();
    await expect(page.getByTestId('chatgpt-reset-dialog')).toBeVisible();
    await expect(page.getByText('Reset ChatGPT connection?')).toBeVisible();
    await expect(page.getByTestId('chatgpt-reset-cancel')).toHaveText('Cancel');
    await expect(page.getByTestId('chatgpt-reset-confirm')).toHaveText('Reset');
    await evidence(page, 'FR-37', 'reset-dialog');

    // Cancel changes nothing
    await page.getByTestId('chatgpt-reset-cancel').click();
    await expect(page.getByTestId('chatgpt-reset-dialog')).toHaveCount(0);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');

    await openResetDialog(page);
    await page.getByTestId('chatgpt-reset-confirm').click();
    await expect(page.getByTestId('chatgpt-message')).toHaveText('ChatGPT connection reset');
    await expect(page.getByTestId('chatgpt-reset-dialog')).toHaveCount(0);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-connect')).toHaveText('Continue with ChatGPT');
    await evidence(page, 'FR-37', 'reset-done');

    await page.getByTestId('chatgpt-connect').click();
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    const again = seen[seen.length - 1];
    expect(again.get('client_id')).toBe('dynamic_agent_client');
    expect(again.get('agent_name_hint')).toBe('ORACUL');
    expect(again.get('ext_agent_host_id')).toBe(hostId);
  });

  test('Escape and the backdrop close the dialog without a request', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    let deletes = 0;
    page.on('request', (r) => {
      if (r.method() === 'DELETE' && r.url().endsWith('/api/auth/chatgpt/registration')) deletes++;
    });
    await openResetDialog(page);
    await page.keyboard.press('Escape');
    await expect(page.getByTestId('chatgpt-reset-dialog')).toHaveCount(0);
    await openResetDialog(page);
    await page.locator('.cdk-overlay-dark-backdrop').click({ position: { x: 5, y: 5 } });
    await expect(page.getByTestId('chatgpt-reset-dialog')).toHaveCount(0);
    expect(deletes).toBe(0);
  });

  test('a run in progress (409) shows the wait message and keeps the connection', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await page.route('**/api/auth/chatgpt/registration', (r) =>
      r.fulfill({ status: 409, contentType: 'application/json', body: '{"code":"RUN_IN_PROGRESS","message":"Wait until the current run finishes"}' }),
    );
    await openResetDialog(page);
    await page.getByTestId('chatgpt-reset-confirm').click();
    await expect(page.getByTestId('chatgpt-message')).toHaveText('Wait until the current run finishes');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await evidence(page, 'FR-37', 'run-in-progress');
  });

  test('a server error shows the generic message', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await page.route('**/api/auth/chatgpt/registration', (r) =>
      r.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}' }),
    );
    await openResetDialog(page);
    await page.getByTestId('chatgpt-reset-confirm').click();
    await expect(page.getByTestId('chatgpt-message')).toHaveText('Something went wrong — try again');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  });
});

// @trace FR-41
test.describe('FR-41 sign-in conditions', () => {
  test('a visitor sees the conditions below the generate hint and no secret input anywhere', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('generate-hint')).toHaveText('Connect ChatGPT to generate');
    await expect(page.getByTestId('chatgpt-conditions')).toHaveCount(1);
    await expect(page.getByTestId('chatgpt-conditions')).toHaveText(CONDITIONS);
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await evidence(page, 'FR-41', 'conditions');

    await page.getByTestId('chatgpt-menu').click();
    await page.getByTestId('chatgpt-reset').click();
    await expect(page.getByTestId('chatgpt-reset-dialog')).toBeVisible();
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await page.keyboard.press('Escape');
  });

  test('the conditions disappear once connected and the sign-in shows no ORACUL form', async ({ page }) => {
    await signIn(page);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await expect(page.getByTestId('chatgpt-conditions')).toHaveCount(0);
    await expect(page.locator('input[type=password]')).toHaveCount(0);
    await page.getByTestId('chatgpt-disconnect').click();
    await expect(page.getByTestId('chatgpt-conditions')).toHaveText(CONDITIONS);
  });

  test('a failed connection load still shows the conditions', async ({ page }) => {
    await page.route('**/api/auth/chatgpt/connection', (r) =>
      r.fulfill({ status: 500, contentType: 'application/json', body: '{"code":"INTERNAL_ERROR","message":"Something went wrong — try again"}' }),
    );
    await page.goto('/');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
    await expect(page.getByTestId('chatgpt-conditions')).toHaveText(CONDITIONS);
  });
});
