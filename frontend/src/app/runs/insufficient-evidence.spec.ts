// @trace FR-31
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { GenerationRun } from '../api/models/generation-run';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const NEW_ID = '99999999-8888-7777-6666-555555555555';
const GENERATION_ID = 'ORC-2026-10-02-1842';
const COUNTS = { searches: 20, articlesRetrieved: 31, articlesConsidered: 12, uniqueEvents: 7, eventsSelected: 5, counterSignals: 3, sourcesUsed: 0 };
const CONFIG10: ScenarioConfiguration = {
  realism: 10,
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
const MESSAGE10 = 'ORACUL found insufficient current evidence to construct this scenario at Realism 10.';
const MESSAGE1 = 'ORACUL found insufficient current evidence to construct this scenario at Realism 1.';

function insufficient(realism: number, extra: Record<string, unknown> = {}): GenerationRun {
  return {
    id: RUN_ID,
    generationId: GENERATION_ID,
    kind: 'STANDARD',
    status: 'INSUFFICIENT_EVIDENCE',
    stage: 'RANKING',
    stageLabel: 'Ranking evidence…',
    stageIndex: 6,
    stageCount: 10,
    configuration: { ...CONFIG10, realism },
    counts: COUNTS,
    failure: {
      code: 'INSUFFICIENT_EVIDENCE',
      message: `ORACUL found insufficient current evidence to construct this scenario at Realism ${realism}.`,
    },
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:43:31Z',
    completedAt: '2026-10-02T18:43:31Z',
    hasOpenCriticIssues: false,
    ...extra,
  } as unknown as GenerationRun;
}

describe('slice 12_insufficient-evidence: insufficient view', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;

  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isRunsPost = (r: { url: string; method: string }): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const button = (): HTMLButtonElement => byId('lower-realism') as HTMLButtonElement;
  const snackText = (): string | null =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ?? null;

  async function render(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function open(run: GenerationRun, connected = true): Promise<void> {
    const connection = TestBed.inject(ConnectionStore);
    connection.state.set(connected ? 'CONNECTED' : 'NOT_CONNECTED');
    connection.canGenerate.set(connected);
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(run);
    await render();
  }

  function accepted(realism: number): GenerationRun {
    return {
      ...insufficient(realism),
      id: NEW_ID,
      status: 'QUEUED',
      stage: undefined,
      stageLabel: undefined,
      stageIndex: 0,
      failure: undefined,
      completedAt: undefined,
    } as unknown as GenerationRun;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    vi.restoreAllMocks();
  });

  // @trace FR-31
  it('shows the failure message exactly and an enabled LOWER REALISM button', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }));
    expect(byId('insufficient-view')).not.toBeNull();
    expect(text('insufficient-message')).toBe(MESSAGE10);
    expect(text('lower-realism')).toBe('LOWER REALISM');
    expect(button().disabled).toBe(false);
  });

  // @trace FR-31
  it('clicking LOWER REALISM sets the panel realism and posts the configuration with the suggested realism once', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    expect(TestBed.inject(ScenarioStore).realism()).toBe(8);
    const post = http.expectOne(isRunsPost);
    expect(post.request.body).toEqual({ ...CONFIG10, realism: 8 });
    expect(TestBed.inject(ScenarioStore).configuration()).toEqual({ ...CONFIG10, realism: 8 });
  });

  // @trace FR-31
  it('the button is disabled while the start request is pending, then 202 navigates to the new run with replaceUrl', async () => {
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await open(insufficient(10, { suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    expect(button().disabled).toBe(true);
    http.expectOne(isRunsPost).flush(accepted(8), { status: 202, statusText: 'Accepted' });
    await render();
    expect(nav).toHaveBeenCalledWith(['/futures', NEW_ID], { replaceUrl: true });
  });

  // @trace FR-31
  it('a 409 shows the API message in the snackbar, keeps the view, keeps the lowered realism and re-enables the button', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    http
      .expectOne(isRunsPost)
      .flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await render();
    expect(snackText()).toBe('A generation is already running');
    expect(byId('insufficient-view')).not.toBeNull();
    expect(TestBed.inject(ScenarioStore).realism()).toBe(8);
    expect(button().disabled).toBe(false);
  });

  // @trace FR-31
  it('a network error shows the generic message and keeps the insufficient view', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    http.expectOne(isRunsPost).error(new ProgressEvent('error'));
    await render();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(byId('insufficient-view')).not.toBeNull();
    expect(button().disabled).toBe(false);
  });

  // @trace FR-31
  it('at realism 1 there is no suggestedRealism, so no LOWER REALISM button', async () => {
    await open(insufficient(1));
    expect(text('insufficient-message')).toBe(MESSAGE1);
    expect(byId('lower-realism')).toBeNull();
  });

  // @trace FR-31
  it('a missing failure shows the fallback text with the configured realism', async () => {
    await open(insufficient(10, { suggestedRealism: 8, failure: undefined }));
    expect(text('insufficient-message')).toBe(MESSAGE10);
  });

  // @trace FR-31
  it('LOWER REALISM is disabled when ChatGPT is not connected', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }), false);
    expect(byId('lower-realism')).not.toBeNull();
    expect(button().disabled).toBe(true);
  });

  // @trace FR-31
  it('never shows the code, run id, generation id, counts, URLs or JSON and offers no Try again', async () => {
    await open(insufficient(10, { suggestedRealism: 8 }));
    const content = byId('insufficient-view')?.textContent ?? '';
    expect(content).not.toBe('');
    for (const forbidden of ['INSUFFICIENT_EVIDENCE', RUN_ID, 'ORC-', 'http', '{', '}']) {
      expect(content).not.toContain(forbidden);
    }
    expect(byId('try-again')).toBeNull();
  });
});
