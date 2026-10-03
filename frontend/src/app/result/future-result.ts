import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit, inject, input, signal } from '@angular/core';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import type { FutureResult } from '../api/models/future-result';
import { ResultService } from '../api/services/result.service';
import { RunFailure } from '../runs/run-failure';
import { FutureStoryComponent } from './future-story';
import { ScenarioMetadataComponent } from './scenario-metadata';

const GENERIC_ERROR = 'Something went wrong — try again';

@Component({
  selector: 'app-future-result',
  imports: [MatProgressBarModule, RunFailure, FutureStoryComponent, ScenarioMetadataComponent],
  template: `
    @if (error(); as message) {
      <app-run-failure [message]="message" />
    } @else if (result(); as r) {
      <div class="result" data-testid="result-view">
        <app-future-story [story]="r.story" [labels]="r.labels" />
        <app-scenario-metadata [metadata]="r.metadata" [issues]="r.openCriticIssues" />
      </div>
    } @else {
      <div class="loading" data-testid="result-loading">
        <mat-progress-bar mode="indeterminate" />
      </div>
    }
  `,
  styles: `
    .result { padding: 32px 16px; overflow: auto; }
    .loading { padding: 16px; }
  `,
})
export class FutureResultComponent implements OnInit {
  private readonly api = inject(ResultService);
  readonly runId = input.required<string>();
  protected readonly result = signal<FutureResult | null>(null);
  protected readonly error = signal<string | null>(null);

  ngOnInit(): void {
    this.api.getFutureResult({ runId: this.runId() }).subscribe({
      next: (r) => this.result.set(r),
      error: (err: unknown) => {
        const body = err instanceof HttpErrorResponse ? err.error : null;
        const ok = body && typeof body === 'object' && typeof body.message === 'string';
        this.error.set(ok ? body.message : GENERIC_ERROR);
      },
    });
  }
}
