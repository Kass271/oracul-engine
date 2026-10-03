// @trace FR-33
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatMenuTrigger } from '@angular/material/menu';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import type { RecentRunSummary } from '../api/models/recent-run-summary';
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

const HORIZONS: [string, string][] = [
  ['1d', 'Tomorrow'],
  ['1w', '1 week'],
  ['1m', '1 month'],
  ['1y', '1 year'],
  ['5y', '5 years'],
  ['10y', '10 years'],
  ['20y', '20 years'],
];

const CATALOGUE: ScenarioCatalogue = {
  horizons: HORIZONS.map(([code, label]) => ({ code, label })),
  categories: [],
  defaults: DEFAULTS,
  limits: { intensityMin: 1, intensityMax: 10, customWildcardMax: 3, customWildcardLabelMaxLength: 40, defaultWildcardIntensity: 5 },
} as ScenarioCatalogue;

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const pad = (n: number): string => String(n).padStart(2, '0');
const idOf = (i: number): string => `00000000-0000-0000-0000-${String(i).padStart(12, '0')}`;

function item(i: number, over: Partial<RecentRunSummary> = {}, cfg: Partial<ScenarioConfiguration> = {}): RecentRunSummary {
  return {
    id: idOf(i),
    generationId: `ORC-2026-10-02-${1000 + i}`,
    kind: 'STANDARD',
    createdAt: new Date(2026, 0, 1, 9, 5).toISOString(),
    completedAt: new Date(2026, 0, 1, 9, 6).toISOString(),
    headline: `Headline ${i}`,
    configuration: { ...DEFAULTS, ...cfg },
    ...over,
  } as RecentRunSummary;
}

