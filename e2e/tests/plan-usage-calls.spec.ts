import { expect, test, type APIRequestContext, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

const M_NO_MODEL = 'ChatGPT offers no model for this account — check your plan, then try again';
const M_INCOMPLETE = 'ChatGPT did not finish the answer — please try again';
const M_RATE_LIMITED = 'ChatGPT usage limit reached — try again later';
const M_EXPIRED = 'ChatGPT session expired — please reconnect';
const M_NOT_ELIGIBLE = 'Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed';
const M_UNEXPECTED = 'ChatGPT returned an unexpected error (weird_new_code) — please try again';
const M_UNAVAILABLE = 'ChatGPT is temporarily unavailable — try again in a few minutes';
const M_REGISTRATION_INVALID = 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect';

test.describe.configure({ mode: 'serial' });

async function post(request: APIRequestContext, path: string, data: unknown): Promise<void> {
  const r = await request.post(`${STUB}${path}`, { data });
  expect(r.ok(), `${path} ${JSON.stringify(data)} -> ${r.status()}`).toBeTruthy();
}

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await post(request, '/__control/news', { mode: 'ok' });
  await post(request, '/__control/models', { mode: 'ok' });
  await post(request, '/__control/responses', { mode: 'ok' });
  await post(request, '/__control/mode', { mode: 'ok' });
});

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

/** Acceptance configuration A of generation-runs.md "Slice 04_run-start" through the panel. */
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

async function awaitStatus(page: Page, id: string, wanted: string, timeout: number) {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout, intervals: [500] })
    .toBe(wanted);
  return (await page.request.get(`/api/runs/${id}`)).json();
}

