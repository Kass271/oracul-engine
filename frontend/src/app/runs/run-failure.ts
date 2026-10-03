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
      <button mat-flat-button data-testid="try-again" [disabled]="runs.starting()" (click)="back()">Try again</button>
    </div>
  `,
  styles: `.failure { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; }`,
})
export class RunFailure {
  readonly message = input('Future not found');
  readonly configuration = input<ScenarioConfiguration | null>(null);
  protected readonly runs = inject(RunStore);
  private readonly router = inject(Router);

  protected back(): void {
    const config = this.configuration();
    if (config) this.runs.start(config);
    else void this.router.navigateByUrl('/');
  }
}
