import { Component, input } from '@angular/core';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';

import type { CriticIssue } from '../api/models/critic-issue';
import type { ScenarioMetadata } from '../api/models/scenario-metadata';

@Component({
  selector: 'app-scenario-metadata',
  imports: [MatCardModule, MatChipsModule],
  template: `
    <mat-card class="meta" appearance="outlined" data-testid="scenario-metadata">
      <mat-card-content>
        <h2>Scenario settings</h2>
        <mat-chip-set aria-label="Settings">
          <mat-chip data-testid="meta-realism"
            >Realism {{ metadata().configuration.realism }}/10</mat-chip
          >
          <mat-chip data-testid="meta-darkness"
            >Darkness {{ metadata().configuration.darkness }}/10</mat-chip
          >
          <mat-chip data-testid="meta-optimism"
            >Optimism {{ metadata().configuration.optimism }}/10</mat-chip
          >
          <mat-chip data-testid="meta-horizon">Horizon {{ metadata().horizonLabel }}</mat-chip>
        </mat-chip-set>
        @if (metadata().wildcards.length > 0) {
          <mat-chip-set aria-label="Wildcards" data-testid="meta-wildcards">
            @for (w of metadata().wildcards; track $index) {
              <mat-chip [attr.data-testid]="'meta-wildcard-' + $index"
                >{{ w.label }} {{ w.intensity }}/10</mat-chip
              >
            }
          </mat-chip-set>
        }
        <ul class="counts">
          <li data-testid="meta-articles-considered">
            Articles considered: {{ metadata().counts.articlesConsidered }}
          </li>
          <li data-testid="meta-unique-events">
            Unique events: {{ metadata().counts.uniqueEvents }}
          </li>
          <li data-testid="meta-evidence-used">
            Evidence used: {{ metadata().counts.eventsSelected }}
          </li>
        </ul>
        @if (issues().length > 0) {
          <div class="issues" data-testid="critic-issues">
            <p>Open critic notes</p>
            <ul>
              @for (i of issues(); track $index) {
                <li>{{ i.description }}</li>
              }
            </ul>
          </div>
        }
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    .meta {
      margin-top: 32px;
    }
    h2 {
      font: var(--mat-sys-title-small);
      letter-spacing: 0.08em;
      text-transform: uppercase;
      opacity: 0.7;
    }
    mat-chip-set {
      margin-bottom: 8px;
    }
    .counts {
      list-style: none;
      padding: 0;
      margin: 8px 0 0;
      opacity: 0.8;
      font: var(--mat-sys-body-medium);
    }
  `,
})
export class ScenarioMetadataComponent {
  readonly metadata = input.required<ScenarioMetadata>();
  readonly issues = input<CriticIssue[]>([]);
}
