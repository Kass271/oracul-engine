import { BreakpointState } from '@angular/cdk/layout';
import { BreakpointObserver } from '@angular/cdk/layout';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';

import { App } from './app';
import type { ScenarioCatalogue } from './api/models/scenario-catalogue';
import type { ScenarioConfiguration } from './api/models/scenario-configuration';
import { ScenarioStore } from './scenario/scenario.store';

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

describe('App mobile layout (slice 19_mobile-layout)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let subject: BehaviorSubject<BreakpointState>;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const state = (matches: boolean): BreakpointState => ({ matches, breakpoints: {} });

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function setup(mobile: boolean): void {
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
  }

  async function startLoaded(): Promise<void> {
    fixture.detectChanges();
    http.expectOne((r) => r.url.endsWith('/api/scenario/catalogue')).flush(CATALOGUE);
    http.expectOne((r) => r.url.endsWith('/api/scenario/configuration')).flush(DEFAULTS);
    await settle();
  }

  const drawerClasses = (): string[] => (byId('scenario-drawer')?.className ?? '').split(/\s+/);

  function setDarkness(n: number): void {
    const input = byId('slider-darkness-input') as HTMLInputElement;
    input.value = String(n);
    input.dispatchEvent(new Event('input', { bubbles: true }));
    input.dispatchEvent(new Event('change', { bubbles: true }));
  }

  // @trace FR-34
  it('desktop: side drawer, opened, no toggle and no close button', async () => {
    setup(false);
    await startLoaded();
    expect(drawerClasses()).toContain('mat-drawer-side');
    expect(drawerClasses()).toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')).toBeNull();
    expect(byId('scenario-drawer-close')).toBeNull();
  });

  // @trace FR-34
  it('mobile: over drawer, closed on load, toggle "Scenario" first in header before wordmark', async () => {
    setup(true);
    await startLoaded();
    expect(drawerClasses()).toContain('mat-drawer-over');
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
    const toggle = byId('scenario-drawer-toggle');
    expect(toggle).not.toBeNull();
    expect((toggle!.textContent ?? '').trim()).toBe('Scenario');
    expect(byId('app-header')!.contains(toggle)).toBe(true);
    const wordmark = byId('app-wordmark')!;
    expect(
      toggle!.compareDocumentPosition(wordmark) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
    expect(toggle!.getAttribute('aria-expanded')).toBe('false');
    expect(byId('scenario-panel')).not.toBeNull();
  });

  // @trace FR-34
  it('mobile while loading: no toggle and no drawer', () => {
    setup(true);
    fixture.detectChanges();
    expect(byId('scenario-drawer-toggle')).toBeNull();
    expect(byId('scenario-drawer')).toBeNull();
  });

  // @trace FR-34
  it('mobile on load error: no toggle and no drawer', async () => {
    setup(true);
    fixture.detectChanges();
    http
      .expectOne((r) => r.url.endsWith('/api/scenario/catalogue'))
      .flush('x', { status: 500, statusText: 'err' });
    http
      .match((r) => r.url.endsWith('/api/scenario/configuration'))
      .forEach((r) => r.flush('x', { status: 500, statusText: 'err' }));
    await settle();
    expect(byId('backend-unavailable')).not.toBeNull();
    expect(byId('scenario-drawer-toggle')).toBeNull();
    expect(byId('scenario-drawer')).toBeNull();
  });

  // @trace FR-34
  it('mobile: toggle opens, close button closes, aria-expanded follows', async () => {
    setup(true);
    await startLoaded();
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('true');
    const close = byId('scenario-drawer-close');
    expect(close).not.toBeNull();
    expect(close!.getAttribute('aria-label')).toBe('Close scenario panel');
    close!.click();
    await settle();
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('false');
  });

  // @trace FR-34
  it('mobile: darkness set while open survives close and reopen', async () => {
    setup(true);
    await startLoaded();
    const store = TestBed.inject(ScenarioStore);
    byId('scenario-drawer-toggle')!.click();
    await settle();
    setDarkness(9);
    await settle();
    byId('scenario-drawer-close')!.click();
    await settle();
    expect(store.darkness()).toBe(9);
    expect(store.configuration()).toEqual({ ...DEFAULTS, darkness: 9 });
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect((byId('value-darkness')!.textContent ?? '').trim()).toBe('9');
  });

  const pressEscape = (): void => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
  };

  // @trace FR-34
  it('mobile: Escape on document closes the open drawer and keeps values', async () => {
    setup(true);
    await startLoaded();
    const store = TestBed.inject(ScenarioStore);
    byId('scenario-drawer-toggle')!.click();
    await settle();
    setDarkness(9);
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    pressEscape();
    await settle();
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('false');
    expect(store.darkness()).toBe(9);
  });

  // @trace FR-34
  it('mobile: Escape with the drawer closed changes nothing', async () => {
    setup(true);
    await startLoaded();
    pressEscape();
    await settle();
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('false');
  });

  // @trace FR-34
  it('desktop: Escape leaves the side drawer open', async () => {
    setup(false);
    await startLoaded();
    pressEscape();
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    expect(drawerClasses()).toContain('mat-drawer-side');
  });

  async function expectClosedThenReopenable(): Promise<void> {
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('false');
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect(byId('scenario-drawer-toggle')!.getAttribute('aria-expanded')).toBe('true');
    expect(drawerClasses()).toContain('mat-drawer-opened');
  }

  // @trace FR-34
  it('mobile: backdrop click closes the drawer and one toggle click reopens it', async () => {
    setup(true);
    await startLoaded();
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    const backdrop = el().querySelector('.mat-drawer-backdrop') as HTMLElement;
    expect(backdrop).not.toBeNull();
    backdrop.click();
    await settle();
    await expectClosedThenReopenable();
  });

  // @trace FR-34
  it('mobile: Escape on the sidenav element closes it and one toggle click reopens it', async () => {
    setup(true);
    await startLoaded();
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    byId('scenario-drawer')!.dispatchEvent(
      new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true }),
    );
    await settle();
    await expectClosedThenReopenable();
  });

  // @trace FR-34
  it('crossing the breakpoint switches mode; entering mobile starts closed', async () => {
    setup(true);
    await startLoaded();
    byId('scenario-drawer-toggle')!.click();
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-opened');
    subject.next(state(false));
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-side');
    expect(drawerClasses()).toContain('mat-drawer-opened');
    expect(byId('scenario-drawer-toggle')).toBeNull();
    subject.next(state(true));
    await settle();
    expect(drawerClasses()).toContain('mat-drawer-over');
    expect(drawerClasses()).not.toContain('mat-drawer-opened');
  });
});
