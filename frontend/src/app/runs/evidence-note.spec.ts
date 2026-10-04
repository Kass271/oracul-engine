// @trace FR-47
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const NEW_ID = '99999999-8888-7777-6666-555555555555';
const COUNTS = {
  searches: 20,
  articlesRetrieved: 31,
  articlesConsidered: 12,
  uniqueEvents: 7,
  eventsSelected: 5,
  counterSignals: 3,
  sourcesUsed: 2,
};
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

const insufficientNote = (realism: number, core: number, needed: number) => ({
  kind: 'INSUFFICIENT_EVIDENCE',
  message: `Realism ${realism} couldn't be fully met: only ${core} core evidence ${core === 1 ? 'item' : 'items'} (needs ${needed}). This future is less grounded.`,
  coreItems: core,
  coreNeeded: needed,
});

const NO_EVIDENCE_NOTE = {
  kind: 'NO_EVIDENCE',
  message: 'No current news could be used — this future is speculative, not grounded in evidence.',
  coreItems: 0,
  coreNeeded: 5,
};

function completed(extra: Record<string, unknown> = {}, realism = 10): GenerationRun {
  return {
    id: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    kind: 'STANDARD',
    status: 'COMPLETED',
    stage: 'WRITING_STORY',
    stageLabel: 'Writing from the future…',
    stageIndex: 10,
    stageCount: 10,
    configuration: { ...CONFIG10, realism },
    counts: COUNTS,
    headline: 'A future headline',
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:43:31Z',
    completedAt: '2026-10-02T18:43:31Z',
    hasOpenCriticIssues: false,
    ...extra,
  } as unknown as GenerationRun;
}

