import { Injectable, computed, signal } from '@angular/core';

import type { HorizonCode } from '../api/models/horizon-code';
import type { ScenarioConfiguration } from '../api/models/scenario-configuration';

export const HORIZON_CODES: readonly HorizonCode[] = ['1d', '1w', '1m', '1y', '5y', '10y', '20y'];

export const DEFAULT_CONFIGURATION: ScenarioConfiguration = {
  realism: 8,
  darkness: 5,
  optimism: 5,
  horizon: '1y',
  wildcards: [],
  customWildcards: [],
  output: { story: true, illustration: false },
};

function validIntensity(n: unknown): n is number {
  return typeof n === 'number' && Number.isInteger(n) && n >= 1 && n <= 10;
}

@Injectable({ providedIn: 'root' })
export class ScenarioStore {
  private readonly state = signal<ScenarioConfiguration>({ ...DEFAULT_CONFIGURATION });

  readonly realism = computed(() => this.state().realism);
  readonly darkness = computed(() => this.state().darkness);
  readonly optimism = computed(() => this.state().optimism);
  readonly horizon = computed<HorizonCode>(() => this.state().horizon);
  readonly configuration = computed<ScenarioConfiguration>(() => this.state());

  load(config: ScenarioConfiguration): void {
    this.state.set({ ...config });
  }

  setRealism(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, realism: n }));
  }

  setDarkness(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, darkness: n }));
  }

  setOptimism(n: number): void {
    if (validIntensity(n)) this.state.update((s) => ({ ...s, optimism: n }));
  }

  setHorizon(code: HorizonCode): void {
    if (HORIZON_CODES.includes(code)) this.state.update((s) => ({ ...s, horizon: code }));
  }
}
