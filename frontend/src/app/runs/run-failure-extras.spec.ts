// @trace FR-39
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import type { GenerationRun } from '../api/models/generation-run';
import { routes } from '../app.routes';

const ID = '11111111-2222-3333-4444-555555555555';
const CONFIG = {
  realism: 8,
  darkness: 9,
  optimism: 2,
  horizon: '5y',
  wildcards: [{ wildcardId: 'biology-new-pandemic', intensity: 8 }],
  customWildcards: [],
  output: { story: true, illustration: false },
};
const COUNTS = { searches: 0, articlesRetrieved: 0, articlesConsidered: 0, uniqueEvents: 0, eventsSelected: 0, counterSignals: 0, sourcesUsed: 0 };

/** Every RunFailureCode that is shown by the failure view (INSUFFICIENT_EVIDENCE has its own view) with its fixed message. */
const CODES: [string, string][] = [
  ['NEWS_UNAVAILABLE', 'ORACUL could not reach its news sources — try again later'],
  ['CHATGPT_RATE_LIMITED', 'ChatGPT usage limit reached — try again later'],
  ['CHATGPT_UNAVAILABLE', 'ChatGPT is temporarily unavailable — try again in a few minutes'],
  ['CHATGPT_SESSION_EXPIRED', 'ChatGPT session expired — please reconnect'],
  ['CHATGPT_PLAN_NOT_ELIGIBLE', 'Your ChatGPT plan is not eligible for ORACUL — a personal Plus or Pro plan is needed'],
  ['CHATGPT_REGISTRATION_INVALID', 'ChatGPT registration is no longer valid — use Reset ChatGPT connection, then reconnect'],
  ['CHATGPT_INCOMPLETE', 'ChatGPT did not finish the answer — please try again'],
  ['CHATGPT_REQUEST_REJECTED', "ChatGPT rejected ORACUL's request — please report this"],
  ['CHATGPT_UNEXPECTED_ERROR', 'ChatGPT returned an unexpected error (weird_new_code) — please try again'],
  ['CHATGPT_NO_MODEL', 'ChatGPT offers no model for this account — check your plan, then try again'],
  ['RUN_TIMEOUT', 'Generation took too long — try again'],
  ['INVALID_SCENARIO', 'ORACUL could not construct a valid scenario'],
  ['SCENARIO_REJECTED', 'ORACUL could not construct a scenario supported by current evidence'],
  ['ALTERNATIVE_NOT_DISTINCT', 'ORACUL could not find a different future — try changing a setting'],
  ['RUN_INTERRUPTED', 'Generation was interrupted — try again'],
  ['INTERNAL_ERROR', 'Something went wrong — try again'],
];
const WITH_PROVIDER_CODE = ['CHATGPT_REQUEST_REJECTED', 'CHATGPT_UNEXPECTED_ERROR'];
const RELOADS_CONNECTION = ['CHATGPT_SESSION_EXPIRED', 'CHATGPT_REGISTRATION_INVALID'];

function failedRun(code: string | null, message?: string, providerCode?: string): GenerationRun {
  return {
    id: ID,
    generationId: 'ORC-2026-10-04-1000',
    kind: 'STANDARD',
    status: 'FAILED',
    stage: 'SEARCHING',
    stageLabel: 'Searching current events…',
    stageIndex: 3,
    stageCount: 10,
    configuration: CONFIG,
    counts: COUNTS,
    createdAt: '2026-10-04T10:00:00Z',
    updatedAt: '2026-10-04T10:00:05Z',
    completedAt: '2026-10-04T10:00:05Z',
    ...(code ? { failure: { code, message, ...(providerCode ? { providerCode } : {}) } } : {}),
  } as unknown as GenerationRun;
}