function result(realism = 10): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    labels: ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'],
    story: {
      headline: 'A future headline',
      dateline: 'ORACUL FUTURE — March 1, 2027',
      futureDate: '2027-03-01',
      body: 'a\n\nb\n\nc',
    },
    metadata: {
      configuration: { ...CONFIG10, realism },
      horizonLabel: '5 years',
      wildcards: [],
      counts: COUNTS,
    },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('run-control FR-47: the evidence note below the result', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;
  let router: Router;

  const isRunGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const isRunsPost = (r: { url: string; method: string }): boolean =>
    r.method === 'POST' && r.url.endsWith('/api/runs');
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const button = (): HTMLButtonElement => {
    expect(byId('lower-realism'), 'the LOWER REALISM button').not.toBeNull();
    return byId('lower-realism') as HTMLButtonElement;
  };
  const snackText = (): string | null =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ??
    null;

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
    http.expectOne(isResultGet).flush(result(run.configuration.realism));
    await render();
  }

  function accepted(realism: number): GenerationRun {
    return {
      ...completed({}, realism),
      id: NEW_ID,
      status: 'QUEUED',
      stage: undefined,
      stageLabel: undefined,
      stageIndex: 0,
      headline: undefined,
      completedAt: undefined,
    } as unknown as GenerationRun;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    vi.restoreAllMocks();
  });

  // ---- the note exists iff evidenceNote is present ----

  it('a COMPLETED run without evidenceNote shows the result and no note, no LOWER REALISM', async () => {
    await open(completed());
    expect(byId('result-view')).not.toBeNull();
    expect(byId('evidence-note')).toBeNull();
    expect(byId('evidence-note-message')).toBeNull();
    expect(byId('lower-realism')).toBeNull();
  });

  it('an INSUFFICIENT_EVIDENCE note shows exactly the message and the LOWER REALISM button below the result', async () => {
    const note = insufficientNote(10, 2, 5);
    await open(completed({ evidenceNote: note, suggestedRealism: 8 }));
    expect(byId('result-view')).not.toBeNull();
    const box = byId('evidence-note')!;
    expect(box).not.toBeNull();
    expect(box.getAttribute('role')).toBe('note');
    expect(text('evidence-note-message')).toBe(
      "Realism 10 couldn't be fully met: only 2 core evidence items (needs 5). This future is less grounded.",
    );
    expect(box.contains(byId('evidence-note-message'))).toBe(true);
    expect(text('lower-realism')).toBe('LOWER REALISM');
    expect(box.contains(byId('lower-realism'))).toBe(true);
    expect(button().disabled).toBe(false);
    // after <app-future-result>
    const futureResult = el().querySelector('app-future-result')!;
    expect(
      futureResult.compareDocumentPosition(box) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(futureResult.contains(box)).toBe(false);
    // the legacy insufficient view is never shown together with the note
    expect(byId('insufficient-view')).toBeNull();
  });

  it('a NO_EVIDENCE note shows its message and no LOWER REALISM button', async () => {
    await open(completed({ evidenceNote: NO_EVIDENCE_NOTE }));
    expect(byId('evidence-note')).not.toBeNull();
    expect(text('evidence-note-message')).toBe(
      'No current news could be used — this future is speculative, not grounded in evidence.',
    );
    expect(byId('lower-realism')).toBeNull();
    // the SOURCES panel is empty (run-control.md FR-47 step 4); it renders after the user opens it
    expect(byId('open-sources'), 'missing open-sources').not.toBeNull();
    byId('open-sources')!.click();
    await render();
    expect(byId('sources-empty')).not.toBeNull();
  });

  it.each([
    ['NO_EVIDENCE, no suggestion', NO_EVIDENCE_NOTE, undefined, false],
    ['INSUFFICIENT with a suggestion', insufficientNote(8, 2, 3), 6, true],
    ['INSUFFICIENT at realism 1 without a suggestion', insufficientNote(1, 0, 1), undefined, false],
    ['INSUFFICIENT with a suggestion of 1', insufficientNote(2, 0, 1), 1, true],
  ])(
    '%s: LOWER REALISM iff suggestedRealism is present',
    async (_name, note, suggested, button_) => {
      await open(
        completed({
          evidenceNote: note,
          ...(suggested === undefined ? {} : { suggestedRealism: suggested }),
        }),
      );
      expect(byId('evidence-note')).not.toBeNull();
      expect(byId('lower-realism') !== null).toBe(button_);
    },
  );

  it('the message is shown as text, never as HTML', async () => {
    await open(completed({ evidenceNote: { ...NO_EVIDENCE_NOTE, message: '<b>bold</b> note' } }));
    const m = byId('evidence-note-message')!;
    expect(m.textContent!.trim()).toBe('<b>bold</b> note');
    expect(m.querySelector('b')).toBeNull();
  });

  it('a note with the message of the singular form is shown unchanged', async () => {
    const note = insufficientNote(7, 1, 3);
    await open(completed({ evidenceNote: note, suggestedRealism: 5 }, 7));
    expect(text('evidence-note-message')).toBe(
      "Realism 7 couldn't be fully met: only 1 core evidence item (needs 3). This future is less grounded.",
    );
  });

  // ---- LOWER REALISM ----

  it('clicking LOWER REALISM posts the run configuration with realism = suggestedRealism, once, and sets the panel', async () => {
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    button().click(); // a second click while the start is pending sends nothing
    const post = http.expectOne(isRunsPost);
    expect(post.request.body).toEqual({ ...CONFIG10, realism: 8 });
    expect(TestBed.inject(ScenarioStore).realism()).toBe(8);
    expect(TestBed.inject(ScenarioStore).configuration()).toEqual({ ...CONFIG10, realism: 8 });
    expect(button().disabled).toBe(true);
  });

  it('a suggestedRealism of 1 starts a run with realism 1', async () => {
    await open(completed({ evidenceNote: insufficientNote(3, 0, 1), suggestedRealism: 1 }, 3));
    button().click();
    harness.detectChanges();
    expect(http.expectOne(isRunsPost).request.body).toEqual({ ...CONFIG10, realism: 1 });
  });

  it('202 navigates to the new run with replaceUrl', async () => {
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    http.expectOne(isRunsPost).flush(accepted(8), { status: 202, statusText: 'Accepted' });
    await render();
    expect(nav).toHaveBeenCalledWith(['/futures', NEW_ID], { replaceUrl: true });
  });

  it('a refused start shows the API message in the snackbar, keeps the note and re-enables the button', async () => {
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    http
      .expectOne(isRunsPost)
      .flush(
        { code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' },
        { status: 409, statusText: 'Conflict' },
      );
    await render();
    expect(snackText()).toBe('A generation is already running');
    expect(byId('evidence-note')).not.toBeNull();
    expect(button().disabled).toBe(false);
  });

  it('a network error shows the generic message', async () => {
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }));
    button().click();
    harness.detectChanges();
    http.expectOne(isRunsPost).error(new ProgressEvent('error'));
    await render();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(byId('evidence-note')).not.toBeNull();
  });

  it('LOWER REALISM is disabled when ChatGPT is not connected', async () => {
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }), false);
    expect(button()).not.toBeNull();
    expect(button().disabled).toBe(true);
    button().click();
    http.expectNone(isRunsPost);
  });

  it('the note never shows ids, codes, URLs or JSON', async () => {
    await open(completed({ evidenceNote: insufficientNote(10, 2, 5), suggestedRealism: 8 }));
    const content = byId('evidence-note')?.textContent ?? '';
    for (const forbidden of [
      'INSUFFICIENT_EVIDENCE',
      'NO_EVIDENCE',
      RUN_ID,
      'ORC-',
      'http',
      '{',
      '}',
    ]) {
      expect(content).not.toContain(forbidden);
    }
  });
});
