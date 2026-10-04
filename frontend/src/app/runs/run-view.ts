import { Component, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { ActivatedRoute } from '@angular/router';

import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { FutureResultComponent } from '../result/future-result';
import { ProgressView } from './progress-view';
import { InsufficientView } from './insufficient-view';
import { ScenarioStore } from '../scenario/scenario.store';
import { ConnectionStore } from '../chatgpt/connection.store';
import { RunFailure } from './run-failure';
import { GenerateButton } from './generate-button';
import { RunStore } from './run.store';

@Component({
  selector: 'app-run-view',
  imports: [MatButtonModule, MatCardModule, GenerateButton, ProgressView, RunFailure, InsufficientView, FutureResultComponent],
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
          [code]="run.failure?.code ?? null"
          [providerCode]="run.failure?.providerCode ?? null"
          [configuration]="run.configuration"
        />
      } @else if (run.status === 'INSUFFICIENT_EVIDENCE') {
        <app-insufficient-view
          [message]="run.failure?.message ?? null"
          [configuration]="run.configuration"
          [suggestedRealism]="run.suggestedRealism ?? null"
        />
      } @else if (run.status === 'STOPPED') {
        <div class="stopped" data-testid="stopped-view">
          <p data-testid="stopped-message">Generation stopped</p>
          <p class="hint" data-testid="stopped-hint">Change the settings or click GENERATE THE FUTURE to start a new one.</p>
          <app-generate-button />
        </div>
      } @else if (run.status === 'COMPLETED' && run.headline) {
        <app-future-result [runId]="run.id" />
        @if (run.evidenceNote; as note) {
          <mat-card class="note" data-testid="evidence-note" role="note">
            <mat-card-content>
              <p data-testid="evidence-note-message">{{ note.message }}</p>
              @if (run.suggestedRealism != null) {
                <button
                  mat-flat-button
                  data-testid="lower-realism"
                  [disabled]="runs.starting() || runs.active() || !connection.canGenerate()"
                  (click)="lower(run.configuration, run.suggestedRealism)"
                >
                  LOWER REALISM
                </button>
              }
            </mat-card-content>
          </mat-card>
        }
      } @else {
        <app-progress-view />
      }
    }
  `,
  styles: `.stopped { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; text-align: center; }
    .note { margin: 0 16px 16px; }
    .unavailable { margin: auto; display: flex; flex-direction: column; align-items: center; gap: 16px; padding: 16px; }`,
})
export class RunView {
  protected readonly runs = inject(RunStore);
  protected readonly connection = inject(ConnectionStore);
  private readonly scenario = inject(ScenarioStore);
  private readonly route = inject(ActivatedRoute);

  protected lower(config: ScenarioConfiguration, target: number): void {
    if (this.runs.starting() || this.runs.active() || !this.connection.canGenerate()) return;
    this.scenario.load(config);
    this.scenario.setRealism(target);
    this.runs.start(this.scenario.configuration());
  }

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      const id = params.get('runId');
      if (id) this.runs.open(id);
    });
  }
}
