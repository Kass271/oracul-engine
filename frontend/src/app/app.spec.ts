import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from './app';
import type { ScenarioCatalogue } from './api/models/scenario-catalogue';
import type { ScenarioConfiguration } from './api/models/scenario-configuration';

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
  horizons: [
    { code: '1d', label: 'Tomorrow' },
    { code: '1w', label: '1 week' },
    { code: '1m', label: '1 month' },
    { code: '1y', label: '1 year' },
    { code: '5y', label: '5 years' },
    { code: '10y', label: '10 years' },
    { code: '20y', label: '20 years' },
  ],
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

const CODES = ['1d', '1w', '1m', '1y', '5y', '10y', '20y'];

describe('App shell (slice 01_scenario-controls)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function flushCatalogue(): void {
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush(CATALOGUE);
  }
  function flushConfiguration(config: ScenarioConfiguration = DEFAULTS): void {
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush(config);
  }

  async function startLoaded(config: ScenarioConfiguration = DEFAULTS): Promise<void> {
    fixture.detectChanges();
    flushCatalogue();
    flushConfiguration(config);
    await settle();
  }

  function setSlider(name: string, value: number): void {
    const input = byId(`slider-${name}-input`) as HTMLInputElement;
    input.value = String(value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    fixture = TestBed.createComponent(App);
    http = TestBed.inject(HttpTestingController);
  });

  // ---- FR-1 ----

  // @trace FR-1
  it('shows app-loading and no panel while catalogue and configuration are pending', () => {
    fixture.detectChanges();
    expect(http.match((r) => r.url.endsWith('/api/scenario/catalogue')).length).toBe(1);
    expect(http.match((r) => r.url.endsWith('/api/scenario/configuration')).length).toBe(1);
    expect(byId('app-loading')).not.toBeNull();
    expect(byId('scenario-panel')).toBeNull();
    expect(byId('welcome-view')).toBeNull();
  });

  // @trace FR-1
  it('keeps the panel absent until both calls have arrived', async () => {
    fixture.detectChanges();
    flushCatalogue();
    await settle();
    expect(byId('app-loading')).not.toBeNull();
    expect(byId('scenario-panel')).toBeNull();
    flushConfiguration();
    await settle();
    expect(byId('app-loading')).toBeNull();
    expect(byId('scenario-panel')).not.toBeNull();
  });

  // @trace FR-1
  it('renders header wordmark, welcome view and a disabled generate button once loaded', async () => {
    await startLoaded();
    expect(byId('app-header')).not.toBeNull();
    expect(text('app-wordmark')).toBe('ORACUL');
    expect(byId('app-header')!.contains(byId('app-wordmark'))).toBe(true);
    expect(byId('welcome-view')).not.toBeNull();
    expect(text('welcome-question')).toBe('What happens next?');
    expect(text('generate-button')).toBe('GENERATE THE FUTURE');
    expect((byId('generate-button') as HTMLButtonElement).disabled).toBe(true);
    expect(text('generate-hint')).toBe('Connect ChatGPT to generate');
    expect(byId('scenario-panel')).not.toBeNull();
  });

  // @trace FR-1
  it('never writes the word "Oracle" in text, title, aria-label or alt', async () => {
    await startLoaded();
    expect((el().textContent ?? '').toLowerCase()).not.toContain('oracle');
    for (const attr of ['title', 'aria-label', 'alt']) {
      el()
        .querySelectorAll(`[${attr}]`)
        .forEach((n) => expect((n.getAttribute(attr) ?? '').toLowerCase()).not.toContain('oracle'));
    }
  });

  // @trace FR-1
  it('shows backend-unavailable when the catalogue answers 503', async () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush('', { status: 503, statusText: 'Unavailable' });
    flushConfiguration();
    await settle();
    expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
    expect(text('backend-retry')).toBe('Try again');
    expect(byId('scenario-panel')).toBeNull();
    expect(byId('welcome-view')).toBeNull();
    expect(byId('app-loading')).toBeNull();
    expect(text('app-wordmark')).toBe('ORACUL');
  });

  // @trace FR-1
  it('shows backend-unavailable on a 500 from the configuration call', async () => {
    fixture.detectChanges();
    flushCatalogue();
    http
      .expectOne((r) => r.url.endsWith('/api/scenario/configuration'))
      .flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong' }, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
    expect(el().textContent).not.toContain('INTERNAL_ERROR');
    expect(byId('scenario-panel')).toBeNull();
  });

  // @trace FR-1
  it('shows backend-unavailable on a network error', async () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).error(new ProgressEvent('error'));
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).error(new ProgressEvent('error'));
    await settle();
    expect(text('backend-unavailable')).toContain('ORACUL is unavailable — try again shortly');
    expect(byId('scenario-panel')).toBeNull();
  });

  // @trace FR-1
  it('retry shows app-loading, repeats both calls and renders the panel on success', async () => {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush('', { status: 502, statusText: 'Bad Gateway' });
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush('', { status: 502, statusText: 'Bad Gateway' });
    await settle();
    expect(byId('backend-retry')).not.toBeNull();

    (byId('backend-retry') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(byId('app-loading')).not.toBeNull();
    expect(byId('backend-unavailable')).toBeNull();

    flushCatalogue();
    flushConfiguration();
    await settle();
    expect(byId('backend-unavailable')).toBeNull();
    expect(byId('app-loading')).toBeNull();
    expect(byId('scenario-panel')).not.toBeNull();
    expect(byId('welcome-view')).not.toBeNull();
  });

  // ---- FR-2 ----

  // @trace FR-2
  it('renders INTENSITY sliders in order Darkness, Optimism, Realism with default values', async () => {
    await startLoaded();
    const labels = ['darkness', 'optimism', 'realism'].map((n) => byId(`label-${n}`)!);
    expect(labels.map((l) => (l.textContent ?? '').trim().startsWith(['Darkness', 'Optimism', 'Realism'][labels.indexOf(l)]))).toEqual([true, true, true]);
    const [a, b, c] = ['slider-darkness', 'slider-optimism', 'slider-realism'].map((id) => byId(id)!);
    expect(a.compareDocumentPosition(b) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(b.compareDocumentPosition(c) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(el().textContent).toContain('INTENSITY');
    expect(text('value-darkness')).toBe('5');
    expect(text('value-optimism')).toBe('5');
    expect(text('value-realism')).toBe('8');
    expect((byId('slider-darkness-input') as HTMLInputElement).value).toBe('5');
    expect((byId('slider-optimism-input') as HTMLInputElement).value).toBe('5');
    expect((byId('slider-realism-input') as HTMLInputElement).value).toBe('8');
  });

  // @trace FR-2
  it('configures each slider as discrete 1..10 step 1 with an accessible name', async () => {
    await startLoaded();
    for (const [n, name] of [['darkness', 'Darkness'], ['optimism', 'Optimism'], ['realism', 'Realism']]) {
      const input = byId(`slider-${n}-input`) as HTMLInputElement;
      expect(input.getAttribute('aria-label')).toBe(name);
      expect(input.min).toBe('1');
      expect(input.max).toBe('10');
      expect(input.step).toBe('1');
      expect(byId(`slider-${n}`)!.classList.contains('mdc-slider--discrete')).toBe(true);
    }
  });

  // @trace FR-2
  it('initial slider values come from the loaded configuration', async () => {
    await startLoaded({ ...DEFAULTS, darkness: 9, optimism: 2, realism: 3 });
    expect(text('value-darkness')).toBe('9');
    expect(text('value-optimism')).toBe('2');
    expect(text('value-realism')).toBe('3');
  });

  // @trace FR-2
  it('changing darkness and optimism never changes the other sliders', async () => {
    await startLoaded();
    setSlider('darkness', 9);
    await settle();
    expect(text('value-darkness')).toBe('9');
    expect(text('value-optimism')).toBe('5');
    expect(text('value-realism')).toBe('8');

    setSlider('optimism', 9);
    await settle();
    expect(text('value-darkness')).toBe('9');
    expect(text('value-optimism')).toBe('9');
    expect(text('value-realism')).toBe('8');
  });

  // ---- FR-3 ----

  // @trace FR-3
  it('renders the TIME HORIZON group with 7 single-select options in order', async () => {
    await startLoaded();
    expect(el().textContent).toContain('TIME HORIZON');
    const group = byId('horizon-group')!;
    expect(group.getAttribute('aria-label')).toBe('Time Horizon');
    const options = CODES.map((c) => byId(`horizon-option-${c}`));
    options.forEach((o) => expect(o).not.toBeNull());
    expect(options.map((o) => (o!.textContent ?? '').trim())).toEqual([
      'Tomorrow', '1 week', '1 month', '1 year', '5 years', '10 years', '20 years',
    ]);
    for (let i = 1; i < options.length; i++) {
      expect(options[i - 1]!.compareDocumentPosition(options[i]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    }
  });

  // @trace FR-3
  it('selects exactly "1 year" on a fresh session', async () => {
    await startLoaded();
    const checked = CODES.filter((c) => byId(`horizon-option-${c}`)!.classList.contains('mat-button-toggle-checked'));
    expect(checked).toEqual(['1y']);
  });

  // @trace FR-3
  it('clicking 5 years selects only 5 years', async () => {
    await startLoaded();
    (byId('horizon-option-5y')!.querySelector('button') as HTMLButtonElement).click();
    await settle();
    const checked = CODES.filter((c) => byId(`horizon-option-${c}`)!.classList.contains('mat-button-toggle-checked'));
    expect(checked).toEqual(['5y']);
  });

  // @trace FR-3
  it('clicking the already selected option keeps it selected', async () => {
    await startLoaded();
    (byId('horizon-option-1y')!.querySelector('button') as HTMLButtonElement).click();
    await settle();
    const checked = CODES.filter((c) => byId(`horizon-option-${c}`)!.classList.contains('mat-button-toggle-checked'));
    expect(checked).toEqual(['1y']);
  });

  // @trace FR-3
  it('horizon selection does not change the sliders', async () => {
    await startLoaded();
    (byId('horizon-option-20y')!.querySelector('button') as HTMLButtonElement).click();
    await settle();
    expect(text('value-darkness')).toBe('5');
    expect(text('value-optimism')).toBe('5');
    expect(text('value-realism')).toBe('8');
  });
});
