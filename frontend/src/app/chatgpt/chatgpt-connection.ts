import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatDialog } from '@angular/material/dialog';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';

import { ResetDialog } from './reset-dialog';

import { ConnectionStore, type ConnectionViewState } from './connection.store';

const STATUS_TEXT: Record<ConnectionViewState, string> = {
  LOADING: 'Checking…',
  NOT_CONNECTED: 'Not connected',
  CONNECTED: 'ChatGPT connected',
  PLAN_NOT_ELIGIBLE: 'Plan not eligible',
  SESSION_EXPIRED: 'Session expired',
  REGISTRATION_INVALID: 'Registration invalid',
};

@Component({
  selector: 'app-chatgpt-connection',
  imports: [MatButtonModule, MatIconModule, MatMenuModule],
  template: `
    <span class="status" data-testid="chatgpt-status">{{ statusText() }}</span>
    @switch (store.state()) {
      @case ('LOADING') {}
      @case ('REGISTRATION_INVALID') {}
      @case ('CONNECTED') {
        <button
          mat-stroked-button
          data-testid="chatgpt-disconnect"
          [disabled]="store.disconnecting()"
          (click)="store.disconnect()"
        >
          Disconnect
        </button>
      }
      @default {
        <a mat-flat-button data-testid="chatgpt-connect" href="/api/auth/chatgpt/authorize"
          >Continue with ChatGPT</a
        >
      }
    }
    @if (store.state() !== 'LOADING') {
      <button mat-icon-button data-testid="chatgpt-menu" aria-label="ChatGPT options" [matMenuTriggerFor]="menu">
        <mat-icon>more_vert</mat-icon>
      </button>
    }
    <mat-menu #menu="matMenu">
      <button mat-menu-item data-testid="chatgpt-reset" (click)="openReset()">Reset ChatGPT connection</button>
    </mat-menu>
  `,
  styles:
    ':host { display: inline-flex; flex-wrap: wrap; justify-content: flex-end; align-items: center; gap: 4px 12px; max-width: 100%; min-width: 0; } .status { font-size: 14px; }',
})
export class ChatGptConnectionComponent {
  protected readonly store = inject(ConnectionStore);
  private readonly dialog = inject(MatDialog);
  protected openReset(): void {
    this.dialog.open(ResetDialog);
  }
  protected statusText = () => STATUS_TEXT[this.store.state()];
}
