// @trace FR-68
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { routes } from '../app.routes';
import type { FutureResult } from '../api/models/future-result';
import type { GenerationRun } from '../api/models/generation-run';
import { ScenarioStore } from '../scenario/scenario.store';
import { SHIPPED_PROFILES, WRITING_STYLES } from '../scenario/testing/panel-fixtures';

const RUN_ID = '11111111-2222-3333-4444-555555555555';
const PARENT_ID = '99999999-2222-3333-4444-555555555555';
const COUNTS = {
  searches: 20,
  articlesRetrieved: 31,
  articlesConsidered: 12,
  uniqueEvents: 7,
  eventsSelected: 5,
  counterSignals: 1,
  sourcesUsed: 2,
};
const CONFIG = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
  customWildcards: [],
  output: {
    story: true,
    illustration: false,
    writingStyle: { mode: 'NARRATIVE', profileId: 'kafka' },
    language: 'uk',
  },
};
const LEGACY = 'Official / Briefing (legacy)';

function completedRun(): GenerationRun {
  return {
    id: RUN_ID,
    generationId: 'ORC-2026-10-08-1000',
    kind: 'STANDARD',
    status: 'COMPLETED',
    stage: 'WRITING_STORY',
    stageLabel: 'Writing from the future…',
    stageIndex: 10,
    stageCount: 10,
    headline: 'Stub headline from the future',
    configuration: CONFIG,
    counts: COUNTS,
    createdAt: '2026-10-08T10:00:00Z',
    updatedAt: '2026-10-08T10:01:00Z',
    completedAt: '2026-10-08T10:01:00Z',
  } as unknown as GenerationRun;
}

interface Labels {
  styleLabel?: string;
  languageLabel?: string;
  rewrittenFrom?: { runId: string; headline: string };
  model?: string;
}

function result(labels: Labels = {}, wildcards = true): FutureResult {
  return {
    runId: RUN_ID,
    generationId: 'ORC-2026-10-08-1000',
    labels: ['AI-GENERATED FUTURE SCENARIO', 'POSSIBLE FUTURE — NOT CURRENT NEWS'],
    story: {
      headline: 'Stub headline from the future',
      dateline: 'ORACUL FUTURE — March 1, 2027',
      futureDate: '2027-03-01',
      body: 'a\n\nb',
    },
    metadata: {
      configuration: CONFIG,
      horizonLabel: '5 years',
      wildcards: wildcards ? [{ label: 'New pandemic', intensity: 8, custom: false }] : [],
      counts: COUNTS,
      ...labels,
    },
    causalChain: [],
    sources: [],
    research: { intents: [], counts: COUNTS },
    openCriticIssues: [],
  } as unknown as FutureResult;
}

