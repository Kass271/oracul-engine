import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { App } from '../app';
import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';

const DEFAULTS: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

const TABLE: [string, string, [string, string][]][] = [
  ['ai', 'AI', [['ai-agi-breakthrough', 'AGI breakthrough'], ['ai-stagnation', 'AI stagnation'], ['ai-loss-of-control', 'AI loss of control']]],
  ['robotics', 'Robotics', [['robotics-massive-automation', 'Massive automation'], ['robotics-humanoid-boom', 'Humanoid robot boom'], ['robotics-robot-uprising', 'Robot uprising']]],
  ['biology', 'Biology', [['biology-new-pandemic', 'New pandemic'], ['biology-dangerous-mutation', 'Dangerous mutation'], ['biology-medical-breakthrough', 'Major medical breakthrough'], ['biology-synthetic-biology', 'Synthetic biology breakthrough']]],
  ['political', 'Political / institutional', [['political-democracy-strengthens', 'Democratic institutions strengthen'], ['political-authoritarian-expansion', 'Authoritarian systems expand'], ['political-international-institutions', 'International institutions strengthen'], ['political-global-fragmentation', 'Global fragmentation increases']]],
  ['economy', 'Economy', [['economy-global-boom', 'Global economic boom'], ['economy-global-recession', 'Global recession'], ['economy-financial-crisis', 'Financial crisis']]],
  ['energy', 'Energy', [['energy-fusion-breakthrough', 'Fusion breakthrough'], ['energy-cheap-energy', 'Cheap energy'], ['energy-energy-crisis', 'Energy crisis']]],
  ['environment', 'Environment', [['environment-extreme-climate-event', 'Extreme climate event'], ['environment-climate-stabilization', 'Climate stabilization'], ['environment-ecosystem-collapse', 'Ecosystem collapse']]],
  ['space', 'Space', [['space-major-discovery', 'Major space discovery'], ['space-asteroid-threat', 'Asteroid threat'], ['space-moon-settlement', 'Moon settlement'], ['space-mars-breakthrough', 'Mars breakthrough']]],
  ['extreme', 'Extreme speculation', [['extreme-alien-contact', 'Alien contact'], ['extreme-unknown-intelligence', 'Unknown intelligence'], ['extreme-unexplained-phenomenon', 'Unexplained global phenomenon']]],
];

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
  categories: TABLE.map(([id, label, wcs]) => ({
    id,
    label,
    wildcards: wcs.map(([wid, wlabel]) => ({ id: wid, label: wlabel, categoryId: id })),
  })),
  defaults: DEFAULTS,
  limits: {
    intensityMin: 1,
    intensityMax: 10,
    customWildcardMax: 3,
    customWildcardLabelMaxLength: 40,
    defaultWildcardIntensity: 5,
  },
};

const ALL = TABLE.flatMap(([, , w]) => w);
const PANDEMIC = 'biology-new-pandemic';

