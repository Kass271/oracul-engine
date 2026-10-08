import { Component, computed, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';

import type { StoryLanguage } from '../api/models/story-language';
import type { WritingStyleProfile } from '../api/models/writing-style-profile';

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
  imports: [MatButtonModule, MatMenuModule],
  template: `
    <div class="quick" data-testid="quick-actions">
      <button mat-stroked-button data-testid="quick-more-realistic" [disabled]="off('MORE_REALISTIC')" (click)="go('MORE_REALISTIC')">MORE REALISTIC</button>
      <button mat-stroked-button data-testid="quick-darker" [disabled]="off('DARKER')" (click)="go('DARKER')">DARKER</button>
      <button mat-stroked-button data-testid="quick-more-optimistic" [disabled]="off('MORE_OPTIMISTIC')" (click)="go('MORE_OPTIMISTIC')">MORE OPTIMISTIC</button>
      <button mat-stroked-button data-testid="quick-more-extreme" [disabled]="off('MORE_EXTREME')" (click)="go('MORE_EXTREME')">MORE EXTREME</button>
      <button mat-stroked-button data-testid="quick-alternative" [disabled]="blocked() || !runs.run()" (click)="alternative()">ALTERNATIVE FUTURE</button>
      <button mat-stroked-button data-testid="quick-rewrite" [disabled]="blocked() || !runs.run()" [matMenuTriggerFor]="rewriteMenu">REWRITE AS…</button>
      <mat-menu #rewriteMenu="matMenu">
        <div data-testid="rewrite-menu">
          @for (lang of languages; track lang) {
            <div [attr.data-testid]="'rewrite-language-' + lang" class="rewrite-heading">{{ languageLabel(lang) }}</div>
            @for (p of profiles(); track p.id) {
              <button mat-menu-item [attr.data-testid]="'rewrite-option-' + p.id + '-' + lang" [disabled]="isOwn(p.id, lang)" (click)="rewrite(p, lang)">{{ p.label }}</button>
            }
          }
        </div>
      </mat-menu>
    </div>
  `,
  styles: `.quick { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 24px; } .rewrite-heading { padding: 8px 16px; font-weight: 500; }`,
})
export class QuickActions {
  private readonly connection = inject(ConnectionStore);
  private readonly runs = inject(RunStore);
  private readonly scenario = inject(ScenarioStore);

  protected readonly blocked = computed(
    () => !this.connection.canGenerate() || this.runs.starting() || this.runs.active(),
  );

  protected readonly languages: StoryLanguage[] = ['en', 'uk'];
  protected readonly profiles = computed(() => this.scenario.writingStyles()?.profiles ?? []);

  protected languageLabel(lang: StoryLanguage): string {
    const found = this.scenario.writingStyles()?.languages.find((l) => l.code === lang);
    return found?.label ?? (lang === 'en' ? 'English' : 'Ukrainian');
  }

  protected isOwn(profileId: string, lang: StoryLanguage): boolean {
    const output = this.runs.run()?.configuration.output;
    return (output?.writingStyle?.profileId ?? 'official-briefing') === profileId && (output?.language ?? 'en') === lang;
  }

  protected rewrite(p: WritingStyleProfile, lang: StoryLanguage): void {
    const run = this.runs.run();
    if (this.blocked() || !run || this.isOwn(p.id, lang)) return;
    this.runs.rewrite(run.id, { mode: p.mode, profileId: p.id }, lang);
  }

  private target(a: QuickAction): number | null {
    const v = a === 'DARKER' ? this.scenario.darkness() : a === 'MORE_OPTIMISTIC' ? this.scenario.optimism() : this.scenario.realism();
    return quickTarget(a, v);
  }

  protected off(a: QuickAction): boolean {
    return this.blocked() || this.target(a) === null;
  }

  protected alternative(): void {
    const run = this.runs.run();
    if (this.blocked() || !run) return;
    this.runs.startAlternative(run.id);
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
