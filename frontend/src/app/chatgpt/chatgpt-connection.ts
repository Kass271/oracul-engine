import { Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ConnectionStore, type ConnectionViewState } from './connection.store';

const STATUS_TEXT: Record<ConnectionViewState, string> = {
  LOADING: 'Checking…',
  NOT_CONNECTED: 'Not connected',
  CONNECTED: 'ChatGPT connected',
  PLAN_NOT_ELIGIBLE: 'Plan not eligible',
  SESSION_EXPIRED: 'Session expired',
};

@Component({
  selector: 'app-chatgpt-connection',
  imports: [MatButtonModule],
  template: `
    <span class="status" data-testid="chatgpt-status">{{ statusText() }}</span>
    @switch (store.state()) {
      @case ('LOADING') {}
      @case ('CONNECTED') {
        <button mat-stroked-button data-testid="chatgpt-disconnect" [disabled]="store.disconnecting()" (click)="store.disconnect()">
          Disconnect
        </button>
      }
      @default {
        <a mat-flat-button data-testid="chatgpt-connect" href="/api/auth/chatgpt/authorize">Continue with ChatGPT</a>
      }
    }
  `,
  styles: ':host { display: inline-flex; align-items: center; gap: 12px; } .status { font-size: 14px; }',
})
export class ChatGptConnectionComponent {
  protected readonly store = inject(ConnectionStore);
  protected statusText = () => STATUS_TEXT[this.store.state()];
}
