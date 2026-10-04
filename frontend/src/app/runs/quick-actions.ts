import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';
import { RunStore } from './run.store';

export type QuickAction = 'MORE_REALISTIC' | 'DARKER' | 'MORE_OPTIMISTIC' | 'MORE_EXTREME';

export function quickTarget(action: QuickAction, value: number): number | null {
  if (action === 'MORE_EXTREME') return value <= 1 ? null : Math.max(1, value - 3);
  return value >= 10 ? null : Math.min(10, value + 2);
}

@Component({
  selector: 'app-quick-actions',
  imports: [MatButtonModule],
  template: `
    <div class="quick" data-testid="quick-actions">
      <button mat-stroked-button data-testid="quick-more-realistic" [disabled]="off('MORE_REALISTIC')" (click)="go('MORE_REALISTIC')">MORE REALISTIC</button>
      <button mat-stroked-button data-testid="quick-darker" [disabled]="off('DARKER')" (click)="go('DARKER')">DARKER</button>
      <button mat-stroked-button data-testid="quick-more-optimistic" [disabled]="off('MORE_OPTIMISTIC')" (click)="go('MORE_OPTIMISTIC')">MORE OPTIMISTIC</button>
      <button mat-stroked-button data-testid="quick-more-extreme" [disabled]="off('MORE_EXTREME')" (click)="go('MORE_EXTREME')">MORE EXTREME</button>
    </div>
  `,
  styles: `.quick { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 24px; }`,
})
export class QuickActions {
  private readonly connection = inject(ConnectionStore);
  private readonly runs = inject(RunStore);
  private readonly scenario = inject(ScenarioStore);

  private readonly blocked = computed(
    () => !this.connection.canGenerate() || this.runs.starting() || this.runs.active(),
  );

  private target(a: QuickAction): number | null {
    const v = a === 'DARKER' ? this.scenario.darkness() : a === 'MORE_OPTIMISTIC' ? this.scenario.optimism() : this.scenario.realism();
    return quickTarget(a, v);
  }

  protected off(a: QuickAction): boolean {
    return this.blocked() || this.target(a) === null;
  }

  protected go(a: QuickAction): void {
    if (this.off(a)) return;
    const t = this.target(a) as number;
    if (a === 'DARKER') this.scenario.setDarkness(t);
    else if (a === 'MORE_OPTIMISTIC') this.scenario.setOptimism(t);
    else this.scenario.setRealism(t);
    this.runs.start(this.scenario.configuration());
  }
}
