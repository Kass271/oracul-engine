// @trace FR-68
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  type TestRequest,
} from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import type { RecentRunSummary } from '../api/models/recent-run-summary';
import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { SHIPPED_PROFILES, flushWritingStyles } from '../scenario/testing/panel-fixtures';

const DEFAULTS: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

const CATALOGUE: ScenarioCatalogue = {
  horizons: [{ code: '1y', label: '1 year' }],
  categories: [],
  defaults: DEFAULTS,
  limits: {
    intensityMin: 1,
    intensityMax: 10,
    customWildcardMax: 3,
    customWildcardLabelMaxLength: 40,
    defaultWildcardIntensity: 5,
  },
} as ScenarioCatalogue;

const idOf = (i: number): string => `00000000-0000-0000-0000-${String(i).padStart(12, '0')}`;
const LEGACY = 'Official / Briefing (legacy)';

function item(i: number, over: Partial<RecentRunSummary> = {}): RecentRunSummary {
  return {
    id: idOf(i),
    generationId: `ORC-2026-10-08-${1000 + i}`,
    kind: 'STANDARD',
    createdAt: new Date(2026, 0, 1, 9, 5).toISOString(),
    completedAt: new Date(2026, 0, 1, 9, 6).toISOString(),
    headline: `Headline ${i}`,
    configuration: DEFAULTS,
    ...over,
  } as RecentRunSummary;
}

describe('slice 05_style-labels: style and language in Recent futures', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const isList = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith('/api/runs');
  const byId = (id: string): HTMLElement | null => document.querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const entries = (): string[] =>
    Array.from(document.querySelectorAll<HTMLElement>('[data-testid^="recent-future-"]'))
      .map((e) => e.getAttribute('data-testid') ?? '')
      .filter((t) => /^recent-future-[0-9a-f-]{36}$/.test(t));

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  async function boot(): Promise<void> {
    fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    http.match((r) => r.url.endsWith('/api/scenario/catalogue')).forEach((r) => r.flush(CATALOGUE));
    http
      .match((r) => r.url.endsWith('/api/scenario/configuration'))
      .forEach((r) => r.flush(DEFAULTS));
    flushWritingStyles(http);
    http
      .match((r) => r.url.endsWith('/api/auth/chatgpt/connection'))
      .forEach((r) => r.flush({ state: 'CONNECTED', canGenerate: true }));
    await settle();
  }

  async function open(): Promise<TestRequest> {
    (
      fixture.nativeElement.querySelector(
        '[data-testid="recent-futures-button"]',
      ) as HTMLButtonElement
    ).click();
    await settle();
    const reqs = http.match(isList);
    expect(reqs).toHaveLength(1);
    return reqs[0];
  }

  async function openWith(items: RecentRunSummary[]): Promise<void> {
    const req = await open();
    req.flush({ items });
    await settle();
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

  const STYLES = [...SHIPPED_PROFILES.map((p) => p.label), LEGACY, 'tolstoy'];
  const CASES = STYLES.flatMap((style) =>
    ['English', 'Ukrainian'].map((language) => [style, language] as const),
  );

  // Ranges & invariants: "<styleLabel> · <languageLabel>" with U+00B7 and one space each side, for the 9 shipped labels, the legacy
  // label, a profile id that left the file and both languages.
  // @trace FR-68
  it.each(CASES)(
    'shows "%s · %s" under the settings of a COMPLETED entry',
    async (style, language) => {
      await boot();
      await openWith([item(1, { styleLabel: style, languageLabel: language })]);
      expect(text(`recent-future-style-${idOf(1)}`)).toBe(`${style} · ${language}`);
      expect(byId(`recent-future-style-${idOf(1)}`)?.tagName.toLowerCase()).toBe('span');
    },
  );

  // @trace FR-68
  it('also labels a STOPPED entry', async () => {
    await boot();
    await openWith([
      item(1, {
        status: 'STOPPED',
        headline: undefined,
        styleLabel: 'Narrative — inspired by Franz Kafka',
        languageLabel: 'Ukrainian',
      }),
    ]);
    expect(text(`recent-future-status-${idOf(1)}`)).toBe('Stopped');
    expect(text(`recent-future-style-${idOf(1)}`)).toBe(
      'Narrative — inspired by Franz Kafka · Ukrainian',
    );
  });

  // @trace FR-68
  it('the label comes after the settings span and does not change the other spans or the entry ids', async () => {
    await boot();
    const items = [
      item(2, {
        styleLabel: 'Narrative — inspired by Edgar Allan Poe',
        languageLabel: 'Ukrainian',
      }),
      item(1, { styleLabel: 'Official / Briefing', languageLabel: 'English' }),
    ];
    await openWith(items);
    expect(entries()).toEqual([`recent-future-${idOf(2)}`, `recent-future-${idOf(1)}`]);
    for (const i of items) {
      const settings = byId(`recent-future-settings-${i.id}`) as HTMLElement;
      const style = byId(`recent-future-style-${i.id}`);
      expect(style, `style label of ${i.id}`).not.toBeNull();
      expect(
        settings.compareDocumentPosition(style as HTMLElement) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
      expect(text(`recent-future-settings-${i.id}`)).toBe('R8 D5 O5 · 1 year');
      expect(text(`recent-future-headline-${i.id}`)).toBe(i.headline);
      expect(byId(`recent-future-time-${i.id}`)).not.toBeNull();
    }
  });

  // @trace FR-68
  it.each([
    ['both fields absent', {}],
    ['the style absent', { languageLabel: 'English' }],
    ['the language absent', { styleLabel: 'Official / Briefing' }],
  ])('renders no style element when %s', async (_name, over) => {
    await boot();
    await openWith([item(1, over as Partial<RecentRunSummary>)]);
    expect(byId(`recent-future-${idOf(1)}`)).not.toBeNull();
    expect(byId(`recent-future-style-${idOf(1)}`)).toBeNull();
  });

  // @trace FR-68
  it('renders the labels as literal text, never as HTML', async () => {
    await boot();
    await openWith([item(1, { styleLabel: '<b>s</b>', languageLabel: '<i>l</i>' })]);
    const el = byId(`recent-future-style-${idOf(1)}`) as HTMLElement;
    expect(el).not.toBeNull();
    expect((el.textContent ?? '').trim()).toBe('<b>s</b> · <i>l</i>');
    expect(el.querySelector('b, i')).toBeNull();
  });

  // @trace FR-68
  it('takes the labels from the list answer, not from the entry configuration', async () => {
    await boot();
    const cfg = {
      ...DEFAULTS,
      output: {
        story: true,
        illustration: false,
        writingStyle: { mode: 'NARRATIVE', profileId: 'kafka' },
        language: 'uk',
      },
    };
    await openWith([
      item(1, {
        configuration: cfg as ScenarioConfiguration,
        styleLabel: 'Official / Briefing',
        languageLabel: 'English',
      }),
    ]);
    expect(text(`recent-future-style-${idOf(1)}`)).toBe('Official / Briefing · English');
  });
});
