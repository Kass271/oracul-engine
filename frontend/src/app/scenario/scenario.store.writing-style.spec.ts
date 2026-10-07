import { TestBed } from '@angular/core/testing';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import type { WritingStyleCatalogue } from '../api/models/writing-style-catalogue';
import {
  NARRATIVE_IDS,
  PANEL_OUTPUT,
  SHIPPED_PROFILES,
  WRITING_STYLES,
  panelOutput,
} from './testing/panel-fixtures';
import { DEFAULT_CONFIGURATION, ScenarioStore } from './scenario.store';

/** The slice 02_style-selection surface of the store, accessed loosely so the spec compiles before it exists. */
interface StyleStoreApi {
  writingStyle(): { mode: string; profileId: string };
  language(): string;
  setWritingStyles(catalogue: WritingStyleCatalogue): void;
  setWritingMode(mode: unknown): void;
  setNarrativeProfile(id: unknown): void;
  setLanguage(code: unknown): void;
}

const OFFICIAL = { mode: 'OFFICIAL', profileId: 'official-briefing' };
const BASE: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};
const withOutput = (output: unknown): ScenarioConfiguration =>
  ({ ...BASE, output }) as ScenarioConfiguration;

/** Every (mode, profile) pair of the shipped catalogue: the profile's own mode is the only valid one. */
const GRID = (['OFFICIAL', 'NARRATIVE'] as const).flatMap((mode) =>
  SHIPPED_PROFILES.map((p) => ({ mode, profileId: p.id, valid: p.mode === mode })),
);

