import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const MARKER_START = '<<<ORACUL_UNTRUSTED_DATA name="';
const INSTRUCTIONS_START = 'You are the scenario reasoning component of ORACUL.\nYou are NOT a researcher.';
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June', 'July', 'August', 'September', 'October', 'November', 'December'];

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  const r = await page.request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  await page.request.post(`${STUB}/__control/news`, { data: { mode: 'ok' } });
  expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode: 'ok' } })).status()).toBe(204);
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

async function setStoryMode(page: Page, mode: string): Promise<void> {
  const res = await page.request.post(`${STUB}/__control/story`, { data: { mode } });
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

/** "ORACUL FUTURE — March 1, 2027" -> "2027-03-01" */
function datelineDate(dateline: string): string {
  const m = /^ORACUL FUTURE — ([A-Z][a-z]+) (\d{1,2}), (\d{4})$/.exec(dateline)!;
  return `${m[3]}-${String(MONTHS.indexOf(m[1]) + 1).padStart(2, '0')}-${m[2].padStart(2, '0')}`;
}

function plusYears(date: string, years: number): string {
  const [y, m, d] = date.split('-');
  return `${Number(y) + years}-${m}-${d}`;
}

// @trace FR-23, FR-25
test.describe('FR-23 / FR-25 Future story and metadata', () => {
  test('FR-23 FR-25 the acceptance run ends in the labelled story with the metadata panel', async ({ page }) => {
    test.setTimeout(120_000);
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    await expect(page).toHaveURL(new RegExp(`/futures/${id}$`));
    await expect(page.getByTestId('progress-view')).toHaveCount(0);

    // FR-23: labels above the headline, dateline within the horizon, plain-text body
    await expect(page.getByTestId('label-ai-generated')).toHaveText('AI-GENERATED FUTURE SCENARIO');
    await expect(page.getByTestId('label-not-current-news')).toHaveText('POSSIBLE FUTURE — NOT CURRENT NEWS');
    const headlineBox = (await page.getByTestId('story-headline').boundingBox())!;
    for (const label of ['label-ai-generated', 'label-not-current-news']) {
      const box = (await page.getByTestId(label).boundingBox())!;
      expect(box.y, label).toBeLessThan(headlineBox.y);
    }
    await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future');
    const dateline = (await page.getByTestId('story-dateline').innerText()).trim();
    expect(dateline).toMatch(/^ORACUL FUTURE — [A-Z][a-z]+ \d{1,2}, \d{4}$/);
    const pack = await (await page.request.get(`/api/runs/${id}/evidence-pack`)).json();
    const cutoffDate = String(pack.cutoff).slice(0, 10);
    const storyDate = datelineDate(dateline);
    expect(storyDate > cutoffDate).toBe(true);
    expect(storyDate <= plusYears(cutoffDate, 5)).toBe(true);
    expect(storyDate).not.toBe(new Date().toISOString().slice(0, 10));
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    expect(result.story.dateline).toBe(dateline);
    await expect(page.getByTestId('story-paragraph')).toHaveCount(3);
    await expect(page.getByTestId('story-paragraph').first()).toHaveText(/^Stub paragraph 1/);
    await evidence(page, 'FR-23', 'future-story');

    // FR-25: metadata panel from the run snapshot
    await expect(page.getByTestId('meta-realism')).toHaveText('Realism 8/10');
    await expect(page.getByTestId('meta-darkness')).toHaveText('Darkness 9/10');
    await expect(page.getByTestId('meta-optimism')).toHaveText('Optimism 2/10');
    await expect(page.getByTestId('meta-horizon')).toHaveText('Horizon 5 years');
    await expect(page.getByTestId('meta-wildcard-0')).toHaveText('New pandemic 8/10');
    await expect(page.getByTestId('meta-wildcard-1')).toHaveText('Humanoid robot boom 6/10');
    const run = await (await page.request.get(`/api/runs/${id}`)).json();
    await expect(page.getByTestId('meta-articles-considered')).toHaveText(`Articles considered: ${run.counts.articlesConsidered}`);
    await expect(page.getByTestId('meta-unique-events')).toHaveText(`Unique events: ${run.counts.uniqueEvents}`);
    await expect(page.getByTestId('meta-evidence-used')).toHaveText(`Evidence used: ${run.counts.eventsSelected}`);
    await evidence(page, 'FR-25', 'scenario-metadata');

    await page.getByTestId('slider-darkness-input').fill('3');
    await expect(page.getByTestId('meta-darkness')).toHaveText('Darkness 9/10');

    await page.reload();
    await expect(page.getByTestId('result-view')).toBeVisible();
    await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future');

    // NFR-3: no tools anywhere, Closed Evidence Mode instructions, one STORY_WRITING request, last
    const requests = await recorded(page);
    for (const r of requests) {
      const keys = keysOf(r);
      expect(keys).not.toContain('tools');
      expect(keys).not.toContain('tool_choice');
      expect(keys.filter((k) => k.startsWith('web_search'))).toEqual([]);
    }
    const purposes = requests.map(purposeOf);
    for (const purpose of ['SCENARIO_GENERATION', 'STORY_WRITING']) {
      const req = requests.find((r) => purposeOf(r) === purpose);
      expect(req.instructions.startsWith(INSTRUCTIONS_START), purpose).toBe(true);
    }
    expect(purposes.filter((p) => p === 'STORY_WRITING')).toHaveLength(1);
    expect(purposes[purposes.length - 1]).toBe('STORY_WRITING');
    // slice 10: the SCENARIO_CRITIC request precedes the story request
    expect(purposes.filter((p) => p === 'SCENARIO_CRITIC')).toHaveLength(1);
    expect(purposes.indexOf('SCENARIO_CRITIC')).toBeLessThan(purposes.indexOf('STORY_WRITING'));

    // NFR-2: the E2E stack paces every stage to >= 2 s, so 10 s + 10 x 2 s
    const elapsed = Date.parse(run.completedAt) - Date.parse(run.createdAt);
    expect(elapsed).toBeLessThan(10_000 + 10 * 2_000);
  });

  test('FR-23 a story with a bad date twice falls back to the scenario future date', async ({ page }) => {
    test.setTimeout(120_000);
    await setStoryMode(page, 'bad-date');
    const id = await startAcceptanceRun(page);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 60_000 });
    const structured = await (await page.request.get(`/api/runs/${id}/structured-scenario`)).json();
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    expect(result.story.futureDate).toBe(structured.structuredScenario.futureEvent.date);
    await expect(page.getByTestId('story-dateline')).toHaveText(result.story.dateline);
    expect(datelineDate(result.story.dateline)).toBe(structured.structuredScenario.futureEvent.date);
    const story = (await recorded(page)).filter((r) => purposeOf(r) === 'STORY_WRITING');
    expect(story).toHaveLength(2);
    expect(inputText(story[1])).toContain(`${MARKER_START}story-errors">>>`);
  });

  test('FR-23 an invalid story twice fails the run and no result is served', async ({ page }) => {
    test.setTimeout(120_000);
    await setStoryMode(page, 'invalid');
    const id = await startAcceptanceRun(page);
    const run = await awaitTerminal(page, id, 90_000);
    expect(run.status).toBe('FAILED');
    expect(run.failure.message).toBe('ORACUL could not construct a valid scenario');
    const res = await page.request.get(`/api/runs/${id}/result`);
    expect(res.status()).toBe(409);
    expect(await res.json()).toEqual({ code: 'RESULT_NOT_READY', message: 'This future is not ready yet' });
    await expect(page.getByTestId('result-view')).toHaveCount(0);
    await expect(page.getByTestId('failure-message')).toHaveText('ORACUL could not construct a valid scenario');
  });

  test('FR-23 the result of an unknown run is 404', async ({ page }) => {
    await connect(page);
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/result');
    expect(res.status()).toBe(404);
    expect(await res.json()).toEqual({ code: 'RUN_NOT_FOUND', message: 'Future not found' });
  });
});
