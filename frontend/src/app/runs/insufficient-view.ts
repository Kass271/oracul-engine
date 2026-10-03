import { Component, computed, inject, input } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';
import { RunStore } from './run.store';

@Component({
  selector: 'app-insufficient-view',
  imports: [MatButtonModule],
  template: `
    <div class="insufficient" data-testid="insufficient-view">
      <p data-testid="insufficient-message">{{ text() }}</p>
      @if (suggestedRealism() !== null) {
        <button
          mat-flat-button
          data-testid="lower-realism"
          [disabled]="runs.starting() || !connection.canGenerate()"
          (click)="lower()"
        >
          LOWER REALISM
        </button>
      }
    </div>
  `,
  styles: `.insufficient { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; }`,
})
export class InsufficientView {
  readonly message = input<string | null>(null);
  readonly configuration = input.required<ScenarioConfiguration>();
  readonly suggestedRealism = input<number | null>(null);

  protected readonly runs = inject(RunStore);
  protected readonly connection = inject(ConnectionStore);
  private readonly scenario = inject(ScenarioStore);

  protected readonly text = computed(
    () =>
      this.message() ??
      `ORACUL found insufficient current evidence to construct this scenario at Realism ${this.configuration().realism}.`,
  );

  protected lower(): void {
    const target = this.suggestedRealism();
    if (target === null || this.runs.starting() || !this.connection.canGenerate()) return;
    this.scenario.load(this.configuration());
    this.scenario.setRealism(target);
    this.runs.start(this.scenario.configuration());
  }
}