async function recorded(page: Page, kind: 'responses' | 'models'): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=${kind}`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

/** Runs the acceptance configuration with a stub mode already set and waits for the failure view. */
async function failedRun(page: Page): Promise<any> {
  const id = await startAcceptanceRun(page);
  await expect(page.getByTestId('failure-view')).toBeVisible({ timeout: 60_000 });
  return (await page.request.get(`/api/runs/${id}`)).json();
}

// @trace FR-38
test.describe('FR-38 Documented plan-usage Responses call', () => {
  test('FR-38 the acceptance run is streamed with store false, uses the preferred model and shows the model chip', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);

    const requests = await recorded(page, 'responses');
    expect(requests.length).toBeGreaterThanOrEqual(6);
    for (const body of requests) {
      expect(body.stream).toBe(true);
      expect(body.store).toBe(false);
      expect(body.model).toBe('gpt-5');
      expect(Object.keys(body).sort()).toEqual(['input', 'instructions', 'model', 'store', 'stream', 'text']);
    }
    const models = await recorded(page, 'models');
    expect(models).toHaveLength(1);
    expect(models.every((m) => m.bearer === true)).toBe(true);

    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('meta-model')).toHaveText('Model gpt-5');
    await evidence(page, 'FR-38', 'model-chip');
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    expect(result.metadata.model).toBe('gpt-5');
  });

  test('FR-38 without the preferred model the first listed model of the catalogue is used', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/models', { mode: 'no-preferred' });
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);
    for (const body of await recorded(page, 'responses')) expect(body.model).toBe('stub-listed');
    expect(await recorded(page, 'models')).toHaveLength(1);
    await expect(page.getByTestId('meta-model')).toHaveText('Model stub-listed', { timeout: 30_000 });
  });

  test('FR-38 an account without any model fails the run with the no-model message and sends no Responses call', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/models', { mode: 'empty' });
    await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_NO_MODEL);
    expect(await recorded(page, 'responses')).toHaveLength(0);
    for (const extra of ['failure-usage-link', 'failure-reconnect', 'failure-provider-code']) {
      await expect(page.getByTestId(extra)).toHaveCount(0);
    }
  });

  test('FR-38 a stream that ends incomplete fails the run with the incomplete message', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'incomplete' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_INCOMPLETE);
    expect(run.failure.code).toBe('CHATGPT_INCOMPLETE');
  });

  test('FR-38 a text.format rejection is repeated once without text.format and the run completes', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'unsupported-capability' });
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);
    const requests = await recorded(page, 'responses');
    expect(requests.some((b) => b.text?.format)).toBe(true); // the rejected first attempt
    expect(requests.filter((b) => !b.text).length).toBeGreaterThan(0); // the fallback bodies
    // after the first fallback no call carries text.format again: exactly one rejected round trip
    expect(requests.filter((b) => b.text?.format)).toHaveLength(1);
    for (const b of requests.filter((x) => !x.text)) expect(b.instructions).toContain('Answer with exactly one JSON object');
  });
});

// @trace FR-39
test.describe('FR-39 Plain-language messages for documented OpenAI errors', () => {
  test('FR-39 a usage limit shows its message and the link to ChatGPT Settings → Usage', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'usage-limit' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_RATE_LIMITED);
    const link = page.getByTestId('failure-usage-link');
    await expect(link).toHaveText('Open ChatGPT Settings → Usage');
    await expect(link).toHaveAttribute('href', 'https://chatgpt.com/#settings/Usage');
    await expect(link).toHaveAttribute('target', '_blank');
    await expect(link).toHaveAttribute('rel', 'noopener noreferrer');
    await expect(page.getByTestId('try-again')).toBeVisible();
    expect(run.failure.code).toBe('CHATGPT_RATE_LIMITED');
    expect(run.failure.providerCode).toBeUndefined();
    await evidence(page, 'FR-39', 'usage-limit');
  });

  test('FR-39 a rejected sign-in context ends the session: message, reconnect action and the Session expired header', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'invalid-user' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_EXPIRED);
    const reconnect = page.getByTestId('failure-reconnect');
    await expect(reconnect).toHaveText('Continue with ChatGPT');
    await expect(reconnect).toHaveAttribute('href', '/api/auth/chatgpt/authorize');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Session expired');
    expect(run.failure.code).toBe('CHATGPT_SESSION_EXPIRED');
    await evidence(page, 'FR-39', 'session-expired');
  });

  test('FR-39 an unknown provider code is shown sanitized in the message and in its own line', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'unknown-code' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_UNEXPECTED);
    await expect(page.getByTestId('failure-provider-code')).toHaveText('Error code: weird_new_code');
    expect(run.failure.code).toBe('CHATGPT_UNEXPECTED_ERROR');
    expect(run.failure.providerCode).toBe('weird_new_code');
    await evidence(page, 'FR-39', 'provider-code');
  });

  test('FR-39 two transient 503 answers are retried and the run completes', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'unavailable-twice' });
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('failure-view')).toHaveCount(0);
    await evidence(page, 'FR-39', 'retried-then-completed');
  });

  test('FR-39 a plan that is not eligible shows its message and leaves the connection untouched', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'not-eligible' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_NOT_ELIGIBLE);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    expect(run.failure.code).toBe('CHATGPT_PLAN_NOT_ELIGIBLE');
    for (const extra of ['failure-usage-link', 'failure-reconnect', 'failure-provider-code']) {
      await expect(page.getByTestId(extra)).toHaveCount(0);
    }
    await evidence(page, 'FR-39', 'not-eligible');
  });

  test('FR-39 every 503 ends the run with the temporarily-unavailable message after the retries', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'unavailable' });
    const run = await failedRun(page);
    await expect(page.getByTestId('failure-message')).toHaveText(M_UNAVAILABLE);
    expect(run.failure.code).toBe('CHATGPT_UNAVAILABLE');
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
  });
});

// @trace FR-40
test.describe('FR-40 Refresh failures end the session cleanly', () => {
  test('FR-40 a refresh answered with invalid_grant shows the session-expired snackbar and the Session expired header', async ({ page, request }) => {
    test.setTimeout(60_000);
    await post(request, '/__control/mode', { mode: 'refresh_error' });
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('run-error-message')).toHaveText(M_EXPIRED);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Session expired');
    await expect(page).not.toHaveURL(/\/futures\//);
    await evidence(page, 'FR-40', 'session-expired');
  });

  test('FR-40 a refresh answered with invalid_client shows the registration message, Registration invalid and a disabled generate button', async ({ page, request }) => {
    test.setTimeout(60_000);
    await post(request, '/__control/mode', { mode: 'refresh_invalid_client' });
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('run-error-message')).toHaveText(M_REGISTRATION_INVALID);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('Registration invalid');
    await expect(page.getByTestId('generate-button')).toBeDisabled();
    await evidence(page, 'FR-40', 'registration-invalid');
  });

  test('FR-40 a refresh that cannot be answered keeps the session: unavailable snackbar, header stays connected', async ({ page, request }) => {
    test.setTimeout(60_000);
    await post(request, '/__control/mode', { mode: 'refresh_unavailable' });
    await connect(page);
    await page.getByTestId('generate-button').click();
    await expect(page.getByTestId('run-error-message')).toHaveText(M_UNAVAILABLE);
    await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
    await expect(page.getByTestId('generate-button')).toBeEnabled();
    await evidence(page, 'FR-40', 'unavailable');
  });
});
