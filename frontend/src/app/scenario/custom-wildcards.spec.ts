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
  horizons: [
    { code: '1d', label: 'Tomorrow' },
    { code: '1w', label: '1 week' },
    { code: '1m', label: '1 month' },
    { code: '1y', label: '1 year' },
    { code: '5y', label: '5 years' },
    { code: '10y', label: '10 years' },
    { code: '20y', label: '20 years' },
  ],
  categories: [
    {
      id: 'biology',
      label: 'Biology',
      wildcards: [{ id: 'biology-new-pandemic', label: 'New pandemic', categoryId: 'biology' }],
    },
  ],
  defaults: DEFAULTS,
  limits: {
    intensityMin: 1,
    intensityMax: 10,
    customWildcardMax: 3,
    customWildcardLabelMaxLength: 40,
    defaultWildcardIntensity: 5,
  },
};

const NAME = 'Wildcard name must be 1–40 characters';
const MAX = 'At most 3 custom wildcards';
const DUP = 'This wildcard already exists';

describe('Custom wildcards in the Scenario Panel (slice 18_custom-wildcards-output)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const rows = (): HTMLElement[] =>
    Array.from(el().querySelectorAll('[data-testid^="custom-wildcard-"]')).filter((e) =>
      /^custom-wildcard-\d+$/.test(e.getAttribute('data-testid') ?? ''),
    ) as HTMLElement[];
  const input = (): HTMLInputElement => {
    const host = byId('custom-wildcard-input') as HTMLElement;
    return (host instanceof HTMLInputElement ? host : host.querySelector('input')) as HTMLInputElement;
  };

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

  async function type(value: string): Promise<void> {
    const i = input();
    i.value = value;
    i.dispatchEvent(new Event('input', { bubbles: true }));
    await settle();
  }

  async function add(value: string): Promise<void> {
    await type(value);
    (byId('custom-wildcard-add') as HTMLElement).click();
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

  // @trace FR-5
  it('shows the custom section inside WILDCARDS with input and an always-enabled Add button', async () => {
    await startLoaded();
    expect(byId('custom-wildcard-section')).not.toBeNull();
    expect(input()).not.toBeNull();
    expect(input().hasAttribute('maxlength')).toBe(false);
    expect(input().getAttribute('aria-label')).toBe('Custom wildcard name');
    expect(input().getAttribute('placeholder')).toBe('e.g. Ocean desalination boom');
    expect(text('custom-wildcard-add')).toBe('Add');
    expect((byId('custom-wildcard-add')?.closest('button') as HTMLButtonElement).disabled).toBe(false);
    expect(byId('custom-wildcard-error')).toBeNull();
    expect(rows().length).toBe(0);
    const sections = Array.from(el().querySelectorAll('[data-testid="wildcard-section"], [data-testid="custom-wildcard-section"]'));
    expect(sections.map((s) => s.getAttribute('data-testid'))).toEqual(['wildcard-section', 'custom-wildcard-section']);
  });

  // @trace FR-5
  it('Add appends a row "<label> 5/10", clears the input, keeps the slider at 5', async () => {
    await startLoaded();
    await add('  Ocean desalination boom ');
    expect(rows().length).toBe(1);
    expect(text('custom-wildcard-label-0')).toBe('Ocean desalination boom 5/10');
    expect(input().value).toBe('');
    expect(byId('custom-wildcard-error')).toBeNull();
    const thumb = byId('custom-wildcard-intensity-0-input') as HTMLInputElement;
    expect(thumb.value).toBe('5');
    expect(thumb.getAttribute('aria-label')).toBe('Ocean desalination boom intensity');
    expect(byId('custom-wildcard-remove-0')?.closest('button')?.getAttribute('aria-label')).toBe(
      'Remove Ocean desalination boom',
    );
    expect(TestBed.inject(ScenarioStore).configuration().customWildcards).toEqual([
      { label: 'Ocean desalination boom', intensity: 5 },
    ]);
  });

  // @trace FR-5
  it('the slider changes the intensity shown and stored', async () => {
    await startLoaded();
    await add('Mars colony');
    const thumb = byId('custom-wildcard-intensity-0-input') as HTMLInputElement;
    thumb.value = '8';
    thumb.dispatchEvent(new Event('input', { bubbles: true }));
    thumb.dispatchEvent(new Event('change', { bubbles: true }));
    await settle();
    expect(text('custom-wildcard-label-0')).toBe('Mars colony 8/10');
    expect(TestBed.inject(ScenarioStore).configuration().customWildcards).toEqual([
      { label: 'Mars colony', intensity: 8 },
    ]);
  });

  // @trace FR-5
  it.each([
    ['', NAME],
    ['   ', NAME],
    ['a'.repeat(41), NAME],
  ])('rejects %j with the name error and keeps the input text', async (value, message) => {
    await startLoaded();
    await add(value);
    expect(text('custom-wildcard-error')).toBe(message);
    expect(rows().length).toBe(0);
    expect(input().value).toBe(value);
  });

  // @trace FR-5
  it('accepts exactly 40 characters', async () => {
    await startLoaded();
    await add('a'.repeat(40));
    expect(byId('custom-wildcard-error')).toBeNull();
    expect(text('custom-wildcard-label-0')).toBe(`${'a'.repeat(40)} 5/10`);
  });

  // @trace FR-5
  it('rejects a case-insensitive duplicate, accepts a catalogue label', async () => {
    await startLoaded();
    await add('Mars colony');
    await add('mars colony');
    expect(text('custom-wildcard-error')).toBe(DUP);
    expect(rows().length).toBe(1);
    await add('New pandemic');
    expect(byId('custom-wildcard-error')).toBeNull();
    expect(rows().length).toBe(2);
  });

  // @trace FR-5
  it('a 4th Add shows the limit error with any input, nothing added; Add stays enabled', async () => {
    await startLoaded();
    for (const l of ['A', 'B', 'C']) await add(l);
    expect(rows().length).toBe(3);
    for (const v of ['Fourth', '', 'A']) {
      await add(v);
      expect(text('custom-wildcard-error')).toBe(MAX);
      expect(rows().length).toBe(3);
      expect((byId('custom-wildcard-add')?.closest('button') as HTMLButtonElement).disabled).toBe(false);
    }
  });

  // @trace FR-5
  it('typing clears the error', async () => {
    await startLoaded();
    await add('');
    expect(byId('custom-wildcard-error')).not.toBeNull();
    await type('x');
    expect(byId('custom-wildcard-error')).toBeNull();
  });

  // @trace FR-5
  it('a successful Add clears an earlier error', async () => {
    await startLoaded();
    await add('');
    await type('Fine');
    (byId('custom-wildcard-add') as HTMLElement).click();
    await settle();
    expect(byId('custom-wildcard-error')).toBeNull();
    expect(rows().length).toBe(1);
  });

  // @trace FR-5
  it('removing a row renumbers the rest, frees a slot and clears the error', async () => {
    await startLoaded();
    for (const l of ['A', 'B', 'C']) await add(l);
    await add('D');
    expect(text('custom-wildcard-error')).toBe(MAX);
    (byId('custom-wildcard-remove-0')?.closest('button') as HTMLElement).click();
    await settle();
    expect(rows().length).toBe(2);
    expect(text('custom-wildcard-label-0')).toBe('B 5/10');
    expect(text('custom-wildcard-label-1')).toBe('C 5/10');
    expect(byId('custom-wildcard-label-2')).toBeNull();
    expect(byId('custom-wildcard-error')).toBeNull();
    await add('D');
    expect(rows().length).toBe(3);
    expect(text('custom-wildcard-label-2')).toBe('D 5/10');
  });

  // @trace FR-5
  it('rows shown always equal configuration().customWildcards for 0..3 items', async () => {
    await startLoaded();
    const store = TestBed.inject(ScenarioStore);
    for (const l of ['A', 'B', 'C']) {
      await add(l);
      const cfg = store.configuration().customWildcards;
      expect(rows().length).toBe(cfg.length);
      cfg.forEach((c, i) => expect(text(`custom-wildcard-label-${i}`)).toBe(`${c.label} ${c.intensity}/10`));
    }
  });

  // @trace FR-5
  it('a loaded configuration renders its custom wildcards with their intensities', async () => {
    await startLoaded({
      ...DEFAULTS,
      customWildcards: [
        { label: 'Mars colony', intensity: 8 },
        { label: 'Fusion towns', intensity: 2 },
      ],
    });
    expect(rows().length).toBe(2);
    expect(text('custom-wildcard-label-0')).toBe('Mars colony 8/10');
    expect(text('custom-wildcard-label-1')).toBe('Fusion towns 2/10');
    expect((byId('custom-wildcard-intensity-1-input') as HTMLInputElement).value).toBe('2');
  });
});