describe('ScenarioStore: writing style and language (slice 02_style-selection)', () => {
  let store: ScenarioStore;
  let styles: StyleStoreApi;

  beforeEach(() => {
    TestBed.configureTestingModule({});
    store = TestBed.inject(ScenarioStore);
    styles = store as unknown as StyleStoreApi;
  });

  // ---- defaults ----

  // @trace FR-63, FR-64
  it('DEFAULT_CONFIGURATION and a never-loaded store carry Official / Briefing and English', () => {
    expect(DEFAULT_CONFIGURATION.output).toEqual(PANEL_OUTPUT);
    expect(store.configuration().output).toEqual(PANEL_OUTPUT);
    expect(styles.writingStyle()).toEqual(OFFICIAL);
    expect(styles.language()).toBe('en');
  });

  // ---- load normalisation (writing style) ----

  const INVALID_STYLES: [string, unknown][] = [
    ['absent', undefined],
    ['null', null],
    ['a string', 'kafka'],
    ['an empty object', {}],
    ['a number', 5],
    ['an array', [{ mode: 'OFFICIAL', profileId: 'official-briefing' }]],
    ['a lowercase mode', { mode: 'narrative', profileId: 'kafka' }],
    ['an unknown mode', { mode: 'POETIC', profileId: 'kafka' }],
    ['a missing mode', { profileId: 'kafka' }],
    ['a missing profile', { mode: 'NARRATIVE' }],
    ['a null profile', { mode: 'NARRATIVE', profileId: null }],
    ['a numeric profile', { mode: 'NARRATIVE', profileId: 5 }],
    ['OFFICIAL with a narrative profile', { mode: 'OFFICIAL', profileId: 'kafka' }],
    ['OFFICIAL with neutral-narrative', { mode: 'OFFICIAL', profileId: 'neutral-narrative' }],
    ['NARRATIVE with the official profile', { mode: 'NARRATIVE', profileId: 'official-briefing' }],
    [
      'NARRATIVE with a profile that is not in the catalogue',
      { mode: 'NARRATIVE', profileId: 'dickens' },
    ],
    ['NARRATIVE with a wrong-case profile', { mode: 'NARRATIVE', profileId: 'Kafka' }],
    ['NARRATIVE with an empty profile', { mode: 'NARRATIVE', profileId: '' }],
  ];

  // Ranges & invariants: every invalid class normalises to Official / Briefing (catalogue known).
  // @trace FR-63
  it.each(INVALID_STYLES)(
    'load with a writingStyle that is %s gives Official / Briefing',
    (_name, writingStyle) => {
      styles.setWritingStyles(WRITING_STYLES);
      store.load(withOutput({ story: true, illustration: false, writingStyle }));
      expect(styles.writingStyle()).toEqual(OFFICIAL);
      expect(store.configuration().output).toEqual(PANEL_OUTPUT);
    },
  );

  // @trace FR-63
  it.each(
    INVALID_STYLES.filter(([, v]) => {
      const p = (v as { profileId?: unknown } | null)?.profileId;
      return p !== 'dickens' && p !== 'Kafka'; // without a catalogue any other non-empty id is kept (next test)
    }),
  )(
    'load without a known catalogue also normalises a writingStyle that is %s',
    (_name, writingStyle) => {
      store.load(withOutput({ story: true, illustration: false, writingStyle }));
      // 'NARRATIVE with an empty profile' and the other non-string / cross-mode classes never survive
      expect(styles.writingStyle()).toEqual(OFFICIAL);
    },
  );

  // @trace FR-63
  it('load without a known catalogue keeps NARRATIVE with any non-empty profile id other than official-briefing', () => {
    store.load(
      withOutput({
        story: true,
        illustration: false,
        writingStyle: { mode: 'NARRATIVE', profileId: 'dickens' },
      }),
    );
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'dickens' });
  });

  // Ranges & invariants: all 2 x 9 (mode, profile) combinations; valid iff the mode equals the profile's mode.
  // @trace FR-63
  it.each(GRID)(
    'load of {$mode, $profileId} is kept iff valid ($valid)',
    ({ mode, profileId, valid }) => {
      styles.setWritingStyles(WRITING_STYLES);
      store.load(
        withOutput({ story: true, illustration: false, writingStyle: { mode, profileId } }),
      );
      expect(styles.writingStyle()).toEqual(valid ? { mode, profileId } : OFFICIAL);
    },
  );

  // @trace FR-63
  it.each(SHIPPED_PROFILES)('load keeps the valid pair of $id and drops other inner keys', (p) => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load(
      withOutput({
        story: true,
        illustration: false,
        writingStyle: { mode: p.mode, profileId: p.id, foo: 1 },
      }),
    );
    expect(styles.writingStyle()).toEqual({ mode: p.mode, profileId: p.id });
    expect(store.configuration().output.writingStyle).toEqual({ mode: p.mode, profileId: p.id });
  });

  // ---- load normalisation (language) ----

  const INVALID_LANGUAGES: [string, unknown][] = [
    ['absent', undefined],
    ['null', null],
    ['de', 'de'],
    ['EN', 'EN'],
    ['ua', 'ua'],
    ['uk-UA', 'uk-UA'],
    ['an empty string', ''],
    ['the number 1', 1],
    ['true', true],
    ['an array', ['uk']],
  ];

  // Ranges & invariants: language absent / null / "de" / "EN" / "ua" / 1 ... -> en; en and uk are kept.
  // @trace FR-64
  it.each(INVALID_LANGUAGES)('load with a language that is %s gives en', (_name, language) => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load(withOutput({ story: true, illustration: false, language }));
    expect(styles.language()).toBe('en');
    expect(store.configuration().output.language).toBe('en');
  });

  // @trace FR-64
  it.each(['en', 'uk'])('load keeps the language %s', (language) => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load(withOutput({ story: true, illustration: false, language }));
    expect(styles.language()).toBe(language);
    expect(store.configuration().output.language).toBe(language);
  });

  // @trace FR-63, FR-64
  it('load keeps story and illustration as given and adds both defaults', () => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load(withOutput({ story: false, illustration: true }));
    expect(store.configuration().output).toEqual({
      ...PANEL_OUTPUT,
      story: false,
      illustration: true,
    });
  });

  // ---- setters ----

  // @trace FR-63
  it('setWritingMode NARRATIVE selects the narrative default, OFFICIAL the official profile', () => {
    styles.setWritingStyles(WRITING_STYLES);
    styles.setWritingMode('NARRATIVE');
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'neutral-narrative' });
    expect(store.configuration().output.writingStyle).toEqual({
      mode: 'NARRATIVE',
      profileId: 'neutral-narrative',
    });
    styles.setWritingMode('OFFICIAL');
    expect(styles.writingStyle()).toEqual(OFFICIAL);
  });

  // @trace FR-63
  it('NARRATIVE starts at the mode default from the catalogue, not at a hard-coded id', () => {
    const catalogue: WritingStyleCatalogue = {
      ...WRITING_STYLES,
      modes: [WRITING_STYLES.modes[0], { ...WRITING_STYLES.modes[1], defaultProfileId: 'poe' }],
    };
    styles.setWritingStyles(catalogue);
    styles.setWritingMode('NARRATIVE');
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'poe' });
  });

  // @trace FR-63
  it('selecting the current mode again changes nothing (the chosen narrative profile stays)', () => {
    styles.setWritingStyles(WRITING_STYLES);
    styles.setWritingMode('NARRATIVE');
    styles.setNarrativeProfile('kafka');
    styles.setWritingMode('NARRATIVE');
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'kafka' });
  });

  // @trace FR-63
  it('leaving NARRATIVE and returning starts at neutral-narrative again', () => {
    styles.setWritingStyles(WRITING_STYLES);
    styles.setWritingMode('NARRATIVE');
    styles.setNarrativeProfile('orwell');
    styles.setWritingMode('OFFICIAL');
    styles.setWritingMode('NARRATIVE');
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'neutral-narrative' });
  });

  // @trace FR-63
  it.each([undefined, null, 'narrative', 'official', 'POETIC', '', 1, {}])(
    'setWritingMode(%j) is ignored',
    (bad) => {
      styles.setWritingStyles(WRITING_STYLES);
      styles.setWritingMode('NARRATIVE');
      styles.setNarrativeProfile('poe');
      styles.setWritingMode(bad);
      expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'poe' });
    },
  );

  // Ranges & invariants: every NARRATIVE profile of the catalogue can be selected.
  // @trace FR-63
  it.each(NARRATIVE_IDS)('setNarrativeProfile(%s) selects it in mode NARRATIVE', (id) => {
    styles.setWritingStyles(WRITING_STYLES);
    styles.setWritingMode('NARRATIVE');
    styles.setNarrativeProfile(id);
    expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: id });
  });

  // @trace FR-63
  it.each(['kafka', 'official-briefing', 'dickens', '', null, 5])(
    'setNarrativeProfile(%j) is ignored while the mode is OFFICIAL',
    (id) => {
      styles.setWritingStyles(WRITING_STYLES);
      styles.setNarrativeProfile(id);
      expect(styles.writingStyle()).toEqual(OFFICIAL);
    },
  );

  // @trace FR-63
  it.each(['official-briefing', 'dickens', 'Kafka', ' kafka', '', null, undefined, 5])(
    'setNarrativeProfile(%j) is ignored in mode NARRATIVE (not a NARRATIVE profile of the catalogue)',
    (id) => {
      styles.setWritingStyles(WRITING_STYLES);
      styles.setWritingMode('NARRATIVE');
      styles.setNarrativeProfile('wells');
      styles.setNarrativeProfile(id);
      expect(styles.writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'wells' });
    },
  );

  // Ranges & invariants: setLanguage accepts only en / uk.
  // @trace FR-64
  it.each(['en', 'uk'])('setLanguage(%s) sets the language', (code) => {
    styles.setLanguage(code === 'en' ? 'uk' : 'en');
    styles.setLanguage(code);
    expect(styles.language()).toBe(code);
    expect(store.configuration().output.language).toBe(code);
  });

  // @trace FR-64
  it.each([
    'EN',
    'Uk',
    'ua',
    'uk-UA',
    'de',
    '',
    ' en',
    'uk ',
    null,
    undefined,
    1,
    true,
    ['uk'],
    {},
  ])('setLanguage(%j) is ignored', (bad) => {
    styles.setLanguage('uk');
    styles.setLanguage(bad);
    expect(styles.language()).toBe('uk');
  });

  // ---- independence ----

  // @trace FR-63, FR-64
  it('a style or language change leaves every other configuration field unchanged', () => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load({
      ...BASE,
      realism: 3,
      darkness: 9,
      optimism: 2,
      horizon: '10y',
      wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 7 }],
      customWildcards: [{ label: 'Mars', intensity: 4 }],
    });
    const rest = () => {
      const { output, ...others } = store.configuration();
      return { others, story: output.story, illustration: output.illustration };
    };
    const before = rest();
    styles.setWritingMode('NARRATIVE');
    expect(rest()).toEqual(before);
    styles.setNarrativeProfile('verne');
    expect(rest()).toEqual(before);
    styles.setLanguage('uk');
    expect(rest()).toEqual(before);
    styles.setWritingMode('OFFICIAL');
    expect(rest()).toEqual(before);
  });

  // @trace FR-63, FR-64
  it('no other setter touches the style or the language', () => {
    styles.setWritingStyles(WRITING_STYLES);
    styles.setWritingMode('NARRATIVE');
    styles.setNarrativeProfile('clarke');
    styles.setLanguage('uk');
    store.setRealism(2);
    store.setDarkness(7);
    store.setOptimism(9);
    store.setHorizon('20y');
    store.enableWildcard('biology-new-pandemic');
    store.setWildcardIntensity('biology-new-pandemic', 9);
    store.disableWildcard('biology-new-pandemic');
    store.addCustomWildcard('Flying trains');
    store.setCustomWildcardIntensity(0, 8);
    store.removeCustomWildcard(0);
    expect(store.configuration().output).toEqual(panelOutput('NARRATIVE', 'clarke', 'uk'));
  });

  // Ranges & invariants: after any sequence of load / setWritingMode / setNarrativeProfile the profile has the mode.
  // @trace FR-63
  it.each(GRID)(
    'the profile always has the store mode after load {$mode, $profileId} and the setters',
    ({ mode, profileId }) => {
      const byId = new Map(SHIPPED_PROFILES.map((p) => [p.id, p.mode]));
      const consistent = () => {
        const s = styles.writingStyle();
        expect(byId.get(s.profileId)).toBe(s.mode);
      };
      styles.setWritingStyles(WRITING_STYLES);
      store.load(
        withOutput({ story: true, illustration: false, writingStyle: { mode, profileId } }),
      );
      consistent();
      for (const m of ['NARRATIVE', 'OFFICIAL', 'NARRATIVE']) {
        styles.setWritingMode(m);
        consistent();
        for (const id of [...NARRATIVE_IDS, 'official-briefing', 'dickens']) {
          styles.setNarrativeProfile(id);
          consistent();
        }
      }
    },
  );

  // @trace FR-63
  it('the profile has the store mode after a load with an unknown profile id and the setters', () => {
    styles.setWritingStyles(WRITING_STYLES);
    store.load(
      withOutput({
        story: true,
        illustration: false,
        writingStyle: { mode: 'NARRATIVE', profileId: 'dickens' },
      }),
    );
    expect(styles.writingStyle()).toEqual(OFFICIAL);
    styles.setNarrativeProfile('dickens');
    expect(styles.writingStyle()).toEqual(OFFICIAL);
    styles.setWritingMode('NARRATIVE');
    expect(styles.writingStyle().profileId).toBe('neutral-narrative');
  });
});
