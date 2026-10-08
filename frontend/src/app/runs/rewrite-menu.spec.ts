// @trace FR-67
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import { ConnectionStore } from '../chatgpt/connection.store';
import { SHIPPED_PROFILES, WRITING_STYLES, panelOutput } from '../scenario/testing/panel-fixtures';
import { ScenarioStore } from '../scenario/scenario.store';
import { QuickActions } from './quick-actions';
import { RunStore } from './run.store';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const NEW_ID = '99999999-2222-3333-4444-555555555555';
const COUNTS = {
  searches: 0,
  articlesRetrieved: 0,
  articlesConsidered: 0,
  uniqueEvents: 0,
  eventsSelected: 0,
  counterSignals: 0,
  sourcesUsed: 0,
};

type Cfg = Record<string, unknown>;

/** Independent copy of the spec: the profiles of the shipped file in API order, and their modes. */
const PROFILE_IDS = [
  'official-briefing',
  'neutral-narrative',
  'poe',
  'kafka',
  'wells',
  'verne',
  'orwell',
  'clarke',
  'harrison',
];
const modeOf = (id: string): 'OFFICIAL' | 'NARRATIVE' =>
  id === 'official-briefing' ? 'OFFICIAL' : 'NARRATIVE';
const LANGUAGES: ('en' | 'uk')[] = ['en', 'uk'];
const COMBINATIONS = PROFILE_IDS.flatMap((id) => LANGUAGES.map((l) => [id, l] as const));
const EXPECTED_ENTRIES = [
  'rewrite-language-en',
  ...PROFILE_IDS.map((id) => `rewrite-option-${id}-en`),
  'rewrite-language-uk',
  ...PROFILE_IDS.map((id) => `rewrite-option-${id}-uk`),
];
const BUTTONS = [
  'quick-more-realistic',
  'quick-darker',
  'quick-more-optimistic',
  'quick-more-extreme',
  'quick-alternative',
  'quick-rewrite',
];

function config(overrides: Cfg = {}): Cfg {
  return {
    realism: 5,
    darkness: 5,
    optimism: 5,
    horizon: '5y',
    wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
    customWildcards: [],
    output: { story: true, illustration: false },
    ...overrides,
  };
}

/** A configuration whose output holds the given profile and language. */
const shown = (profileId: string, language: 'en' | 'uk'): Cfg =>
  config({ output: panelOutput(modeOf(profileId), profileId, language) });

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
    headline: 'Rewrite base headline',
    configuration: cfg,
    counts: COUNTS,
    createdAt: '2026-10-02T18:42:31Z',
    updatedAt: '2026-10-02T18:43:10Z',
    completedAt: '2026-10-02T18:43:10Z',
  } as unknown as GenerationRun;
}

