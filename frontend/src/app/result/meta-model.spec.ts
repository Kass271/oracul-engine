// @trace FR-38
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import { routes } from '../app.routes';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const COUNTS = { searches: 20, articlesRetrieved: 31, articlesConsidered: 12, uniqueEvents: 7, eventsSelected: 5, counterSignals: 1, sourcesUsed: 2 };
const CONFIG = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
  customWildcards: [],
  output: { story: true, illustration: false },
};

function completedRun(): GenerationRun {
  return {
    id: RUN_ID,
    generationId: 'ORC-2026-10-04-1000',
    kind: 'STANDARD',
    status: 'COMPLETED',
    stage: 'WRITING_STORY',
    stageLabel: 'Writing from the future…',
    stageIndex: 10,
    stageCount: 10,
    headline: 'Stub headline from the future',
    configuration: CONFIG,
    counts: COUNTS,
    createdAt: '2026-10-04T10:00:00Z',
    updatedAt: '2026-10-04T10:01:00Z',
    completedAt: '2026-10-04T10:01:00Z',
  } as unknown as GenerationRun;
}

function result(model?: string): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-04-1000',
    labels: ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'],
    story: { headline: 'Stub headline from the future', dateline: 'ORACUL FUTURE — March 1, 2027', futureDate: '2027-03-01', body: 'a\n\nb' },
    metadata: {
      configuration: CONFIG,
      horizonLabel: '5 years',
      wildcards: [{ label: 'New pandemic', intensity: 8, custom: false }],
      counts: COUNTS,
      ...(model === undefined ? {} : { model }),
    },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('slice 02_plan-usage-calls: model chip of the scenario settings', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();

  async function show(r: FutureResult): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne((q) => q.method === 'GET' && q.url.endsWith(`/api/runs/${RUN_ID}`)).flush(completedRun());
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectOne((q) => q.method === 'GET' && q.url.endsWith(`/api/runs/${RUN_ID}/result`)).flush(r);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
  });

  // @trace FR-38
  it.each(['gpt-5', 'gpt-5-mini', 'stub-listed', 'x', 's'.repeat(128)])('shows the chip "Model %s" for the run model', async (slug) => {
    await show(result(slug));
    expect(text('meta-model')).toBe(`Model ${slug}`);
  });

  // @trace FR-38
  it('the model chip is the last chip of the Settings chip set, after meta-horizon', async () => {
    await show(result('gpt-5'));
    const settings = byId('meta-realism')?.closest('mat-chip-set') as HTMLElement;
    const ids = Array.from(settings.querySelectorAll('[data-testid]')).map((n) => n.getAttribute('data-testid'));
    expect(ids).toEqual(['meta-realism', 'meta-darkness', 'meta-optimism', 'meta-horizon', 'meta-model']);
  });

  // @trace FR-38
  it('there is no model chip when the result has no model', async () => {
    await show(result());
    expect(byId('scenario-metadata')).not.toBeNull();
    expect(byId('meta-model')).toBeNull();
    expect(text('meta-horizon')).toBe('Horizon 5 years');
  });

  // @trace FR-38
  it('the chip takes the model from the result, not from anything else on the page', async () => {
    await show(result('gpt-5-mini'));
    expect(text('meta-model')).toBe('Model gpt-5-mini');
    expect(byId('scenario-metadata')?.textContent).not.toContain('gpt-5 ');
  });
});
