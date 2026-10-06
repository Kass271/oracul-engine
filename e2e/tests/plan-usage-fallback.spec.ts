import { expect, test } from '@playwright/test';
import { STUB, awaitStatus, post, recorded, startAcceptanceRun } from './plan-usage-helpers';

// The text.format fallback flag lives for the backend's lifetime (spec), so this scenario runs in its own
// Playwright project after every other spec (see playwright.config.ts) and the backend is restarted before each run.
test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await post(request, '/__control/models', { mode: 'ok' });
  await post(request, '/__control/responses', { mode: 'ok' });
  await post(request, '/__control/mode', { mode: 'ok' });
});

// @trace FR-38
test.describe('FR-38 text.format fallback', () => {
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
