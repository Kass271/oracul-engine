// @trace FR-28
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { CausalStep } from '../api/models/causal-step';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const LABELS = ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'];
const COUNTS = {
  searches: 5,
  articlesRetrieved: 31,
  articlesConsidered: 12,
  uniqueEvents: 7,
  eventsSelected: 2,
  counterSignals: 1,
  sourcesUsed: 2,
};
const C14 = {
  searches: 20,
  articlesRetrieved: 100,
  articlesConsidered: 81,
  uniqueEvents: 12,
  eventsSelected: 6,
  counterSignals: 2,
  sourcesUsed: 3,
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
const CHAIN = [
  { order: 1, informationClass: 'FACT', claimId: 'F1', statement: 'Ports adopt robots.', evidenceIds: ['E001'] },
  { order: 2, informationClass: 'FUTURE_EVENT', statement: 'Robots run the ports.', evidenceIds: [], year: 2031 },
] as CausalStep[];
const SOURCES = [
  {
    evidenceId: 'E001',
    section: 'CORE',
    title: 'Ports adopt robots',
    publisher: 'Reuters',
    url: 'https://example.com/a',
    usedInScenario: true,
    counterSignal: false,
  },
];

interface Intent {
  id: string;
  bucket: string;
  description: string;
  drivenBy: string[];
}
const D = ['Darkness 9/10', 'Horizon 5 years'];
const INTENTS: Intent[] = [
  { id: 'I01', bucket: 'WILDCARD', description: 'Current developments related to New pandemic — risks, threats, failures and warnings', drivenBy: ['New pandemic 8/10', ...D] },
  { id: 'I02', bucket: 'WILDCARD', description: 'Current developments related to Humanoid robot boom — risks, threats, failures and warnings', drivenBy: ['Humanoid robot boom 6/10', ...D] },
  { id: 'I03', bucket: 'MAJOR', description: 'Major current world events — risks, threats, failures and warnings', drivenBy: D },
  { id: 'I04', bucket: 'ADJACENT', description: 'Adjacent developments in Biology — risks, threats, failures and warnings', drivenBy: D },
  { id: 'I05', bucket: 'ADJACENT', description: 'Adjacent developments in Robotics — risks, threats, failures and warnings', drivenBy: D },
  { id: 'I06', bucket: 'UNEXPECTED', description: 'Unusual early signals and research — risks, threats, failures and warnings', drivenBy: D },
];

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

function futureResult(intents: Intent[] = INTENTS, counts: Record<string, number> = C14): FutureResult {
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
    causalChain: CHAIN,
    sources: SOURCES,
    research: { intents, counts },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

const SUMMARY: [string, string, string, string][] = [
  ['summary-searches', 'searches', 'search performed', 'searches performed'],
  ['summary-articles', 'articlesConsidered', 'article considered', 'articles considered'],
  ['summary-events', 'uniqueEvents', 'unique event identified', 'unique events identified'],
  ['summary-selected', 'eventsSelected', 'event selected', 'events selected'],
  ['summary-counter-signals', 'counterSignals', 'counter-signal retained', 'counter-signals retained'],
  ['summary-sources-used', 'sourcesUsed', 'source directly influenced the scenario', 'sources directly influenced the scenario'],
];

describe('slice 14_why-these-news: WHY THESE NEWS? panel', () => {
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
  const follows = (a: string, b: string): boolean =>
    !!byId(a) && !!byId(b) && !!(byId(a)!.compareDocumentPosition(byId(b)!) & Node.DOCUMENT_POSITION_FOLLOWING);

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

  async function openWith(intents: Intent[], counts: Record<string, number> = C14): Promise<void> {
    await openResult(futureResult(intents, counts));
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

  // @trace FR-28
  it('N1 shows the closed WHY THESE NEWS? button after SOURCES and no panel', async () => {
    await openResult();
    const b = byId('open-why-news');
    expect(b).not.toBeNull();
    expect(text('open-why-news')).toBe('WHY THESE NEWS?');
    expect(b!.getAttribute('type')).toBe('button');
    expect(b!.getAttribute('aria-expanded')).toBe('false');
    expect(byId('result-actions')!.contains(b)).toBe(true);
    expect(follows('open-sources', 'open-why-news')).toBe(true);
    expect(byId('why-news-panel')).toBeNull();
  });

  // @trace FR-28
  it('N2 opens the panel with one intent per entry in API order', async () => {
    await openResult();
    await click('open-why-news');
    expect(byId('why-news-panel')).not.toBeNull();
    expect(byId('result-view')!.contains(byId('why-news-panel'))).toBe(true);
    expect(follows('result-actions', 'why-news-panel')).toBe(true);
    expect(byId('open-why-news')!.getAttribute('aria-expanded')).toBe('true');
    expect(text('why-news-title')).toBe('WHY THESE NEWS?');
    expect(ids(/^why-news-intent-I\d+$/)).toEqual(INTENTS.map((i) => `why-news-intent-${i.id}`));
    expect(text('why-news-description-I01')).toBe(INTENTS[0].description);
    expect(byId('why-news-empty')).toBeNull();
  });

  // @trace FR-28
  it('N3 shows the drivenBy chips in array order inside their container', async () => {
    await openResult();
    await click('open-why-news');
    expect(text('why-news-driver-I01-0')).toBe('New pandemic 8/10');
    expect(text('why-news-driver-I01-1')).toBe('Darkness 9/10');
    expect(text('why-news-driver-I01-2')).toBe('Horizon 5 years');
    expect(byId('why-news-driver-I01-3')).toBeNull();
    expect(follows('why-news-driver-I01-0', 'why-news-driver-I01-1')).toBe(true);
    expect(follows('why-news-driver-I01-1', 'why-news-driver-I01-2')).toBe(true);
    expect(ids(/^why-news-driver-I03-\d+$/)).toEqual(['why-news-driver-I03-0', 'why-news-driver-I03-1']);
    expect(text('why-news-driver-I03-0')).toBe('Darkness 9/10');
    expect(text('why-news-driver-I03-1')).toBe('Horizon 5 years');
    for (const i of INTENTS) {
      const box = byId(`why-news-drivers-${i.id}`);
      expect(box, `drivers ${i.id}`).not.toBeNull();
      i.drivenBy.forEach((_, k) => expect(box!.contains(byId(`why-news-driver-${i.id}-${k}`))).toBe(true));
    }
  });

  // @trace FR-28
  it('N4 shows the research summary from research.counts, never articlesRetrieved', async () => {
    await openResult();
    await click('open-why-news');
    const summary = byId('research-summary');
    expect(summary).not.toBeNull();
    expect(byId('why-news-panel')!.contains(summary)).toBe(true);
    expect(follows('why-news-intent-I06', 'research-summary')).toBe(true);
    expect(text('research-summary-title')).toBe('Research summary');
    const expected = [
      '20 searches performed',
      '81 articles considered',
      '12 unique events identified',
      '6 events selected',
      '2 counter-signals retained',
      '3 sources directly influenced the scenario',
    ];
    expect(SUMMARY.map(([id]) => text(id))).toEqual(expected);
    for (let i = 1; i < SUMMARY.length; i++) expect(follows(SUMMARY[i - 1][0], SUMMARY[i][0])).toBe(true);
    expect(follows('research-summary-title', SUMMARY[0][0])).toBe(true);
    expect(byId('why-news-panel')!.textContent).not.toContain('100');
    expect(byId('why-news-panel')!.textContent).not.toContain('31');
  });

  // @trace FR-28
  it.each(SUMMARY)('N5 %s with 0 is plural', async (id, field, _s, plural) => {
    await openWith(INTENTS, { ...C14, [field]: 0 });
    expect(text(id)).toBe(`0 ${plural}`);
  });

  // @trace FR-28
  it.each(SUMMARY)('N6 %s with 1 is singular', async (id, field, singular) => {
    await openWith(INTENTS, { ...C14, [field]: 1 });
    expect(text(id)).toBe(`1 ${singular}`);
  });

  // @trace FR-28
  it.each(SUMMARY)('N7 %s with 1234 has no separator', async (id, field, _s, plural) => {
    await openWith(INTENTS, { ...C14, [field]: 1234 });
    expect(text(id)).toBe(`1234 ${plural}`);
  });

  // @trace FR-28
  it('N7b the six numbers come from research.counts, not metadata.counts', async () => {
    await openResult();
    await click('open-why-news');
    expect(text('summary-searches')).toBe('20 searches performed');
    expect(text('summary-searches')).not.toContain('5 searches');
  });

  // @trace FR-28
  it('N8 empty intents show only the empty note and the summary', async () => {
    await openWith([]);
    expect(text('why-news-empty')).toBe('No research intents recorded');
    expect(ids(/^why-news-intent-/)).toEqual([]);
    expect(byId('research-summary')).not.toBeNull();
    expect(follows('why-news-empty', 'research-summary')).toBe(true);
  });

  // @trace FR-28
  it.each([0, 1, 6])('R1 %i intents render that many entries', async (n) => {
    await openWith(INTENTS.slice(0, n));
    expect(ids(/^why-news-intent-I\d+$/)).toHaveLength(n);
    expect(!!byId('why-news-empty')).toBe(n === 0);
  });

  // @trace FR-28
  it.each([0, 1, 3])('R2 %i drivers render that many chips', async (n) => {
    const drivers = ['A 1/10', 'B 2/10', 'C 3/10'].slice(0, n);
    await openWith([{ ...INTENTS[0], drivenBy: drivers }]);
    expect(ids(/^why-news-driver-I01-\d+$/)).toHaveLength(n);
    drivers.forEach((d, k) => expect(text(`why-news-driver-I01-${k}`)).toBe(d));
    expect(!!byId('why-news-drivers-I01')).toBe(n > 0);
  });

  // @trace FR-28
  it('N9 an intent without drivers has no drivers container', async () => {
    await openWith([{ ...INTENTS[0], drivenBy: [] }]);
    expect(byId('why-news-intent-I01')).not.toBeNull();
    expect(byId('why-news-drivers-I01')).toBeNull();
    expect(ids(/^why-news-driver-I01-/)).toEqual([]);
  });

  // @trace FR-28
  it('N10 descriptions and drivers are rendered as text, never markup', async () => {
    await openWith([
      { ...INTENTS[0], description: '<img src=x onerror=alert(1)>', drivenBy: ['<b>x</b> 7/10', 'Horizon 1 year'] },
    ]);
    expect(text('why-news-description-I01')).toBe('<img src=x onerror=alert(1)>');
    expect(text('why-news-driver-I01-0')).toBe('<b>x</b> 7/10');
    const panel = byId('why-news-panel')!;
    expect(panel.querySelector('img')).toBeNull();
    expect(panel.querySelector('b')).toBeNull();
  });

  // @trace FR-28
  it('N11 the button toggles only its own panel', async () => {
    await openResult();
    await click('open-why');
    await click('open-sources');
    await click('open-why-news');
    expect(byId('why-panel')).not.toBeNull();
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('why-news-panel')).not.toBeNull();
    await click('open-why-news');
    expect(byId('why-news-panel')).toBeNull();
    expect(byId('why-panel')).not.toBeNull();
    expect(byId('sources-panel')).not.toBeNull();
    expect(byId('open-why-news')!.getAttribute('aria-expanded')).toBe('false');
  });

  // @trace FR-28
  it('N12 makes no extra HTTP call and keeps panel order', async () => {
    await openResult();
    await click('open-why-news');
    await click('open-why');
    expect(byId('why-news-panel')).not.toBeNull();
    expect(follows('why-panel', 'why-news-panel')).toBe(true);
    expect(http.match(isResultGet)).toHaveLength(0);
    expect(http.match((r) => r.url.includes(`/api/runs/${RUN_ID}/research`))).toHaveLength(0);
  });
});
