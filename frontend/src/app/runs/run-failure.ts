import { Component, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { Router } from '@angular/router';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { RunStore } from './run.store';

@Component({
  selector: 'app-run-failure',
  imports: [MatButtonModule],
  template: `
    <div class="failure" data-testid="failure-view">
      <p data-testid="failure-message">{{ message() }}</p>
      @if (code() === 'CHATGPT_RATE_LIMITED') {
        <a
          mat-stroked-button
          data-testid="failure-usage-link"
          href="https://chatgpt.com/#settings/Usage"
          target="_blank"
          rel="noopener noreferrer"
          >Open ChatGPT Settings → Usage</a
        >
      }
      @if (code() === 'CHATGPT_SESSION_EXPIRED') {
        <a mat-stroked-button data-testid="failure-reconnect" href="/api/auth/chatgpt/authorize">Continue with ChatGPT</a>
      }
      @if (providerCode(); as pc) {
        @if (code() === 'CHATGPT_REQUEST_REJECTED' || code() === 'CHATGPT_UNEXPECTED_ERROR') {
          <p class="code" data-testid="failure-provider-code">Error code: {{ pc }}</p>
        }
      }
      <button mat-flat-button data-testid="try-again" [disabled]="runs.starting()" (click)="back()">Try again</button>
    </div>
  `,
  styles: `.failure { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; max-width: 100%; box-sizing: border-box; text-align: center; }
    .failure p { max-width: 100%; overflow-wrap: anywhere; margin: 0; }`,
})
export class RunFailure {
  readonly message = input('Future not found');
  readonly code = input<string | null>(null);
  readonly providerCode = input<string | null>(null);
  readonly configuration = input<ScenarioConfiguration | null>(null);
  protected readonly runs = inject(RunStore);
  private readonly router = inject(Router);

  protected back(): void {
    const config = this.configuration();
    if (config) this.runs.start(config);
    else void this.router.navigateByUrl('/');
  }
}
