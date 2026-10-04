import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';
import { RunStore } from './run.store';

@Component({
  selector: 'app-generate-button',
  imports: [MatButtonModule],
  template: `
    <button mat-flat-button data-testid="generate-button" [disabled]="!enabled()" (click)="generate()">
      {{ runs.active() ? 'STOP' : 'GENERATE THE FUTURE' }}
    </button>
    @if (!runs.active() && !connection.canGenerate()) {
      <p class="hint" data-testid="generate-hint">Connect ChatGPT to generate</p>
      @if (connection.state() !== 'LOADING' && connection.state() !== 'CONNECTED') {
        <p class="hint" data-testid="chatgpt-conditions">
          Signing in needs a personal ChatGPT Plus or Pro account. Open ORACUL in a browser on the same computer where ORACUL runs.
        </p>
      }
    }
  `,
  styles: `
    :host { display: contents; }
    .hint { color: var(--mat-sys-on-surface-variant); margin: 0; }
  `,
})
export class GenerateButton {
  protected readonly connection = inject(ConnectionStore);
  protected readonly runs = inject(RunStore);
  private readonly scenario = inject(ScenarioStore);

  protected readonly enabled = computed(() =>
    this.runs.active() ? !this.runs.stopping() : this.connection.canGenerate() && !this.runs.starting(),
  );

  protected generate(): void {
    if (!this.enabled()) return;
    if (this.runs.active()) this.runs.stopRun();
    else this.runs.start(this.scenario.configuration());
  }
}