describe('slice 15_recent-futures: Recent futures menu', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;

  const isList = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith('/api/runs');
  const byId = (id: string): HTMLElement | null => document.querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').replace(/\s+/g, ' ').trim();
  const entries = (): HTMLElement[] => Array.from(document.querySelectorAll<HTMLElement>('[data-testid^="recent-future-"]'))
    .filter((e) => /^recent-future-[0-9a-f-]{36}$/.test(e.getAttribute('data-testid') ?? ''));
  const trigger = (): MatMenuTrigger =>
    fixture.debugElement.query(By.css('[data-testid="recent-futures-button"]')).injector.get(MatMenuTrigger);

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
    http.match((r) => r.url.endsWith('/api/scenario/configuration')).forEach((r) => r.flush(DEFAULTS));
    http.match((r) => r.url.endsWith('/api/auth/chatgpt/connection')).forEach((r) => r.flush({ state: 'CONNECTED', canGenerate: true }));
    await settle();
  }

  async function open(): Promise<TestRequest> {
    (fixture.nativeElement.querySelector('[data-testid="recent-futures-button"]') as HTMLButtonElement).click();
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
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
  });

  it('has the button between wordmark and ChatGPT status and sends no list request before the click', async () => {
    await boot();
    const button = fixture.nativeElement.querySelector('[data-testid="recent-futures-button"]') as HTMLElement;
    expect(button).not.toBeNull();
    expect((button.textContent ?? '').trim()).toBe('Recent futures');
    const wordmark = fixture.nativeElement.querySelector('[data-testid="app-wordmark"]') as HTMLElement;
    const status = fixture.nativeElement.querySelector('[data-testid="chatgpt-status"]') as HTMLElement;
    expect(wordmark.compareDocumentPosition(button) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(button.compareDocumentPosition(status) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(http.match(isList)).toHaveLength(0);
  });

  it('shows only the loading state while the request is pending', async () => {
    await boot();
    await open();
    expect(byId('recent-futures-loading')).not.toBeNull();
    expect(byId('recent-futures-list')).toBeNull();
    expect(byId('recent-futures-empty')).toBeNull();
    expect(byId('recent-futures-error')).toBeNull();
  });

  it('shows "No futures yet" for an empty list', async () => {
    await boot();
    await openWith([]);
    expect(text('recent-futures-empty')).toBe('No futures yet');
    expect(byId('recent-futures-loading')).toBeNull();
    expect(entries()).toHaveLength(0);
  });

  it.each([1, 2, 20])('shows exactly %i entries in API order', async (n) => {
    await boot();
    const items = Array.from({ length: n }, (_, k) => item(n - k));
    await openWith(items);
    expect(byId('recent-futures-list')).not.toBeNull();
    expect(byId('recent-futures-empty')).toBeNull();
    expect(byId('recent-futures-loading')).toBeNull();
    expect(entries().map((e) => e.getAttribute('data-testid'))).toEqual(items.map((i) => `recent-future-${i.id}`));
    for (const i of items) {
      expect(text(`recent-future-headline-${i.id}`)).toBe(i.headline);
      expect(byId(`recent-future-time-${i.id}`)).not.toBeNull();
      expect(byId(`recent-future-settings-${i.id}`)).not.toBeNull();
    }
  });

  it('shows HH:mm for a run created today in browser local time', async () => {
    await boot();
    const now = new Date();
    const created = new Date(now.getFullYear(), now.getMonth(), now.getDate(), 9, 5).toISOString();
    await openWith([item(1, { createdAt: created })]);
    expect(text(`recent-future-time-${idOf(1)}`)).toBe('09:05');
  });

  it('shows "d MMM yyyy, HH:mm" for yesterday', async () => {
    await boot();
    const now = new Date();
    const y = new Date(now.getFullYear(), now.getMonth(), now.getDate() - 1, 9, 5);
    await openWith([item(1, { createdAt: y.toISOString() })]);
    expect(text(`recent-future-time-${idOf(1)}`)).toBe(`${y.getDate()} ${MONTHS[y.getMonth()]} ${y.getFullYear()}, 09:05`);
  });

  it('shows "d MMM yyyy, HH:mm" for a day in another year', async () => {
    await boot();
    const d = new Date(2025, 9, 3, 18, 30);
    await openWith([item(1, { createdAt: d.toISOString() })]);
    expect(text(`recent-future-time-${idOf(1)}`)).toBe('3 Oct 2025, 18:30');
  });

  it.each(HORIZONS)('shows key settings for horizon %s as "%s"', async (code, label) => {
    await boot();
    await openWith([item(1, {}, { realism: 7, darkness: 4, optimism: 6, horizon: code as ScenarioConfiguration['horizon'] })]);
    expect(text(`recent-future-settings-${idOf(1)}`)).toBe(`R7 D4 O6 · ${label}`);
  });

  it.each([
    [1, 10, 1],
    [10, 1, 10],
  ])('shows the bounds R%i D%i O%i', async (r, d, o) => {
    await boot();
    await openWith([item(1, {}, { realism: r, darkness: d, optimism: o, horizon: '1d' })]);
    expect(text(`recent-future-settings-${idOf(1)}`)).toBe(`R${r} D${d} O${o} · Tomorrow`);
  });

  it('renders a headline as text, never as HTML', async () => {
    await boot();
    await openWith([item(1, { headline: '<b>x</b>' })]);
    const h = byId(`recent-future-headline-${idOf(1)}`)!;
    expect(h.textContent!.trim()).toBe('<b>x</b>');
    expect(h.querySelector('b')).toBeNull();
  });

  it.each([
    ['a network error', 0],
    ['a 500', 500],
  ])('shows "Recent futures are unavailable" on %s', async (_name, status) => {
    await boot();
    const req = await open();
    if (status === 0) req.error(new ProgressEvent('error'));
    else req.flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status, statusText: 'Server Error' });
    await settle();
    expect(text('recent-futures-error')).toBe('Recent futures are unavailable');
    expect(entries()).toHaveLength(0);
    expect(byId('recent-futures-loading')).toBeNull();
    expect(document.querySelector('.mat-mdc-snack-bar-container')).toBeNull();
  });

  it('fetches again every time the menu is opened and shows the new result', async () => {
    await boot();
    await openWith([item(1)]);
    expect(entries()).toHaveLength(1);
    trigger().closeMenu();
    await settle();
    await openWith([item(2), item(1)]);
    expect(entries().map((e) => e.getAttribute('data-testid'))).toEqual([`recent-future-${idOf(2)}`, `recent-future-${idOf(1)}`]);
  });

  it('ignores a response that arrives after the menu was reopened', async () => {
    await boot();
    const first = await open();
    trigger().closeMenu();
    await settle();
    const second = await open();
    second.flush({ items: [item(2)] });
    await settle();
    first.flush({ items: [item(1), item(3)] });
    await settle();
    expect(entries().map((e) => e.getAttribute('data-testid'))).toEqual([`recent-future-${idOf(2)}`]);
  });

  it('navigates to /futures/<id> and closes the menu when an entry is clicked', async () => {
    await boot();
    await openWith([item(2), item(1)]);
    byId(`recent-future-${idOf(1)}`)!.click();
    await settle();
    expect(TestBed.inject(Router).url).toBe(`/futures/${idOf(1)}`);
    expect(byId('recent-futures-list')).toBeNull();
    http.match(() => true);
  });
});
