import { Injectable, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarRef } from '@angular/material/snack-bar';
import { Router } from '@angular/router';
import { Observable, finalize } from 'rxjs';

import type { ChatGptConnectionState } from '../api/models/chat-gpt-connection-state';
import { ChatgptService } from '../api/services/chatgpt.service';
import { ChatGptMessage } from './chatgpt-message';

export type ConnectionViewState = 'LOADING' | ChatGptConnectionState;

const RETURN_MESSAGES: Record<string, string> = {
  connected: 'ChatGPT connected',
  not_completed: 'ChatGPT connection was not completed — please try again',
  not_eligible: 'Your ChatGPT plan is not eligible for ORACUL',
  not_verified: 'ChatGPT sign-in could not be verified — please try again',
  expired: 'Sign-in expired — click Continue with ChatGPT to start again',
};

/** Connection state lives in memory only; nothing is persisted in the browser. */
@Injectable({ providedIn: 'root' })
export class ConnectionStore {
  private readonly api = inject(ChatgptService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);

  private current?: MatSnackBarRef<ChatGptMessage>;

  readonly state = signal<ConnectionViewState>('LOADING');
  readonly canGenerate = signal(false);
  readonly disconnecting = signal(false);
  readonly resetting = signal(false);
  readonly isConnected = computed(() => this.state() === 'CONNECTED');

  load(): void {
    this.api.getChatGptConnection().subscribe({
      next: (c) => {
        this.state.set(c.state);
        this.canGenerate.set(c.canGenerate);
      },
      error: () => {
        this.state.set('NOT_CONNECTED');
        this.canGenerate.set(false);
      },
    });
  }

  disconnect(): void {
    if (this.disconnecting()) return;
    this.disconnecting.set(true);
    this.api.disconnectChatGpt().subscribe({
      next: () => {
        this.disconnecting.set(false);
        this.load();
      },
      error: () => {
        this.disconnecting.set(false);
        this.message('Something went wrong — try again');
      },
    });
  }

  /** Resets the installation-wide registration; the returned observable completes when the request ends. */
  reset(): Observable<void> {
    this.resetting.set(true);
    const done = this.api.resetChatGptRegistration().pipe(finalize(() => this.resetting.set(false)));
    return new Observable<void>((sub) => {
      done.subscribe({
        next: () => {
          this.message('ChatGPT connection reset');
          this.load();
        },
        error: (e: { status?: number; error?: { message?: string } }) => {
          this.message(
            e?.status === 409
              ? (e.error?.message ?? 'Wait until the current run finishes')
              : 'Something went wrong — try again',
          );
          sub.complete();
        },
        complete: () => sub.complete(),
      });
    });
  }

  handleReturn(outcome: string | null): void {
    if (outcome !== null) {
      const text = RETURN_MESSAGES[outcome];
      if (text) this.message(text);
      void this.router.navigate([], { queryParams: { chatgpt: null }, queryParamsHandling: 'merge', replaceUrl: true });
    }
    this.load();
  }

  private message(text: string): void {
    // Only one ChatGPT message may exist in the DOM: drop the previous one at once (no exit animation).
    this.current?.dismiss();
    this.current = undefined;
    document
      .querySelectorAll('.mat-mdc-snack-bar-container')
      .forEach((el) => {
        if (el.querySelector('[data-testid="chatgpt-message"]')) el.remove();
      });
    this.current = this.snackBar.openFromComponent(ChatGptMessage, { duration: 6000, data: text });
  }
}
