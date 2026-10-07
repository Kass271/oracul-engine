import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';
const UK_MONTHS = ['січня', 'лютого', 'березня', 'квітня', 'травня', 'червня', 'липня', 'серпня', 'вересня', 'жовтня', 'листопада', 'грудня'];
const UK_DATELINE = /^ORACUL FUTURE — [1-9][0-9]? (січня|лютого|березня|квітня|травня|червня|липня|серпня|вересня|жовтня|листопада|грудня) [0-9]{4}$/;

test.describe.configure({ mode: 'serial' });

async function resetStub(page: Page): Promise<void> {
  expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
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

async function awaitTerminal(page: Page, id: string): Promise<any> {
  await expect
    .poll(async () => (await (await page.request.get(`/api/runs/${id}`)).json()).status, { timeout: 90_000, intervals: [500] })
    .toMatch(/^(COMPLETED|FAILED|INSUFFICIENT_EVIDENCE|STOPPED)$/);
  return (await page.request.get(`/api/runs/${id}`)).json();
}

/** Acceptance configuration A with the given output.writingStyle and language. */
function configuration(profileId: string, mode: string, language: string): object {
  return {
    realism: 8,
    darkness: 9,
    optimism: 2,
    horizon: '5y',
    wildcards: [
      { wildcardId: 'biology-new-pandemic', intensity: 8 },
      { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
    ],
    customWildcards: [],
    output: { story: true, illustration: false, writingStyle: { mode, profileId }, language },
  };
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

/** Lines of one ORACUL_UNTRUSTED_DATA block, markers excluded. */
function dataBlock(text: string, name: string): string {
  const open = `<<<ORACUL_UNTRUSTED_DATA name="${name}">>>\n`;
  const start = text.indexOf(open);
  if (start < 0) return '';
  const end = text.indexOf('\n<<<END_ORACUL_UNTRUSTED_DATA>>>', start + open.length);
  return end < 0 ? '' : text.slice(start + open.length, end);
}

/** The pack text carries the run's generation id and the cutoff minute: run-specific lines, not part of the style. */
function normalized(request: any): string {
  return JSON.stringify(request)
    .replace(/Generation: ORC-[0-9-]+/g, 'Generation: ORC')
    .replace(/Cutoff: \d{4}-\d{2}-\d{2}T\d{2}:\d{2}Z/g, 'Cutoff: T');
}

/** Starts a run through the API in the connected session, waits for its end and returns the id and the recorded requests. */
async function runAndRecord(page: Page, profileId: string, mode: string, language: string): Promise<{ id: string; requests: any[] }> {
  await resetStub(page);
  const res = await page.request.post('/api/runs', { data: configuration(profileId, mode, language) });
  expect(res.status(), await res.text()).toBe(202);
  const id = (await res.json()).id as string;
  const run = await awaitTerminal(page, id);
  expect(run.status).toBe('COMPLETED');
  return { id, requests: await recorded(page) };
}

// @trace FR-65
test.describe('FR-65 the style is applied only when the story is written', () => {
  test('FR-65 two runs that differ only in the writing style send identical requests except STORY_WRITING', async ({ page }) => {
    test.setTimeout(240_000);
    await connect(page);
    const official = await runAndRecord(page, 'official-briefing', 'OFFICIAL', 'en');
    const poe = await runAndRecord(page, 'poe', 'NARRATIVE', 'en');

    // every Responses request but STORY_WRITING: same purposes in the same order, pairwise identical bodies
    const rest = (requests: any[]) => requests.filter((r) => purposeOf(r) !== 'STORY_WRITING');
    const a = rest(official.requests);
    const b = rest(poe.requests);
    expect(a.length).toBeGreaterThan(0);
    expect(b.map(purposeOf)).toEqual(a.map(purposeOf));
    for (const purpose of Array.from(new Set(a.map(purposeOf)))) {
      const x = a.filter((r) => purposeOf(r) === purpose).map(normalized).sort();
      const y = b.filter((r) => purposeOf(r) === purpose).map(normalized).sort();
      expect(y, purpose).toEqual(x);
    }
    for (const r of [...a, ...b]) {
      const body = JSON.stringify(r);
      for (const forbidden of ['WRITING STYLE', 'Style profile:', 'Narrative — inspired by', 'Official / Briefing']) {
        expect(body, `${purposeOf(r)} contains "${forbidden}"`).not.toContain(forbidden);
      }
    }

    // the two STORY_WRITING requests: same scenario block byte for byte, the style differs
    const story = (requests: any[]) => requests.filter((r) => purposeOf(r) === 'STORY_WRITING');
    expect(story(official.requests)).toHaveLength(1);
    expect(story(poe.requests)).toHaveLength(1);
    const t1 = inputText(story(official.requests)[0]);
    const t2 = inputText(story(poe.requests)[0]);
    expect(dataBlock(t2, 'structured-scenario')).not.toBe('');
    expect(dataBlock(t2, 'structured-scenario')).toBe(dataBlock(t1, 'structured-scenario'));
    expect(t1).toContain('Style profile: Official / Briefing');
    expect(t2).toContain('Style profile: Narrative — inspired by Edgar Allan Poe');
    expect(t1).not.toBe(t2);

    // the Poe result shows the styled stub headline
    await page.goto(`/futures/${poe.id}`);
    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 30_000 });
    await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future — Narrative — inspired by Edgar Allan Poe');
    await evidence(page, 'FR-65', 'poe-story');

    await page.goto(`/futures/${official.id}`);
    await expect(page.getByTestId('story-headline')).toHaveText('Stub headline from the future');
  });
});

// @trace FR-66
test.describe('FR-66 the story in the chosen language', () => {
  test('FR-66 a Kafka run in Ukrainian started from the panel shows the Ukrainian story with a Ukrainian dateline', async ({ page }) => {
    test.setTimeout(150_000);
    await connect(page);
    await page.getByTestId('writing-style-option-NARRATIVE').click();
    await page.getByTestId('narrative-style-option-kafka').click();
    await page.getByTestId('story-language-select').click();
    await page.getByTestId('story-language-option-uk').click();
    await expect(page.getByTestId('story-language-select')).toHaveText(/Ukrainian/);
    await page.getByTestId('generate-button').click();
    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const id = page.url().split('/').pop()!;

    await expect(page.getByTestId('result-view')).toBeVisible({ timeout: 90_000 });
    await expect(page.getByTestId('story-headline')).toHaveText('Заголовок із майбутнього — Narrative — inspired by Franz Kafka');
    const dateline = (await page.getByTestId('story-dateline').innerText()).trim();
    expect(dateline).toMatch(UK_DATELINE);
    const result = await (await page.request.get(`/api/runs/${id}/result`)).json();
    const [y, m, d] = String(result.story.futureDate).split('-').map(Number);
    expect(dateline).toBe(`ORACUL FUTURE — ${d} ${UK_MONTHS[m - 1]} ${y}`);
    expect(result.story.dateline).toBe(dateline);
    expect(result.story.headline).toBe('Заголовок із майбутнього — Narrative — inspired by Franz Kafka');
    await expect(page.getByTestId('story-paragraph')).toHaveCount(3);
    await expect(page.getByTestId('story-paragraph').first()).toHaveText(/^Абзац 1/);

    // the labels stay English and above the headline
    await expect(page.getByTestId('label-ai-generated')).toHaveText('AI-GENERATED FUTURE SCENARIO');
    await expect(page.getByTestId('label-not-current-news')).toHaveText('POSSIBLE FUTURE — NOT CURRENT NEWS');
    expect(result.labels).toEqual(['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS']);
    const headlineBox = (await page.getByTestId('story-headline').boundingBox())!;
    for (const label of ['label-ai-generated', 'label-not-current-news']) {
      const box = (await page.getByTestId(label).boundingBox())!;
      expect(box.y, label).toBeLessThan(headlineBox.y);
    }

    // the request asked for Ukrainian headline and body
    const story = (await recorded(page)).filter((r) => purposeOf(r) === 'STORY_WRITING');
    expect(story).toHaveLength(1);
    expect(inputText(story[0])).toContain('Story language: Ukrainian');
    expect(inputText(story[0])).toContain('Write the headline and the body in Ukrainian.');
    await evidence(page, 'FR-66', 'ukrainian-story');
  });
});
