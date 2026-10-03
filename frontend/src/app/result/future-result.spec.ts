// @trace FR-23
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const LABELS = ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'];
const COUNTS = { searches: 20, articlesRetrieved: 31, articlesConsidered: 12, uniqueEvents: 7, eventsSelected: 2, counterSignals: 1, sourcesUsed: 2 };
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

function futureResult(body = 'a\n\nb\n\nc'): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    labels: LABELS,
    story: { headline: 'Stub headline from the future', dateline: 'ORACUL FUTURE — March 1, 2027', futureDate: '2027-03-01', body },
    metadata: {
      configuration: CONFIG,
      horizonLabel: '5 years',
      wildcards: [
        { label: 'New pandemic', intensity: 8, custom: false },
        { label: 'Humanoid robot boom', intensity: 6, custom: false },
      ],
      counts: COUNTS,
    },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('slice 09_future-story: future result view', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();

  async function openCompleted(): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(completedRun());
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function flushResult(result: FutureResult): Promise<void> {
    http.expectOne(isResultGet).flush(result);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function failResult(status: number, body: object): Promise<void> {
    http.expectOne(isResultGet).flush(body, { status, statusText: 'Error' });
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

  // @trace FR-23
  it('shows only the loading indicator while getFutureResult is in flight', async () => {
    await openCompleted();
    expect(byId('result-loading')).not.toBeNull();
    expect(byId('result-view')).toBeNull();
    expect(byId('progress-view')).toBeNull();
    const bar = el().querySelector('mat-progress-bar');
    expect(bar).not.toBeNull();
    expect(bar!.getAttribute('aria-valuenow')).toBeNull();
    http.expectOne(isResultGet).flush(futureResult());
  });

  // @trace FR-23
  it('calls getFutureResult exactly once with the run id', async () => {
    await openCompleted();
    const req = http.expectOne(isResultGet);
    expect(req.request.method).toBe('GET');
    req.flush(futureResult());
    harness.detectChanges();
    await harness.fixture.whenStable();
    expect(http.match(isResultGet)).toHaveLength(0);
    expect(http.match(isRunGet)).toHaveLength(0);
  });

  // @trace FR-23
  it('renders both labels, headline, dateline and one paragraph per blank-line separated block', async () => {
    await openCompleted();
    await flushResult(futureResult());
    expect(byId('result-view')).not.toBeNull();
    expect(byId('result-loading')).toBeNull();
    expect(byId('progress-view')).toBeNull();
    expect(text('label-ai-generated')).toBe(LABELS[0]);
    expect(text('label-not-current-news')).toBe(LABELS[1]);
    expect(text('story-headline')).toBe('Stub headline from the future');
    expect(byId('story-headline')!.tagName).toBe('H1');
    expect(text('story-dateline')).toBe('ORACUL FUTURE — March 1, 2027');
    const paragraphs = Array.from(el().querySelectorAll('[data-testid="story-paragraph"]'));
    expect(paragraphs.map((p) => (p.textContent ?? '').trim())).toEqual(['a', 'b', 'c']);
    expect(paragraphs.every((p) => p.tagName === 'P')).toBe(true);
    expect(byId('story-body')!.contains(paragraphs[0])).toBe(true);
  });

  // @trace FR-23
  it('puts the labels above the headline, then dateline, body and the metadata panel (DOM order)', async () => {
    await openCompleted();
    await flushResult(futureResult());
    const order = ['story-labels', 'label-ai-generated', 'label-not-current-news', 'story-headline', 'story-dateline', 'story-body', 'scenario-metadata'];
    const nodes = order.map((id) => {
      const n = byId(id);
      expect(n, id).not.toBeNull();
      return n as HTMLElement;
    });
    expect(byId('story-labels')!.contains(byId('label-ai-generated'))).toBe(true);
    expect(byId('story-labels')!.contains(byId('label-not-current-news'))).toBe(true);
    for (const id of ['label-ai-generated', 'label-not-current-news']) {
      const label = byId(id)!;
      expect(label.compareDocumentPosition(byId('story-headline')!) & Node.DOCUMENT_POSITION_FOLLOWING, id).toBeTruthy();
    }
    const flow = [nodes[3], nodes[4], nodes[5], nodes[6]];
    for (let i = 0; i < flow.length - 1; i++) {
      expect(flow[i].compareDocumentPosition(flow[i + 1]) & Node.DOCUMENT_POSITION_FOLLOWING, order[i + 3]).toBeTruthy();
    }
  });

  // @trace FR-23
  it('renders an HTML-looking body as literal text, never as markup', async () => {
    await openCompleted();
    await flushResult(futureResult('<img src=x onerror=alert(1)>'));
    expect(el().querySelector('img')).toBeNull();
    expect(text('story-body')).toBe('<img src=x onerror=alert(1)>');
    expect(el().querySelectorAll('[data-testid="story-paragraph"]')).toHaveLength(1);
  });

  // @trace FR-23
  it('a 404 shows the failure view "Future not found" and Try again returns to the start', async () => {
    await openCompleted();
    await failResult(404, { code: 'RUN_NOT_FOUND', message: 'Future not found' });
    expect(byId('failure-view')).not.toBeNull();
    expect(text('failure-message')).toBe('Future not found');
    expect(byId('result-view')).toBeNull();
    (byId('try-again') as HTMLButtonElement).click();
    await harness.fixture.whenStable();
    expect(TestBed.inject(Router).url).toBe('/');
  });

  // @trace FR-23
  it('a 409 shows "This future is not ready yet" from the ApiError message', async () => {
    await openCompleted();
    await failResult(409, { code: 'RESULT_NOT_READY', message: 'This future is not ready yet' });
    expect(byId('failure-view')).not.toBeNull();
    expect(text('failure-message')).toBe('This future is not ready yet');
    expect(byId('result-view')).toBeNull();
  });

  // @trace FR-23
  it('a network error shows the generic message', async () => {
    await openCompleted();
    http.expectOne(isResultGet).error(new ProgressEvent('error'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(byId('failure-view')).not.toBeNull();
    expect(text('failure-message')).toBe('Something went wrong — try again');
  });

  // @trace FR-22
  it('shows the open critic issue of the result inside the metadata panel', async () => {
    await openCompleted();
    await flushResult({
      ...futureResult(),
      openCriticIssues: [{ type: 'IGNORED_COUNTER_SIGNALS', description: 'The scenario ignores the counter-signals of the Evidence Pack.' }],
    } as unknown as FutureResult);
    const issue = byId('critic-issue-0');
    expect(issue).not.toBeNull();
    expect(text('critic-issue-0')).toBe('The scenario ignores the counter-signals of the Evidence Pack.');
    expect(byId('scenario-metadata')!.contains(issue)).toBe(true);
    expect(text('critic-issues-title')).toBe("Open questions from ORACUL's critic");
  });

  // @trace FR-22
  it('shows no critic-issues block when the result has no open critic issues', async () => {
    await openCompleted();
    await flushResult(futureResult());
    expect(byId('result-view')).not.toBeNull();
    expect(byId('critic-issues')).toBeNull();
  });
});
