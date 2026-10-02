import { Injectable, computed, inject, signal } from '@angular/core';

import type { ScenarioCatalogue } from '../api/models/scenario-catalogue';
import { ScenarioService } from '../api/services/scenario.service';
import { ScenarioStore } from './scenario.store';

export type LoadState = 'loading' | 'ready' | 'error';

/** Loads catalogue + configuration from the API and feeds the store. */
@Injectable({ providedIn: 'root' })
export class ScenarioLoader {
  private readonly api = inject(ScenarioService);
  private readonly store = inject(ScenarioStore);

  readonly catalogue = signal<ScenarioCatalogue | null>(null);
  private readonly loaded = signal(false);
  private readonly failed = signal(false);

  readonly state = computed<LoadState>(() => {
    if (this.failed()) return 'error';
    return this.catalogue() && this.loaded() ? 'ready' : 'loading';
  });

  private generation = 0;

  load(): void {
    const gen = ++this.generation;
    this.failed.set(false);
    this.catalogue.set(null);
    this.loaded.set(false);
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
        this.store.load(c);
        this.loaded.set(true);
      },
      error: fail,
    });
  }
}
