import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from '../app';
import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
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

describe('Output settings in the Scenario Panel (slice 18_custom-wildcards-output)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const box = (id: string): HTMLInputElement =>
    byId(id)?.querySelector('input[type="checkbox"]') as HTMLInputElement;

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  async function startLoaded(config: ScenarioConfiguration = DEFAULTS): Promise<void> {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush(CATALOGUE);
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush(config);
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
  });

  // @trace FR-6
  it('shows an OUTPUT heading after WILDCARDS with the output section', async () => {
    await startLoaded();
    const headings = Array.from(el().querySelectorAll('h2.section')).map((h) => h.textContent?.trim());
    expect(headings).toContain('OUTPUT');
    expect(headings.indexOf('OUTPUT')).toBeGreaterThan(headings.indexOf('WILDCARDS'));
    expect(byId('output-section')).not.toBeNull();
  });

  // @trace FR-6
  it('Story is checked and enabled, Illustration unchecked and disabled with the MVP+1 badge', async () => {
    await startLoaded();
    expect(byId('output-story')?.textContent).toContain('Story');
    expect(byId('output-illustration')?.textContent).toContain('Illustration');
    expect(box('output-story').checked).toBe(true);
    expect(box('output-story').disabled).toBe(false);
    expect(box('output-illustration').checked).toBe(false);
    expect(box('output-illustration').disabled).toBe(true);
    expect((byId('output-illustration-badge')?.textContent ?? '').trim()).toBe('MVP+1');
  });

  // @trace FR-6
  it('clicks never change the checkboxes or the store output (repeated)', async () => {
    await startLoaded();
    const store = TestBed.inject(ScenarioStore);
    for (let i = 0; i < 3; i++) {
      box('output-story').click();
      await settle();
      box('output-illustration').click();
      (byId('output-illustration') as HTMLElement).click();
      await settle();
      expect(box('output-story').checked).toBe(true);
      expect(box('output-illustration').checked).toBe(false);
      expect(box('output-illustration').disabled).toBe(true);
      expect(store.configuration().output).toEqual({ story: true, illustration: false });
    }
  });

  // @trace FR-6
  it('a loaded configuration with other output values still shows Story checked / Illustration unchecked + disabled', async () => {
    await startLoaded({ ...DEFAULTS, output: { story: false, illustration: true } });
    expect(box('output-story').checked).toBe(true);
    expect(box('output-illustration').checked).toBe(false);
    expect(box('output-illustration').disabled).toBe(true);
    expect(TestBed.inject(ScenarioStore).configuration().output).toEqual({ story: false, illustration: true });
  });
});
