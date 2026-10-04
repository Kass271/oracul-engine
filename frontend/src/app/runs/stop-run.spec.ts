// @trace FR-45
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  type TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import type { ChatGptConnectionState } from '../api/models/chat-gpt-connection-state';
import type { GenerationRun } from '../api/models/generation-run';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const NEW_ID = '99999999-8888-7777-6666-555555555555';
const POLL_MS = 1000;
const STOPPED_HINT = 'Change the settings or click GENERATE THE FUTURE to start a new one.';

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
  limits: {
    intensityMin: 1,
    intensityMax: 10,
    customWildcardMax: 3,
    customWildcardLabelMaxLength: 40,
    defaultWildcardIntensity: 5,
  },
};

const COUNTS = {
  searches: 0,
  articlesRetrieved: 0,
  articlesConsidered: 0,
  uniqueEvents: 0,
  eventsSelected: 0,
  counterSignals: 0,
  sourcesUsed: 0,
};

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
    generationId: 'ORC-2026-10-02-1842',
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
function runAt(
  index: number,
  status: string = 'RUNNING',
  extra: Record<string, unknown> = {},
): GenerationRun {
  const [stage, stageLabel] = STAGES[index - 1];
  return {
    ...queued(),
    status,
    stage,
    stageLabel,
    stageIndex: index,
    updatedAt: '2026-10-02T18:42:40Z',
    ...extra,
  } as GenerationRun;
}

const stoppedRun = (index = 3, extra: Record<string, unknown> = {}): GenerationRun =>
  runAt(index, 'STOPPED', { completedAt: '2026-10-02T18:42:50Z', ...extra });

const ACTIVE = ['QUEUED', 'RUNNING'];
const TERMINAL = ['COMPLETED', 'FAILED', 'INSUFFICIENT_EVIDENCE', 'STOPPED'];
const CONNECTION_STATES: ChatGptConnectionState[] = [
  'CONNECTED',
  'NOT_CONNECTED',
  'SESSION_EXPIRED',
  'PLAN_NOT_ELIGIBLE',
];

