import { Injectable, computed, inject, signal } from '@angular/core';

import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';
import type { WritingStyleCatalogue } from '../api/models/writing-style-catalogue';
import { ScenarioService } from '../api/services/scenario.service';
import { WritingStylesService } from '../api/services/writing-styles.service';
import { ScenarioStore } from './scenario.store';

export type LoadState = 'loading' | 'ready' | 'error';

/** Loads catalogue + configuration from the API and feeds the store. */
@Injectable({ providedIn: 'root' })
export class ScenarioLoader {
  private readonly api = inject(ScenarioService);
  private readonly stylesApi = inject(WritingStylesService);
  private readonly store = inject(ScenarioStore);

  readonly catalogue = signal<ScenarioCatalogue | null>(null);
  readonly writingStyles = signal<WritingStyleCatalogue | null>(null);
  private readonly config = signal<ScenarioConfiguration | null>(null);
  private readonly loaded = signal(false);
  private readonly failed = signal(false);

  readonly state = computed<LoadState>(() => {
    if (this.failed()) return 'error';
    return this.catalogue() && this.loaded() && this.writingStyles() ? 'ready' : 'loading';
  });

  private generation = 0;

  load(): void {
    const gen = ++this.generation;
    this.failed.set(false);
    this.catalogue.set(null);
    this.loaded.set(false);
    this.writingStyles.set(null);
    this.config.set(null);
    const feed = () => {
      const c = this.config();
      const w = this.writingStyles();
      if (!c || !w) return;
      this.store.setWritingStyles(w);
      this.store.load(c);
      this.loaded.set(true);
    };
    const fail = () => {
      if (gen === this.generation) this.failed.set(true);
    };
    this.api.getScenarioCatalogue().subscribe({
      next: (c) => gen === this.generation && this.catalogue.set(c),
      error: fail,
    });
    this.api.getScenarioConfiguration().subscribe({
      next: (c) => {
        if (gen !== this.generation) return;
        this.config.set(c);
        feed();
      },
      error: fail,
    });
    this.stylesApi.getWritingStyles().subscribe({
      next: (w) => {
        if (gen !== this.generation) return;
        this.writingStyles.set(w);
        feed();
      },
      error: fail,
    });
  }
}
