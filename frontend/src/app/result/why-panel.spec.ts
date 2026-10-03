// @trace FR-26
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { CausalStep } from '../api/models/causal-step';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import type { ResultSource } from '../api/models/result-source';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const LABELS = ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'];
const COUNTS = {
  searches: 20,
  articlesRetrieved: 31,
  articlesConsidered: 12,
  uniqueEvents: 7,
  eventsSelected: 2,
  counterSignals: 1,
  sourcesUsed: 2,
};
const CONFIG = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

const CHAIN: CausalStep[] = [
  {
    order: 1,
    informationClass: 'FACT',
    claimId: 'F1',
    statement: 'Ports adopt robots.',
    evidenceIds: ['E001', 'E004'],
  },
  {
    order: 2,
    informationClass: 'INFERENCE',
    claimId: 'I1',
    statement: 'Labour demand shifts.',
    evidenceIds: ['E002', 'E009'],
  },
  {
    order: 3,
    informationClass: 'SPECULATION',
    claimId: 'P1',
    statement: 'Unions push back <b>hard</b>.',
    evidenceIds: ['E003'],
  },
  {
    order: 4,
    informationClass: 'FUTURE_EVENT',
    statement: 'Robots run the ports.',
    evidenceIds: [],
    year: 2031,
  },
] as CausalStep[];

const SOURCES: ResultSource[] = [
  {
    evidenceId: 'E001',
    section: 'CORE',
    title: 'Ports adopt robots',
    publisher: 'Reuters',
    publishedAt: '2026-09-30T23:30:00Z',
    url: 'https://example.com/a',
    usedInScenario: true,
    counterSignal: false,
  },
  {
    evidenceId: 'E002',
    section: 'SUPPORTING',
    title: 'Robot sales rise',
    publisher: 'AP',
    url: 'http://example.com/b',
    usedInScenario: false,
    counterSignal: false,
  },
  {
    evidenceId: 'E003',
    section: 'COUNTER_SIGNAL',
    title: 'Unions win',
    publisher: 'BBC',
    publishedAt: '2026-01-05T08:00:00Z',
    url: 'https://example.com/c',
    usedInScenario: false,
    counterSignal: true,
  },
  {
    evidenceId: 'E004',
    section: 'COUNTER_SIGNAL',
    title: '',
    publisher: '',
    url: 'javascript:alert(1)',
    usedInScenario: true,
    counterSignal: true,
  },
] as ResultSource[];

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

