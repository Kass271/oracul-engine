import { Component, computed, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import type { ResearchExplanation } from '../api/models/research-explanation';

@Component({
  selector: 'app-why-news-panel',
  imports: [MatCardModule, MatChipsModule],
  template: `
    <mat-card class="panel" data-testid="why-news-panel">
      <mat-card-content>
        <h2 data-testid="why-news-title">WHY THESE NEWS?</h2>
        @if (intents().length === 0) {
          <p data-testid="why-news-empty">No research intents recorded</p>
        }
        @for (i of intents(); track i.id) {
          <div class="step" [attr.data-testid]="'why-news-intent-' + i.id">
            <p [attr.data-testid]="'why-news-description-' + i.id">{{ i.description }}</p>
            @if ((i.drivenBy?.length ?? 0) > 0) {
              <mat-chip-set
                aria-label="Drivers of this research intent"
                [attr.data-testid]="'why-news-drivers-' + i.id"
              >
                @for (d of i.drivenBy ?? []; track $index) {
                  <mat-chip [attr.data-testid]="'why-news-driver-' + i.id + '-' + $index">{{
                    d
                  }}</mat-chip>
                }
              </mat-chip-set>
            }
          </div>
        }
        @let r = research();
        <div data-testid="research-summary">
          <h3 data-testid="research-summary-title">Research summary</h3>
          <p data-testid="summary-searches">
            {{ n(r.counts.searches, 'search performed', 'searches performed') }}
          </p>
          <p data-testid="summary-articles">
            {{ n(r.counts.articlesConsidered, 'article considered', 'articles considered') }}
          </p>
          <p data-testid="summary-events">
            {{ n(r.counts.uniqueEvents, 'unique event identified', 'unique events identified') }}
          </p>
          <p data-testid="summary-selected">
            {{ n(r.counts.eventsSelected, 'event selected', 'events selected') }}
          </p>
          <p data-testid="summary-counter-signals">
            {{ n(r.counts.counterSignals, 'counter-signal retained', 'counter-signals retained') }}
          </p>
          <p data-testid="summary-sources-used">
            {{
              n(
                r.counts.sourcesUsed,
                'source directly influenced the scenario',
                'sources directly influenced the scenario'
              )
            }}
          </p>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .panel {
      max-width: 720px;
      margin: 16px auto;
    }
    .step {
      border: 1px solid var(--mat-sys-outline-variant);
      border-radius: 8px;
      padding: 12px 16px;
    }
  `,
})
export class WhyNewsPanelComponent {
  readonly research = input.required<ResearchExplanation>();
  protected readonly intents = computed(() => this.research().intents ?? []);

  protected n(count: number, one: string, many: string): string {
    return `${count} ${count === 1 ? one : many}`;
  }
}
