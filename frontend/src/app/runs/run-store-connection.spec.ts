// @trace FR-40
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { routes } from '../app.routes';
import { RunStore } from './run.store';

const PARENT_ID = '11111111-2222-3333-4444-555555555555';
const C: ScenarioConfiguration = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
  customWildcards: [],
  output: { story: true, illustration: false },
};

describe('slice 02_plan-usage-calls: starting a run reloads the connection for the new codes', () => {
  let http: HttpTestingController;
  let store: RunStore;

  const isRunsPost = (r: { url: string; method: string }): boolean => r.method === 'POST' && r.url.endsWith('/api/runs');
  const isAlternativePost = (r: { url: string; method: string }): boolean =>
    r.method === 'POST' && r.url.endsWith(`/api/runs/${PARENT_ID}/alternatives`);
  const isConnectionGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection');
  const snackText = (): string =>
    (document.querySelector('[data-testid="run-error-message"]')?.textContent ?? '').trim();

  async function settle(): Promise<void> {
    await Promise.resolve();
    await new Promise((r) => setTimeout(r, 0));
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(RunStore);
  });

  afterEach(() => {
    http.match(() => true);
    TestBed.inject(MatSnackBar).dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
  });

  // @trace FR-40
  it('startRun: CHATGPT_REGISTRATION_INVALID shows its message and reloads the connection once', async () => {
    store.start(C);
    http
      .expectOne(isRunsPost)
      .flush(
        { code: 'CHATGPT_REGISTRATION_INVALID', message: 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await settle();
    expect(snackText()).toBe('ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect');
    expect(http.match(isConnectionGet)).toHaveLength(1);
  });

  // @trace FR-40
  it('startAlternativeRun: CHATGPT_REGISTRATION_INVALID shows its message and reloads the connection once', async () => {
    store.startAlternative(PARENT_ID);
    http
      .expectOne(isAlternativePost)
      .flush(
        { code: 'CHATGPT_REGISTRATION_INVALID', message: 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect' },
        { status: 401, statusText: 'Unauthorized' },
      );
    await settle();
    expect(snackText()).toBe('ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect');
    expect(http.match(isConnectionGet)).toHaveLength(1);
  });

  // @trace FR-40
  it('startRun: a 503 CHATGPT_UNAVAILABLE shows its message and does not reload the connection', async () => {
    store.start(C);
    http
      .expectOne(isRunsPost)
      .flush(
        { code: 'CHATGPT_UNAVAILABLE', message: 'ChatGPT is temporarily unavailable — try again in a few minutes' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await settle();
    expect(snackText()).toBe('ChatGPT is temporarily unavailable — try again in a few minutes');
    expect(http.match(isConnectionGet)).toHaveLength(0);
  });

  // @trace FR-40
  it('startAlternativeRun: a 503 CHATGPT_UNAVAILABLE shows its message and does not reload the connection', async () => {
    store.startAlternative(PARENT_ID);
    http
      .expectOne(isAlternativePost)
      .flush(
        { code: 'CHATGPT_UNAVAILABLE', message: 'ChatGPT is temporarily unavailable — try again in a few minutes' },
        { status: 503, statusText: 'Service Unavailable' },
      );
    await settle();
    expect(snackText()).toBe('ChatGPT is temporarily unavailable — try again in a few minutes');
    expect(http.match(isConnectionGet)).toHaveLength(0);
  });

  // @trace FR-40
  it.each([
    ['CHATGPT_NOT_CONNECTED', 401, 'Connect ChatGPT to generate', 1],
    ['CHATGPT_SESSION_EXPIRED', 401, 'ChatGPT session expired — please reconnect', 1],
    ['CHATGPT_PLAN_NOT_ELIGIBLE', 403, 'Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed', 1],
    ['CHATGPT_REGISTRATION_INVALID', 401, 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect', 1],
    ['CHATGPT_UNAVAILABLE', 503, 'ChatGPT is temporarily unavailable — try again in a few minutes', 0],
    ['RUN_ALREADY_ACTIVE', 409, 'A generation is already running', 0],
  ])('startRun error %s (%i): %s -> connection loads: %i', async (code, status, message, loads) => {
    store.start(C);
    http.expectOne(isRunsPost).flush({ code, message }, { status: status as number, statusText: 'Error' });
    await settle();
    expect(snackText()).toBe(message);
    expect(http.match(isConnectionGet)).toHaveLength(loads as number);
  });

  // @trace FR-40
  it('a FAILED run with CHATGPT_REGISTRATION_INVALID reloads the connection once when it is opened', async () => {
    store.open(PARENT_ID);
    http.expectOne((r) => r.method === 'GET' && r.url.endsWith(`/api/runs/${PARENT_ID}`)).flush({
      id: PARENT_ID,
      generationId: 'ORC-2026-10-04-1000',
      kind: 'STANDARD',
      status: 'FAILED',
      stage: 'UNDERSTANDING_REQUEST',
      stageIndex: 1,
      stageCount: 10,
      configuration: C,
      counts: { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 },
      createdAt: '2026-10-04T10:00:00Z',
      updatedAt: '2026-10-04T10:00:05Z',
      failure: { code: 'CHATGPT_REGISTRATION_INVALID', message: 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect' },
    });
    await settle();
    expect(http.match(isConnectionGet)).toHaveLength(1);
  });
});
