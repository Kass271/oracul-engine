import { Component, computed, inject } from '@angular/core';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';

import type { RunStage } from '../api/models/run-stage';
import { RunStore } from './run.store';

const STEPS: { stage: RunStage; label: string }[] = [
  { stage: 'UNDERSTANDING', label: 'Understanding your future…' },
  { stage: 'RESEARCH_STRATEGY', label: 'Building research strategy…' },
  { stage: 'SEARCHING', label: 'Searching current events…' },
  { stage: 'READING_SOURCES', label: 'Reading relevant sources…' },
  { stage: 'CONNECTING_SIGNALS', label: 'Connecting signals…' },
  { stage: 'RANKING', label: 'Ranking evidence…' },
  { stage: 'EXPLORING_FUTURES', label: 'Exploring possible futures…' },
  { stage: 'CHALLENGING_ASSUMPTIONS', label: 'Challenging assumptions…' },
  { stage: 'CONSTRUCTING_SCENARIO', label: 'Constructing scenario…' },
  { stage: 'WRITING_STORY', label: 'Writing from the future…' },
];

@Component({
  selector: 'app-progress-view',
  imports: [MatProgressBarModule, MatListModule],
  template: `
    <div class="progress" data-testid="progress-view">
      <p class="stage" data-testid="progress-stage">{{ label() }}</p>
      <mat-progress-bar data-testid="progress-bar" aria-label="Generation progress" mode="determinate" [value]="value()" />
      <mat-list>
        @for (s of steps(); track s.stage) {
          <mat-list-item [attr.data-testid]="'progress-step-' + s.stage" [attr.data-state]="s.state">{{ s.label }}</mat-list-item>
        }
      </mat-list>
    </div>
  `,
  styles: `
    .progress { margin: auto; width: min(480px, 100%); padding: 16px; display: flex; flex-direction: column; gap: 16px; }
    .stage { font-size: 1.25rem; margin: 0; text-align: center; }
    [data-state='done'] { color: var(--mat-sys-primary); }
    [data-state='current'] { font-weight: 600; }
    [data-state='pending'] { color: var(--mat-sys-on-surface-variant); }
  `,
})
export class ProgressView {
  private readonly runs = inject(RunStore);

  protected readonly label = computed(() => this.runs.run()?.stageLabel ?? STEPS[0].label);
  protected readonly value = computed(() => (this.runs.run()?.stageIndex ?? 0) * 10);
  protected readonly steps = computed(() => {
    const run = this.runs.run();
    const index = run?.stageIndex ?? 0;
    const completed = run?.status === 'COMPLETED';
    return STEPS.map((s, i) => ({
      ...s,
      state: completed || i + 1 < index ? 'done' : i + 1 === index && run?.status !== 'QUEUED' ? 'current' : 'pending',
    }));
  });
}
