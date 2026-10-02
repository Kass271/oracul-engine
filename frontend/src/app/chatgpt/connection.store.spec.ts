import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ApplicationRef } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MatSnackBar } from '@angular/material/snack-bar';
import { provideRouter } from '@angular/router';

import { ConnectionStore } from './connection.store';

// @trace FR-8
describe('ConnectionStore message replacement (FR-8)', () => {
  let store: ConnectionStore;
  let http: HttpTestingController;
  let snackBar: MatSnackBar;

  const messages = (): string[] => {
    TestBed.inject(ApplicationRef).tick();
    return Array.from(document.querySelectorAll('[data-testid="chatgpt-message"]')).map((e) =>
      (e.textContent ?? '').trim(),
    );
  };

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter([]), provideHttpClient(), provideHttpClientTesting()],
    });
    store = TestBed.inject(ConnectionStore);
    http = TestBed.inject(HttpTestingController);
    snackBar = TestBed.inject(MatSnackBar);
  });

  afterEach(() => {
    http.match(() => true);
    snackBar.dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((e) => e.remove());
  });

  it('keeps exactly one chatgpt message in the DOM, with the newest text, and dismisses the previous ref', async () => {
    const open = vi.spyOn(snackBar, 'openFromComponent');

    store.handleReturn('not_completed');
    expect(messages()).toEqual(['ChatGPT connection was not completed']);
    const firstRef = open.mock.results[0].value;
    const dismiss = vi.spyOn(firstRef, 'dismiss');

    store.handleReturn('connected');

    expect(dismiss).toHaveBeenCalled();
    expect(messages()).toEqual(['ChatGPT connected']);
    expect(
      document.querySelectorAll('.mat-mdc-snack-bar-container [data-testid="chatgpt-message"]')
        .length,
    ).toBe(1);
  });
});
