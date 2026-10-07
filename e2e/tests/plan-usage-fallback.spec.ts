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

// @trace FR-38, FR-51
test.describe('FR-38 text.format fallback', () => {
  test('FR-38 a text.format rejection is repeated once without text.format and the run completes', async ({ page, request }) => {
    test.setTimeout(120_000);
    await post(request, '/__control/responses', { mode: 'unsupported-capability' });
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 60_000);
    const requests = await recorded(page, 'responses');
    expect(requests.some((b) => b.text?.format)).toBe(true); // the rejected first attempt
    expect(requests.filter((b) => !b.text).length).toBeGreaterThan(0); // the fallback bodies
    // FR-51: the QUERY_GENERATION calls started before the client has seen the rejection (body A: 1 or 2) carry text.format, each rejected
    // one is repeated exactly once without text; every call started after the first rejection goes without text.format
    const rejected = requests.filter((b) => b.text?.format);
    expect(rejected.length).toBeGreaterThanOrEqual(1);
    expect(rejected.length).toBeLessThanOrEqual(2);
    for (const b of rejected) expect(JSON.stringify(b)).toContain('ORACUL REQUEST QUERY_GENERATION');
    const repeats = requests.filter((x) => !x.text);
    for (const b of rejected) {
      expect(repeats.some((x) => JSON.stringify(x.input) === JSON.stringify(b.input)), 'a rejected call is repeated without text.format').toBe(true);
    }
    for (const b of repeats) expect(b.instructions).toContain('Answer with exactly one JSON object');
  });
});