describe('slice 02_plan-usage-calls: failure view extras', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  const isRunGet = (r: { url: string; method: string }): boolean => r.method === 'GET' && r.url.endsWith(`/api/runs/${ID}`);
  const isConnectionGet = (r: { url: string; method: string }): boolean =>
    r.method === 'GET' && r.url.endsWith('/api/auth/chatgpt/connection');
  const el = (): HTMLElement => harness.routeNativeElement as HTMLElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();

  async function show(run: GenerationRun): Promise<void> {
    harness = await RouterTestingHarness.create(`/futures/${ID}`);
    http.expectOne(isRunGet).flush(run);
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()] });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.match(() => true);
  });

  // @trace FR-39
  it.each(CODES)('%s: the usage link exists only for CHATGPT_RATE_LIMITED', async (code, message) => {
    await show(failedRun(code, message, WITH_PROVIDER_CODE.includes(code) ? 'weird_new_code' : undefined));
    if (code === 'CHATGPT_RATE_LIMITED') {
      const link = byId('failure-usage-link') as HTMLAnchorElement;
      expect(link).not.toBeNull();
      expect((link.textContent ?? '').trim()).toBe('Open ChatGPT Settings → Usage');
      expect(link.getAttribute('href')).toBe('https://chatgpt.com/#settings/Usage');
      expect(link.getAttribute('target')).toBe('_blank');
      expect(link.getAttribute('rel')).toBe('noopener noreferrer');
    } else {
      expect(byId('failure-usage-link')).toBeNull();
    }
  });

  // @trace FR-39, FR-40
  it.each(CODES)('%s: the reconnect action exists only for CHATGPT_SESSION_EXPIRED', async (code, message) => {
    await show(failedRun(code, message, WITH_PROVIDER_CODE.includes(code) ? 'weird_new_code' : undefined));
    http.match(isConnectionGet);
    if (code === 'CHATGPT_SESSION_EXPIRED') {
      const link = byId('failure-reconnect') as HTMLAnchorElement;
      expect(link).not.toBeNull();
      expect((link.textContent ?? '').trim()).toBe('Continue with ChatGPT');
      expect(link.getAttribute('href')).toBe('/api/auth/chatgpt/authorize');
    } else {
      expect(byId('failure-reconnect')).toBeNull();
    }
  });

  // @trace FR-39
  it.each(CODES)('%s: the provider code line exists only for the two codes that carry a providerCode', async (code, message) => {
    await show(failedRun(code, message, 'weird_new_code'));
    http.match(isConnectionGet);
    if (WITH_PROVIDER_CODE.includes(code)) {
      expect(text('failure-provider-code')).toBe('Error code: weird_new_code');
    } else {
      expect(byId('failure-provider-code')).toBeNull();
    }
  });

  // @trace FR-39
  it.each(WITH_PROVIDER_CODE)('%s without a providerCode shows no provider code line', async (code) => {
    await show(failedRun(code, 'ChatGPT rejected ORACUL\'s request — please report this'));
    expect(byId('failure-provider-code')).toBeNull();
    expect(text('failure-message')).toBe('ChatGPT rejected ORACUL\'s request — please report this');
  });

  // @trace FR-39
  it.each([
    ['subscription_sharing_route_not_supported'],
    ['http_403'],
    ['unknown_error'],
    ['A.b:c-d_9'],
  ])('the provider code %s is shown verbatim', async (providerCode) => {
    await show(failedRun('CHATGPT_REQUEST_REJECTED', "ChatGPT rejected ORACUL's request — please report this", providerCode));
    expect(text('failure-provider-code')).toBe(`Error code: ${providerCode}`);
  });

  // @trace FR-39
  it.each(CODES)('%s: failure-message and try-again are always there with the message of the run', async (code, message) => {
    await show(failedRun(code, message, WITH_PROVIDER_CODE.includes(code) ? 'weird_new_code' : undefined));
    http.match(isConnectionGet);
    expect(byId('failure-view')).not.toBeNull();
    expect(text('failure-message')).toBe(message);
    expect(text('try-again')).toBe('Try again');
  });

  // @trace FR-39
  it.each(CODES)('%s: the extras sit between failure-message and try-again', async (code, message) => {
    await show(failedRun(code, message, WITH_PROVIDER_CODE.includes(code) ? 'weird_new_code' : undefined));
    http.match(isConnectionGet);
    const order = Array.from(el().querySelectorAll('[data-testid="failure-view"] [data-testid]')).map((n) => n.getAttribute('data-testid'));
    expect(order[0]).toBe('failure-message');
    expect(order[order.length - 1]).toBe('try-again');
    const extras = order.slice(1, -1);
    for (const e of extras) expect(['failure-usage-link', 'failure-reconnect', 'failure-provider-code']).toContain(e);
    const expected =
      code === 'CHATGPT_RATE_LIMITED' ? ['failure-usage-link']
      : code === 'CHATGPT_SESSION_EXPIRED' ? ['failure-reconnect']
      : WITH_PROVIDER_CODE.includes(code) ? ['failure-provider-code']
      : [];
    expect(extras).toEqual(expected);
  });

  // @trace FR-39
  it('a FAILED run without a failure keeps the phase-01 generic message and no extras', async () => {
    await show(failedRun(null));
    expect(text('failure-message')).toBe('Something went wrong — try again');
    for (const id of ['failure-usage-link', 'failure-reconnect', 'failure-provider-code']) expect(byId(id)).toBeNull();
    expect(http.match(isConnectionGet)).toHaveLength(0);
  });

  // @trace FR-39, FR-40
  it.each(CODES)('%s: the connection is reloaded exactly once for CHATGPT_SESSION_EXPIRED and CHATGPT_REGISTRATION_INVALID, never otherwise', async (code, message) => {
    await show(failedRun(code, message, WITH_PROVIDER_CODE.includes(code) ? 'weird_new_code' : undefined));
    const loads = http.match(isConnectionGet);
    expect(loads).toHaveLength(RELOADS_CONNECTION.includes(code) ? 1 : 0);
    // re-rendering does not load it again
    harness.detectChanges();
    await harness.fixture.whenStable();
    harness.detectChanges();
    expect(http.match(isConnectionGet)).toHaveLength(0);
  });

  // @trace FR-39
  it('the failure view of a rate limit never shows the code, a token, JSON or the run id', async () => {
    await show(failedRun('CHATGPT_RATE_LIMITED', 'ChatGPT usage limit reached — try again later'));
    const content = byId('failure-view')?.textContent ?? '';
    for (const forbidden of ['CHATGPT_RATE_LIMITED', ID, '{', '}', 'Bearer']) expect(content).not.toContain(forbidden);
  });
});
