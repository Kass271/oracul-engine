import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting, type TestRequest } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MATERIAL_ANIMATIONS } from '@angular/material/core';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import type { ChatGptConnectionState } from '../api/models/chat-gpt-connection-state';

const CONNECTION_URL = '/api/auth/chatgpt/connection';
const ANIMATIONS_OFF = { provide: MATERIAL_ANIMATIONS, useValue: { animationsDisabled: true } };

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

// phase 02: the model has five states (REGISTRATION_INVALID added); the union keeps this spec compiling
// before and after the generated client is regenerated from the contract
type State = ChatGptConnectionState | 'REGISTRATION_INVALID';

const STATUS_TEXT: Record<State, string> = {
  NOT_CONNECTED: 'Not connected',
  CONNECTED: 'ChatGPT connected',
  PLAN_NOT_ELIGIBLE: 'Plan not eligible',
  SESSION_EXPIRED: 'Session expired',
  REGISTRATION_INVALID: 'Registration invalid',
};

const CONDITIONS_TEXT =
  'Signing in needs a personal ChatGPT Plus or Pro account. Open ORACUL in a browser on the same computer where ORACUL runs.';
const REGISTRATION_URL = '/api/auth/chatgpt/registration';

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
  function flushConnection(state: State): void {
    const pending = http.match((r) => isConnection(r));
    expect(pending.length).toBeGreaterThanOrEqual(1);
    pending.forEach((r) => r.flush({ state, canGenerate: state === 'CONNECTED' }));
  }

  async function startWith(state: State): Promise<void> {
    fixture.detectChanges();
    flushScenario();
    flushConnection(state);
    await settle();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), ANIMATIONS_OFF],
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
    for (const state of Object.keys(STATUS_TEXT) as State[]) {
      TestBed.resetTestingModule();
      TestBed.configureTestingModule({
        imports: [App],
        providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting(), ANIMATIONS_OFF],
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

  // ---- FR-37: header menu and reset dialog ----

  describe('Reset ChatGPT connection (FR-37)', () => {
    const doc = (id: string): HTMLElement | null => document.querySelector<HTMLElement>(`[data-testid="${id}"]`);
    const isReset = (r: { url: string; method: string }): boolean => r.url.endsWith(REGISTRATION_URL) && r.method === 'DELETE';
    const messages = (): string[] => snackbars().map((m) => (m.textContent ?? '').trim());

    async function until(cond: () => boolean): Promise<void> {
      for (let i = 0; i < 100 && !cond(); i++) {
        await new Promise((r) => setTimeout(r, 20));
        fixture.detectChanges();
      }
    }

    async function openMenu(): Promise<void> {
      (byId('chatgpt-menu') as HTMLElement).click();
      await settle();
    }

    async function openDialog(state: State = 'CONNECTED'): Promise<void> {
      await startWith(state);
      await openMenu();
      doc('chatgpt-reset')!.click();
      await settle();
      expect(doc('chatgpt-reset-dialog')).not.toBeNull();
    }

    afterEach(() => {
      http.match(() => true);
      document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    });

    // @trace FR-37
    it('has no menu while loading', () => {
      fixture.detectChanges();
      expect(text('chatgpt-status')).toBe('Checking…');
      expect(byId('chatgpt-menu')).toBeNull();
    });

    // @trace FR-37
    it.each(Object.keys(STATUS_TEXT) as State[])('shows the menu button in state %s', async (state) => {
      await startWith(state);
      const menu = byId('chatgpt-menu') as HTMLButtonElement;
      expect(menu).not.toBeNull();
      expect(menu.tagName).toBe('BUTTON');
      expect(menu.getAttribute('aria-label')).toBe('ChatGPT options');
      expect(menu.querySelector('mat-icon')?.textContent?.trim()).toBe('more_vert');
      expect(byId('app-header')!.contains(menu)).toBe(true);
    });

    // @trace FR-37
    it('REGISTRATION_INVALID shows "Registration invalid" without the connect action but with the menu', async () => {
      await startWith('REGISTRATION_INVALID');
      expect(text('chatgpt-status')).toBe('Registration invalid');
      expect(byId('chatgpt-connect')).toBeNull();
      expect(byId('chatgpt-disconnect')).toBeNull();
      expect(byId('chatgpt-menu')).not.toBeNull();
    });

    // @trace FR-37
    it('the menu offers "Reset ChatGPT connection"', async () => {
      await startWith('NOT_CONNECTED');
      await openMenu();
      expect((doc('chatgpt-reset')?.textContent ?? '').trim()).toBe('Reset ChatGPT connection');
    });

    // @trace FR-37
    it('the dialog explains the reset and offers Cancel and Reset', async () => {
      await openDialog();
      const dialog = document.querySelector('.mat-mdc-dialog-container')!.textContent ?? '';
      expect(dialog).toContain('Reset ChatGPT connection?');
      expect(dialog).toContain(
        'ORACUL forgets its ChatGPT registration and signs you out. The next Continue with ChatGPT asks you to connect ORACUL again.',
      );
      expect((doc('chatgpt-reset-cancel')?.textContent ?? '').trim()).toBe('Cancel');
      expect((doc('chatgpt-reset-confirm')?.textContent ?? '').trim()).toBe('Reset');
      expect((doc('chatgpt-reset-confirm') as HTMLButtonElement).disabled).toBe(false);
      expect(http.match(isReset).length).toBe(0);
    });

    // @trace FR-37
    it('Cancel sends no request and changes nothing', async () => {
      await openDialog();
      doc('chatgpt-reset-cancel')!.click();
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
      expect(http.match(isReset).length).toBe(0);
      expect(http.match((r) => isConnection(r)).length).toBe(0);
      expect(text('chatgpt-status')).toBe('ChatGPT connected');
      expect(snackbars().length).toBe(0);
    });

    // @trace FR-37
    it('Escape closes the dialog without a request', async () => {
      await openDialog();
      const event = new KeyboardEvent('keydown', { key: 'Escape', code: 'Escape', keyCode: 27, bubbles: true });
      document.querySelector('.mat-mdc-dialog-container')!.dispatchEvent(event);
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
      expect(http.match(isReset).length).toBe(0);
    });

    // @trace FR-37
    it('a click on the backdrop closes the dialog without a request', async () => {
      await openDialog();
      (document.querySelector('.cdk-overlay-dark-backdrop') as HTMLElement).click();
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
      expect(http.match(isReset).length).toBe(0);
    });

    // @trace FR-37
    it('Reset sends exactly one DELETE, stays disabled while in flight, then shows the message and reloads', async () => {
      await openDialog();
      const confirm = doc('chatgpt-reset-confirm') as HTMLButtonElement;
      confirm.click();
      fixture.detectChanges();
      const del: TestRequest = http.expectOne(isReset);
      expect(confirm.disabled).toBe(true);
      confirm.click();
      fixture.detectChanges();
      expect(http.match(isReset).length).toBe(0);

      del.flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(messages()).toEqual(['ChatGPT connection reset']);
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
      flushConnection('NOT_CONNECTED');
      await settle();
      expect(text('chatgpt-status')).toBe('Not connected');
      expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
      expect(byId('chatgpt-disconnect')).toBeNull();
    });

    // @trace FR-37
    it('a 409 shows "Wait until the current run finishes" and keeps the header', async () => {
      await openDialog();
      doc('chatgpt-reset-confirm')!.click();
      fixture.detectChanges();
      http
        .expectOne(isReset)
        .flush({ code: 'RUN_IN_PROGRESS', message: 'Wait until the current run finishes' }, { status: 409, statusText: 'Conflict' });
      await settle();
      expect(messages()).toEqual(['Wait until the current run finishes']);
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
      expect(text('chatgpt-status')).toBe('ChatGPT connected');
      expect(byId('chatgpt-disconnect')).not.toBeNull();
    });

    // @trace FR-37
    it('a 500 shows "Something went wrong — try again" and keeps the header', async () => {
      await openDialog();
      doc('chatgpt-reset-confirm')!.click();
      fixture.detectChanges();
      http
        .expectOne(isReset)
        .flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status: 500, statusText: 'Server Error' });
      await settle();
      expect(messages()).toEqual(['Something went wrong — try again']);
      expect(text('chatgpt-status')).toBe('ChatGPT connected');
    });

    // @trace FR-37
    it('a network error shows "Something went wrong — try again"', async () => {
      await openDialog();
      doc('chatgpt-reset-confirm')!.click();
      fixture.detectChanges();
      http.expectOne(isReset).error(new ProgressEvent('error'));
      await settle();
      expect(messages()).toEqual(['Something went wrong — try again']);
      expect(text('chatgpt-status')).toBe('ChatGPT connected');
      await until(() => doc('chatgpt-reset-dialog') === null);
      expect(doc('chatgpt-reset-dialog')).toBeNull();
    });

    // @trace FR-37
    it('the reset is available from every state, e.g. PLAN_NOT_ELIGIBLE', async () => {
      await openDialog('PLAN_NOT_ELIGIBLE');
      doc('chatgpt-reset-confirm')!.click();
      fixture.detectChanges();
      http.expectOne(isReset).flush(null, { status: 204, statusText: 'No Content' });
      await settle();
      expect(messages()).toEqual(['ChatGPT connection reset']);
    });
  });

  // ---- FR-41: sign-in conditions ----

  describe('Sign-in conditions (FR-41)', () => {
    const conditions = (): HTMLElement[] => Array.from(el().querySelectorAll<HTMLElement>('[data-testid="chatgpt-conditions"]'));

    function assertNoSecretInput(): void {
      expect(document.querySelectorAll('input[type=password]').length).toBe(0);
      expect(el().textContent ?? '').not.toMatch(/api[ _-]?key/i);
    }

    afterEach(() => {
      http.match(() => true);
      document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
    });

    // @trace FR-41
    it.each(['NOT_CONNECTED', 'PLAN_NOT_ELIGIBLE', 'SESSION_EXPIRED', 'REGISTRATION_INVALID'] as State[])(
      'state %s shows the conditions exactly once below the generate hint',
      async (state) => {
        await startWith(state);
        expect(conditions().length).toBe(1);
        const c = conditions()[0];
        expect(c.tagName).toBe('P');
        expect((c.textContent ?? '').trim()).toBe(CONDITIONS_TEXT);
        expect(byId('welcome-view')!.contains(c)).toBe(true);
        expect(text('generate-hint')).toBe('Connect ChatGPT to generate');
        expect(byId('generate-hint')!.compareDocumentPosition(c) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        assertNoSecretInput();
      },
    );

    // @trace FR-41
    it('a failed connection load (500) shows the conditions', async () => {
      fixture.detectChanges();
      flushScenario();
      http.match((r) => isConnection(r)).forEach((r) =>
        r.flush({ code: 'INTERNAL_ERROR', message: 'Something went wrong — try again' }, { status: 500, statusText: 'Server Error' }),
      );
      await settle();
      expect(conditions().length).toBe(1);
      expect((conditions()[0].textContent ?? '').trim()).toBe(CONDITIONS_TEXT);
      assertNoSecretInput();
    });

    // @trace FR-41
    it('a network error on the connection load shows the conditions', async () => {
      fixture.detectChanges();
      flushScenario();
      http.match((r) => isConnection(r)).forEach((r) => r.error(new ProgressEvent('error')));
      await settle();
      expect(conditions().length).toBe(1);
      expect((conditions()[0].textContent ?? '').trim()).toBe(CONDITIONS_TEXT);
    });

    // @trace FR-41
    it('is not shown while the connection is loading', async () => {
      fixture.detectChanges();
      flushScenario();
      await settle();
      expect(text('chatgpt-status')).toBe('Checking…');
      expect(conditions().length).toBe(0);
      assertNoSecretInput();
    });

    // @trace FR-41
    it('is not shown while CONNECTED', async () => {
      await startWith('CONNECTED');
      expect(conditions().length).toBe(0);
      assertNoSecretInput();
    });

    // @trace FR-41
    it('has no password input or key field while the reset dialog is open', async () => {
      await startWith('NOT_CONNECTED');
      (byId('chatgpt-menu') as HTMLElement).click();
      await settle();
      document.querySelector<HTMLElement>('[data-testid="chatgpt-reset"]')!.click();
      await settle();
      expect(document.querySelector('[data-testid="chatgpt-reset-dialog"]')).not.toBeNull();
      expect(document.querySelectorAll('input[type=password]').length).toBe(0);
      expect(document.body.textContent ?? '').not.toMatch(/api[ _-]?key/i);
      expect(conditions().length).toBe(1);
    });
  });

  describe('return from OpenAI (?chatgpt=)', () => {
    async function startAfterReturn(outcome: string, state: State): Promise<void> {
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

    // @trace FR-7, FR-36
    it('not_completed: snackbar "ChatGPT connection was not completed — please try again", header stays "Not connected"', async () => {
      await startAfterReturn('not_completed', 'NOT_CONNECTED');
      expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual([
        'ChatGPT connection was not completed — please try again',
      ]);
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

    // @trace FR-36
    it.each([
      ['not_verified', 'ChatGPT sign-in could not be verified — please try again'],
      ['expired', 'Sign-in expired — click Continue with ChatGPT to start again'],
    ])('%s: snackbar "%s", parameter removed, header "Not connected"', async (outcome, message) => {
      await startAfterReturn(outcome, 'NOT_CONNECTED');
      expect(snackbars().map((s) => (s.textContent ?? '').trim())).toEqual([message]);
      expect(router.url).toBe('/');
      expect(text('chatgpt-status')).toBe('Not connected');
      expect(text('chatgpt-connect')).toBe('Continue with ChatGPT');
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
