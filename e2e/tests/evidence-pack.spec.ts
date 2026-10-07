import { expect, test, type Page } from '@playwright/test';

const STUB = 'http://localhost:4010';

test.describe.configure({ mode: 'serial' });

test.beforeEach(async ({ request }) => {
  const r = await request.post(`${STUB}/__control/reset`);
  expect(r.status()).toBe(204);
  const events = await request.post(`${STUB}/__control/events`, { data: { mode: 'ok' } });
  expect(events.status()).toBe(204);
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

async function listEvents(page: Page, id: string): Promise<any[]> {
  const res = await page.request.get(`/api/runs/${id}/events`);
  expect(res.status()).toBe(200);
  return (await res.json()).items;
}

async function getPack(page: Page, id: string): Promise<any> {
  const res = await page.request.get(`/api/runs/${id}/evidence-pack`);
  expect(res.status()).toBe(200);
  return res.json();
}

function allItems(pack: any): any[] {
  return [...pack.core, ...pack.supporting, ...pack.counterSignals];
}

// @trace FR-16
// @trace FR-17
// @trace FR-18
// @trace FR-46
// @trace FR-50
// @trace FR-53
test.describe('FR-16 / FR-17 / FR-18 Ranking, evidence selection and the Evidence Pack', () => {
  test('mode evidence: 4 events from the 7 kept sources give 3 core + 1 counter-signal item with diversity caps and the exact prompt text', async ({ page }) => {
    test.setTimeout(90_000);
    const mode = await page.request.post(`${STUB}/__control/events`, { data: { mode: 'evidence' } });
    expect(mode.status()).toBe(204);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    // FR-53: 7 kept sources -> 4 events (EV004 = [S007]); EV n mod 3 = 1 dark (EV001, EV004), = 2 mid (EV002), = 0 bright (EV003).
    // Darkness 9 / optimism 2: the bright one is the counter-signal candidate, the other 3 fill the core, nothing is left for SUPPORTING.
    expect(run.counts.articlesConsidered).toBe(25);
    expect(run.counts.sourcesKept).toBe(7);
    expect(run.counts.uniqueEvents).toBe(4);
    expect(run.counts.eventsSelected).toBe(4);
    expect(run.counts.counterSignals).toBe(1);
    expect(run.evidencePackId).toBeTruthy();

    const pack = await getPack(page, id);
    expect(pack.id).toBe(run.evidencePackId);
    expect(pack.core).toHaveLength(3);
    expect(pack.supporting).toHaveLength(0);
    expect(pack.counterSignals).toHaveLength(1);
    const items = allItems(pack);
    expect(items.map((i) => i.evidenceId)).toEqual(['E001', 'E002', 'E003', 'E004']);
    expect(pack.core.every((i: any) => i.section === 'CORE')).toBe(true);
    expect(pack.supporting.every((i: any) => i.section === 'SUPPORTING')).toBe(true);
    expect(pack.counterSignals.every((i: any) => i.section === 'COUNTER_SIGNAL')).toBe(true);

    const events = await listEvents(page, id);
    const byId = new Map<string, any>(events.map((e) => [e.id, e]));
    for (const item of pack.core) expect([1.0, 0.5]).toContain(byId.get(item.eventId).classification.risk);
    expect(pack.core.filter((i: any) => byId.get(i.eventId).classification.risk === 1.0)).toHaveLength(2);
    for (const item of pack.counterSignals) expect(byId.get(item.eventId).classification.opportunity).toBe(0.8);

    // diversity: at most 2 per primary entity, at most 3 per publisher
    const sources = new Map<string, any>(pack.sources.map((s: any) => [s.id, s]));
    const perEntity = new Map<string, number>();
    const perPublisher = new Map<string, number>();
    for (const item of items) {
      const entity = (item.entities[0] ?? '').trim().toLowerCase();
      if (entity) perEntity.set(entity, (perEntity.get(entity) ?? 0) + 1);
      const primary = [...item.sourceIds]
        .map((sid: string) => sources.get(sid))
        .sort((a: any, b: any) => b.sourceQuality - a.sourceQuality || a.id.localeCompare(b.id))[0];
      const publisher = primary.publisher.trim().toLowerCase();
      perPublisher.set(publisher, (perPublisher.get(publisher) ?? 0) + 1);
    }
    for (const n of perEntity.values()) expect(n).toBeLessThanOrEqual(2);
    for (const n of perPublisher.values()) expect(n).toBeLessThanOrEqual(3);

    expect(pack.generationId).toBe(run.generationId);
    expect(pack.promptText.startsWith(`ORACUL EVIDENCE PACK\nGeneration: ${run.generationId}\nCutoff: `)).toBe(true);
    for (const part of [
      'Realism: 8 | Darkness: 9 | Optimism: 2 | Horizon: 5 years',
      'New pandemic: 8 | Humanoid robot boom: 6',
      '[E001] ',
      '[E004] ',
      'COUNTER-SIGNALS',
    ]) {
      expect(pack.promptText).toContain(part);
    }

    const selected = events.filter((e) => e.selection);
    expect(selected).toHaveLength(4);
    expect(new Map(selected.map((e) => [e.id, e.selection.evidenceId]))).toEqual(
      new Map(items.map((i) => [i.eventId, i.evidenceId])),
    );
  });

  test('default classification: every one of the 4 events is bright, so only counter-signals are selected', async ({ page }) => {
    test.setTimeout(90_000);
    const id = await startAcceptanceRun(page);
    const run = await awaitStatus(page, id, 'COMPLETED', 40_000);
    const pack = await getPack(page, id);
    expect(pack.core).toHaveLength(0);
    expect(pack.supporting).toHaveLength(0);
    expect(pack.counterSignals).toHaveLength(4);
    expect(run.counts.counterSignals).toBe(4);
    expect(run.counts.eventsSelected).toBe(4);
  });

  test('FR-18 an unknown run has no Evidence Pack', async ({ page }) => {
    const res = await page.request.get('/api/runs/00000000-0000-0000-0000-000000000000/evidence-pack');
    expect(res.status()).toBe(404);
    expect((await res.json()).code).toBe('RUN_NOT_FOUND');
  });
});
