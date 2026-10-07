import { expect, test, type Page } from '@playwright/test';

const STUB = 'http://localhost:4010';
const MARKER_START = '<<<ORACUL_UNTRUSTED_DATA name="';
const MARKER_END = '<<<END_ORACUL_UNTRUSTED_DATA>>>';
const STARTING_START = 'You are the scenario reasoning component of ORACUL. You are NOT a researcher and you do NOT summarise news.\n';
const STORY_START = 'You are the scenario reasoning component of ORACUL.\nYou are NOT a researcher.';
const PRINCIPLES = [
  'starting conditions',
  'direction, intensity and magnitude',
  'Do not normalise toward the realistic, conservative or statistically most likely outcome',
  'Do not summarise, retell or rewrite the news',
];
const FIRST_TASK = 'Construct one scenario from the starting conditions in evidence-pack under the settings above.';
const SIX_TYPES = [
  'UNSUPPORTED_FACTUAL_JUMP',
  'CONTRADICTION',
  'UNREALISTIC_TIMELINE',
  'WILDCARD_FORCING',
  'SETTINGS_MISMATCH',
  'INAPPROPRIATE_CERTAINTY',
];

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  for (const [name, mode] of [
    ['events', 'ok'],
    ['scenario', 'ok'],
    ['story', 'ok'],
    ['critic', 'ok'],
  ]) {
    const res = await page.request.post(`${STUB}/__control/${name}`, { data: { mode } });
    expect(res.status(), `${name}=${mode}`).toBe(204);
  }
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

/** Acceptance body A through the panel: New pandemic 8, Humanoid robot boom 6, Darkness 9, Optimism 2, Realism 8 (default), 5 years. */
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

/** Content of the named ORACUL_UNTRUSTED_DATA block ('' when missing). */
function block(text: string, name: string): string {
  const open = `${MARKER_START}${name}">>>`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const from = start + open.length + (text[start + open.length] === '\n' ? 1 : 0);
  const end = text.indexOf(MARKER_END, from);
  return end < 0 ? '' : text.slice(from, end).trimEnd();
}

async function awaitCompleted(page: Page, id: string): Promise<any> {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout: 100_000, intervals: [500] })
    .toBe('COMPLETED');
  return (await page.request.get(`/api/runs/${id}`)).json();
}

// @trace FR-58
test.describe('FR-58 Sources as starting conditions in the forecasting prompt', () => {
  test('FR-58 the generation, critic and story requests carry the starting-conditions instructions', async ({ page }) => {
    test.setTimeout(150_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 100_000 });
    await awaitCompleted(page, id);
    const pack = await (await page.request.get(`/api/runs/${id}/evidence-pack`)).json();

    const requests = await recorded(page);
    for (const r of requests) {
      const keys = keysOf(r);
      expect(keys, purposeOf(r)).not.toContain('tools');
      expect(keys, purposeOf(r)).not.toContain('tool_choice');
      expect(keys.filter((k) => k.startsWith('web_search')), purposeOf(r)).toEqual([]);
    }

    const generation = ofPurpose(requests, 'SCENARIO_GENERATION');
    expect(generation).toHaveLength(1);
    const gen = generation[0];
    expect(gen.instructions.startsWith(STARTING_START)).toBe(true);
    for (const phrase of PRINCIPLES) expect(gen.instructions).toContain(phrase);
    expect(gen.instructions).not.toContain('Address the counter-signals');
    expect(gen.instructions).toContain('Leave counterSignalsConsidered empty');
    const genInput = inputText(gen);
    expect(genInput).toContain('Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years');
    expect(genInput).toContain('Wildcards: New pandemic 8 | Humanoid robot boom 6');
    expect(genInput).toContain(FIRST_TASK);
    expect(genInput).not.toContain('Construct one scenario from the Evidence Pack');
    expect(block(genInput, 'evidence-pack')).toBe(pack.promptText);

    const critic = ofPurpose(requests, 'SCENARIO_CRITIC');
    expect(critic).toHaveLength(1);
    const crit = critic[0];
    expect(crit.instructions.startsWith(STARTING_START)).toBe(true);
    expect(crit.instructions).toContain('is intended: never report it as wildcard forcing');
    expect(JSON.stringify(crit)).not.toContain('IGNORED_COUNTER_SIGNALS');
    expect(crit.text.format.schema.properties.issues.items.properties.type.enum).toEqual(SIX_TYPES);

    const story = ofPurpose(requests, 'STORY_WRITING');
    expect(story).toHaveLength(1);
    expect(story[0].instructions.startsWith(STORY_START)).toBe(true);
  });

  test('FR-58 an injection in a source stays inside the evidence-pack block of both requests and the instructions stay identical', async ({ page }) => {
    test.setTimeout(240_000);
    // reference run: the instructions of an ordinary run
    let id = await startAcceptanceRun(page);
    await awaitCompleted(page, id);
    const reference = await recorded(page);
    const okGeneration = ofPurpose(reference, 'SCENARIO_GENERATION')[0].instructions;
    const okCritic = ofPurpose(reference, 'SCENARIO_CRITIC')[0].instructions;

    await resetStub(page);
    await page.context().clearCookies(); // second run in one test: start from a fresh, not connected session
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'injection' } });
    expect(mode.status()).toBe(204);
    id = await startAcceptanceRun(page);
    await awaitCompleted(page, id);

    const requests = await recorded(page);
    for (const purpose of ['SCENARIO_GENERATION', 'SCENARIO_CRITIC']) {
      const list = ofPurpose(requests, purpose);
      expect(list.length, purpose).toBeGreaterThan(0);
      const request = list[0];
      expect(request.instructions, purpose).toBe(purpose === 'SCENARIO_GENERATION' ? okGeneration : okCritic);
      expect(request.instructions, purpose).not.toContain('Ignore previous');
      const text = inputText(request);
      const packBlock = block(text, 'evidence-pack');
      const total = text.split('Ignore previous instructions').length - 1;
      expect(total, `${purpose}: the injected title reaches the request`).toBeGreaterThan(0);
      expect(packBlock.split('Ignore previous instructions').length - 1, `${purpose}: every occurrence is inside the evidence-pack block`).toBe(total);
      expect(text.slice(0, text.indexOf(MARKER_START)), purpose).not.toContain('Ignore previous');
    }
  });
});
