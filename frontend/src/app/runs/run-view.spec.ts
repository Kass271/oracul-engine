// @trace FR-24
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { App } from '../app';
import { routes } from '../app.routes';
import type { ChatGptConnectionState } from '../api/models/chat-gpt-connection-state';
import type { GenerationRun } from '../api/models/generation-run';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const GENERATION_ID = 'ORC-2026-10-02-1842';
const POLL_MS = 1000;

const CATALOGUE = {
  horizons: [{ code: '1y', label: '1 year' }],
  categories: [],
  defaults: {
    realism: 8,
    darkness: 5,
    optimism: 5,
    horizon: '1y',
    wildcards: [],
    customWildcards: [],
    output: { story: true, illustration: false },
  },
  limits: { intensityMin: 1, intensityMax: 10, customWildcardMax: 3, customWildcardLabelMaxLength: 40, defaultWildcardIntensity: 5 },
};

const COUNTS = { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 };

const STAGES: [string, string][] = [
  ['UNDERSTANDING', 'Understanding your future…'],
  ['RESEARCH_STRATEGY', 'Building research strategy…'],
  ['SEARCHING', 'Searching current events…'],
  ['READING_SOURCES', 'Reading relevant sources…'],
  ['CONNECTING_SIGNALS', 'Connecting signals…'],
  ['RANKING', 'Ranking evidence…'],
  ['EXPLORING_FUTURES', 'Exploring possible futures…'],
  ['CHALLENGING_ASSUMPTIONS', 'Challenging assumptions…'],
  ['CONSTRUCTING_SCENARIO', 'Constructing scenario…'],
  ['WRITING_STORY', 'Writing from the future…'],
];

function queued(): GenerationRun {
  return {
    id: RUN_ID,
    generationId: GENERATION_ID,
    kind: 'STANDARD',
    status: 'QUEUED',
    stageIndex: 0,
    stageCount: 10,
    configuration: CATALOGUE.defaults,
    counts: COUNTS,
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:42:31Z',
  } as GenerationRun;
}

/** Run at stage `index` (1-based); status RUNNING unless given. */
function runAt(index: number, status: string = 'RUNNING'): GenerationRun {
  const [stage, stageLabel] = STAGES[index - 1];
  return { ...queued(), status, stage, stageLabel, stageIndex: index, updatedAt: '2026-10-02T18:42:40Z' } as GenerationRun;
}

