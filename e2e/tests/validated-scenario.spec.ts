import { expect, test, type Page } from '@playwright/test';

const STUB = 'http://localhost:4010';
const MARKER_START = '<<<ORACUL_UNTRUSTED_DATA name="';
const MARKER_END = '<<<END_ORACUL_UNTRUSTED_DATA>>>';
const INSTRUCTIONS_START = 'You are the scenario reasoning component of ORACUL.';

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await page.request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  const events = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } });
  expect(events.status()).toBe(204);
  // the reset must also bring the scenario answers back to normal
  const scenario = await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } });
  expect(scenario.status()).toBe(204);
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

async function setScenarioMode(page: Page, mode: string): Promise<void> {
  const res = await page.request.post(`${STUB}/__control/scenario`, { data: { mode } });
  expect(res.status()).toBe(204);
}

async function recorded(page: Page): Promise<any[]> {
  const res = await page.request.get(`${STUB}/__control/requests?kind=responses`);
  expect(res.status()).toBe(200);
  const body = await res.json();
  return Array.isArray(body) ? body : body.requests;
}

function inputText(request: any): string {
  return (request.input ?? [])
    .flatMap((m: any) => (Array.isArray(m.content) ? m.content : []))
    .map((c: any) => c.text ?? '')
    .join('\n');
}

function purposeOf(request: any): string | undefined {
  return /ORACUL REQUEST ([A-Z_]+)/.exec(inputText(request))?.[1];
}

async function generationRequests(page: Page): Promise<any[]> {
  return (await recorded(page)).filter((r) => purposeOf(r) === 'SCENARIO_GENERATION');
}

/** Names of every JSON key at any depth. */
function keysOf(value: unknown, out: string[] = []): string[] {
  if (Array.isArray(value)) value.forEach((v) => keysOf(v, out));
  else if (value && typeof value === 'object') {
    for (const [k, v] of Object.entries(value)) {
      out.push(k);
      keysOf(v, out);
    }
  }
  return out;
}

/** Content of the named ORACUL_UNTRUSTED_DATA block ('' when missing). */
function block(text: string, name: string): string {
  const open = `${MARKER_START}${name}">>>`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const from = start + open.length + (text[start + open.length] === '\n' ? 1 : 0);
  const end = text.indexOf(MARKER_END, from);
  return end < 0 ? '' : text.slice(from, end).trimEnd();
}

async function structured(page: Page, id: string): Promise<any> {
  const res = await page.request.get(`/api/runs/${id}/structured-scenario`);
  expect(res.status(), await res.text()).toBe(200);
  return res.json();
}

async function expectNotReady(page: Page, id: string): Promise<void> {
  const res = await page.request.get(`/api/runs/${id}/structured-scenario`);
  expect(res.status()).toBe(409);
  expect(await res.json()).toEqual({ code: 'SCENARIO_NOT_READY', message: 'The scenario is not ready yet' });
}

