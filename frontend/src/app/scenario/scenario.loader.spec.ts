import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  type TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { PANEL_OUTPUT, WRITING_STYLES, panelOutput } from './testing/panel-fixtures';
import { ScenarioLoader } from './scenario.loader';
import { ScenarioStore } from './scenario.store';

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
};

type Part = 'catalogue' | 'configuration' | 'styles';
const URLS: Record<Part, string> = {
  catalogue: '/api/scenario/catalogue',
  configuration: '/api/scenario/configuration',
  styles: '/api/writing-styles',
};
const BODIES: Record<Part, object> = {
  catalogue: CATALOGUE,
  configuration: DEFAULTS,
  styles: WRITING_STYLES,
};

/** All 6 arrival orders of the three load responses. */
const ORDERS: Part[][] = [
  ['catalogue', 'configuration', 'styles'],
  ['catalogue', 'styles', 'configuration'],
  ['configuration', 'catalogue', 'styles'],
  ['configuration', 'styles', 'catalogue'],
  ['styles', 'catalogue', 'configuration'],
  ['styles', 'configuration', 'catalogue'],
];

/** The slice 02_style-selection surface of the loader, accessed loosely so the spec compiles before it exists. */
interface LoaderStyles {
  writingStyles?: () => unknown;
}

describe('ScenarioLoader: catalogue + configuration + writing styles (slice 02_style-selection)', () => {
  let loader: ScenarioLoader;
  let store: ScenarioStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    loader = TestBed.inject(ScenarioLoader);
    store = TestBed.inject(ScenarioStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
  });

  const open = (part: Part): TestRequest =>
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith(URLS[part]));
  const styles = (): unknown => (loader as unknown as LoaderStyles).writingStyles?.();

  // @trace FR-63
  it('load() issues the three GET requests at once, in parallel', () => {
    loader.load();
    expect(loader.state()).toBe('loading');
    open('catalogue');
    open('configuration');
    open('styles');
    http.verify();
  });

  // Ranges & invariants: the panel becomes ready only with all three responses, for each of the 6 arrival orders.
  // @trace FR-63
  it.each(ORDERS)(
    'arrival order %s, %s, %s: loading until the third response, then ready',
    (a, b, c) => {
      loader.load();
      const reqs = {
        catalogue: open('catalogue'),
        configuration: open('configuration'),
        styles: open('styles'),
      };
      reqs[a].flush(BODIES[a]);
      expect(loader.state()).toBe('loading');
      reqs[b].flush(BODIES[b]);
      expect(loader.state()).toBe('loading');
      reqs[c].flush(BODIES[c]);
      expect(loader.state()).toBe('ready');
      expect(loader.catalogue()).toEqual(CATALOGUE);
      expect(styles()).toEqual(WRITING_STYLES);
    },
  );

  // Ranges & invariants: the panel goes to the error state when any one of the three fails (3 cases).
  // @trace FR-63
  it.each(['catalogue', 'configuration', 'styles'] as Part[])(
    'a failing %s request gives the error state at once',
    (failing) => {
      loader.load();
      const reqs = {
        catalogue: open('catalogue'),
        configuration: open('configuration'),
        styles: open('styles'),
      };
      reqs[failing].flush('', { status: 503, statusText: 'Unavailable' });
      expect(loader.state()).toBe('error');
      for (const p of ['catalogue', 'configuration', 'styles'] as Part[])
        if (p !== failing) reqs[p].flush(BODIES[p]);
      expect(loader.state()).toBe('error');
    },
  );

  // @trace FR-63
  it('a network error of the writing styles request gives the error state', () => {
    loader.load();
    open('catalogue').flush(CATALOGUE);
    open('configuration').flush(DEFAULTS);
    open('styles').error(new ProgressEvent('error'));
    expect(loader.state()).toBe('error');
  });

  // @trace FR-63
  it('load() again after an error re-issues all three requests and becomes ready with the new answers', () => {
    loader.load();
    open('styles').flush('', { status: 502, statusText: 'Bad Gateway' });
    open('catalogue').flush(CATALOGUE);
    open('configuration').flush(DEFAULTS);
    expect(loader.state()).toBe('error');
    loader.load();
    expect(loader.state()).toBe('loading');
    open('catalogue').flush(CATALOGUE);
    open('configuration').flush(DEFAULTS);
    expect(loader.state()).toBe('loading');
    open('styles').flush(WRITING_STYLES);
    expect(loader.state()).toBe('ready');
  });

  // @trace FR-63
  it('the store is fed only after the configuration and the writing styles have both arrived', () => {
    loader.load();
    open('configuration').flush({
      ...DEFAULTS,
      darkness: 9,
      output: panelOutput('NARRATIVE', 'kafka', 'uk'),
    });
    expect(store.darkness()).toBe(5);
    open('styles').flush(WRITING_STYLES);
    expect(store.darkness()).toBe(9);
    expect(store.configuration().output).toEqual(panelOutput('NARRATIVE', 'kafka', 'uk'));
  });

  // @trace FR-63
  it('the store is fed when the writing styles arrived first', () => {
    loader.load();
    open('styles').flush(WRITING_STYLES);
    expect(store.darkness()).toBe(5);
    open('configuration').flush({
      ...DEFAULTS,
      darkness: 9,
      output: panelOutput('NARRATIVE', 'harrison', 'en'),
    });
    expect(store.darkness()).toBe(9);
    expect(store.configuration().output).toEqual(panelOutput('NARRATIVE', 'harrison', 'en'));
  });

  // @trace FR-63, FR-64
  it.each([
    [
      'a profile that left the catalogue',
      {
        story: true,
        illustration: false,
        writingStyle: { mode: 'NARRATIVE', profileId: 'dickens' },
        language: 'uk',
      },
      panelOutput('OFFICIAL', 'official-briefing', 'uk'),
    ],
    [
      'a profile of the other mode',
      {
        story: true,
        illustration: false,
        writingStyle: { mode: 'OFFICIAL', profileId: 'poe' },
        language: 'xx',
      },
      PANEL_OUTPUT,
    ],
    ['no style and no language', { story: true, illustration: false }, PANEL_OUTPUT],
  ])(
    'the loaded configuration with %s is normalised against the loaded catalogue',
    (_name, output, expected) => {
      loader.load();
      open('styles').flush(WRITING_STYLES);
      open('configuration').flush({ ...DEFAULTS, output });
      open('catalogue').flush(CATALOGUE);
      expect(store.configuration().output).toEqual(expected);
    },
  );
});
