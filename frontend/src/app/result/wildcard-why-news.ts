import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import type { ResearchCounts } from '../api/models/research-counts';
import type { ResultWildcardGroup } from '../api/models/result-wildcard-group';

@Component({
  selector: 'app-wildcard-why-news',
  imports: [MatCardModule, MatChipsModule],
  template: `
    <mat-card class="panel" data-testid="why-news-panel">
      <mat-card-content>
        <h2 data-testid="why-news-title">WHY THESE NEWS?</h2>
        @for (g of groups(); track g.pipelineId) {
          <div class="step" [attr.data-testid]="'why-news-group-' + g.pipelineId">
            <h3 [attr.data-testid]="'why-news-group-title-' + g.pipelineId">{{ g.heading }}</h3>
            @for (q of g.queries; track q.id) {
              <div class="query" [attr.data-testid]="'why-news-query-' + q.id">
                <span [attr.data-testid]="'why-news-query-text-' + q.id">{{ q.text }}</span>
                <mat-chip [attr.data-testid]="'why-news-query-status-' + q.id">{{ q.status }}</mat-chip>
                <span [attr.data-testid]="'why-news-query-count-' + q.id">{{ n(q.articlesReturned, 'item', 'items') }}</span>
              </div>
            }
            @if (g.sources.length === 0) {
              <p [attr.data-testid]="'why-news-group-empty-' + g.pipelineId">no current sources found</p>
            } @else {
              <p [attr.data-testid]="'why-news-group-sources-' + g.pipelineId">{{ n(g.sources.length, 'source', 'sources') }}</p>
            }
          </div>
        }
        @let c = counts();
        <div data-testid="research-summary">
          <h3 data-testid="research-summary-title">Research summary</h3>
          <p data-testid="summary-searches">{{ n(c.searches, 'search performed', 'searches performed') }}</p>
          <p data-testid="summary-articles">{{ n(c.articlesConsidered, 'article considered', 'articles considered') }}</p>
          <p data-testid="summary-kept">{{ n(c.sourcesKept ?? 0, 'source kept', 'sources kept') }}</p>
          <p data-testid="summary-content">{{ n(c.sourcesWithContent ?? 0, 'source with content', 'sources with content') }}</p>
          <p data-testid="summary-used">{{ n(c.sourcesUsed, 'source used in the scenario', 'sources used in the scenario') }}</p>
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
      margin-bottom: 8px;
    }
    .query {
      display: flex;
      gap: 12px;
      align-items: center;
    }
  `,
})
export class WildcardWhyNewsComponent {
  readonly groups = input.required<ResultWildcardGroup[]>();
  readonly counts = input.required<ResearchCounts>();

  protected n(count: number, one: string, many: string): string {
    return `${count} ${count === 1 ? one : many}`;
  }
}
