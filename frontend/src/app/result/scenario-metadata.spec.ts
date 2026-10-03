// @trace FR-25
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import { ScenarioStore } from '../scenario/scenario.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const COUNTS = { searches: 20, articlesRetrieved: 31, articlesConsidered: 12, uniqueEvents: 7, eventsSelected: 5, counterSignals: 1, sourcesUsed: 2 };
const CONFIG = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [
    { wildcardId: 'biology-new-pandemic', intensity: 8 },
    { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
  ],
  customWildcards: [],
  output: { story: true, illustration: false },
};

function completedRun(): GenerationRun {
  return {
    id: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    kind: 'STANDARD',
    status: 'COMPLETED',
    stage: 'WRITING_STORY',
    stageLabel: 'Writing from the future…',
    stageIndex: 10,
    stageCount: 10,
    headline: 'Stub headline from the future',
    configuration: CONFIG,
    counts: COUNTS,
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:43:10Z',
    completedAt: '2026-10-02T18:43:10Z',
  } as unknown as GenerationRun;
}

function acceptance(wildcards: { label: string; intensity: number; custom: boolean }[]): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    labels: ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'],
    story: { headline: 'Stub headline from the future', dateline: 'ORACUL FUTURE — March 1, 2027', futureDate: '2027-03-01', body: 'a\n\nb' },
    metadata: { configuration: CONFIG, horizonLabel: '5 years', wildcards, counts: COUNTS },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

const TWO = [
  { label: 'New pandemic', intensity: 8, custom: false },
  { label: 'Humanoid robot boom', intensity: 6, custom: false },
];

describe('slice 09_future-story: scenario metadata panel', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();

  async function show(result: FutureResult): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`)).flush(completedRun());
    harness.detectChanges();
    await harness.fixture.whenStable();
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`)).flush(result);
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

  // @trace FR-25
  it('shows settings, wildcards and counts of the acceptance metadata', async () => {
    await show(acceptance(TWO));
    expect(byId('scenario-metadata')).not.toBeNull();
    expect(text('meta-realism')).toBe('Realism 8/10');
    expect(text('meta-darkness')).toBe('Darkness 9/10');
    expect(text('meta-optimism')).toBe('Optimism 2/10');
    expect(text('meta-horizon')).toBe('Horizon 5 years');
    expect(byId('meta-wildcards')).not.toBeNull();
    expect(text('meta-wildcard-0')).toBe('New pandemic 8/10');
    expect(text('meta-wildcard-1')).toBe('Humanoid robot boom 6/10');
    expect(byId('meta-wildcard-2')).toBeNull();
    expect(text('meta-articles-considered')).toBe('Articles considered: 12');
    expect(text('meta-unique-events')).toBe('Unique events: 7');
    expect(text('meta-evidence-used')).toBe('Evidence used: 5');
  });

  // @trace FR-25
  it('takes every value from the result, never from the current panel state', async () => {
    const store = TestBed.inject(ScenarioStore);
    store.setRealism(2);
    store.setDarkness(3);
    store.setOptimism(7);
    store.setHorizon('1d');
    await show(acceptance(TWO));
    expect(text('meta-realism')).toBe('Realism 8/10');
    expect(text('meta-darkness')).toBe('Darkness 9/10');
    expect(text('meta-optimism')).toBe('Optimism 2/10');
    expect(text('meta-horizon')).toBe('Horizon 5 years');
    store.setDarkness(5);
    harness.detectChanges();
    expect(text('meta-darkness')).toBe('Darkness 9/10');
  });

  // @trace FR-25
  it('omits the wildcard block when there are no wildcards', async () => {
    await show(acceptance([]));
    expect(byId('scenario-metadata')).not.toBeNull();
    expect(byId('meta-wildcards')).toBeNull();
    expect(byId('meta-wildcard-0')).toBeNull();
    expect(text('meta-realism')).toBe('Realism 8/10');
  });

  // @trace FR-25
  it('lists wildcards in array order including custom ones', async () => {
    await show(acceptance([{ label: 'Mars colony', intensity: 7, custom: true }, ...TWO]));
    expect(text('meta-wildcard-0')).toBe('Mars colony 7/10');
    expect(text('meta-wildcard-1')).toBe('New pandemic 8/10');
    expect(text('meta-wildcard-2')).toBe('Humanoid robot boom 6/10');
  });

  // @trace FR-25
  it('shows no critic notice when there are no open critic issues', async () => {
    await show(acceptance(TWO));
    expect(byId('critic-issues')).toBeNull();
  });

  // @trace FR-25
  it('lists open critic issues when the result carries them', async () => {
    const result = acceptance(TWO);
    (result as unknown as { openCriticIssues: unknown[] }).openCriticIssues = [
      { type: 'INAPPROPRIATE_CERTAINTY', description: 'Too certain about the outcome' },
      { type: 'INAPPROPRIATE_CERTAINTY', description: 'Second note' },
    ];
    await show(result);
    const block = byId('critic-issues');
    expect(block).not.toBeNull();
    expect(block?.querySelectorAll('li').length).toBe(2);
    expect(block?.textContent).toContain('Too certain about the outcome');
    expect(block?.textContent).toContain('Second note');
  });
});