describe('Wildcard catalogue in the Scenario Panel (slice 03_wildcards)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const sw = (id: string): HTMLElement | null =>
    byId(`wildcard-toggle-${id}`)?.querySelector('button[role="switch"]') ?? null;

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

  async function toggle(id: string): Promise<void> {
    sw(id)?.click();
    await settle();
  }

  async function setIntensity(id: string, value: number): Promise<void> {
    const input = byId(`wildcard-intensity-${id}-input`) as HTMLInputElement;
    input.value = String(value);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
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

  // @trace FR-4
  it('shows a WILDCARDS heading after TIME HORIZON with a section container', async () => {
    await startLoaded();
    const headings = Array.from(el().querySelectorAll('h2.section')).map((h) => h.textContent?.trim());
    expect(headings).toContain('WILDCARDS');
    expect(headings.indexOf('WILDCARDS')).toBeGreaterThan(headings.indexOf('TIME HORIZON'));
    expect(byId('wildcard-section')).not.toBeNull();
  });

  // @trace FR-4
  it('renders one collapsed expansion panel per category in catalogue order with its label', async () => {
    await startLoaded();
    const panels = Array.from(
      el().querySelectorAll('[data-testid="wildcard-section"] mat-expansion-panel'),
    ) as HTMLElement[];
    expect(panels.map((p) => p.getAttribute('data-testid'))).toEqual(
      TABLE.map(([id]) => `wildcard-category-${id}`),
    );
    for (const [id, label] of TABLE) {
      expect(byId(`wildcard-category-${id}`)?.classList.contains('mat-expanded')).toBe(false);
      expect(text(`wildcard-category-header-${id}`)).toContain(label);
    }
  });

  // @trace FR-4
  it('expands a category when its header is clicked, several may be open at once', async () => {
    await startLoaded();
    (byId('wildcard-category-header-biology') as HTMLElement).click();
    await settle();
    (byId('wildcard-category-header-political') as HTMLElement).click();
    await settle();
    expect(byId('wildcard-category-biology')?.classList.contains('mat-expanded')).toBe(true);
    expect(byId('wildcard-category-political')?.classList.contains('mat-expanded')).toBe(true);
    expect(byId('wildcard-category-ai')?.classList.contains('mat-expanded')).toBe(false);
  });

  // @trace FR-4
  it('groups every wildcard under its category in catalogue order', async () => {
    await startLoaded();
    for (const [cid, , wcs] of TABLE) {
      const panel = byId(`wildcard-category-${cid}`) as HTMLElement;
      const ids = Array.from(panel.querySelectorAll('[data-testid^="wildcard-toggle-"]')).map((n) =>
        n.getAttribute('data-testid'),
      );
      expect(ids).toEqual(wcs.map(([wid]) => `wildcard-toggle-${wid}`));
    }
  });

  // @trace FR-4
  it('fresh session: all 30 switches are off, no sliders, labels show only the label', async () => {
    await startLoaded();
    for (const [id, label] of ALL) {
      expect(sw(id)?.getAttribute('aria-checked')).toBe('false');
      expect(byId(`wildcard-toggle-${id}`)?.getAttribute('aria-label') ?? sw(id)?.getAttribute('aria-label')).toBe(label);
      expect(text(`wildcard-label-${id}`)).toBe(label);
    }
    expect(el().querySelectorAll('[data-testid^="wildcard-intensity-"]').length).toBe(0);
  });

  // @trace FR-4
  it('enabling a wildcard shows its slider at 5 and the label "<label> 5/10"', async () => {
    await startLoaded();
    await toggle(PANDEMIC);
    expect(sw(PANDEMIC)?.getAttribute('aria-checked')).toBe('true');
    expect(byId(`wildcard-intensity-${PANDEMIC}`)).not.toBeNull();
    const input = byId(`wildcard-intensity-${PANDEMIC}-input`) as HTMLInputElement;
    expect(input.min).toBe('1');
    expect(input.max).toBe('10');
    expect(input.step).toBe('1');
    expect(input.value).toBe('5');
    expect(input.getAttribute('aria-label')).toBe('New pandemic intensity');
    expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic 5/10');
  });

  // @trace FR-4
  it('moving the slider to 8 shows "New pandemic 8/10" and leaves other wildcards alone', async () => {
    await startLoaded();
    await toggle(PANDEMIC);
    await setIntensity(PANDEMIC, 8);
    expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic 8/10');
    expect(text('wildcard-label-robotics-humanoid-boom')).toBe('Humanoid robot boom');
    expect(sw('robotics-humanoid-boom')?.getAttribute('aria-checked')).toBe('false');
  });

  // @trace FR-4
  it('disabling removes the slider and the intensity; re-enabling starts again at 5', async () => {
    await startLoaded();
    await toggle(PANDEMIC);
    await setIntensity(PANDEMIC, 8);
    await toggle(PANDEMIC);
    expect(sw(PANDEMIC)?.getAttribute('aria-checked')).toBe('false');
    expect(byId(`wildcard-intensity-${PANDEMIC}`)).toBeNull();
    expect(byId(`wildcard-intensity-${PANDEMIC}-input`)).toBeNull();
    expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic');
    await toggle(PANDEMIC);
    expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic 5/10');
    expect((byId(`wildcard-intensity-${PANDEMIC}-input`) as HTMLInputElement).value).toBe('5');
  });

  // @trace FR-4
  it('loads wildcards of the current configuration as enabled rows with their intensities', async () => {
    await startLoaded({
      ...DEFAULTS,
      wildcards: [
        { wildcardId: PANDEMIC, intensity: 8 },
        { wildcardId: 'robotics-humanoid-boom', intensity: 6 },
      ],
    });
    expect(sw(PANDEMIC)?.getAttribute('aria-checked')).toBe('true');
    expect(text(`wildcard-label-${PANDEMIC}`)).toBe('New pandemic 8/10');
    expect((byId(`wildcard-intensity-${PANDEMIC}-input`) as HTMLInputElement).value).toBe('8');
    expect(text('wildcard-label-robotics-humanoid-boom')).toBe('Humanoid robot boom 6/10');
    expect(sw('ai-stagnation')?.getAttribute('aria-checked')).toBe('false');
  });
});