describe('run-control FR-45: stop a generation and start a new one', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const snackText = (): string | null =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ??
    null;

  const isRunsPost = (r: { url: string; method: string }): boolean =>
    r.method === 'POST' && r.url.endsWith('/api/runs');
  const isStopPost = (r: { url: string; method: string }): boolean =>
    r.method === 'POST' && r.url.endsWith(`/api/runs/${RUN_ID}/stop`);
  const isRunGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const isConnectionGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection');

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function tick(ms: number): Promise<void> {
    await vi.advanceTimersByTimeAsync(ms);
    fixture.detectChanges();
  }

  async function boot(
    state: ChatGptConnectionState = 'CONNECTED',
    url: string | null = null,
  ): Promise<void> {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.match((r) => r.url.endsWith('/api/scenario/catalogue')).forEach((r) => r.flush(CATALOGUE));
    http
      .match((r) => r.url.endsWith('/api/scenario/configuration'))
      .forEach((r) => r.flush(CATALOGUE.defaults));
    http
      .match(isConnectionGet)
      .forEach((r) => r.flush({ state, canGenerate: state === 'CONNECTED' }));
    await settle();
    if (url) {
      await router.navigateByUrl(url);
      fixture.detectChanges();
    }
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
  }

  function click(id: string): void {
    expect(byId(id), `element ${id}`).not.toBeNull();
    (byId(id) as HTMLButtonElement).click();
    fixture.detectChanges();
  }

  const disabled = (id: string): boolean => {
    expect(byId(id), `element ${id}`).not.toBeNull();
    return (byId(id) as HTMLButtonElement).disabled;
  };

  /** Clicks generate and answers the POST with the given run (202); the run is then tracked. */
  async function generate(run: GenerationRun = queued()): Promise<TestRequest> {
    click('generate-button');
    const req = http.expectOne(isRunsPost);
    req.flush(run, { status: 202, statusText: 'Accepted' });
    await tick(0);
    await tick(0);
    return req;
  }

  /** The tracked run reaches `run` (one poll). */
  async function pollTo(run: GenerationRun): Promise<void> {
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(run);
    await tick(0);
  }

  /** Clicks STOP and answers the stop request. */
  async function stopWith(run: GenerationRun): Promise<void> {
    click('generate-button');
    http.expectOne(isStopPost).flush(run);
    await tick(0);
    await tick(0);
  }

  const setConnection = (state: ChatGptConnectionState): void => {
    const connection = TestBed.inject(ConnectionStore);
    connection.state.set(state);
    connection.canGenerate.set(state === 'CONNECTED');
  };

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

  // ---- label and enabled state of the generate button: every status x every connection state ----

  // run-control.md FR-45 #1 (range & invariant: label STOP iff the tracked run is QUEUED/RUNNING)
  it.each(ACTIVE.flatMap((status) => CONNECTION_STATES.map((state) => [status, state] as const)))(
    'a %s run with ChatGPT %s: the button reads STOP and is enabled',
    async (status, state) => {
      await boot();
      await generate();
      if (status === 'RUNNING') await pollTo(runAt(3));
      setConnection(state);
      await router.navigateByUrl('/');
      await tick(0);
      expect(byId('welcome-view')).not.toBeNull();
      expect(text('generate-button')).toBe('STOP');
      expect(disabled('generate-button')).toBe(false);
    },
  );

  it.each(TERMINAL.flatMap((status) => CONNECTION_STATES.map((state) => [status, state] as const)))(
    'a %s run with ChatGPT %s: GENERATE THE FUTURE, enabled only when connected',
    async (status, state) => {
      await boot();
      await generate();
      await pollTo(
        runAt(
          10,
          status,
          status === 'FAILED'
            ? { failure: { code: 'RUN_TIMEOUT', message: 'Generation took too long — try again' } }
            : {},
        ),
      );
      setConnection(state);
      await router.navigateByUrl('/');
      await tick(0);
      expect(byId('welcome-view')).not.toBeNull();
      expect(text('generate-button')).toBe('GENERATE THE FUTURE');
      expect(disabled('generate-button')).toBe(state !== 'CONNECTED');
    },
  );

  it('no tracked run: GENERATE THE FUTURE (phase-01 rule)', async () => {
    await boot();
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(false);
  });

  // ---- where the button is shown ----

  it('the progress view shows the STOP button below the steps', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    expect(router.url).toBe(`/futures/${RUN_ID}`);
    const progress = byId('progress-view')!;
    expect(progress).not.toBeNull();
    expect(progress.contains(byId('generate-button'))).toBe(true);
    expect(text('generate-button')).toBe('STOP');
    expect(disabled('generate-button')).toBe(false);
    const lastStep = byId('progress-step-WRITING_STORY')!;
    expect(
      lastStep.compareDocumentPosition(byId('generate-button')!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('a QUEUED run shows the STOP button in the progress view too', async () => {
    await boot();
    await generate();
    expect(byId('progress-view')).not.toBeNull();
    expect(text('generate-button')).toBe('STOP');
  });

  // ---- clicking STOP ----

  it('a burst of clicks on STOP sends exactly one POST /api/runs/{id}/stop without a body and keeps the label STOP', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    const button = byId('generate-button') as HTMLButtonElement;
    for (let i = 0; i < 6; i++) button.click();
    fixture.detectChanges();
    const posts = http.match(isStopPost);
    expect(posts).toHaveLength(1);
    expect(posts[0].request.body).toBeNull();
    expect(text('generate-button')).toBe('STOP');
    expect(disabled('generate-button')).toBe(true);
    posts[0].flush(stoppedRun());
    await tick(0);
  });

  it('a 200 STOPPED answer shows the stopped view and the polling ends for good', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    await stopWith(stoppedRun(3));
    expect(byId('stopped-view')).not.toBeNull();
    expect(text('stopped-message')).toBe('Generation stopped');
    expect(text('stopped-hint')).toBe(STOPPED_HINT);
    expect(byId('progress-view')).toBeNull();
    expect(byId('quick-actions')).toBeNull();
    expect(byId('result-view')).toBeNull();
    expect(byId('failure-view')).toBeNull();
    expect(snackText()).toBeNull();
    const stopped = byId('stopped-view')!;
    expect(stopped.contains(byId('generate-button'))).toBe(true);
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(false);
    await tick(10 * POLL_MS);
    expect(http.match(isRunGet)).toHaveLength(0);
    expect(http.match(isStopPost)).toHaveLength(0);
  });

  it('a poll answer that was already in flight when STOP was answered is ignored', async () => {
    await boot();
    await generate();
    await tick(POLL_MS); // the poll request is now outstanding
    const poll = http.expectOne(isRunGet);
    click('generate-button');
    http.expectOne(isStopPost).flush(stoppedRun(3));
    await tick(0);
    expect(byId('stopped-view')).not.toBeNull();
    poll.flush(runAt(4)); // arrives late, RUNNING
    await tick(0);
    expect(byId('stopped-view')).not.toBeNull();
    expect(byId('progress-view')).toBeNull();
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    await tick(5 * POLL_MS);
    expect(http.match(isRunGet)).toHaveLength(0);
  });

  it('STOP also works when ChatGPT is no longer connected', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    setConnection('SESSION_EXPIRED');
    fixture.detectChanges();
    expect(text('generate-button')).toBe('STOP');
    expect(disabled('generate-button')).toBe(false);
    await stopWith(stoppedRun());
    expect(byId('stopped-view')).not.toBeNull();
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(disabled('generate-button')).toBe(true);
  });

  it('GENERATE THE FUTURE after a stop starts a run with the current panel configuration and opens it', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    await stopWith(stoppedRun());
    TestBed.inject(ScenarioStore).setDarkness(9);
    click('generate-button');
    const post = http.expectOne(isRunsPost);
    expect(post.request.body).toEqual({ ...CATALOGUE.defaults, darkness: 9 });
    post.flush({ ...queued(), id: NEW_ID }, { status: 202, statusText: 'Accepted' });
    await tick(0);
    await tick(0);
    expect(router.url).toBe(`/futures/${NEW_ID}`);
    expect(byId('stopped-view')).toBeNull();
    expect(byId('progress-view')).not.toBeNull();
    expect(text('generate-button')).toBe('STOP');
  });

  // ---- stop errors ----

  it.each([
    [
      '404 RUN_NOT_FOUND',
      404,
      { code: 'RUN_NOT_FOUND', message: 'Future not found' },
      'Future not found',
    ],
    [
      '500 INTERNAL_ERROR',
      500,
      { code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' },
      'Something went wrong — try again',
    ],
    ['500 without an ApiError', 500, '<html>boom</html>', 'Something went wrong — try again'],
    ['503 without a body', 503, '', 'Something went wrong — try again'],
  ])(
    '%s: snackbar message, STOP enabled again, polling continues',
    async (_name, status, body, message) => {
      await boot();
      await generate();
      await pollTo(runAt(3));
      click('generate-button');
      http.expectOne(isStopPost).flush(body, { status, statusText: 'Error' });
      await tick(0);
      expect(snackText()).toBe(message);
      expect(text('generate-button')).toBe('STOP');
      expect(disabled('generate-button')).toBe(false);
      expect(byId('stopped-view')).toBeNull();
      expect(byId('progress-view')).not.toBeNull();
      await tick(POLL_MS);
      http.expectOne(isRunGet).flush(runAt(4));
      await tick(0);
      expect(text('progress-stage')).toBe('Reading relevant sources…');
    },
  );

  it('a network error shows the generic message and re-enables STOP', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    click('generate-button');
    http.expectOne(isStopPost).error(new ProgressEvent('error'));
    await tick(0);
    expect(snackText()).toBe('Something went wrong — try again');
    expect(text('generate-button')).toBe('STOP');
    expect(disabled('generate-button')).toBe(false);
  });

  it('after a failed stop STOP can be clicked again and sends a second request', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    click('generate-button');
    http
      .expectOne(isStopPost)
      .flush(
        { code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' },
        { status: 500, statusText: 'Error' },
      );
    await tick(0);
    click('generate-button');
    http.expectOne(isStopPost).flush(stoppedRun());
    await tick(0);
    expect(byId('stopped-view')).not.toBeNull();
  });

  // ---- the error snackbar always closes, also when a second error replaces the first ----
  // run-store showError: dismiss the old snackbar, remove its message node by hand, open a new one for 6000 ms.
  // A snackbar that never closes would cover STOP for good. Gap = time between the first and the second error.

  const FIRST_ERROR = 'First error message';
  const SECOND_ERROR = 'Second error message';
  const SNACK_MS = 6000;
  // enter fallback (200 ms) before the 6 s timer starts + exit fallback (200 ms) after it, plus slack
  const EXIT_MARGIN_MS = 600;
  const messageNodes = (): Element[] => [
    ...document.querySelectorAll('[data-testid="run-error-message"]'),
  ];
  const containers = (): Element[] => [...document.querySelectorAll('.mat-mdc-snack-bar-container')];
  const failWith = (req: TestRequest, message: string): void =>
    req.flush({ code: 'INTERNAL_ERROR', message }, { status: 500, statusText: 'Error' });

  /** Moves the fake clock by `ms` in poll-sized steps and answers every poll that falls due with `answer`. */
  async function advance(ms: number, answer: GenerationRun): Promise<void> {
    let remaining = ms;
    while (remaining > 0) {
      const step = Math.min(POLL_MS, remaining);
      await tick(step);
      remaining -= step;
      http.match(isRunGet).forEach((r) => r.flush(answer));
      await tick(0);
    }
  }

  const FAILED_RUN = (): GenerationRun =>
    runAt(10, 'FAILED', {
      failure: { code: 'RUN_TIMEOUT', message: 'Generation took too long — try again' },
    });

  const SEQUENCES = [
    ['stop', 'stop'],
    ['stop', 'generate'],
    ['generate', 'generate'],
  ] as const;
  // overlapping the first snackbar, just before it closes, during its exit animation, after it closed
  const GAPS = [1000, 3000, 5900, 6050, 6250, 6300, 6500];

  it.each(SEQUENCES.flatMap(([a, b]) => GAPS.map((gap) => [a, b, gap] as const)))(
    'an error from a %s request, then %s request failing %i ms later: only the second message shows and it closes after 6 s',
    async (first, second, gap) => {
      await boot();
      // the run the polls report while the clock runs
      const answer = first === 'stop' && second === 'generate' ? FAILED_RUN() : runAt(3);
      if (first === 'stop') {
        await generate();
        await pollTo(runAt(3));
        click('generate-button');
        failWith(http.expectOne(isStopPost), FIRST_ERROR);
      } else {
        click('generate-button');
        failWith(http.expectOne(isRunsPost), FIRST_ERROR);
      }
      await tick(0);
      expect(snackText()).toBe(FIRST_ERROR);
      expect(messageNodes()).toHaveLength(1);

      await advance(gap, answer);
      if (first === 'stop' && second === 'generate') {
        // the run ended meanwhile: the start button is the one of the welcome view
        await router.navigateByUrl('/');
        await tick(0);
      }

      expect(disabled('generate-button')).toBe(false);
      expect(text('generate-button')).toBe(second === 'stop' ? 'STOP' : 'GENERATE THE FUTURE');
      click('generate-button');
      failWith(http.expectOne(second === 'stop' ? isStopPost : isRunsPost), SECOND_ERROR);
      await tick(0);

      expect(messageNodes(), 'exactly one error message').toHaveLength(1);
      expect(snackText()).toBe(SECOND_ERROR);

      await advance(SNACK_MS + EXIT_MARGIN_MS, answer);

      expect(messageNodes(), 'the message is gone').toHaveLength(0);
      expect(containers(), 'no snackbar container is left').toHaveLength(0);
      // the button is usable again
      expect(disabled('generate-button')).toBe(false);
    },
  );

  it('a single error closes by itself after 6 s', async () => {
    await boot();
    await generate();
    await pollTo(runAt(3));
    click('generate-button');
    failWith(http.expectOne(isStopPost), FIRST_ERROR);
    await tick(0);
    expect(messageNodes()).toHaveLength(1);
    await advance(SNACK_MS - 100, runAt(3));
    expect(messageNodes()).toHaveLength(1);
    await advance(100 + EXIT_MARGIN_MS, runAt(3));
    expect(messageNodes()).toHaveLength(0);
    expect(containers()).toHaveLength(0);
  });

  // ---- the stop that lost the race against the end of the run ----

  it('a stop answered with a COMPLETED run shows the result, without a message', async () => {
    await boot();
    await generate();
    await pollTo(runAt(10));
    click('generate-button');
    http
      .expectOne(isStopPost)
      .flush(
        runAt(10, 'COMPLETED', {
          headline: 'A future headline',
          completedAt: '2026-10-02T18:42:50Z',
        }),
      );
    await tick(0);
    await tick(0);
    expect(byId('stopped-view')).toBeNull();
    expect(snackText()).toBeNull();
    expect(http.match(isResultGet)).toHaveLength(1);
    await tick(5 * POLL_MS);
    expect(http.match(isRunGet)).toHaveLength(0);
  });

  it('a stop answered with a FAILED run shows the failure view, without a message', async () => {
    await boot();
    await generate();
    await pollTo(runAt(5));
    click('generate-button');
    http
      .expectOne(isStopPost)
      .flush(
        runAt(5, 'FAILED', {
          failure: { code: 'RUN_TIMEOUT', message: 'Generation took too long — try again' },
        }),
      );
    await tick(0);
    expect(byId('stopped-view')).toBeNull();
    expect(text('failure-message')).toBe('Generation took too long — try again');
    expect(snackText()).toBeNull();
  });

  // ---- reopening a stopped run ----

  it('/futures/<id> of a STOPPED run shows the stopped view and loads its configuration into the panel', async () => {
    await boot('CONNECTED', `/futures/${RUN_ID}`);
    const config = { ...CATALOGUE.defaults, darkness: 9, horizon: '1y' };
    http.expectOne(isRunGet).flush(stoppedRun(2, { configuration: config }));
    await tick(0);
    await tick(0);
    expect(byId('stopped-view')).not.toBeNull();
    expect(text('stopped-message')).toBe('Generation stopped');
    expect(TestBed.inject(ScenarioStore).darkness()).toBe(9);
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect(http.match(isResultGet)).toHaveLength(0);
    await tick(5 * POLL_MS);
    expect(http.match(isRunGet)).toHaveLength(0);
  });
});
