// @trace FR-60
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { CausalStep } from '../api/models/causal-step';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import type { PipelineQuery } from '../api/models/pipeline-query';
import type { ResearchCounts } from '../api/models/research-counts';
import type { ResultGroupSource } from '../api/models/result-group-source';
import type { ResultSource } from '../api/models/result-source';
import type { ResultWildcardGroup } from '../api/models/result-wildcard-group';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const LABELS = ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'];
const COUNTS: ResearchCounts = {
  searches: 6,
  articlesRetrieved: 20,
  articlesConsidered: 12,
  uniqueEvents: 0,
  eventsSelected: 7,
  counterSignals: 0,
  sourcesUsed: 1,
  sourcesKept: 7,
  sourcesWithContent: 5,
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
const XSS = '<img src=x onerror=alert(1)>';
const CHAIN = [
  {
    order: 1,
    informationClass: 'FACT',
    claimId: 'F1',
    statement: 'Ports adopt robots.',
    evidenceIds: ['E001'],
  },
  {
    order: 2,
    informationClass: 'FUTURE_EVENT',
    statement: 'Robots run the ports.',
    evidenceIds: [],
    year: 2031,
  },
] as CausalStep[];

const src = (e: string): ResultGroupSource => ({
  evidenceId: e,
  sourceId: `S${e.slice(1)}`,
  title: `Title ${e}`,
  publisher: 'Reuters',
  publishedAt: '2026-09-30T23:59:59Z',
  url: `https://a.example/${e}`,
  contentRetrieved: true,
  fragments: [`Fragment of ${e}`],
  usedInScenario: false,
});

const many = (from: number, n: number): ResultGroupSource[] =>
  Array.from({ length: n }, (_, i) => src(`E${String(from + i).padStart(3, '0')}`));

const query = (id: string, over: Partial<PipelineQuery> = {}): PipelineQuery =>
  ({ id, text: `Query text ${id}`, status: 'OK', articlesReturned: 2, ...over }) as PipelineQuery;

const queryId = (n: number, i: number): string => `Q${String(3 * (n - 1) + i).padStart(2, '0')}`;

const grp = (
  n: number,
  sources: ResultGroupSource[],
  over: Partial<ResultWildcardGroup> = {},
): ResultWildcardGroup =>
  ({
    pipelineId: `W0${n}`,
    kind: 'CATALOGUE',
    label: `Wildcard ${n}`,
    level: 5,
    heading: `Wildcard ${n} 5/10`,
    queries: [1, 2, 3].map((i) => query(queryId(n, i))),
    sources,
    ...over,
  }) as ResultWildcardGroup;

function flat(groups: ResultWildcardGroup[]): ResultSource[] {
  const byId = new Map<string, ResultSource>();
  for (const g of groups) {
    for (const s of g.sources) {
      byId.set(s.evidenceId, {
        evidenceId: s.evidenceId,
        section: 'CORE',
        title: s.title,
        publisher: s.publisher,
        url: s.url,
        usedInScenario: s.usedInScenario,
        counterSignal: false,
      } as ResultSource);
    }
  }
  return [...byId.values()];
}

const LEGACY_INTENTS = [
  { id: 'I1', bucket: 'BASELINE', description: 'Baseline intent', drivenBy: ['New pandemic'] },
];

function futureResult(
  groups: ResultWildcardGroup[] | undefined,
  counts: ResearchCounts = COUNTS,
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
    metadata: { configuration: CONFIG, horizonLabel: '5 years', wildcards: [], counts },
    causalChain: CHAIN,
    sources: groups === undefined ? [] : flat(groups),
    research: { intents: groups === undefined ? LEGACY_INTENTS : [], counts },
    openCriticIssues: [],
    ...(groups === undefined ? {} : { wildcardGroups: groups }),
  } as unknown as FutureResult;
}

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

const plural = (n: number, one: string, many_: string): string => `${n} ${n === 1 ? one : many_}`;

