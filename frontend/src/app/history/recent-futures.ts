import { Component, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatMenuModule } from '@angular/material/menu';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { Router } from '@angular/router';

import type { RecentRunSummary } from '../api/models/recent-run-summary';
import { HistoryService } from '../api/services/history.service';
import { ScenarioLoader } from '../scenario/scenario.loader';

const FALLBACK_LABELS: Record<string, string> = {
  '1d': 'Tomorrow',
  '1w': '1 week',
  '1m': '1 month',
  '1y': '1 year',
  '5y': '5 years',
  '10y': '10 years',
  '20y': '20 years',
};

@Component({
  selector: 'app-recent-futures',
  imports: [MatButtonModule, MatMenuModule, MatProgressSpinnerModule],
  template: `
    <button mat-button data-testid="recent-futures-button" [matMenuTriggerFor]="menu" (menuOpened)="load()" (menuClosed)="close()">
      Recent futures
    </button>
    <mat-menu #menu="matMenu">
      @if (open()) {
      @switch (state()) {
        @case ('loading') {
          <div class="state" data-testid="recent-futures-loading" (click)="$event.stopPropagation()">
            <mat-spinner diameter="24" />
          </div>
        }
        @case ('error') {
          <div class="state" data-testid="recent-futures-error">Recent futures are unavailable</div>
        }
        @case ('ready') {
          @if (items().length === 0) {
            <div class="state" data-testid="recent-futures-empty">No futures yet</div>
          } @else {
            <div data-testid="recent-futures-list">
              @for (it of items(); track it.id) {
                <button mat-menu-item [attr.data-testid]="'recent-future-' + it.id" (click)="select(it)">
                  <span class="entry">
                    <span class="time" [attr.data-testid]="'recent-future-time-' + it.id">{{ time(it) }}</span>
                    <span class="headline" [attr.data-testid]="'recent-future-headline-' + it.id">{{ it.headline }}</span>
                    <span class="settings" [attr.data-testid]="'recent-future-settings-' + it.id">{{ settings(it) }}</span>
                  </span>
                </button>
              }
            </div>
          }
        }
      }
      }
    </mat-menu>
  `,
  styles: `
    .state { padding: 12px 16px; }
    .entry { display: flex; flex-direction: column; line-height: 1.3; padding: 4px 0; }
    .time, .settings { font-size: 12px; opacity: 0.7; }
    .headline { white-space: normal; }
  `,
})
export class RecentFuturesComponent {
  private readonly api = inject(HistoryService);
  private readonly router = inject(Router);
  private readonly loader = inject(ScenarioLoader);
  private seq = 0;

  protected readonly state = signal<'loading' | 'error' | 'ready'>('loading');
  protected readonly open = signal(false);
  protected readonly items = signal<RecentRunSummary[]>([]);

  protected load(): void {
    const mine = ++this.seq;
    this.open.set(true);
    this.state.set('loading');
    this.items.set([]);
    this.api.listRecentRuns().subscribe({
      next: (list) => {
        if (mine !== this.seq) return;
        this.items.set(list.items);
        this.state.set('ready');
      },
      error: () => {
        if (mine !== this.seq) return;
        this.items.set([]);
        this.state.set('error');
      },
    });
  }

  protected close(): void {
    this.seq++;
    this.open.set(false);
  }

  protected select(it: RecentRunSummary): void {
    this.open.set(false);
    void this.router.navigate(['/futures', it.id]);
  }

  protected time(it: RecentRunSummary): string {
    const d = new Date(it.createdAt);
    const now = new Date();
    const same = d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
    const pad = (n: number): string => String(n).padStart(2, '0');
    const hm = `${pad(d.getHours())}:${pad(d.getMinutes())}`;
    if (same) return hm;
    const m = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'][d.getMonth()];
    return `${d.getDate()} ${m} ${d.getFullYear()}, ${hm}`;
  }

  protected settings(it: RecentRunSummary): string {
    const c = it.configuration;
    const label =
      this.loader.catalogue()?.horizons.find((h) => h.code === c.horizon)?.label ?? FALLBACK_LABELS[c.horizon] ?? c.horizon;
    return `R${c.realism} D${c.darkness} O${c.optimism} · ${label}`;
  }
}