// @trace FR-19, FR-20, FR-21
test.describe('FR-19 / FR-20 / FR-21 Validated scenario (API level)', () => {
  test('FR-19 FR-20 the acceptance run produces a validated structured scenario from the Evidence Pack only', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    expect(run.counts.sourcesUsed).toBe(1);

    const record = await structured(page, id);
    expect(record.accepted).toBe(true);
    expect(record.attempt).toBe(1);
    expect(record.guardReports).toEqual([{ outcome: 'PASS', violations: [], attempt: 1 }]);
    const pack = await (await page.request.get(`/api/runs/${id}/evidence-pack`)).json();
    const firstId = /^\[(E\d+)\]/m.exec(pack.promptText)![1];
    expect(record.structuredScenario.factsUsed[0].evidenceIds[0]).toBe(firstId);

    const generation = await generationRequests(page);
    expect(generation).toHaveLength(1);
    for (const r of await recorded(page)) {
      const keys = keysOf(r);
      expect(keys).not.toContain('tools');
      expect(keys).not.toContain('tool_choice');
      expect(keys.filter((k) => k.startsWith('web_search'))).toEqual([]);
    }
    expect(generation[0].instructions.startsWith(INSTRUCTIONS_START)).toBe(true);
    expect(block(inputText(generation[0]), 'evidence-pack')).toBe(pack.promptText);
  });

  test('FR-19 an injection inside a news source stays inside the Evidence Pack data block', async ({ page }) => {
    test.setTimeout(150_000);
    // reference run: the instructions of an ordinary run
    let id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);
    const okInstructions = (await generationRequests(page))[0].instructions;

    await resetStub(page);
    await page.context().clearCookies(); // second run in one test: start from a fresh, not connected session
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'injection' } });
    expect(mode.status()).toBe(204);
    id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);

    const generation = await generationRequests(page);
    expect(generation).toHaveLength(1);
    expect(generation[0].instructions).toBe(okInstructions);
    expect(generation[0].instructions).not.toContain('Ignore previous');
    const text = inputText(generation[0]);
    const open = text.indexOf(`${MARKER_START}evidence-pack">>>`);
    const close = text.indexOf(MARKER_END, open);
    expect(open).toBeGreaterThan(0);
    let at = text.indexOf('Ignore previous instructions');
    expect(at).toBeGreaterThan(open);
    while (at >= 0) {
      expect(at).toBeGreaterThan(open);
      expect(at).toBeLessThan(close);
      at = text.indexOf('Ignore previous instructions', at + 1);
    }
    expect(text.slice(0, text.indexOf(MARKER_START))).not.toContain('Ignore previous');
  });

  test('FR-20 an invalid first answer gets exactly one schema correction', async ({ page }) => {
    test.setTimeout(90_000);
    await setScenarioMode(page, 'invalid-once');
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);
    const generation = await generationRequests(page);
    expect(generation).toHaveLength(2);
    expect(inputText(generation[1])).toContain('name="schema-errors"');
    const record = await structured(page, id);
    expect(record.attempt).toBe(2);
    expect(record.accepted).toBe(true);
    expect(record.attempts.map((a: any) => [a.attempt, a.reason, a.parsed])).toEqual([
      [1, 'INITIAL', false],
      [2, 'SCHEMA_CORRECTION', true],
    ]);
  });

  test('FR-20 two invalid answers fail the run with INVALID_SCENARIO and no scenario is served', async ({ page }) => {
    test.setTimeout(90_000);
    await setScenarioMode(page, 'invalid');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'FAILED', 40_000);
    expect(run.failure.code).toBe('INVALID_SCENARIO');
    expect(run.failure.message).toBe('ORACUL could not construct a valid scenario');
    expect(await generationRequests(page)).toHaveLength(2);
    await expectNotReady(page, id);
  });

  test('FR-20 the structured scenario of an unknown run is 404 RUN_NOT_FOUND', async ({ page }) => {
    await connect(page);
    for (const bad of ['00000000-0000-0000-0000-000000000000', 'abc']) {
      const res = await page.request.get(`/api/runs/${bad}/structured-scenario`);
      expect(res.status()).toBe(404);
      expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
    }
  });

  test('FR-21 a fact citing an unknown Evidence ID is removed by the guard and the run continues', async ({ page }) => {
    test.setTimeout(90_000);
    await setScenarioMode(page, 'e099');
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);
    const record = await structured(page, id);
    const report = record.guardReports[0];
    expect(report.outcome).toBe('PASS_WITH_REMOVALS');
    expect(report.violations).toHaveLength(1);
    expect(report.violations[0]).toMatchObject({ type: 'UNKNOWN_EVIDENCE_ID', claimId: 'F2', evidenceId: 'E099', action: 'REMOVED' });
    expect(typeof report.violations[0].detail).toBe('string');
    expect(record.structuredScenario.factsUsed.map((f: any) => f.id)).not.toContain('F2');
    expect(record.accepted).toBe(true);
  });

  test('FR-21 one failing guard run is regenerated once and then passes', async ({ page }) => {
    test.setTimeout(90_000);
    await setScenarioMode(page, 'guard-fail-once');
    const id = await startAcceptanceRun(page);
    await awaitStatus(page, id, 'COMPLETED', 40_000);
    const record = await structured(page, id);
    expect(record.guardReports.map((g: any) => g.outcome)).toEqual(['FAIL', 'PASS']);
    expect(record.accepted).toBe(true);
    const generation = await generationRequests(page);
    expect(generation).toHaveLength(2);
    expect(inputText(generation[1])).toContain('name="guard-violations"');
  });

  test('FR-21 a scenario that fails the guard twice is rejected with SCENARIO_REJECTED', async ({ page }) => {
    test.setTimeout(90_000);
    await setScenarioMode(page, 'guard-fail');
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'FAILED', 40_000);
    expect(run.failure.code).toBe('SCENARIO_REJECTED');
    expect(run.failure.message).toBe('ORACUL could not construct a scenario supported by current evidence');
    const record = await structured(page, id);
    expect(record.accepted).toBe(false);
    expect(run.counts.sourcesUsed).toBe(0);
  });
});