describe('FR-60: WHY THESE NEWS? grouped by wildcard', () => {
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

  async function openResult(result: FutureResult): Promise<void> {
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

  async function openWhyNews(
    groups: ResultWildcardGroup[] | undefined,
    counts: ResearchCounts = COUNTS,
  ): Promise<void> {
    await openResult(futureResult(groups, counts));
    await click('open-why-news');
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

  // ---- (a) layout switch ----

  it('A1 results without wildcardGroups keep the phase-01 intents view and its six summary lines', async () => {
    await openWhyNews(undefined);
    expect(byId('why-news-panel')).not.toBeNull();
    expect(text('why-news-title')).toBe('WHY THESE NEWS?');
    expect(byId('why-news-intent-I1')).not.toBeNull();
    expect(text('summary-events')).toBe('0 unique events identified');
    expect(byId('summary-selected')).not.toBeNull();
    expect(byId('summary-counter-signals')).not.toBeNull();
    expect(byId('summary-sources-used')).not.toBeNull();
    expect(ids(/^why-news-group/)).toEqual([]);
    expect(ids(/^why-news-query/)).toEqual([]);
    expect(byId('summary-kept')).toBeNull();
    expect(byId('summary-content')).toBeNull();
    expect(byId('summary-used')).toBeNull();
  });

  it('A2 results with wildcardGroups show the grouped layout only', async () => {
    await openWhyNews([grp(1, many(1, 2)), grp(2, [])]);
    expect(byId('why-news-panel')).not.toBeNull();
    expect(text('why-news-title')).toBe('WHY THESE NEWS?');
    expect(ids(/^why-news-group-W\d+$/)).toEqual(['why-news-group-W01', 'why-news-group-W02']);
    for (const legacy of [
      'why-news-empty',
      'summary-events',
      'summary-selected',
      'summary-counter-signals',
      'summary-sources-used',
    ]) {
      expect(byId(legacy), legacy).toBeNull();
    }
    expect(ids(/^why-news-(intent|description|drivers|driver)-/)).toEqual([]);
    expect(text('research-summary-title')).toBe('Research summary');
  });

  it('A3 the panel starts closed, is independent of SOURCES and a mat-card', async () => {
    await openResult(futureResult([grp(1, many(1, 1))]));
    expect(byId('why-news-panel')).toBeNull();
    expect(byId('open-why-news')!.getAttribute('aria-expanded')).toBe('false');
    await click('open-why-news');
    expect(byId('why-news-panel')!.tagName.toLowerCase()).toBe('mat-card');
    expect(byId('sources-panel')).toBeNull();
    await click('open-sources');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('why-news-panel')).not.toBeNull();
    await click('open-why-news');
    expect(byId('why-news-panel')).toBeNull();
    expect(byId('sources-panel')).not.toBeNull();
  });

  // ---- (b) groups 1 / 2 / 3 in API order ----

  it.each([
    [['New pandemic 8/10']],
    [['New pandemic 8/10', 'Energy crisis 3/10']],
    [['New pandemic 8/10', 'Ocean desalination boom 7/10', 'General']],
  ] as const)(
    'B1 headings %j: one group each in API order with the heading verbatim',
    async (headings) => {
      const groups = headings.map((h, i) => grp(i + 1, many(10 * (i + 1) + 1, 1), { heading: h }));
      await openWhyNews(groups);
      expect(ids(/^why-news-group-W\d+$/)).toEqual(
        groups.map((g) => `why-news-group-${g.pipelineId}`),
      );
      expect(ids(/^why-news-group-title-W\d+$/)).toEqual(
        groups.map((g) => `why-news-group-title-${g.pipelineId}`),
      );
      for (const g of groups) {
        expect(text(`why-news-group-title-${g.pipelineId}`)).toBe(g.heading);
        expect(
          byId(`why-news-group-${g.pipelineId}`)!.contains(
            byId(`why-news-group-title-${g.pipelineId}`),
          ),
        ).toBe(true);
      }
      // all queries in API order over the whole panel
      expect(ids(/^why-news-query-Q\d+$/)).toEqual(
        groups.flatMap((g) => g.queries.map((q) => `why-news-query-${q.id}`)),
      );
    },
  );

  // ---- queries ----

  it('Q1 per query: text, status chip and item count inside its row, in API order, then the group sources', async () => {
    await openWhyNews([
      grp(1, many(1, 2), {
        queries: [
          query('Q01', { text: 'first query words', status: 'OK', articlesReturned: 5 }),
          query('Q02', { text: 'second query words', status: 'EMPTY', articlesReturned: 0 }),
          query('Q03', { text: 'third query words', status: 'FAILED', articlesReturned: 0 }),
        ],
      }),
    ]);
    expect(text('why-news-query-text-Q01')).toBe('first query words');
    expect(text('why-news-query-text-Q02')).toBe('second query words');
    expect(text('why-news-query-status-Q01')).toBe('OK');
    expect(text('why-news-query-status-Q02')).toBe('EMPTY');
    expect(text('why-news-query-status-Q03')).toBe('FAILED');
    expect(byId('why-news-query-status-Q01')!.closest('mat-chip')).not.toBeNull();
    expect(text('why-news-query-count-Q01')).toBe('5 items');
    expect(text('why-news-query-count-Q02')).toBe('0 items');
    for (const q of ['Q01', 'Q02', 'Q03']) {
      const row = byId(`why-news-query-${q}`)!;
      for (const part of ['text', 'status', 'count']) {
        expect(row.contains(byId(`why-news-query-${part}-${q}`)), `${part}-${q}`).toBe(true);
      }
    }
    expect(text('why-news-group-sources-W01')).toBe('2 sources');
    // the group's sources line follows its queries
    const group = byId('why-news-group-W01')!;
    const order = Array.from(group.querySelectorAll<HTMLElement>('[data-testid]')).map((n) =>
      n.getAttribute('data-testid'),
    );
    expect(order.indexOf('why-news-group-sources-W01')).toBeGreaterThan(
      order.indexOf('why-news-query-Q03'),
    );
  });

  // ---- (g) status classes, count classes ----

  it.each(['OK', 'EMPTY', 'FAILED', 'PENDING'] as const)(
    'G1 query status %s is shown verbatim',
    async (status) => {
      await openWhyNews([grp(1, many(1, 1), { queries: [query('Q01', { status })] })]);
      expect(text('why-news-query-status-Q01')).toBe(status);
      expect(byId('why-news-query-status-Q01')!.closest('mat-chip')).not.toBeNull();
    },
  );

  it.each([
    [0, '0 items'],
    [1, '1 item'],
    [2, '2 items'],
    [1234, '1234 items'],
  ] as const)('G2 articlesReturned %i reads %s', async (n, expected) => {
    await openWhyNews([grp(1, many(1, 1), { queries: [query('Q01', { articlesReturned: n })] })]);
    expect(text('why-news-query-count-Q01')).toBe(expected);
  });

  it.each([
    [1, '1 source'],
    [2, '2 sources'],
    [5, '5 sources'],
  ] as const)('G3 a group with %i sources reads %s and no empty line', async (n, expected) => {
    await openWhyNews([grp(1, many(1, n))]);
    expect(text('why-news-group-sources-W01')).toBe(expected);
    expect(byId('why-news-group-empty-W01')).toBeNull();
  });

  // ---- (c) empty groups ----

  it('C1 an empty group says "no current sources found" instead of a source count', async () => {
    await openWhyNews([grp(1, many(1, 2)), grp(2, [])]);
    expect(text('why-news-group-sources-W01')).toBe('2 sources');
    expect(byId('why-news-group-empty-W01')).toBeNull();
    expect(text('why-news-group-empty-W02')).toBe('no current sources found');
    expect(byId('why-news-group-sources-W02')).toBeNull();
    expect(ids(/^why-news-query-Q\d+$/)).toHaveLength(6);
  });

  it.each([[1], [2], [3]] as const)(
    'C2 %i groups all without sources: every group and every query is still listed',
    async (n) => {
      const groups = Array.from({ length: n }, (_, i) =>
        grp(i + 1, [], {
          queries: [1, 2, 3].map((k) =>
            query(queryId(i + 1, k), { status: 'EMPTY', articlesReturned: 0 }),
          ),
        }),
      );
      await openWhyNews(groups, {
        ...COUNTS,
        sourcesKept: 0,
        sourcesWithContent: 0,
        sourcesUsed: 0,
      });
      expect(ids(/^why-news-group-W\d+$/)).toEqual(
        groups.map((g) => `why-news-group-${g.pipelineId}`),
      );
      expect(ids(/^why-news-query-Q\d+$/)).toHaveLength(3 * n);
      for (const g of groups) {
        expect(text(`why-news-group-empty-${g.pipelineId}`)).toBe('no current sources found');
        expect(byId(`why-news-group-sources-${g.pipelineId}`)).toBeNull();
      }
      for (const id of ids(/^why-news-query-status-Q\d+$/)) expect(text(id)).toBe('EMPTY');
      for (const id of ids(/^why-news-query-count-Q\d+$/)) expect(text(id)).toBe('0 items');
    },
  );

  // ---- the five summary lines ----

  it('S1 the research summary has exactly five lines in this order with the texts of the counts', async () => {
    await openWhyNews([grp(1, many(1, 1))], {
      ...COUNTS,
      searches: 6,
      articlesConsidered: 12,
      sourcesKept: 7,
      sourcesWithContent: 5,
      sourcesUsed: 2,
    });
    const box = byId('research-summary')!;
    expect(text('research-summary-title')).toBe('Research summary');
    const lines = Array.from(box.querySelectorAll<HTMLElement>('[data-testid^="summary-"]')).map(
      (n) => n.getAttribute('data-testid'),
    );
    expect(lines).toEqual([
      'summary-searches',
      'summary-articles',
      'summary-kept',
      'summary-content',
      'summary-used',
    ]);
    expect(text('summary-searches')).toBe('6 searches performed');
    expect(text('summary-articles')).toBe('12 articles considered');
    expect(text('summary-kept')).toBe('7 sources kept');
    expect(text('summary-content')).toBe('5 sources with content');
    expect(text('summary-used')).toBe('2 sources used in the scenario');
  });

  const FIELDS = [
    ['summary-searches', 'searches', 'search performed', 'searches performed'],
    ['summary-articles', 'articlesConsidered', 'article considered', 'articles considered'],
    ['summary-kept', 'sourcesKept', 'source kept', 'sources kept'],
    ['summary-content', 'sourcesWithContent', 'source with content', 'sources with content'],
    ['summary-used', 'sourcesUsed', 'source used in the scenario', 'sources used in the scenario'],
  ] as const;
  const CASES = FIELDS.flatMap(([testid, field, one, many_]) =>
    [0, 1, 2, 1234].map((n) => ({ testid, field, one, many_, n })),
  );

  it.each(CASES)(
    'S2 $testid: $field = $n is singular iff 1 (plain integer)',
    async ({ testid, field, one, many_, n }) => {
      await openWhyNews([grp(1, many(1, 1))], { ...COUNTS, [field]: n });
      expect(text(testid)).toBe(plural(n, one, many_));
    },
  );

  it.each([
    ['summary-kept', '0 sources kept'],
    ['summary-content', '0 sources with content'],
  ] as const)(
    'S3 an absent sourcesKept / sourcesWithContent reads 0: %s',
    async (testid, expected) => {
      const counts = { ...COUNTS } as Partial<ResearchCounts>;
      delete counts.sourcesKept;
      delete counts.sourcesWithContent;
      await openWhyNews([grp(1, many(1, 1))], counts as ResearchCounts);
      expect(text(testid)).toBe(expected);
    },
  );

  // ---- (i) literal text ----

  it('I1 query texts, headings and custom labels are rendered as text, never as markup', async () => {
    await openWhyNews([
      grp(1, many(1, 1), {
        kind: 'CUSTOM',
        label: XSS,
        heading: `${XSS} 7/10`,
        queries: [query('Q01', { text: XSS }), query('Q02', { text: '<b>bold</b> words' })],
      }),
    ]);
    expect(el().querySelector('img')).toBeNull();
    expect(el().querySelector('b')).toBeNull();
    expect(text('why-news-group-title-W01')).toBe(`${XSS} 7/10`);
    expect(text('why-news-query-text-Q01')).toBe(XSS);
    expect(text('why-news-query-text-Q02')).toBe('<b>bold</b> words');
  });

  // ---- (j) HTTP ----

  it('J1 the panel needs no further request: one getFutureResult, no /research, /sources, /events, /evidence-pack', async () => {
    await openWhyNews([grp(1, many(1, 2)), grp(2, [])]);
    await click('open-sources');
    expect(http.match(isResultGet)).toHaveLength(0);
    expect(
      http.match((r) => /\/(research|sources|events|evidence-pack)(\?|$)/.test(r.url)),
    ).toHaveLength(0);
  });
});
