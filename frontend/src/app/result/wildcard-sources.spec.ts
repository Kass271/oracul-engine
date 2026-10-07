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
import type { ResultGroupSource } from '../api/models/result-group-source';
import type { ResultSource } from '../api/models/result-source';
import type { ResultWildcardGroup } from '../api/models/result-wildcard-group';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const LABELS = ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'];
const COUNTS = {
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

/** FACT step 1 cites E001 (in two groups of the shared fixture) and E009 (in no group); step 2 cites E002. */
const CHAIN: CausalStep[] = [
  {
    order: 1,
    informationClass: 'FACT',
    claimId: 'F1',
    statement: 'Ports adopt robots.',
    evidenceIds: ['E001', 'E009'],
  },
  {
    order: 2,
    informationClass: 'FACT',
    claimId: 'F2',
    statement: 'Labour demand shifts.',
    evidenceIds: ['E002'],
  },
  {
    order: 3,
    informationClass: 'FUTURE_EVENT',
    statement: 'Robots run the ports.',
    evidenceIds: [],
    year: 2031,
  },
] as CausalStep[];

const src = (e: string, over: Partial<ResultGroupSource> = {}): ResultGroupSource => ({
  evidenceId: e,
  sourceId: `S${e.slice(1)}`,
  title: `Title ${e}`,
  publisher: 'Reuters',
  publishedAt: '2026-09-30T23:59:59Z',
  url: `https://a.example/${e}`,
  contentRetrieved: true,
  fragments: [`Fragment of ${e}`],
  usedInScenario: false,
  ...over,
});

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
    queries: [1, 2, 3].map((i) => ({
      id: queryId(n, i),
      text: `W0${n} stub query ${i}`,
      status: 'OK',
      articlesReturned: 2,
    })),
    sources,
    ...over,
  }) as ResultWildcardGroup;

/** Flat `sources` of the result: one entry per distinct evidenceId over all groups, in id order, section CORE, no counter-signal. */
function flat(groups: ResultWildcardGroup[]): ResultSource[] {
  const byId = new Map<string, ResultSource>();
  for (const g of groups) {
    for (const s of g.sources) {
      const prev = byId.get(s.evidenceId);
      byId.set(s.evidenceId, {
        evidenceId: s.evidenceId,
        section: 'CORE',
        title: s.title,
        publisher: s.publisher,
        url: s.url,
        usedInScenario: (prev?.usedInScenario ?? false) || s.usedInScenario,
        counterSignal: false,
      } as ResultSource);
    }
  }
  return [...byId.values()].sort((a, b) => a.evidenceId.localeCompare(b.evidenceId));
}

const LEGACY_SOURCES = [
  {
    evidenceId: 'E001',
    section: 'CORE',
    title: 'Legacy A',
    publisher: 'Reuters',
    url: 'https://example.com/a',
    usedInScenario: true,
    counterSignal: false,
  },
  {
    evidenceId: 'E003',
    section: 'COUNTER_SIGNAL',
    title: 'Legacy C',
    publisher: 'BBC',
    url: 'https://example.com/c',
    usedInScenario: false,
    counterSignal: true,
  },
] as ResultSource[];