describe('slice 05_style-labels: style, language and origin in the scenario metadata', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const chipIds = (): (string | null)[] => {
    const settings = byId('meta-realism')?.closest('mat-chip-set') as HTMLElement;
    return Array.from(settings.querySelectorAll('[data-testid]')).map((n) =>
      n.getAttribute('data-testid'),
    );
  };

  async function show(r: FutureResult): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${RUN_ID}`);
    http
      .expectOne((q) => q.method === 'GET' && q.url.endsWith(`/api/runs/${RUN_ID}`))
      .flush(completedRun());
    harness.detectChanges();
    await harness.fixture.whenStable();
    http
      .expectOne((q) => q.method === 'GET' && q.url.endsWith(`/api/runs/${RUN_ID}/result`))
      .flush(r);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
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

  const STYLE_LABELS = [...SHIPPED_PROFILES.map((p) => p.label), LEGACY, 'tolstoy'];
  const CASES = STYLE_LABELS.flatMap((style) =>
    ['English', 'Ukrainian'].map((language) => [style, language] as const),
  );

  // Ranges & invariants: the texts are exactly "Style: " / "Language: " + the API value, for the 9 shipped labels, the legacy label,
  // a profile id that left the file, and both languages.
  // @trace FR-68
  it.each(CASES)(
    'shows "Style: %s" and "Language: %s" from the result',
    async (style, language) => {
      await show(result({ styleLabel: style, languageLabel: language }));
      expect(text('meta-style')).toBe(`Style: ${style}`);
      expect(text('meta-language')).toBe(`Language: ${language}`);
      expect(byId('meta-rewritten-from')).toBeNull();
    },
  );

  // @trace FR-68
  it('the chips are meta-style and meta-language after meta-horizon and before meta-model', async () => {
    await show(
      result({
        styleLabel: 'Narrative — inspired by Franz Kafka',
        languageLabel: 'Ukrainian',
        model: 'gpt-5',
      }),
    );
    expect(chipIds()).toEqual([
      'meta-realism',
      'meta-darkness',
      'meta-optimism',
      'meta-horizon',
      'meta-style',
      'meta-language',
      'meta-model',
    ]);
    expect(byId('meta-style')?.tagName.toLowerCase()).toBe('mat-chip');
    expect(byId('meta-language')?.tagName.toLowerCase()).toBe('mat-chip');
  });

  // @trace FR-68
  it('without the two labels the chip list is the earlier one', async () => {
    await show(result({ model: 'gpt-5' }));
    expect(chipIds()).toEqual([
      'meta-realism',
      'meta-darkness',
      'meta-optimism',
      'meta-horizon',
      'meta-model',
    ]);
    expect(byId('meta-style')).toBeNull();
    expect(byId('meta-language')).toBeNull();
  });

  // @trace FR-68
  it.each([
    ['only the style', { styleLabel: 'Official / Briefing' }, ['meta-style']],
    ['only the language', { languageLabel: 'English' }, ['meta-language']],
    ['neither', {}, []],
  ])('renders each chip only when its field is present: %s', async (_name, labels, expected) => {
    await show(result(labels as Labels));
    const ids = chipIds().filter((id) => id === 'meta-style' || id === 'meta-language');
    expect(ids).toEqual(expected);
  });

  // @trace FR-68
  it('shows the origin of a rewrite right after the Settings chip set and before the wildcards', async () => {
    await show(
      result({
        styleLabel: 'Narrative — inspired by Franz Kafka',
        languageLabel: 'Ukrainian',
        rewrittenFrom: { runId: PARENT_ID, headline: 'Stub headline from the future' },
      }),
    );
    const origin = byId('meta-rewritten-from') as HTMLElement;
    expect(origin).not.toBeNull();
    expect(origin.tagName.toLowerCase()).toBe('p');
    expect(text('meta-rewritten-from')).toBe('Rewritten from: Stub headline from the future');
    expect(origin.querySelector('a')).toBeNull();
    expect(origin.closest('[data-testid="scenario-metadata"]')).not.toBeNull();
    const settings = byId('meta-realism')?.closest('mat-chip-set') as HTMLElement;
    expect(
      settings.compareDocumentPosition(origin) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(settings.nextElementSibling).toBe(origin);
    const wildcards = byId('meta-wildcards') as HTMLElement;
    expect(
      origin.compareDocumentPosition(wildcards) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  // @trace FR-68
  it('the origin comes right after the Settings chip set also when there are no wildcards', async () => {
    await show(result({ rewrittenFrom: { runId: PARENT_ID, headline: 'H' } }, false));
    const settings = byId('meta-realism')?.closest('mat-chip-set') as HTMLElement;
    expect(settings.nextElementSibling).toBe(byId('meta-rewritten-from'));
    expect(text('meta-rewritten-from')).toBe('Rewritten from: H');
  });

  // @trace FR-68
  it('there is no origin element for a result without rewrittenFrom', async () => {
    await show(result({ styleLabel: 'Official / Briefing', languageLabel: 'English' }));
    expect(byId('scenario-metadata')).not.toBeNull();
    expect(byId('meta-rewritten-from')).toBeNull();
  });

  // @trace FR-68
  it.each(['<b>x</b>', '<img src=x onerror=alert(1)>', 'Заголовок із майбутнього — Narrative'])(
    'renders the origin headline %s as literal text',
    async (headline) => {
      await show(
        result({
          styleLabel: '<i>s</i>',
          languageLabel: 'English',
          rewrittenFrom: { runId: PARENT_ID, headline },
        }),
      );
      expect(text('meta-rewritten-from')).toBe(`Rewritten from: ${headline}`);
      expect(byId('meta-rewritten-from')?.querySelector('b, img')).toBeNull();
      expect(text('meta-style')).toBe('Style: <i>s</i>');
      expect(byId('meta-style')?.querySelector('i')).toBeNull();
    },
  );

  // @trace FR-68
  it('takes the labels from the result, never from the panel, the style catalogue or the run configuration', async () => {
    const store = TestBed.inject(ScenarioStore);
    store.setWritingStyles(WRITING_STYLES);
    store.load({
      ...CONFIG,
      output: {
        story: true,
        illustration: false,
        writingStyle: { mode: 'NARRATIVE', profileId: 'poe' },
        language: 'uk',
      },
    } as never);
    await show(result({ styleLabel: 'Official / Briefing', languageLabel: 'English' }));
    // the run and the result configuration say Kafka / uk; the panel says Poe / uk; the labels of the result win
    expect(text('meta-style')).toBe('Style: Official / Briefing');
    expect(text('meta-language')).toBe('Language: English');
    store.setLanguage('en');
    store.setNarrativeProfile('wells');
    harness.detectChanges();
    expect(text('meta-style')).toBe('Style: Official / Briefing');
    expect(text('meta-language')).toBe('Language: English');
  });

  // @trace FR-68
  it('shows no style or language chip for a result whose configuration names a style but whose labels are absent', async () => {
    await show(result());
    expect(byId('scenario-metadata')?.textContent).not.toContain('Style:');
    expect(byId('scenario-metadata')?.textContent).not.toContain('Language:');
    expect(byId('meta-style')).toBeNull();
  });
});
