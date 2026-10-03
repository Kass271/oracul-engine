// @trace FR-32
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Router, provideRouter } from '@angular/router';

import type { GenerationRun } from '../api/models/generation-run';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { RunFailure } from './run-failure';

const NEW_ID = '99999999-8888-7777-6666-555555555555';
const C: ScenarioConfiguration = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
  customWildcards: [],
  output: { story: true, illustration: false },
};
const MESSAGE = 'ChatGPT plan limit reached — try again later';

describe('slice 11_run-failures: RunFailure component', () => {
  let fixture: ComponentFixture<RunFailure>;
  let http: HttpTestingController;
  let router: Router;

  const byId = (id: string): HTMLElement | null => (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const isRunsPost = (r: { url: string; method: string }): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
  const button = (): HTMLButtonElement => byId('try-again') as HTMLButtonElement;

  function create(inputs: Record<string, unknown> = {}): void {
    fixture = TestBed.createComponent(RunFailure);
    for (const [k, v] of Object.entries(inputs)) fixture.componentRef.setInput(k, v);
    fixture.detectChanges();
  }

  function click(): void {
    button().click();
    fixture.detectChanges();
  }

  function accepted(): GenerationRun {
    return {
      id: NEW_ID,
      generationId: 'ORC-2026-10-03-1000',
      kind: 'STANDARD',
      status: 'QUEUED',
      stageIndex: 0,
      stageCount: 10,
      configuration: C,
      counts: { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 },
      createdAt: '2026-10-03T10:00:00Z',
      updatedAt: '2026-10-03T10:00:00Z',
    } as GenerationRun;
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    vi.restoreAllMocks();
  });

  // @trace FR-32
  it('default inputs show "Future not found" and Try again navigates to / without starting a run', () => {
    const nav = vi.spyOn(router, 'navigateByUrl').mockResolvedValue(true);
    create();
    expect(text('failure-message')).toBe('Future not found');
    expect(text('try-again')).toBe('Try again');
    click();
    expect(nav).toHaveBeenCalledWith('/');
    expect(http.match(isRunsPost)).toHaveLength(0);
  });

  // @trace FR-32
  it('with a configuration the message is exact and Try again re-submits that configuration once', () => {
    create({ message: MESSAGE, configuration: C });
    expect(text('failure-message')).toBe(MESSAGE);
    click();
    const req = http.expectOne(isRunsPost);
    expect(req.request.body).toEqual(C);
  });

  // @trace FR-32
  it('Try again is disabled while the start request is pending, then 202 navigates to the new run with replaceUrl', async () => {
    const nav = vi.spyOn(router, 'navigate').mockResolvedValue(true);
    create({ message: MESSAGE, configuration: C });
    click();
    expect(button().disabled).toBe(true);
    http.expectOne(isRunsPost).flush(accepted(), { status: 202, statusText: 'Accepted' });
    await fixture.whenStable();
    expect(nav).toHaveBeenCalledWith(['/futures', NEW_ID], { replaceUrl: true });
  });

  // @trace FR-32
  it('a 409 shows the API message, keeps the failure view and re-enables the button', async () => {
    create({ message: MESSAGE, configuration: C });
    click();
    http
      .expectOne(isRunsPost)
      .flush({ code: 'RUN_ALREADY_ACTIVE', message: 'A generation is already running' }, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    fixture.detectChanges();
    expect((document.querySelector('[data-testid="run-error-message"]')?.textContent ?? '').trim()).toBe('A generation is already running');
    expect(byId('failure-view')).not.toBeNull();
    expect(button().disabled).toBe(false);
  });

  // @trace FR-32
  it('a network error shows the generic message and keeps the failure view', async () => {
    create({ message: MESSAGE, configuration: C });
    click();
    http.expectOne(isRunsPost).error(new ProgressEvent('error'));
    await fixture.whenStable();
    fixture.detectChanges();
    expect((document.querySelector('[data-testid="run-error-message"]')?.textContent ?? '').trim()).toBe('Something went wrong — try again');
    expect(byId('failure-view')).not.toBeNull();
    expect(button().disabled).toBe(false);
  });
});
