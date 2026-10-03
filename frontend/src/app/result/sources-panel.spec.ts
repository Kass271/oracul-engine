// @trace FR-27
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

describe('slice 13_why-and-sources: SOURCES panel', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}`);
  const isResultGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith(`/api/runs/${RUN_ID}/result`);
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const ids = (re: RegExp): string[] =>
    Array.from(el().querySelectorAll<HTMLElement>('[data-testid]'))
      .map((n) => n.getAttribute('data-testid') as string)
      .filter((t) => re.test(t));

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

  const one = (over: Partial<ResultSource>): ResultSource =>
    ({ ...SOURCES[0], ...over }) as ResultSource;

  async function openWith(sources: ResultSource[]): Promise<void> {
    await openResult(futureResult(CHAIN, sources));
    await click('open-sources');
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
  });

  // @trace FR-27
  it('S1 shows the closed SOURCES button and no panel', async () => {
    await openResult();
    expect(text('open-sources')).toBe('SOURCES');
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('false');
    expect(byId('open-sources')!.getAttribute('type')).toBe('button');
    expect(byId('sources-panel')).toBeNull();
    expect(byId('why-panel')).toBeNull();
    expect(byId('result-actions')!.contains(byId('open-sources'))).toBe(true);
    expect(http.match(isResultGet)).toHaveLength(0);
  });

  // @trace FR-27
  it('S2 opens the panel with one item per source in API order', async () => {
    await openResult();
    await click('open-sources');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('true');
    expect(text('sources-title')).toBe('SOURCES');
    expect(ids(/^source-item-E\d+$/)).toEqual([
      'source-item-E001',
      'source-item-E002',
      'source-item-E003',
      'source-item-E004',
    ]);
    expect(byId('why-panel')).toBeNull();
    expect(byId('sources-empty')).toBeNull();
  });

  // @trace FR-27
  it('S3 shows id, title, publisher and date with fallbacks', async () => {
    await openResult();
    await click('open-sources');
    expect(text('source-id-E001')).toBe('E001');
    expect(text('source-title-E001')).toBe('Ports adopt robots');
    expect(text('source-publisher-E001')).toBe('Reuters');
    expect(text('source-date-E001')).toBe('30 Sep 2026');
    expect(text('source-date-E002')).toBe('date unknown');
    expect(text('source-date-E003')).toBe('5 Jan 2026');
    expect(text('source-title-E004')).toBe('Untitled source');
    expect(text('source-publisher-E004')).toBe('Unknown publisher');
    expect(byId('source-item-E001')!.contains(byId('source-title-E001'))).toBe(true);
  });

  // @trace FR-27
  it('S4 links open in a new tab; unsafe urls render no link', async () => {
    await openResult();
    await click('open-sources');
    const a = byId('source-link-E001') as HTMLAnchorElement;
    expect(a.tagName).toBe('A');
    expect(a.getAttribute('href')).toBe('https://example.com/a');
    expect(a.getAttribute('target')).toBe('_blank');
    expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    expect((a.textContent ?? '').trim()).toBe('Open source');
    expect(byId('source-link-E002')!.getAttribute('href')).toBe('http://example.com/b');
    expect(byId('source-link-E004')).toBeNull();
    expect(byId('source-item-E004')!.querySelector('a')).toBeNull();
    expect(text('source-no-link-E004')).toBe('Link unavailable');
    expect(byId('source-no-link-E001')).toBeNull();
  });

  // @trace FR-27
  it('S5 badges follow usedInScenario and counterSignal', async () => {
    await openResult();
    await click('open-sources');
    const flags = (id: string): [boolean, boolean] => [
      !!byId(`source-used-${id}`),
      !!byId(`source-counter-${id}`),
    ];
    expect(flags('E001')).toEqual([true, false]);
    expect(flags('E002')).toEqual([false, false]);
    expect(flags('E003')).toEqual([false, true]);
    expect(flags('E004')).toEqual([true, true]);
    expect(text('source-used-E001')).toBe('used in scenario');
    expect(text('source-counter-E003')).toBe('counter-signal');
  });

  // @trace FR-27
  it('S6 an empty list shows only "No sources"', async () => {
    await openWith([]);
    expect(text('sources-empty')).toBe('No sources');
    expect(ids(/^source-item-/)).toEqual([]);
  });

  // @trace FR-27
  it('S7 title and publisher are rendered as text, never markup', async () => {
    await openWith([one({ title: '<img src=x onerror=alert(1)>', publisher: '<b>Pub</b>' })]);
    expect(el().querySelector('img')).toBeNull();
    expect(text('source-title-E001')).toBe('<img src=x onerror=alert(1)>');
    expect(text('source-publisher-E001')).toBe('<b>Pub</b>');
    expect(byId('source-item-E001')!.querySelector('b')).toBeNull();
  });

  // @trace FR-27
  it('S8 the SOURCES button toggles only its own panel', async () => {
    await openResult();
    await click('open-why');
    await click('open-sources');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('why-panel')).not.toBeNull();
    await click('open-sources');
    expect(byId('sources-panel')).toBeNull();
    expect(byId('why-panel')).not.toBeNull();
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('false');
  });

  // @trace FR-27
  it('S9 no source item is highlighted when opened with the button', async () => {
    await openResult();
    await click('open-sources');
    expect(el().querySelectorAll('[data-highlighted="true"]')).toHaveLength(0);
    expect(el().querySelectorAll('.highlighted')).toHaveLength(0);
  });

  // @trace FR-27
  it.each([0, 1, 4] as const)('R1 %i sources render that many items in API order', async (n) => {
    const list = Array.from({ length: n }, (_, i) => one({ evidenceId: `E00${i + 1}` }));
    await openWith(list);
    expect(ids(/^source-item-E\d+$/)).toEqual(list.map((s) => `source-item-${s.evidenceId}`));
    expect(!!byId('sources-empty')).toBe(n === 0);
  });

  // @trace FR-27
  it.each([
    [false, false],
    [true, false],
    [false, true],
    [true, true],
  ] as const)(
    'R2 usedInScenario=%s counterSignal=%s: each badge present iff its flag is true',
    async (used, counter) => {
      await openWith([one({ usedInScenario: used, counterSignal: counter })]);
      expect(!!byId('source-used-E001')).toBe(used);
      expect(!!byId('source-counter-E001')).toBe(counter);
    },
  );

  // @trace FR-27
  it.each([
    ['2026-09-30T23:30:00Z', '30 Sep 2026'],
    ['2026-01-05T08:00:00Z', '5 Jan 2026'],
    ['2026-02-01T00:00:00Z', '1 Feb 2026'],
    ['2026-03-31T23:59:59Z', '31 Mar 2026'],
    ['2026-04-10T12:00:00Z', '10 Apr 2026'],
    ['2026-05-09T12:00:00Z', '9 May 2026'],
    ['2026-06-15T12:00:00Z', '15 Jun 2026'],
    ['2026-07-04T12:00:00Z', '4 Jul 2026'],
    ['2026-08-20T12:00:00Z', '20 Aug 2026'],
    ['2026-10-02T18:42:31Z', '2 Oct 2026'],
    ['2026-11-30T12:00:00Z', '30 Nov 2026'],
    ['2026-12-31T23:59:59Z', '31 Dec 2026'],
    ['2027-01-01T00:00:00Z', '1 Jan 2027'],
  ] as const)('R3 publishedAt %s is shown as %s (UTC)', async (iso, expected) => {
    await openWith([one({ publishedAt: iso })]);
    expect(text('source-date-E001')).toBe(expected);
  });

  // @trace FR-27
  it('R3b a missing publishedAt shows "date unknown"', async () => {
    await openWith([one({ publishedAt: undefined })]);
    expect(text('source-date-E001')).toBe('date unknown');
  });

  // @trace FR-27
  it.each([
    ['https://example.com/x', true],
    ['http://example.com/x', true],
    ['HTTPS://EXAMPLE.COM/x', true],
    ['javascript:alert(1)', false],
    ['ftp://example.com/x', false],
    ['', false],
  ] as const)('R4 url %j renders a link: %s', async (url, link) => {
    await openWith([one({ url })]);
    expect(!!byId('source-link-E001')).toBe(link);
    expect(!!byId('source-no-link-E001')).toBe(!link);
    if (link) {
      const a = byId('source-link-E001')!;
      expect(a.getAttribute('href')).toBe(url);
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
    } else {
      expect(byId('source-item-E001')!.querySelector('a')).toBeNull();
      expect(text('source-no-link-E001')).toBe('Link unavailable');
    }
  });

  // @trace FR-27
  it.each([
    ['', '', 'Untitled source', 'Unknown publisher'],
    ['T', '', 'T', 'Unknown publisher'],
    ['', 'P', 'Untitled source', 'P'],
    ['T', 'P', 'T', 'P'],
  ] as const)(
    'R5 title %j / publisher %j shows %j / %j',
    async (title, publisher, expTitle, expPublisher) => {
      await openWith([one({ title, publisher })]);
      expect(text('source-title-E001')).toBe(expTitle);
      expect(text('source-publisher-E001')).toBe(expPublisher);
    },
  );
});
