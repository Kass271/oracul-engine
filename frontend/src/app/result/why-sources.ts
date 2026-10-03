import {
  Component,
  Injector,
  OnDestroy,
  computed,
  inject,
  input,
  afterNextRender,
  signal,
} from '@angular/core';
import { MatButtonModule } from '@angular/material/button';

import type { CausalStep } from '../api/models/causal-step';
import type { ResultSource } from '../api/models/result-source';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const HIGHLIGHT_MS = 3000;

@Component({
  selector: 'app-why-sources',
  imports: [MatButtonModule],
  template: `
    <div class="actions" data-testid="result-actions">
      <button mat-stroked-button type="button" data-testid="open-why" [attr.aria-expanded]="whyOpen()" (click)="whyOpen.set(!whyOpen())">WHY COULD THIS HAPPEN?</button>
      <button mat-stroked-button type="button" data-testid="open-sources" [attr.aria-expanded]="sourcesOpen()" (click)="sourcesOpen.set(!sourcesOpen())">SOURCES</button>
    </div>
    @if (whyOpen()) {
      <section class="panel" data-testid="why-panel">
        <h2 data-testid="why-title">WHY COULD THIS HAPPEN?</h2>
        @for (s of chain(); track s.order; let last = $last) {
          <div class="step" [attr.data-testid]="'why-step-' + s.order">
            <div class="klass" [attr.data-testid]="'why-step-class-' + s.order">{{ classLabel(s) }}</div>
            <p [attr.data-testid]="'why-step-statement-' + s.order">{{ s.statement }}</p>
            @if ((s.informationClass === 'FACT' || s.informationClass === 'INFERENCE') && (s.evidenceIds?.length ?? 0) > 0) {
              <div class="chips" [attr.data-testid]="'why-evidence-list-' + s.order">
                @for (e of s.evidenceIds ?? []; track e) {
                  <button type="button" class="chip" [attr.data-testid]="'why-evidence-' + s.order + '-' + e" [disabled]="!known().has(e)" (click)="select(e)">{{ e }}</button>
                }
              </div>
            }
          </div>
          @if (!last) {
            <div class="arrow" data-testid="why-arrow">↓</div>
          }
        }
      </section>
    }
    @if (sourcesOpen()) {
      <section class="panel" data-testid="sources-panel">
        <h2 data-testid="sources-title">SOURCES</h2>
        @if (sources().length === 0) {
          <p data-testid="sources-empty">No sources</p>
        }
        @for (s of sources(); track s.evidenceId) {
          <div class="source" [class.highlighted]="highlighted() === s.evidenceId" [attr.data-highlighted]="highlighted() === s.evidenceId ? 'true' : null" [attr.data-testid]="'source-item-' + s.evidenceId">
            <span class="id" [attr.data-testid]="'source-id-' + s.evidenceId">{{ s.evidenceId }}</span>
            <span class="title" [attr.data-testid]="'source-title-' + s.evidenceId">{{ s.title || 'Untitled source' }}</span>
            <span [attr.data-testid]="'source-publisher-' + s.evidenceId">{{ s.publisher || 'Unknown publisher' }}</span>
            <span [attr.data-testid]="'source-date-' + s.evidenceId">{{ date(s.publishedAt) }}</span>
            @if (s.usedInScenario) {
              <span class="badge" [attr.data-testid]="'source-used-' + s.evidenceId">used in scenario</span>
            }
            @if (s.counterSignal) {
              <span class="badge" [attr.data-testid]="'source-counter-' + s.evidenceId">counter-signal</span>
            }
            @if (safe(s.url)) {
              <a [attr.data-testid]="'source-link-' + s.evidenceId" [href]="s.url" target="_blank" rel="noopener noreferrer">Open source</a>
            } @else {
              <span [attr.data-testid]="'source-no-link-' + s.evidenceId">Link unavailable</span>
            }
          </div>
        }
      </section>
    }
  `,
  styles: `
    .actions {
      display: flex;
      gap: 12px;
      justify-content: center;
      margin: 16px 0;
    }
    .panel {
      max-width: 720px;
      margin: 16px auto;
    }
    .step {
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      padding: 12px 16px;
    }
    .klass {
      font: var(--mat-sys-label-large);
      letter-spacing: 0.08em;
    }
    .arrow {
      text-align: center;
      font-size: 24px;
    }
    .chips {
      display: flex;
      gap: 8px;
      flex-wrap: wrap;
    }
    .chip {
      border: 1px solid var(--mat-sys-outline);
      border-radius: 12px;
      padding: 2px 10px;
      background: none;
      color: inherit;
      cursor: pointer;
    }
    .chip:disabled {
      opacity: 0.5;
      cursor: default;
    }
    .source {
      display: flex;
      flex-wrap: wrap;
      gap: 12px;
      align-items: center;
      padding: 8px 12px;
      border-radius: 8px;
      border: 1px solid transparent;
    }
    .source.highlighted {
      border-color: var(--mat-sys-primary);
      background: var(--mat-sys-primary-container);
    }
    .badge {
      border: 1px solid var(--mat-sys-tertiary);
      border-radius: 4px;
      padding: 0 6px;
      font: var(--mat-sys-label-small);
    }
  `,
})
export class WhySourcesComponent implements OnDestroy {
  private readonly injector = inject(Injector);
  readonly causalChain = input.required<CausalStep[]>();
  readonly sources = input.required<ResultSource[]>();
  readonly futureDate = input.required<string>();

  protected readonly whyOpen = signal(false);
  protected readonly sourcesOpen = signal(false);
  protected readonly highlighted = signal<string | null>(null);
  protected readonly chain = computed(() =>
    [...this.causalChain()].sort((a, b) => a.order - b.order),
  );
  protected readonly known = computed(() => new Set(this.sources().map((s) => s.evidenceId)));
  private timer: ReturnType<typeof setTimeout> | null = null;

  protected classLabel(s: CausalStep): string {
    if (s.informationClass === 'FUTURE_EVENT') {
      return `ORACUL FUTURE — ${s.year ?? this.futureDate().slice(0, 4)}`;
    }
    return s.informationClass;
  }

  protected select(id: string): void {
    if (!this.known().has(id)) return;
    this.sourcesOpen.set(true);
    this.highlighted.set(id);
    if (this.timer !== null) clearTimeout(this.timer);
    this.timer = setTimeout(() => {
      this.timer = null;
      this.highlighted.set(null);
    }, HIGHLIGHT_MS);
    afterNextRender(
      () => {
        const el = document.querySelector<HTMLElement>(`[data-testid="source-item-${id}"]`);
        if (el && typeof el.scrollIntoView === 'function') {
          el.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
      },
      { injector: this.injector },
    );
  }

  protected safe(url: string | undefined): boolean {
    return !!url && /^https?:\/\//i.test(url);
  }

  protected date(iso: string | undefined): string {
    if (!iso) return 'date unknown';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return 'date unknown';
    return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
  }

  ngOnDestroy(): void {
    if (this.timer !== null) clearTimeout(this.timer);
  }
}
