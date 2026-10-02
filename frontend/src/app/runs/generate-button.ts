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
      GENERATE THE FUTURE
    </button>
    @if (!connection.canGenerate()) {
      <p class="hint" data-testid="generate-hint">Connect ChatGPT to generate</p>
    }
  `,
  styles: `
    :host { display: contents; }
    .hint { color: var(--mat-sys-on-surface-variant); margin: 0; }
  `,
})
export class GenerateButton {
  protected readonly connection = inject(ConnectionStore);
  private readonly runs = inject(RunStore);
  private readonly scenario = inject(ScenarioStore);

  protected readonly enabled = computed(
    () => this.connection.canGenerate() && !this.runs.starting() && !this.runs.active(),
  );

  protected generate(): void {
    if (this.enabled()) this.runs.start(this.scenario.configuration());
  }
}