function rewriteRun(cfg: Cfg): GenerationRun {
  return {
    id: NEW_ID,
    generationId: 'ORC-2026-10-02-1900',
    kind: 'REWRITE',
    parentRunId: RUN_ID,
    scenarioRunId: RUN_ID,
    status: 'QUEUED',
    stageIndex: 0,
    stageCount: 10,
    configuration: cfg,
    evidencePackId: 'aaaaaaaa-2222-3333-4444-555555555555',
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
    story: {
      headline: 'Rewrite base headline',
      dateline: 'ORACUL FUTURE — March 1, 2027',
      futureDate: '2027-03-01',
      body: 'a\n\nb\n\nc',
    },
    metadata: { configuration: cfg, horizonLabel: '5 years', wildcards: [], counts: COUNTS },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

type Req = { url: string; method: string };
const isRewritePost = (r: Req): boolean =>
  r.method === 'POST' && r.url.endsWith(`/api/runs/${RUN_ID}/rewrites`);
const isRunsPost = (r: Req): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
const isAltPost = (r: Req): boolean =>
  r.method === 'POST' && r.url.endsWith(`/api/runs/${RUN_ID}/alternatives`);
const isRunGet = (r: Req): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
const isNewRunGet = (r: Req): boolean =>
  r.method === 'GET' && r.url.endsWith(`/api/runs/${NEW_ID}`);
const isResultGet = (r: Req): boolean =>
  r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
const isConnectionGet = (r: Req): boolean =>
  r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection');

const overlay = (id: string): HTMLElement | null => document.querySelector(`[data-testid="${id}"]`);
const entries = (): HTMLElement[] =>
  Array.from(
    document.querySelectorAll(
      '[data-testid^="rewrite-language-"], [data-testid^="rewrite-option-"]',
    ),
  ) as HTMLElement[];
const entryIds = (): string[] => entries().map((e) => e.getAttribute('data-testid') ?? '');
const disabledOptions = (): string[] =>
  entries()
    .filter(
      (e) =>
        e.getAttribute('data-testid')?.startsWith('rewrite-option-') && e.hasAttribute('disabled'),
    )
    .map((e) => e.getAttribute('data-testid') ?? '');
const snackText = (): string | null =>
  (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? null)?.trim() ??
  null;

function cleanUp(): void {
  TestBed.inject(RunStore).stop();
  TestBed.inject(MatSnackBar).dismiss();
  document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
  vi.useRealTimers();
}

describe('slice 04_story-rewrite: REWRITE AS… in the result view', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLButtonElement | null =>
    el().querySelector(`[data-testid="${id}"]`);
  const btn = (id: string): HTMLButtonElement => {
    const b = byId(id);
    expect(b, `${id} exists`).not.toBeNull();
    return b as HTMLButtonElement;
  };

  async function render(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  /** Opens /futures/<id> with a COMPLETED run of `cfg`; the style catalogue is loaded unless `catalogue` is false. */
  async function openCompleted(
    cfg: Cfg,
    options: { canGenerate?: boolean; catalogue?: boolean } = {},
  ): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(completed(cfg));
    await render();
    http.expectOne(isResultGet).flush(result(cfg));
    await render();
    const panel = TestBed.inject(ScenarioStore);
    panel.load(cfg as never);
    if (options.catalogue !== false) panel.setWritingStyles(WRITING_STYLES);
    TestBed.inject(ConnectionStore).canGenerate.set(options.canGenerate ?? true);
    await render();
    http.match(() => true);
  }

  async function openMenu(): Promise<void> {
    btn('quick-rewrite').click();
    await render();
  }

  async function choose(id: string): Promise<void> {
    const item = overlay(id);
    expect(item, `${id} exists`).not.toBeNull();
    item!.click();
    await render();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    cleanUp();
  });

  // ---- the button and the menu ----------------------------------------------------------------------------------

  // @trace FR-67
  it('quick-rewrite is a stroked button with the exact text REWRITE AS… after quick-alternative', async () => {
    await openCompleted(config());
    const b = btn('quick-rewrite');
    expect(b.hasAttribute('mat-stroked-button')).toBe(true);
    expect((b.textContent ?? '').trim()).toBe('REWRITE AS…');
    expect((b.textContent ?? '').trim().endsWith('…')).toBe(true);
    const bar = el().querySelector('[data-testid="quick-actions"]') as HTMLElement;
    expect(
      Array.from(bar.querySelectorAll('button')).map((x) => x.getAttribute('data-testid')),
    ).toEqual(BUTTONS);
    expect(b.disabled).toBe(false);
  });

  // @trace FR-67
  it('the menu exists only while it is open and then lives in the overlay, outside the component host', async () => {
    await openCompleted(config());
    expect(overlay('rewrite-menu')).toBeNull();
    await openMenu();
    const menu = overlay('rewrite-menu');
    expect(menu).not.toBeNull();
    expect(el().contains(menu)).toBe(false);
    expect(document.body.contains(menu)).toBe(true);
  });

  // @trace FR-67
  it('the menu holds the two language headings and one item per profile and language in API order', async () => {
    await openCompleted(config());
    await openMenu();
    const menu = overlay('rewrite-menu') as HTMLElement;
    const found = Array.from(
      menu.querySelectorAll('[data-testid^="rewrite-language-"], [data-testid^="rewrite-option-"]'),
    ).map((e) => e.getAttribute('data-testid'));
    expect(found).toEqual(EXPECTED_ENTRIES);
    expect(found).toHaveLength(2 + 2 * SHIPPED_PROFILES.length);
    expect(SHIPPED_PROFILES.map((p) => p.id)).toEqual(PROFILE_IDS);

    const text = (id: string): string => (overlay(id)?.textContent ?? '').trim();
    expect(text('rewrite-language-en')).toBe('English');
    expect(text('rewrite-language-uk')).toBe('Ukrainian');
    for (const p of SHIPPED_PROFILES) {
      expect(text(`rewrite-option-${p.id}-en`), p.id).toBe(p.label);
      expect(text(`rewrite-option-${p.id}-uk`), p.id).toBe(p.label);
    }
  });

  // @trace FR-67
  it('the headings are plain divs, not menu items and not buttons; the options are menu-item buttons', async () => {
    await openCompleted(config());
    await openMenu();
    for (const id of ['rewrite-language-en', 'rewrite-language-uk']) {
      const h = overlay(id) as HTMLElement;
      expect(h.tagName, id).toBe('DIV');
      expect(h.classList.contains('mat-mdc-menu-item'), id).toBe(false);
      expect(h.closest('button'), id).toBeNull();
    }
    for (const o of entries().filter((e) =>
      e.getAttribute('data-testid')?.startsWith('rewrite-option-'),
    )) {
      expect(o.tagName, o.getAttribute('data-testid') ?? '').toBe('BUTTON');
      expect(o.classList.contains('mat-mdc-menu-item'), o.getAttribute('data-testid') ?? '').toBe(
        true,
      );
    }
  });

  // @trace FR-67
  it('clicking a heading sends nothing', async () => {
    await openCompleted(config());
    await openMenu();
    (overlay('rewrite-language-uk') as HTMLElement).click();
    (overlay('rewrite-language-en') as HTMLElement)?.click();
    await render();
    http.expectNone((r) => r.method === 'POST');
  });

  // @trace FR-67
  it('opening the menu sends no request', async () => {
    await openCompleted(config());
    http.match(() => true);
    await openMenu();
    http.expectNone(() => true);
  });

  // @trace FR-67
  it('with no catalogue loaded the menu holds only the two headings', async () => {
    await openCompleted(config(), { catalogue: false });
    expect(TestBed.inject(ScenarioStore).writingStyles()).toBeNull();
    await openMenu();
    expect(overlay('rewrite-menu')).not.toBeNull();
    expect(entryIds()).toEqual(['rewrite-language-en', 'rewrite-language-uk']);
    http.expectNone(() => true);
  });

  // ---- the shown result's own combination ----------------------------------------------------------------------------

  // Ranges & invariants: the menu has 2 headings + 2 x 9 items and exactly one disabled item - the shown result's own effective
  // combination - for each of the 18 combinations as the shown run's configuration, plus the legacy configuration (19 cases).
  // @trace FR-67
  it.each(COMBINATIONS)(
    'a result told as %s / %s disables exactly its own item',
    async (profileId, language) => {
      await openCompleted(shown(profileId, language));
      await openMenu();
      expect(entryIds()).toEqual(EXPECTED_ENTRIES);
      expect(disabledOptions()).toEqual([`rewrite-option-${profileId}-${language}`]);
    },
  );

  // @trace FR-67
  it('a legacy result (no writing style, no language) disables rewrite-option-official-briefing-en only', async () => {
    await openCompleted(config());
    await openMenu();
    expect(entryIds()).toEqual(EXPECTED_ENTRIES);
    expect(disabledOptions()).toEqual(['rewrite-option-official-briefing-en']);
  });

  // @trace FR-67
  it('a style without a language is told in English', async () => {
    await openCompleted(
      config({
        output: {
          story: true,
          illustration: false,
          writingStyle: { mode: 'NARRATIVE', profileId: 'poe' },
        },
      }),
    );
    await openMenu();
    expect(disabledOptions()).toEqual(['rewrite-option-poe-en']);
  });

  // @trace FR-67
  it('a language without a style is told in Official / Briefing', async () => {
    await openCompleted(config({ output: { story: true, illustration: false, language: 'uk' } }));
    await openMenu();
    expect(disabledOptions()).toEqual(['rewrite-option-official-briefing-uk']);
  });

  // @trace FR-67
  it("the own combination is the shown run's, never the panel's", async () => {
    await openCompleted(shown('kafka', 'uk'));
    const panel = TestBed.inject(ScenarioStore);
    panel.setWritingMode('OFFICIAL');
    panel.setLanguage('en');
    await render();
    expect(panel.writingStyle().profileId).toBe('official-briefing');
    await openMenu();
    expect(disabledOptions()).toEqual(['rewrite-option-kafka-uk']);
  });

  // @trace FR-67
  it('a profile id missing from the catalogue disables no item', async () => {
    await openCompleted(shown('poe', 'en'));
    TestBed.inject(RunStore).run.set(
      completed(config({ output: panelOutput('NARRATIVE', 'dickens', 'en') })),
    );
    await render();
    await openMenu();
    expect(entryIds()).toEqual(EXPECTED_ENTRIES);
    expect(disabledOptions()).toEqual([]);
  });

  // ---- choosing an item ---------------------------------------------------------------------------------------------

  // Ranges & invariants: each of the 18 items; the shown result is told as Poe / English, so that item is disabled and sends
  // nothing, every other item sends exactly one POST /api/runs/<id>/rewrites with the profile's mode, id and the item's language.
  // @trace FR-67
  it.each(COMBINATIONS)(
    'choosing %s / %s sends exactly one rewrite request with that body (nothing for the own combination)',
    async (profileId, language) => {
      await openCompleted(shown('poe', 'en'));
      await openMenu();
      await choose(`rewrite-option-${profileId}-${language}`);
      const own = profileId === 'poe' && language === 'en';
      const reqs = http.match(isRewritePost);
      expect(reqs).toHaveLength(own ? 0 : 1);
      if (!own) {
        expect(reqs[0].request.body).toEqual({
          writingStyle: { mode: modeOf(profileId), profileId },
          language,
        });
      }
      http.expectNone(isRunsPost);
      http.expectNone(isAltPost);
    },
  );

  // @trace FR-67
  it('while the request is pending every quick button is disabled and nothing more can be sent', async () => {
    await openCompleted(shown('poe', 'en'));
    await openMenu();
    await choose('rewrite-option-kafka-uk');
    const pending = http.match(isRewritePost);
    expect(pending).toHaveLength(1);
    expect(TestBed.inject(RunStore).starting()).toBe(true);
    for (const id of BUTTONS) expect(btn(id).disabled, id).toBe(true);
    btn('quick-rewrite').click();
    await render();
    const second = overlay('rewrite-option-wells-en');
    second?.click();
    await render();
    http.expectNone(isRewritePost);
    http.expectNone(isRunsPost);
    http.expectNone(isAltPost);
    pending[0].flush(rewriteRun(shown('kafka', 'uk')), { status: 202, statusText: 'Accepted' });
  });

  // @trace FR-67
  it('202: the new run is followed, the panel shows its style and language, progress is shown and polling starts', async () => {
    await openCompleted(shown('poe', 'en'));
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    await openMenu();
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    (overlay('rewrite-option-kafka-uk') as HTMLElement).click();
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    http
      .expectOne(isRewritePost)
      .flush(rewriteRun(shown('kafka', 'uk')), { status: 202, statusText: 'Accepted' });
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(router.url).toBe(`/futures/${NEW_ID}`);
    expect(navigate).toHaveBeenCalledWith(['/futures', NEW_ID], { replaceUrl: true });
    const runs = TestBed.inject(RunStore);
    expect(runs.run()?.id).toBe(NEW_ID);
    expect(runs.run()?.kind).toBe('REWRITE');
    const panel = TestBed.inject(ScenarioStore);
    expect(panel.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'kafka' });
    expect(panel.language()).toBe('uk');
    expect(panel.configuration().output).toEqual(panelOutput('NARRATIVE', 'kafka', 'uk'));
    await vi.advanceTimersByTimeAsync(0);
    harness.detectChanges();
    expect(el().querySelector('[data-testid="progress-view"]')).not.toBeNull();
    expect(el().querySelector('[data-testid="result-view"]')).toBeNull();
    await vi.advanceTimersByTimeAsync(1000);
    expect(http.match(isNewRunGet).length).toBeGreaterThanOrEqual(1);
  });

  // @trace FR-67
  it.each([
    [409, 'RUN_NOT_COMPLETED', 'Only a completed future can be rewritten'],
    [409, 'RUN_ALREADY_ACTIVE', 'A generation is already running'],
    [401, 'CHATGPT_NOT_CONNECTED', 'Connect ChatGPT to generate'],
    [401, 'CHATGPT_SESSION_EXPIRED', 'ChatGPT session expired — please reconnect'],
    [403, 'CHATGPT_PLAN_NOT_ELIGIBLE', 'Your ChatGPT plan is not eligible for ORACUL'],
    [400, 'VALIDATION_FAILED', 'Unknown writing style'],
    [404, 'RUN_NOT_FOUND', 'Future not found'],
    [500, 'INTERNAL_ERROR', 'Something went wrong — try again'],
  ])(
    '%i %s: the message is shown, the result, URL and panel stay, the buttons are enabled again',
    async (status, code, message) => {
      await openCompleted(shown('poe', 'en'));
      const panel = TestBed.inject(ScenarioStore);
      const panelBefore = panel.configuration();
      await openMenu();
      await choose('rewrite-option-kafka-uk');
      http.expectOne(isRewritePost).flush({ code, message }, { status, statusText: 'Error' });
      await render();
      expect(snackText()).toBe(message);
      expect(TestBed.inject(Router).url).toBe(`/futures/${RUN_ID}`);
      expect(el().querySelector('[data-testid="result-view"]')).not.toBeNull();
      expect(TestBed.inject(RunStore).run()?.id).toBe(RUN_ID);
      expect(panel.configuration()).toEqual(panelBefore);
      for (const id of BUTTONS) expect(btn(id).disabled, id).toBe(false);
      const connectionCodes = [
        'CHATGPT_NOT_CONNECTED',
        'CHATGPT_SESSION_EXPIRED',
        'CHATGPT_PLAN_NOT_ELIGIBLE',
        'CHATGPT_REGISTRATION_INVALID',
      ];
      expect(http.match(isConnectionGet).length > 0).toBe(connectionCodes.includes(code));
    },
  );

  // @trace FR-67
  it('a network error shows the generic message and keeps everything', async () => {
    await openCompleted(shown('poe', 'en'));
    await openMenu();
    await choose('rewrite-option-wells-uk');
    http.expectOne(isRewritePost).error(new ProgressEvent('error'));
    await render();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(TestBed.inject(Router).url).toBe(`/futures/${RUN_ID}`);
    expect(el().querySelector('[data-testid="result-view"]')).not.toBeNull();
    for (const id of BUTTONS) expect(btn(id).disabled, id).toBe(false);
  });

  // @trace FR-67
  it('an answer that is not an ApiError shows the generic message', async () => {
    await openCompleted(shown('poe', 'en'));
    await openMenu();
    await choose('rewrite-option-wells-uk');
    http.expectOne(isRewritePost).flush('oops', { status: 500, statusText: 'Server Error' });
    await render();
    expect(snackText()).toBe('Something went wrong — try again');
    expect(btn('quick-rewrite').disabled).toBe(false);
  });
});

