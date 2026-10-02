// @trace FR-10, FR-24
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

  // ---- FR-10: start ----

  // @trace FR-10
  it('start posts exactly the panel configuration, disables the button while in flight, then opens /futures/<id>', async () => {
    await boot();
    click('generate-button');
    const req = http.expectOne(isRunsPost);
    expect(req.request.body).toEqual(CATALOGUE.defaults);
    expect(disabled('generate-button')).toBe(true);
    req.flush(queued(), { status: 202, statusText: 'Accepted' });
    await tick(0);
    await tick(0);
    expect(router.url).toBe(`/futures/${RUN_ID}`);
    expect(byId('progress-view')).not.toBeNull();
    expect(byId('welcome-view')).toBeNull();
  });

  // @trace FR-10
  it('a 409 shows the API message in the snackbar, stays on / and re-enables the button', async () => {
    await boot();
    const open = vi.spyOn(TestBed.inject(MatSnackBar), 'openFromComponent');
    click('generate-button');
    http.expectOne(isRunsPost).flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await tick(0);
    expect(snackText()).toBe('A generation is already running');
    expect(open.mock.calls[0][1]?.duration).toBe(6000);
    expect(router.url).toBe('/');
    expect(byId('welcome-view')).not.toBeNull();
    expect(disabled('generate-button')).toBe(false);
  });

  // @trace FR-10
  it('a response that is not an ApiError shows the generic message', async () => {
    await boot();
    click('generate-button');
    http.expectOne(isRunsPost).flush('<html>boom</html>', { status: 500, statusText: 'Server Error' });
    await tick(0);
    expect(snackText()).toBe('Something went wrong — try again');
    expect(router.url).toBe('/');
  });

  // @trace FR-10
  it('a network error shows the generic message', async () => {
    await boot();
    click('generate-button');
    http.expectOne(isRunsPost).error(new ProgressEvent('error'));
    await tick(0);
    expect(snackText()).toBe('Something went wrong — try again');
  });

  // @trace FR-10
  it.each([
    ['CHATGPT_NOT_CONNECTED', 401, 'Connect ChatGPT to generate'],
    ['CHATGPT_SESSION_EXPIRED', 401, 'ChatGPT session expired — please reconnect'],
    ['CHATGPT_PLAN_NOT_ELIGIBLE', 403, 'Your ChatGPT plan is not eligible for ORACUL'],
  ])('%s shows its message and reloads the connection state', async (code, status, message) => {
    await boot();
    click('generate-button');
    http.expectOne(isRunsPost).flush({ code, message }, { status, statusText: 'Error' });
    await tick(0);
    expect(snackText()).toBe(message);
    expect(http.match(isConnectionGet).length).toBe(1);
  });

  // @trace FR-10
  it('a 409 does not reload the connection state', async () => {
    await boot();
    click('generate-button');
    http.expectOne(isRunsPost).flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await tick(0);
    expect(http.match(isConnectionGet).length).toBe(0);
  });

  // ---- FR-24: polling ----

  // @trace FR-24
  it('first poll happens 1000 ms after the 202, the next 1000 ms after the previous response', async () => {
    await boot();
    await generate();
    await tick(POLL_MS - 1);
    expect(pendingPolls()).toBe(0);
    await tick(1);
    const first = http.expectOne(isRunGet);
    await tick(3 * POLL_MS); // the poll is outstanding: no overlapping requests
    expect(pendingPolls()).toBe(0);
    first.flush(runAt(2));
    await tick(POLL_MS - 1);
    expect(pendingPolls()).toBe(0);
    await tick(1);
    http.expectOne(isRunGet).flush(runAt(3));
    await tick(0);
    expect(text('progress-stage')).toBe('Searching current events…');
  });

  // @trace FR-24
  it.each(['COMPLETED', 'INSUFFICIENT_EVIDENCE', 'FAILED'])('polling stops when the status is %s', async (status) => {
    await boot();
    await generate();
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(10, status));
    await tick(5 * POLL_MS);
    expect(pendingPolls()).toBe(0);
  });

  // @trace FR-24
  it('polling is app-wide: leaving the run view keeps polling and keeps the generate button disabled', async () => {
    await boot();
    await generate();
    await router.navigateByUrl('/');
    await tick(0);
    expect(byId('welcome-view')).not.toBeNull();
    expect(disabled('generate-button')).toBe(true);
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(2));
    await tick(0);
    expect(disabled('generate-button')).toBe(true);
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(10, 'COMPLETED'));
    await tick(0);
    expect(disabled('generate-button')).toBe(false);
  });

  // @trace FR-24
  it('a failed poll keeps the last state and retries on the next tick', async () => {
    await boot();
    await generate();
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(3));
    await tick(0);
    await tick(POLL_MS);
    http.expectOne(isRunGet).error(new ProgressEvent('error'));
    await tick(0);
    expect(text('progress-stage')).toBe('Searching current events…');
    expect(byId('backend-unavailable')).toBeNull();
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(4));
    await tick(0);
    expect(text('progress-stage')).toBe('Reading relevant sources…');
  });

  // @trace FR-24
  it('a 5xx poll counts as a failure; 10 consecutive failures show backend-unavailable and pause polling', async () => {
    await boot();
    await generate();
    for (let i = 0; i < 10; i++) {
      expect(byId('backend-unavailable')).toBeNull();
      await tick(POLL_MS);
      http.expectOne(isRunGet).flush('', { status: i % 2 ? 503 : 500, statusText: 'Unavailable' });
      await tick(0);
    }
    expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
    await tick(5 * POLL_MS);
    expect(pendingPolls()).toBe(0);
  });

  // @trace FR-24
  it('a successful poll resets the failure counter', async () => {
    await boot();
    await generate();
    for (let i = 0; i < 9; i++) {
      await tick(POLL_MS);
      http.expectOne(isRunGet).error(new ProgressEvent('error'));
      await tick(0);
    }
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(2));
    await tick(0);
    for (let i = 0; i < 9; i++) {
      await tick(POLL_MS);
      http.expectOne(isRunGet).error(new ProgressEvent('error'));
      await tick(0);
    }
    expect(byId('backend-unavailable')).toBeNull();
    expect(byId('progress-view')).not.toBeNull();
  });

  // @trace FR-24
  it('a 404 poll shows the not-found failure view and stops polling', async () => {
    await boot();
    await generate();
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush({ code: 'RUN_NOT_FOUND', message: 'Future not found' }, { status: 404, statusText: 'Not Found' });
    await tick(0);
    expect(byId('failure-view')).not.toBeNull();
    expect(text('failure-message')).toBe('Future not found');
    await tick(5 * POLL_MS);
    expect(pendingPolls()).toBe(0);
  });
});