describe('slice 04_run-start', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const snackText = (): string | null =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ?? null;

  const isRunsPost = (r: { url: string; method: string }): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const pendingPolls = (): number => http.match(isRunGet).length;
  const isConnectionGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection');

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  /** Advances (fake) time and renders. */
  async function tick(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  /** Boots the app; navigates to `url` when given (a run url flushes the opening GET with `open`). */
  async function boot(state: ChatGptConnectionState = 'CONNECTED', url: string | null = null): Promise<void> {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.match((r) => r.url.endsWith('/api/scenario/catalogue')).forEach((r) => r.flush(CATALOGUE));
    http.match((r) => r.url.endsWith('/api/scenario/configuration')).forEach((r) => r.flush(CATALOGUE.defaults));
    http.match(isConnectionGet).forEach((r) => r.flush({ state, canGenerate: state === 'CONNECTED' }));
    await settle();
    if (url) {
      await router.navigateByUrl(url);
      fixture.detectChanges();
    }
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
  }

  function click(id: string): void {
    (byId(id) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  const disabled = (id: string): boolean => (byId(id) as HTMLButtonElement).disabled;

  /** Clicks generate and answers the POST with the given run (202). */
  async function generate(run: GenerationRun = queued()): Promise<TestRequest> {
    click('generate-button');
    const req = http.expectOne(isRunsPost);
    req.flush(run, { status: 202, statusText: 'Accepted' });
    await tick(0);
    await tick(0);
    return req;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    vi.useRealTimers();
  });

  const views = (): string[] => ['progress-view', 'failure-view', 'backend-unavailable'].filter((id) => byId(id) !== null);

  // @trace FR-24
  it('opening /futures/<id> loads the run immediately and shows exactly the progress view', async () => {
    await boot('CONNECTED', `/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(runAt(3));
    await tick(0);
    expect(views()).toEqual(['progress-view']);
    expect(text('progress-stage')).toBe('Searching current events…');
    expect(byId('welcome-view')).toBeNull();
  });

  // @trace FR-24
  it('an unknown run shows "Future not found" with Try again, which returns to the welcome view', async () => {
    await boot('CONNECTED', '/futures/00000000-0000-0000-0000-000000000000');
    http
      .expectOne((r) => r.method === 'GET' && r.url.endsWith('/api/runs/00000000-0000-0000-0000-000000000000'))
      .flush({ code: 'RUN_NOT_FOUND', message: 'Future not found' }, { status: 404, statusText: 'Not Found' });
    await tick(0);
    expect(views()).toEqual(['failure-view']);
    expect(text('failure-message')).toBe('Future not found');
    expect(text('try-again')).toBe('Try again');
    click('try-again');
    await tick(0);
    await tick(0);
    expect(router.url).toBe('/');
    expect(byId('welcome-view')).not.toBeNull();
    expect(byId('failure-view')).toBeNull();
  });

  // @trace FR-24
  it('after 10 failed polls the view shows backend-unavailable and Try again resumes polling', async () => {
    await boot('CONNECTED', `/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(runAt(2));
    await tick(0);
    for (let i = 0; i < 10; i++) {
      await tick(POLL_MS);
      http.expectOne(isRunGet).error(new ProgressEvent('error'));
      await tick(0);
    }
    expect(views()).toEqual(['backend-unavailable']);
    expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
    expect(text('backend-retry')).toBe('Try again');
    click('backend-retry');
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(3));
    await tick(0);
    expect(views()).toEqual(['progress-view']);
    expect(text('progress-stage')).toBe('Searching current events…');
  });

  // @trace FR-24
  it('reloading the url resumes the run in its current state', async () => {
    await boot('CONNECTED', `/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(runAt(10, 'COMPLETED'));
    await tick(0);
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('100');
    await tick(5 * POLL_MS);
    expect(pendingPolls()).toBe(0);
  });

  // @trace FR-24
  it('an unknown path redirects to the welcome view', async () => {
    await boot('CONNECTED', '/nowhere/at/all');
    await tick(0);
    await tick(0);
    expect(router.url).toBe('/');
    expect(byId('welcome-view')).not.toBeNull();
  });
});

describe('slice 09_future-story: run view switches to the result', () => {
  const ID = '11111111-2222-3333-4444-555555555555';
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const has = (selector: string): boolean => el().querySelector(selector) !== null;

  function run(status: string, stageIndex: number, headline?: string): GenerationRun {
    const [stage, stageLabel] = STAGES[stageIndex - 1];
    return {
      ...queued(),
      id: ID,
      status,
      stage,
      stageLabel,
      stageIndex,
      ...(headline ? { headline, completedAt: '2026-10-02T18:43:10Z' } : {}),
    } as GenerationRun;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    vi.useRealTimers();
  });

  // @trace FR-23
  it('COMPLETED with a headline shows the future result and no progress view', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(run('COMPLETED', 10, 'Stub headline from the future'));
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(has('app-future-result')).toBe(true);
    expect(has('[data-testid="progress-view"]')).toBe(false);
    http.expectOne(isResultGet);
  });

  // @trace FR-23
  it('COMPLETED without a headline keeps the progress view', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(run('COMPLETED', 10));
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(has('[data-testid="progress-view"]')).toBe(true);
    expect(has('app-future-result')).toBe(false);
    expect(http.match(isResultGet)).toHaveLength(0);
  });

  // @trace FR-23
  it('a RUNNING run shows the progress view and no result call is made', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(run('RUNNING', 10));
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(has('[data-testid="progress-view"]')).toBe(true);
    expect(has('app-future-result')).toBe(false);
    expect(http.match(isResultGet)).toHaveLength(0);
  });

  // @trace FR-23
  it('polling that sees COMPLETED with a headline replaces the progress view by the result within 2 s', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    http.expectOne(isRunGet).flush(run('RUNNING', 10));
    harness.detectChanges();
    expect(has('[data-testid="progress-view"]')).toBe(true);
    await vi.advanceTimersByTimeAsync(1000);
    http.expectOne(isRunGet).flush(run('COMPLETED', 10, 'Stub headline from the future'));
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(has('app-future-result')).toBe(true);
    expect(has('[data-testid="progress-view"]')).toBe(false);
    http.expectOne(isResultGet);
  });
});

describe('slice 11_run-failures', () => {
  const ID = '11111111-2222-3333-4444-555555555555';
  const FAILURES: [string, string][] = [
    ['NEWS_UNAVAILABLE', 'ORACUL could not reach its news sources — try again later'],
    ['CHATGPT_RATE_LIMITED', 'ChatGPT plan limit reached — try again later'],
    ['CHATGPT_UNAVAILABLE', 'ChatGPT is unavailable right now — try again later'],
    ['CHATGPT_SESSION_EXPIRED', 'ChatGPT session expired — please reconnect'],
    ['RUN_TIMEOUT', 'Generation took too long — try again'],
    ['INVALID_SCENARIO', 'ORACUL could not construct a valid scenario'],
    ['SCENARIO_REJECTED', 'ORACUL could not construct a scenario supported by current evidence'],
    ['RUN_INTERRUPTED', 'Generation was interrupted — try again'],
    ['INTERNAL_ERROR', 'Something went wrong — try again'],
  ];
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean => r.url.includes(`/api/runs/${ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const has = (selector: string): boolean => el().querySelector(selector) !== null;
  const text = (id: string): string => (el().querySelector(`[data-testid="${id}"]`)?.textContent ?? '').trim();

  function failed(code: string | null, message?: string, stageIndex = 3): GenerationRun {
    const [stage, stageLabel] = STAGES[stageIndex - 1];
    return {
      ...queued(),
      id: ID,
      status: 'FAILED',
      stage,
      stageLabel,
      stageIndex,
      completedAt: '2026-10-02T18:45:31Z',
      ...(code ? { failure: { code, message } } : {}),
    } as GenerationRun;
  }

  async function render(): Promise<void> {
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
    vi.useRealTimers();
  });

  // @trace FR-32
  it.each(FAILURES)('a FAILED run with %s shows only the failure view with its table message', async (code, message) => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(failed(code, message));
    await render();
    const present = ['progress-view', 'failure-view', 'backend-unavailable'].filter((id) => has(`[data-testid="${id}"]`));
    expect(present).toEqual(['failure-view']);
    expect(has('app-future-result')).toBe(false);
    expect(text('failure-message')).toBe(message);
    expect(http.match(isResultGet)).toHaveLength(0);
  });

  // @trace FR-32
  it('a FAILED run without a failure shows the generic message', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(failed(null));
    await render();
    expect(has('[data-testid="failure-view"]')).toBe(true);
    expect(text('failure-message')).toBe('Something went wrong — try again');
  });

  // @trace FR-32
  it('polling RUNNING then FAILED RUN_TIMEOUT switches to the failure view within one tick and stops polling', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    http.expectOne(isRunGet).flush(runAt(3));
    harness.detectChanges();
    expect(has('[data-testid="progress-view"]')).toBe(true);
    await vi.advanceTimersByTimeAsync(1000);
    http.expectOne(isRunGet).flush(failed('RUN_TIMEOUT', 'Generation took too long — try again'));
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(has('[data-testid="failure-view"]')).toBe(true);
    expect(has('[data-testid="progress-view"]')).toBe(false);
    expect(text('failure-message')).toBe('Generation took too long — try again');
    await vi.advanceTimersByTimeAsync(5000);
    expect(http.match(isRunGet)).toHaveLength(0);
  });

  // @trace FR-32
  it('the failure view never shows the code, the run id, the generation id, a URL or JSON', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(failed('RUN_TIMEOUT', 'Generation took too long — try again'));
    await render();
    const content = el().querySelector('[data-testid="failure-view"]')?.textContent ?? '';
    expect(content).not.toBe('');
    for (const forbidden of ['RUN_TIMEOUT', ID, GENERATION_ID, 'http', '{', '}']) {
      expect(content).not.toContain(forbidden);
    }
  });

  // @trace FR-32
  it('Try again of a FAILED run re-submits the failed run configuration', async () => {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush({ ...failed('RUN_TIMEOUT', 'Generation took too long — try again'), configuration: { ...CATALOGUE.defaults, darkness: 9 } });
    await render();
    (el().querySelector('[data-testid="try-again"]') as HTMLButtonElement).click();
    const post = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/runs'));
    expect(post.request.body).toEqual({ ...CATALOGUE.defaults, darkness: 9 });
  });
});