describe('slice 04_story-rewrite: quick-rewrite is disabled exactly when quick-alternative is', () => {
  let http: HttpTestingController;
  let fixture: ComponentFixture<QuickActions>;

  type State = 'not connected' | 'starting' | 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'no run';
  const STATES: [State, boolean][] = [
    ['not connected', true],
    ['starting', true],
    ['QUEUED', true],
    ['RUNNING', true],
    ['COMPLETED', false],
    ['no run', true],
  ];

  const bar = (): HTMLElement => fixture.nativeElement as HTMLElement;
  const button = (id: string): HTMLButtonElement =>
    bar().querySelector(`[data-testid="${id}"]`) as HTMLButtonElement;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
    cleanUp();
  });

  async function setUp(state: State): Promise<void> {
    fixture = TestBed.createComponent(QuickActions);
    const connection = TestBed.inject(ConnectionStore);
    const runs = TestBed.inject(RunStore);
    connection.canGenerate.set(state !== 'not connected');
    if (state === 'starting') runs.starting.set(true);
    if (state === 'QUEUED' || state === 'RUNNING' || state === 'COMPLETED') {
      runs.run.set({ ...completed(config()), status: state } as unknown as GenerationRun);
    }
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  // Ranges & invariants: quick-rewrite.disabled = quick-alternative.disabled in every state
  // (not connected, starting, QUEUED, RUNNING, COMPLETED, no run).
  // @trace FR-67
  it.each(STATES)('%s', async (state, disabled) => {
    await setUp(state);
    expect(button('quick-rewrite'), 'quick-rewrite exists').not.toBeNull();
    expect(button('quick-alternative').disabled).toBe(disabled);
    expect(button('quick-rewrite').disabled).toBe(button('quick-alternative').disabled);
  });

  // @trace FR-67
  it.each(STATES.filter(([, disabled]) => disabled))(
    '%s: a click on the disabled quick-rewrite opens no menu and sends nothing',
    async (state) => {
      await setUp(state);
      expect(button('quick-rewrite'), 'quick-rewrite exists').not.toBeNull();
      button('quick-rewrite').click();
      fixture.detectChanges();
      await fixture.whenStable();
      expect(overlay('rewrite-menu')).toBeNull();
      http.expectNone((r) => r.method === 'POST');
    },
  );
});
