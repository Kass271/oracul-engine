// @trace FR-29
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';
import { RunStore } from './run.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const NEW_ID = '99999999-2222-3333-4444-555555555555';
const COUNTS = { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 };

type Control = 'realism' | 'darkness' | 'optimism';
type Cfg = Record<string, unknown>;

interface ActionDef {
  action: 'MORE_REALISTIC' | 'DARKER' | 'MORE_OPTIMISTIC' | 'MORE_EXTREME';
  testid: string;
  text: string;
  control: Control;
  /** Independent copy of the spec table: target, or null at the bound. */
  target: (v: number) => number | null;
}

const plus = (v: number): number | null => (v >= 10 ? null : Math.min(10, v + 2));
const ACTIONS: ActionDef[] = [
  { action: 'MORE_REALISTIC', testid: 'quick-more-realistic', text: 'MORE REALISTIC', control: 'realism', target: plus },
  { action: 'DARKER', testid: 'quick-darker', text: 'DARKER', control: 'darkness', target: plus },
  { action: 'MORE_OPTIMISTIC', testid: 'quick-more-optimistic', text: 'MORE OPTIMISTIC', control: 'optimism', target: plus },
  { action: 'MORE_EXTREME', testid: 'quick-more-extreme', text: 'MORE EXTREME', control: 'realism', target: (v) => (v <= 1 ? null : Math.max(1, v - 3)) },
];
const VALUES = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const CASES = ACTIONS.flatMap((a) => VALUES.map((v) => [a.action, v] as const));

