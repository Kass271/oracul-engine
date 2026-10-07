import { Component, computed, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';

import type { ResultWildcardGroup } from '../api/models/result-wildcard-group';

const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];

@Component({
  selector: 'app-wildcard-sources',
  imports: [MatCardModule],
  template: `
    <mat-card class="panel" data-testid="sources-panel">
      <mat-card-content>
        <h2 data-testid="sources-title">SOURCES</h2>
    @if (allEmpty()) {
      <p data-testid="sources-empty">No sources</p>
    } @else {
      @for (g of groups(); track g.pipelineId) {
        <div class="group" [attr.data-testid]="'source-group-' + g.pipelineId">
          <h3 [attr.data-testid]="'source-group-title-' + g.pipelineId">{{ g.heading }}</h3>
          @if (g.sources.length === 0) {
            <p [attr.data-testid]="'source-group-empty-' + g.pipelineId">no current sources found</p>
          }
          @for (s of g.sources; track s.evidenceId) {
            @let k = g.pipelineId + '-' + s.evidenceId;
            <div
              class="source"
              [class.highlighted]="highlighted() === s.evidenceId"
              [attr.data-highlighted]="highlighted() === s.evidenceId ? 'true' : null"
              [attr.data-evidence-id]="s.evidenceId"
              [attr.data-testid]="'source-item-' + k"
            >
              <span class="id" [attr.data-testid]="'source-id-' + k">{{ s.evidenceId }}</span>
              <span class="title" [attr.data-testid]="'source-title-' + k">{{ s.title || 'Untitled source' }}</span>
              <span [attr.data-testid]="'source-publisher-' + k">{{ s.publisher || 'Unknown publisher' }}</span>
              <span [attr.data-testid]="'source-date-' + k">{{ date(s.publishedAt) }}</span>
              @if (s.usedInScenario) {
                <span class="badge" [attr.data-testid]="'source-used-' + k">used in scenario</span>
              }
              @if (safe(s.url)) {
                <a [attr.data-testid]="'source-link-' + k" [href]="s.url" target="_blank" rel="noopener noreferrer">Open source</a>
              } @else {
                <span [attr.data-testid]="'source-no-link-' + k">Link unavailable</span>
              }
              @if (s.contentRetrieved) {
                <div class="excerpt" [attr.data-testid]="'source-excerpt-' + k">
                  @for (f of s.fragments; track $index) {
                    <p [attr.data-testid]="'source-fragment-' + k + '-' + $index">{{ f }}</p>
                  }
                </div>
              } @else {
                <span [attr.data-testid]="'source-not-retrieved-' + k">content not retrieved</span>
              }
            </div>
          }
        </div>
      }
    }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .panel {
      max-width: 720px;
      margin: 16px auto;
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
    .excerpt {
      flex-basis: 100%;
    }
    .badge {
      border: 1px solid var(--mat-sys-tertiary);
      border-radius: 4px;
      padding: 0 6px;
      font: var(--mat-sys-label-small);
    }
  `,
})
export class WildcardSourcesComponent {
  readonly groups = input.required<ResultWildcardGroup[]>();
  readonly highlighted = input<string | null>(null);
  protected readonly allEmpty = computed(() => this.groups().every((g) => g.sources.length === 0));

  protected safe(url: string | undefined): boolean {
    return !!url && /^https?:\/\//i.test(url);
  }

  protected date(iso: string | undefined): string {
    if (!iso) return 'date unknown';
    const d = new Date(iso);
    if (isNaN(d.getTime())) return 'date unknown';
    return `${d.getUTCDate()} ${MONTHS[d.getUTCMonth()]} ${d.getUTCFullYear()}`;
  }
}
