// @trace FR-10, FR-45
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';

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

  // @trace FR-10
  it('not connected: disabled, with the hint', async () => {
    await boot('NOT_CONNECTED');
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(true);
    expect(text('generate-hint')).toBe('Connect ChatGPT to generate');
    click('generate-button');
    http.expectNone(isRunsPost);
  });

  // @trace FR-10
  it.each(['PLAN_NOT_ELIGIBLE', 'SESSION_EXPIRED'] as ChatGptConnectionState[])('%s: disabled, with the hint', async (state) => {
    await boot(state);
    expect(disabled('generate-button')).toBe(true);
    expect(text('generate-hint')).toBe('Connect ChatGPT to generate');
  });

  // @trace FR-10
  it('connected: enabled and no hint', async () => {
    await boot('CONNECTED');
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(false);
    expect(byId('generate-hint')).toBeNull();
  });

  // @trace FR-10
  it('a start request in flight disables the button and a second click sends nothing', async () => {
    await boot();
    click('generate-button');
    const req = http.expectOne(isRunsPost);
    expect(disabled('generate-button')).toBe(true);
    click('generate-button');
    http.expectNone(isRunsPost);
    req.flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await tick(0);
    expect(disabled('generate-button')).toBe(false);
  });

  // @trace FR-10, FR-45
  it('an active run turns the button into an enabled STOP on the welcome view (run-control.md FR-45 #1)', async () => {
    await boot();
    await generate();
    await router.navigateByUrl('/');
    await tick(0);
    expect(byId('welcome-view')).not.toBeNull();
    expect(text('generate-button')).toBe('STOP');
    expect(disabled('generate-button')).toBe(false);
  });

  // @trace FR-10
  it('a terminal run enables the button again', async () => {
    await boot();
    await generate();
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(10, 'COMPLETED'));
    await tick(0);
    await router.navigateByUrl('/');
    await tick(0);
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(false);
  });

  // @trace FR-10
  it('the snackbar message is replaced, not stacked, by a second error', async () => {
    await boot();
    click('generate-button');
    http.expectOne(isRunsPost).flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await tick(0);
    click('generate-button');
    http.expectOne(isRunsPost).flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status: 500, statusText: 'Error' });
    await tick(0);
    expect(document.querySelectorAll('[data-testid="run-error-message"]').length).toBe(1);
    expect(snackText()).toBe('Something went wrong — try again');
  });
});