function config(overrides: Cfg = {}): Cfg {
  return {
    realism: 5,
    darkness: 5,
    optimism: 5,
    horizon: '5y',
    wildcards: [
      { wildcardId: 'biology-new-pandemic', intensity: 8 },
      { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
    ],
    customWildcards: [{ label: 'Flying trains', intensity: 4 }],
    output: { story: true, illustration: false },
    ...overrides,
  };
}

function completed(cfg: Cfg): GenerationRun {
  return {
    id: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    kind: 'STANDARD',
    status: 'COMPLETED',
    stage: 'WRITING_STORY',
    stageLabel: 'Writing from the future…',
    stageIndex: 10,
    stageCount: 10,
    headline: 'Quick base headline',
    configuration: cfg,
    counts: COUNTS,
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:43:10Z',
    completedAt: '2026-10-02T18:43:10Z',
  } as unknown as GenerationRun;
}

function queued(cfg: Cfg, id = NEW_ID): GenerationRun {
  return {
    id,
    generationId: 'ORC-2026-10-02-1900',
    kind: 'STANDARD',
    status: 'QUEUED',
    stageIndex: 0,
    stageCount: 10,
    configuration: cfg,
    counts: COUNTS,
    createdAt: '2026-10-02T19:00:00Z',
    updatedAt: '2026-10-02T19:00:00Z',
  } as unknown as GenerationRun;
}

function result(cfg: Cfg): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    labels: ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'],
    story: { headline: 'Quick base headline', dateline: 'ORACUL FUTURE — March 1, 2027', futureDate: '2027-03-01', body: 'a\n\nb\n\nc' },
    metadata: { configuration: cfg, horizonLabel: '5 years', wildcards: [], counts: COUNTS },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('slice 16_quick-regeneration: quick actions bar', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  type Req = { url: string; method: string };
  const isRunsPost = (r: Req): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
  const isRunGet = (r: Req): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: Req): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLButtonElement | null => el().querySelector(`[data-testid="${id}"]`);
  const btn = (id: string): HTMLButtonElement => {
    const b = byId(id);
    expect(b, `${id} exists`).not.toBeNull();
    return b as HTMLButtonElement;
  };
  const snackText = (): string | null =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ?? null;

  async function render(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  /** Opens /futures/<id> with a COMPLETED run of `cfg`, flushes its result and loads `cfg` into the panel. */
  async function openCompleted(cfg: Cfg, canGenerate = true): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(completed(cfg));
    await render();
    http.expectOne(isResultGet).flush(result(cfg));
    await render();
    TestBed.inject(ScenarioStore).load(cfg as never);
    TestBed.inject(ConnectionStore).canGenerate.set(canGenerate);
    await render();
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
  }

  async function click(id: string): Promise<void> {
    btn(id).click();
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(RunStore).stop();
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    vi.useRealTimers();
  });

  // @trace FR-29
  it('renders exactly the four buttons with the exact texts in table order and no alternative button', async () => {
    await openCompleted(config());
    const bar = el().querySelector('[data-testid="result-view"] [data-testid="quick-actions"]');
    expect(bar).not.toBeNull();
    const buttons = Array.from((bar as HTMLElement).querySelectorAll('button'));
    expect(buttons.map((b) => b.getAttribute('data-testid'))).toEqual(ACTIONS.map((a) => a.testid));
    expect(buttons.map((b) => (b.textContent ?? '').trim())).toEqual(ACTIONS.map((a) => a.text));
    expect(el().querySelector('[data-testid="quick-alternative"]')).toBeNull();
  });

  // Ranges & invariants: 4 actions x v = 1..10 (40 cases) against the table, enabled state, body and panel invariants.
  // @trace FR-29
  it.each(CASES)('%s at value %i: enabled iff a target exists; click sends one body with only that control replaced', async (action, v) => {
    const def = ACTIONS.find((a) => a.action === action)!;
    const target = def.target(v);
    const before = config({ [def.control]: v });
    await openCompleted(before);
    expect(btn(def.testid).disabled).toBe(target === null);
    await click(def.testid);
    const store = TestBed.inject(ScenarioStore);
    if (target === null) {
      http.expectNone(isRunsPost);
      expect(store.configuration()).toEqual(before);
      return;
    }
    const reqs = http.match(isRunsPost);
    expect(reqs).toHaveLength(1);
    const expected = { ...before, [def.control]: target };
    expect(reqs[0].request.body).toEqual(expected);
    expect(store.configuration()).toEqual(expected);
  });

  // @trace FR-29
  it('the other three buttons follow their own control at the bounds (realism 1 / 10)', async () => {
    await openCompleted(config({ realism: 1 }));
    expect(btn('quick-more-extreme').disabled).toBe(true);
    expect(btn('quick-more-realistic').disabled).toBe(false);
    TestBed.inject(ScenarioStore).setRealism(10);
    await render();
    expect(btn('quick-more-realistic').disabled).toBe(true);
    expect(btn('quick-more-extreme').disabled).toBe(false);
    await click('quick-more-extreme');
    expect(http.expectOne(isRunsPost).request.body).toEqual(config({ realism: 7 }));
  });

  // @trace FR-29
  it('panel edits made while the result is shown are part of the next quick run', async () => {
    await openCompleted(config());
    const store = TestBed.inject(ScenarioStore);
    store.setHorizon('1m');
    store.setOptimism(2);
    await render();
    await click('quick-darker');
    expect(http.expectOne(isRunsPost).request.body).toEqual(config({ horizon: '1m', optimism: 2, darkness: 7 }));
  });

  // @trace FR-29
  it('canGenerate false: all four disabled and a click sends nothing', async () => {
    await openCompleted(config(), false);
    for (const a of ACTIONS) {
      expect(btn(a.testid).disabled).toBe(true);
      await click(a.testid);
    }
    http.expectNone(isRunsPost);
    expect(TestBed.inject(ScenarioStore).configuration()).toEqual(config());
  });

  // @trace FR-29
  it('while the POST is pending all four are disabled and a second click still sends one request', async () => {
    await openCompleted(config());
    await click('quick-darker');
    const req = http.expectOne(isRunsPost);
    for (const a of ACTIONS) expect(btn(a.testid).disabled).toBe(true);
    await click('quick-darker');
    await click('quick-more-optimistic');
    http.expectNone(isRunsPost);
    expect(TestBed.inject(ScenarioStore).optimism()).toBe(5);
    req.flush(queued(config({ darkness: 7 })), { status: 202, statusText: 'Accepted' });
  });

  // @trace FR-29
  it.each(['QUEUED', 'RUNNING'])('an active (%s) run never leaves an enabled quick button', async (status) => {
    await openCompleted(config());
    TestBed.inject(RunStore).run.set({ ...queued(config()), id: RUN_ID, status } as unknown as GenerationRun);
    await render();
    const enabled = ACTIONS.filter((a) => byId(a.testid) && !btn(a.testid).disabled);
    expect(enabled).toEqual([]);
    expect(el().querySelector('[data-testid="progress-view"]')).not.toBeNull();
  });

  // @trace FR-29
  it('202: navigates to the new run and the panel keeps the target value', async () => {
    await openCompleted(config());
    await click('quick-darker');
    http.expectOne(isRunsPost).flush(queued(config({ darkness: 7 })), { status: 202, statusText: 'Accepted' });
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(TestBed.inject(Router).url).toBe(`/futures/${NEW_ID}`);
    expect(TestBed.inject(ScenarioStore).darkness()).toBe(7);
    expect(TestBed.inject(RunStore).run()?.id).toBe(NEW_ID);
  });

  // @trace FR-29
  it('409 RUN_ALREADY_ACTIVE: message shown, panel keeps 7, URL unchanged, buttons enabled again', async () => {
    await openCompleted(config());
    await click('quick-darker');
    http
      .expectOne(isRunsPost)
      .flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(snackText()).toBe('A generation is already running');
    expect(TestBed.inject(ScenarioStore).darkness()).toBe(7);
    expect(TestBed.inject(Router).url).toBe(`/futures/${RUN_ID}`);
    expect(el().querySelector('[data-testid="result-view"]')).not.toBeNull();
    expect(btn('quick-darker').disabled).toBe(false);
    expect(btn('quick-more-optimistic').disabled).toBe(false);
  });

  // @trace FR-29
  it('network error (status 0): generic message, panel keeps the value', async () => {
    await openCompleted(config());
    await click('quick-more-optimistic');
    http.expectOne(isRunsPost).error(new ProgressEvent('error'));
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(TestBed.inject(ScenarioStore).optimism()).toBe(7);
    expect(btn('quick-more-optimistic').disabled).toBe(false);
  });

  // @trace FR-29
  it('500 without an ApiError body: generic message, panel keeps the value', async () => {
    await openCompleted(config());
    await click('quick-more-extreme');
    http.expectOne(isRunsPost).flush('oops', { status: 500, statusText: 'Server Error' });
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(TestBed.inject(ScenarioStore).realism()).toBe(2);
  });
});
