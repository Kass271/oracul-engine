import { Component, computed, input } from '@angular/core';
import { MatChipsModule } from '@angular/material/chips';

import type { FutureStory } from '../api/models/future-story';

@Component({
  selector: 'app-future-story',
  imports: [MatChipsModule],
  template: `
    <article class="story">
      <div class="labels" data-testid="story-labels">
        <span class="label" data-testid="label-ai-generated">{{ labels()[0] }}</span>
        <span class="label" data-testid="label-not-current-news">{{ labels()[1] }}</span>
      </div>
      <h1 data-testid="story-headline">{{ story().headline }}</h1>
      <p class="dateline" data-testid="story-dateline">{{ story().dateline }}</p>
      <div class="body" data-testid="story-body">
        @for (p of paragraphs(); track $index) {
          <p data-testid="story-paragraph">{{ p }}</p>
        }
      </div>
    </article>
  `,
  styles: `
    .story { max-width: 720px; margin: 0 auto; }
    .labels { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
    .label { border: 1px solid var(--mat-sys-tertiary); color: var(--mat-sys-tertiary); border-radius: 4px; padding: 2px 10px; font: var(--mat-sys-label-large); letter-spacing: 0.08em; }
    h1 { font: var(--mat-sys-headline-large); margin: 8px 0; }
    .dateline { font: var(--mat-sys-label-large); letter-spacing: 0.1em; opacity: 0.7; margin: 0 0 24px; }
    .body p { font: var(--mat-sys-body-large); line-height: 1.7; margin: 0 0 16px; }
  `,
})
export class FutureStoryComponent {
  readonly story = input.required<FutureStory>();
  readonly labels = input.required<string[]>();
  protected readonly paragraphs = computed(() =>
    this.story()
      .body.split(/\n\s*\n/)
      .map((p) => p.trim())
      .filter((p) => p.length > 0),
  );
}