function futureResult(groups: ResultWildcardGroup[] | undefined, counts = COUNTS): FutureResult {
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
    sources: groups === undefined ? LEGACY_SOURCES : flat(groups),
    research: { intents: [], counts },
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

/** n sources E<from>… with their group number encoded nowhere: ids are unique over the whole fixture. */
const many = (from: number, n: number): ResultGroupSource[] =>
  Array.from({ length: n }, (_, i) => src(`E${String(from + i).padStart(3, '0')}`));

describe('FR-60: SOURCES grouped by wildcard', () => {
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
  const highlighted = (): string[] =>
    Array.from(el().querySelectorAll<HTMLElement>('[data-highlighted="true"]')).map(
      (n) => n.getAttribute('data-testid') as string,
    );

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

  async function openSources(groups: ResultWildcardGroup[] | undefined): Promise<void> {
    await openResult(futureResult(groups));
    await click('open-sources');
  }

  /** One group W01 with one source E001 modified by {@code over}, SOURCES opened. */
  async function openOne(over: Partial<ResultGroupSource>): Promise<void> {
    await openSources([grp(1, [src('E001', over)])]);
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

  // ---- (a) the layout switch ----

  it('A1 results without wildcardGroups keep the phase-01 flat list and no grouped element', async () => {
    await openSources(undefined);
    expect(ids(/^source-item-E\d+$/)).toEqual(['source-item-E001', 'source-item-E003']);
    expect(byId('source-counter-E003')).not.toBeNull();
    expect(text('source-counter-E003')).toBe('counter-signal');
    expect(ids(/^source-(group|fragment|excerpt|not-retrieved)/)).toEqual([]);
    expect(ids(/^source-item-W\d+-E\d+$/)).toEqual([]);
    expect(byId('source-group-W01')).toBeNull();
  });

  it('A2 results with wildcardGroups show the grouped testids only', async () => {
    await openSources([grp(1, [src('E001', { usedInScenario: true })]), grp(2, [])]);
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('source-group-W01')).not.toBeNull();
    expect(byId('source-item-W01-E001')).not.toBeNull();
    expect(ids(/^source-item-E\d+$/)).toEqual([]);
    expect(ids(/^source-counter-/)).toEqual([]);
    expect(byId('sources-empty')).toBeNull();
  });

  it('A3 the SOURCES panel starts closed, is a mat-card with the title SOURCES and toggles on its own', async () => {
    await openResult(futureResult([grp(1, many(1, 1))]));
    expect(byId('sources-panel')).toBeNull();
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('false');
    await click('open-sources');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('sources-panel')!.tagName.toLowerCase()).toBe('mat-card');
    expect(text('sources-title')).toBe('SOURCES');
    expect(byId('why-news-panel')).toBeNull();
    expect(byId('why-panel')).toBeNull();
    await click('open-sources');
    expect(byId('sources-panel')).toBeNull();
  });

  // ---- (b) groups 1, 2, 3 in API order, headings verbatim ----

  it.each([
    [['New pandemic 8/10']],
    [['New pandemic 8/10', 'Energy crisis 3/10']],
    [['New pandemic 8/10', 'Ocean desalination boom 7/10', 'General']],
    [['Zeta 1/10', 'Alpha 10/10', 'Mid 5/10']],
  ] as const)('B1 headings %j render one group each in API order', async (headings) => {
    const groups = headings.map((h, i) =>
      grp(i + 1, many(10 * (i + 1) + 1, 1), {
        heading: h,
        label: h.replace(/ \d+\/10$/, ''),
        ...(h === 'General' ? { kind: 'GENERAL', level: undefined } : {}),
      } as Partial<ResultWildcardGroup>),
    );
    await openSources(groups);
    expect(ids(/^source-group-W\d+$/)).toEqual(groups.map((g) => `source-group-${g.pipelineId}`));
    expect(ids(/^source-group-title-W\d+$/)).toEqual(
      groups.map((g) => `source-group-title-${g.pipelineId}`),
    );
    for (const g of groups) {
      expect(text(`source-group-title-${g.pipelineId}`)).toBe(g.heading);
      expect(
        byId(`source-group-${g.pipelineId}`)!.contains(byId(`source-group-title-${g.pipelineId}`)),
      ).toBe(true);
    }
    // the title comes first in its group
    const first = byId(`source-group-${groups[0].pipelineId}`)!.firstElementChild as HTMLElement;
    expect(first.getAttribute('data-testid')).toBe(`source-group-title-${groups[0].pipelineId}`);
  });

  // ---- (c) sources per group 0 / 1 / 5, and all groups empty ----

  it.each([
    [[0, 1]],
    [[1, 0]],
    [[0, 5]],
    [[5, 0]],
    [[1, 5]],
    [[5, 1]],
    [[0, 1, 0]],
    [[5, 0, 1]],
  ] as const)(
    'C1 group sizes %j list each group with its items or "no current sources found"',
    async (sizes) => {
      let next = 1;
      const groups = sizes.map((n, i) => {
        const list = many(next, n);
        next += n;
        return grp(i + 1, list);
      });
      await openSources(groups);
      expect(byId('sources-empty')).toBeNull();
      expect(ids(/^source-group-W\d+$/)).toEqual(groups.map((g) => `source-group-${g.pipelineId}`));
      for (const g of groups) {
        const p = g.pipelineId;
        if (g.sources.length === 0) {
          expect(text(`source-group-empty-${p}`)).toBe('no current sources found');
          expect(
            byId(`source-group-${p}`)!.querySelector('[data-testid^="source-item-"]'),
          ).toBeNull();
        } else {
          expect(byId(`source-group-empty-${p}`)).toBeNull();
          const own = Array.from(
            byId(`source-group-${p}`)!.querySelectorAll<HTMLElement>(
              '[data-testid^="source-item-"]',
            ),
          ).map((n) => n.getAttribute('data-testid'));
          expect(own).toEqual(g.sources.map((s) => `source-item-${p}-${s.evidenceId}`));
        }
      }
    },
  );

  it.each([[1], [2], [3]] as const)(
    'C2 %i groups, all without sources: only "No sources" and no group element',
    async (n) => {
      await openSources(Array.from({ length: n }, (_, i) => grp(i + 1, [])));
      expect(text('sources-empty')).toBe('No sources');
      expect(ids(/^source-group/)).toEqual([]);
      expect(ids(/^source-item-/)).toEqual([]);
    },
  );

  // ---- per source: id, title, publisher, date, link, used, excerpt ----

  it('D1 a source shows id, title, publisher, date, link, badge and excerpt inside its item', async () => {
    await openSources([
      grp(1, [
        src('E001', { usedInScenario: true, fragments: ['First passage', 'Second passage'] }),
      ]),
    ]);
    const item = byId('source-item-W01-E001')!;
    expect(text('source-id-W01-E001')).toBe('E001');
    expect(text('source-title-W01-E001')).toBe('Title E001');
    expect(text('source-publisher-W01-E001')).toBe('Reuters');
    expect(text('source-date-W01-E001')).toBe('30 Sep 2026');
    expect(text('source-used-W01-E001')).toBe('used in scenario');
    expect(text('source-fragment-W01-E001-0')).toBe('First passage');
    expect(text('source-fragment-W01-E001-1')).toBe('Second passage');
    for (const id of [
      'source-id-W01-E001',
      'source-title-W01-E001',
      'source-publisher-W01-E001',
      'source-date-W01-E001',
      'source-link-W01-E001',
      'source-used-W01-E001',
      'source-excerpt-W01-E001',
      'source-fragment-W01-E001-0',
    ]) {
      expect(item.contains(byId(id)), id).toBe(true);
    }
    expect(byId('source-excerpt-W01-E001')!.contains(byId('source-fragment-W01-E001-1'))).toBe(
      true,
    );
  });

  // ---- (d) fragments 0 / 1 / 3 and the four contentRetrieved x usedInScenario classes ----

  it.each([0, 1, 3] as const)(
    'D2 %i fragments: excerpt and not-retrieved follow contentRetrieved',
    async (n) => {
      const fragments = Array.from({ length: n }, (_, k) => `Passage number ${k}`);
      await openOne({ contentRetrieved: n > 0, fragments });
      if (n === 0) {
        expect(text('source-not-retrieved-W01-E001')).toBe('content not retrieved');
        expect(byId('source-excerpt-W01-E001')).toBeNull();
        expect(ids(/^source-fragment-/)).toEqual([]);
      } else {
        expect(byId('source-not-retrieved-W01-E001')).toBeNull();
        expect(byId('source-excerpt-W01-E001')).not.toBeNull();
        expect(ids(/^source-fragment-/)).toEqual(
          fragments.map((_, k) => `source-fragment-W01-E001-${k}`),
        );
        fragments.forEach((f, k) => expect(text(`source-fragment-W01-E001-${k}`)).toBe(f));
      }
    },
  );

  it.each([
    [false, false],
    [true, false],
    [false, true],
    [true, true],
  ] as const)(
    'D3 contentRetrieved=%s usedInScenario=%s: excerpt / not-retrieved / badge present iff their flag',
    async (retrieved, used) => {
      await openOne({
        contentRetrieved: retrieved,
        usedInScenario: used,
        fragments: retrieved ? ['A passage'] : [],
      });
      expect(!!byId('source-excerpt-W01-E001')).toBe(retrieved);
      expect(!!byId('source-not-retrieved-W01-E001')).toBe(!retrieved);
      expect(!!byId('source-used-W01-E001')).toBe(used);
      expect(ids(/^source-counter-/)).toEqual([]);
    },
  );

  // ---- (e) url classes ----

  it.each([
    ['https://a.example/x', true],
    ['HTTP://A.EXAMPLE/x', true],
    ['http://a.example/x', true],
    ['javascript:alert(1)', false],
    ['ftp://a/b', false],
    ['', false],
    ['/relative', false],
  ] as const)('E1 url %j renders a link: %s', async (url, link) => {
    await openOne({ url });
    expect(!!byId('source-link-W01-E001')).toBe(link);
    expect(!!byId('source-no-link-W01-E001')).toBe(!link);
    if (link) {
      const a = byId('source-link-W01-E001') as HTMLAnchorElement;
      expect(a.tagName).toBe('A');
      expect(a.getAttribute('href')).toBe(url);
      expect(a.getAttribute('target')).toBe('_blank');
      expect(a.getAttribute('rel')).toBe('noopener noreferrer');
      expect((a.textContent ?? '').trim()).toBe('Open source');
    } else {
      expect(text('source-no-link-W01-E001')).toBe('Link unavailable');
      expect(byId('source-item-W01-E001')!.querySelector('a')).toBeNull();
    }
  });

  // ---- (f) date classes, empty title / publisher ----

  it.each([
    ['2026-09-30T23:59:59Z', '30 Sep 2026'],
    ['2026-01-01T00:00:00+02:00', '31 Dec 2025'],
    ['2026-10-05T08:00:00Z', '5 Oct 2026'],
    ['2027-01-01T00:00:00Z', '1 Jan 2027'],
    [undefined, 'date unknown'],
    ['not-a-date', 'date unknown'],
  ] as const)('F1 publishedAt %j is shown as %s (UTC)', async (iso, expected) => {
    await openOne({ publishedAt: iso });
    expect(text('source-date-W01-E001')).toBe(expected);
  });

  it.each([
    ['', '', 'Untitled source', 'Unknown publisher'],
    ['T', '', 'T', 'Unknown publisher'],
    ['', 'P', 'Untitled source', 'P'],
    ['T', 'P', 'T', 'P'],
  ] as const)('F2 title %j / publisher %j shows %j / %j', async (title, publisher, t, p) => {
    await openOne({ title, publisher });
    expect(text('source-title-W01-E001')).toBe(t);
    expect(text('source-publisher-W01-E001')).toBe(p);
  });

  // ---- (i) literal text ----

  it('I1 titles, publishers, fragments and headings are rendered as text, never as markup', async () => {
    await openSources([
      grp(1, [src('E001', { title: XSS, publisher: '<b>Pub</b>', fragments: [XSS, '<i>x</i>'] })], {
        heading: XSS,
        label: XSS,
      }),
    ]);
    expect(el().querySelector('img')).toBeNull();
    expect(el().querySelector('b')).toBeNull();
    expect(el().querySelector('i')).toBeNull();
    expect(text('source-group-title-W01')).toBe(XSS);
    expect(text('source-title-W01-E001')).toBe(XSS);
    expect(text('source-publisher-W01-E001')).toBe('<b>Pub</b>');
    expect(text('source-fragment-W01-E001-0')).toBe(XSS);
    expect(text('source-fragment-W01-E001-1')).toBe('<i>x</i>');
  });

  // ---- (h) a source in two groups, highlight ----

  /** E001 is in both groups (with each group's own fragments), E002 only in W02. */
  const shared = (): ResultWildcardGroup[] => [
    grp(1, [src('E001', { fragments: ['W01 passage'], usedInScenario: true })]),
    grp(2, [src('E001', { fragments: ['W02 passage'], usedInScenario: true }), src('E002')]),
  ];

  it('H1 a source listed in two groups is rendered in both with its own group fragments', async () => {
    await openSources(shared());
    expect(ids(/^source-item-W\d+-E\d+$/)).toEqual([
      'source-item-W01-E001',
      'source-item-W02-E001',
      'source-item-W02-E002',
    ]);
    expect(text('source-id-W01-E001')).toBe('E001');
    expect(text('source-id-W02-E001')).toBe('E001');
    expect(text('source-fragment-W01-E001-0')).toBe('W01 passage');
    expect(text('source-fragment-W02-E001-0')).toBe('W02 passage');
  });

  it('H2 a chip click opens SOURCES and highlights every item of that id (class and data-highlighted), others none', async () => {
    await openResult(futureResult(shared()));
    await click('open-why');
    expect(byId('sources-panel')).toBeNull();
    await click('why-evidence-1-E001');
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('open-sources')!.getAttribute('aria-expanded')).toBe('true');
    expect(highlighted()).toEqual(['source-item-W01-E001', 'source-item-W02-E001']);
    for (const id of ['source-item-W01-E001', 'source-item-W02-E001']) {
      expect(byId(id)!.classList.contains('highlighted'), id).toBe(true);
    }
    const other = byId('source-item-W02-E002')!;
    expect(other.classList.contains('highlighted')).toBe(false);
    expect(other.hasAttribute('data-highlighted')).toBe(false);
    expect(el().querySelectorAll('.highlighted')).toHaveLength(2);
  });

  it('H3 the first item of the id (DOM order) is scrolled into view', async () => {
    const spy = vi.fn();
    const original = HTMLElement.prototype.scrollIntoView;
    HTMLElement.prototype.scrollIntoView = spy;
    try {
      await openResult(futureResult(shared()));
      await click('open-why');
      await click('why-evidence-1-E001');
      await vi.waitFor(() => expect(spy).toHaveBeenCalled());
      expect(spy.mock.contexts[0]).toBe(byId('source-item-W01-E001'));
    } finally {
      HTMLElement.prototype.scrollIntoView = original;
    }
  });

  it('H4 the highlight lasts exactly 3000 ms', async () => {
    await openResult(futureResult(shared()));
    await click('open-why');
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    await click('why-evidence-1-E001');
    expect(highlighted()).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(2999);
    harness.detectChanges();
    expect(highlighted()).toHaveLength(2);
    await vi.advanceTimersByTimeAsync(1);
    harness.detectChanges();
    expect(highlighted()).toEqual([]);
    expect(el().querySelectorAll('.highlighted')).toHaveLength(0);
    expect(byId('sources-panel')).not.toBeNull();
  });

  it('H5 a second chip click within 3000 ms moves the highlight and restarts the timer', async () => {
    await openResult(futureResult(shared()));
    await click('open-why');
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'setInterval', 'clearInterval'] });
    await click('why-evidence-1-E001');
    await vi.advanceTimersByTimeAsync(2000);
    await click('why-evidence-2-E002');
    expect(highlighted()).toEqual(['source-item-W02-E002']);
    await vi.advanceTimersByTimeAsync(2999);
    harness.detectChanges();
    expect(highlighted()).toEqual(['source-item-W02-E002']);
    await vi.advanceTimersByTimeAsync(1);
    harness.detectChanges();
    expect(highlighted()).toEqual([]);
  });

  it('H6 a chip whose id is not among the sources is disabled and highlights nothing', async () => {
    await openResult(futureResult(shared()));
    await click('open-why');
    const chip = byId('why-evidence-1-E009') as HTMLButtonElement;
    expect(chip.disabled).toBe(true);
    chip.click();
    await settle();
    expect(byId('sources-panel')).toBeNull();
    expect(highlighted()).toEqual([]);
    expect((byId('why-evidence-1-E001') as HTMLButtonElement).disabled).toBe(false);
  });

  it('H7 opening SOURCES with its button highlights nothing', async () => {
    await openSources(shared());
    expect(highlighted()).toEqual([]);
    expect(el().querySelectorAll('.highlighted')).toHaveLength(0);
  });

  // ---- (j) HTTP ----

  it('J1 the result view calls getFutureResult once and never /research, /sources, /events or /evidence-pack', async () => {
    await openResult(futureResult(shared()));
    await click('open-sources');
    await click('open-why-news');
    await click('open-why');
    await click('why-evidence-1-E001');
    expect(http.match(isResultGet)).toHaveLength(0);
    expect(
      http.match((r) => /\/(research|sources|events|evidence-pack)(\?|$)/.test(r.url)),
    ).toHaveLength(0);
    expect(http.match(isRunGet)).toHaveLength(0);
  });
});
