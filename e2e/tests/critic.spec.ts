import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const INSTRUCTIONS_START = 'You are the scenario reasoning component of ORACUL.';
const ICS_LINE = 'IGNORED_COUNTER_SIGNALS | The scenario ignores the counter-signals of the Evidence Pack.';
const CERT_1 = 'P1 is stated as a certain fact.';
const CERT_2 = 'The future event comes too early for the causal chain.';

test.describe.configure({ mode: 'serial' });

async function control(page: Page, name: string, mode: string): Promise<void> {
  const res = await page.request.post(`${STUB}/__control/${name}`, { data: { mode } });
  expect(res.status(), `${name}=${mode}`).toBe(204);
}

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await control(page, 'events', 'ok');
  await control(page, 'scenario', 'ok');
  await control(page, 'story', 'ok');
  await control(page, 'critic', 'ok');
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

/** Acceptance configuration A through the panel (realism stays 8). */
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

function ofPurpose(requests: any[], purpose: string): any[] {
  return requests.filter((r) => purposeOf(r) === purpose);
}

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

async function awaitTerminal(page: Page, id: string, timeout: number): Promise<any> {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout, intervals: [500] })
    .toMatch(/COMPLETED|FAILED|INSUFFICIENT_EVIDENCE/);
  return (await page.request.get(`/api/runs/${id}`)).json();
}

// @trace FR-22
test.describe('FR-22 Critic validation', () => {
  test('FR-22 a passing critic is called once between the scenario and the story and shows nothing', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('critic-issues')).toHaveCount(0);
    await evidence(page, 'FR-22', 'critic-passed');

    const requests = await recorded(page);
    const purposes = requests.map(purposeOf);
    const critic = ofPurpose(requests, 'SCENARIO_CRITIC');
    expect(critic).toHaveLength(1);
    expect(purposes.indexOf('SCENARIO_CRITIC')).toBeGreaterThan(purposes.indexOf('SCENARIO_GENERATION'));
    expect(purposes.indexOf('SCENARIO_CRITIC')).toBeLessThan(purposes.indexOf('STORY_WRITING'));
    const keys = keysOf(critic[0]);
    expect(keys).not.toContain('tools');
    expect(keys).not.toContain('tool_choice');
    expect(keys.filter((k) => k.startsWith('web_search'))).toEqual([]);
    expect(critic[0].instructions.startsWith(INSTRUCTIONS_START)).toBe(true);

    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    expect(run.hasOpenCriticIssues).toBe(false);
    const structured = await (await page.request.get(`/api/runs/${id}/structured-scenario`)).json();
    expect(structured.criticReports).toEqual([{ verdict: 'PASS', issues: [], attempt: 1 }]);
  });

  test('FR-22 a failing critic triggers one regeneration with the critique attached', async ({ page }) => {
    test.setTimeout(150_000);
    await control(page, 'critic', 'fail-once');
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('critic-issues')).toHaveCount(0);
    const requests = await recorded(page);
    const generation = ofPurpose(requests, 'SCENARIO_GENERATION');
    expect(generation).toHaveLength(2);
    const second = inputText(generation[1]);
    expect(second).toContain('Reason: CRITIC_REGENERATION');
    expect(second).toContain('name="critique"');
    expect(second).toContain(ICS_LINE);
    const structured = await (await page.request.get(`/api/runs/${id}/structured-scenario`)).json();
    expect(structured.criticReports.map((c: any) => c.verdict)).toEqual(['FAIL', 'PASS']);
  });

  test('FR-22 a second critic failure shows the open questions with the passed guard', async ({ page }) => {
    test.setTimeout(150_000);
    await control(page, 'critic', 'fail');
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    const notice = page.getByTestId('critic-issues');
    await expect(notice).toBeVisible();
    await expect(page.getByTestId('scenario-metadata').getByTestId('critic-issues')).toBeVisible();
    await expect(page.getByTestId('critic-issues-title')).toHaveText("Open questions from ORACUL's critic");
    await expect(page.getByTestId('critic-issue-0')).toHaveText(CERT_1);
    await expect(page.getByTestId('critic-issue-1')).toHaveText(CERT_2);
    await evidence(page, 'FR-22', 'open-critic-issues');

    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    expect(run.hasOpenCriticIssues).toBe(true);
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    expect(result.openCriticIssues.map((i: any) => i.type)).toEqual(['INAPPROPRIATE_CERTAINTY', 'UNREALISTIC_TIMELINE']);
    expect(result.openCriticIssues.map((i: any) => i.description)).toEqual([CERT_1, CERT_2]);

    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible();
    await expect(page.getByTestId('critic-issues')).toBeVisible();
    await expect(page.getByTestId('critic-issue-0')).toHaveText(CERT_1);
  });

  test('FR-22 a second critic failure with a failing guard rejects the scenario', async ({ page }) => {
    test.setTimeout(150_000);
    await control(page, 'critic', 'fail');
    await control(page, 'scenario', 'bad-after-first');
    const id = await startAcceptanceRun(page);
    const run = await awaitTerminal(page, id, 120_000);
    expect(run.status).toBe('FAILED');
    expect(run.failure.message).toBe('ORACUL could not construct a scenario supported by current evidence');
    await expect(page.getByTestId('result-view')).toHaveCount(0);
    expect(ofPurpose(await recorded(page), 'STORY_WRITING')).toHaveLength(0);
    const res = await page.request.get(`/api/runs/${id}/result`);
    expect(res.status()).toBe(409);
    expect(await res.json()).toEqual({ code: 'RESULT_NOT_READY', message: 'This future is not ready yet' });
  });

  test('FR-22 a malformed critic answer is retried once and then counts as a pass', async ({ page }) => {
    test.setTimeout(120_000);
    await control(page, 'critic', 'malformed');
    await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    await expect(page.getByTestId('critic-issues')).toHaveCount(0);
    expect(ofPurpose(await recorded(page), 'SCENARIO_CRITIC')).toHaveLength(2);
  });
});
