import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import type { Subscription } from 'rxjs';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import type { FutureResult } from '../api/models/future-result';
import { ResultService } from '../api/services/result.service';
import { RunFailure } from '../runs/run-failure';
import { FutureStoryComponent } from './future-story';
import { ScenarioMetadataComponent } from './scenario-metadata';
import { WhySourcesComponent } from './why-sources';

const GENERIC_ERROR = 'Something went wrong — try again';

@Component({
  selector: 'app-future-result',
  imports: [MatProgressBarModule, RunFailure, FutureStoryComponent, ScenarioMetadataComponent, WhySourcesComponent],
  template: `
    @if (error(); as message) {
      <app-run-failure [message]="message" />
    } @else if (result(); as r) {
      <div class="result" data-testid="result-view">
        <app-future-story [story]="r.story" [labels]="r.labels" />
        <app-scenario-metadata [metadata]="r.metadata" [issues]="r.openCriticIssues ?? []" />
        <app-why-sources [causalChain]="r.causalChain ?? []" [sources]="r.sources ?? []" [futureDate]="r.story.futureDate" [research]="r.research" />
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
export class FutureResultComponent {
  private readonly api = inject(ResultService);
  readonly runId = input.required<string>();
  protected readonly result = signal<FutureResult | null>(null);
  protected readonly error = signal<string | null>(null);

  private sub?: Subscription;

  constructor() {
    inject(DestroyRef).onDestroy(() => this.sub?.unsubscribe());
    effect(() => {
      const runId = this.runId();
      untracked(() => this.load(runId));
    });
  }

  private load(runId: string): void {
    this.sub?.unsubscribe();
    this.result.set(null);
    this.error.set(null);
    this.sub = this.api.getFutureResult({ runId }).subscribe({
      next: (r) => this.result.set(r),
      error: (err: unknown) => {
        const body = err instanceof HttpErrorResponse ? err.error : null;
        const ok = body && typeof body === 'object' && typeof body.message === 'string';
        this.error.set(ok ? body.message : GENERIC_ERROR);
      },
    });
  }
}
