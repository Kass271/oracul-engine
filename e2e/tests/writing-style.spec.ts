import { expect, test, type Page } from '@playwright/test';
import { evidence } from './evidence';

const STUB = 'http://localhost:4010';

/** The 8 NARRATIVE profiles of the shipped style file, in order, with their labels. */
const NARRATIVE: [string, string][] = [
  ['neutral-narrative', 'Neutral Narrative'],
  ['poe', 'Narrative — inspired by Edgar Allan Poe'],
  ['kafka', 'Narrative — inspired by Franz Kafka'],
  ['wells', 'Narrative — inspired by H. G. Wells'],
  ['verne', 'Narrative — inspired by Jules Verne'],
  ['orwell', 'Narrative — inspired by George Orwell'],
  ['clarke', 'Narrative — inspired by Arthur C. Clarke'],
  ['harrison', 'Narrative — inspired by Harry Harrison'],
];

const KAFKA_UK_OUTPUT = {
  story: true,
  illustration: false,
  writingStyle: { mode: 'NARRATIVE', profileId: 'kafka' },
  language: 'uk',
};

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ page }) => {
  expect((await page.request.post(`${STUB}/__control/reset`)).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/scenario`, { data: { mode: 'ok' } })).status()).toBe(204);
  expect((await page.request.post(`${STUB}/__control/story`, { data: { mode: 'ok' } })).status()).toBe(204);
});

const radio = (page: Page, testId: string) => page.getByTestId(testId).locator('input[type="radio"]');

async function connect(page: Page): Promise<void> {
  await page.goto('/');
  await expect(page.getByTestId('chatgpt-status')).toHaveText('Not connected');
  await page.getByTestId('chatgpt-connect').click();
  await expect(page.getByTestId('chatgpt-status')).toHaveText('ChatGPT connected');
}

async function awaitTerminal(page: Page, runId: string): Promise<void> {
  await expect
    .poll(
      async () => {
        const r = await page.request.get(`/api/runs/${runId}`);
        return r.ok() ? (await r.json()).status : '';
      },
      { timeout: 60_000, intervals: [500, 1000] },
    )
    .toMatch(/^(COMPLETED|FAILED|INSUFFICIENT_EVIDENCE|STOPPED)$/);
}

// @trace FR-63, FR-64
test.describe('FR-63 / FR-64 writing style and story language', () => {
  test('FR-63 a fresh session has Official / Briefing and English selected, with no narrative section', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByTestId('writing-style-section')).toBeVisible();
    await expect(page.getByTestId('writing-style-group')).toHaveAttribute('aria-label', 'Writing Style');
    await expect(page.getByTestId('writing-style-option-OFFICIAL')).toHaveText('Official / Briefing');
    await expect(page.getByTestId('writing-style-option-NARRATIVE')).toHaveText('Narrative');
    await expect(radio(page, 'writing-style-option-OFFICIAL')).toBeChecked();
    await expect(radio(page, 'writing-style-option-NARRATIVE')).not.toBeChecked();
    await expect(page.getByTestId('narrative-style-section')).toHaveCount(0);
    await expect(page.getByTestId('story-language-select')).toHaveText(/English/);
    await evidence(page, 'FR-63', 'official-default');
  });

  test('FR-63 Narrative shows the 8 profiles in order with neutral-narrative selected, Official removes them again', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId('writing-style-option-NARRATIVE').click();
    await expect(page.getByTestId('narrative-style-section')).toBeVisible();
    await expect(page.getByTestId('narrative-style-group')).toHaveAttribute('aria-label', 'Narrative Style');
    const ids = await page
      .getByTestId('narrative-style-group')
      .locator('[data-testid^="narrative-style-option-"]')
      .evaluateAll((nodes) => nodes.map((n) => n.getAttribute('data-testid')));
    expect(ids).toEqual(NARRATIVE.map(([id]) => `narrative-style-option-${id}`));
    for (const [id, label] of NARRATIVE) {
      await expect(page.getByTestId(`narrative-style-option-${id}`)).toHaveText(label);
    }
    await expect(radio(page, 'narrative-style-option-neutral-narrative')).toBeChecked();
    await expect(page.getByTestId('narrative-style-select')).toHaveCount(0);
    await evidence(page, 'FR-63', 'narrative-profiles');

    await page.getByTestId('writing-style-option-OFFICIAL').click();
    await expect(page.getByTestId('narrative-style-section')).toHaveCount(0);
    await expect(radio(page, 'writing-style-option-OFFICIAL')).toBeChecked();

    await page.getByTestId('writing-style-option-NARRATIVE').click();
    await expect(radio(page, 'narrative-style-option-neutral-narrative')).toBeChecked();
  });

  test('FR-63 FR-64 Kafka and Ukrainian are sent, stored in the run configuration and restored after a reload', async ({ page }) => {
    test.setTimeout(120_000);
    await connect(page);
    await page.getByTestId('writing-style-option-NARRATIVE').click();
    await page.getByTestId('narrative-style-option-kafka').click();
    await expect(radio(page, 'narrative-style-option-kafka')).toBeChecked();
    await page.getByTestId('story-language-select').click();
    await expect(page.getByTestId('story-language-option-en')).toHaveText('English');
    await expect(page.getByTestId('story-language-option-uk')).toHaveText('Ukrainian');
    await page.getByTestId('story-language-option-uk').click();
    await expect(page.getByTestId('story-language-select')).toHaveText(/Ukrainian/);

    const posted = page.waitForRequest((r) => r.method() === 'POST' && new URL(r.url()).pathname === '/api/runs');
    await page.getByTestId('generate-button').click();
    const body = (await posted).postDataJSON();
    expect(body.output).toEqual(KAFKA_UK_OUTPUT);

    await expect(page).toHaveURL(/\/futures\/[0-9a-f-]{36}$/);
    const runId = page.url().split('/').pop()!;
    const run = await (await page.request.get(`/api/runs/${runId}`)).json();
    expect(run.configuration.output).toEqual(KAFKA_UK_OUTPUT);
    await evidence(page, 'FR-63', 'kafka-ukrainian-started');
    await awaitTerminal(page, runId);

    // reload on /futures/<id>: the newest run's choice is selected again
    await page.reload();
    await expect(radio(page, 'writing-style-option-NARRATIVE')).toBeChecked();
    await expect(radio(page, 'narrative-style-option-kafka')).toBeChecked();
    await expect(page.getByTestId('story-language-select')).toHaveText(/Ukrainian/);
    const configuration = await (await page.request.get('/api/scenario/configuration')).json();
    expect(configuration.output).toEqual(KAFKA_UK_OUTPUT);
    await evidence(page, 'FR-64', 'ukrainian-restored-after-reload');

    // reload on /: the same choice
    await page.goto('/');
    await expect(radio(page, 'narrative-style-option-kafka')).toBeChecked();
    await expect(page.getByTestId('story-language-select')).toHaveText(/Ukrainian/);
  });

  test('FR-63 a phone shows the Narrative Style as a dropdown, not as radios', async ({ page }) => {
    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto('/');
    await page.getByTestId('scenario-drawer-toggle').click();
    await page.getByTestId('writing-style-option-NARRATIVE').click();
    await expect(page.getByTestId('narrative-style-select')).toBeVisible();
    await expect(page.getByTestId('narrative-style-select')).toHaveAttribute('aria-label', 'Narrative Style');
    await expect(page.getByTestId('narrative-style-group')).toHaveCount(0);
    await expect(page.getByTestId('narrative-style-select')).toHaveText(/Neutral Narrative/);

    await page.getByTestId('narrative-style-select').click();
    for (const [id, label] of NARRATIVE) {
      await expect(page.getByTestId(`narrative-style-select-option-${id}`)).toHaveText(label);
    }
    await page.getByTestId('narrative-style-select-option-kafka').click();
    await expect(page.getByTestId('narrative-style-select')).toHaveText(/Narrative — inspired by Franz Kafka/);
    await evidence(page, 'FR-63', 'narrative-dropdown-mobile');
  });

  test('FR-63 FR-64 the API rejects every invalid writing style and language with the exact body', async ({ request }) => {
    const base = { realism: 8, darkness: 5, optimism: 5, horizon: '1y', wildcards: [], customWildcards: [] };
    const out = (extra: Record<string, unknown>) => ({ ...base, output: { story: true, illustration: false, ...extra } });
    const list = async (): Promise<number> => (await (await request.get('/api/runs')).json()).items.length;
    const before = await list();

    const badStyles: unknown[] = [
      'kafka', '', 5, true, [], [{ mode: 'OFFICIAL', profileId: 'official-briefing' }], {},
      { profileId: 'kafka' }, { mode: null, profileId: 'kafka' }, { mode: 'official', profileId: 'official-briefing' },
      { mode: 'Narrative', profileId: 'kafka' }, { mode: 'POETIC', profileId: 'kafka' }, { mode: '', profileId: 'kafka' },
      { mode: 1, profileId: 'kafka' },
      { mode: 'NARRATIVE' }, { mode: 'NARRATIVE', profileId: null }, { mode: 'NARRATIVE', profileId: '' },
      { mode: 'NARRATIVE', profileId: 5 }, { mode: 'NARRATIVE', profileId: 'Kafka' }, { mode: 'NARRATIVE', profileId: ' kafka' },
      { mode: 'NARRATIVE', profileId: 'kafka ' }, { mode: 'NARRATIVE', profileId: 'dickens' },
      { mode: 'NARRATIVE', profileId: 'a'.repeat(41) },
      { mode: 'OFFICIAL', profileId: 'kafka' }, { mode: 'OFFICIAL', profileId: 'neutral-narrative' },
      { mode: 'NARRATIVE', profileId: 'official-briefing' },
    ];
    for (const writingStyle of badStyles) {
      const r = await request.post('/api/runs', { data: out({ writingStyle }) });
      expect(r.status(), JSON.stringify(writingStyle)).toBe(400);
      expect(r.headers()['content-type']).toContain('application/json');
      expect(await r.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'Unknown writing style' });
    }

    const badLanguages: unknown[] = ['EN', 'Uk', 'UK', 'ua', 'uk-UA', 'en-US', 'de', '', ' en', 'uk ', 1, 0, true, ['uk'], {}, { code: 'uk' }];
    for (const language of badLanguages) {
      const r = await request.post('/api/runs', { data: out({ language }) });
      expect(r.status(), JSON.stringify(language)).toBe(400);
      expect(await r.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'Unknown story language' });
    }

    // both wrong: the style is reported first
    const both = await request.post('/api/runs', { data: out({ writingStyle: 'kafka', language: 'de' }) });
    expect(both.status()).toBe(400);
    expect(await both.json()).toEqual({ code: 'VALIDATION_FAILED', message: 'Unknown writing style' });

    // valid choices pass validation (no ChatGPT connection here: 401, never 400)
    for (const output of [
      { writingStyle: { mode: 'NARRATIVE', profileId: 'poe' }, language: 'uk' },
      { writingStyle: null, language: null },
      {},
    ]) {
      const r = await request.post('/api/runs', { data: out(output) });
      expect(r.status(), JSON.stringify(output)).toBe(401);
      expect(await r.json()).toEqual({ code: 'CHATGPT_NOT_CONNECTED', message: 'Connect ChatGPT to generate' });
    }

    expect(await list()).toBe(before);
  });
});