function futureResult(
  chain: CausalStep[] = CHAIN,
  sources: ResultSource[] = SOURCES,
): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-02-1842',
    labels: LABELS,
    story: {
      headline: 'Stub headline from the future',
      dateline: 'ORACUL FUTURE — March 1, 2027',
      futureDate: '2027-03-01',
      body: 'a\n\nb\n\nc',
    },
    metadata: { configuration: CONFIG, horizonLabel: '5 years', wildcards: [], counts: COUNTS },
    causalChain: chain,
    sources,
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('slice 13_why-and-sources: WHY panel', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const all = (re: RegExp): HTMLElement[] =>
    Array.from(el().querySelectorAll<HTMLElement>('[data-testid]')).filter((n) =>
      re.test(n.getAttribute('data-testid') ?? ''),
    );
  const ids = (re: RegExp): string[] => all(re).map((n) => n.getAttribute('data-testid') as string);
  const highlighted = (): string[] =>
    Array.from(el().querySelectorAll<HTMLElement>('[data-testid^="source-item-"]'))
      .filter(
        (n) => n.classList.contains('highlighted') || n.getAttribute('data-highlighted') === 'true',
      )
      .map((n) => n.getAttribute('data-testid') as string);

  async function settle(): Promise<void> {
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  async function openResult(result: FutureResult = futureResult()): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http.expectOne(isRunGet).flush(completedRun());
    await settle();
    http.expectOne(isResultGet).flush(result);
    await settle();
  }

  async function click(id: string): Promise<void> {
    expect(byId(id), `missing ${id}`).not.toBeNull();
    (byId(id) as HTMLElement).click();
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.match(() => true);
  });

  // @trace FR-26
  it('W1 shows the closed WHY button and neither panel', async () => {
    await openResult();
    expect(text('open-why')).toBe('WHY COULD THIS HAPPEN?');
    expect(byId('open-why')!.getAttribute('aria-expanded')).toBe('false');
    expect(byId('open-why')!.getAttribute('type')).toBe('button');
    expect(byId('why-panel')).toBeNull();
    expect(byId('sources-panel')).toBeNull();
    expect(byId('result-actions')!.contains(byId('open-why'))).toBe(true);
    expect(byId('result-actions')!.contains(byId('open-sources'))).toBe(true);
  });

  // @trace FR-26
  it('W2 opens the chain in order with class labels, literal statements and arrows', async () => {
    await openResult();
    await click('open-why');
    expect(byId('why-panel')).not.toBeNull();
    expect(byId('open-why')!.getAttribute('aria-expanded')).toBe('true');
    expect(text('why-title')).toBe('WHY COULD THIS HAPPEN?');
    expect(ids(/^why-step-\d+$/)).toEqual(['why-step-1', 'why-step-2', 'why-step-3', 'why-step-4']);
    expect(['1', '2', '3', '4'].map((o) => text(`why-step-class-${o}`))).toEqual([
      'FACT',
      'INFERENCE',
      'SPECULATION',
      'ORACUL FUTURE — 2031',
    ]);
    expect(text('why-step-statement-1')).toBe('Ports adopt robots.');
    expect(text('why-step-statement-3')).toBe('Unions push back <b>hard</b>.');
    expect(byId('why-step-3')!.querySelector('b')).toBeNull();
    const arrows = all(/^why-arrow$/);
    expect(arrows).toHaveLength(3);
    expect(arrows.every((a) => (a.textContent ?? '').trim() === '↓')).toBe(true);
    expect(byId('why-panel')!.contains(byId('why-step-1'))).toBe(true);
  });

  // @trace FR-26
  it('W3 renders chips only on FACT and INFERENCE steps; disabled iff the source is missing', async () => {
    await openResult();
    await click('open-why');
    expect(ids(/^why-evidence-\d+-E\d+$/)).toEqual([
      'why-evidence-1-E001',
      'why-evidence-1-E004',
      'why-evidence-2-E002',
      'why-evidence-2-E009',
    ]);
    for (const id of ['E001', 'E004']) expect(text(`why-evidence-1-${id}`)).toBe(id);
    expect(text('why-evidence-2-E009')).toBe('E009');
    expect(byId('why-evidence-1-E001')!.tagName).toBe('BUTTON');
    expect(byId('why-evidence-3-E003')).toBeNull();
    expect(byId('why-evidence-list-3')).toBeNull();
    expect(byId('why-evidence-list-4')).toBeNull();
    expect(byId('why-evidence-list-1')).not.toBeNull();
    expect((byId('why-evidence-2-E009') as HTMLButtonElement).disabled).toBe(true);
    for (const id of ['why-evidence-1-E001', 'why-evidence-1-E004', 'why-evidence-2-E002']) {
      expect((byId(id) as HTMLButtonElement).disabled, id).toBe(false);
    }
  });

  // @trace FR-26
  it('W4 an enabled chip opens SOURCES and highlights exactly that item', async () => {
    await openResult();
    await click('open-why');
    await click('why-evidence-1-E001');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('true');
    expect(byId('why-panel')).not.toBeNull();
    const item = byId('source-item-E001')!;
    expect(item.classList.contains('highlighted')).toBe(true);
    expect(item.getAttribute('data-highlighted')).toBe('true');
    expect(highlighted()).toEqual(['source-item-E001']);
    expect(el().querySelectorAll('[data-highlighted="true"]')).toHaveLength(1);
    expect(http.match(isResultGet)).toHaveLength(0);
  });

  // @trace FR-26
  it('W4b scrolls the highlighted item into view (smooth, centered) when scrollIntoView exists', async () => {
    const spy = vi.fn();
    const original = HTMLElement.prototype.scrollIntoView;
    HTMLElement.prototype.scrollIntoView = spy;
    try {
      await openResult();
      await click('open-why');
      await click('why-evidence-1-E001');
      await vi.waitFor(() => expect(spy).toHaveBeenCalled());
      expect(spy).toHaveBeenCalledWith({ behavior: 'smooth', block: 'center' });
      expect(spy.mock.contexts[0]).toBe(byId('source-item-E001'));
    } finally {
      HTMLElement.prototype.scrollIntoView = original;
    }
  });

  // @trace FR-26
  it('W5 the highlight lasts exactly 3000 ms', async () => {
    await openResult();
    await click('open-why');
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    await click('why-evidence-1-E001');
    expect(highlighted()).toEqual(['source-item-E001']);
    await vi.advanceTimersByTimeAsync(2999);
    harness.detectChanges();
    expect(highlighted()).toEqual(['source-item-E001']);
    await vi.advanceTimersByTimeAsync(1);
    harness.detectChanges();
    expect(highlighted()).toEqual([]);
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('why-panel')).not.toBeNull();
  });

  // @trace FR-26
  it('W6 a second click restarts the 3000 ms timer and moves the highlight', async () => {
    await openResult();
    await click('open-why');
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    await click('why-evidence-1-E001');
    await vi.advanceTimersByTimeAsync(2000);
    await click('why-evidence-2-E002');
    expect(highlighted()).toEqual(['source-item-E002']);
    await vi.advanceTimersByTimeAsync(2999);
    harness.detectChanges();
    expect(highlighted()).toEqual(['source-item-E002']);
    await vi.advanceTimersByTimeAsync(1);
    harness.detectChanges();
    expect(highlighted()).toEqual([]);
  });

  // @trace FR-26
  it('W7 a disabled chip does nothing', async () => {
    await openResult();
    await click('open-why');
    (byId('why-evidence-2-E009') as HTMLButtonElement).click();
    await settle();
    expect(byId('sources-panel')).toBeNull();
    expect(highlighted()).toEqual([]);
  });

  // @trace FR-26
  it('W8 a chip click keeps an already open SOURCES panel open', async () => {
    await openResult();
    await click('open-sources');
    await click('open-why');
    await click('why-evidence-1-E004');
    expect(byId('sources-panel')).not.toBeNull();
    expect(highlighted()).toEqual(['source-item-E004']);
  });

  // @trace FR-26
  it('W9 a FUTURE_EVENT step without year uses the year of story.futureDate', async () => {
    const chain = CHAIN.map((s) => (s.order === 4 ? { ...s, year: undefined } : s));
    await openResult(futureResult(chain));
    await click('open-why');
    expect(text('why-step-class-4')).toBe('ORACUL FUTURE — 2027');
  });

  // @trace FR-26
  it('W10 the WHY button toggles the panel', async () => {
    await openResult();
    await click('open-why');
    await click('open-why');
    expect(byId('why-panel')).toBeNull();
    expect(byId('open-why')!.getAttribute('aria-expanded')).toBe('false');
  });

  // @trace FR-26
  it('W11 opening WHY leaves SOURCES closed (panels are independent) and the DOM order is metadata, actions, why', async () => {
    await openResult();
    await click('open-why');
    expect(byId('sources-panel')).toBeNull();
    const follows = (a: string, b: string): boolean =>
      !!(byId(a)!.compareDocumentPosition(byId(b)!) & Node.DOCUMENT_POSITION_FOLLOWING);
    expect(follows('scenario-metadata', 'result-actions')).toBe(true);
    expect(follows('open-why', 'open-sources')).toBe(true);
    expect(follows('result-actions', 'why-panel')).toBe(true);
    expect(byId('result-view')!.contains(byId('why-panel'))).toBe(true);
  });

  // @trace FR-26
  it.each([
    ['FACT', 'FACT', ['E001'], true],
    ['INFERENCE', 'INFERENCE', ['E001'], true],
    ['SPECULATION', 'SPECULATION', ['E001'], false],
    ['FUTURE_EVENT', 'ORACUL FUTURE — 2031', ['E001'], false],
  ] as const)(
    'R1 class %s shows label %s; chips only for FACT/INFERENCE even when evidenceIds is non-empty',
    async (klass, label, evidenceIds, chips) => {
      const chain = [
        { order: 1, informationClass: 'FACT', statement: 's1', evidenceIds: [] },
        {
          order: 2,
          informationClass: klass,
          statement: 's2',
          evidenceIds: [...evidenceIds],
          year: 2031,
        },
      ] as unknown as CausalStep[];
      await openResult(futureResult(chain));
      await click('open-why');
      expect(text('why-step-class-2')).toBe(label);
      expect(!!byId('why-evidence-2-E001')).toBe(chips);
      expect(!!byId('why-evidence-list-2')).toBe(chips);
    },
  );

  // @trace FR-26
  it.each([
    [0, []],
    [1, ['E001']],
    [2, ['E001', 'E002']],
  ] as const)(
    'R2 a FACT step with %i evidence ids renders that many chips in order',
    async (_n, evidenceIds) => {
      const chain = [
        { order: 1, informationClass: 'FACT', statement: 's1', evidenceIds: [...evidenceIds] },
        {
          order: 2,
          informationClass: 'FUTURE_EVENT',
          statement: 's2',
          evidenceIds: [],
          year: 2031,
        },
      ] as unknown as CausalStep[];
      await openResult(futureResult(chain));
      await click('open-why');
      expect(ids(/^why-evidence-1-E\d+$/)).toEqual(evidenceIds.map((e) => `why-evidence-1-${e}`));
      expect(!!byId('why-evidence-list-1')).toBe(evidenceIds.length > 0);
    },
  );

  // @trace FR-26
  it.each([2, 3, 5, 8])(
    'R3 a chain of %i steps renders one card each in order and n-1 arrows',
    async (n) => {
      const chain = Array.from({ length: n }, (_, i) => ({
        order: i + 1,
        informationClass: i === n - 1 ? 'FUTURE_EVENT' : 'INFERENCE',
        statement: `step ${i + 1}`,
        evidenceIds: [],
        year: 2030,
      })) as unknown as CausalStep[];
      await openResult(futureResult(chain));
      await click('open-why');
      expect(ids(/^why-step-\d+$/)).toEqual(chain.map((s) => `why-step-${s.order}`));
      expect(all(/^why-arrow$/)).toHaveLength(n - 1);
      for (const s of chain) expect(text(`why-step-statement-${s.order}`)).toBe(s.statement);
    },
  );

  // @trace FR-26
  it.each([
    ['E001', true],
    ['E002', true],
    ['E003', true],
    ['E004', true],
    ['E005', false],
    ['E999', false],
  ] as const)('R4 chip %s is enabled iff it is the id of a source (%s)', async (id, enabled) => {
    const chain = [
      { order: 1, informationClass: 'FACT', statement: 's1', evidenceIds: [id] },
      { order: 2, informationClass: 'FUTURE_EVENT', statement: 's2', evidenceIds: [], year: 2031 },
    ] as unknown as CausalStep[];
    await openResult(futureResult(chain));
    await click('open-why');
    expect((byId(`why-evidence-1-${id}`) as HTMLButtonElement).disabled).toBe(!enabled);
  });
});
