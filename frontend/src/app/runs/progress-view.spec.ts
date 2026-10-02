// @trace FR-24
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

  const steps = (): HTMLElement[] =>
    STAGES.map(([stage]) => byId(`progress-step-${stage}`) as HTMLElement);
  const states = (): (string | null)[] => steps().map((s) => s.getAttribute('data-state'));
  const progressText = (): string => (byId('progress-view')?.textContent ?? '');

  async function open(run: GenerationRun): Promise<void> {
    await boot('CONNECTED', `/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(run);
    await tick(0);
  }

  // @trace FR-24
  it('QUEUED shows the first label, an empty bar and every step pending', async () => {
    await open(queued());
    expect(text('progress-stage')).toBe('Understanding your future…');
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('0');
    expect(states()).toEqual(Array(10).fill('pending'));
  });

  // @trace FR-24
  it('RUNNING at stage 3 marks 1-2 done, 3 current, 4-10 pending, bar 30', async () => {
    await open(runAt(3));
    expect(text('progress-stage')).toBe('Searching current events…');
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('30');
    expect(states()).toEqual(['done', 'done', 'current', ...Array(7).fill('pending')]);
  });

  // @trace FR-24
  it('RESEARCH_STRATEGY gives aria-valuenow 20', async () => {
    await open(runAt(2));
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('20');
    expect(text('progress-stage')).toBe('Building research strategy…');
  });

  // @trace FR-24
  it('COMPLETED shows every step done and the bar at 100', async () => {
    await open(runAt(10, 'COMPLETED'));
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('100');
    expect(states()).toEqual(Array(10).fill('done'));
  });

  // @trace FR-24
  it('the checklist lists the ten labels in stage order, exactly', async () => {
    await open(runAt(1));
    expect(steps().map((s) => (s.textContent ?? '').trim())).toEqual(STAGES.map(([, label]) => label));
    expect(states()[0]).toBe('current');
  });

  // @trace FR-24
  it('uses a determinate mat-progress-bar', async () => {
    await open(runAt(5));
    const bar = byId('progress-bar')!;
    expect(bar.tagName.toLowerCase()).toBe('mat-progress-bar');
    expect(bar.getAttribute('mode')).toBe('determinate');
    expect(el().querySelector('mat-list, .mat-mdc-list')).not.toBeNull();
  });

  // @trace FR-24
  it('shows no URLs, JSON, error text, run id or generation id', async () => {
    await open(runAt(4));
    const t = progressText();
    expect(t).not.toMatch(/http/);
    expect(t).not.toMatch(/[{}]/);
    expect(t).not.toMatch(/Exception|Error:/);
    expect(t).not.toContain(RUN_ID);
    expect(t).not.toContain(GENERATION_ID);
  });

  // @trace FR-24
  it('the display follows each polled stage change', async () => {
    await open(runAt(1));
    await tick(POLL_MS);
    http.expectOne(isRunGet).flush(runAt(2));
    await tick(0);
    expect(text('progress-stage')).toBe('Building research strategy…');
    expect(states().slice(0, 3)).toEqual(['done', 'current', 'pending']);
    expect(byId('progress-bar')!.getAttribute('aria-valuenow')).toBe('20');
  });
});
