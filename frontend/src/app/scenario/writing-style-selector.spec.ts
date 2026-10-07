import { BreakpointObserver, type BreakpointState } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { App } from '../app';
import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import type { WritingStyleCatalogue } from '../api/models/writing-style-catalogue';
import {
  NARRATIVE_IDS,
  PANEL_OUTPUT,
  SHIPPED_PROFILES,
  WRITING_STYLES,
  panelOutput,
} from './testing/panel-fixtures';
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

const NARRATIVE_LABELS = SHIPPED_PROFILES.filter((p) => p.mode === 'NARRATIVE').map((p) => p.label);

/** The slice 02_style-selection surface of the store, accessed loosely so the spec compiles before it exists. */
interface StyleStoreApi {
  writingStyle(): { mode: string; profileId: string };
  language(): string;
}

describe('Writing Style selector and Story language select (slice 02_style-selection)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let subject: BehaviorSubject<BreakpointState>;

  const state = (matches: boolean): BreakpointState => ({ matches, breakpoints: {} });
  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const count = (id: string): number => el().querySelectorAll(`[data-testid="${id}"]`).length;
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const overlay = (id: string): HTMLElement | null =>
    document.querySelector(`[data-testid="${id}"]`);
  const store = (): ScenarioStore => TestBed.inject(ScenarioStore);
  const styles = (): StyleStoreApi => store() as unknown as StyleStoreApi;
  const radio = (id: string): HTMLInputElement =>
    byId(id)?.querySelector('input[type="radio"]') as HTMLInputElement;
  const checkedIn = (groupId: string): string[] =>
    Array.from(byId(groupId)?.querySelectorAll('mat-radio-button') ?? [])
      .filter((b) => (b.querySelector('input[type="radio"]') as HTMLInputElement).checked)
      .map((b) => b.getAttribute('data-testid') ?? '');

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function setup(mobile = false, catalogue: WritingStyleCatalogue = WRITING_STYLES): void {
    subject = new BehaviorSubject<BreakpointState>(state(mobile));
    TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        {
          provide: BreakpointObserver,
          useValue: { observe: () => subject, isMatched: () => subject.value.matches },
        },
      ],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
    startCatalogue = catalogue;
  }
  let startCatalogue: WritingStyleCatalogue = WRITING_STYLES;

  async function startLoaded(config: ScenarioConfiguration = DEFAULTS): Promise<void> {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush(CATALOGUE);
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush(config);
    http.expectOne((r) => r.url.endsWith('/api/writing-styles')).flush(startCatalogue);
    await settle();
    if (subject.value.matches) {
      byId('scenario-drawer-toggle')?.click();
      await settle();
    }
  }

  /** Answers the pending connection request: CONNECTED and allowed to generate. */
  function connected(): void {
    http
      .match((r) => r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection'))
      .forEach((r) => r.flush({ state: 'CONNECTED', canGenerate: true }));
  }

  async function chooseMode(mode: 'OFFICIAL' | 'NARRATIVE'): Promise<void> {
    radio(`writing-style-option-${mode}`).click();
    await settle();
  }

  async function chooseProfile(id: string): Promise<void> {
    radio(`narrative-style-option-${id}`).click();
    await settle();
  }

  async function openSelect(id: string): Promise<void> {
    (byId(id)?.querySelector('.mat-mdc-select-trigger') as HTMLElement).click();
    await settle();
  }

  const withStyle = (mode: string, profileId: string, language?: string): ScenarioConfiguration =>
    ({
      ...DEFAULTS,
      output: { story: true, illustration: false, writingStyle: { mode, profileId }, language },
    }) as ScenarioConfiguration;

  afterEach(() => {
    http.match(() => true);
  });

  // ---- FR-63: writing style group ----

  describe('desktop', () => {
    beforeEach(() => setup(false));

    // @trace FR-63
    it('the Output section holds Story, Illustration, the Writing Style section and the language field, in that order', async () => {
      await startLoaded();
      const section = byId('output-section')!;
      const order = [
        'output-story',
        'output-illustration',
        'writing-style-section',
        'story-language-select',
      ].map((id) => {
        const node = byId(id);
        expect(node, id).not.toBeNull();
        expect(section.contains(node), `${id} inside output-section`).toBe(true);
        return node!;
      });
      for (let i = 1; i < order.length; i++) {
        expect(
          order[i - 1].compareDocumentPosition(order[i]) & Node.DOCUMENT_POSITION_FOLLOWING,
        ).toBeTruthy();
      }
    });

    // @trace FR-63
    it('shows the "Writing Style" sub-heading and a radio group with Official / Briefing and Narrative', async () => {
      await startLoaded();
      expect(text('writing-style-section')).toContain('Writing Style');
      const group = byId('writing-style-group')!;
      expect(group.getAttribute('aria-label')).toBe('Writing Style');
      expect(group.getAttribute('role')).toBe('radiogroup');
      expect(text('writing-style-option-OFFICIAL')).toBe('Official / Briefing');
      expect(text('writing-style-option-NARRATIVE')).toBe('Narrative');
      const options = Array.from(group.querySelectorAll('mat-radio-button')).map((b) =>
        b.getAttribute('data-testid'),
      );
      expect(options).toEqual(['writing-style-option-OFFICIAL', 'writing-style-option-NARRATIVE']);
    });

    // @trace FR-63
    it('a fresh session has OFFICIAL checked and no narrative-style-section in the DOM', async () => {
      await startLoaded();
      expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-OFFICIAL']);
      expect(count('narrative-style-section')).toBe(0);
      expect(count('narrative-style-group')).toBe(0);
      expect(styles().writingStyle()).toEqual({ mode: 'OFFICIAL', profileId: 'official-briefing' });
      expect(store().configuration().output).toEqual(PANEL_OUTPUT);
    });

    // @trace FR-63
    it('selecting NARRATIVE shows "Narrative Style" with the 8 profiles in API order and neutral-narrative selected', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      expect(count('narrative-style-section')).toBe(1);
      expect(text('narrative-style-section')).toContain('Narrative Style');
      const group = byId('narrative-style-group')!;
      expect(group.getAttribute('aria-label')).toBe('Narrative Style');
      const ids = Array.from(group.querySelectorAll('mat-radio-button')).map((b) =>
        b.getAttribute('data-testid'),
      );
      expect(ids).toEqual(NARRATIVE_IDS.map((id) => `narrative-style-option-${id}`));
      expect(ids.length).toBe(8);
      expect(NARRATIVE_IDS.map((id) => text(`narrative-style-option-${id}`))).toEqual(
        NARRATIVE_LABELS,
      );
      expect(checkedIn('narrative-style-group')).toEqual([
        'narrative-style-option-neutral-narrative',
      ]);
      expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-NARRATIVE']);
      expect(styles().writingStyle()).toEqual({
        mode: 'NARRATIVE',
        profileId: 'neutral-narrative',
      });
      expect(count('narrative-style-select')).toBe(0);
    });

    // @trace FR-63
    it('selecting another profile selects only it and writes it to the store', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await chooseProfile('kafka');
      expect(checkedIn('narrative-style-group')).toEqual(['narrative-style-option-kafka']);
      expect(styles().writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'kafka' });
      expect(store().configuration().output.writingStyle).toEqual({
        mode: 'NARRATIVE',
        profileId: 'kafka',
      });
    });

    // @trace FR-63
    it('selecting OFFICIAL again removes the section and sets official-briefing; NARRATIVE starts at neutral-narrative again', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await chooseProfile('orwell');
      await chooseMode('OFFICIAL');
      expect(count('narrative-style-section')).toBe(0);
      expect(count('narrative-style-group')).toBe(0);
      expect(styles().writingStyle()).toEqual({ mode: 'OFFICIAL', profileId: 'official-briefing' });
      expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-OFFICIAL']);
      await chooseMode('NARRATIVE');
      expect(styles().writingStyle()).toEqual({
        mode: 'NARRATIVE',
        profileId: 'neutral-narrative',
      });
      expect(checkedIn('narrative-style-group')).toEqual([
        'narrative-style-option-neutral-narrative',
      ]);
    });

    // Ranges & invariants: every NARRATIVE profile; exactly one checked radio per visible group, equal to the store.
    // @trace FR-63
    it.each(NARRATIVE_IDS)(
      'choosing %s: one checked radio in each visible group, equal to the store',
      async (id) => {
        await startLoaded();
        await chooseMode('NARRATIVE');
        await chooseProfile(id);
        expect(checkedIn('narrative-style-group')).toEqual([`narrative-style-option-${id}`]);
        expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-NARRATIVE']);
        expect(styles().writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: id });
        expect(count('narrative-style-section') === 1).toBe(
          styles().writingStyle().mode === 'NARRATIVE',
        );
      },
    );

    // @trace FR-63
    it('the panel shows the stored NARRATIVE / kafka choice of the loaded configuration', async () => {
      await startLoaded(withStyle('NARRATIVE', 'kafka', 'uk'));
      expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-NARRATIVE']);
      expect(checkedIn('narrative-style-group')).toEqual(['narrative-style-option-kafka']);
    });

    // @trace FR-63
    it.each([
      ['a profile that is not in the catalogue', withStyle('NARRATIVE', 'dickens')],
      ['a profile of the other mode', withStyle('OFFICIAL', 'kafka')],
      ['no style', DEFAULTS],
    ])(
      'a loaded configuration with %s shows OFFICIAL and no narrative section',
      async (_name, config) => {
        await startLoaded(config);
        expect(checkedIn('writing-style-group')).toEqual(['writing-style-option-OFFICIAL']);
        expect(count('narrative-style-section')).toBe(0);
        expect(styles().writingStyle()).toEqual({
          mode: 'OFFICIAL',
          profileId: 'official-briefing',
        });
      },
    );

    // @trace FR-63
    it('the labels come from the catalogue (nothing hard-coded except the two sub-headings)', async () => {
      TestBed.resetTestingModule();
      setup(false, {
        ...WRITING_STYLES,
        modes: [
          {
            id: 'OFFICIAL',
            label: 'Report mode',
            isDefault: true,
            defaultProfileId: 'official-briefing',
          },
          {
            id: 'NARRATIVE',
            label: 'Prose mode',
            isDefault: false,
            defaultProfileId: 'neutral-narrative',
          },
        ],
        profiles: SHIPPED_PROFILES.map((p) => ({
          ...p,
          label: p.id === 'poe' ? 'Raven prose' : p.label,
        })),
        languages: [
          { code: 'en', label: 'Anglais', isDefault: true },
          { code: 'uk', label: 'Ukrainien', isDefault: false },
        ],
      });
      await startLoaded();
      expect(text('writing-style-option-OFFICIAL')).toBe('Report mode');
      expect(text('writing-style-option-NARRATIVE')).toBe('Prose mode');
      await chooseMode('NARRATIVE');
      expect(text('narrative-style-option-poe')).toBe('Raven prose');
      expect(text('story-language-select')).toContain('Anglais');
      await openSelect('story-language-select');
      expect((overlay('story-language-option-uk')?.textContent ?? '').trim()).toBe('Ukrainien');
    });

    // @trace FR-63
    it('shows only radios on desktop, never the mobile select', async () => {
      await startLoaded(withStyle('NARRATIVE', 'poe', 'en'));
      expect(count('narrative-style-group')).toBe(1);
      expect(count('narrative-style-select')).toBe(0);
    });

    // @trace FR-63
    it('a style change leaves realism, darkness, optimism, horizon, wildcards, story, illustration and language unchanged', async () => {
      await startLoaded({
        ...DEFAULTS,
        darkness: 9,
        wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 7 }],
      });
      await openSelect('story-language-select');
      overlay('story-language-option-uk')!.click();
      await settle();
      const before = store().configuration();
      await chooseMode('NARRATIVE');
      await chooseProfile('clarke');
      const after = store().configuration();
      expect({ ...after, output: { ...after.output, writingStyle: undefined } }).toEqual({
        ...before,
        output: { ...before.output, writingStyle: undefined },
      });
      expect(after.output.writingStyle).toEqual({ mode: 'NARRATIVE', profileId: 'clarke' });
    });

    // ---- FR-64: language select ----

    // @trace FR-64
    it('shows a "Story language" field with a select that reads English on a fresh session', async () => {
      await startLoaded();
      const select = byId('story-language-select')!;
      expect(select.tagName.toLowerCase()).toBe('mat-select');
      expect(select.closest('mat-form-field')).not.toBeNull();
      expect(select.closest('mat-form-field')?.textContent ?? '').toContain('Story language');
      expect(text('story-language-select')).toContain('English');
      expect(text('story-language-select')).not.toContain('Ukrainian');
      expect(styles().language()).toBe('en');
    });

    // @trace FR-64
    it('the overlay holds exactly the two options English and Ukrainian, in that order', async () => {
      await startLoaded();
      await openSelect('story-language-select');
      const options = Array.from(
        document.querySelectorAll('[data-testid^="story-language-option-"]'),
      );
      expect(options.map((o) => o.getAttribute('data-testid'))).toEqual([
        'story-language-option-en',
        'story-language-option-uk',
      ]);
      expect(options.map((o) => (o.textContent ?? '').trim())).toEqual(['English', 'Ukrainian']);
    });

    // Ranges & invariants: the select shows exactly one of English / Ukrainian, equal to the store's language.
    // @trace FR-64
    it.each([
      ['uk', 'Ukrainian'],
      ['en', 'English'],
    ])('choosing %s sets the store language and the select shows %s', async (code, label) => {
      await startLoaded(withStyle('OFFICIAL', 'official-briefing', code === 'uk' ? 'en' : 'uk'));
      await openSelect('story-language-select');
      overlay(`story-language-option-${code}`)!.click();
      await settle();
      expect(styles().language()).toBe(code);
      expect(store().configuration().output.language).toBe(code);
      expect(text('story-language-select')).toContain(label);
      expect(text('story-language-select')).not.toContain(
        label === 'English' ? 'Ukrainian' : 'English',
      );
    });

    // @trace FR-64
    it.each([
      ['uk', 'Ukrainian'],
      ['en', 'English'],
      ['de', 'English'],
    ])('a loaded configuration with language %s shows %s', async (language, label) => {
      await startLoaded(withStyle('OFFICIAL', 'official-briefing', language));
      expect(text('story-language-select')).toContain(label);
      expect(styles().language()).toBe(label === 'English' ? 'en' : 'uk');
    });

    // @trace FR-64
    it('a language change leaves the style and every other configuration field unchanged', async () => {
      await startLoaded(withStyle('NARRATIVE', 'verne', 'en'));
      const before = store().configuration();
      await openSelect('story-language-select');
      overlay('story-language-option-uk')!.click();
      await settle();
      expect(store().configuration()).toEqual({
        ...before,
        output: { ...before.output, language: 'uk' },
      });
      expect(checkedIn('narrative-style-group')).toEqual(['narrative-style-option-verne']);
    });

    // ---- the GENERATE body ----

    // @trace FR-63, FR-64
    it('GENERATE sends the chosen style and language in output', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await chooseProfile('kafka');
      await openSelect('story-language-select');
      overlay('story-language-option-uk')!.click();
      await settle();
      connected();
      await settle();
      (byId('generate-button') as HTMLButtonElement).click();
      const post = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/runs'));
      expect((post.request.body as ScenarioConfiguration).output).toEqual(
        panelOutput('NARRATIVE', 'kafka', 'uk'),
      );
    });

    // @trace FR-63, FR-64
    it('GENERATE with untouched controls sends Official / Briefing and en', async () => {
      await startLoaded();
      connected();
      await settle();
      (byId('generate-button') as HTMLButtonElement).click();
      const post = http.expectOne((r) => r.method === 'POST' && r.url.endsWith('/api/runs'));
      expect((post.request.body as ScenarioConfiguration).output).toEqual(PANEL_OUTPUT);
    });

    // ---- loading / error ----

    // @trace FR-63
    it('shows backend-unavailable with a retry when only the writing styles request fails', async () => {
      fixture.detectChanges();
      http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush(CATALOGUE);
      http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush(DEFAULTS);
      http
        .expectOne((r) => r.url.endsWith('/api/writing-styles'))
        .flush('', { status: 500, statusText: 'Server Error' });
      await settle();
      expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
      expect(text('backend-retry')).toBe('Try again');
      expect(byId('scenario-panel')).toBeNull();
      expect(byId('writing-style-section')).toBeNull();
    });
  });

  // ---- mobile: dropdown ----

  describe('mobile (phone width)', () => {
    beforeEach(() => setup(true));

    // @trace FR-63
    it('NARRATIVE shows the narrative-style-select dropdown with the selected profile label and no radio group', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      expect(count('narrative-style-section')).toBe(1);
      expect(count('narrative-style-group')).toBe(0);
      const select = byId('narrative-style-select')!;
      expect(select.tagName.toLowerCase()).toBe('mat-select');
      expect(select.getAttribute('aria-label')).toBe('Narrative Style');
      expect(text('narrative-style-select')).toContain('Neutral Narrative');
      expect(text('narrative-style-section')).toContain('Narrative Style');
    });

    // @trace FR-63
    it('OFFICIAL has neither the select nor the group', async () => {
      await startLoaded();
      expect(count('narrative-style-section')).toBe(0);
      expect(count('narrative-style-select')).toBe(0);
      expect(count('narrative-style-group')).toBe(0);
    });

    // @trace FR-63
    it('the dropdown lists the 8 profiles in catalogue order with their labels', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await openSelect('narrative-style-select');
      const options = Array.from(
        document.querySelectorAll('[data-testid^="narrative-style-select-option-"]'),
      );
      expect(options.map((o) => o.getAttribute('data-testid'))).toEqual(
        NARRATIVE_IDS.map((id) => `narrative-style-select-option-${id}`),
      );
      expect(options.map((o) => (o.textContent ?? '').trim())).toEqual(NARRATIVE_LABELS);
    });

    // @trace FR-63
    it('choosing narrative-style-select-option-kafka shows its label and writes it to the store', async () => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await openSelect('narrative-style-select');
      overlay('narrative-style-select-option-kafka')!.click();
      await settle();
      expect(text('narrative-style-select')).toContain('Narrative — inspired by Franz Kafka');
      expect(styles().writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'kafka' });
      expect(count('narrative-style-group')).toBe(0);
    });

    // Ranges & invariants: every profile can be chosen from the dropdown.
    // @trace FR-63
    it.each(NARRATIVE_IDS)('the dropdown selects %s', async (id) => {
      await startLoaded();
      await chooseMode('NARRATIVE');
      await openSelect('narrative-style-select');
      overlay(`narrative-style-select-option-${id}`)!.click();
      await settle();
      expect(styles().writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: id });
      const label = SHIPPED_PROFILES.find((p) => p.id === id)!.label;
      expect(text('narrative-style-select')).toContain(label);
    });

    // @trace FR-63
    it('a breakpoint change keeps the selected profile and swaps the radios for the select and back', async () => {
      await startLoaded(withStyle('NARRATIVE', 'wells', 'en'));
      expect(text('narrative-style-select')).toContain('Narrative — inspired by H. G. Wells');
      subject.next(state(false));
      await settle();
      expect(count('narrative-style-select')).toBe(0);
      expect(checkedIn('narrative-style-group')).toEqual(['narrative-style-option-wells']);
      subject.next(state(true));
      await settle();
      expect(count('narrative-style-group')).toBe(0);
      expect(text('narrative-style-select')).toContain('Narrative — inspired by H. G. Wells');
      expect(styles().writingStyle()).toEqual({ mode: 'NARRATIVE', profileId: 'wells' });
    });

    // @trace FR-64
    it('the language select works on a phone and reads Ukrainian after choosing it', async () => {
      await startLoaded();
      await openSelect('story-language-select');
      overlay('story-language-option-uk')!.click();
      await settle();
      expect(text('story-language-select')).toContain('Ukrainian');
      expect(styles().language()).toBe('uk');
    });
  });
});
