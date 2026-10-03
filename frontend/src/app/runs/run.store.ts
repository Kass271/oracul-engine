import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { MatSnackBar, MatSnackBarRef } from '@angular/material/snack-bar';
import { Router } from '@angular/router';

import type { GenerationRun } from '../api/models/generation-run';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import { RunsService } from '../api/services/runs.service';
import { ConnectionStore } from '../chatgpt/connection.store';
import { ScenarioStore } from '../scenario/scenario.store';
import { RunErrorMessage } from './run-error-message';

export const POLL_INTERVAL_MS = 1000;
export const MAX_POLL_FAILURES = 10;

const GENERIC_ERROR = 'Something went wrong — try again';
const CONNECTION_CODES = ['CHATGPT_NOT_CONNECTED', 'CHATGPT_SESSION_EXPIRED', 'CHATGPT_PLAN_NOT_ELIGIBLE'];

@Injectable({ providedIn: 'root' })
export class RunStore {
  private readonly api = inject(RunsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly router = inject(Router);
  private readonly connection = inject(ConnectionStore);
  private readonly panel = inject(ScenarioStore);
  private panelPending = false;

  private timer?: ReturnType<typeof setTimeout>;
  private failures = 0;
  private runId: string | null = null;
  private epoch = 0;
  private expiredLoadedFor: string | null = null;
  private snack?: MatSnackBarRef<RunErrorMessage>;

  readonly run = signal<GenerationRun | null>(null);
  readonly starting = signal(false);
  readonly notFound = signal(false);
  readonly unavailable = signal(false);
  readonly active = computed(() => {
    const s = this.run()?.status;
    return s === 'QUEUED' || s === 'RUNNING';
  });

  start(config: ScenarioConfiguration): void {
    if (this.starting()) return;
    this.starting.set(true);
    this.api.startRun({ body: config }).subscribe({
      next: (run) => {
        this.starting.set(false);
        this.reset();
        this.run.set(run);
        this.runId = run.id;
        this.schedule();
        void this.router.navigate(['/futures', run.id], { replaceUrl: true });
      },
      error: (err: unknown) => {
        this.starting.set(false);
        const body = err instanceof HttpErrorResponse ? err.error : null;
        const isApiError =
          body && typeof body === 'object' && typeof body.code === 'string' && typeof body.message === 'string';
        this.showError(isApiError ? body.message : GENERIC_ERROR);
        if (isApiError && CONNECTION_CODES.includes(body.code)) this.connection.load();
      },
    });
  }

  open(runId: string): void {
    if (this.runId === runId && this.run() && !this.notFound()) {
      // Already tracking this run; keep polling if still active.
      if (this.active() && !this.unavailable() && this.timer === undefined) this.schedule();
      return;
    }
    this.reset();
    this.runId = runId;
    this.panelPending = true;
    this.poll();
  }

  retry(): void {
    this.unavailable.set(false);
    this.failures = 0;
    this.clear();
    if (this.runId) this.schedule();
  }

  stop(): void {
    this.clear();
    this.epoch++;
  }

  private reset(): void {
    this.stop();
    this.run.set(null);
    this.notFound.set(false);
    this.unavailable.set(false);
    this.failures = 0;
    this.runId = null;
    this.panelPending = false;
  }

  private clear(): void {
    if (this.timer !== undefined) clearTimeout(this.timer);
    this.timer = undefined;
  }

  private schedule(): void {
    this.clear();
    this.timer = setTimeout(() => {
      this.timer = undefined;
      this.poll();
    }, POLL_INTERVAL_MS);
  }

  private poll(): void {
    const id = this.runId;
    if (!id) return;
    const epoch = this.epoch;
    this.api.getRun({ runId: id }).subscribe({
      next: (run) => {
        if (epoch !== this.epoch) return;
        this.failures = 0;
        this.run.set(run);
        if (this.panelPending) {
          this.panelPending = false;
          this.panel.load(run.configuration);
        }
        if (
          run.status === 'FAILED' &&
          run.failure?.code === 'CHATGPT_SESSION_EXPIRED' &&
          this.expiredLoadedFor !== run.id
        ) {
          this.expiredLoadedFor = run.id;
          this.connection.load();
        }
        if (run.status === 'QUEUED' || run.status === 'RUNNING') this.schedule();
      },
      error: (err: unknown) => {
        if (epoch !== this.epoch) return;
        if (err instanceof HttpErrorResponse && err.status === 404) {
          this.run.set(null);
          this.notFound.set(true);
          return;
        }
        this.failures++;
        if (this.failures >= MAX_POLL_FAILURES) {
          this.unavailable.set(true);
          return;
        }
        this.schedule();
      },
    });
  }

  private showError(text: string): void {
    this.snack?.dismiss();
    document.querySelectorAll('.mat-mdc-snack-bar-container').forEach((el) => {
      if (el.querySelector('[data-testid="run-error-message"]')) el.remove();
    });
    this.snack = this.snackBar.openFromComponent(RunErrorMessage, { duration: 6000, data: text });
  }
}
