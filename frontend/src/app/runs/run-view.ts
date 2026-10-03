import { Component, OnInit, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { ActivatedRoute } from '@angular/router';

import { FutureResultComponent } from '../result/future-result';
import { ProgressView } from './progress-view';
import { InsufficientView } from './insufficient-view';
import { RunFailure } from './run-failure';
import { RunStore } from './run.store';

@Component({
  selector: 'app-run-view',
  imports: [MatButtonModule, ProgressView, RunFailure, InsufficientView, FutureResultComponent],
  template: `
    @if (runs.notFound()) {
      <app-run-failure />
    } @else if (runs.unavailable()) {
      <div class="unavailable" data-testid="backend-unavailable">
        <p>ORACUL is unavailable — try again shortly</p>
        <button mat-flat-button data-testid="backend-retry" (click)="runs.retry()">Try again</button>
      </div>
    } @else if (runs.run(); as run) {
      @if (run.status === 'FAILED') {
        <app-run-failure
          [message]="run.failure?.message ?? 'Something went wrong — try again'"
          [configuration]="run.configuration"
        />
      } @else if (run.status === 'INSUFFICIENT_EVIDENCE') {
        <app-insufficient-view
          [message]="run.failure?.message ?? null"
          [configuration]="run.configuration"
          [suggestedRealism]="run.suggestedRealism ?? null"
        />
      } @else if (run.status === 'COMPLETED' && run.headline) {
        <app-future-result [runId]="run.id" />
      } @else {
        <app-progress-view />
      }
    }
  `,
  styles: `.unavailable { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; }`,
})
export class RunView implements OnInit {
  protected readonly runs = inject(RunStore);
  private readonly route = inject(ActivatedRoute);

  ngOnInit(): void {
    const id = this.route.snapshot.paramMap.get('runId');
    if (id) this.runs.open(id);
  }
}
