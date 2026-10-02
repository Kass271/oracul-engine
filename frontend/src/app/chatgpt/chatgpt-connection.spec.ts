import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import type { ChatGptConnectionState } from '../api/models/chat-gpt-connection-state';

const CONNECTION_URL = '/api/auth/chatgpt/connection';

const CATALOGUE = {
  horizons: [{ code: '1y', label: '1 year' }],
  categories: [],
  defaults: {
    realism: 8,
    darkness: 5,
    optimism: 5,
    horizon: '1y',
    wildcards: [],
    customWildcards: [],
    output: { story: true, illustration: false },
  },
  limits: { intensityMin: 1, intensityMax: 10, customWildcardMax: 3, customWildcardLabelMaxLength: 40, defaultWildcardIntensity: 5 },
};

const STATUS_TEXT: Record<ChatGptConnectionState, string> = {
  NOT_CONNECTED: 'Not connected',
  CONNECTED: 'ChatGPT connected',
  PLAN_NOT_ELIGIBLE: 'Plan not eligible',
  SESSION_EXPIRED: 'Session expired',
};

describe('ChatGPT connection header (slice 02_chatgpt-connection)', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let router: Router;

  const el = (): HTMLElement => fixture.nativeElement;
  const byId = (id: string): HTMLElement | null => el().querySelector(`[data-testid="${id}"]`);
  const text = (id: string): string => (byId(id)?.textContent ?? '').trim();
  const snackbars = (): HTMLElement[] => Array.from(document.querySelectorAll<HTMLElement>('[data-testid="chatgpt-message"]'));

  const isConnection = (r: { url: string; method: string }, method = 'GET'): boolean =>
    r.url.endsWith(CONNECTION_URL) && r.method === method;

  async function settle(): Promise<void> {
    await fixture.whenStable();
    fixture.detectChanges();
    await fixture.whenStable();
  }

  function flushScenario(): void {
    http.match((r) => r.url.endsWith('/api/scenario/catalogue')).forEach((r) => r.flush(CATALOGUE));
    http.match((r) => r.url.endsWith('/api/scenario/configuration')).forEach((r) => r.flush(CATALOGUE.defaults));
  }

  /** Answers every pending GET connection request; at least one must be pending. */
  function flushConnection(state: ChatGptConnectionState): void {
    const pending = http.match((r) => isConnection(r));
    expect(pending.length).toBeGreaterThanOrEqual(1);
    pending.forEach((r) => r.flush({ state, canGenerate: state === 'CONNECTED' }));
  }

  async function startWith(state: ChatGptConnectionState): Promise<void> {
    fixture.detectChanges();
    flushScenario();
    flushConnection(state);
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    router = TestBed.inject(Router);
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
  });

  // ---- FR-8: status in the header ----

  // @trace FR-8
  it('requests the connection state on page load and shows "Checking…" without any action', () => {
    fixture.detectChanges();
    expect(http.match((r) => isConnection(r)).length).toBe(1);
    expect(byId('app-header')!.contains(byId('chatgpt-status'))).toBe(true);
    expect(text('chatgpt-status')).toBe('Checking…');
    expect(byId('chatgpt-connect')).toBeNull();
    expect(byId('chatgpt-disconnect')).toBeNull();
  });

  // @trace FR-8
  it('places the status to the right of the wordmark inside the toolbar', async () => {
    await startWith('NOT_CONNECTED');
    const header = byId('app-header')!;
    expect(header.contains(byId('app-wordmark'))).toBe(true);
    expect(header.contains(byId('chatgpt-status'))).toBe(true);
    expect(byId('app-wordmark')!.compareDocumentPosition(byId('chatgpt-status')!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    expect(byId('chatgpt-status')!.tagName).toBe('SPAN');
  });

  // @trace FR-8
  it('NOT_CONNECTED shows "Not connected" and "Continue with ChatGPT"', async () => {
    await startWith('NOT_CONNECTED');
    expect(text('chatgpt-status')).toBe(STATUS_TEXT.NOT_CONNECTED);
    expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    expect(byId('chatgpt-disconnect')).toBeNull();
  });

  // @trace FR-8
  it('CONNECTED shows "ChatGPT connected" and "Disconnect" only', async () => {
    await startWith('CONNECTED');
    expect(text('chatgpt-status')).toBe(STATUS_TEXT.CONNECTED);
    expect(text('chatgpt-disconnect')).toBe('Disconnect');
    expect((byId('chatgpt-disconnect') as HTMLButtonElement).disabled).toBe(false);
    expect(byId('chatgpt-connect')).toBeNull();
  });

  // @trace FR-8
  it('PLAN_NOT_ELIGIBLE shows "Plan not eligible" with the connect action', async () => {
    await startWith('PLAN_NOT_ELIGIBLE');
    expect(text('chatgpt-status')).toBe(STATUS_TEXT.PLAN_NOT_ELIGIBLE);
    expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    expect(byId('chatgpt-disconnect')).toBeNull();
  });

  // @trace FR-8
  it('SESSION_EXPIRED shows "Session expired" with the connect action', async () => {
    await startWith('SESSION_EXPIRED');
    expect(text('chatgpt-status')).toBe(STATUS_TEXT.SESSION_EXPIRED);
    expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    expect(byId('chatgpt-disconnect')).toBeNull();
  });

  // @trace FR-8
  it('shows the status even when the scenario backend is unavailable', async () => {
    fixture.detectChanges();
    http.match((r) => r.url.endsWith('/api/scenario/catalogue')).forEach((r) => r.flush('', { status: 503, statusText: 'Unavailable' }));
    http.match((r) => r.url.endsWith('/api/scenario/configuration')).forEach((r) => r.flush('', { status: 503, statusText: 'Unavailable' }));
    flushConnection('NOT_CONNECTED');
    await settle();
    expect(byId('backend-unavailable')).not.toBeNull();
    expect(text('chatgpt-status')).toBe('Not connected');
  });

  // @trace FR-8
  it('a failed connection load (500) shows "Not connected", offers connect and shows no snackbar', async () => {
    fixture.detectChanges();
    flushScenario();
    http.match((r) => isConnection(r)).forEach((r) =>
      r.flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status: 500, statusText: 'Server Error' }),
    );
    await settle();
    expect(text('chatgpt-status')).toBe('Not connected');
    expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    expect(snackbars().length).toBe(0);
  });

  // @trace FR-8
  it('a network error on the connection load shows "Not connected" without snackbar', async () => {
    fixture.detectChanges();
    flushScenario();
    http.match((r) => isConnection(r)).forEach((r) => r.error(new ProgressEvent('error')));
    await settle();
    expect(text('chatgpt-status')).toBe('Not connected');
    expect(byId('chatgpt-connect')).not.toBeNull();
    expect(snackbars().length).toBe(0);
  });

  // ---- FR-8: disconnect ----

  // @trace FR-8
  it('Disconnect sends DELETE, is disabled while in flight, then reloads and shows "Not connected"', async () => {
    await startWith('CONNECTED');
    (byId('chatgpt-disconnect') as HTMLButtonElement).click();
    fixture.detectChanges();
    const del: TestRequest = http.expectOne((r) => isConnection(r, 'DELETE'));
    expect((byId('chatgpt-disconnect') as HTMLButtonElement).disabled).toBe(true);
    del.flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    flushConnection('NOT_CONNECTED');
    await settle();
    expect(text('chatgpt-status')).toBe('Not connected');
    expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    expect(byId('chatgpt-disconnect')).toBeNull();
  });

  // @trace FR-8
  it('a failed DELETE shows "Something went wrong — try again" and keeps the connected state', async () => {
    await startWith('CONNECTED');
    (byId('chatgpt-disconnect') as HTMLButtonElement).click();
    fixture.detectChanges();
    http
      .expectOne((r) => isConnection(r, 'DELETE'))
      .flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status: 500, statusText: 'Server Error' });
    await settle();
    expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['Something went wrong — try again']);
    expect(text('chatgpt-status')).toBe('ChatGPT connected');
    expect(byId('chatgpt-disconnect')).not.toBeNull();
  });

  // @trace FR-8
  it('a DELETE network error shows the same snackbar and keeps the state', async () => {
    await startWith('CONNECTED');
    (byId('chatgpt-disconnect') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne((r) => isConnection(r, 'DELETE')).error(new ProgressEvent('error'));
    await settle();
    expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['Something went wrong — try again']);
    expect(text('chatgpt-status')).toBe('ChatGPT connected');
  });

  // ---- FR-7: sign-in entry and return ----

  // @trace FR-7
  it('"Continue with ChatGPT" is a plain link to the authorize endpoint', async () => {
    await startWith('NOT_CONNECTED');
    const link = byId('chatgpt-connect') as HTMLAnchorElement;
    expect(link.tagName).toBe('A');
    expect(link.getAttribute('href')).toBe('/api/auth/chatgpt/authorize');
  });

  // @trace FR-7
  it('never renders a password or API key input in any state', async () => {
    for (const state of Object.keys(STATUS_TEXT) as ChatGptConnectionState[]) {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [App],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
      });
      http = TestBed.inject(HttpTestingController);
      fixture = TestBed.createComponent(App);
      await startWith(state);
      expect(el().querySelectorAll('input[type=password]').length).toBe(0);
      expect(byId('chatgpt-status')).not.toBeNull();
    }
  });

  // @trace FR-7, FR-9
  it('writes nothing to localStorage, sessionStorage or IndexedDB-backed keys', async () => {
    localStorage.clear();
    sessionStorage.clear();
    await startWith('CONNECTED');
    (byId('chatgpt-disconnect') as HTMLButtonElement).click();
    fixture.detectChanges();
    http.expectOne((r) => isConnection(r, 'DELETE')).flush(null, { status: 204, statusText: 'No Content' });
    await settle();
    flushConnection('NOT_CONNECTED');
    await settle();
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
    expect(text('chatgpt-status')).toBe('Not connected');
  });

  describe('return from OpenAI (?chatgpt=)', () => {
    async function startAfterReturn(outcome: string, state: ChatGptConnectionState): Promise<void> {
      await router.navigateByUrl(`/?chatgpt=${outcome}`);
      await startWith(state);
    }

    // @trace FR-7
    it('connected: snackbar "ChatGPT connected", parameter removed, connection reloaded', async () => {
      await startAfterReturn('connected', 'CONNECTED');
      expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['ChatGPT connected']);
      expect(router.url).toBe('/');
      expect(text('chatgpt-status')).toBe('ChatGPT connected');
      expect(text('chatgpt-disconnect')).toBe('Disconnect');
    });

    // @trace FR-7
    it('not_completed: snackbar "ChatGPT connection was not completed", header stays "Not connected"', async () => {
      await startAfterReturn('not_completed', 'NOT_CONNECTED');
      expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['ChatGPT connection was not completed']);
      expect(router.url).toBe('/');
      expect(text('chatgpt-status')).toBe('Not connected');
      expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
    });

    // @trace FR-7, FR-8
    it('not_eligible: snackbar "Your ChatGPT plan is not eligible for ORACUL" and "Plan not eligible"', async () => {
      await startAfterReturn('not_eligible', 'PLAN_NOT_ELIGIBLE');
      expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['Your ChatGPT plan is not eligible for ORACUL']);
      expect(router.url).toBe('/');
      expect(text('chatgpt-status')).toBe('Plan not eligible');
    });

    // @trace FR-7
    it('an unknown value shows no snackbar but is still removed from the URL', async () => {
      await startAfterReturn('whatever', 'NOT_CONNECTED');
      expect(snackbars().length).toBe(0);
      expect(router.url).toBe('/');
      expect(text('chatgpt-status')).toBe('Not connected');
    });

    // @trace FR-7
    it('the other query parameters survive the removal of chatgpt', async () => {
      await router.navigateByUrl('/?chatgpt=connected&keep=1');
      await startWith('CONNECTED');
      expect(router.url).toBe('/?keep=1');
    });

    // @trace FR-7
    it('at bootstrap (router not navigated yet) the parameter is read from window.location.search', async () => {
      const original = window.location.pathname + window.location.search + window.location.hash;
      window.history.replaceState(null, '', '/?chatgpt=connected');
      try {
        expect(router.navigated).toBe(false);
        await startWith('CONNECTED');
        expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual(['ChatGPT connected']);
        expect(router.url).toBe('/');
        expect(text('chatgpt-status')).toBe('ChatGPT connected');
      } finally {
        window.history.replaceState(null, '', original);
      }
    });

    // @trace FR-7
    it('without the parameter no snackbar is shown', async () => {
      await startWith('NOT_CONNECTED');
      expect(snackbars().length).toBe(0);
    });
  });
});
